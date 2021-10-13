package tice.utility

import com.google.android.gms.maps.model.LatLng
import tice.models.Coordinates

val Coordinates.latLng: LatLng
    get() = LatLng(latitude, longitude)

fun LatLng.coordinates() = Coordinates(latitude, longitude)
