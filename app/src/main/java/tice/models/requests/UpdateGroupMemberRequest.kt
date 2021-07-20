@file:UseSerializers(
    UUIDSerializer::class,
    DataSerializer::class
)

package tice.models.requests

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import tice.models.Ciphertext
import tice.models.NotificationRecipient
import tice.models.UserId
import tice.utility.serializer.DataSerializer
import tice.utility.serializer.UUIDSerializer

@Serializable
data class UpdateGroupMemberRequest(
    val encryptedMembership: Ciphertext,
    val userId: UserId,
    val notificationRecipients: List<NotificationRecipient>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UpdateGroupMemberRequest

        if (!encryptedMembership.contentEquals(other.encryptedMembership)) return false
        if (userId != other.userId) return false
        if (notificationRecipients != other.notificationRecipients) return false

        return true
    }

    override fun hashCode(): Int {
        var result = encryptedMembership.contentHashCode()
        result = 31 * result + userId.hashCode()
        result = 31 * result + notificationRecipients.hashCode()
        return result
    }
}
