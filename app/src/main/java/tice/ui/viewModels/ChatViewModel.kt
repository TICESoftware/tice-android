package tice.ui.viewModels

import androidx.lifecycle.*
import androidx.paging.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import tice.managers.ChatManagerType
import tice.managers.SignedInUserManagerType
import tice.managers.UserManagerType
import tice.managers.storageManagers.ChatStorageManagerType
import tice.managers.storageManagers.GroupStorageManagerType
import tice.models.GroupId
import tice.models.Team
import tice.models.chat.Message
import tice.models.chat.MessageStatus
import tice.models.database.message
import tice.ui.models.ChatMessagesRepresentation
import tice.ui.models.ChatMessagesRepresentation.Direction
import tice.ui.models.GroupNameData
import tice.utility.getLogger
import tice.utility.provider.CoroutineContextProviderType
import tice.utility.provider.NameProviderType
import tice.utility.provider.UserDataGeneratorType
import java.util.*
import javax.inject.Inject

class ChatViewModel @Inject constructor(
    private val coroutineContextProvider: CoroutineContextProviderType,
    private val groupStorageManager: GroupStorageManagerType,
    private val nameProvider: NameProviderType,
    private val userDataGenerator: UserDataGeneratorType,
    private val signedInUserManager: SignedInUserManagerType,
    private val chatManager: ChatManagerType,
    private val chatStorageManager: ChatStorageManagerType,
    private val userManager: UserManagerType
) : ViewModel() {
    private val logger by getLogger()

    private val _teamName = MediatorLiveData<GroupNameData>()
    val teamName: LiveData<GroupNameData>
        get() = _teamName

    lateinit var paging: LiveData<PagingData<ChatMessagesRepresentation>>
    lateinit var groupId: GroupId
    var team: Team? = null

    private val _event = MutableSharedFlow<ChatEvent>()
    val event: SharedFlow<ChatEvent>
        get() = _event

    fun setUpData(groupId: GroupId) {
        this.groupId = groupId
        _teamName.addSource(groupStorageManager.getTeamObservable(groupId)) {
            this.team = it

            viewModelScope.launch(coroutineContextProvider.IO) {
                it?.let { _teamName.postValue(nameProvider.getTeamName(it)) }
                    ?: _event.emit(ChatEvent.NoTeam)
            }
        }

        viewModelScope.launch(coroutineContextProvider.IO) {
            chatManager.markAllAsRead(groupId)
        }

        setupPager(groupId)
    }

    private fun setupPager(groupId: GroupId) {
        paging = Pager(PagingConfig(pageSize = 15)) {
            chatStorageManager.groupMessagePagingSource(groupId)
        }.liveData.map { pagingData ->
            val signedInUserId = signedInUserManager.signedInUser.userId
            pagingData.map {
                val message = it.message()

                if (!message.read) {
                    viewModelScope.launch(coroutineContextProvider.IO) {
                        chatManager.markAsRead(message.messageId)
                    }
                }

                when (message) {
                    is Message.TextMessage -> {
                        val user = userManager.getUser(message.senderId) ?: throw Exception()
                        val color = userDataGenerator.generateColor(user.userId)
                        val shortName = nameProvider.getShortName(nameProvider.getUserName(user))
                        ChatMessagesRepresentation.MessageItem.TextMessage(
                            it.messageId,
                            it.date,
                            it.read,
                            shortName,
                            color,
                            true,
                            if (user.userId != signedInUserId) Direction.Incoming else Direction.Outgoing,
                            message.text
                        )
                    }
                    is Message.ImageMessage -> {
                        val user = userManager.getUser(message.senderId) ?: throw Exception()
                        val color = userDataGenerator.generateColor(user.userId)
                        val shortName = nameProvider.getShortName(nameProvider.getUserName(user))
                        ChatMessagesRepresentation.MessageItem.ImageMessage(
                            it.messageId,
                            it.date,
                            it.read,
                            shortName,
                            color,
                            true,
                            if (user.userId != signedInUserId) Direction.Incoming else Direction.Outgoing,
                            message.imageData
                        )
                    }
                    is Message.MetaMessage -> ChatMessagesRepresentation.MetaInfo(message.date, message.text)
                }
            }.insertSeparators { message1, message2 ->
                message1 ?: return@insertSeparators null
                message2 ?: return@insertSeparators null

                return@insertSeparators when {
                    message1 !is ChatMessagesRepresentation.MessageItem -> null
                    message2 !is ChatMessagesRepresentation.MessageItem -> null
                    message1.senderName == message2.senderName -> {
                        message2.isLastOfUser = false
                        null
                    }
                    else -> null
                }
            }.insertSeparators { chatMessageModel, chatMessageModel2 ->
                if (!isSameDay(chatMessageModel?.date, chatMessageModel2?.date)) {
                    return@insertSeparators ChatMessagesRepresentation.DateSeparator(chatMessageModel!!.date)
                }
                null
            }
        }
    }

    private fun isSameDay(date1: Date?, date2: Date?): Boolean {
        date1 ?: return true
        date2 ?: return false

        val calendar1 = Calendar.getInstance()
        calendar1.time = date1
        val calendar2 = Calendar.getInstance()
        calendar2.time = date2
        return calendar1.get(Calendar.YEAR) == calendar2.get(Calendar.YEAR) &&
            calendar1.get(Calendar.MONTH) == calendar2.get(Calendar.MONTH) &&
            calendar1.get(Calendar.DAY_OF_MONTH) == calendar2.get(Calendar.DAY_OF_MONTH)
    }

    fun addMessage(messageText: String) {
        viewModelScope.launch(coroutineContextProvider.IO) {
            val textMessage = Message.TextMessage(
                UUID.randomUUID(),
                groupId,
                signedInUserManager.signedInUser.userId,
                Date(),
                true,
                MessageStatus.Sending,
                messageText
            )

            try {
                chatManager.send(textMessage, team!!)
            } catch (e: Exception) {
                logger.error(e.message ?: "textMessage could net get send: $textMessage", e)
            }
        }
    }

    sealed class ChatEvent {
        object NoTeam : ChatEvent()
        object Error : ChatEvent()
    }
}
