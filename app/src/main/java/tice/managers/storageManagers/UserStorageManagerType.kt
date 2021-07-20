package tice.managers.storageManagers

import tice.models.User
import tice.models.UserId

interface UserStorageManagerType {
    suspend fun store(user: User)
    suspend fun loadUser(userId: UserId): User?
}
