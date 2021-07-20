package tice.managers.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.ticeapp.TICE.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import tice.TICEApplication
import tice.dagger.scopes.AppScope
import tice.managers.LocationManagerType
import tice.managers.LocationServiceControllerType
import tice.models.Location
import tice.utility.getLogger
import tice.utility.provider.CoroutineContextProviderType
import java.util.*
import javax.inject.Inject

@AppScope
class LocationService @Inject constructor() : Service(), LocationListener {
    private val logger by getLogger()

    @Inject
    lateinit var locationManager: LocationManagerType

    @Inject
    lateinit var coroutineContextProvider: CoroutineContextProviderType

    @Inject
    lateinit var locationServiceController: LocationServiceControllerType

    private val channelId = "LOCATION_SERVICE_ID"

    private val jobSemaphore = Semaphore(1)
    private var nextJob: (suspend () -> Unit)? = null

    private var androidLocationManager: LocationManager? = null

    private var fusedLocationProviderClient: FusedLocationProviderClient? = null
    private var locationRequest: LocationRequest? = null
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)

            onLocationChanged(locationResult.lastLocation)
        }
    }

    override fun onLocationChanged(location: android.location.Location) {
        logger.debug("Receive location update from ${location.provider}.")

        if (jobSemaphore.tryAcquire()) {
            CoroutineScope(coroutineContextProvider.IO).launch {
                doRequest(location)

                val job = nextJob
                nextJob = null
                job?.invoke()

                jobSemaphore.release()
            }
        } else {
            nextJob = {
                doRequest(location)
                nextJob?.invoke()
            }
        }
    }

    private suspend fun doRequest(locationResult: android.location.Location) {
        val location = Location(
            locationResult.latitude,
            locationResult.longitude,
            locationResult.altitude,
            locationResult.accuracy,
            0f,
            Date(locationResult.time)
        )

        try {
            locationManager.processLocationUpdate(location)
        } catch (e: Exception) {
            logger.error("processLocationUpdate failed", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logger.debug("OnStartCommand from LocationService")
        androidLocationManager ?: run {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
            ) {
                logger.error("Location permissions have been denied before. Not requesting location updates.")
                return@run
            }

            androidLocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        }

        startLocationTracking()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (locationServiceController.isForegroundService) {
                logger.debug("Start foreground")
                val channel = NotificationChannel(
                    channelId,
                    getString(R.string.notification_locationService_title),
                    NotificationManager.IMPORTANCE_HIGH
                )
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)

                val notification = NotificationCompat.Builder(this, channelId)
                    .setContentTitle(getString(R.string.notification_locationService_title))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setSmallIcon(R.drawable.ic_logo_tice_small)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()

                startForeground(1, notification)
            } else {
                logger.debug("Stop foreground")
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }

        return START_NOT_STICKY
    }

    @RequiresPermission(anyOf = ["android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"])
    fun startLocationTracking() {
        fusedLocationProviderClient ?: run {
            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        }

        fusedLocationProviderClient?.locationAvailability?.addOnSuccessListener {
            if (it.isLocationAvailable) {
                logger.debug("Register google fusedLocationProviderClient.")

                locationRequest = LocationRequest()
                locationRequest?.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                locationRequest?.interval = 3000L
                locationRequest?.maxWaitTime = 3000L
                locationRequest?.fastestInterval = 3000L

                fusedLocationProviderClient?.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper())
            } else {
                logger.debug("Register android location providers.")
                registerLocationListener()
            }
        }
    }

    override fun onCreate() {
        (application as TICEApplication).appComponent.bind(this)
        super.onCreate()
    }

    override fun onDestroy() {
        logger.debug("destroy LocationService")
        androidLocationManager?.removeUpdates(this)
        fusedLocationProviderClient?.removeLocationUpdates(locationCallback)
        stopSelf(1)
        stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    @RequiresPermission(anyOf = ["android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"])
    private fun registerLocationListener() {
        androidLocationManager?.let { manager ->
            androidLocationManager?.removeUpdates(this)

            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, 0f, this)
                logger.debug("Register GPS_PROVIDER.")
            } else {
                logger.debug("GPS_PROVIDER is disabled.")
            }

            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000, 0f, this)
                logger.debug("Register NETWORK_PROVIDER location provider.")
            } else {
                logger.debug("NETWORK_PROVIDER location provider is disabled.")
            }
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        logger.debug("Status of $provider changed. status: $status | provider: $provider")
    }

    @SuppressLint("MissingPermission")
    override fun onProviderEnabled(provider: String) {
        registerLocationListener()
        logger.debug("$provider got enabled.")
    }

    @SuppressLint("MissingPermission")
    override fun onProviderDisabled(provider: String) {
        registerLocationListener()
        logger.debug("$provider got disabled.")
    }
}
