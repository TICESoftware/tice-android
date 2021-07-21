package tice.ui.viewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import tice.TICEApplication
import tice.managers.storageManagers.VersionCodeStorageManager
import tice.managers.storageManagers.migration.MigrationManager
import tice.utility.getLogger
import javax.inject.Inject

class MigrationViewModel @Inject constructor(
    private val application: TICEApplication,
) : ViewModel() {
    val logger by getLogger()

    private val versionCodeStorageManager = VersionCodeStorageManager(application.baseContext)
    private val migrationManager = MigrationManager(application, versionCodeStorageManager)

    private val _state = MutableLiveData<MigrationState>(MigrationState.InitialState)
    val state: LiveData<MigrationState>
        get() = _state

    fun initializeMigration() {
        try {
            _state.postValue(MigrationState.Migrating)
            migrationManager.executeMigrationsBlocking(application.applicationContext)
            _state.postValue(MigrationState.Finished)
        } catch (e: Exception) {
            logger.error(e.message)
            _state.postValue(MigrationState.Error)
        }
    }

    sealed class MigrationState {
        object InitialState : MigrationState()
        object Finished : MigrationState()
        object Migrating : MigrationState()
        object Error : MigrationState()
    }
}
