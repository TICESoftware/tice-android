package tice.models.database

import androidx.room.*
import tice.models.ConversationId
import java.util.*

@Entity(primaryKeys = ["conversationId", "messageNumber", "publicKey"])
data class MessageKeyCacheEntry(
    val conversationId: ConversationId,
    val messageKey: ByteArray,
    val messageNumber: Int,
    val publicKey: ByteArray,
    val timestamp: Date
)

@Dao
interface MessageKeyCacheInterface {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MessageKeyCacheEntry)

    @Query("SELECT messageKey FROM messageKeyCacheEntry WHERE conversationId=:conversationId AND messageNumber=:messageNumber AND publicKey=:publicKey LIMIT 1")
    suspend fun getMessageKey(conversationId: ConversationId, messageNumber: Int, publicKey: ByteArray): ByteArray?

    @Query("DELETE FROM messageKeyCacheEntry WHERE conversationId=:conversationId AND messageNumber=:messageNumber AND publicKey=:publicKey")
    suspend fun deleteMessageKey(conversationId: ConversationId, messageNumber: Int, publicKey: ByteArray)

    @Query("DELETE FROM messageKeyCacheEntry WHERE timestamp < :threshold")
    suspend fun deleteMessageKeys(threshold: Date)

    @Query("DELETE FROM messageKeyCacheEntry WHERE timestamp < :threshold")
    suspend fun deleteMessageKeysOlderThanAnHour(threshold: Date)

    @Query("DELETE FROM messageKeyCacheEntry")
    suspend fun deleteAll()
}
