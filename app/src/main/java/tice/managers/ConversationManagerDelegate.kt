package tice.managers

import tice.models.Certificate
import tice.models.CollapseIdentifier
import tice.models.UserId

interface ConversationManagerDelegate {
    suspend fun sendResetReply(
        userId: UserId,
        receiverCertificate: Certificate,
        senderCertificate: Certificate,
        collapseId: CollapseIdentifier?
    )
}
