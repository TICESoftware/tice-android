package tice.managers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import tice.models.*
import java.lang.ref.WeakReference

interface LocationManagerType {

    var delegate: WeakReference<LocationManagerDelegate>?

    suspend fun processLocationUpdate(location: Location)
    fun startMonitoringSharingStates(scope: CoroutineScope): Job
    fun getOwnLocationUpdateFlow(): SharedFlow<Location>
}
