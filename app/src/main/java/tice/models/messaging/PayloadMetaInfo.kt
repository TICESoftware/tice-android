package tice.models.messaging

import tice.models.Certificate
import tice.models.CollapseIdentifier
import tice.models.UserId
import tice.models.messaging.conversation.ConversationInvitation
import java.util.*

data class PayloadMetaInfo(
    val senderId: UserId,
    val timestamp: Date,
    val collapseId: CollapseIdentifier? = null,
    val senderServerSignedMembershipCertificate: Certificate? = null,
    val receiverSeverSignedMembershipCertificate: Certificate? = null,
    var authenticated: Boolean,
    val conversationInvitation: ConversationInvitation? = null
)
