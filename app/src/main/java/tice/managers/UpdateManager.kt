package tice.managers

import kotlinx.serialization.UnsafeSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import tice.exceptions.HTTPRequesterException
import tice.exceptions.MinVersionException
import tice.models.responses.ServerInformationResponse

class UpdateManager constructor(
    private val BASE_URL: String,
) : UpdateManagerType {

    @OptIn(UnsafeSerializationApi::class)
    override suspend fun check(currentVersion: Int) {
        val okHttp = OkHttpClient()
        val request = Request.Builder().url(BASE_URL.toHttpUrl()).get()
        val response = okHttp.newCall(request.build()).execute()

        val responseBody = response.body ?: throw HTTPRequesterException.EmptyErrorResponse(response.code)
        val apiResponse: ServerInformationResponse = Json.decodeFromString(responseBody.string())

        if (apiResponse.minVersion.android > currentVersion) {
            throw MinVersionException.Outdated(apiResponse.minVersion.android)
        }
    }
}
