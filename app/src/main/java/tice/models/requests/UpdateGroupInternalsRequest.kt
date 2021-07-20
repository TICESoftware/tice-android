@file:UseSerializers(
    DataSerializer::class
)

package tice.models.requests

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import tice.models.Ciphertext
import tice.models.NotificationRecipient
import tice.utility.serializer.DataSerializer

@Serializable
data class UpdateGroupInternalsRequest(
    val newInternalSettings: Ciphertext,
    val notificationRecipients: List<NotificationRecipient>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UpdateGroupInternalsRequest

        if (!newInternalSettings.contentEquals(other.newInternalSettings)) return false
        if (notificationRecipients != other.notificationRecipients) return false

        return true
    }

    override fun hashCode(): Int {
        var result = newInternalSettings.contentHashCode()
        result = 31 * result + notificationRecipients.hashCode()
        return result
    }
}
