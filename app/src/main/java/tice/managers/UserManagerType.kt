package tice.managers

import tice.models.User
import tice.models.UserId

interface UserManagerType {

    suspend fun getUser(userId: UserId): User?
    suspend fun getOrFetchUser(userId: UserId): User
}
