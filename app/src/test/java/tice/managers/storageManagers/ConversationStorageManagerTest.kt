package tice.managers.storageManagers

import io.mockk.*
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tice.models.database.ConversationInterface
import tice.models.database.ReceivedReset
import tice.models.messaging.conversation.ConversationInvitation
import tice.models.messaging.conversation.InboundConversationInvitation
import tice.models.messaging.conversation.InvalidConversation
import tice.models.messaging.conversation.OutboundConversationInvitation
import java.util.*

internal class ConversationStorageManagerTest {

    private lateinit var conversationStorageManager: ConversationStorageManager

    private val mockAppDatabase: AppDatabase = mockk(relaxUnitFun = true)
    private val mockConversationInterface: ConversationInterface = mockk(relaxUnitFun = true)

    private val TEST_USER_ID = UUID.randomUUID()
    private val TEST_CONVERSATION_ID = UUID.randomUUID()

    private val TEST_PUBLIC_KEY = "publicKey".toByteArray()
    private val TEST_EPH_KEY = "ephKey".toByteArray()
    private val TEST_ON_TIME_KEY = "usedOnTimePrekey".toByteArray()
    val TEST_DATE = Date()

    val TEST_CONVERSATION_INVITATION = ConversationInvitation(
        TEST_PUBLIC_KEY,
        TEST_EPH_KEY,
        TEST_ON_TIME_KEY
    )

    val TEST_OUTBOUND = OutboundConversationInvitation(
        TEST_USER_ID,
        TEST_CONVERSATION_ID,
        TEST_PUBLIC_KEY,
        TEST_EPH_KEY,
        TEST_ON_TIME_KEY
    )

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        every { mockAppDatabase.conversationStateInterface() } returns mockConversationInterface

        conversationStorageManager = ConversationStorageManager(mockAppDatabase)
    }

    @Test
    fun storeOutboundConversationInvitation() = runBlockingTest {
        conversationStorageManager.storeOutboundConversationInvitation(
            TEST_USER_ID,
            TEST_CONVERSATION_ID,
            TEST_CONVERSATION_INVITATION
        )

        val expected = OutboundConversationInvitation(
            TEST_USER_ID,
            TEST_CONVERSATION_ID,
            TEST_PUBLIC_KEY,
            TEST_EPH_KEY,
            TEST_ON_TIME_KEY
        )

        coVerify(exactly = 1) { mockConversationInterface.insert(expected) }
    }

    @Test
    fun outboundConversationInvitation() = runBlockingTest {
        coEvery { mockConversationInterface.outboundConversationInvitation(TEST_USER_ID, TEST_CONVERSATION_ID) } returns TEST_OUTBOUND

        val result = conversationStorageManager.outboundConversationInvitation(TEST_USER_ID, TEST_CONVERSATION_ID)

        assertEquals(TEST_OUTBOUND.conversationInvitation(), result)

        coVerify(exactly = 1) { mockConversationInterface.outboundConversationInvitation(TEST_USER_ID, TEST_CONVERSATION_ID) }
    }

    @Test
    fun deleteOutboundConversationInvitation() = runBlockingTest {
        conversationStorageManager.deleteOutboundConversationInvitation(TEST_USER_ID, TEST_CONVERSATION_ID)

        coVerify(exactly = 1) { mockConversationInterface.deleteOutboundConversationInvitation(TEST_USER_ID, TEST_CONVERSATION_ID) }
    }

    @Test
    fun storeInboundConversationInvitation() = runBlockingTest {
        val TEST_IN_CONVERSATION_INVITATION = InboundConversationInvitation(
            TEST_USER_ID,
            TEST_CONVERSATION_ID,
            TEST_PUBLIC_KEY,
            TEST_EPH_KEY,
            TEST_ON_TIME_KEY,
            TEST_DATE
        )

        conversationStorageManager.storeInboundConversationInvitation(
            TEST_USER_ID,
            TEST_CONVERSATION_ID,
            TEST_CONVERSATION_INVITATION,
            TEST_DATE
        )

        coVerify(exactly = 1) { mockConversationInterface.insert(TEST_IN_CONVERSATION_INVITATION) }
    }

    @Test
    fun inboundConversationInvitation() = runBlockingTest {
        val TEST_IN_CONVERSATION_INVITATION = InboundConversationInvitation(
            TEST_USER_ID,
            TEST_CONVERSATION_ID,
            TEST_PUBLIC_KEY,
            TEST_EPH_KEY,
            TEST_ON_TIME_KEY,
            TEST_DATE
        )

        coEvery { mockConversationInterface.inboundConversationInvitation(TEST_USER_ID, TEST_CONVERSATION_ID) }
            .returns(TEST_IN_CONVERSATION_INVITATION)

        val result = conversationStorageManager.inboundConversationInvitation(TEST_USER_ID, TEST_CONVERSATION_ID)

        assertEquals(TEST_IN_CONVERSATION_INVITATION, result)
    }

    @Test
    fun storeReceivedReset() = runBlockingTest {
        val expectedReceiveReset = ReceivedReset(TEST_USER_ID, TEST_CONVERSATION_ID, TEST_DATE)

        conversationStorageManager.storeReceivedReset(TEST_USER_ID, TEST_CONVERSATION_ID, TEST_DATE)

        coVerify(exactly = 1) { mockConversationInterface.insert(expectedReceiveReset) }
    }

    @Test
    fun receivedReset() = runBlockingTest {
        val TEST_RECEIVE_RESULT = ReceivedReset(TEST_USER_ID, TEST_CONVERSATION_ID, TEST_DATE)

        coEvery { mockConversationInterface.receivedReset(TEST_USER_ID, TEST_CONVERSATION_ID) } returns TEST_RECEIVE_RESULT

        val result = conversationStorageManager.receivedReset(TEST_USER_ID, TEST_CONVERSATION_ID)

        assertEquals(TEST_DATE, result)
    }

    @Test
    fun storeInvalidConversation() = runBlockingTest {
        val TEST_FINGERPRINT = "fingerprint"
        val TEST_RESEND_TIME = Date()

        val TEST_IN_CONVERSATION_INVITATION = InvalidConversation(
            TEST_USER_ID,
            TEST_CONVERSATION_ID,
            TEST_FINGERPRINT,
            TEST_DATE,
            TEST_RESEND_TIME
        )

        conversationStorageManager.storeInvalidConversation(
            TEST_USER_ID,
            TEST_CONVERSATION_ID,
            TEST_FINGERPRINT,
            TEST_DATE,
            TEST_RESEND_TIME
        )

        coVerify(exactly = 1) { mockConversationInterface.insert(TEST_IN_CONVERSATION_INVITATION) }
    }

    @Test
    fun invalidConversation() = runBlockingTest {
        val mockInvalidConversation: InvalidConversation = mockk()

        coEvery { mockConversationInterface.invalidConversation(TEST_USER_ID, TEST_CONVERSATION_ID) } returns mockInvalidConversation

        conversationStorageManager.invalidConversation(TEST_USER_ID, TEST_CONVERSATION_ID)

        coVerify(exactly = 1) { mockConversationInterface.invalidConversation(TEST_USER_ID, TEST_CONVERSATION_ID) }
    }

    @Test
    fun updateInvalidConversation() = runBlockingTest {
        val TEST_RESEND_TIME = Date()

        conversationStorageManager.updateInvalidConversation(TEST_USER_ID, TEST_CONVERSATION_ID, TEST_RESEND_TIME)

        coVerify(exactly = 1) { mockConversationInterface.updateInvalidConversation(TEST_USER_ID, TEST_CONVERSATION_ID, TEST_RESEND_TIME) }
    }
}