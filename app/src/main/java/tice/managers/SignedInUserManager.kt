package tice.managers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import tice.dagger.scopes.AppScope
import tice.exceptions.SignedInUserStorageManagerException
import tice.managers.group.GroupManagerType
import tice.managers.storageManagers.GroupStorageManagerType
import tice.managers.storageManagers.SignedInUserStorageManagerType
import tice.managers.storageManagers.UserStorageManagerType
import tice.models.SignedInUser
import tice.models.User
import tice.models.messaging.MessagePriority
import tice.models.messaging.Payload
import tice.models.messaging.PayloadContainer
import tice.models.messaging.UserUpdate
import tice.utility.getLogger
import tice.utility.provider.CoroutineContextProviderType
import java.lang.ref.WeakReference
import javax.inject.Inject

@AppScope
class SignedInUserManager @Inject constructor(
    private val storageManager: SignedInUserStorageManagerType,
    private val userStorageManager: UserStorageManagerType,
    private val groupStorageManager: GroupStorageManagerType,
    private val coroutineContextProvider: CoroutineContextProviderType
) : SignedInUserManagerType {
    val logger by getLogger()

    override lateinit var groupManagerDelegate: WeakReference<GroupManagerType>
    private lateinit var initJob: Job

    override fun setup() {
        initJob = CoroutineScope(coroutineContextProvider.IO).launch {
            try {
                prefetchSignedInUser()
            } catch (e: SignedInUserStorageManagerException.NotSignedIn) {
                logger.info("User not signed in.")
            } catch (e: Exception) {
                logger.error("Loading SignedInUser failed.")
            }
        }
    }

    private var _signedInUser: SignedInUser? = null
    override val signedInUser: SignedInUser
        get() = _signedInUser ?: runBlocking {
            initJob.join()
            return@runBlocking _signedInUser ?: prefetchSignedInUser()
        }

    private suspend fun prefetchSignedInUser(): SignedInUser {
        val newSignedInUser = storageManager.loadSignedInUser()
        _signedInUser = newSignedInUser
        return newSignedInUser
    }

    override fun signedIn(): Boolean {
        try {
            signedInUser
        } catch (e: Exception) {
            return false
        }
        return true
    }

    override suspend fun storeSignedInUser(newSignedInUser: SignedInUser) {
        _signedInUser = newSignedInUser
        storageManager.storeSignedInUser(newSignedInUser)
        userStorageManager.store(User(newSignedInUser.userId, newSignedInUser.publicSigningKey, newSignedInUser.publicName))
    }

    override suspend fun changeSignedInUserName(publicName: String) {
        signedInUser.publicName = publicName
        storeSignedInUser(signedInUser)

        val teams = groupStorageManager.loadTeams()

        teams.forEach { team ->
            groupManagerDelegate.get()?.send(
                PayloadContainer(Payload.PayloadType.UserUpdateV1, UserUpdate(this.signedInUser.userId)),
                team,
                null,
                MessagePriority.Alert
            )
        }
    }

    override suspend fun deleteSignedInUser() {
        storageManager.deleteSignedInUser()
        _signedInUser = null
    }
}
