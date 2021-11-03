package tice.utility

import com.google.android.gms.maps.model.LatLng
import com.mapbox.geojson.Point
import tice.models.Coordinates

val Coordinates.latLng: LatLng
    get() = LatLng(latitude, longitude)

fun LatLng.coordinates() = Coordinates(latitude, longitude)

fun Point.pointCoordinates() = Coordinates(latitude(), longitude())
fun Coordinates.point(): Point = Point.fromLngLat(longitude, latitude)