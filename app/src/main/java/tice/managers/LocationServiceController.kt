package tice.managers

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import tice.dagger.scopes.AppScope
import tice.managers.services.LocationService
import javax.inject.Inject

@AppScope
class LocationServiceController @Inject constructor(private val context: Context) : LocationServiceControllerType {

    override var locationServiceRunning = false
    override var isForegroundService: Boolean = false

    override fun startLocationService() {
        locationServiceRunning = true
        val intent = Intent(context, LocationService::class.java)

        context.startService(intent)
    }

    override fun stopLocationService() {
        context.stopService(Intent(context, LocationService::class.java))
        locationServiceRunning = false
    }

    override fun restartService() {
        val intent = Intent(context, LocationService::class.java)

        if (locationServiceRunning) {
            if (isForegroundService) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun promoteToForeground() {
        locationServiceRunning = true
        val intent = Intent(context, LocationService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            isForegroundService = true
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    }

    override fun demotetoBackground() {
        locationServiceRunning = true
        val intent = Intent(context, LocationService::class.java)

        isForegroundService = false
        context.startService(intent)
    }
}
