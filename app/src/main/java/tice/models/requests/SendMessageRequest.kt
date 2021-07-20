@file:UseSerializers(
    UUIDSerializer::class,
    DataSerializer::class
)

package tice.models.requests

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import tice.models.Certificate
import tice.models.Ciphertext
import tice.models.MessageId
import tice.models.UserId
import tice.models.messaging.MessagePriority
import tice.models.messaging.Recipient
import tice.utility.serializer.DataSerializer
import tice.utility.serializer.UUIDSerializer

@Serializable
data class SendMessageRequest(
    val id: MessageId,
    val senderId: UserId,
    val timestamp: String,
    val encryptedMessage: Ciphertext,
    val serverSignedMembershipCertificate: Certificate,
    val recipients: Set<Recipient>,
    val priority: MessagePriority,
    val collapseId: String? = null,
    val messageTimeToLive: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SendMessageRequest

        if (id != other.id) return false
        if (senderId != other.senderId) return false
        if (timestamp != other.timestamp) return false
        if (!encryptedMessage.contentEquals(other.encryptedMessage)) return false
        if (serverSignedMembershipCertificate != other.serverSignedMembershipCertificate) return false
        if (recipients != other.recipients) return false
        if (priority != other.priority) return false
        if (collapseId != other.collapseId) return false
        if (messageTimeToLive != other.messageTimeToLive) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + senderId.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + encryptedMessage.contentHashCode()
        result = 31 * result + serverSignedMembershipCertificate.hashCode()
        result = 31 * result + recipients.hashCode()
        result = 31 * result + priority.hashCode()
        result = 31 * result + (collapseId?.hashCode() ?: 0)
        result = 31 * result + messageTimeToLive.hashCode()
        return result
    }
}
