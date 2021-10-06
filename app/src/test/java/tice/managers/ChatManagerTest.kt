package tice.managers

import com.ticeapp.TICE.R
import io.mockk.*
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tice.exceptions.BackendException
import tice.managers.group.GroupManagerType
import tice.managers.messaging.PostOfficeType
import tice.managers.storageManagers.ChatStorageManagerType
import tice.models.LocalizationId
import tice.models.Team
import tice.models.chat.Message
import tice.models.chat.MessageStatus
import tice.models.messaging.*
import tice.utility.provider.LocalizationProviderType
import java.util.*

internal class ChatManagerTest {

    private lateinit var chatManager: ChatManager

    private val mockPostOffice: PostOfficeType = mockk(relaxUnitFun = true)
    private val mockChatStorageManager: ChatStorageManagerType = mockk(relaxUnitFun = true)
    private val mockGroupManager: GroupManagerType = mockk(relaxUnitFun = true)
    private val mockLocalizationProvider: LocalizationProviderType = mockk(relaxUnitFun = true)
    private val mockPopupNotificationManager: PopupNotificationManagerType = mockk(relaxUnitFun = true)

    private val TEST_GROUP_ID = UUID.randomUUID()
    private val TEST_SENDER_ID = UUID.randomUUID()

    @BeforeEach
    fun before() {
        clearAllMocks()

        chatManager = ChatManager(
            mockPostOffice,
            mockChatStorageManager,
            mockGroupManager,
            mockPopupNotificationManager,
            mockLocalizationProvider
        )
    }

    @Test
    fun init_RegisterEnvelopeReceiver() = runBlockingTest {
        chatManager.registerEnvelopeReceiver()

        verify(exactly = 1) { mockPostOffice.registerEnvelopeReceiver(Payload.PayloadType.ChatMessageV1, chatManager) }
    }

    @Nested
    inner class Receives {

        @Test
        fun `TextMessage`() = runBlockingTest {
            val TEST_TEXT = "testString"
            val TEST_DATE = Date()
            val TEST_LOC_MESSAGE = "locChatMessageTitle"

            every { mockLocalizationProvider.getString(LocalizationId(R.string.notification_group_message_title_unknown)) } returns TEST_LOC_MESSAGE

            val mockBundleText = mockk<PayloadContainerBundle> {
                every { payloadType } returns Payload.PayloadType.ChatMessageV1
                every { payload } returns mockk<ChatMessage> {
                    every { groupId } returns TEST_GROUP_ID
                    every { text } returns TEST_TEXT
                    every { imageData } returns null
                }
                every { metaInfo } returns mockk {
                    every { senderId } returns TEST_SENDER_ID
                    every { timestamp } returns TEST_DATE
                }
            }

            val storedMessageSlot = slot<List<Message>>()

            chatManager.handlePayloadContainerBundle(mockBundleText)

            coVerify(exactly = 1) { mockChatStorageManager.store(capture(storedMessageSlot)) }

            val expectedTextMessage = Message.TextMessage(
                storedMessageSlot.captured.first().messageId,
                TEST_GROUP_ID,
                TEST_SENDER_ID,
                TEST_DATE,
                false,
                MessageStatus.Success,
                TEST_TEXT
            )
            Assertions.assertEquals(expectedTextMessage, storedMessageSlot.captured.first())

            verify(exactly = 1) { mockPopupNotificationManager.showPopUpNotification(TEST_LOC_MESSAGE, TEST_TEXT) }
        }

        @Test
        fun imageMessage() = runBlockingTest {
            val TEST_IMAGE_DATA = ByteArray(0)
            val TEST_DATE = Date()
            val TEST_LOC_MESSAGE = "locChatMessageTitle"

            every { mockLocalizationProvider.getString(LocalizationId(R.string.notification_group_message_title_unknown)) } returns TEST_LOC_MESSAGE

            val mockBundleImage = mockk<PayloadContainerBundle> {
                every { payloadType } returns Payload.PayloadType.ChatMessageV1
                every { payload } returns mockk<ChatMessage> {
                    every { groupId } returns TEST_GROUP_ID
                    every { text } returns null
                    every { imageData } returns TEST_IMAGE_DATA
                }
                every { metaInfo } returns mockk {
                    every { senderId } returns TEST_SENDER_ID
                    every { timestamp } returns TEST_DATE
                }
            }

            val storedMessageSlot = slot<List<Message>>()

            chatManager.handlePayloadContainerBundle(mockBundleImage)

            coVerify(exactly = 1) { mockChatStorageManager.store(capture(storedMessageSlot)) }

            val expectedImageMessage = Message.ImageMessage(
                storedMessageSlot.captured.first().messageId,
                TEST_GROUP_ID,
                TEST_SENDER_ID,
                TEST_DATE,
                false,
                MessageStatus.Success,
                TEST_IMAGE_DATA
            )
            Assertions.assertEquals(expectedImageMessage, storedMessageSlot.captured.first())

            confirmVerified(mockPopupNotificationManager)
        }
    }

    @Nested
    inner class Send {

        @Test
        fun success() = runBlockingTest {
            val mockTeam: Team = mockk { every { groupId } returns TEST_GROUP_ID }
            val TEST_STRING = "testString"

            val mockMessage: Message.TextMessage = mockk(relaxed = true) { every { text } returns TEST_STRING }

            chatManager.send(mockMessage, mockTeam)

            val expectedPayload = ChatMessage(TEST_GROUP_ID, TEST_STRING)
            val expectedPayloadContainer = PayloadContainer(Payload.PayloadType.ChatMessageV1, expectedPayload)

            verify(exactly = 1) { mockMessage.text }
            verify(exactly = 1) { mockMessage.status = MessageStatus.Sending }
            verify(exactly = 1) { mockMessage.status = MessageStatus.Success }
            coVerify(exactly = 2) { mockChatStorageManager.store(listOf(mockMessage)) }
            coVerify { mockGroupManager.send(expectedPayloadContainer, mockTeam, null, MessagePriority.Alert) }
        }

        @Test
        fun failed() = runBlockingTest {
            val mockTeam: Team = mockk { every { groupId } returns TEST_GROUP_ID }
            val TEST_STRING = "testString"

            val mockMessage: Message.TextMessage = mockk(relaxed = true) { every { text } returns TEST_STRING }

            val expectedPayload = ChatMessage(TEST_GROUP_ID, TEST_STRING)
            val expectedPayloadContainer = PayloadContainer(Payload.PayloadType.ChatMessageV1, expectedPayload)

            coEvery { mockGroupManager.send(expectedPayloadContainer, mockTeam, null, MessagePriority.Alert) }
                .throws(BackendException.GroupOutdated)

            Assertions.assertThrows(BackendException::class.java) {
                runBlockingTest { chatManager.send(mockMessage, mockTeam) }
            }

            verify(exactly = 1) { mockMessage.text }
            verify(exactly = 1) { mockMessage.status = MessageStatus.Sending }
            verify(exactly = 1) { mockMessage.status = MessageStatus.Failed }
            verify(inverse = true) { mockMessage.status = MessageStatus.Success }
            coVerify(exactly = 2) { mockChatStorageManager.store(listOf(mockMessage)) }
            coVerify { mockGroupManager.send(expectedPayloadContainer, mockTeam, null, MessagePriority.Alert) }
        }
    }

    @Test
    fun add() = runBlockingTest {
        val mockMessage1: Message.TextMessage = mockk()
        val mockMessage2: Message.ImageMessage = mockk()
        val mockMessage3: Message = mockk()

        chatManager.add(mockMessage1)
        chatManager.add(mockMessage2)
        chatManager.add(mockMessage3)

        coVerify(exactly = 1) { mockChatStorageManager.store(listOf(mockMessage1)) }
        coVerify(exactly = 1) { mockChatStorageManager.store(listOf(mockMessage2)) }
        coVerify(exactly = 1) { mockChatStorageManager.store(listOf(mockMessage3)) }
    }

    @Test
    fun markAsRead() = runBlockingTest {
        val TEST_MESSAGE_ID = UUID.randomUUID()
        val mockMessage1: Message.TextMessage = mockk(relaxed = true)

        coEvery { mockChatStorageManager.message(TEST_MESSAGE_ID) } returns mockMessage1

        chatManager.markAsRead(TEST_MESSAGE_ID)

        verify(exactly = 1) { mockMessage1.read = true }
        coVerify(exactly = 1) { mockChatStorageManager.store(listOf(mockMessage1)) }
    }

    @Test
    fun markAllAsRead() = runBlockingTest {
        val mockMessage1: Message.TextMessage = mockk(relaxed = true)
        val mockMessage2: Message.ImageMessage = mockk(relaxed = true)
        val mockMessage3: Message = mockk(relaxed = true)

        coEvery { mockChatStorageManager.unreadMessages(TEST_GROUP_ID) } returns listOf(mockMessage1, mockMessage2, mockMessage3)

        chatManager.markAllAsRead(TEST_GROUP_ID)

        verify(exactly = 1) { mockMessage1.read = true }
        verify(exactly = 1) { mockMessage2.read = true }
        verify(exactly = 1) { mockMessage3.read = true }
        coVerify(exactly = 1) { mockChatStorageManager.store(listOf(mockMessage1, mockMessage2, mockMessage3)) }
    }
}