package tice.backend

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.UnsafeSerializationApi
import kotlinx.serialization.serializer
import okhttp3.Headers
import okhttp3.Response

interface HTTPRequesterType {

    enum class HTTPMethod {
        GET,
        POST,
        PUT,
        DELETE,
    }

    suspend fun <T> executeRequest(url: String, method: HTTPMethod, headers: Headers?, body: Pair<T, KSerializer<T>>?): Response
    suspend fun <T> extractResponse(response: Response, deserializer: DeserializationStrategy<T>): T
}

@UnsafeSerializationApi
suspend inline fun <reified TIn : Any, reified TOut : Any> HTTPRequesterType.request(
    url: String,
    method: HTTPRequesterType.HTTPMethod,
    headers: Headers?,
    body: TIn
): TOut {
    val response = executeRequest(
        url,
        method,
        headers,
        Pair(body, TIn::class.serializer())
    )

    return if (TOut::class == Unit::class) {
        Unit as TOut
    } else {
        return extractResponse(response, TOut::class.serializer())
    }
}

@UnsafeSerializationApi
suspend inline fun <reified TOut : Any> HTTPRequesterType.request(url: String, method: HTTPRequesterType.HTTPMethod, headers: Headers?): TOut {
    val response = executeRequest<Unit>(
        url,
        method,
        headers,
        null
    )

    return if (TOut::class == Unit::class) {
        Unit as TOut
    } else {
        return extractResponse(response, TOut::class.serializer())
    }
}
