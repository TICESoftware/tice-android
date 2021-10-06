package tice.managers.storageManagers

import androidx.lifecycle.LiveData
import androidx.room.withTransaction
import io.mockk.*
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tice.models.*
import tice.models.database.*
import java.util.*

internal class GroupStorageManagerTest {

    private lateinit var groupStorageManager: GroupStorageManager

    private val mockAppDatabase: AppDatabase = mockk(relaxUnitFun = true)

    private val mockGroupInterface: GroupInterface = mockk(relaxUnitFun = true)
    private val mockUserInterface: UserInterface = mockk(relaxUnitFun = true)
    private val mockChatInterface: ChatMessageInterface = mockk(relaxUnitFun = true)
    private val mockLocationSharingInterface: LocationSharingInterface = mockk(relaxUnitFun = true)

    private val TEST_GROUP_ID = GroupId.randomUUID()

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        every { mockAppDatabase.groupInterface() } returns mockGroupInterface
        every { mockAppDatabase.userInterface() } returns mockUserInterface
        every { mockAppDatabase.chatMessageInterface() } returns mockChatInterface
        every { mockAppDatabase.locationSharingInterface() } returns mockLocationSharingInterface

        groupStorageManager = GroupStorageManager(mockAppDatabase)
    }

    @Test
    fun getTeams() = runBlockingTest {
        val mockTeamLiveData: LiveData<List<Team>> = mockk()

        coEvery { mockGroupInterface.getTeamsObservable() } returns mockTeamLiveData

        val result = groupStorageManager.teams

        assertEquals(mockTeamLiveData, result)
    }

    @Test
    fun loadTeam() = runBlockingTest {
        val mockTeam: Team = mockk()

        coEvery { mockGroupInterface.getTeam(TEST_GROUP_ID) } returns mockTeam

        val result = groupStorageManager.loadTeam(TEST_GROUP_ID)

        assertEquals(mockTeam, result)
    }

    @Test
    fun getTeamObservable() = runBlockingTest {
        val mockTeamLiveData: LiveData<Team?> = mockk()

        coEvery { mockGroupInterface.getTeamObservable(TEST_GROUP_ID) } returns mockTeamLiveData

        val result = groupStorageManager.getTeamObservable(TEST_GROUP_ID)

        assertEquals(mockTeamLiveData, result)
    }

    @Test
    fun loadTeams() = runBlockingTest {
        val mockTeam: Team = mockk()

        val teamList: List<Team> = listOf(mockTeam)

        coEvery { mockGroupInterface.getAllTeams() } returns teamList
        val expected = teamList.toSet()

        val result = groupStorageManager.loadTeams()

        assertEquals(expected, result)
    }

    @Test
    fun loadMeetups() = runBlockingTest {
        val mockMeetup: Meetup = mockk()

        val meetupList: List<Meetup> = listOf(mockMeetup)

        coEvery { mockGroupInterface.getAllMeetups() } returns meetupList
        val expected = meetupList.toSet()

        val result = groupStorageManager.loadMeetups()
        assertEquals(expected, result)
    }

    @Test
    fun loadMeetup() = runBlockingTest {
        val mockMeetup: Meetup = mockk()

        coEvery { mockGroupInterface.getMeetup(TEST_GROUP_ID) } returns mockMeetup

        val result = groupStorageManager.loadMeetup(TEST_GROUP_ID)

        assertEquals(mockMeetup, result)
    }

    @Test
    fun getMeetupObservableForTeam() {
        val mockMeetupLiveData: LiveData<Meetup?> = mockk()

        coEvery { mockGroupInterface.getMeetupObservableForTeam(TEST_GROUP_ID) } returns mockMeetupLiveData

        val result = groupStorageManager.getMeetupObservableForTeam(TEST_GROUP_ID)

        assertEquals(mockMeetupLiveData, result)
    }

    @Test
    fun teamOfMeetup() = runBlockingTest {
        val mockTeam: Team = mockk { every { groupId } returns TEST_GROUP_ID }
        val mockMeetup: Meetup = mockk()

        every { mockMeetup.teamId } returns TEST_GROUP_ID
        coEvery { mockGroupInterface.getTeam(TEST_GROUP_ID) } returns mockTeam

        val result = groupStorageManager.teamOfMeetup(mockMeetup)

        assertEquals(mockTeam, result)
    }

    @Test
    fun meetupInTeam() = runBlockingTest {
        val mockTeam: Team = mockk()
        val mockMeetup: Meetup = mockk { every { teamId } returns TEST_GROUP_ID }

        every { mockMeetup.teamId } returns TEST_GROUP_ID
        coEvery { mockGroupInterface.getTeamAndMeetup(TEST_GROUP_ID) } returns TeamAndMeetup(mockTeam, mockMeetup)

        val result = groupStorageManager.meetupInTeam(TEST_GROUP_ID)

        assertEquals(mockMeetup, result)
    }

    @Nested
    inner class StoreTeam {

        @Test
        fun `success with meetup and MembershipsDiff Add`() = runBlockingTest {
            val membership1 = Membership(
                UserId.randomUUID(),
                TEST_GROUP_ID,
                "publicKey1".toByteArray(),
                true,
                "selfSignedCert1",
                "serverSignedCert1",
                "adminSignedCert1"
            )

            val membership2 = Membership(
                UserId.randomUUID(),
                TEST_GROUP_ID,
                "publicKey2".toByteArray(),
                false,
                "selfSignedCert2",
                "serverSignedCert2",
                "adminSignedCert2"
            )

            val membershipsDiff: MembershipsDiff.Add = MembershipsDiff.Add(listOf(membership1, membership2))

            val mockTeam: Team = mockk { every { meetupId } returns TEST_GROUP_ID }

            val lambdaSlot = slot<(suspend () -> MembershipsDiff.Add)>()
            mockkStatic("androidx.room.RoomDatabaseKt")
            coEvery { mockAppDatabase.withTransaction(capture(lambdaSlot)) } returns membershipsDiff

            every { mockTeam.groupId } returns TEST_GROUP_ID
            every { mockTeam.meetupId } returns null
            coEvery { mockGroupInterface.getTeam(TEST_GROUP_ID) } returns mockTeam

            groupStorageManager.storeTeam(mockTeam, membershipsDiff)
            lambdaSlot.captured.invoke()

            coVerify(exactly = 1) { mockGroupInterface.deleteMeetupOfTeam(TEST_GROUP_ID) }
            coVerify(exactly = 1) { mockGroupInterface.insert(mockTeam) }

            coVerify(exactly = 1) { mockGroupInterface.insert(membershipsDiff.memberships.map { it.databaseEntity() }) }
        }

        @Test
        fun `success with meetup and MembershipsDiff Remove`() = runBlockingTest {
            val membership1 = Membership(
                UserId.randomUUID(),
                TEST_GROUP_ID,
                "publicKey1".toByteArray(),
                true,
                "selfSignedCert1",
                "serverSignedCert1",
                "adminSignedCert1"
            )

            val membership2 = Membership(
                UserId.randomUUID(),
                TEST_GROUP_ID,
                "publicKey2".toByteArray(),
                false,
                "selfSignedCert2",
                "serverSignedCert2",
                "adminSignedCert2"
            )

            val membershipsDiff: MembershipsDiff.Remove = MembershipsDiff.Remove(listOf(membership1, membership2))

            val mockTeam: Team = mockk { every { meetupId } returns TEST_GROUP_ID }

            val lambdaSlot = slot<(suspend () -> MembershipsDiff.Remove)>()
            mockkStatic("androidx.room.RoomDatabaseKt")
            coEvery { mockAppDatabase.withTransaction(capture(lambdaSlot)) } returns membershipsDiff

            every { mockTeam.groupId } returns TEST_GROUP_ID
            every { mockTeam.meetupId } returns null
            coEvery { mockGroupInterface.getTeam(TEST_GROUP_ID) } returns mockTeam

            groupStorageManager.storeTeam(mockTeam, membershipsDiff)
            lambdaSlot.captured.invoke()

            coVerify(exactly = 1) { mockGroupInterface.deleteMeetupOfTeam(TEST_GROUP_ID) }
            coVerify(exactly = 1) { mockGroupInterface.insert(mockTeam) }

            coVerify(exactly = 1) { mockGroupInterface.delete(membershipsDiff.memberships.map { it.databaseEntity() }) }
        }


        @Test
        fun `success with meetup and MembershipsDiff Replace`() = runBlockingTest {
            val membership1 = Membership(
                UserId.randomUUID(),
                TEST_GROUP_ID,
                "publicKey1".toByteArray(),
                true,
                "selfSignedCert1",
                "serverSignedCert1",
                "adminSignedCert1"
            )

            val membership2 = Membership(
                UserId.randomUUID(),
                TEST_GROUP_ID,
                "publicKey2".toByteArray(),
                false,
                "selfSignedCert2",
                "serverSignedCert2",
                "adminSignedCert2"
            )

            val invalidMembership = Membership(
                UserId.randomUUID(),
                TEST_GROUP_ID,
                "publicKey3".toByteArray(),
                false,
                "selfSignedCert3",
                "serverSignedCert3",
                "adminSignedCert3"
            )

            val membershipsDiff: MembershipsDiff.Replace = MembershipsDiff.Replace(listOf(membership1, membership2))

            val mockTeam: Team = mockk { every { meetupId } returns TEST_GROUP_ID }

            val lambdaSlot = slot<(suspend () -> MembershipsDiff.Replace)>()
            mockkStatic("androidx.room.RoomDatabaseKt")
            coEvery { mockAppDatabase.withTransaction(capture(lambdaSlot)) } returns membershipsDiff

            every { mockTeam.groupId } returns TEST_GROUP_ID
            every { mockTeam.meetupId } returns null
            coEvery { mockGroupInterface.getTeam(TEST_GROUP_ID) } returns mockTeam
            coEvery { mockGroupInterface.getMembershipsOfGroup(TEST_GROUP_ID) } returns listOf(
                membership1.databaseEntity(),
                membership2.databaseEntity(),
                invalidMembership.databaseEntity()
            )

            groupStorageManager.storeTeam(mockTeam, membershipsDiff)
            lambdaSlot.captured.invoke()

            coVerify(exactly = 1) { mockGroupInterface.deleteMeetupOfTeam(TEST_GROUP_ID) }
            coVerify(exactly = 1) { mockGroupInterface.insert(mockTeam) }

            coVerify(exactly = 1) { mockGroupInterface.delete(listOf(invalidMembership.databaseEntity())) }
            coVerify(exactly = 1) { mockGroupInterface.insert(membershipsDiff.memberships.map { it.databaseEntity() }) }
        }

        @Test
        fun `success without meetup and without MembershipsDiff`() = runBlockingTest {
            val mockTeam: Team = mockk { every { meetupId } returns TEST_GROUP_ID }

            val lambdaSlot = slot<(suspend () -> MembershipsDiff.Replace)>()
            mockkStatic("androidx.room.RoomDatabaseKt")
            coEvery { mockAppDatabase.withTransaction(capture(lambdaSlot)) } returns mockk()

            every { mockTeam.groupId } returns TEST_GROUP_ID
            coEvery { mockGroupInterface.getTeam(TEST_GROUP_ID) } returns mockTeam

            groupStorageManager.storeTeam(mockTeam, null)
            lambdaSlot.captured.invoke()

            coVerify(exactly = 0) { mockGroupInterface.deleteMeetupOfTeam(TEST_GROUP_ID) }
            coVerify(exactly = 1) { mockGroupInterface.insert(mockTeam) }
        }
    }

    @Nested
    inner class StoreMeetup {

        @Test
        fun `success with MembershipsDiff Add`() = runBlockingTest {
            val membership1 = Membership(
                UserId.randomUUID(),
                TEST_GROUP_ID,
                "publicKey1".toByteArray(),
                true,
                "selfSignedCert1",
                "serverSignedCert1",
                "adminSignedCert1"
            )

            val membership2 = Membership(
                UserId.randomUUID(),
                TEST_GROUP_ID,
                "publicKey2".toByteArray(),
                false,
                "selfSignedCert2",
                "serverSignedCert2",
                "adminSignedCert2"
            )

            val membershipsDiff: MembershipsDiff.Add = MembershipsDiff.Add(listOf(membership1, membership2))

            val mockMeetup: Meetup = mockk { every { groupId } returns TEST_GROUP_ID }

            val lambdaSlot = slot<(suspend () -> MembershipsDiff.Add)>()
            mockkStatic("androidx.room.RoomDatabaseKt")
            coEvery { mockAppDatabase.withTransaction(capture(lambdaSlot)) } returns membershipsDiff

            groupStorageManager.storeMeetup(mockMeetup, membershipsDiff)
            lambdaSlot.captured.invoke()

            coVerify(exactly = 1) { mockGroupInterface.insert(mockMeetup) }
            coVerify(exactly = 1) { mockGroupInterface.insert(membershipsDiff.memberships.map { it.databaseEntity() }) }
        }

        @Test
        fun `success with MembershipsDiff Remove`() = runBlockingTest {
            val membership1 = Membership(
                UserId.randomUUID(),
                TEST_GROUP_ID,
                "publicKey1".toByteArray(),
                true,
                "selfSignedCert1",
                "serverSignedCert1",
                "adminSignedCert1"
            )

            val membership2 = Membership(
                UserId.randomUUID(),
                TEST_GROUP_ID,
                "publicKey2".toByteArray(),
                false,
                "selfSignedCert2",
                "serverSignedCert2",
                "adminSignedCert2"
            )

            val membershipsDiff: MembershipsDiff.Remove = MembershipsDiff.Remove(listOf(membership1, membership2))

            val mockMeetup: Meetup = mockk { every { groupId } returns TEST_GROUP_ID }

            val lambdaSlot = slot<(suspend () -> MembershipsDiff.Remove)>()
            mockkStatic("androidx.room.RoomDatabaseKt")
            coEvery { mockAppDatabase.withTransaction(capture(lambdaSlot)) } returns membershipsDiff

            groupStorageManager.storeMeetup(mockMeetup, membershipsDiff)
            lambdaSlot.captured.invoke()

            coVerify(exactly = 1) { mockGroupInterface.insert(mockMeetup) }
            coVerify(exactly = 1) { mockGroupInterface.delete(membershipsDiff.memberships.map { it.databaseEntity() }) }
        }

        @Test
        fun `success with MembershipsDiff Replace`() = runBlockingTest {
            val membership1 = Membership(
                UserId.randomUUID(),
                TEST_GROUP_ID,
                "publicKey1".toByteArray(),
                true,
                "selfSignedCert1",
                "serverSignedCert1",
                "adminSignedCert1"
            )

            val membership2 = Membership(
                UserId.randomUUID(),
                TEST_GROUP_ID,
                "publicKey2".toByteArray(),
                false,
                "selfSignedCert2",
                "serverSignedCert2",
                "adminSignedCert2"
            )

            val invalidMembership = Membership(
                UserId.randomUUID(),
                TEST_GROUP_ID,
                "publicKey3".toByteArray(),
                false,
                "selfSignedCert3",
                "serverSignedCert3",
                "adminSignedCert3"
            )

            val membershipsDiff: MembershipsDiff.Replace = MembershipsDiff.Replace(listOf(membership1, membership2))

            val mockMeetup: Meetup = mockk { every { groupId } returns TEST_GROUP_ID }

            val lambdaSlot = slot<(suspend () -> MembershipsDiff.Replace)>()
            mockkStatic("androidx.room.RoomDatabaseKt")
            coEvery { mockAppDatabase.withTransaction(capture(lambdaSlot)) } returns membershipsDiff

            coEvery { mockGroupInterface.getMembershipsOfGroup(TEST_GROUP_ID) } returns listOf(
                membership1.databaseEntity(),
                membership2.databaseEntity(),
                invalidMembership.databaseEntity()
            )

            groupStorageManager.storeMeetup(mockMeetup, membershipsDiff)
            lambdaSlot.captured.invoke()

            coVerify(exactly = 1) { mockGroupInterface.insert(mockMeetup) }
            coVerify(exactly = 1) { mockGroupInterface.delete(listOf(invalidMembership.databaseEntity())) }
            coVerify(exactly = 1) { mockGroupInterface.insert(membershipsDiff.memberships.map { it.databaseEntity() }) }
        }


        @Test
        fun `success without meetup and without MembershipsDiff`() = runBlockingTest {
            val mockMeetup: Meetup = mockk { every { groupId } returns TEST_GROUP_ID }

            val lambdaSlot = slot<(suspend () -> MembershipsDiff.Replace)>()
            mockkStatic("androidx.room.RoomDatabaseKt")
            coEvery { mockAppDatabase.withTransaction(capture(lambdaSlot)) } returns mockk()

            groupStorageManager.storeMeetup(mockMeetup, null)
            lambdaSlot.captured.invoke()

            coVerify(exactly = 0) { mockGroupInterface.deleteMeetupOfTeam(TEST_GROUP_ID) }
            coVerify(exactly = 1) { mockGroupInterface.insert(mockMeetup) }
        }
    }

    @Test
    fun removeMeetup() = runBlockingTest {
        val TEST_MEETUP_ID = UUID.randomUUID()

        val mockMeetup: Meetup = mockk()

        val lambdaSlot = slot<(suspend () -> MembershipsDiff)>()
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { mockAppDatabase.withTransaction(capture(lambdaSlot)) } returns mockk()

        every { mockMeetup.groupId } returns TEST_GROUP_ID
        every { mockMeetup.teamId } returns TEST_MEETUP_ID

        groupStorageManager.removeMeetup(mockMeetup)
        lambdaSlot.captured.invoke()

        coVerify(exactly = 1) { mockGroupInterface.deleteMembership(TEST_GROUP_ID) }
        coVerify(exactly = 1) { mockGroupInterface.updateMeetup(TEST_MEETUP_ID, null) }
    }

    @Test
    fun removeTeam() = runBlockingTest {
        val TEST_MEETUP_ID = UUID.randomUUID()

        val mockTeam: Team = mockk()

        val lambdaSlot = slot<(suspend () -> MembershipsDiff)>()
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { mockAppDatabase.withTransaction(capture(lambdaSlot)) } returns mockk()

        every { mockTeam.groupId } returns TEST_GROUP_ID
        every { mockTeam.meetupId } returns TEST_MEETUP_ID

        groupStorageManager.removeTeam(TEST_GROUP_ID)
        lambdaSlot.captured.invoke()

        coVerify(exactly = 1) { mockGroupInterface.deleteMeetupOfTeam(TEST_GROUP_ID) }
        coVerify(exactly = 1) { mockGroupInterface.deleteMembership(TEST_GROUP_ID) }
        coVerify(exactly = 1) { mockGroupInterface.deleteTeam(TEST_GROUP_ID) }
        coVerify(exactly = 1) { mockChatInterface.deleteAll(TEST_GROUP_ID) }
    }

    @Test
    fun loadMembership() = runBlockingTest {
        val TEST_USER_ID = UUID.randomUUID()

        val membership = Membership(
            UserId.randomUUID(),
            TEST_GROUP_ID,
            "publicKey".toByteArray(),
            true,
            "selfSignedCert",
            "serverSignedCert",
            "adminSignedCert"
        )

        coEvery { mockGroupInterface.getMembership(TEST_USER_ID, TEST_GROUP_ID) } returns membership.databaseEntity()

        val result = groupStorageManager.loadMembership(TEST_USER_ID, TEST_GROUP_ID)

        coVerify(exactly = 1) { mockGroupInterface.getMembership(TEST_USER_ID, TEST_GROUP_ID) }
        assertEquals(membership, result)
    }

    @Test
    fun `loadNullableMembership not null`() = runBlockingTest {
        val TEST_USER_ID = UUID.randomUUID()

        val membership = Membership(
            UserId.randomUUID(),
            TEST_GROUP_ID,
            "publicKey".toByteArray(),
            true,
            "selfSignedCert",
            "serverSignedCert",
            "adminSignedCert"
        )

        coEvery { mockGroupInterface.getNullableMembership(TEST_USER_ID, TEST_GROUP_ID) } returns membership.databaseEntity()

        val result = groupStorageManager.loadNullableMembership(TEST_USER_ID, TEST_GROUP_ID)

        coVerify(exactly = 1) { mockGroupInterface.getNullableMembership(TEST_USER_ID, TEST_GROUP_ID) }
        assertEquals(membership, result)
    }

    @Test
    fun `loadNullableMembership null`() = runBlockingTest {
        val TEST_USER_ID = UUID.randomUUID()

        coEvery { mockGroupInterface.getNullableMembership(TEST_USER_ID, TEST_GROUP_ID) } returns null

        val result = groupStorageManager.loadNullableMembership(TEST_USER_ID, TEST_GROUP_ID)

        coVerify(exactly = 1) { mockGroupInterface.getNullableMembership(TEST_USER_ID, TEST_GROUP_ID) }
        assertEquals(null, result)
    }

    @Test
    fun loadMemberships() = runBlockingTest {
        val membership1 = Membership(
            UserId.randomUUID(),
            TEST_GROUP_ID,
            "publicKey1".toByteArray(),
            true,
            "selfSignedCert1",
            "serverSignedCert1",
            "adminSignedCert1"
        )

        val membership2 = Membership(
            UserId.randomUUID(),
            TEST_GROUP_ID,
            "publicKey2".toByteArray(),
            false,
            "selfSignedCert2",
            "serverSignedCert2",
            "adminSignedCert2"
        )

        val entityList = listOf(membership1.databaseEntity(), membership2.databaseEntity())

        coEvery { mockGroupInterface.getMembershipsOfGroup(TEST_GROUP_ID) } returns entityList

        val result = groupStorageManager.loadMembershipsOfGroup(TEST_GROUP_ID)

        coVerify(exactly = 1) { mockGroupInterface.getMembershipsOfGroup(TEST_GROUP_ID) }
        assertEquals(setOf(membership1, membership2), result)
    }

    @Test
    fun getMeetupIdParticipating() = runBlockingTest {
        val TEST_USER_ID = UUID.randomUUID()

        val mockLiveDataListGroupId: LiveData<List<GroupId>> = mockk()

        every { mockGroupInterface.getMeetupIdParticipating(TEST_USER_ID) } returns mockLiveDataListGroupId

        val result = groupStorageManager.getMeetupIdParticipating(TEST_USER_ID)

        assertEquals(mockLiveDataListGroupId, result)
    }

    @Test
    fun isMember_true() = runBlockingTest {
        val TEST_USER_ID = UUID.randomUUID()

        val membership = Membership(
            UserId.randomUUID(),
            TEST_GROUP_ID,
            "publicKey".toByteArray(),
            true,
            "selfSignedCert",
            "serverSignedCert",
            "adminSignedCert"
        )

        coEvery { mockGroupInterface.getMembership(TEST_USER_ID, TEST_GROUP_ID) } returns membership.databaseEntity()

        val result = groupStorageManager.isMember(TEST_USER_ID, TEST_GROUP_ID)

        assertEquals(true, result)
    }


    @Test
    fun isMember_false() = runBlockingTest {
        val TEST_USER_ID = UUID.randomUUID()

        coEvery { mockGroupInterface.getMembership(TEST_USER_ID, TEST_GROUP_ID) } throws Exception()

        val result = groupStorageManager.isMember(TEST_USER_ID, TEST_GROUP_ID)

        assertEquals(false, result)
    }

    @Test
    fun loadUser() = runBlockingTest {
        val TEST_USER_ID = UUID.randomUUID()

        val mockMembership: Membership = mockk { every { userId } returns TEST_USER_ID }
        val mockUser: User = mockk()

        coEvery { mockUserInterface.get(TEST_USER_ID) } returns mockUser

        val result = groupStorageManager.loadUser(mockMembership)
        assertEquals(mockUser, result)
    }

    @Test
    fun members() = runBlockingTest {
        val membership1 = Membership(
            UserId.randomUUID(),
            TEST_GROUP_ID,
            "publicKey1".toByteArray(),
            true,
            "selfSignedCert1",
            "serverSignedCert1",
            "adminSignedCert1"
        )
        val mockUser1: User = mockk()

        val membership2 = Membership(
            UserId.randomUUID(),
            TEST_GROUP_ID,
            "publicKey2".toByteArray(),
            false,
            "selfSignedCert2",
            "serverSignedCert2",
            "adminSignedCert2"
        )
        val mockUser2: User = mockk()

        val entityList = listOf(membership1.databaseEntity(), membership2.databaseEntity())

        coEvery { mockGroupInterface.getMembershipsOfGroup(TEST_GROUP_ID) } returns entityList
        coEvery { mockUserInterface.get(membership1.userId) } returns mockUser1
        coEvery { mockUserInterface.get(membership2.userId) } returns mockUser2

        val expected = setOf(Member(mockUser1, membership1), Member(mockUser2, membership2))

        val result = groupStorageManager.members(TEST_GROUP_ID)

        assertEquals(expected, result)
    }

    @Test
    fun deleteAllData() = runBlockingTest {
        groupStorageManager.deleteAllData()

        coVerify(exactly = 1) { mockGroupInterface.deleteAllGroupData() }
    }
}