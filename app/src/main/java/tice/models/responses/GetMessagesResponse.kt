package tice.models.responses

import kotlinx.serialization.Serializable
import tice.models.messaging.Envelope

@Serializable
data class GetMessagesResponse(
    val messages: Array<Envelope>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GetMessagesResponse

        if (!messages.contentEquals(other.messages)) return false

        return true
    }

    override fun hashCode(): Int {
        return messages.contentHashCode()
    }
}
