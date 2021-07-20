package tice.ui.models

import tice.models.Data
import tice.models.MessageId
import java.util.*

sealed class ChatMessagesRepresentation {
    abstract val date: Date

    enum class Direction { Incoming, Outgoing }

    sealed class MessageItem : ChatMessagesRepresentation() {
        abstract val messageId: MessageId
        abstract val read: Boolean
        abstract val senderName: String
        abstract val senderColor: Int
        abstract var isLastOfUser: Boolean

        data class TextMessage(
            override val messageId: MessageId,
            override val date: Date,

            override val read: Boolean,
            override val senderName: String,
            override val senderColor: Int,
            override var isLastOfUser: Boolean,

            val direction: Direction,
            val text: String
        ) : MessageItem()

        data class ImageMessage(
            override val messageId: MessageId,
            override val date: Date,

            override val read: Boolean,
            override val senderName: String,
            override val senderColor: Int,
            override var isLastOfUser: Boolean,

            val direction: Direction,
            val imageData: Data
        ) : MessageItem() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as ImageMessage

                if (messageId != other.messageId) return false
                if (date != other.date) return false
                if (read != other.read) return false
                if (senderName != other.senderName) return false
                if (senderColor != other.senderColor) return false
                if (!imageData.contentEquals(other.imageData)) return false

                return true
            }
        }
    }

    data class MetaInfo(
        override val date: Date,
        val text: String
    ) : ChatMessagesRepresentation()

    data class DateSeparator(
        override val date: Date
    ) : ChatMessagesRepresentation()
}
