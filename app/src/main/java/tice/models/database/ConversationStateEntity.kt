package tice.models.database

import androidx.room.*
import kotlinx.serialization.Serializable
import tice.models.*
import tice.models.messaging.conversation.InboundConversationInvitation
import tice.models.messaging.conversation.InvalidConversation
import tice.models.messaging.conversation.OutboundConversationInvitation
import tice.utility.serializer.DateSerializer
import tice.utility.serializer.UUIDSerializer
import java.util.*

@Entity(primaryKeys = ["userId", "conversationId"])
data class ConversationStateEntity(
    val userId: UserId,
    val conversationId: ConversationId,
    val rootKey: SecretKey,
    val rootChainPublicKey: PublicKey,
    val rootChainPrivateKey: PrivateKey,
    val rootChainRemotePublicKey: PublicKey?,
    val sendingChainKey: SecretKey?,
    val receivingChainKey: SecretKey?,
    val sendMessageNumber: Int,
    val receivedMessageNumber: Int,
    val previousSendingChanLength: Int
)

@Entity(primaryKeys = ["senderId", "conversationId"])
@kotlinx.serialization.Serializable
data class ReceivedReset(
    @Serializable(with = UUIDSerializer::class)
    val senderId: UserId,
    @Serializable(with = UUIDSerializer::class)
    val conversationId: ConversationId,
    @Serializable(with = DateSerializer::class)
    val timestamp: Date
)

@Dao
interface ConversationInterface {
    @Query("SELECT * FROM conversationStateEntity WHERE userId=:userId AND conversationId=:conversationId limit 1")
    suspend fun get(userId: UserId, conversationId: ConversationId): ConversationStateEntity?

    @Query("SELECT * FROM conversationStateEntity")
    suspend fun getAll(): List<ConversationStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ConversationStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entities: List<ConversationStateEntity>)

    @Query("DELETE FROM conversationStateEntity")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: OutboundConversationInvitation)

    @Query("SELECT * FROM outboundConversationInvitation WHERE receiverId=:receiverId AND conversationId=:conversationId")
    suspend fun outboundConversationInvitation(receiverId: UserId, conversationId: ConversationId): OutboundConversationInvitation?

    @Query("DELETE FROM outboundConversationInvitation WHERE receiverId=:receiverId AND conversationId=:conversationId")
    suspend fun deleteOutboundConversationInvitation(receiverId: UserId, conversationId: ConversationId)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: InboundConversationInvitation)

    @Query("SELECT * FROM inboundConversationInvitation WHERE senderId=:senderId AND conversationId=:conversationId")
    suspend fun inboundConversationInvitation(senderId: UserId, conversationId: ConversationId): InboundConversationInvitation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ReceivedReset)

    @Query("SELECT * FROM receivedReset WHERE senderId=:senderId AND conversationId=:conversationId")
    suspend fun receivedReset(senderId: UserId, conversationId: ConversationId): ReceivedReset?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: InvalidConversation)

    @Query("SELECT * FROM invalidConversation WHERE senderId=:userId AND conversationId=:conversationId")
    suspend fun invalidConversation(userId: UserId, conversationId: ConversationId): InvalidConversation?

    @Query("UPDATE invalidConversation SET resendResetTimeout=:resendResetTimeout WHERE senderId=:userId AND conversationId=:conversationId")
    suspend fun updateInvalidConversation(userId: UserId, conversationId: ConversationId, resendResetTimeout: Date)
}
