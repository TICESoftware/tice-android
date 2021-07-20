package tice.models.messaging.conversation

import tice.models.ConversationId
import tice.models.UserId

data class Conversation(
    val userId: UserId,
    val conversationId: ConversationId
)
