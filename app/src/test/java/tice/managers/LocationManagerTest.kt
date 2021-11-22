package tice.managers

import io.mockk.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runBlockingTest
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

    private val mockCoroutineContextProvider: CoroutineContextProviderType = mockk(relaxUnitFun = true)
    private val mockSignedInUserManager: SignedInUserManagerType = mockk(relaxUnitFun = true)
    private val mockGroupStorageManager: GroupStorageManagerType = mockk(relaxUnitFun = true)
    private val mockLocationSharingStorageManager: LocationSharingStorageManagerType = mockk(relaxUnitFun = true)

    private val mockLocationManagerDelegate: LocationManagerDelegate = mockk(relaxUnitFun = true)

    private val mockTestUser: User = mockk(relaxUnitFun = true)

    private val mockSignedInUser: SignedInUser = mockk(relaxUnitFun = true)
    private val TEST_SIGNED_IN_USER_ID = UUID.randomUUID()

    private val TEST_USER_ID = UUID.randomUUID()
    private val TEST_USER_NAME = "UserName"
    private val TEST_USER_PUBLIC_KEY = "PublicKey".toByteArray()

    private val TEST_GROUP_ID_1 = UUID.randomUUID()
    private val TEST_GROUP_ID_2 = UUID.randomUUID()

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

        locationManager = LocationManager(mockCoroutineContextProvider)
    }

    @Test
    fun processLocationUpdate() = runBlockingTest {
        val TEST_LOCATION = mockk<Location>()

        locationManager.delegate = WeakReference(mockLocationManagerDelegate)

        locationManager.processLocationUpdate(TEST_LOCATION)

        coVerify { mockLocationManagerDelegate.processLocationUpdate(TEST_LOCATION) }
    }
}
