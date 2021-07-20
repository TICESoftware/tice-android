@file:UseSerializers(
    UUIDSerializer::class
)

package tice.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import tice.models.messaging.MessagePriority
import tice.utility.serializer.UUIDSerializer

@Serializable
data class NotificationRecipient(
    val userId: UserId,
    val serverSignedMembershipCertificate: Certificate,
    val priority: MessagePriority
)
