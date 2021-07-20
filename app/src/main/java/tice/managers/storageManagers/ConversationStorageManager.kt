package tice.managers.storageManagers

import tice.dagger.scopes.AppScope
import tice.models.ConversationFingerprint
import tice.models.ConversationId
import tice.models.UserId
import tice.models.database.ReceivedReset
import tice.models.messaging.conversation.ConversationInvitation
import tice.models.messaging.conversation.InboundConversationInvitation
import tice.models.messaging.conversation.InvalidConversation
import tice.models.messaging.conversation.OutboundConversationInvitation
import java.util.*
import javax.inject.Inject

@AppScope
class ConversationStorageManager @Inject constructor(db: AppDatabase) : ConversationStorageManagerType {
    private val conversationInterface = db.conversationStateInterface()

    override suspend fun storeOutboundConversationInvitation(
        receiverId: UserId,
        conversationId: ConversationId,
        conversationInvitation: ConversationInvitation
    ) {
        val outboundConversationInvitation = OutboundConversationInvitation(
            receiverId,
            conversationId,
            conversationInvitation.identityKey,
            conversationInvitation.ephemeralKey,
            conversationInvitation.usedOneTimePrekey
        )
        conversationInterface.insert(outboundConversationInvitation)
    }

    override suspend fun outboundConversationInvitation(
        receiverId: UserId,
        conversationId: ConversationId
    ): ConversationInvitation? {
        return conversationInterface.outboundConversationInvitation(receiverId, conversationId)?.conversationInvitation()
    }

    override suspend fun deleteOutboundConversationInvitation(
        receiverId: UserId,
        conversationId: ConversationId
    ) {
        conversationInterface.deleteOutboundConversationInvitation(receiverId, conversationId)
    }

    override suspend fun storeInboundConversationInvitation(
        senderId: UserId,
        conversationId: ConversationId,
        conversationInvitation: ConversationInvitation,
        timestamp: Date
    ) {
        val inboundConversationInvitation = InboundConversationInvitation(
            senderId,
            conversationId,
            conversationInvitation.identityKey,
            conversationInvitation.ephemeralKey,
            conversationInvitation.usedOneTimePrekey,
            timestamp
        )
        conversationInterface.insert(inboundConversationInvitation)
    }

    override suspend fun inboundConversationInvitation(
        senderId: UserId,
        conversationId: ConversationId
    ): InboundConversationInvitation? {
        return conversationInterface.inboundConversationInvitation(senderId, conversationId)
    }

    override suspend fun storeReceivedReset(
        senderId: UserId,
        conversationId: ConversationId,
        timestamp: Date
    ) {
        val receivedReset = ReceivedReset(senderId, conversationId, timestamp)
        conversationInterface.insert(receivedReset)
    }

    override suspend fun receivedReset(senderId: UserId, conversationId: ConversationId): Date? {
        return conversationInterface.receivedReset(senderId, conversationId)?.timestamp
    }

    override suspend fun storeInvalidConversation(
        userId: UserId,
        conversationId: ConversationId,
        conversationFingerprint: ConversationFingerprint,
        timestamp: Date,
        resendResetTimeout: Date
    ) {
        val invalidConversation = InvalidConversation(userId, conversationId, conversationFingerprint, timestamp, resendResetTimeout)
        conversationInterface.insert(invalidConversation)
    }

    override suspend fun invalidConversation(
        userId: UserId,
        conversationId: ConversationId
    ): InvalidConversation? {
        return conversationInterface.invalidConversation(userId, conversationId)
    }

    override suspend fun updateInvalidConversation(
        userId: UserId,
        conversationId: ConversationId,
        resendResetTimeout: Date
    ) {
        conversationInterface.updateInvalidConversation(userId, conversationId, resendResetTimeout)
    }
}
