package tice.managers.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ticeapp.TICE.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import tice.AppFlow
import tice.TICEApplication
import tice.managers.messaging.notificationHandler.VerifyDeviceHandlerType
import tice.managers.storageManagers.DeviceIdStorageManagerType
import tice.managers.storageManagers.VersionCodeStorageManager
import tice.models.LocalizationId
import tice.models.messaging.Envelope
import tice.ui.activitys.MainActivity
import tice.utility.getLogger
import tice.utility.provider.LocalizationProvider
import tice.utility.safeParse
import javax.inject.Inject
import kotlin.random.Random

class FirebaseReceiverService : FirebaseMessagingService() {
    private val logger by getLogger()

    @Inject
    lateinit var postOffice: PostOfficeType

    @Inject
    lateinit var verifyDeviceHandler: VerifyDeviceHandlerType

    @Inject
    lateinit var deviceIdStorageManager: DeviceIdStorageManagerType

    @Inject
    lateinit var appContext: Context

    private val remoteMessageDataPayloadKey = "payload"

    private var initJob: Job? = null
    private var migrationNecessary: Boolean = false

    override fun onCreate() {
        val versionCodeStorageManager = VersionCodeStorageManager(baseContext)

        try {
            if (versionCodeStorageManager.outdatedVersion) {
                migrationNecessary = true
                return
            }

            initJob = AppFlow(application as TICEApplication).initApp()
            (application as TICEApplication).appComponent.bind(this)
        } catch (e: Exception) {
            logger.error(e.message)
        }

        super.onCreate()
    }

    override fun onNewToken(newToken: String) {
        super.onNewToken(newToken)

        logger.debug("Received new DeviceId: [$newToken]")
        deviceIdStorageManager.storeDeviceId(newToken)
        verifyDeviceHandler.startUpdatingDeviceId(newToken)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        runBlocking { initJob?.children?.forEach { it.join() } }

        if (migrationNecessary) {
            logger.debug("Trying to handle incoming push notification but migration needs be executed first. Showing placeholder notification instead.")
            showNotMigratedPushNotification()
            return
        }

        val data = remoteMessage.data[remoteMessageDataPayloadKey] ?: run {
            logger.error("Received push notification without data section.")
            return
        }

        val envelope = Json.safeParse(Envelope.serializer(), data)
        postOffice.receiveEnvelope(envelope)
    }

    private fun showNotMigratedPushNotification() {
        val localizationProvider = LocalizationProvider(baseContext)
        val channelId = "TICE_CHANNEL_ID"
        val channelName = localizationProvider.getString(LocalizationId(R.string.notification_channel_name))

        val notificationManager = NotificationManagerCompat.from(baseContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(notificationChannel)
        }

        val notificationIntent = Intent(baseContext, MainActivity::class.java)

        val intentFlags = PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE

        val pendingIntent = PendingIntent.getActivities(
            baseContext,
            0,
            arrayOf(notificationIntent),
            intentFlags
        )

        val notification = NotificationCompat.Builder(baseContext, channelId)
            .setContentTitle(localizationProvider.getString(LocalizationId(R.string.notification_placeholder_title)))
            .setContentText(localizationProvider.getString(LocalizationId(R.string.notification_placeholder_body)))
            .setSmallIcon(R.drawable.ic_logo_tice_small)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(channelId, Random.nextInt(), notification)
    }
}
