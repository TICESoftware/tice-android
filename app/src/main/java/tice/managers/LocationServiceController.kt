package tice.managers

import android.content.Context
import android.content.Intent
import android.os.Build
import tice.dagger.scopes.AppScope
import tice.utility.getLogger
import javax.inject.Inject

@AppScope
class LocationServiceController @Inject constructor(private val context: Context, private val serviceClass: Class<*>) : LocationServiceControllerType {
    val logger by getLogger()

    override var locationServiceRunning = false

    override fun requestStartingLocationService() {
        logger.debug("Start location service.")

        if (locationServiceRunning) {
            logger.debug("Location service already running.")
            return
        }

        val intent = Intent(context, serviceClass)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    override fun stopLocationService() {
        logger.debug("Stopping location service.")

        if (locationServiceRunning) {
            context.stopService(Intent(context, serviceClass))
        } else {
            logger.debug("No location service instance running.")
        }
    }
}
