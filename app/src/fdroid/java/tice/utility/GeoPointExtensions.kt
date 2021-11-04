package tice.utility

import org.osmdroid.util.GeoPoint
import tice.models.Coordinates

fun android.location.Location.coordinates(): Coordinates = Coordinates(latitude, longitude)
fun GeoPoint.coordinates(): Coordinates = Coordinates(latitude, longitude)
fun Coordinates.geoPoint(): GeoPoint = GeoPoint(latitude, longitude)