package tice.managers.messaging

import com.google.firebase.messaging.RemoteMessage
import io.mockk.*
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tice.managers.messaging.notificationHandler.VerifyDeviceHandlerType
import tice.models.messaging.Envelope
import tice.models.messaging.Payload
import tice.models.messaging.PayloadContainer
import tice.models.messaging.ResetConversation
import java.util.*

internal class FirebaseReceiverServiceTest {

    private lateinit var firebaseReceiverService: FirebaseReceiverService

    private val mockPostOffice: PostOfficeType = mockk(relaxUnitFun = true)
    private val mockVerifyDeviceHandler: VerifyDeviceHandlerType = mockk(relaxUnitFun = true)

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        firebaseReceiverService = FirebaseReceiverService().apply {
            postOffice = mockPostOffice
            verifyDeviceHandler = mockVerifyDeviceHandler
        }
    }

    @Test
    fun onNewToken() = runBlockingTest {
        val TEST_NEW_TOKEN = "newToken"

        firebaseReceiverService.onNewToken(TEST_NEW_TOKEN)

        verify(exactly = 1) { mockVerifyDeviceHandler.startUpdatingDeviceId(TEST_NEW_TOKEN) }
    }

    @Test
    fun onMessageReceived() {
        val mockRemoteMessage = mockk<RemoteMessage>()
        val TEST_ENVELOPE = Envelope(
            id = UUID.randomUUID(),
            senderId = UUID.randomUUID(),
            senderServerSignedMembershipCertificate = "senderServerCert",
            receiverServerSignedMembershipCertificate = "receiverServerCert",
            timestamp = Date(),
            serverTimestamp = Date(),
            collapseId = "collapseId",
            conversationInvitation = null,
            payloadContainer = PayloadContainer(Payload.PayloadType.ResetConversationV1, ResetConversation)
        )

        val envelopeString = Json.encodeToString(Envelope.serializer(), TEST_ENVELOPE)

        every { mockRemoteMessage.data } returns mapOf(Pair("payload", envelopeString))

        firebaseReceiverService.onMessageReceived(mockRemoteMessage)

        verify(exactly = 1) { mockPostOffice.receiveEnvelope(TEST_ENVELOPE) }
        confirmVerified(mockPostOffice)
    }
}