package tice.managers

import io.mockk.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tice.managers.messaging.PostOfficeType
import tice.managers.storageManagers.GroupStorageManagerType
import tice.managers.storageManagers.LocationSharingStorageManagerType
import tice.models.*
import tice.models.messaging.LocationUpdateV2
import tice.models.messaging.Payload
import tice.models.messaging.PayloadContainerBundle
import tice.models.messaging.PayloadMetaInfo
import tice.utility.provider.CoroutineContextProviderType
import java.util.*

internal class LocationSharingManagerTest {

    private lateinit var locationSharingManager: LocationSharingManager

    private val mockLocationSharingStorageManager: LocationSharingStorageManagerType = mockk(relaxUnitFun = true)
    private val mockLocationManager: LocationManagerType = mockk(relaxUnitFun = true)
    private val mockPostOffice: PostOfficeType = mockk(relaxUnitFun = true)
    private val mockGroupStorageManager: GroupStorageManagerType = mockk(relaxUnitFun = true)
    private val mockUserManager: UserManagerType = mockk(relaxUnitFun = true)
    private val mockCoroutineContextProvider: CoroutineContextProviderType = mockk(relaxUnitFun = true)
    private val mockSignedInUserManager: SignedInUserManagerType = mockk(relaxUnitFun = true)

    private val TEST_CHECK_TIME = 50L
    private val TEST_LOCATION_MAX_AGE = 100L

    private val TEST_USER_ID = UUID.randomUUID()
    private val TEST_USER_NAME = "UserName"
    private val TEST_USER_PUBLIC_KEY = "PublicKey".toByteArray()
    private val TEST_GROUP_ID = UUID.randomUUID()
    private val TEST_NEW_DATE = Date()
    private val TEST_OLD_DATE = Date(TEST_NEW_DATE.time - 1000L)

    private val mockTestUser: User = mockk(relaxUnitFun = true)

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        every { mockSignedInUserManager.signedInUser } returns mockk {
            every { userId } returns TEST_USER_ID
        }

        every { mockTestUser.userId } returns TEST_USER_ID
        every { mockTestUser.publicName } returns TEST_USER_NAME
        every { mockTestUser.publicSigningKey } returns TEST_USER_PUBLIC_KEY

        locationSharingManager = LocationSharingManager(
            mockLocationSharingStorageManager,
            mockLocationManager,
            mockPostOffice,
            mockGroupStorageManager,
            mockUserManager,
            mockCoroutineContextProvider,
            mockSignedInUserManager,
            TEST_CHECK_TIME,
            TEST_LOCATION_MAX_AGE
        )
    }

    // Refactor test: Flaky due to concurrency issues

//    @Test
//    fun startOutdatedLocationSharingStateCheck() = runBlocking {
//        val TEST_USER_ID_2 = UUID.randomUUID()
//
//        val TEST_STATE_1 = LocationSharingState(TEST_USER_ID, TEST_GROUP_ID, true, TEST_OLD_DATE)
//        val TEST_STATE_2 = LocationSharingState(TEST_USER_ID_2, TEST_GROUP_ID, true, TEST_OLD_DATE)
//
//        val testDispatcher = TestCoroutineDispatcher()
//        val locationStateSlot = slot<LocationSharingState>()
//
//        every { mockCoroutineContextProvider.IO } returns testDispatcher
//        coEvery { mockLocationSharingStorageManager.getAllStatesEnabled(true) } returns listOf(TEST_STATE_1, TEST_STATE_2)
//        coEvery { mockLocationSharingStorageManager.storeLocationSharingState(capture(locationStateSlot)) } returns Unit
//        every { mockLocationManager.lastLocation(UserGroupIds(TEST_USER_ID, TEST_GROUP_ID)) }
//            .returns(mockk { every { timestamp } returns TEST_OLD_DATE })
//        every { mockLocationManager.lastLocation(UserGroupIds(TEST_USER_ID_2, TEST_GROUP_ID)) }
//            .returns(mockk { every { timestamp } returns TEST_OLD_DATE })
//
//        locationSharingManager.startOutdatedLocationSharingStateCheck()
//
//        delay(60L)
//        testDispatcher.advanceUntilIdle()
//
//        coVerify(exactly = 1) { mockLocationSharingStorageManager.storeLocationSharingState(any()) }
//    }

    @Test
    fun checkOutdatedLocationSharingStates() = runBlockingTest {
        val TEST_USER_ID_2 = UUID.randomUUID()

        val TEST_STATE_1 = LocationSharingState(TEST_USER_ID, TEST_GROUP_ID, true, TEST_OLD_DATE)
        val TEST_STATE_2 = LocationSharingState(TEST_USER_ID_2, TEST_GROUP_ID, true, TEST_OLD_DATE)

        val locationStateSlot = slot<LocationSharingState>()

        coEvery { mockLocationSharingStorageManager.getAllStatesEnabled(true) } returns listOf(TEST_STATE_1, TEST_STATE_2)
        coEvery { mockLocationSharingStorageManager.storeLocationSharingState(capture(locationStateSlot)) } returns Unit

        locationSharingManager.checkOutdatedLocationSharingStates()

        coVerify(exactly = 1) { mockLocationSharingStorageManager.storeLocationSharingState(any()) }
    }

    @Test
    fun getAllLocationSharingStatesOfGroup() = runBlockingTest {
        val TEST_DATE = Date()
        val TEST_USER_ID_1 = UUID.randomUUID()
        val TEST_USER_ID_2 = UUID.randomUUID()
        val TEST_USER_ID_3 = UUID.randomUUID()
        val TEST_STATES = listOf(LocationSharingState(TEST_USER_ID_3, TEST_GROUP_ID, false, TEST_DATE))

        val mockMembership1: Membership = mockk {
            every { userId } returns TEST_USER_ID_1
            every { groupId } returns TEST_GROUP_ID
        }
        val mockMembership2: Membership = mockk {
            every { userId } returns TEST_USER_ID_2
            every { groupId } returns TEST_GROUP_ID
        }
        val mockMembership3: Membership = mockk {
            every { userId } returns TEST_USER_ID_3
            every { groupId } returns TEST_GROUP_ID
        }

        val mockMemberships = setOf(mockMembership1, mockMembership2, mockMembership3)

        coEvery { mockLocationSharingStorageManager.getAllUserStatesOfGroup(TEST_GROUP_ID) } returns TEST_STATES
        coEvery { mockGroupStorageManager.loadMembershipsOfGroup(TEST_GROUP_ID) } returns mockMemberships

        val result = locationSharingManager.getAllLocationSharingStatesOfGroup(TEST_GROUP_ID)

        val resultUser1 = result.find { it.userId == TEST_USER_ID_1 && it.groupId == TEST_GROUP_ID && !it.sharingEnabled }
        val resultUser2 = result.find { it.userId == TEST_USER_ID_2 && it.groupId == TEST_GROUP_ID && !it.sharingEnabled }
        val resultUser3 = result.find { it.userId == TEST_USER_ID_3 && it.groupId == TEST_GROUP_ID && !it.sharingEnabled }

        Assertions.assertEquals(3, result.size)
        Assertions.assertNotNull(resultUser1)
        Assertions.assertNotNull(resultUser2)
        Assertions.assertNotNull(resultUser3)
    }

    @Test
    fun getFlowOfAllLocationSharingStatesOfGroup() = runBlockingTest {
        val TEST_DATE = Date()
        val TEST_USER_ID_1 = UUID.randomUUID()
        val TEST_USER_ID_2 = UUID.randomUUID()
        val TEST_USER_ID_3 = UUID.randomUUID()
        val TEST_STATES = listOf(LocationSharingState(TEST_USER_ID_3, TEST_GROUP_ID, false, TEST_DATE))

        val TEST_FLOW = flowOf(TEST_STATES)

        val mockMembership1: Membership = mockk {
            every { userId } returns TEST_USER_ID_1
            every { groupId } returns TEST_GROUP_ID
        }
        val mockMembership2: Membership = mockk {
            every { userId } returns TEST_USER_ID_2
            every { groupId } returns TEST_GROUP_ID
        }
        val mockMembership3: Membership = mockk {
            every { userId } returns TEST_USER_ID_3
            every { groupId } returns TEST_GROUP_ID
        }

        val mockMemberships = setOf(mockMembership1, mockMembership2, mockMembership3)

        coEvery { mockLocationSharingStorageManager.getStatesFlowOfAllUserInGroup(TEST_GROUP_ID) } returns TEST_FLOW
        coEvery { mockGroupStorageManager.loadMembershipsOfGroup(TEST_GROUP_ID) } returns mockMemberships

        val result: MutableList<LocationSharingState> = mutableListOf()
        locationSharingManager.getFlowOfAllLocationSharingStatesOfGroup(TEST_GROUP_ID).collect { result.addAll(it) }

        val resultUser1 = result.find { it.userId == TEST_USER_ID_1 && it.groupId == TEST_GROUP_ID && !it.sharingEnabled }
        val resultUser2 = result.find { it.userId == TEST_USER_ID_2 && it.groupId == TEST_GROUP_ID && !it.sharingEnabled }
        val resultUser3 = result.find { it.userId == TEST_USER_ID_3 && it.groupId == TEST_GROUP_ID && !it.sharingEnabled }

        Assertions.assertEquals(3, result.size)
        Assertions.assertNotNull(resultUser1)
        Assertions.assertNotNull(resultUser2)
        Assertions.assertNotNull(resultUser3)
    }


    @Test
    fun registerEnvelopeReceiver() = runBlockingTest {
        locationSharingManager.registerEnvelopeReceiver()

        verify(exactly = 1) {
            mockPostOffice.registerEnvelopeReceiver(
                Payload.PayloadType.LocationUpdateV2,
                locationSharingManager)
        }
    }

    @Nested
    inner class ReceivePayloadContainerBundle {

        @Test
        fun receivePayloadContainerBundle_LastLocationUpdated_KeepHighestTimestamp() = runBlockingTest {
            val mockMetaInfo = mockk<PayloadMetaInfo>()
            val mockLocation = mockk<Location>()
            val payloadBundle =
                PayloadContainerBundle(Payload.PayloadType.LocationUpdateV2, LocationUpdateV2(mockLocation, TEST_GROUP_ID), mockMetaInfo)

            val mockMetaInfo2 = mockk<PayloadMetaInfo>()
            val mockLocation2 = mockk<Location>()
            val payloadBundle2 =
                PayloadContainerBundle(Payload.PayloadType.LocationUpdateV2, LocationUpdateV2(mockLocation2, TEST_GROUP_ID), mockMetaInfo2)

            val timestampOld = Date(Date().time - 10000L)
            val timestampNew = Date(Date().time - 5000L)

            every { mockMetaInfo.senderId } returns TEST_USER_ID
            every { mockLocation.timestamp } returns timestampOld

            every { mockMetaInfo2.senderId } returns TEST_USER_ID
            every { mockLocation2.timestamp } returns timestampNew

            coEvery { mockUserManager.getUser(TEST_USER_ID) } returns mockTestUser

            coEvery { mockLocationSharingStorageManager.getStateOfUserInGroup(TEST_USER_ID, TEST_GROUP_ID) } returns LocationSharingState(TEST_USER_ID, TEST_GROUP_ID, true, Date(Date().time - 20000L))

            locationSharingManager.handlePayloadContainerBundle(payloadBundle2)
            val firstResult = locationSharingManager.lastLocation(UserGroupIds(TEST_USER_ID, TEST_GROUP_ID))

            locationSharingManager.handlePayloadContainerBundle(payloadBundle)
            val secondResult = locationSharingManager.lastLocation(UserGroupIds(TEST_USER_ID, TEST_GROUP_ID))

            Assertions.assertEquals(mockLocation2, firstResult)
            Assertions.assertEquals(mockLocation2, secondResult)
            coVerify(exactly = 2) { mockUserManager.getUser(TEST_USER_ID) }
        }

        @Test
        fun receivePayloadContainerBundle_FlowEmittedOnce_DontUpdateOlderTimeStamp() = runBlockingTest {
            val mockMetaInfo = mockk<PayloadMetaInfo>()
            val mockLocation = mockk<Location>()
            val payloadBundle =
                PayloadContainerBundle(Payload.PayloadType.LocationUpdateV2, LocationUpdateV2(mockLocation, TEST_GROUP_ID), mockMetaInfo)

            val timestampOld = Date(Date().time - 10000L)
            val timestampNew = Date(Date().time - 5000L)

            every { mockMetaInfo.senderId } returns TEST_USER_ID
            every { mockLocation.timestamp } returns timestampOld

            val mockMetaInfo2 = mockk<PayloadMetaInfo>()
            val mockLocation2 = mockk<Location>()
            val payloadBundle2 =
                PayloadContainerBundle(Payload.PayloadType.LocationUpdateV2, LocationUpdateV2(mockLocation2, TEST_GROUP_ID), mockMetaInfo2)
            every { mockMetaInfo2.senderId } returns TEST_USER_ID
            every { mockLocation2.timestamp } returns timestampNew

            coEvery { mockUserManager.getUser(TEST_USER_ID) } returns mockTestUser

            coEvery { mockLocationSharingStorageManager.getStateOfUserInGroup(TEST_USER_ID, TEST_GROUP_ID) } returns LocationSharingState(TEST_USER_ID, TEST_GROUP_ID, true, Date(Date().time - 20000L))

            val results = locationSharingManager.memberLocationFlow.onSubscription {
                TestCoroutineScope(TestCoroutineDispatcher()).launch {
                    locationSharingManager.handlePayloadContainerBundle(payloadBundle2)
                    locationSharingManager.handlePayloadContainerBundle(payloadBundle)
                }
            }.take(1).toList()

            val takenResult = locationSharingManager.lastLocation(UserGroupIds(TEST_USER_ID, TEST_GROUP_ID))

            Assertions.assertEquals(mockLocation2, takenResult)
            Assertions.assertEquals(1, results.size)
            Assertions.assertEquals(UserLocation(TEST_USER_ID, mockLocation2), results[0])
        }

        @Test
        fun receivePayloadContainerBundle_FlowEmittedTwice_UpdatedLocation_NewerTimeStamp() = runBlockingTest {
            val timestampOld = Date(Date().time - 10000L)
            val timestampNew = Date(Date().time - 5000L)

            val mockMetaInfo = mockk<PayloadMetaInfo>()
            val mockLocation = mockk<Location>()
            val payloadBundle =
                PayloadContainerBundle(Payload.PayloadType.LocationUpdateV2, LocationUpdateV2(mockLocation, TEST_GROUP_ID), mockMetaInfo)
            every { mockMetaInfo.senderId } returns TEST_USER_ID
            every { mockLocation.timestamp } returns timestampOld

            val mockMetaInfo2 = mockk<PayloadMetaInfo>()
            val mockLocation2 = mockk<Location>()
            val payloadBundle2 =
                PayloadContainerBundle(Payload.PayloadType.LocationUpdateV2, LocationUpdateV2(mockLocation2, TEST_GROUP_ID), mockMetaInfo2)
            every { mockMetaInfo2.senderId } returns TEST_USER_ID
            every { mockLocation2.timestamp } returns timestampNew

            coEvery { mockUserManager.getUser(TEST_USER_ID) } returns mockTestUser

            coEvery { mockLocationSharingStorageManager.getStateOfUserInGroup(TEST_USER_ID, TEST_GROUP_ID) } returns LocationSharingState(TEST_USER_ID, TEST_GROUP_ID, true, Date(Date().time - 20000L))

            val results = locationSharingManager.memberLocationFlow.onSubscription {
                TestCoroutineScope(TestCoroutineDispatcher()).launch {
                    locationSharingManager.handlePayloadContainerBundle(payloadBundle)
                    locationSharingManager.handlePayloadContainerBundle(payloadBundle2)
                }
            }.take(2).toList()

            val grabbedResult = locationSharingManager.lastLocation(UserGroupIds(TEST_USER_ID, TEST_GROUP_ID))

            Assertions.assertEquals(mockLocation2, grabbedResult)
            Assertions.assertEquals(2, results.size)
            Assertions.assertEquals(UserLocation(TEST_USER_ID, mockLocation), results[0])
            Assertions.assertEquals(UserLocation(TEST_USER_ID, mockLocation2), results[1])
        }

        @Test
        fun receivePayloadContainerBundle_LastLocationUpdated() = runBlockingTest {
            val timestampOld = Date(Date().time - 10000L)
            val timestampNew = Date(Date().time - 5000L)

            val mockMetaInfo = mockk<PayloadMetaInfo>()
            val mockLocation = mockk<Location>()
            val payloadBundle =
                PayloadContainerBundle(Payload.PayloadType.LocationUpdateV2, LocationUpdateV2(mockLocation, TEST_GROUP_ID), mockMetaInfo)

            val mockMetaInfo2 = mockk<PayloadMetaInfo>()
            val mockLocation2 = mockk<Location>()
            val payloadBundle2 =
                PayloadContainerBundle(Payload.PayloadType.LocationUpdateV2, LocationUpdateV2(mockLocation2, TEST_GROUP_ID), mockMetaInfo2)

            every { mockMetaInfo.senderId } returns TEST_USER_ID
            every { mockLocation.timestamp } returns timestampOld

            every { mockMetaInfo2.senderId } returns TEST_USER_ID
            every { mockLocation2.timestamp } returns timestampNew

            coEvery { mockUserManager.getUser(TEST_USER_ID) } returns mockTestUser

            coEvery { mockLocationSharingStorageManager.getStateOfUserInGroup(TEST_USER_ID, TEST_GROUP_ID) } returns LocationSharingState(TEST_USER_ID, TEST_GROUP_ID, true, Date(Date().time - 20000L))

            locationSharingManager.handlePayloadContainerBundle(payloadBundle)
            val firstResult = locationSharingManager.lastLocation(UserGroupIds(TEST_USER_ID, TEST_GROUP_ID))

            locationSharingManager.handlePayloadContainerBundle(payloadBundle2)
            val secondResult = locationSharingManager.lastLocation(UserGroupIds(TEST_USER_ID, TEST_GROUP_ID))

            Assertions.assertEquals(mockLocation, firstResult)
            Assertions.assertEquals(mockLocation2, secondResult)
            coVerify(exactly = 2) { mockUserManager.getUser(TEST_USER_ID) }
        }
    }
}