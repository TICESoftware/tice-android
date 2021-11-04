package tice.managers

import tice.models.Coordinates

interface MapboxGeocodingManagerType {
    suspend fun reverseGeocoding(coordinates: Coordinates): String
}