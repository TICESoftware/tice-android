package tice.models.database

import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import tice.models.Data
import tice.models.GroupId
import tice.models.MessageId
import tice.models.UserId
import tice.models.chat.Message
import tice.models.chat.MessageStatus
import java.util.*

@Entity
data class MessageEntity(
    @PrimaryKey
    val messageId: MessageId,
    val groupId: GroupId,
    val senderId: UserId,
    val date: Date,

    var read: Boolean,
    var status: MessageStatus,
    val messageType: MessageType,

    var text: String?,
    var data: Data?
) {
    enum class MessageType { TextMessage, ImageMessage, MetaMessage }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MessageEntity

        if (messageId != other.messageId) return false
        if (groupId != other.groupId) return false
        if (senderId != other.senderId) return false
        if (date != other.date) return false
        if (read != other.read) return false
        if (status != other.status) return false
        if (messageType != other.messageType) return false
        if (text != other.text) return false
        if (data != null) {
            if (other.data == null) return false
            if (!data!!.contentEquals(other.data!!)) return false
        } else if (other.data != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = messageId.hashCode()
        result = 31 * result + groupId.hashCode()
        result = 31 * result + senderId.hashCode()
        result = 31 * result + date.hashCode()
        result = 31 * result + read.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + messageType.hashCode()
        result = 31 * result + (text?.hashCode() ?: 0)
        result = 31 * result + (data?.contentHashCode() ?: 0)
        return result
    }
}

@Dao
interface ChatMessageInterface {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: List<MessageEntity>)

    @Query("SELECT * FROM MessageEntity WHERE messageId=:messageId")
    fun getMessageById(messageId: MessageId): MessageEntity?

    @Query("SELECT * FROM MessageEntity WHERE groupId=:groupId ORDER BY date DESC")
    fun loadMessagesFromGroup(groupId: GroupId): PagingSource<Int, MessageEntity>

    @Query("SELECT * FROM MessageEntity WHERE groupId=:groupId AND read = 0 ORDER BY date DESC LIMIT 1")
    fun getLastUnreadMessage(groupId: GroupId): Flow<MessageEntity?>

    @Query("SELECT * FROM MessageEntity WHERE groupId=:groupId AND read=:readStatus")
    suspend fun getMessageByReadStatus(groupId: GroupId, readStatus: Boolean): List<MessageEntity>

    @Query("SELECT * FROM MessageEntity WHERE groupId=:groupId LIMIT :limit OFFSET :offset")
    suspend fun getMessagesPage(groupId: GroupId, offset: Int, limit: Int): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM MessageEntity WHERE groupId=:groupId")
    suspend fun getMessageCountFromGroup(groupId: GroupId): Int

    @Query("SELECT COUNT(*) FROM MessageEntity WHERE groupId=:groupId AND read=:readStatus")
    suspend fun getMessageCountByReadStatus(groupId: GroupId, readStatus: Boolean): Int

    @Query("SELECT COUNT(*) FROM MessageEntity WHERE groupId=:groupId AND read=:readStatus")
    fun getMessageCountByReadStatusLiveData(groupId: GroupId, readStatus: Boolean): LiveData<Int>

    @Query("SELECT messageId, groupId, senderId, MAX(date) as date, read, status, messageType, text, data FROM MessageEntity GROUP BY groupId")
    fun getLastMessagePerGroup(): LiveData<List<MessageEntity>>

    @Query("DELETE FROM MessageEntity WHERE groupId=:groupId")
    suspend fun deleteAll(groupId: GroupId)

    @Query("DELETE FROM MessageEntity")
    suspend fun deleteAll()
}

fun Message.messageEntity(): MessageEntity {
    return when (this) {
        is Message.TextMessage -> MessageEntity(messageId, groupId, senderId, date, read, status, MessageEntity.MessageType.TextMessage, text, null)
        is Message.ImageMessage -> MessageEntity(messageId, groupId, senderId, date, read, status, MessageEntity.MessageType.ImageMessage, null, imageData)
        is Message.MetaMessage -> MessageEntity(messageId, groupId, senderId, date, read, status, MessageEntity.MessageType.MetaMessage, text, null)
    }
}

fun MessageEntity.message(): Message {
    return when (messageType) {
        MessageEntity.MessageType.TextMessage -> Message.TextMessage(messageId, groupId, senderId, date, read, status, text!!)
        MessageEntity.MessageType.ImageMessage -> Message.ImageMessage(messageId, groupId, senderId, date, read, status, data!!)
        MessageEntity.MessageType.MetaMessage -> Message.MetaMessage(messageId, groupId, senderId, date, read, status, text!!)
    }
}
