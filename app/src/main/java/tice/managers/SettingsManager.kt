package tice.managers

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.android.gms.maps.model.LatLng
import com.ticeapp.TICE.R
import tice.dagger.scopes.AppScope
import tice.models.CameraSettings
import javax.inject.Inject
import javax.inject.Named

@AppScope
class SettingsManager @Inject constructor(
    @Named("SETTINGS_PREFS") private val sharedPrefs: SharedPreferences,
    context: Context
) : SettingsManagerType {

    private val lastCameraPositionLatitudeKey = context.getString(R.string.lastCameraPositionKeyLat)
    private val lastCameraPositionLongitudeKey = context.getString(R.string.lastCameraPositionKeyLng)
    private val lastCameraPositionZoomKey = context.getString(R.string.lastCameraPositionKeyZoom)
    private val lastCameraPositionTiltKey = context.getString(R.string.lastCameraPositionKeyTilt)
    private val lastCameraPositionBearingKey = context.getString(R.string.lastCameraPositionKeyBearing)

    override var cameraSettings: CameraSettings
        get() = getLastCameraLocationObject()
        set(value) = setLastCameraLocationObject(value)

    private fun getLastCameraLocationObject(): CameraSettings {
        val firstValue = sharedPrefs.getFloat(lastCameraPositionLatitudeKey, 0f).toDouble()
        val secondValue = sharedPrefs.getFloat(lastCameraPositionLongitudeKey, 0f).toDouble()
        val zoom = sharedPrefs.getFloat(lastCameraPositionZoomKey, 0F)
        val tilt = sharedPrefs.getFloat(lastCameraPositionTiltKey, 0F)
        val baering = sharedPrefs.getFloat(lastCameraPositionBearingKey, 0F)

        return CameraSettings(LatLng(firstValue, secondValue), zoom, tilt, baering)
    }

    private fun setLastCameraLocationObject(settings: CameraSettings) {
        sharedPrefs.edit {
            putFloat(lastCameraPositionLatitudeKey, settings.latLng.latitude.toFloat())
            putFloat(lastCameraPositionLongitudeKey, settings.latLng.longitude.toFloat())
            putFloat(lastCameraPositionZoomKey, settings.zoom)
            putFloat(lastCameraPositionTiltKey, settings.tilt)
            putFloat(lastCameraPositionBearingKey, settings.bearing)
        }
    }
}
