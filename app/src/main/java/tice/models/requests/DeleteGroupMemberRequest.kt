@file:UseSerializers(
    UUIDSerializer::class
)

package tice.models.requests

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import tice.models.Certificate
import tice.models.NotificationRecipient
import tice.models.UserId
import tice.utility.serializer.UUIDSerializer

@Serializable
data class DeleteGroupMemberRequest(
    val userId: UserId,
    val serverSignedMembershipCertificate: Certificate,
    val notificationRecipients: List<NotificationRecipient>
)
