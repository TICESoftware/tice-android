package tice.managers

import tice.models.Location
import java.lang.ref.WeakReference

interface LocationManagerType {

    var delegate: WeakReference<LocationManagerDelegate>?

    suspend fun processLocationUpdate(location: Location)
}
