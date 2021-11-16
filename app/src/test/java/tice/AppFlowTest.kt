package tice

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.core.app.NotificationCompat
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.WorkManager
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.tasks.OnSuccessListener
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.ticeapp.TICE.BuildConfig
import com.ticeapp.TICE.R
import dagger.Lazy
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyInt
import tice.crypto.CryptoManager
import tice.dagger.components.AppComponent
import tice.helper.InstantExecutorExtension
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
import tice.managers.storageManagers.GroupStorageManager
import tice.models.SignedInUser
import tice.models.TrackerEvent
import tice.models.UserId
import tice.ui.delegates.AppStatusProvider
import tice.utility.TrackerType
import tice.utility.provider.CoroutineContextProvider
import java.lang.ref.WeakReference
import java.util.*

@ExtendWith(InstantExecutorExtension::class)
internal class AppFlowTest {

    private lateinit var appFlow: AppFlow

    private val mockGroupNotificationReceiver: GroupNotificationReceiver = mockk(relaxUnitFun = true)
    private val mockFewOneTimePrekeysReceiver: FewOneTimePrekeysReceiver = mockk(relaxUnitFun = true)
    private val mockChatManager: ChatManager = mockk(relaxUnitFun = true)
    private val mockSignedInUserManager: SignedInUserManager = mockk(relaxUnitFun = true)
    private val mockWebSocketReceiver: WebSocketReceiver = mockk(relaxUnitFun = true)
    private val mockCryptoManager: CryptoManager = mockk(relaxUnitFun = true)
    private val mockLocationManager: LocationManager = mockk(relaxUnitFun = true)
    private val mockVerifyDeviceHandler: VerifyDeviceHandler = mockk(relaxUnitFun = true)
    private val mockPopupNotificationManager: PopupNotificationManager = mockk(relaxUnitFun = true)
    private val mockPostOffice: PostOfficeType = mockk(relaxUnitFun = true)
    private val mockLocationServiceControllerType: LocationServiceControllerType = mockk(relaxUnitFun = true)
    private val mockConversationManager: ConversationManager = mockk(relaxUnitFun = true)
    private val mockGroupManager: GroupManager = mockk(relaxUnitFun = true)
    private val mockGroupStorageManager: GroupStorageManager = mockk(relaxUnitFun = true)
    private val mockTeamManager: TeamManager = mockk(relaxUnitFun = true)
    private val mockMeetupManager: MeetupManager = mockk(relaxUnitFun = true)
    private val mockMailBox: Mailbox = mockk(relaxUnitFun = true)
    private val mockCoroutineContextProvider: CoroutineContextProvider = mockk(relaxUnitFun = true)
    private val mockTracker: TrackerType = mockk(relaxUnitFun = true)
    private val mockContext: Context = mockk(relaxUnitFun = true)
    private val mockLocationSharingManager: LocationSharingManager = mockk(relaxUnitFun = true)
    private val mockUserManager: UserManager = mockk(relaxUnitFun = true)

    private val mockLazyGroupNotificationReceiver: Lazy<GroupNotificationReceiver> = mockk(relaxUnitFun = true)
    private val mockLazyFewOneTimePrekeysReceiver: Lazy<FewOneTimePrekeysReceiver> = mockk(relaxUnitFun = true)
    private val mockLazyChatManager: Lazy<ChatManager> = mockk(relaxUnitFun = true)
    private val mockLazySignedInUserManager: Lazy<SignedInUserManager> = mockk(relaxUnitFun = true)
    private val mockLazyWebSocketReceiver: Lazy<WebSocketReceiver> = mockk(relaxUnitFun = true)
    private val mockLazyCryptoManager: Lazy<CryptoManager> = mockk(relaxUnitFun = true)
    private val mockLazyLocationManager: Lazy<LocationManager> = mockk(relaxUnitFun = true)
    private val mockLazyVerifyDeviceHandler: Lazy<VerifyDeviceHandler> = mockk(relaxUnitFun = true)
    private val mockLazyPopupNotificationManager: Lazy<PopupNotificationManager> = mockk(relaxUnitFun = true)
    private val mockLazyPostOffice: Lazy<PostOfficeType> = mockk(relaxUnitFun = true)
    private val mockLazyLocationServiceControllerType: Lazy<LocationServiceControllerType> = mockk(relaxUnitFun = true)
    private val mockLazyConversationManager: Lazy<ConversationManager> = mockk(relaxUnitFun = true)
    private val mockLazyGroupManager: Lazy<GroupManager> = mockk(relaxUnitFun = true)
    private val mockLazyGroupStorageManager: Lazy<GroupStorageManager> = mockk(relaxUnitFun = true)
    private val mockLazyTeamManager: Lazy<TeamManager> = mockk(relaxUnitFun = true)
    private val mockLazyMeetupManager: Lazy<MeetupManager> = mockk(relaxUnitFun = true)
    private val mockLazyMailBox: Lazy<Mailbox> = mockk(relaxUnitFun = true)
    private val mockLazyCoroutineContextProvider: Lazy<CoroutineContextProvider> = mockk(relaxUnitFun = true)
    private val mockLazyTracker: Lazy<TrackerType> = mockk(relaxUnitFun = true)
    private val mockLazyContext: Lazy<Context> = mockk(relaxUnitFun = true)
	private val mockLazyLocationSharingManager: Lazy<LocationSharingManager> = mockk(relaxUnitFun = true)
    private val mockLazyUserManager: Lazy<UserManager> = mockk(relaxUnitFun = true)

    private val mockResources: Resources = mockk(relaxUnitFun = true)
    private val mockConfiguration: Configuration = mockk(relaxUnitFun = true)
    private val mockLocalListCompat: LocaleListCompat = mockk(relaxUnitFun = true)
    private val mockLocale: Locale = mockk(relaxUnitFun = true)

    private val testPackageVersionCode = BuildConfig.VERSION_CODE
    private val testLanguage = "testLanguage"

    private val mockApplication = mockk<TICEApplication>(relaxUnitFun = true)
    private val mockAppComponent = mockk<AppComponent>(relaxUnitFun = true)

    private var testDispatcher = TestCoroutineDispatcher()

    private val mockWorkManager: WorkManager = mockk(relaxUnitFun = true)

    private val TEST_WORKER_KEY = "worker_membership_renewal"

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        testDispatcher = TestCoroutineDispatcher()

        every { mockLazyGroupNotificationReceiver.get() } returns mockGroupNotificationReceiver
        every { mockLazyFewOneTimePrekeysReceiver.get() } returns mockFewOneTimePrekeysReceiver
        every { mockLazyChatManager.get() } returns mockChatManager
        every { mockLazySignedInUserManager.get() } returns mockSignedInUserManager
        every { mockLazyWebSocketReceiver.get() } returns mockWebSocketReceiver
        every { mockLazyCryptoManager.get() } returns mockCryptoManager
        every { mockLazyLocationManager.get() } returns mockLocationManager
        every { mockLazyVerifyDeviceHandler.get() } returns mockVerifyDeviceHandler
        every { mockLazyPopupNotificationManager.get() } returns mockPopupNotificationManager
        every { mockLazyPostOffice.get() } returns mockPostOffice
        every { mockLazyLocationServiceControllerType.get() } returns mockLocationServiceControllerType
        every { mockLazyConversationManager.get() } returns mockConversationManager
        every { mockLazyGroupManager.get() } returns mockGroupManager
        every { mockLazyGroupStorageManager.get() } returns mockGroupStorageManager
        every { mockLazyTeamManager.get() } returns mockTeamManager
        every { mockLazyMeetupManager.get() } returns mockMeetupManager
        every { mockLazyMailBox.get() } returns mockMailBox
        every { mockLazyCoroutineContextProvider.get() } returns mockCoroutineContextProvider
        every { mockLazyTracker.get() } returns mockTracker
        every { mockLazyContext.get() } returns mockContext
        every { mockLazyLocationSharingManager.get() } returns mockLocationSharingManager
        every { mockLazyUserManager.get() } returns mockUserManager

        every { mockCoroutineContextProvider.IO } returns testDispatcher
        every { mockContext.resources } returns mockResources
        every { mockApplication.resources } returns mockResources
        every { mockResources.configuration } returns mockConfiguration
        every { mockResources.getBoolean(anyInt()) } returns true
        every { mockResources.getString(R.string.worker_membership_renewal) } returns TEST_WORKER_KEY

        every { mockApplication.appComponent } returns mockAppComponent
        every { mockApplication.applicationContext } returns mockContext
        every { mockApplication.initializeNewAppComponent(any()) } returns true

        mockkStatic(FirebaseApp::class)
        every { FirebaseApp.initializeApp(mockContext) } returns null

        mockkStatic(ConfigurationCompat::class)
        every { ConfigurationCompat.getLocales(mockConfiguration) } returns mockLocalListCompat
        every { mockLocalListCompat[0] } returns mockLocale
        every { mockLocale.language } returns testLanguage

        val mockSignedInUser: SignedInUser = mockk(relaxUnitFun = true)
        val mockUserId: UserId = mockk(relaxUnitFun = true)
        val mockConfigWorkManager: androidx.work.Configuration = mockk(relaxUnitFun = true)
        every { mockSignedInUserManager.signedInUser } returns mockSignedInUser
        every { mockSignedInUser.userId } returns mockUserId
        coEvery { mockGroupStorageManager.isUserInMeetups(mockUserId) } returns false
        every { mockApplication.workManagerConfiguration } returns mockConfigWorkManager

        appFlow = AppFlow(mockApplication).apply {
            groupNotificationReceiver = mockLazyGroupNotificationReceiver
            fewOneTimePrekeysReceiver = mockLazyFewOneTimePrekeysReceiver
            chatManager = mockLazyChatManager
            signedInUserManager = mockLazySignedInUserManager
            webSocketReceiver = mockLazyWebSocketReceiver
            cryptoManager = mockLazyCryptoManager
            locationManager = mockLazyLocationManager
            verifyDeviceHandler = mockLazyVerifyDeviceHandler
            popupNotificationManager = mockLazyPopupNotificationManager
            postOffice = mockLazyPostOffice
            locationServiceController = mockLazyLocationServiceControllerType
            conversationManager = mockLazyConversationManager
            groupManager = mockLazyGroupManager
            groupStorageManager = mockLazyGroupStorageManager
            teamManager = mockLazyTeamManager
            meetupManager = mockLazyMeetupManager
            mailbox = mockLazyMailBox
            coroutineContextProvider = mockLazyCoroutineContextProvider
            tracker = mockLazyTracker
            locationSharingManager = mockLazyLocationSharingManager
            userManager = mockLazyUserManager
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun verifyInitProcess_SignedIn() = runBlockingTest {
        val TEST_ID = "testId"

        mockkStatic(GoogleApiAvailability::class)
        mockkStatic(FirebaseMessaging::class)
        mockkStatic(ProcessLifecycleOwner::class)
        mockkStatic(WorkManager::class)
        mockkConstructor(NotificationCompat.Builder::class)

        every { mockLocationManager.startMonitoringSharingStates(any()) } returns mockk()
        every { mockSignedInUserManager.signedIn() } returns true
        every { WorkManager.initialize(any(), any()) } returns Unit
        every { WorkManager.getInstance(any()) } returns mockWorkManager
        every { mockWorkManager.enqueueUniquePeriodicWork(any(), any(), any()) } returns mockk()

        val temp = slot<OnSuccessListener<String>>()

        every { GoogleApiAvailability.getInstance() } returns mockk {
            every { isGooglePlayServicesAvailable(any()) } returns ConnectionResult.SUCCESS
        }

        every { FirebaseMessaging.getInstance() } returns mockk {
            every { token } returns mockk {
                every { addOnSuccessListener(capture(temp)) } answers {
                    temp.captured.onSuccess(TEST_ID)
                    mockk()
                }
            }
        }

        appFlow.initApp()

        testDispatcher.advanceUntilIdle()

        val weakReferenceSlot = slot<WeakReference<AppStatusProvider>>()

        verify(exactly = 1) { mockMailBox.registerForDelegate() }
        verify(exactly = 1) { mockGroupManager.registerForDelegate() }
        verify(exactly = 1) { mockTeamManager.registerForDelegate() }
        verify(exactly = 1) { mockSignedInUserManager.setup() }
        verify(exactly = 1) { mockChatManager.registerEnvelopeReceiver() }
        verify(exactly = 1) { mockConversationManager.registerEnvelopeReceiver() }
        verify(exactly = 1) { mockConversationManager.registerPayloadProcessor() }
        verify(exactly = 1) { mockFewOneTimePrekeysReceiver.registerEnvelopeReceiver() }
        verify(exactly = 1) { mockGroupNotificationReceiver.registerEnvelopeReceiver() }
        verify(exactly = 1) { mockUserManager.registerEnvelopeReceiver() }
        verify(exactly = 1) { mockPopupNotificationManager.delegate = capture(weakReferenceSlot) }
        verify(exactly = 1) { mockLocationSharingManager.registerEnvelopeReceiver() }
        verify(exactly = 1) { mockLocationManager.startMonitoringSharingStates(any()) }

        verify(exactly = 1) { mockCoroutineContextProvider.IO }
        verify(exactly = 1) { mockVerifyDeviceHandler.startUpdatingDeviceId(TEST_ID) }
        coVerify(exactly = 1) { mockPostOffice.fetchMessages() }
        coVerify(exactly = 1) { mockWebSocketReceiver.connect() }

        verify(exactly = 1) { mockTracker.setProperty(0, "android-$testPackageVersionCode") }

        verify(exactly = 1) { ProcessLifecycleOwner.get() }
        verify(exactly = 1) { mockWorkManager.enqueueUniquePeriodicWork(any(), any(), any()) }
        Assertions.assertEquals(appFlow, weakReferenceSlot.captured.get())
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun verifyInitProcess_NotSignedIn() = runBlocking {
        mockkStatic(ProcessLifecycleOwner::class)
        mockkStatic(WorkManager::class)
        mockkStatic(GoogleApiAvailability::class)

        every { mockLocationManager.startMonitoringSharingStates(any()) } returns mockk()
        every { mockSignedInUserManager.signedIn() } returns false
        every { WorkManager.initialize(any(), any()) } returns Unit
        every { WorkManager.getInstance(any()) } returns mockWorkManager
        every { GoogleApiAvailability.getInstance() } returns mockk {
            every { isGooglePlayServicesAvailable(any()) } returns ConnectionResult.SUCCESS
        }

        appFlow.initApp()

        val weakReferenceSlot = slot<WeakReference<AppStatusProvider>>()

        verify(exactly = 1) { mockMailBox.registerForDelegate() }
        verify(exactly = 1) { mockGroupManager.registerForDelegate() }
        verify(exactly = 1) { mockTeamManager.registerForDelegate() }
        verify(exactly = 1) { mockSignedInUserManager.setup() }
        verify(exactly = 1) { mockChatManager.registerEnvelopeReceiver() }
        verify(exactly = 1) { mockConversationManager.registerEnvelopeReceiver() }
        verify(exactly = 1) { mockConversationManager.registerPayloadProcessor() }
        verify(exactly = 1) { mockFewOneTimePrekeysReceiver.registerEnvelopeReceiver() }
        verify(exactly = 1) { mockGroupNotificationReceiver.registerEnvelopeReceiver() }
        verify(exactly = 1) { mockUserManager.registerEnvelopeReceiver() }
        verify(exactly = 1) { mockPopupNotificationManager.delegate = capture(weakReferenceSlot) }
        verify(exactly = 1) { mockLocationManager.startMonitoringSharingStates(any()) }
        verify(exactly = 1) { mockLocationSharingManager.startOutdatedLocationSharingStateCheck() }
        verify(exactly = 1) { mockLocationSharingManager.registerEnvelopeReceiver() }

        verify(exactly = 0) { mockCoroutineContextProvider.IO }
        coVerify(exactly = 0) { mockPostOffice.fetchMessages() }
        coVerify(exactly = 0) { mockWebSocketReceiver.connect() }

        verify(exactly = 1) { ProcessLifecycleOwner.get() }
        verify(exactly = 1) { mockTracker.setProperty(0, "android-$testPackageVersionCode") }

        Assertions.assertEquals(appFlow, weakReferenceSlot.captured.get())
    }

    @Test
    fun onStop() = runBlocking {
        appFlow.onStop()

        coVerify(exactly = 1) { mockWebSocketReceiver.disconnect() }
    }

    @Test
    fun onMoveToForeground() = runBlocking {
        val defaultStatus = appFlow.status

        appFlow.onMoveToForeground()

        val newStatus = appFlow.status

        Assertions.assertEquals(defaultStatus, AppStatusProvider.Status.BACKGROUND)
        Assertions.assertEquals(newStatus, AppStatusProvider.Status.FOREGROUND)
    }

    @Test
    fun onMoveToBackground() = runBlocking {
        val defaultStatus = appFlow.status
        appFlow.onMoveToForeground()
        val newStatus = appFlow.status

        appFlow.onMoveToBackground()
        val resultStatus = appFlow.status

        Assertions.assertEquals(defaultStatus, AppStatusProvider.Status.BACKGROUND)
        Assertions.assertEquals(newStatus, AppStatusProvider.Status.FOREGROUND)
        Assertions.assertEquals(resultStatus, AppStatusProvider.Status.BACKGROUND)
    }

    @Test
    fun `initialize second time`() = runBlockingTest {
        every { mockApplication.initializeNewAppComponent(appFlow) } returns false

        appFlow.initApp()

        verify(exactly = 0) { mockMailBox.registerForDelegate() }
        verify(exactly = 0) { mockGroupManager.registerForDelegate() }
        verify(exactly = 0) { mockTeamManager.registerForDelegate() }
        verify(exactly = 0) { mockSignedInUserManager.setup() }
        verify(exactly = 0) { mockChatManager.registerEnvelopeReceiver() }
        verify(exactly = 0) { mockConversationManager.registerEnvelopeReceiver() }
        verify(exactly = 0) { mockConversationManager.registerPayloadProcessor() }
        verify(exactly = 0) { mockFewOneTimePrekeysReceiver.registerEnvelopeReceiver() }
        verify(exactly = 0) { mockGroupNotificationReceiver.registerEnvelopeReceiver() }
        verify(exactly = 0) { mockLocationSharingManager.registerEnvelopeReceiver() }
        verify(exactly = 0) { mockUserManager.registerEnvelopeReceiver() }
        verify(exactly = 0) { mockLocationManager.startMonitoringSharingStates(any()) }
    }
}
