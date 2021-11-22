package tice.managers.services

import android.location.LocationListener
import android.os.Looper
import androidx.annotation.RequiresPermission
import com.google.android.gms.location.*
import tice.dagger.scopes.AppScope
import tice.utility.getLogger
import javax.inject.Inject

@AppScope
class GMSLocationService @Inject constructor() : LocationService(), LocationListener {
    private val logger by getLogger()

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)

            onLocationChanged(locationResult.lastLocation)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
    }

    @RequiresPermission(anyOf = ["android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"])
    override fun startLocationTracking() {
        fusedLocationProviderClient.locationAvailability?.addOnSuccessListener {
            logger.debug("Google FusedLocationProviderClient registered successfully.")

            val locationRequest = LocationRequest()
            locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            locationRequest.interval = 3000L
            locationRequest.maxWaitTime = 3000L
            locationRequest.fastestInterval = 3000L

            fusedLocationProviderClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.myLooper()
            )
        }
    }

    override fun stopLocationTracking() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
    }
}
