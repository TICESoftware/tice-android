@file:Suppress("NAME_SHADOWING")

package tice.backend

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import tice.dagger.scopes.AppScope
import tice.exceptions.HTTPRequesterException
import tice.models.responses.APIError
import tice.utility.safeParse
import javax.inject.Inject

@AppScope
class HTTPRequester @Inject constructor(
    private val okHttpClient: OkHttpClient
) : HTTPRequesterType {

    @Serializable
    data class APIErrorDummy(val type: APIError.ErrorType, val description: String)

    override suspend fun <T> executeRequest(
        url: String,
        method: HTTPRequesterType.HTTPMethod,
        headers: Headers?,
        body: Pair<T, KSerializer<T>>?
    ): Response {
        val httpUrl: HttpUrl = url.toHttpUrl()

        val request = Request.Builder()
            .url(httpUrl)

        headers?.let { request.headers(it) }

        val requestBody = body?.let {
            val body: String = Json.encodeToString(it.second, it.first)
            val mediaType = "application/json; charset=utf-8".toMediaType()
            body.toRequestBody(mediaType)
        } ?: "".toRequestBody()

        when (method) {
            HTTPRequesterType.HTTPMethod.GET -> request.get()
            HTTPRequesterType.HTTPMethod.POST -> request.post(requestBody)
            HTTPRequesterType.HTTPMethod.PUT -> request.put(requestBody)
            HTTPRequesterType.HTTPMethod.DELETE -> request.delete(requestBody)
        }

        val response = okHttpClient.newCall(request.build()).execute()

        if (response.code !in 200 until 300) {
            val responseBody = response.body ?: throw HTTPRequesterException.EmptyErrorResponse(response.code)
            val error = Json.safeParse(APIErrorDummy.serializer(), responseBody.string())
            throw APIError(error.type, error.description)
        }

        return response
    }

    override suspend fun <T> extractResponse(response: Response, deserializer: DeserializationStrategy<T>): T {
        val responseBody = response.body ?: throw HTTPRequesterException.EmptyResponse

        val apiResponse = Json.parseToJsonElement(responseBody.string()).jsonObject
        val success = apiResponse.getValue("success").jsonPrimitive.boolean
        if (success) {
            return Json.decodeFromJsonElement(deserializer, apiResponse.getValue("result").jsonObject)
        } else {
            val error = Json.decodeFromJsonElement(APIErrorDummy.serializer(), apiResponse.getValue("error").jsonObject)
            throw APIError(error.type, error.description)
        }
    }
}
