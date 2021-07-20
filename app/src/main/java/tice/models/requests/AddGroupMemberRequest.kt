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
data class AddGroupMemberRequest(
    val encryptedMembership: Ciphertext,
    val userId: UserId,
    val newTokenKey: String,
    val notificationRecipients: List<NotificationRecipient>
)
