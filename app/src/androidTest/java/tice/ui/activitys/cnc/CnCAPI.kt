package tice.ui.activitys.cnc

import tice.models.GroupId
import tice.models.UserId
import tice.models.responses.CreateUserResponse

interface CnCAPI {
    suspend fun createUser(): CreateUserResponse
    suspend fun joinGroup(userId: UserId, groupId: GroupId, groupKey: String)
    suspend fun changeUserName(userId: UserId, name: String)
}