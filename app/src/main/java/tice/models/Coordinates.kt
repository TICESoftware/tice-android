package tice.models

import com.mapbox.geojson.Point

data class Coordinates(val latitude: Double, val longitude: Double) {
    val point: Point = Point.fromLngLat(longitude, latitude)
}

fun Point.pointCoordinates() = Coordinates(latitude(), longitude())
