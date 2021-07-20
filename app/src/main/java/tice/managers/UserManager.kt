package tice.managers

import tice.backend.BackendType
import tice.dagger.scopes.AppScope
import tice.exceptions.UnexpectedPayloadTypeException
import tice.managers.messaging.PostOfficeType
import tice.managers.messaging.notificationHandler.PayloadReceiver
import tice.managers.storageManagers.UserStorageManagerType
import tice.models.User
import tice.models.UserId
import tice.models.messaging.Payload
import tice.models.messaging.PayloadContainerBundle
import tice.models.messaging.UserUpdate
import tice.utility.getLogger
import javax.inject.Inject

@AppScope
class UserManager @Inject constructor(
    private val postOffice: PostOfficeType,
    private val backend: BackendType,
    private val signedInUserManager: SignedInUserManagerType,
    private val userStorageManager: UserStorageManagerType
) : UserManagerType, PayloadReceiver {
    private val logger by getLogger()

    override fun registerEnvelopeReceiver() {
        postOffice.registerEnvelopeReceiver(Payload.PayloadType.UserUpdateV1, this)
    }

    override suspend fun getUser(userId: UserId): User? {
        if (signedInUserManager.signedInUser.userId == userId) {
            return signedInUserManager.signedInUser
        }

        return userStorageManager.loadUser(userId)
    }

    override suspend fun getOrFetchUser(userId: UserId): User {
        getUser(userId)?.let { return it }
        return fetchUser(userId)
    }

    private suspend fun fetchUser(userId: UserId): User {
        val userResponse = backend.getUser(userId)
        val user = User(userId, userResponse.publicSigningKey, userResponse.publicName)

        userStorageManager.store(user)

        return user
    }

    override suspend fun handlePayloadContainerBundle(bundle: PayloadContainerBundle) {
        val userUpdate: UserUpdate = bundle.payload as? UserUpdate ?: throw UnexpectedPayloadTypeException
        try {
            fetchUser(userUpdate.userId)
        } catch (e: Exception) {
            logger.error("User Update for user ${userUpdate.userId} failed.", e)
        }
    }
}
