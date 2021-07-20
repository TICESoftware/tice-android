package tice.managers.storageManagers

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tice.dagger.scopes.AppScope
import tice.models.GroupId
import tice.models.MessageId
import tice.models.chat.Message
import tice.models.database.MessageEntity
import tice.models.database.message
import tice.models.database.messageEntity
import javax.inject.Inject

@AppScope
class ChatStorageManager @Inject constructor(appDatabase: AppDatabase) : ChatStorageManagerType {

    private val chatInterface = appDatabase.chatMessageInterface()

    override fun groupMessagePagingSource(groupId: GroupId): PagingSource<Int, MessageEntity> {
        return chatInterface.loadMessagesFromGroup(groupId)
    }

    override fun unreadMessageCountLiveData(groupId: GroupId): LiveData<Int> {
        return chatInterface.getMessageCountByReadStatusLiveData(groupId, false)
    }

    override suspend fun unreadMessageCount(groupId: GroupId): Int {
        return chatInterface.getMessageCountByReadStatus(groupId, false)
    }

    override fun getLastUnreadMessagesFlow(groupId: GroupId): Flow<Message?> {
        return chatInterface.getLastUnreadMessage(groupId).map { it?.message() }
    }

    override suspend fun store(messages: List<Message>) {
        chatInterface.insert(messages.map { it.messageEntity() })
    }

    override suspend fun message(messageId: MessageId): Message? {
        return chatInterface.getMessageById(messageId)?.message()
    }

    override suspend fun messageCount(groupId: GroupId): Int {
        return chatInterface.getMessageCountFromGroup(groupId)
    }

    override suspend fun messages(groupId: GroupId, offset: Int, limit: Int): List<Message> {
        return chatInterface.getMessagesPage(groupId, offset, limit).map { it.message() }
    }

    override suspend fun unreadMessages(groupId: GroupId): List<Message> {
        return chatInterface.getMessageByReadStatus(groupId, false).map { it.message() }
    }

    override fun lastMessagePerGroup(): LiveData<Map<GroupId, Message>> {
        return chatInterface.getLastMessagePerGroup().map {
            it.associateBy { it.groupId }.mapValues { it.value.message() }
        }
    }
}
