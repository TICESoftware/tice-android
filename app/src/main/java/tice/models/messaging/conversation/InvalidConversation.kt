@file:UseSerializers(
    UUIDSerializer::class,
    DateSerializer::class
)

package tice.models.messaging.conversation

import androidx.room.Entity
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import tice.models.ConversationFingerprint
import tice.models.ConversationId
import tice.models.UserId
import tice.utility.serializer.DateSerializer
import tice.utility.serializer.UUIDSerializer
import java.util.*

@Entity(primaryKeys = ["senderId", "conversationId"])
@Serializable
data class InvalidConversation(
    val senderId: UserId,
    val conversationId: ConversationId,
    val conversationFingerprint: ConversationFingerprint,
    val timestamp: Date,
    val resendResetTimeout: Date
)
