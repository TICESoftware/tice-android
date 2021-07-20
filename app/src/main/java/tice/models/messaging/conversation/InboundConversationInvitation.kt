@file:UseSerializers(
    UUIDSerializer::class,
    DateSerializer::class
)

package tice.models.messaging.conversation

import androidx.room.Entity
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import tice.models.ConversationId
import tice.models.PublicKey
import tice.models.UserId
import tice.utility.serializer.DateSerializer
import tice.utility.serializer.UUIDSerializer
import java.util.*

@Entity(primaryKeys = ["senderId", "conversationId"])
@Serializable
data class InboundConversationInvitation(
    val senderId: UserId,
    val conversationId: ConversationId,
    val identityKey: PublicKey,
    val ephemeralKey: PublicKey,
    val usedOneTimePrekey: PublicKey? = null,
    val timestamp: Date
) {
    fun conversationInvitation(): ConversationInvitation = ConversationInvitation(identityKey, ephemeralKey, usedOneTimePrekey)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InboundConversationInvitation

        if (senderId != other.senderId) return false
        if (conversationId != other.conversationId) return false
        if (!identityKey.contentEquals(other.identityKey)) return false
        if (!ephemeralKey.contentEquals(other.ephemeralKey)) return false
        if (usedOneTimePrekey != null) {
            if (other.usedOneTimePrekey == null) return false
            if (!usedOneTimePrekey.contentEquals(other.usedOneTimePrekey)) return false
        } else if (other.usedOneTimePrekey != null) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = senderId.hashCode()
        result = 31 * result + conversationId.hashCode()
        result = 31 * result + identityKey.contentHashCode()
        result = 31 * result + ephemeralKey.contentHashCode()
        result = 31 * result + (usedOneTimePrekey?.contentHashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        return result
    }
}
