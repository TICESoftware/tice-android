package tice.managers.messaging

import tice.models.Certificate
import tice.models.CollapseIdentifier
import tice.models.Membership
import tice.models.messaging.MessagePriority
import tice.models.messaging.Payload
import tice.models.messaging.PayloadContainer
import tice.models.messaging.ResetConversation

interface MailboxType {
    suspend fun sendToGroup(
        payloadContainer: PayloadContainer = PayloadContainer(Payload.PayloadType.ResetConversationV1, ResetConversation),
        members: Set<Membership>,
        serverSignedMembershipCertificate: Certificate,
        priority: MessagePriority,
        collapseIdentifier: CollapseIdentifier?
    )

    fun registerForDelegate()
}
