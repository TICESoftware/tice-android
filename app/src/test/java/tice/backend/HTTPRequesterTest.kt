package tice.backend

import io.mockk.*
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.serialization.UnsafeSerializationApi
import okhttp3.*
import okhttp3.Call
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tice.exceptions.HTTPRequesterException
import tice.models.Platform
import tice.models.UserId
import tice.models.requests.VerifyRequest
import tice.models.responses.APIError
import tice.models.responses.CreateUserResponse
import java.util.*


@UnsafeSerializationApi
internal class HTTPRequesterTest {

    private lateinit var httpRequester: HTTPRequesterType

    private val mockOkHttpClient: OkHttpClient = mockk(relaxUnitFun = true)
    private val mockResponse: Response = mockk(relaxUnitFun = true)
    private val mockResponseBody: ResponseBody = mockk(relaxUnitFun = true)
    private val mockCall: Call = mockk(relaxUnitFun = true)

    val FULL_URL = "https://base_url/test_path"

    val DEVICE_ID = "verifyRequestDeviceId"
    val REQUEST_BODY = VerifyRequest(DEVICE_ID, Platform.Android)
    val SERIALIZED_REQUEST_BODY = """{"deviceId":"$DEVICE_ID","platform":"android"}"""

    val USER_ID: UserId = UUID.fromString("070655ad-fc29-401b-a2b0-22bcf7a7c2cc")
    val RESPONSE_BODY = CreateUserResponse(USER_ID)
    val SERIALIZED_RESPONSE_BODY_SUCCESS = """{ "success": true, "result": {"userId":"$USER_ID"}}"""

    val API_ERROR_DESCRIPTION = "Resource not found"
    val SERIALIZED_RESPONSE_BODY_ERROR = """{ "success": false, "error": {"type": "notFound", "description": "$API_ERROR_DESCRIPTION"}}"""

    @BeforeEach
    fun before() {
        clearAllMocks()

        httpRequester = HTTPRequester(mockOkHttpClient)
    }

    @Test
    fun get_WithoutBody_Response() = runBlockingTest {
        val testHeaders = Headers.Builder().add("TestHeaderName", "TestHeaderValue").build()

        every { mockOkHttpClient.newCall(any()) } answers {
            val request: Request = arg(0)

            assertEquals("GET", request.method)
            assertEquals(FULL_URL, request.url.toString())
            assertEquals(request.headers, testHeaders)
            assert(request.isHttps)
            mockCall
        }

        every { mockCall.execute() } returns mockResponse
        every { mockResponse.code } returns 200
        every { mockResponse.body } returns mockResponseBody
        every { mockResponseBody.string() } returns SERIALIZED_RESPONSE_BODY_SUCCESS

        val response: CreateUserResponse = httpRequester.request(
            FULL_URL,
            HTTPRequesterType.HTTPMethod.GET,
            testHeaders,
            REQUEST_BODY
        )

        assertEquals(response, RESPONSE_BODY)

        verify(exactly = 1) { mockCall.execute() }
        confirmVerified(mockCall)
    }

    @Test
    fun get_WithoutBody_APIError() = runBlockingTest {
        val testHeaders = Headers.Builder().add("TestHeaderName", "TestHeaderValue").build()

        every { mockOkHttpClient.newCall(any()) } answers {
            val request: Request = arg(0)

            assertEquals("GET", request.method)
            assertEquals(FULL_URL, request.url.toString())
            assertEquals(request.headers, testHeaders)
            assert(request.isHttps)

            mockCall
        }

        every { mockCall.execute() } returns mockResponse
        every { mockResponse.code } returns 200
        every { mockResponse.body } returns mockResponseBody
        every { mockResponseBody.string() } returns SERIALIZED_RESPONSE_BODY_ERROR

        val apiError = assertThrows<APIError> {
            runBlockingTest {
                httpRequester.request<VerifyRequest, CreateUserResponse>(
                    FULL_URL,
                    HTTPRequesterType.HTTPMethod.GET,
                    testHeaders,
                    REQUEST_BODY
                )
            }
        }

        assertEquals(apiError.type, APIError.ErrorType.NOT_FOUND)
        assertEquals(apiError.description, API_ERROR_DESCRIPTION)

        verify(exactly = 1) { mockCall.execute() }
        confirmVerified(mockCall)
    }

    @Test
    fun post_Body_Response() = runBlockingTest {
        val testHeaders = Headers.Builder().build()

        every { mockOkHttpClient.newCall(any()) } answers {
            val request: Request = arg(0)
            assertEquals("POST", request.method)
            assertEquals(FULL_URL, request.url.toString())
            assertEquals(request.headers, testHeaders)
            assert(request.isHttps)

            val bufferedSink = Buffer()
            request.body!!.writeTo(bufferedSink)
            assertEquals(SERIALIZED_REQUEST_BODY, bufferedSink.readUtf8())
            assertEquals(request.body!!.contentType().toString(), "application/json; charset=utf-8")
            mockCall
        }

        every { mockCall.execute() } returns mockResponse
        every { mockResponse.code } returns 200
        every { mockResponse.body } returns mockResponseBody
        every { mockResponseBody.string() } returns SERIALIZED_RESPONSE_BODY_SUCCESS

        val response: CreateUserResponse = httpRequester.request(
            FULL_URL,
            HTTPRequesterType.HTTPMethod.POST,
            testHeaders,
            REQUEST_BODY
        )

        assertEquals(response, RESPONSE_BODY)

        verify(exactly = 1) { mockCall.execute() }
        confirmVerified(mockCall)
    }

    @Test
    fun post_Body_APIError() = runBlockingTest {
        val testHeaders = Headers.Builder().build()

        every {
            mockOkHttpClient.newCall(any())
        } answers {
            val request: Request = arg(0)
            assertEquals("POST", request.method)
            assertEquals(FULL_URL, request.url.toString())
            assertEquals(request.headers, testHeaders)
            assert(request.isHttps)

            val bufferedSink = Buffer()
            request.body!!.writeTo(bufferedSink)
            assertEquals(SERIALIZED_REQUEST_BODY, bufferedSink.readUtf8())
            assertEquals(request.body!!.contentType().toString(), "application/json; charset=utf-8")
            mockCall
        }

        every { mockCall.execute() } returns mockResponse
        every { mockResponse.code } returns 200
        every { mockResponse.body } returns mockResponseBody
        every { mockResponseBody.string() } returns SERIALIZED_RESPONSE_BODY_ERROR

        val apiError = assertThrows<APIError> {
            runBlockingTest {
                httpRequester.request<VerifyRequest, CreateUserResponse>(
                    FULL_URL,
                    HTTPRequesterType.HTTPMethod.POST,
                    testHeaders,
                    REQUEST_BODY
                )
            }
        }

        assertEquals(apiError.type, APIError.ErrorType.NOT_FOUND)
        assertEquals(apiError.description, API_ERROR_DESCRIPTION)

        verify(exactly = 1) { mockCall.execute() }
        confirmVerified(mockCall)
    }

    @Test
    fun put_Body_EmptyResponse() = runBlockingTest {
        val testHeaders = Headers.Builder().build()

        every { mockOkHttpClient.newCall(any()) } answers {
            val request: Request = arg(0)
            assertEquals("PUT", request.method)
            assertEquals(FULL_URL, request.url.toString())
            assertEquals(request.headers, testHeaders)
            assert(request.isHttps)

            val bufferedSink = Buffer()
            request.body!!.writeTo(bufferedSink)
            assertEquals(SERIALIZED_REQUEST_BODY, bufferedSink.readUtf8())
            assertEquals(request.body!!.contentType().toString(), "application/json; charset=utf-8")

            mockCall
        }

        every { mockCall.execute() } returns mockResponse
        every { mockResponse.code } returns 200

        httpRequester.request<VerifyRequest, Unit>(
            FULL_URL,
            HTTPRequesterType.HTTPMethod.PUT,
            testHeaders,
            REQUEST_BODY
        )

        verify(exactly = 1) { mockCall.execute() }
        confirmVerified(mockCall)
    }

    @Test
    fun delete_WithoutBody_WithoutResponse() = runBlockingTest {
        val testHeaders = Headers.Builder().build()

        every { mockOkHttpClient.newCall(any()) } answers {
            val request: Request = arg(0)
            assertEquals("DELETE", request.method)
            assertEquals(FULL_URL, request.url.toString())
            assertEquals(request.headers, testHeaders)
            assert(request.isHttps)

            mockCall
        }

        every { mockCall.execute() } returns mockResponse
        every { mockResponse.code } returns 200

        httpRequester.request<Unit>(
            FULL_URL,
            HTTPRequesterType.HTTPMethod.DELETE,
            testHeaders
        )

        verify(exactly = 1) { mockCall.execute() }
        confirmVerified(mockCall)
    }

    @Test
    fun apiError() = runBlockingTest {
        val testHeaders = Headers.Builder().add("TestHeaderName", "TestHeaderValue").build()
        val errorDescription = "Encountered an invalid value."
        val serializedAPIError = """{"type": "invalidValue", "description": "$errorDescription"}"""

        every { mockOkHttpClient.newCall(any()) } answers {
            val request: Request = arg(0)

            assertEquals("GET", request.method)
            assertEquals(FULL_URL, request.url.toString())
            assertEquals(request.headers, testHeaders)
            assert(request.isHttps)

            mockCall
        }

        every { mockCall.execute() } returns mockResponse
        every { mockResponse.code } returns 404
        every { mockResponse.body } returns mockResponseBody
        every { mockResponseBody.string() } returns serializedAPIError

        val exception = assertThrows<APIError> {
            runBlockingTest {
                httpRequester.request(
                    FULL_URL,
                    HTTPRequesterType.HTTPMethod.GET,
                    testHeaders
                )
            }
        }

        assertEquals(exception.type, APIError.ErrorType.INVALID_VALUE)
        assertEquals(exception.description, errorDescription)

        verify(exactly = 1) { mockCall.execute() }
        confirmVerified(mockCall)
    }

    @Test
    fun emptyErrorResponse() = runBlockingTest {
        val testHeaders = Headers.Builder().add("TestHeaderName", "TestHeaderValue").build()

        every { mockOkHttpClient.newCall(any()) } answers {
            val request: Request = arg(0)

            assertEquals("GET", request.method)
            assertEquals(FULL_URL, request.url.toString())
            assertEquals(request.headers, testHeaders)
            assert(request.isHttps)

            mockCall
        }

        every { mockCall.execute() } returns mockResponse
        every { mockResponse.code } returns 404
        every { mockResponse.body } returns null

        val exception = assertThrows<HTTPRequesterException.EmptyErrorResponse> {
            runBlockingTest {
                httpRequester.request(
                    FULL_URL,
                    HTTPRequesterType.HTTPMethod.GET,
                    testHeaders
                )
            }
        }

        assertEquals(exception.statusCode, 404)

        verify(exactly = 1) { mockCall.execute() }
        confirmVerified(mockCall)
    }

    @Test
    fun body_missingResponse() = runBlockingTest {
        val testHeaders = Headers.Builder().build()

        every { mockOkHttpClient.newCall(any()) } answers {
            val request: Request = arg(0)
            assertEquals("DELETE", request.method)
            assertEquals(FULL_URL, request.url.toString())
            assertEquals(request.headers, testHeaders)
            assert(request.isHttps)

            assertNotNull(request.body)

            mockCall
        }

        every { mockCall.execute() } returns mockResponse
        every { mockResponse.code } returns 200
        every { mockResponse.body } returns null

        assertThrows<HTTPRequesterException.EmptyResponse> {
            runBlockingTest {
                httpRequester.request<VerifyRequest, CreateUserResponse>(
                    FULL_URL,
                    HTTPRequesterType.HTTPMethod.DELETE,
                    testHeaders,
                    REQUEST_BODY
                )
            }
        }

        verify(exactly = 1) { mockCall.execute() }
        confirmVerified(mockCall)
    }

    @Test
    fun withoutBody_missingResponse() = runBlockingTest {
        val testHeaders = Headers.Builder().build()

        every { mockOkHttpClient.newCall(any()) } answers {
            val request: Request = arg(0)
            assertEquals("DELETE", request.method)
            assertEquals(FULL_URL, request.url.toString())
            assertEquals(request.headers, testHeaders)
            assert(request.isHttps)

            mockCall
        }

        every { mockCall.execute() } returns mockResponse
        every { mockResponse.code } returns 200
        every { mockResponse.body } returns null

        assertThrows<HTTPRequesterException.EmptyResponse> {
            runBlockingTest {
                httpRequester.request<CreateUserResponse>(
                    FULL_URL,
                    HTTPRequesterType.HTTPMethod.DELETE,
                    testHeaders
                )
            }
        }

        verify(exactly = 1) { mockCall.execute() }
        confirmVerified(mockCall)
    }
}