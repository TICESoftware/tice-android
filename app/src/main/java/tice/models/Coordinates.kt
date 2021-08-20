package tice.models

import com.google.android.gms.maps.model.LatLng
import com.mapbox.geojson.Point

data class Coordinates(val latitude: Double, val longitude: Double) {
    val latLng = LatLng(latitude, longitude)
    val point: Point = Point.fromLngLat(longitude, latitude)
}

fun LatLng.coordinates() = Coordinates(latitude, longitude)
fun Point.pointCoordinates() = Coordinates(latitude(), longitude())