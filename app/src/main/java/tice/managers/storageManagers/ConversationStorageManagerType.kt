package tice.managers.storageManagers

import tice.models.ConversationFingerprint
import tice.models.ConversationId
import tice.models.UserId
import tice.models.messaging.conversation.ConversationInvitation
import tice.models.messaging.conversation.InboundConversationInvitation
import tice.models.messaging.conversation.InvalidConversation
import java.util.*

interface ConversationStorageManagerType {
    suspend fun storeOutboundConversationInvitation(receiverId: UserId, conversationId: ConversationId, conversationInvitation: ConversationInvitation)
    suspend fun outboundConversationInvitation(receiverId: UserId, conversationId: ConversationId): ConversationInvitation?
    suspend fun deleteOutboundConversationInvitation(receiverId: UserId, conversationId: ConversationId)

    suspend fun storeInboundConversationInvitation(senderId: UserId, conversationId: ConversationId, conversationInvitation: ConversationInvitation, timestamp: Date)
    suspend fun inboundConversationInvitation(senderId: UserId, conversationId: ConversationId): InboundConversationInvitation?

    suspend fun storeReceivedReset(senderId: UserId, conversationId: ConversationId, timestamp: Date)
    suspend fun receivedReset(senderId: UserId, conversationId: ConversationId): Date?

    suspend fun storeInvalidConversation(userId: UserId, conversationId: ConversationId, conversationFingerprint: ConversationFingerprint, timestamp: Date, resendResetTimeout: Date)
    suspend fun invalidConversation(userId: UserId, conversationId: ConversationId): InvalidConversation?
    suspend fun updateInvalidConversation(userId: UserId, conversationId: ConversationId, resendResetTimeout: Date)
}
