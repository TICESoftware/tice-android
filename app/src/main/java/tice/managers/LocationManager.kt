package tice.managers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tice.dagger.scopes.AppScope
import tice.models.Location
import tice.utility.provider.CoroutineContextProviderType
import java.lang.ref.WeakReference
import java.util.*
import javax.inject.Inject
import kotlin.concurrent.timerTask

@AppScope
class LocationManager @Inject constructor(
    private val coroutineContextProvider: CoroutineContextProviderType,
) : LocationManagerType {
    private var lastOwnLocation: Location? = null

    private var locationTimer = Timer()
    private var timerRunning = false

    override var delegate: WeakReference<LocationManagerDelegate>? = null

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
        delegate?.get()?.processLocationUpdate(location)
    }
}
