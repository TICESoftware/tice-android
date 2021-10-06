package tice.managers.messaging

import io.mockk.*
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tice.backend.BackendType
import tice.helper.joinAllChildren
import tice.managers.messaging.notificationHandler.PayloadPreprocessor
import tice.managers.messaging.notificationHandler.PayloadReceiver
import tice.models.messaging.*
import tice.models.messaging.conversation.ConversationInvitation
import tice.models.responses.GetMessagesResponse
import tice.utility.provider.CoroutineContextProviderType
import java.util.*

internal class PostOfficeTest {

    private lateinit var postOffice: PostOffice

    private val mockCoroutineContextProvider: CoroutineContextProviderType = mockk(relaxUnitFun = true)
    private val mockBackend: BackendType = mockk(relaxUnitFun = true)

    private lateinit var testJob: CompletableJob

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        testJob = Job()
        postOffice = PostOffice(mockCoroutineContextProvider, mockBackend)

        every { mockCoroutineContextProvider.IO } returns Dispatchers.IO + testJob
    }

    @Test
    fun registerPayloadPreprocessor() = runBlockingTest {
        val mockPayloadProcessor = mockk<PayloadPreprocessor>()

        postOffice.registerPayloadPreprocessor(Payload.PayloadType.ResetConversationV1, mockPayloadProcessor)
    }

    @Test
    fun registerEnvelopeReceiver() = runBlockingTest {
        val mockEnvelopeReceiver = mockk<PayloadReceiver>()

        postOffice.registerEnvelopeReceiver(Payload.PayloadType.ResetConversationV1, mockEnvelopeReceiver)
    }

    @Test
    fun receiveEnvelope() = runBlocking {
        val mockEnvelopeReceiver = mockk<PayloadReceiver>()
        val mockPayloadProcessor = mockk<PayloadPreprocessor>()

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
        val metaInfo = PayloadMetaInfo(
            TEST_ENVELOPE.senderId,
            TEST_ENVELOPE.timestamp,
            TEST_ENVELOPE.collapseId,
            TEST_ENVELOPE.senderServerSignedMembershipCertificate,
            TEST_ENVELOPE.receiverServerSignedMembershipCertificate,
            false,
            TEST_ENVELOPE.conversationInvitation
        )

        val expectedPayloadContainerBundle = PayloadContainerBundle(Payload.PayloadType.ResetConversationV1, ResetConversation, metaInfo)
        val expectedPayloadContainerBundleNew =
            PayloadContainerBundle(Payload.PayloadType.UserUpdateV1, UserUpdate(UUID.randomUUID()), metaInfo)

        coEvery { mockPayloadProcessor.preprocess(expectedPayloadContainerBundle) } returns expectedPayloadContainerBundleNew

        postOffice.registerEnvelopeReceiver(Payload.PayloadType.UserUpdateV1, mockEnvelopeReceiver)
        postOffice.registerPayloadPreprocessor(Payload.PayloadType.ResetConversationV1, mockPayloadProcessor)

        postOffice.receiveEnvelope(TEST_ENVELOPE)

        testJob.joinAllChildren()

        coVerify(exactly = 1) { mockEnvelopeReceiver.handlePayloadContainerBundle(any()) }
        coVerify(exactly = 1) { mockPayloadProcessor.preprocess(expectedPayloadContainerBundle) }
        confirmVerified(mockEnvelopeReceiver)
    }

    @Test
    fun fetchMessages() = runBlockingTest {
        val mockEnvelopeReceiver = mockk<PayloadReceiver>()
        val mockPayloadProcessor = mockk<PayloadPreprocessor>()

        val TEST_ENVELOPE_1 = Envelope(
            id = UUID.randomUUID(),
            senderId = UUID.randomUUID(),
            senderServerSignedMembershipCertificate = "senderServerCert_1",
            receiverServerSignedMembershipCertificate = "receiverServerCert_1",
            timestamp = Date(),
            serverTimestamp = Date(),
            collapseId = "collapseId_1",
            conversationInvitation = ConversationInvitation(
                "identityKey_1".toByteArray(),
                "ephemeralKey_1".toByteArray(),
                "usedOneTimeOreKey_1".toByteArray()
            ),
            payloadContainer = PayloadContainer(Payload.PayloadType.ResetConversationV1, ResetConversation)
        )


        val TEST_ENVELOPE_2 = Envelope(
            id = UUID.randomUUID(),
            senderId = UUID.randomUUID(),
            senderServerSignedMembershipCertificate = "senderServerCert_2",
            receiverServerSignedMembershipCertificate = "receiverServerCert_2",
            timestamp = Date(),
            serverTimestamp = Date(),
            collapseId = "collapseId_2",
            conversationInvitation = ConversationInvitation(
                "identityKey_2".toByteArray(),
                "ephemeralKey_2".toByteArray(),
                "usedOneTimeOreKey_2".toByteArray()
            ),
            payloadContainer = PayloadContainer(Payload.PayloadType.ResetConversationV1, ResetConversation)
        )

        val metaInfo_1 = PayloadMetaInfo(
            TEST_ENVELOPE_1.senderId,
            TEST_ENVELOPE_1.timestamp,
            TEST_ENVELOPE_1.collapseId,
            TEST_ENVELOPE_1.senderServerSignedMembershipCertificate,
            TEST_ENVELOPE_1.receiverServerSignedMembershipCertificate,
            false,
            TEST_ENVELOPE_1.conversationInvitation
        )

        val metaInfo_2 = PayloadMetaInfo(
            TEST_ENVELOPE_2.senderId,
            TEST_ENVELOPE_2.timestamp,
            TEST_ENVELOPE_2.collapseId,
            TEST_ENVELOPE_2.senderServerSignedMembershipCertificate,
            TEST_ENVELOPE_2.receiverServerSignedMembershipCertificate,
            false,
            TEST_ENVELOPE_2.conversationInvitation
        )

        val expectedPayloadContainerBundle_1 = PayloadContainerBundle(Payload.PayloadType.ResetConversationV1, ResetConversation, metaInfo_1)
        val expectedPayloadContainerBundle_2 = PayloadContainerBundle(Payload.PayloadType.ResetConversationV1, ResetConversation, metaInfo_2)
        val expectedPayloadContainerBundleNew_1 = PayloadContainerBundle(Payload.PayloadType.UserUpdateV1, UserUpdate(UUID.randomUUID()), metaInfo_1)
        val expectedPayloadContainerBundleNew_2 = PayloadContainerBundle(Payload.PayloadType.UserUpdateV1, UserUpdate(UUID.randomUUID()), metaInfo_2)

        val backendMessage = GetMessagesResponse(arrayOf(TEST_ENVELOPE_1, TEST_ENVELOPE_2))

        coEvery { mockPayloadProcessor.preprocess(expectedPayloadContainerBundle_1) } returns expectedPayloadContainerBundleNew_1
        coEvery { mockPayloadProcessor.preprocess(expectedPayloadContainerBundle_2) } returns expectedPayloadContainerBundleNew_2

        coEvery { mockBackend.getMessages() } returns backendMessage

        postOffice.registerPayloadPreprocessor(Payload.PayloadType.ResetConversationV1, mockPayloadProcessor)
        postOffice.registerEnvelopeReceiver(Payload.PayloadType.UserUpdateV1, mockEnvelopeReceiver)

        postOffice.fetchMessages()

        coVerify(exactly = 2) { mockEnvelopeReceiver.handlePayloadContainerBundle(any()) }
        coVerify(exactly = 1) { mockPayloadProcessor.preprocess(expectedPayloadContainerBundle_1) }
        coVerify(exactly = 1) { mockPayloadProcessor.preprocess(expectedPayloadContainerBundle_2) }
        confirmVerified(mockEnvelopeReceiver)
    }
}
