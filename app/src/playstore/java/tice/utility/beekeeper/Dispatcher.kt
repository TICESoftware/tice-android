package tice.utility.beekeeper

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.ByteString.Companion.encode
import tice.exceptions.HTTPRequesterException
import tice.utility.safeParse
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Named

class Dispatcher @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @Named("BEEKEEPER_BASE_URL") private val BASE_URL: String,
    @Named("BEEKEEPER_DISPATCH_INTERVAL") internal val dispatchInterval: Long,
    @Named("BEEKEEPER_MAX_BATCH_SIZE") internal val maxBatchSize: Int,
    @Named("BEEKEEPER_SECRET") private val secret: String,
) {
    private val formatter: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    init {
        formatter.timeZone = TimeZone.getTimeZone("UTC")
    }

    fun dispatch(events: List<Event>): Response {
        val httpUrl = BASE_URL.toHttpUrl().resolve(events.first().product)!!
        val body: String = Json.encodeToString(events)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = body.toRequestBody(mediaType)

        val headers = signature("POST", requestBody, httpUrl.encodedPath, Date())
        val request = Request.Builder()
            .url(httpUrl)
            .headers(headers)
            .post(requestBody)
            .build()

        val response = okHttpClient.newCall(request).execute()

        if (response.code !in 200 until 300) {
            val responseBody = response.body ?: throw HTTPRequesterException.EmptyErrorResponse(
                response.code
            )
            val error = Json.safeParse(URLDispatcherError.serializer(), responseBody.string())
            throw error
        }

        return response
    }

    fun signature(method: String, body: RequestBody, path: String, date: Date): Headers {
        val contentType = body.contentType().toString()

        val buffer = Buffer()
        body.writeTo(buffer)
        val contentHash = buffer.sha1().hex()
        val dateString = formatter.format(date)

        val contents = """
            $method
            $contentHash
            $contentType
            $dateString
            $path
        """.trimIndent()

        val hmacBuffer = Buffer()
        hmacBuffer.write(contents.toByteArray())
        val signature = hmacBuffer.hmacSha256(secret.encode()).base64()

        val headers = Headers.Builder()
        headers.add("authorization-date", dateString)
        headers.add("authorization", signature)
        return headers.build()
    }
}
