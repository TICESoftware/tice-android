package tice.ui.viewModels

import android.content.pm.PackageManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tice.AppFlow
import tice.TICEApplication
import tice.exceptions.MinVersionException
import tice.managers.UpdateManager
import tice.managers.storageManagers.VersionCodeStorageManager
import tice.utility.getLogger
import javax.inject.Inject

class ForceUpdateViewModel @Inject constructor(
    application: TICEApplication,
) : ViewModel() {
    val logger by getLogger()

    private val appFlow = AppFlow(application)

    private val baseURL = application.packageManager.getApplicationInfo(application.packageName, PackageManager.GET_META_DATA)
        .metaData.getString("base_url")!!

    private val updateManager = UpdateManager(baseURL)
    private val versionCodeStorageManager = VersionCodeStorageManager(application.baseContext)

    private val _state = MutableLiveData<UpdateState>(UpdateState.InitialState)
    val state: LiveData<UpdateState>
        get() = _state

    fun checkForUpdate() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _state.postValue(UpdateState.Loading)
                updateManager.check(versionCodeStorageManager.getStoredVersionCode())

                launch(Dispatchers.Main) {
                    appFlow.initApp()
                }.join()

                _state.postValue(UpdateState.UpdateNotNecessary)
            } catch (e: MinVersionException.Outdated) {
                logger.error("MinVersion is outdated.")
                _state.postValue(UpdateState.UpdateAvailable(e.minVersion))
            } catch (e: Exception) {
                logger.error("Check for update failed. ${e.printStackTrace()}")
                _state.postValue(UpdateState.Error(e))
            }
        }
    }

    fun checkAgain() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.postValue(UpdateState.Loading)
            delay(500)
            checkForUpdate()
        }
    }

    sealed class UpdateState {
        data class UpdateAvailable(val minVersion: Int) : UpdateState()
        object UpdateNotNecessary : UpdateState()
        object InitialState : UpdateState()
        object Loading : UpdateState()
        data class Error(val exception: Exception) : UpdateState()
    }
}
