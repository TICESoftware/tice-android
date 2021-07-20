package tice.managers

import com.ticeapp.TICE.R
import tice.dagger.scopes.AppScope
import tice.exceptions.BackendException
import tice.exceptions.ChatMessageInterfaceException
import tice.exceptions.UnexpectedPayloadTypeException
import tice.managers.group.GroupManagerType
import tice.managers.messaging.PostOfficeType
import tice.managers.messaging.notificationHandler.PayloadReceiver
import tice.managers.storageManagers.ChatStorageManagerType
import tice.models.GroupId
import tice.models.LocalizationId
import tice.models.MessageId
import tice.models.Team
import tice.models.chat.Message
import tice.models.chat.MessageStatus
import tice.models.messaging.*
import tice.utility.getLogger
import tice.utility.provider.LocalizationProviderType
import java.util.*
import javax.inject.Inject

@AppScope
class ChatManager @Inject constructor(
    private val postOffice: PostOfficeType,
    private val chatStorageManager: ChatStorageManagerType,
    private val groupManager: GroupManagerType,
    private val popupNotificationManager: PopupNotificationManagerType,
    private val localizationProvider: LocalizationProviderType
) : ChatManagerType, PayloadReceiver {
    val logger by getLogger()

    override fun registerEnvelopeReceiver() {
        postOffice.registerEnvelopeReceiver(Payload.PayloadType.ChatMessageV1, this)
    }

    override suspend fun send(message: Message.TextMessage, team: Team) {
        val payload = ChatMessage(team.groupId, message.text)
        val payloadContainer = PayloadContainer(Payload.PayloadType.ChatMessageV1, payload)

        message.status = MessageStatus.Sending
        chatStorageManager.store(listOf(message))

        try {
            groupManager.send(payloadContainer, team, null, MessagePriority.Alert)
        } catch (e: BackendException) {
            message.status = MessageStatus.Failed
            chatStorageManager.store(listOf(message))
            logger.error("sending message failed")
            throw e
        }

        message.status = MessageStatus.Success
        chatStorageManager.store(listOf(message))
    }

    override suspend fun add(message: Message) {
        chatStorageManager.store(listOf(message))
    }

    override suspend fun markAsRead(messageId: MessageId) {
        chatStorageManager.message(messageId)?.let { message ->
            message.read = true
            chatStorageManager.store(listOf(message))
        }
    }

    override suspend fun markAllAsRead(groupId: GroupId) {
        val messages = chatStorageManager.unreadMessages(groupId)
        messages.forEach { it.read = true }

        chatStorageManager.store(messages)
    }

    override suspend fun handlePayloadContainerBundle(bundle: PayloadContainerBundle) {
        val chatMessage = (bundle.payload as? ChatMessage)
            ?: throw UnexpectedPayloadTypeException

        val message = when {
            chatMessage.text != null -> {
                Message.TextMessage(
                    UUID.randomUUID(),
                    chatMessage.groupId,
                    bundle.metaInfo.senderId,
                    bundle.metaInfo.timestamp,
                    false,
                    MessageStatus.Success,
                    chatMessage.text
                ).also {
                    val locTitle = localizationProvider.getString(LocalizationId(R.string.notification_group_message_title_unknown))
                    popupNotificationManager.showPopUpNotification(locTitle, chatMessage.text)
                }
            }

            chatMessage.imageData != null -> {
                Message.ImageMessage(
                    UUID.randomUUID(),
                    chatMessage.groupId,
                    bundle.metaInfo.senderId,
                    bundle.metaInfo.timestamp,
                    false,
                    MessageStatus.Success,
                    chatMessage.imageData
                )
            }
            else -> throw ChatMessageInterfaceException.NotSupportedMessageType
        }

        add(message)
    }
}
