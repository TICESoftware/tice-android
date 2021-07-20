package tice.managers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ticeapp.TICE.R
import tice.dagger.scopes.AppScope
import tice.models.LocalizationId
import tice.ui.activitys.MainActivity
import tice.ui.delegates.AppStatusProvider
import tice.utility.getLogger
import tice.utility.provider.LocalizationProviderType
import java.lang.ref.WeakReference
import javax.inject.Inject
import kotlin.random.Random

@AppScope
class PopupNotificationManager @Inject constructor(
    private val appContext: Context,
    private val notificationManager: NotificationManagerCompat,
    localizationProvider: LocalizationProviderType,
    private val appStatusProvider: AppStatusProvider
) : PopupNotificationManagerType {
    private val logger by getLogger()

    override var delegate: WeakReference<AppStatusProvider>? = null

    private val channelId = "TICE_CHANNEL_ID"
    private val channelName = localizationProvider.getString(LocalizationId(R.string.notification_channel_name))

    private lateinit var notificationChannel: NotificationChannel

    init {
        initiateChannel()
    }

    private fun initiateChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationChannel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }

    override fun showPopUpNotification(title: String, text: String) {
        if (appStatusProvider.status == AppStatusProvider.Status.FOREGROUND) {
            logger.debug("App is in foreground. Notification not created.")
            return
        }

        logger.debug("create notification\ntext: [$text]\ntitle: [$title]")
        val notificationIntent = Intent(appContext, MainActivity::class.java)

        val pendingIntent = PendingIntent.getActivities(
            appContext,
            0,
            arrayOf(notificationIntent),
            PendingIntent.FLAG_ONE_SHOT
        )

        val notification = NotificationCompat.Builder(appContext, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_logo_tice_small)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(channelId, Random.nextInt(), notification)
    }
}
