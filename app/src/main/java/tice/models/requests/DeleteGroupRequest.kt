package tice.models.requests

import kotlinx.serialization.Serializable
import tice.models.Certificate
import tice.models.NotificationRecipient

@Serializable
data class DeleteGroupRequest(
    val serverSignedAdminCertificate: Certificate,
    val notificationRecipients: List<NotificationRecipient>
)
