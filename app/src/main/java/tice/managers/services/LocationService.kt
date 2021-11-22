package tice.managers.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.ticeapp.TICE.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import tice.TICEApplication
import tice.managers.LocationManagerType
import tice.managers.LocationServiceControllerType
import tice.models.Location
import tice.utility.getLogger
import tice.utility.provider.CoroutineContextProviderType
import java.util.*
import javax.inject.Inject

abstract class LocationService : Service() {
    private val logger by getLogger()

    @Inject
    lateinit var locationManager: LocationManagerType

    @Inject
    lateinit var locationServiceController: LocationServiceControllerType

    @Inject
    lateinit var coroutineContextProvider: CoroutineContextProviderType

    private val channelId = "LOCATION_SERVICE_ID"
    private val jobSemaphore = Semaphore(1)
    private var nextJob: (suspend () -> Unit)? = null

    override fun onCreate() {
        (application as TICEApplication).appComponent.bind(this)
        super.onCreate()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logger.debug("OnStartCommand in LocationService called.")

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            logger.error("Location permissions have been denied before. Not requesting location updates.")
            return START_NOT_STICKY
        }

        startLocationTracking()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            logger.debug("Call startForeground on Android 8.0+")
            val channel = NotificationChannel(channelId, getString(R.string.notification_locationService_title), NotificationManager.IMPORTANCE_HIGH)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)

            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle(getString(R.string.notification_locationService_title))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSmallIcon(R.drawable.ic_logo_tice_small)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            startForeground(1, notification)
        }

        locationServiceController.locationServiceRunning = true

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        logger.debug("Destroy Location service")
        stopLocationTracking()
        stopSelf(1)
        stopForeground(true)
        locationServiceController.locationServiceRunning = false
        super.onDestroy()
    }

    abstract fun startLocationTracking()
    abstract fun stopLocationTracking()

    fun onLocationChanged(location: android.location.Location) {
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

    suspend fun doRequest(locationResult: android.location.Location) {
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
}
