@file:UseSerializers(
    ConversationInvitationSerializer::class,
    UUIDSerializer::class,
    DataSerializer::class,
    PayloadContainerSerializer::class
)

package tice.models.messaging.conversation

import androidx.room.Entity
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import tice.models.ConversationId
import tice.models.PublicKey
import tice.models.UserId
import tice.utility.serializer.ConversationInvitationSerializer
import tice.utility.serializer.DataSerializer
import tice.utility.serializer.PayloadContainerSerializer
import tice.utility.serializer.UUIDSerializer

@Entity(primaryKeys = ["receiverId", "conversationId"])
@Serializable
data class OutboundConversationInvitation(
    val receiverId: UserId,
    val conversationId: ConversationId,
    val identityKey: PublicKey,
    val ephemeralKey: PublicKey,
    val usedOneTimePrekey: PublicKey? = null
) {
    fun conversationInvitation(): ConversationInvitation = ConversationInvitation(identityKey, ephemeralKey, usedOneTimePrekey)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OutboundConversationInvitation

        if (receiverId != other.receiverId) return false
        if (conversationId != other.conversationId) return false
        if (!identityKey.contentEquals(other.identityKey)) return false
        if (!ephemeralKey.contentEquals(other.ephemeralKey)) return false
        if (usedOneTimePrekey != null) {
            if (other.usedOneTimePrekey == null) return false
            if (!usedOneTimePrekey.contentEquals(other.usedOneTimePrekey)) return false
        } else if (other.usedOneTimePrekey != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = receiverId.hashCode()
        result = 31 * result + conversationId.hashCode()
        result = 31 * result + identityKey.contentHashCode()
        result = 31 * result + ephemeralKey.contentHashCode()
        result = 31 * result + (usedOneTimePrekey?.contentHashCode() ?: 0)
        return result
    }
}
