package tice.models

import com.google.android.gms.maps.model.LatLng

data class CameraSettings(val latLng: LatLng, val zoom: Float, val tilt: Float, val bearing: Float)
