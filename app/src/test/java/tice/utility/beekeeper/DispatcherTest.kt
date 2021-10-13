package tice.utility.beekeeper

import io.mockk.*
import kotlinx.coroutines.test.runBlockingTest
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import org.junit.Assert
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.*

internal class DispatcherTest {

    private lateinit var dispatcher: Dispatcher

    private val mockOkHttpClient: OkHttpClient = mockk(relaxUnitFun = true)

    @BeforeEach
    fun before() {
        clearAllMocks()

        dispatcher = Dispatcher(mockOkHttpClient, "https://example.com", 1000, 10, "1234")
    }

    @Nested
    inner class TrackEvent {
        @Test
        fun `test dispatch`() = runBlockingTest {

            val mockCall = mockk<Call>(relaxUnitFun = true)
            every { mockOkHttpClient.newCall(any()) } returns mockCall

            val mockResponse = mockk<Response>(relaxUnitFun = true)
            every { mockCall.execute() } returns mockResponse
            every { mockResponse.code } returns 200

            val event = Event(
                "1234",
                "TestProduct",
                Date(1524268799_000),
                "TestName",
                "TestGroup",
                "TestDetail",
                1.0,
                "Previous",
                "2000-01-02",
                "2000-01-01",
                emptyList()
            )

            dispatcher.dispatch(listOf(event))

            val slot = slot<Request>()
            verify(exactly = 1) { mockOkHttpClient.newCall(capture(slot)) }
            verify(exactly = 1) { mockCall.execute() }

            Assertions.assertEquals("POST", slot.captured.method)

            val serializedBody = """
                [{"id":"1234","p":"TestProduct","t":"2018-04-20T23:59:59.000+0000","name":"TestName","group":"TestGroup","detail":"TestDetail","value":1.0,"prev":"Previous","last":"2000-01-02","install":"2000-01-01","custom":[]}]
            """.trimIndent()

            val request = slot.captured
            val bufferedSink = Buffer()
            request.body!!.writeTo(bufferedSink)
            Assert.assertEquals(serializedBody, bufferedSink.readUtf8())
            Assert.assertEquals(request.body!!.contentType().toString(), "application/json; charset=utf-8")
        }

        @Test
        fun `test signature`() = runBlockingTest {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = "body".toRequestBody(mediaType)

            val headers = dispatcher.signature("POST", requestBody, "/path", Date(0L))

            Assertions.assertEquals("1970-01-01T00:00:00Z", headers["authorization-date"])
            Assertions.assertEquals("cyzE5bhmqPtV+OdmOGdA24p14Zbxw72RmFLOLJHUqQo=", headers["authorization"])
        }
    }

}
