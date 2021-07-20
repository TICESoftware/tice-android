package tice.managers

import tice.models.Location

interface LocationManagerDelegate {
    suspend fun processLocationUpdate(location: Location)
}
