package tice.managers.storageManagers

import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tice.models.LocationSharingState
import tice.models.database.LocationSharingInterface
import java.util.*

internal class LocationSharingStorageManagerTest {

    private lateinit var locationSharingStorageManager: LocationSharingStorageManager

    private val mockAppDatabase: AppDatabase = mockk(relaxUnitFun = true)

    private val mockLocationSharingInterface: LocationSharingInterface = mockk(relaxUnitFun = true)

    private val TEST_USER_ID = UUID.randomUUID()
    private val TEST_GROUP_ID = UUID.randomUUID()
    private val TEST_STATUS = LocationSharingState(TEST_USER_ID, TEST_GROUP_ID, true, Date())

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        every { mockAppDatabase.locationSharingInterface() } returns mockLocationSharingInterface

        locationSharingStorageManager = LocationSharingStorageManager(
            mockAppDatabase
        )
    }

    @Test
    fun getAllStatesFlow() = runBlockingTest {
        val TEST_FLOW = flowOf<List<LocationSharingState>>()

        every { mockLocationSharingInterface.getAllStatesFlow() } returns TEST_FLOW

        val result = locationSharingStorageManager.getAllStatesFlow()

        assertEquals(TEST_FLOW, result)
        verify(exactly = 1) { mockLocationSharingInterface.getAllStatesFlow() }
    }

    @Test
    fun getAllStatesOfUser() = runBlockingTest {
        val TEST_LIST = listOf<LocationSharingState>()

        coEvery { mockLocationSharingInterface.getAllStatesOfUser(TEST_USER_ID) } returns TEST_LIST

        val result = locationSharingStorageManager.getAllStatesOfUser(TEST_USER_ID)

        assertEquals(TEST_LIST, result)
        coVerify(exactly = 1) { mockLocationSharingInterface.getAllStatesOfUser(TEST_USER_ID) }
    }

    @Test
    fun getAllStatesFlowOfUser() = runBlockingTest {
        val TEST_FLOW = flowOf<List<LocationSharingState>>()

        every { mockLocationSharingInterface.getAllStatesFlowOfUser(TEST_USER_ID) } returns TEST_FLOW

        val result = locationSharingStorageManager.getAllStatesFlowOfUser(TEST_USER_ID)

        assertEquals(TEST_FLOW, result)
        verify(exactly = 1) { mockLocationSharingInterface.getAllStatesFlowOfUser(TEST_USER_ID) }

    }


    @Test
    fun getStateOfUserInGroup() = runBlockingTest {
        coEvery { mockLocationSharingInterface.getStateOfUserInGroup(TEST_USER_ID, TEST_GROUP_ID) } returns TEST_STATUS

        val result = locationSharingStorageManager.getStateOfUserInGroup(TEST_USER_ID, TEST_GROUP_ID)

        assertEquals(TEST_STATUS, result)
        coVerify(exactly = 1) { mockLocationSharingInterface.getStateOfUserInGroup(TEST_USER_ID, TEST_GROUP_ID) }
    }

    @Test
    fun getStateFlowOfUserInGroup() = runBlockingTest {
        val TEST_FLOW = flowOf<LocationSharingState>()

        every { mockLocationSharingInterface.getStateFlowOfUserInGroup(TEST_USER_ID, TEST_GROUP_ID) } returns TEST_FLOW

        val result = locationSharingStorageManager.getStateFlowOfUserInGroup(TEST_USER_ID, TEST_GROUP_ID)

        assertEquals(TEST_FLOW, result)
        verify(exactly = 1) { mockLocationSharingInterface.getStateFlowOfUserInGroup(TEST_USER_ID, TEST_GROUP_ID) }
    }

    @Test
    fun getAllUserStatesOfGroup() = runBlockingTest {
        val TEST_LIST = listOf<LocationSharingState>()

        coEvery { mockLocationSharingInterface.getAllUserStatesOfGroup(TEST_GROUP_ID) } returns TEST_LIST

        val result = locationSharingStorageManager.getAllUserStatesOfGroup(TEST_GROUP_ID)

        assertEquals(TEST_LIST, result)
        coVerify(exactly = 1) { mockLocationSharingInterface.getAllUserStatesOfGroup(TEST_GROUP_ID) }
    }

    @Test
    fun getStatesFlowOfAllUserInGroup() = runBlockingTest {
        val TEST_FLOW = flowOf<List<LocationSharingState>>()

        every { mockLocationSharingInterface.getStatesFlowOfAllUserInGroup(TEST_GROUP_ID) } returns TEST_FLOW

        val result = locationSharingStorageManager.getStatesFlowOfAllUserInGroup(TEST_GROUP_ID)

        assertEquals(TEST_FLOW, result)
        verify(exactly = 1) { mockLocationSharingInterface.getStatesFlowOfAllUserInGroup(TEST_GROUP_ID) }
    }

    @Test
    fun storeLocationSharingstate() = runBlockingTest {
        locationSharingStorageManager.storeLocationSharingState(TEST_STATUS)

        coVerify(exactly = 1) { mockLocationSharingInterface.insert(TEST_STATUS) }
    }

    @Test
    fun deleteAll() = runBlockingTest {
        locationSharingStorageManager.deleteAll(TEST_GROUP_ID)

        coVerify(exactly = 1) { mockLocationSharingInterface.deleteAll(TEST_GROUP_ID) }
    }
}
