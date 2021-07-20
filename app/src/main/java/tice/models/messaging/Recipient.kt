@file:UseSerializers(
    UUIDSerializer::class,
    DataSerializer::class,
    ConversationInvitationSerializer::class
)

package tice.models.messaging

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import tice.models.Certificate
import tice.models.Ciphertext
import tice.models.UserId
import tice.models.messaging.conversation.ConversationInvitation
import tice.utility.serializer.ConversationInvitationSerializer
import tice.utility.serializer.DataSerializer
import tice.utility.serializer.UUIDSerializer

@Serializable
data class Recipient(
    val userId: UserId,
    val serverSignedMembershipCertificate: Certificate,
    val encryptedMessageKey: Ciphertext,
    val conversationInvitation: ConversationInvitation? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Recipient

        if (userId != other.userId) return false
        if (serverSignedMembershipCertificate != other.serverSignedMembershipCertificate) return false
        if (!encryptedMessageKey.contentEquals(other.encryptedMessageKey)) return false
        if (conversationInvitation != other.conversationInvitation) return false

        return true
    }

    override fun hashCode(): Int {
        var result = userId.hashCode()
        result = 31 * result + serverSignedMembershipCertificate.hashCode()
        result = 31 * result + encryptedMessageKey.contentHashCode()
        result = 31 * result + (conversationInvitation?.hashCode() ?: 0)
        return result
    }
}
