package tice.managers.services

import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import androidx.annotation.RequiresPermission
import tice.dagger.scopes.AppScope
import tice.utility.getLogger
import javax.inject.Inject

@AppScope
class NonGMSLocationService @Inject constructor() : LocationService(), LocationListener {
    private val logger by getLogger()

    private lateinit var androidLocationManager: LocationManager

    override fun onCreate() {
        super.onCreate()
        androidLocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    @RequiresPermission(anyOf = ["android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"])
    override fun startLocationTracking() {
        if (androidLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            androidLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, 0f, this)
            logger.debug("Register GPS_PROVIDER.")
        } else {
            logger.debug("GPS_PROVIDER is disabled.")
        }

        if (androidLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            androidLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000, 0f, this)
            logger.debug("Register NETWORK_PROVIDER location provider.")
        } else {
            logger.debug("NETWORK_PROVIDER location provider is disabled.")
        }
    }

    override fun stopLocationTracking() {
        androidLocationManager.removeUpdates(this)
    }
}
