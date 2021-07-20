package tice.managers

import tice.models.GroupId
import tice.models.MessageId
import tice.models.Team
import tice.models.chat.Message

interface ChatManagerType {
    suspend fun send(message: Message.TextMessage, team: Team)
    suspend fun add(message: Message)

    suspend fun markAsRead(messageId: MessageId)
    suspend fun markAllAsRead(groupId: GroupId)
}
