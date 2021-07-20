@file:UseSerializers(
    ConversationInvitationSerializer::class,
    UUIDSerializer::class,
    DateSerializer::class,
    PayloadContainerSerializer::class
)

package tice.models.messaging

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import tice.models.Certificate
import tice.models.CollapseIdentifier
import tice.models.MessageId
import tice.models.UserId
import tice.models.messaging.conversation.ConversationInvitation
import tice.utility.serializer.ConversationInvitationSerializer
import tice.utility.serializer.DateSerializer
import tice.utility.serializer.PayloadContainerSerializer
import tice.utility.serializer.UUIDSerializer
import java.util.*

@Serializable
data class Envelope(
    val id: MessageId,
    val senderId: UserId,
    val senderServerSignedMembershipCertificate: Certificate? = null,
    val receiverServerSignedMembershipCertificate: Certificate? = null,
    val timestamp: Date,
    val serverTimestamp: Date,
    val collapseId: CollapseIdentifier? = null,
    val conversationInvitation: ConversationInvitation? = null,
    val payloadContainer: PayloadContainer
)
