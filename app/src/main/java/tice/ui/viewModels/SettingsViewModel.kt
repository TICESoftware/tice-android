package tice.ui.viewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import tice.backend.BackendType
import tice.managers.SignedInUserManagerType
import tice.managers.messaging.WebSocketReceiverType
import tice.managers.storageManagers.CryptoStorageManagerType
import tice.managers.storageManagers.DeviceIdStorageManagerType
import tice.managers.storageManagers.GroupStorageManagerType
import tice.managers.storageManagers.LocationSharingStorageManager
import tice.ui.models.GroupNameData
import tice.utility.getLogger
import tice.utility.provider.CoroutineContextProviderType
import tice.utility.provider.UserDataGeneratorType
import javax.inject.Inject

class SettingsViewModel @Inject constructor(
    private val coroutineContextProvider: CoroutineContextProviderType,
    private val signedInUserManager: SignedInUserManagerType,
    private val cryptoStorageManager: CryptoStorageManagerType,
    private val groupStorageManager: GroupStorageManagerType,
    private val locationSharingStorageManager: LocationSharingStorageManager,
    private val backend: BackendType,
    private val webSocketReceiver: WebSocketReceiverType,
    private val userDataGenerator: UserDataGeneratorType,
    private val deviceIdStorageManager: DeviceIdStorageManagerType
) : ViewModel() {
    private val logger by getLogger()

    private val _name = MutableLiveData<GroupNameData>()
    val name: LiveData<GroupNameData>
        get() = _name

    private val _event = MutableSharedFlow<SettingsEvent>()
    val event: SharedFlow<SettingsEvent>
        get() = _event

    init {
        postSignedInUserName()
    }

    private fun postSignedInUserName() {
        val name = signedInUserManager.signedInUser.publicName?.let {
            GroupNameData.TeamName(it)
        } ?: GroupNameData.PseudoName(userDataGenerator.generatePseudonym(signedInUserManager.signedInUser.userId))

        _name.postValue(name)
    }

    fun changeUserName(newName: String) {
        GlobalScope.launch(coroutineContextProvider.IO) {
            try {
                val user = signedInUserManager.signedInUser
                backend.updateUser(user.userId, null, null, null, newName)
                signedInUserManager.changeSignedInUserName(newName)
                postSignedInUserName()
            } catch (e: Exception) {
                logger.error("Changing public name failed.", e)
                _event.emit(SettingsEvent.ErrorEvent.ChangeNameError)
            }
        }
    }

    fun deleteAllData() {
        viewModelScope.launch(coroutineContextProvider.IO) {
            groupStorageManager.loadTeams().let {
                if (it.isNotEmpty()) {
                    logger.error("User still participating in groups.")
                    _event.emit(SettingsEvent.ErrorEvent.InGroupError)
                    return@launch
                }
            }

            try {
                backend.deleteUser(signedInUserManager.signedInUser.userId)
            } catch (e: Exception) {
                logger.error("Deleting user failed.", e)
                _event.emit(SettingsEvent.ErrorEvent.DeleteError)
            }

            signedInUserManager.deleteSignedInUser()
            deviceIdStorageManager.deleteDeviceId()
            cryptoStorageManager.removeAllData()
            groupStorageManager.deleteAllData()
            webSocketReceiver.disconnect()
            _event.emit(SettingsEvent.UserDeleted)
        }
    }

    sealed class SettingsEvent {
        object UserDeleted : SettingsEvent()
        sealed class ErrorEvent() : SettingsEvent() {
            object Error : ErrorEvent()
            object DeleteError : ErrorEvent()
            object InGroupError : ErrorEvent()
            object ChangeNameError : ErrorEvent()
        }
    }
}
