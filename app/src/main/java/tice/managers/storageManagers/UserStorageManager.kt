package tice.managers.storageManagers

import tice.dagger.scopes.AppScope
import tice.models.User
import tice.models.UserId
import javax.inject.Inject

@AppScope
class UserStorageManager @Inject constructor(db: AppDatabase) : UserStorageManagerType {
    private val userInterface = db.userInterface()

    override suspend fun store(user: User) {
        userInterface.insert(user)
    }

    override suspend fun loadUser(userId: UserId): User? {
        return userInterface.get(userId)
    }
}
