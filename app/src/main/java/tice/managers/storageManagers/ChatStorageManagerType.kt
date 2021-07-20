package tice.managers.storageManagers

import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow
import tice.models.GroupId
import tice.models.MessageId
import tice.models.chat.Message
import tice.models.database.MessageEntity

interface ChatStorageManagerType {

    fun groupMessagePagingSource(groupId: GroupId): PagingSource<Int, MessageEntity>
    fun unreadMessageCountLiveData(groupId: GroupId): LiveData<Int>

    suspend fun unreadMessageCount(groupId: GroupId): Int
    fun getLastUnreadMessagesFlow(groupId: GroupId): Flow<Message?>
    suspend fun store(messages: List<Message>)
    suspend fun message(messageId: MessageId): Message?
    suspend fun messageCount(groupId: GroupId): Int
    suspend fun messages(groupId: GroupId, offset: Int, limit: Int): List<Message>
    suspend fun unreadMessages(groupId: GroupId): List<Message>
    fun lastMessagePerGroup(): LiveData<Map<GroupId, Message>>
}
