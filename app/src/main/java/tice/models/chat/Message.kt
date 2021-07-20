package tice.models.chat

import tice.models.Data
import tice.models.GroupId
import tice.models.MessageId
import tice.models.UserId
import java.util.*

sealed class Message {
    abstract val groupId: GroupId

    abstract val messageId: MessageId
    abstract val date: Date
    abstract var read: Boolean

    abstract val senderId: UserId
    abstract var status: MessageStatus

    data class TextMessage(
        override val messageId: MessageId,
        override val groupId: GroupId,
        override val senderId: UserId,
        override val date: Date,

        override var read: Boolean,
        override var status: MessageStatus,

        var text: String
    ) : Message()

    data class ImageMessage(
        override val messageId: MessageId,
        override val groupId: GroupId,
        override val senderId: UserId,
        override val date: Date,

        override var read: Boolean,
        override var status: MessageStatus,

        var imageData: Data
    ) : Message() {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ImageMessage

            if (groupId != other.groupId) return false
            if (messageId != other.messageId) return false
            if (senderId != other.senderId) return false
            if (date != other.date) return false
            if (read != other.read) return false
            if (status != other.status) return false
            if (!imageData.contentEquals(other.imageData)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = groupId.hashCode()
            result = 31 * result + messageId.hashCode()
            result = 31 * result + senderId.hashCode()
            result = 31 * result + date.hashCode()
            result = 31 * result + read.hashCode()
            result = 31 * result + status.hashCode()
            result = 31 * result + imageData.contentHashCode()
            return result
        }
    }

    data class MetaMessage(
        override val messageId: MessageId,
        override val groupId: GroupId,
        override val senderId: UserId,
        override val date: Date,

        override var read: Boolean,
        override var status: MessageStatus,

        val text: String
    ) : Message()
}
