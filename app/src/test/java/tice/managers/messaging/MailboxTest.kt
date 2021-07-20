package tice.managers.messaging

import tice.crypto.CryptoManagerType
import tice.models.Membership
import io.mockk.*
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tice.backend.BackendType
import tice.managers.ConversationManagerType
import tice.managers.SignedInUserManagerType
import tice.models.SignedInUser
import tice.models.UserId
import tice.models.messaging.*
import tice.utility.serializer.PayloadContainerSerializer
import java.util.*

internal class MailboxTest {

    private lateinit var mailBox: Mailbox

    private val mockBackend: BackendType = mockk(relaxUnitFun = true)
    private val mockCryptoManager: CryptoManagerType = mockk(relaxUnitFun = true)
    private val mockSignedInUserManager: SignedInUserManagerType = mockk(relaxUnitFun = true)
    private val mockConversationManager: ConversationManagerType = mockk(relaxUnitFun = true)

    private val mockSignedInUser: SignedInUser = mockk()
    private val TEST_SIGNED_IN_USER_ID = UUID.randomUUID()

    private val mockMembership1: Membership = mockk()
    private val TEST_MEMBER_1_ID = UUID.randomUUID()
    private val TEST_SERVER_SIGNED_MEMBER_1_CERT = "serverSignedMember1Cert"
    private val TEST_MEMBER_1_CIPHERTEXT = "member1Ciphertext".toByteArray()

    private val mockMembership2: Membership = mockk()
    private val TEST_MEMBER_2_ID = UUID.randomUUID()
    private val TEST_SERVER_SIGNED_MEMBER_2_CERT = "serverSignedMember2Cert"
    private val TEST_MEMBER_2_CIPHERTEXT = "member2Ciphertext".toByteArray()

    private val TEST_TIME_TO_LIVE = 1000L

    private val TEST_PayloadContainer = PayloadContainer(Payload.PayloadType.ResetConversationV1, ResetConversation)
    private val TEST_MEMBER_SET: Set<Membership> = setOf(mockMembership1, mockMembership2)
    private val TEST_SERVER_SIGNED_MEMBER_CERT = "serverSignedMemberCert"
    private val TEST_PRIORITY = MessagePriority.Background
    private val TEST_COLLAPSE_ID = "collapsId"

    private val TEST_SECRET_KEY = "SecretKey".toByteArray()


    @BeforeEach
    fun setUp() {
        clearAllMocks()

        mailBox = Mailbox(mockBackend, mockCryptoManager, mockSignedInUserManager, mockConversationManager, TEST_TIME_TO_LIVE)

        every { mockSignedInUserManager.signedInUser } returns mockSignedInUser
        every { mockSignedInUser.userId } returns TEST_SIGNED_IN_USER_ID

        every { mockMembership1.userId } returns TEST_MEMBER_1_ID
        every { mockMembership1.serverSignedMembershipCertificate } returns TEST_SERVER_SIGNED_MEMBER_1_CERT
        every { mockMembership2.userId } returns TEST_MEMBER_2_ID
        every { mockMembership2.serverSignedMembershipCertificate } returns TEST_SERVER_SIGNED_MEMBER_2_CERT

        every { mockCryptoManager.encrypt(any()) } answers { Pair(arg(0), TEST_SECRET_KEY) }

        coEvery { mockConversationManager.encrypt(TEST_SECRET_KEY, TEST_MEMBER_1_ID, true) } returns TEST_MEMBER_1_CIPHERTEXT
        coEvery { mockConversationManager.encrypt(TEST_SECRET_KEY, TEST_MEMBER_2_ID, true) } returns TEST_MEMBER_2_CIPHERTEXT

        coEvery { mockConversationManager.conversationInvitation(any(), any()) } returns null
    }

    @Test
    fun `init and sets the Delegate in the ConversationManager`() = runBlockingTest {
        mailBox.registerForDelegate()

        verify(exactly = 1) { mockConversationManager.setDelegate(mailBox) }
    }

    @Nested
    inner class SendToGroup {
        @Test
        fun `with empty members set`() = runBlockingTest {
            mailBox.sendToGroup(TEST_PayloadContainer, emptySet(), TEST_SERVER_SIGNED_MEMBER_CERT, TEST_PRIORITY, TEST_COLLAPSE_ID)

            verify(exactly = 1) { mockSignedInUserManager.signedInUser }
            confirmVerified(mockBackend, mockCryptoManager, mockSignedInUserManager, mockConversationManager)
        }

        @Test
        fun `success`() = runBlockingTest {
            val payloadContainerString = Json.encodeToString(PayloadContainerSerializer, TEST_PayloadContainer)

            val expectedRecipientSet = setOf(
                Recipient(TEST_MEMBER_1_ID, TEST_SERVER_SIGNED_MEMBER_1_CERT, TEST_MEMBER_1_CIPHERTEXT, null),
                Recipient(TEST_MEMBER_2_ID, TEST_SERVER_SIGNED_MEMBER_2_CERT, TEST_MEMBER_2_CIPHERTEXT, null)
            )

            var didAssertBackendCall = false
            coEvery { mockBackend.message(any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
                Assertions.assertEquals(TEST_SIGNED_IN_USER_ID, arg(1) as UserId)
                Assertions.assertTrue(Date().time + 500 >= arg(2) as Long)
                Assertions.assertTrue(Date().time - 500 <= arg(2) as Long)
                Assertions.assertArrayEquals(payloadContainerString.toByteArray(), arg(3) as ByteArray)
                Assertions.assertEquals(TEST_SERVER_SIGNED_MEMBER_CERT, arg(4) as String)
                Assertions.assertEquals(expectedRecipientSet, arg(5) as Set<Recipient>)
                Assertions.assertEquals(TEST_PRIORITY, arg(6) as MessagePriority)
                Assertions.assertEquals(TEST_COLLAPSE_ID, arg(7) as String)
                Assertions.assertEquals(TEST_TIME_TO_LIVE, arg(8) as Long)

                didAssertBackendCall = true
            }

            mailBox.sendToGroup(TEST_PayloadContainer, TEST_MEMBER_SET, TEST_SERVER_SIGNED_MEMBER_CERT, TEST_PRIORITY, TEST_COLLAPSE_ID)

            Assertions.assertTrue(didAssertBackendCall)
            coVerify(exactly = 1) { mockBackend.message(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        }
    }

    @Test
    fun sendResetReply() = runBlockingTest {
        val TEST_RECEIVER_CERT = "receiverCert"
        val TEST_SENDER_CERT = "senderCert"
        val payloadContainerString = Json.encodeToString(PayloadContainerSerializer, TEST_PayloadContainer)

        val expectedRecipientSet = setOf(Recipient(TEST_MEMBER_1_ID, TEST_RECEIVER_CERT, TEST_MEMBER_1_CIPHERTEXT, null))

        var didAssertBackendCall = false
        coEvery { mockBackend.message(any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            Assertions.assertEquals(TEST_SIGNED_IN_USER_ID, arg(1) as UserId)
            Assertions.assertTrue(Date().time + 500 >= arg(2) as Long)
            Assertions.assertTrue(Date().time - 500 <= arg(2) as Long)
            Assertions.assertArrayEquals(payloadContainerString.toByteArray(), arg(3) as ByteArray)
            Assertions.assertEquals(TEST_SENDER_CERT, arg(4) as String)
            Assertions.assertEquals(expectedRecipientSet, arg(5) as Set<Recipient>)
            Assertions.assertEquals(TEST_PRIORITY, arg(6) as MessagePriority)
            Assertions.assertEquals(TEST_COLLAPSE_ID, arg(7) as String)
            Assertions.assertEquals(TEST_TIME_TO_LIVE, arg(8) as Long)

            didAssertBackendCall = true
        }

        coEvery { mockConversationManager.encrypt(TEST_SECRET_KEY, TEST_MEMBER_1_ID, false) } returns TEST_MEMBER_1_CIPHERTEXT

        mailBox.sendResetReply(TEST_MEMBER_1_ID, TEST_RECEIVER_CERT, TEST_SENDER_CERT, TEST_COLLAPSE_ID)

        Assertions.assertTrue(didAssertBackendCall)
        coVerify(exactly = 1) { mockBackend.message(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        confirmVerified(mockBackend)
    }
}