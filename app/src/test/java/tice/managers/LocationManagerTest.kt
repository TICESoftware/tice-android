package tice.managers

import io.mockk.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tice.managers.messaging.PostOfficeType
import tice.managers.storageManagers.GroupStorageManagerType
import tice.managers.storageManagers.LocationSharingStorageManagerType
import tice.models.Location
import tice.models.LocationSharingState
import tice.models.SignedInUser
import tice.models.User
import tice.models.database.GroupInterface
import tice.utility.provider.CoroutineContextProviderType
import java.lang.ref.WeakReference
import java.util.*

internal class LocationManagerTest {

    private lateinit var locationManager: LocationManager

    private val mockPostOffice: PostOfficeType = mockk(relaxUnitFun = true)
    private val mockLocationServiceController: LocationServiceController = mockk(relaxUnitFun = true)
    private val mockCoroutineContextProvider: CoroutineContextProviderType = mockk(relaxUnitFun = true)
    private val mockSignedInUserManager: SignedInUserManagerType = mockk(relaxUnitFun = true)
    private val mockGroupStorageManager: GroupStorageManagerType = mockk(relaxUnitFun = true)
    private val mockUserManager: UserManagerType = mockk(relaxUnitFun = true)
    private val mockLocationSharingStorageManager: LocationSharingStorageManagerType = mockk(relaxUnitFun = true)

    private val mockLocationManagerDelegate: LocationManagerDelegate = mockk(relaxUnitFun = true)
    private val mockGroupInterface: GroupInterface = mockk(relaxUnitFun = true)

    private val mockTestUser: User = mockk(relaxUnitFun = true)

    private val mockSignedInUser: SignedInUser = mockk(relaxUnitFun = true)
    private val TEST_SIGNED_IN_USER_ID = UUID.randomUUID()

    private val TEST_USER_ID = UUID.randomUUID()
    private val TEST_USER_NAME = "UserName"
    private val TEST_USER_PUBLIC_KEY = "PublicKey".toByteArray()

    private val TEST_TIMESTAMP_OLD = Date(Date().time - 10000L)
    private val TEST_TIMESTAMP_NEW = Date(Date().time + 10000L)

    private val TEST_GROUP_ID_1 = UUID.randomUUID()
    private val TEST_GROUP_ID_2 = UUID.randomUUID()
    private val TEST_GROUP_ID_3 = UUID.randomUUID()

    private val TEST_LOCATION_SHARING_STATE_1 = LocationSharingState(TEST_SIGNED_IN_USER_ID, TEST_GROUP_ID_1, true, Date())
    private val TEST_LOCATION_SHARING_STATE_2 = LocationSharingState(TEST_USER_ID, TEST_GROUP_ID_1, true, Date())
    private val TEST_LOCATION_SHARING_STATE_3 = LocationSharingState(TEST_SIGNED_IN_USER_ID, TEST_GROUP_ID_2, false, Date())

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        every { mockLocationSharingStorageManager.getAllStatesFlow() } returns flowOf(
            listOf(TEST_LOCATION_SHARING_STATE_1, TEST_LOCATION_SHARING_STATE_2, TEST_LOCATION_SHARING_STATE_3)
        )

        every { mockCoroutineContextProvider.IO } returns Job()
        coEvery { mockGroupStorageManager.isMember(TEST_SIGNED_IN_USER_ID, TEST_GROUP_ID_1) }.returnsMany(listOf(true, false))

        every { mockSignedInUserManager.signedInUser } returns mockSignedInUser
        every { mockSignedInUser.userId } returns TEST_SIGNED_IN_USER_ID

        every { mockTestUser.userId } returns TEST_USER_ID
        every { mockTestUser.publicName } returns TEST_USER_NAME
        every { mockTestUser.publicSigningKey } returns TEST_USER_PUBLIC_KEY

        locationManager = LocationManager(
            mockPostOffice,
            mockLocationServiceController,
            mockCoroutineContextProvider,
            mockSignedInUserManager,
            mockUserManager,
            mockLocationSharingStorageManager
        )
    }

    @Test
    fun startMonitoringSharingStates_NoSignedInUser() = runBlockingTest {
        every { mockSignedInUserManager.signedInUser } throws Exception()

        locationManager.startMonitoringSharingStates(TestCoroutineScope())

        confirmVerified(mockGroupStorageManager)
        confirmVerified(mockLocationServiceController)
    }

    @Test
    fun startMonitoringSharingStates_NoLocationSharing() = runBlockingTest {
        every { mockSignedInUserManager.signedInUser } throws Exception()

        locationManager.startMonitoringSharingStates(TestCoroutineScope())

        confirmVerified(mockGroupStorageManager)
        confirmVerified(mockLocationServiceController)
    }

    @Test
    fun startMonitoringSharingStates_NoEnabledSharingStates() = runBlockingTest {
        every { mockLocationSharingStorageManager.getAllStatesFlow() } returns flowOf(
            listOf(TEST_LOCATION_SHARING_STATE_2, TEST_LOCATION_SHARING_STATE_3)
        )

        locationManager.startMonitoringSharingStates(TestCoroutineScope())

        verify(exactly = 1) { mockLocationServiceController.demotetoBackground() }
    }

    @Test
    fun init_StartLocationService_HasGroups() = runBlockingTest {
        coEvery { mockGroupStorageManager.isMember(TEST_SIGNED_IN_USER_ID, TEST_GROUP_ID_1) } returns true
        coEvery { mockGroupStorageManager.isMember(TEST_SIGNED_IN_USER_ID, TEST_GROUP_ID_2) } returns true
        coEvery { mockGroupStorageManager.isMember(TEST_SIGNED_IN_USER_ID, TEST_GROUP_ID_3) } returns true

        locationManager.startMonitoringSharingStates(TestCoroutineScope())

        verify(exactly = 1) { mockLocationServiceController.promoteToForeground() }
    }

    @Test
    fun processLocationUpdate_HasNoMonitoringGroups() = runBlockingTest {
        val TEST_LOCATION = mockk<Location>()

        every { mockLocationServiceController.locationServiceRunning } returns true

        locationManager.delegate = WeakReference(mockLocationManagerDelegate)

        locationManager.processLocationUpdate(TEST_LOCATION)

        locationManager.getOwnLocationUpdateFlow().first {
            Assertions.assertEquals(TEST_LOCATION, it)
            true
        }

        coVerify(exactly = 0) { mockLocationManagerDelegate.processLocationUpdate(TEST_LOCATION) }
    }


    @Test
    fun processLocationUpdate_HasMonitoringGroups() = runBlockingTest {
        every { mockLocationSharingStorageManager.getAllStatesFlow() } returns flowOf(listOf(TEST_LOCATION_SHARING_STATE_1))
        val TEST_LOCATION = mockk<Location>()

        every { mockLocationServiceController.locationServiceRunning } returns false

        locationManager.delegate = WeakReference(mockLocationManagerDelegate)

        val testDispatcher = TestCoroutineDispatcher()
        locationManager.startMonitoringSharingStates(TestCoroutineScope(testDispatcher))
        testDispatcher.advanceUntilIdle()

        locationManager.processLocationUpdate(TEST_LOCATION)

        locationManager.getOwnLocationUpdateFlow().first {
            Assertions.assertEquals(TEST_LOCATION, it)
            true
        }

        coVerify(exactly = 1) { mockLocationManagerDelegate.processLocationUpdate(TEST_LOCATION) }
    }

    @Test
    fun processLocationUpdate_LocationSharingDisabled() = runBlockingTest {
        val TEST_LOCATION = mockk<Location>()

        locationManager.delegate = WeakReference(mockLocationManagerDelegate)

        locationManager.processLocationUpdate(TEST_LOCATION)

        coVerify(exactly = 0) { mockLocationManagerDelegate.processLocationUpdate(TEST_LOCATION) }
    }

    @Test
    fun getOwnLocationFlow() = runBlockingTest {
        val TEST_LOCATION1 = mockk<Location>()
        val TEST_LOCATION2 = mockk<Location>()
        val TEST_LOCATION3 = mockk<Location>()

        every { mockLocationServiceController.locationServiceRunning } returns true

        val results = locationManager.getOwnLocationUpdateFlow().onSubscription {
            TestCoroutineScope(TestCoroutineDispatcher()).launch {
                locationManager.processLocationUpdate(TEST_LOCATION1)
                locationManager.processLocationUpdate(TEST_LOCATION2)
                locationManager.processLocationUpdate(TEST_LOCATION3)
            }
        }.take(3).toList()

        Assertions.assertEquals(TEST_LOCATION1, results[0])
        Assertions.assertEquals(TEST_LOCATION2, results[1])
        Assertions.assertEquals(TEST_LOCATION3, results[2])
    }
}
