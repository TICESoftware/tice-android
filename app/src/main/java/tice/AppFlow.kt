package tice

import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.ticeapp.TICE.BuildConfig
import com.ticeapp.TICE.R
import dagger.Lazy
import kotlinx.coroutines.*
import tice.crypto.CryptoManager
import tice.dagger.scopes.AppScope
import tice.managers.*
import tice.managers.group.GroupManager
import tice.managers.group.MeetupManager
import tice.managers.group.TeamManager
import tice.managers.messaging.Mailbox
import tice.managers.messaging.PostOfficeType
import tice.managers.messaging.WebSocketReceiver
import tice.managers.messaging.notificationHandler.FewOneTimePrekeysReceiver
import tice.managers.messaging.notificationHandler.GroupNotificationReceiver
import tice.managers.messaging.notificationHandler.VerifyDeviceHandler
import tice.managers.storageManagers.CryptoStorageManagerType
import tice.managers.storageManagers.GroupStorageManager
import tice.ui.delegates.AppStatusProvider
import tice.utility.beekeeper.BeekeeperEvent
import tice.utility.beekeeper.BeekeeperType
import tice.utility.beekeeper.track
import tice.utility.getLogger
import tice.utility.provider.CoroutineContextProvider
import tice.workers.BackendSyncWorker
import tice.workers.MembershipRenewalWorker
import tice.workers.MessageKeyCacheWorker
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@AppScope
class AppFlow constructor(private val application: TICEApplication) : LifecycleObserver, AppStatusProvider {
    private val logger by getLogger()

    @Inject
    lateinit var groupNotificationReceiver: Lazy<GroupNotificationReceiver>

    @Inject
    lateinit var fewOneTimePrekeysReceiver: Lazy<FewOneTimePrekeysReceiver>

    @Inject
    lateinit var chatManager: Lazy<ChatManager>

    @Inject
    lateinit var signedInUserManager: Lazy<SignedInUserManager>

    @Inject
    lateinit var webSocketReceiver: Lazy<WebSocketReceiver>

    @Inject
    lateinit var cryptoManager: Lazy<CryptoManager>

    @Inject
    lateinit var cryptoStorageManager: Lazy<CryptoStorageManagerType>

    @Inject
    lateinit var locationManager: Lazy<LocationManager>

    @Inject
    lateinit var verifyDeviceHandler: Lazy<VerifyDeviceHandler>

    @Inject
    lateinit var popupNotificationManager: Lazy<PopupNotificationManager>

    @Inject
    lateinit var postOffice: Lazy<PostOfficeType>

    @Inject
    lateinit var locationServiceController: Lazy<LocationServiceControllerType>

    @Inject
    lateinit var conversationManager: Lazy<ConversationManager>

    @Inject
    lateinit var groupManager: Lazy<GroupManager>

    @Inject
    lateinit var groupStorageManager: Lazy<GroupStorageManager>

    @Inject
    lateinit var teamManager: Lazy<TeamManager>

    @Inject
    lateinit var meetupManager: Lazy<MeetupManager>

    @Inject
    lateinit var mailbox: Lazy<Mailbox>

    @Inject
    lateinit var coroutineContextProvider: Lazy<CoroutineContextProvider>

    @Inject
    lateinit var beekeeper: Lazy<BeekeeperType>

    @Inject
    lateinit var locationSharingManager: Lazy<LocationSharingManager>

    @Inject
    lateinit var userManager: Lazy<UserManager>

    private var _isInForeground: AppStatusProvider.Status = AppStatusProvider.Status.BACKGROUND
    override val status: AppStatusProvider.Status
        get() = _isInForeground

    private var sessionStart: Date? = null

    private lateinit var workManager: WorkManager

    @OptIn(ExperimentalStdlibApi::class)
    fun initApp(): Job {
        val initJob = Job()

        if (!application.initializeNewAppComponent(this)) {
            initJob.complete()
            return initJob
        }
        application.appComponent.bind(this)
        logger.debug("Initialize app flow")

        workManager = WorkManager.getInstance(application)

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        signedInUserManager.get().setup()

        mailbox.get().registerForDelegate()

        groupManager.get().registerForDelegate()
        teamManager.get().registerForDelegate()

        verifyDeviceHandler.get().registerEnvelopeReceiver()

        chatManager.get().registerEnvelopeReceiver()

        locationSharingManager.get().registerEnvelopeReceiver()

        conversationManager.get().registerEnvelopeReceiver()
        conversationManager.get().registerPayloadProcessor()

        fewOneTimePrekeysReceiver.get().registerEnvelopeReceiver()

        groupNotificationReceiver.get().registerEnvelopeReceiver()

        userManager.get().registerEnvelopeReceiver()

        popupNotificationManager.get().delegate = WeakReference(this)

        locationSharingManager.get().startOutdatedLocationSharingStateCheck()

        locationManager.get().startMonitoringSharingStates(CoroutineScope(Dispatchers.IO))

        FirebaseApp.initializeApp(application.applicationContext)
        workManager = WorkManager.getInstance(application)

        if (signedInUserManager.get().signedIn()) {
            CoroutineScope(coroutineContextProvider.get().IO + initJob).launch {
                if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(application.applicationContext) == ConnectionResult.SUCCESS) {
                    withTimeoutOrNull(2000) {
                        suspendCoroutine<String> { continuation ->
                            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                                continuation.resume(token)
                            }
                        }
                    }?.let { verifyDeviceHandler.get().startUpdatingDeviceId(it) }
                } else {
                    logger.info("Skip device token retrieval because Google Play Services are missing.")
                }

                try {
                    postOffice.get().fetchMessages()
                } catch (e: Throwable) {
                    logger.error("Fetching messages failed", e)
                }

                webSocketReceiver.get().connect()
            }

            val work = PeriodicWorkRequestBuilder<MembershipRenewalWorker>(
                1,
                TimeUnit.DAYS,
                1,
                TimeUnit.DAYS
            ).build()

            workManager.enqueueUniquePeriodicWork(
                application.resources.getString(R.string.worker_membership_renewal),
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
        }

        val versionCode = BuildConfig.VERSION_CODE
        beekeeper.get().setProperty(0, "android-$versionCode")

        if (BuildConfig.APPLICATION_ID != "app.tice.TICE.development") {
            beekeeper.get().start()
        }

        return initJob
    }

    fun onStop() {
        logger.debug("OnStop called")
        val sessionDuration = Date().time - (sessionStart ?: Date()).time
        beekeeper.get().track(BeekeeperEvent.sessionEnd(sessionDuration))

        CoroutineScope(coroutineContextProvider.get().IO).launch {
            beekeeper.get().dispatch()
        }

        webSocketReceiver.get().disconnect()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onMoveToForeground() {
        _isInForeground = AppStatusProvider.Status.FOREGROUND

        val lang =
            ConfigurationCompat.getLocales(application.applicationContext.resources.configuration)[0].language
        beekeeper.get().track(BeekeeperEvent.sessionStart(lang))
        sessionStart = Date()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onMoveToBackground() {
        _isInForeground = AppStatusProvider.Status.BACKGROUND

        onStop()

        CoroutineScope(coroutineContextProvider.get().IO).launch {
            val userIsInMeetups = groupStorageManager.get()
                .isUserInMeetups(signedInUserManager.get().signedInUser.userId)

            if (userIsInMeetups) {
                val work = PeriodicWorkRequestBuilder<BackendSyncWorker>(
                    15,
                    TimeUnit.MINUTES,
                    5,
                    TimeUnit.MINUTES
                ).build()

                workManager.enqueueUniquePeriodicWork(
                    application.resources.getString(R.string.worker_backend),
                    ExistingPeriodicWorkPolicy.KEEP,
                    work
                )
            }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onResume() {
        locationServiceController.get().restartService()

        if (signedInUserManager.get().signedIn()) {
            val work = PeriodicWorkRequestBuilder<MessageKeyCacheWorker>(
                1,
                TimeUnit.HOURS,
                15,
                TimeUnit.MINUTES
            ).build()

            workManager.enqueueUniquePeriodicWork(
                application.resources.getString(R.string.worker_messageKey),
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )

            webSocketReceiver.get().connect()
        }

        workManager.cancelUniqueWork(application.getString(R.string.worker_backend))
    }
}
