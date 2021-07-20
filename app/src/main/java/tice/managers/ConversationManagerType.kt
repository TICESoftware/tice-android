package tice.managers

import tice.models.Ciphertext
import tice.models.Data
import tice.models.UserId
import tice.models.messaging.PayloadContainerBundle
import tice.models.messaging.conversation.ConversationInvitation

interface ConversationManagerType {
    fun setDelegate(conversationManagerDelegate: ConversationManagerDelegate)

    suspend fun conversationInvitation(userId: UserId, collapsing: Boolean): ConversationInvitation?

    suspend fun encrypt(data: Data, userId: UserId, collapsing: Boolean): Ciphertext
    suspend fun decrypt(bundle: PayloadContainerBundle): PayloadContainerBundle
}
