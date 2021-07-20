package tice.managers.messaging

import tice.models.messaging.conversation.ConversationInvitation
import tice.crypto.CryptoManagerType
import io.mockk.*
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tice.crypto.AuthManagerType
import tice.managers.SignedInUserManagerType
import tice.models.SignedInUser
import tice.models.messaging.Envelope
import tice.models.messaging.Payload
import tice.models.messaging.PayloadContainer
import tice.models.messaging.ResetConversation
import tice.utility.dataFromBase64
import tice.utility.provider.CoroutineContextProviderType
import tice.utility.toBase64String
import java.nio.charset.Charset
import java.util.*

internal class WebSocketReceiverTest {

    private lateinit var webSocketReceiver: WebSocketReceiver

    private val mockOkHttpClient: OkHttpClient = mockk(relaxUnitFun = true)
    private val mockSignedInUserManager: SignedInUserManagerType = mockk(relaxUnitFun = true)
    private val mockSignedInUser: SignedInUser = mockk(relaxUnitFun = true)
    private val mockAuthManager: AuthManagerType = mockk(relaxUnitFun = true)
    private val mockPostOffice: PostOfficeType = mockk(relaxUnitFun = true)
    private val mockCoroutineContextProvider: CoroutineContextProviderType = mockk(relaxUnitFun = true)
    private val mockSocket: WebSocket = mockk(relaxUnitFun = true)

    val EXPECTED_CLOSE_CODE = 1000
    private val TEST_URL = "http://example.com/"
    private val TEST_RETRY_DELAY = 20000L

    private val TEST_SIGNED_IN_USER_ID = UUID.randomUUID()
    private val TEST_PRIVATE_SIGNING_KEY = "privateSigningKey".toByteArray()

    private val TEST_AUTH_HEADER = "authHeader"

    val TEST_ENVELOPE = Envelope(
        id = UUID.randomUUID(),
        senderId = UUID.randomUUID(),
        senderServerSignedMembershipCertificate = "senderServerCert",
        receiverServerSignedMembershipCertificate = "receiverServerCert",
        timestamp = Date(),
        serverTimestamp = Date(),
        collapseId = "collapseId",
        conversationInvitation = ConversationInvitation(
            "identityKey".toByteArray(),
            "ephemeralKey".toByteArray(),
            "usedOneTimeOreKey".toByteArray()
        ),
        payloadContainer = PayloadContainer(Payload.PayloadType.ResetConversationV1, ResetConversation)
    )

    lateinit var TEST_ENVELOPE_STRING: String

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        webSocketReceiver = WebSocketReceiver(
            mockOkHttpClient,
            mockSignedInUserManager,
            mockAuthManager,
            mockPostOffice,
            mockCoroutineContextProvider,
            TEST_URL,
            TEST_RETRY_DELAY
        )

        every { mockSignedInUserManager.signedInUser } returns mockSignedInUser
        every { mockSignedInUser.userId } returns TEST_SIGNED_IN_USER_ID
        every { mockSignedInUser.privateSigningKey } returns TEST_PRIVATE_SIGNING_KEY

        every { mockAuthManager.generateAuthHeader(TEST_PRIVATE_SIGNING_KEY, TEST_SIGNED_IN_USER_ID) } returns TEST_AUTH_HEADER

        mockkStatic("tice.utility.Base64ConvertFunctionsKt")
        val slotString = slot<String>()
        val slotByteArray = slot<ByteArray>()
        every { capture(slotString).dataFromBase64() } answers { slotString.captured.toByteArray() }
        every { capture(slotByteArray).toBase64String() } answers { String(slotByteArray.captured) }

        TEST_ENVELOPE_STRING = Json.encodeToString(Envelope.serializer(), TEST_ENVELOPE)
    }

    @Test
    fun `connect assert request`() = runBlockingTest {
        val capRequest = slot<Request>()
        val capListener = slot<WebSocketListener>()

        every { mockOkHttpClient.newWebSocket(request = capture(capRequest), listener = capture(capListener)) } returns mockSocket
        webSocketReceiver.connect()

        val capturedRequest = capRequest.captured

        Assertions.assertEquals(TEST_URL, capturedRequest.url.toString())
        Assertions.assertNotNull(capturedRequest.headers["X-Authorization"])
        Assertions.assertEquals(TEST_AUTH_HEADER, capturedRequest.headers["X-Authorization"])
    }

    @Nested
    inner class WebSocketListenerTest {

        @Test
        fun `onMessage String`() = runBlockingTest {
            val capListener = slot<WebSocketListener>()

            every { mockOkHttpClient.newWebSocket(request = any(), listener = capture(capListener)) } returns mockSocket

            webSocketReceiver.connect()

            val capturedListener = capListener.captured

            capturedListener.onMessage(mockSocket, TEST_ENVELOPE_STRING)

            verify(exactly = 1) { mockPostOffice.receiveEnvelope(TEST_ENVELOPE) }
            confirmVerified(mockPostOffice)
        }

        @Test
        fun `onMessage ByteString`() = runBlockingTest {
            val mockByteString = mockk<ByteString>()
            val capListener = slot<WebSocketListener>()

            every { mockByteString.string(Charset.defaultCharset()) } returns TEST_ENVELOPE_STRING
            every { mockOkHttpClient.newWebSocket(request = any(), listener = capture(capListener)) } returns mockSocket

            webSocketReceiver.connect()

            val capturedListener = capListener.captured

            capturedListener.onMessage(mockSocket, mockByteString)

            verify(exactly = 1) { mockPostOffice.receiveEnvelope(TEST_ENVELOPE) }
            confirmVerified(mockPostOffice)
        }

        val testScope = TestCoroutineScope(TestCoroutineDispatcher())

        @Test
        fun `onMessage OnFailure`() = runBlockingTest {
            val capListener = slot<WebSocketListener>()
            val testDispatcher = TestCoroutineDispatcher()

            every { mockOkHttpClient.newWebSocket(request = any(), listener = capture(capListener)) } returns mockSocket
            every { mockCoroutineContextProvider.IO } returns testDispatcher

            webSocketReceiver.connect()

            val capturedListener = capListener.captured
            capturedListener.onFailure(mockSocket, mockk(), mockk())

            testDispatcher.advanceUntilIdle()

            verify(exactly = 2) { mockOkHttpClient.newWebSocket(any(), any()) }
            confirmVerified(mockPostOffice)
        }

        @Test
        fun `onMessage OnFailure twice`() = runBlockingTest {
            val capListener = slot<WebSocketListener>()
            val testDispatcher = TestCoroutineDispatcher()

            every { mockOkHttpClient.newWebSocket(request = any(), listener = capture(capListener)) } returns mockSocket
            every { mockCoroutineContextProvider.IO } returns testDispatcher

            webSocketReceiver.connect()

            val capturedListener = capListener.captured
            capturedListener.onFailure(mockSocket, mockk(), mockk())

            testDispatcher.advanceUntilIdle()

            capturedListener.onFailure(mockSocket, mockk(), mockk())
            testDispatcher.advanceUntilIdle()

            verify(exactly = 3) { mockOkHttpClient.newWebSocket(any(), any()) }
            confirmVerified(mockPostOffice)
        }

        @Test
        fun `onMessage OnFailure but disconnect before delay finishes`() = runBlockingTest {
            val capListener = slot<WebSocketListener>()
            val testDispatcher = TestCoroutineDispatcher()

            every { mockOkHttpClient.newWebSocket(request = any(), listener = capture(capListener)) } returns mockSocket
            every { mockCoroutineContextProvider.IO } returns testDispatcher
            every { mockSocket.close(EXPECTED_CLOSE_CODE, any()) } returns true

            webSocketReceiver.connect()

            val capturedListener = capListener.captured
            capturedListener.onFailure(mockSocket, mockk(), mockk())

            webSocketReceiver.disconnect()

            testDispatcher.advanceUntilIdle()

            verify(exactly = 1) { mockOkHttpClient.newWebSocket(any(), any()) }
            confirmVerified(mockPostOffice)
        }


        @Test
        fun `onMessage OnFailure but connect before delay finishes`() = runBlockingTest {
            val capListener = slot<WebSocketListener>()
            val testDispatcher = TestCoroutineDispatcher()

            every { mockOkHttpClient.newWebSocket(request = any(), listener = capture(capListener)) } returns mockSocket
            every { mockCoroutineContextProvider.IO } returns testDispatcher
            every { mockSocket.close(EXPECTED_CLOSE_CODE, any()) } returns true

            webSocketReceiver.connect()

            val capturedListener = capListener.captured
            capturedListener.onOpen(mockSocket, mockk())

            capturedListener.onFailure(mockSocket, mockk(), mockk())

            capturedListener.onOpen(mockSocket, mockk())

            testDispatcher.advanceUntilIdle()

            verify(exactly = 1) { mockOkHttpClient.newWebSocket(any(), any()) }
            confirmVerified(mockPostOffice)
        }
    }

    @Nested
    inner class Disconnect {

        @Test
        fun `without Socket`() {

            webSocketReceiver.disconnect()

            verify(exactly = 0) { mockSocket.close(EXPECTED_CLOSE_CODE, null) }
            confirmVerified(mockSocket)
        }

        @Test
        fun `with Socket`() {
            val listenerSlot = slot<WebSocketListener>()

            every { mockOkHttpClient.newWebSocket(request = any(), listener = capture(listenerSlot)) } returns mockSocket
            webSocketReceiver.connect()

            listenerSlot.captured.onOpen(mockSocket, mockk())

            every { mockSocket.close(any(), any()) } returns true

            webSocketReceiver.disconnect()

            verify(exactly = 1) { mockSocket.close(EXPECTED_CLOSE_CODE, null) }
            confirmVerified(mockSocket)
        }
    }
}