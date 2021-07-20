package tice.managers.storageManagers

import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import io.mockk.*
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tice.models.chat.Message
import tice.models.chat.MessageStatus
import tice.models.database.ChatMessageInterface
import tice.models.database.MessageEntity
import tice.models.database.message
import tice.models.database.messageEntity
import java.util.*

internal class ChatStorageManagerTest {

    private lateinit var chatStorageManager: ChatStorageManager

    private val mockChatInterface: ChatMessageInterface = mockk(relaxUnitFun = true)
    private val mockAppDatabase: AppDatabase = mockk(relaxUnitFun = true)

    private val TEST_GROUP_ID = UUID.randomUUID()

    @BeforeEach
    fun before() {
        clearAllMocks()

        every { mockAppDatabase.chatMessageInterface() } returns mockChatInterface
        chatStorageManager = ChatStorageManager(mockAppDatabase)
    }

    @Test
    fun groupMessagePagingSource() = runBlockingTest {
        val mockPagingSource: PagingSource<Int, MessageEntity> = mockk()
        every { mockChatInterface.loadMessagesFromGroup(TEST_GROUP_ID) } returns mockPagingSource

        val result = chatStorageManager.groupMessagePagingSource(TEST_GROUP_ID)

        Assertions.assertEquals(mockPagingSource, result)

        verify(exactly = 1) { mockChatInterface.loadMessagesFromGroup(TEST_GROUP_ID) }
        confirmVerified(mockChatInterface)
    }

    @Test
    fun unreadMessageCountLiveData() = runBlockingTest {
        val mockLiveData: LiveData<Int> = mockk()
        every { mockChatInterface.getMessageCountByReadStatusLiveData(TEST_GROUP_ID, false) } returns mockLiveData

        val result = chatStorageManager.unreadMessageCountLiveData(TEST_GROUP_ID)

        Assertions.assertEquals(mockLiveData, result)

        verify(exactly = 1) { mockChatInterface.getMessageCountByReadStatusLiveData(TEST_GROUP_ID, false) }
        confirmVerified(mockChatInterface)
    }

    @Test
    fun unreadMessageCount() = runBlockingTest {
        val mockCount: Int = 2
        coEvery { mockChatInterface.getMessageCountByReadStatus(TEST_GROUP_ID, false) } returns mockCount

        val result = chatStorageManager.unreadMessageCount(TEST_GROUP_ID)

        Assertions.assertEquals(mockCount, result)

        coVerify(exactly = 1) { mockChatInterface.getMessageCountByReadStatus(TEST_GROUP_ID, false) }
        confirmVerified(mockChatInterface)
    }

    @Test
    fun store() = runBlockingTest {
        val testMessage =
            Message.TextMessage(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Date(), false, MessageStatus.Success, "someText")

        lateinit var expectedMessageEntity: MessageEntity
        testMessage.apply {
            expectedMessageEntity =
                MessageEntity(messageId, groupId, senderId, date, read, status, MessageEntity.MessageType.TextMessage, text, null)
        }

        chatStorageManager.store(listOf(testMessage))

        coVerify { mockChatInterface.insert(listOf(expectedMessageEntity)) }
    }

    @Test
    fun message() = runBlockingTest {
        val TEST_TEXT = "TestText"
        val TEST_MESSAGE_ID = UUID.randomUUID()

        val messageEntity = MessageEntity(
            TEST_MESSAGE_ID,
            UUID.randomUUID(),
            UUID.randomUUID(),
            Date(),
            false,
            MessageStatus.Success,
            MessageEntity.MessageType.TextMessage,
            TEST_TEXT,
            null
        )

        lateinit var expectedMessage: Message.TextMessage
        messageEntity.apply {
            expectedMessage = Message.TextMessage(messageId, groupId, senderId, date, read, status, TEST_TEXT)
        }

        coEvery { mockChatInterface.getMessageById(TEST_MESSAGE_ID) } returns messageEntity

        val result = chatStorageManager.message(TEST_MESSAGE_ID)

        Assertions.assertEquals(expectedMessage, result)

        coVerify { mockChatInterface.getMessageById(TEST_MESSAGE_ID) }
    }

    @Test
    fun messageCount() = runBlockingTest {
        val mockCount: Int = 2
        coEvery { mockChatInterface.getMessageCountFromGroup(TEST_GROUP_ID) } returns mockCount

        val result = chatStorageManager.messageCount(TEST_GROUP_ID)

        Assertions.assertEquals(mockCount, result)

        coVerify(exactly = 1) { mockChatInterface.getMessageCountFromGroup(TEST_GROUP_ID) }
        confirmVerified(mockChatInterface)
    }

    @Test
    fun messages() = runBlockingTest {
        val TEST_TEXT = "TestText"
        val TEST_MESSAGE_ID = UUID.randomUUID()
        val TEST_LIMIT = Random().nextInt()
        val TEST_OFFSET = Random().nextInt()

        val messageEntity1 = MessageEntity(
            TEST_MESSAGE_ID,
            UUID.randomUUID(),
            UUID.randomUUID(),
            Date(),
            false,
            MessageStatus.Success,
            MessageEntity.MessageType.TextMessage,
            TEST_TEXT,
            null
        )

        val messageEntity2 = MessageEntity(
            TEST_MESSAGE_ID,
            UUID.randomUUID(),
            UUID.randomUUID(),
            Date(),
            false,
            MessageStatus.Success,
            MessageEntity.MessageType.TextMessage,
            TEST_TEXT,
            null
        )

        lateinit var expectedMessage1: Message.TextMessage
        messageEntity1.apply {
            expectedMessage1 = Message.TextMessage(messageId, groupId, senderId, date, read, status, TEST_TEXT)
        }

        lateinit var expectedMessage2: Message.TextMessage
        messageEntity2.apply {
            expectedMessage2 = Message.TextMessage(messageId, groupId, senderId, date, read, status, TEST_TEXT)
        }

        val messageEntityList = listOf(messageEntity1, messageEntity2)
        val messagesList = listOf(expectedMessage1, expectedMessage2)

        coEvery { mockChatInterface.getMessagesPage(TEST_GROUP_ID, TEST_OFFSET, TEST_LIMIT) } returns messageEntityList

        val result = chatStorageManager.messages(TEST_GROUP_ID, TEST_OFFSET, TEST_LIMIT)

        Assertions.assertEquals(messagesList, result)

        coVerify(exactly = 1) { mockChatInterface.getMessagesPage(TEST_GROUP_ID, TEST_OFFSET, TEST_LIMIT) }
    }

    @Test
    fun unreadMessages() = runBlockingTest {
        val TEST_TEXT = "TestText"
        val TEST_MESSAGE_ID = UUID.randomUUID()

        val messageEntity1 = MessageEntity(
            TEST_MESSAGE_ID,
            UUID.randomUUID(),
            UUID.randomUUID(),
            Date(),
            false,
            MessageStatus.Success,
            MessageEntity.MessageType.TextMessage,
            TEST_TEXT,
            null
        )

        val messageEntity2 = MessageEntity(
            TEST_MESSAGE_ID,
            UUID.randomUUID(),
            UUID.randomUUID(),
            Date(),
            false,
            MessageStatus.Success,
            MessageEntity.MessageType.TextMessage,
            TEST_TEXT,
            null
        )

        lateinit var expectedMessage1: Message.TextMessage
        messageEntity1.apply {
            expectedMessage1 = Message.TextMessage(messageId, groupId, senderId, date, read, status, TEST_TEXT)
        }

        lateinit var expectedMessage2: Message.TextMessage
        messageEntity2.apply {
            expectedMessage2 = Message.TextMessage(messageId, groupId, senderId, date, read, status, TEST_TEXT)
        }

        val messageEntityList = listOf(messageEntity1, messageEntity2)
        val messagesList = listOf(expectedMessage1, expectedMessage2)

        coEvery { mockChatInterface.getMessageByReadStatus(TEST_GROUP_ID, false) } returns messageEntityList

        val result = chatStorageManager.unreadMessages(TEST_GROUP_ID)

        Assertions.assertEquals(messagesList, result)

        coVerify(exactly = 1) { mockChatInterface.getMessageByReadStatus(TEST_GROUP_ID, false) }
    }

    @Nested
    inner class `MessageEntity map from` {
        val TEST_MESSAGE_ID = UUID.randomUUID()
        val TEST_GROUP_ID = UUID.randomUUID()
        val TEST_SENDER_ID = UUID.randomUUID()
        val TEST_DATE = Date()
        val TEST_READ = true
        val TEST_STATUS = MessageStatus.Success

        @Test
        fun textMessage() {
            val TEST_TEXT = "SomeCoolText"

            val message = Message.TextMessage(TEST_MESSAGE_ID, TEST_GROUP_ID, TEST_SENDER_ID, TEST_DATE, TEST_READ, TEST_STATUS, TEST_TEXT)

            val expectedMessageEntity = MessageEntity(
                TEST_MESSAGE_ID,
                TEST_GROUP_ID,
                TEST_SENDER_ID,
                TEST_DATE,
                TEST_READ,
                TEST_STATUS,
                MessageEntity.MessageType.TextMessage,
                TEST_TEXT,
                null
            )

            val result = message.messageEntity()

            Assertions.assertEquals(expectedMessageEntity, result)
        }

        @Test
        fun imageMessage() {
            val TEST_DATA = ByteArray(0)

            val message = Message.ImageMessage(TEST_MESSAGE_ID, TEST_GROUP_ID, TEST_SENDER_ID, TEST_DATE, TEST_READ, TEST_STATUS, TEST_DATA)

            val expectedMessageEntity = MessageEntity(
                TEST_MESSAGE_ID,
                TEST_GROUP_ID,
                TEST_SENDER_ID,
                TEST_DATE,
                TEST_READ,
                TEST_STATUS,
                MessageEntity.MessageType.ImageMessage,
                null,
                TEST_DATA
            )

            val result = message.messageEntity()

            Assertions.assertEquals(expectedMessageEntity, result)
        }

        @Test
        fun metaMessage() {
            val TEST_EVENT = "TEST_EVENT"

            val message = Message.MetaMessage(TEST_MESSAGE_ID, TEST_GROUP_ID, TEST_SENDER_ID, TEST_DATE, TEST_READ, TEST_STATUS, TEST_EVENT)

            val expectedMessageEntity = MessageEntity(
                TEST_MESSAGE_ID,
                TEST_GROUP_ID,
                TEST_SENDER_ID,
                TEST_DATE,
                TEST_READ,
                TEST_STATUS,
                MessageEntity.MessageType.MetaMessage,
                TEST_EVENT,
                null
            )

            val result = message.messageEntity()

            Assertions.assertEquals(expectedMessageEntity, result)
        }
    }

    @Nested
    inner class `MessageEntity map to` {
        val TEST_MESSAGE_ID = UUID.randomUUID()
        val TEST_GROUP_ID = UUID.randomUUID()
        val TEST_SENDER_ID = UUID.randomUUID()
        val TEST_DATE = Date()
        val TEST_READ = true
        val TEST_STATUS = MessageStatus.Success

        @Test
        fun textMessage() {
            val TEST_TEXT = "SomeCoolText"

            val messageEntity = MessageEntity(
                TEST_MESSAGE_ID,
                TEST_GROUP_ID,
                TEST_SENDER_ID,
                TEST_DATE,
                TEST_READ,
                TEST_STATUS,
                MessageEntity.MessageType.TextMessage,
                TEST_TEXT,
                null
            )

            val expectedMessage =
                Message.TextMessage(TEST_MESSAGE_ID, TEST_GROUP_ID, TEST_SENDER_ID, TEST_DATE, TEST_READ, TEST_STATUS, TEST_TEXT)

            val result = messageEntity.message()

            Assertions.assertEquals(expectedMessage, result)
        }

        @Test
        fun imageMessage() {
            val TEST_DATA = ByteArray(0)

            val messageEntity = MessageEntity(
                TEST_MESSAGE_ID,
                TEST_GROUP_ID,
                TEST_SENDER_ID,
                TEST_DATE,
                TEST_READ,
                TEST_STATUS,
                MessageEntity.MessageType.ImageMessage,
                null,
                TEST_DATA
            )

            val expectedMessage =
                Message.ImageMessage(TEST_MESSAGE_ID, TEST_GROUP_ID, TEST_SENDER_ID, TEST_DATE, TEST_READ, TEST_STATUS, TEST_DATA)

            val result = messageEntity.message()

            Assertions.assertEquals(expectedMessage, result)
        }

        @Test
        fun metaMessage() {
            val TEST_EVENT = "TEST_EVENT"

            val messageEntity = MessageEntity(
                TEST_MESSAGE_ID,
                TEST_GROUP_ID,
                TEST_SENDER_ID,
                TEST_DATE,
                TEST_READ,
                TEST_STATUS,
                MessageEntity.MessageType.MetaMessage,
                TEST_EVENT,
                null
            )

            val expectedMessage =
                Message.MetaMessage(TEST_MESSAGE_ID, TEST_GROUP_ID, TEST_SENDER_ID, TEST_DATE, TEST_READ, TEST_STATUS, TEST_EVENT)

            val result = messageEntity.message()

            Assertions.assertEquals(expectedMessage, result)
        }
    }
}