package tice.managers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import tice.dagger.scopes.AppScope
import tice.managers.messaging.PostOfficeType
import tice.managers.storageManagers.LocationSharingStorageManagerType
import tice.models.*
import tice.utility.getLogger
import tice.utility.provider.CoroutineContextProviderType
import java.lang.ref.WeakReference
import java.util.*
import javax.inject.Inject
import kotlin.concurrent.timerTask

@AppScope
class LocationManager @Inject constructor(
    private val postOffice: PostOfficeType,
    private val locationServiceController: LocationServiceControllerType,
    private val coroutineContextProvider: CoroutineContextProviderType,
    private val signedInUserManager: SignedInUserManagerType,
    private val userManager: UserManagerType,
    private val locationSharingStorageManager: LocationSharingStorageManagerType
) : LocationManagerType {
    private val logger by getLogger()

    private var lastOwnLocation: Location? = null
    private val ownLocationFlow = MutableSharedFlow<Location>()

    private var hasMonitoringTeams = false

    private var locationTimer = Timer()
    private var timerRunning = false

    override var delegate: WeakReference<LocationManagerDelegate>? = null

    override fun startMonitoringSharingStates(scope: CoroutineScope): Job {
        return scope.launch(coroutineContextProvider.IO) {
            locationSharingStorageManager.getAllStatesFlow().collect { sharingStates ->

                val signedInUserId = try {
                    signedInUserManager.signedInUser.userId
                } catch (e: Exception) {
                    return@collect
                }

                val ownEnabledStates = sharingStates.filter {
                    it.userId == signedInUserId && it.sharingEnabled
                }

                hasMonitoringTeams = ownEnabledStates.isNotEmpty()

                if (hasMonitoringTeams) {
                    locationServiceController.promoteToForeground()
                } else {
                    locationServiceController.demotetoBackground()
                }
            }
        }
    }

    override fun getOwnLocationUpdateFlow(): SharedFlow<Location> = ownLocationFlow.onSubscription {
        if (!locationServiceController.locationServiceRunning) {
            logger.debug("Restart location service.")
            locationServiceController.startLocationService()
        }

        lastOwnLocation?.let { emit(it) }
    }

    override suspend fun processLocationUpdate(location: Location) {
        if (timerRunning) {
            locationTimer.cancel()
            timerRunning = false
        } else {
            locationTimer = Timer()
            locationTimer.schedule(
                timerTask {
                    CoroutineScope(coroutineContextProvider.Default).launch {
                        locationUpdate(location)
                    }
                },
                60000
            )

            timerRunning = true
        }

        locationUpdate(location)
    }

    private suspend fun locationUpdate(location: Location) {
        lastOwnLocation = location
        ownLocationFlow.emit(location)

        if (hasMonitoringTeams) {
            delegate?.get()?.processLocationUpdate(location)
        }
    }
}
