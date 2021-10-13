package tice.managers.group

import com.google.android.gms.maps.model.LatLng
import com.ticeapp.TICE.R
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tice.backend.BackendType
import tice.crypto.AuthManagerType
import tice.crypto.CryptoManagerType
import tice.exceptions.BackendException
import tice.exceptions.MeetupManagerException
import tice.managers.SignedInUserManagerType
import tice.managers.storageManagers.ChatStorageManagerType
import tice.managers.storageManagers.GroupStorageManagerType
import tice.managers.storageManagers.MembershipsDiff.*
import tice.models.*
import tice.models.chat.Message
import tice.models.chat.MessageStatus
import tice.models.messaging.GroupUpdate
import tice.models.messaging.MessagePriority
import tice.models.responses.CreateGroupResponse
import tice.models.responses.GroupInternalsResponse
import tice.models.responses.JoinGroupResponse
import tice.models.responses.UpdatedETagResponse
import tice.utility.provider.LocalizationProviderType
import java.lang.ref.WeakReference
import java.net.URL
import java.util.*

internal class MeetupManagerTest {

    private lateinit var meetupManager: MeetupManager

    private val mockGroupManager: GroupManagerType = mockk(relaxUnitFun = true)
    private val mockGroupStorageManager: GroupStorageManagerType = mockk(relaxUnitFun = true)
    private val mockCryptoManager: CryptoManagerType = mockk(relaxUnitFun = true)
    private val mockAuthManager: AuthManagerType = mockk(relaxUnitFun = true)
    private val mockSignedInUserManager: SignedInUserManagerType = mockk(relaxUnitFun = true)
    private val mockBackend: BackendType = mockk(relaxUnitFun = true)
    private val mockMeetupManagerDelegate: MeetupManagerDelegate = mockk(relaxUnitFun = true)
    private val mockChatStorageManager: ChatStorageManagerType = mockk(relaxUnitFun = true)
    private val mockLocalizationProvider: LocalizationProviderType = mockk(relaxUnitFun = true)

    private val mockSignedInUser: SignedInUser = mockk(relaxUnitFun = true)
    private val TEST_SIGNED_IN_USER_ID = UUID.randomUUID()
    private val TEST_SIGNED_IN_PRIVATE_KEY = "PrivateKey".encodeToByteArray()

    private val mockUrl = mockk<URL>()
    private val mockMembership = mockk<Membership>()
    private val mockTeam = mockk<Team>()
    private val mockMeetup = mockk<Meetup>(relaxUnitFun = true)

    private val TEST_JOIN_MODE = JoinMode.Open
    private val TEST_PERMISSION_MODE = PermissionMode.Everyone
    private val TEST_GROUP_NAME = "GroupName"

    private val TEST_TEAM_ID = UUID.randomUUID()
    private val TEST_TEAM_TAG1 = "GroupTag1"
    private val TEST_TEAM_KEY = "TeamKey".toByteArray()

    private val TEST_MEETUP_ID = UUID.randomUUID()
    private val TEST_MEETUP_KEY = "MeetupKey".toByteArray()
    private val TEST_MEETUP_TYPE = GroupType.Meetup
    private val TEST_MEETUP_TAG1 = "GroupTag1"
    private val TEST_MEETUP_TAG2 = "GroupTag2"

    private val TEST_SELF_CERTIFICATE = "selfSignedMembershipCertificate"
    private val TEST_USER_CERTIFICATE = "userSignedMembershipCertificate"
    private val TEST_SERVER_CERTIFICATE = "serverSignedMembershipCertificate"

    private val TEST_GROUP_SETTINGS = GroupSettings(TEST_SIGNED_IN_USER_ID, TEST_GROUP_NAME)
    private val TEST_GROUP_SETTINGS_DATA = Json.encodeToString(GroupSettings.serializer(), TEST_GROUP_SETTINGS).toByteArray()

    private val TEST_LOC_CHAT_STRING = "locChatString"

    private val user1Id = UUID.randomUUID()
    private val user2Id = UUID.randomUUID()
    private val user3Id = UUID.randomUUID()

    private val user1ServerSigned = "user1Server"
    private val user2ServerSigned = "user2Server"
    private val user3ServerSigned = "user3Server"

    private val user1Membership = mockk<Membership>()
    private val user2Membership = mockk<Membership>()
    private val user3Membership = mockk<Membership>()

    private val meetupMemberships = setOf(user1Membership, user2Membership)
    private val teamMemberships = setOf(user1Membership, user2Membership, user3Membership)

    private val user1NotifyReceipt = NotificationRecipient(user1Id, user1ServerSigned, MessagePriority.Alert)
    private val user2NotifyReceipt = NotificationRecipient(user2Id, user2ServerSigned, MessagePriority.Alert)
    private val user3NotifyReceipt = NotificationRecipient(user3Id, user3ServerSigned, MessagePriority.Deferred)

    private val filteredNotify = listOf(user1NotifyReceipt, user2NotifyReceipt, user3NotifyReceipt)

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        meetupManager = MeetupManager(
            mockGroupManager,
            mockGroupStorageManager,
            mockCryptoManager,
            mockAuthManager,
            mockSignedInUserManager,
            mockBackend,
            mockChatStorageManager,
            mockLocalizationProvider
        )

        meetupManager.delegate = WeakReference(mockMeetupManagerDelegate)

        every { mockSignedInUserManager.signedInUser } returns mockSignedInUser
        every { mockSignedInUser.userId } returns TEST_SIGNED_IN_USER_ID
        every { mockSignedInUser.privateSigningKey } returns TEST_SIGNED_IN_PRIVATE_KEY

        coEvery { mockCryptoManager.encrypt(any(), any()) } answers { arg(0) as Ciphertext }
        coEvery { mockCryptoManager.decrypt(any(), any()) } answers { arg(0) as Ciphertext }

        every { mockTeam.groupId } returns TEST_TEAM_ID
        every { mockTeam.tag } returns TEST_TEAM_TAG1
        every { mockTeam.groupKey } returns TEST_TEAM_KEY
        every { mockTeam.owner } returns TEST_SIGNED_IN_USER_ID

        every { mockMeetup.groupId } returns TEST_MEETUP_ID
        every { mockMeetup.groupKey } returns TEST_MEETUP_KEY
        every { mockMeetup.tag } returns TEST_MEETUP_TAG1
        every { mockMeetup.teamId } returns TEST_TEAM_ID
        every { mockMeetup.owner } returns TEST_SIGNED_IN_USER_ID

        every { mockMembership.serverSignedMembershipCertificate } returns TEST_SERVER_CERTIFICATE

        every { user1Membership.userId } returns user1Id
        every { user1Membership.serverSignedMembershipCertificate } returns user1ServerSigned
        every { user2Membership.userId } returns user2Id
        every { user2Membership.serverSignedMembershipCertificate } returns user2ServerSigned
        every { user3Membership.userId } returns user3Id
        every { user3Membership.serverSignedMembershipCertificate } returns user3ServerSigned

        coEvery { mockGroupStorageManager.loadMembershipsOfGroup(TEST_MEETUP_ID) } returns meetupMemberships
        coEvery { mockGroupStorageManager.loadMembershipsOfGroup(TEST_TEAM_ID) } returns teamMemberships
    }

    @Test
    fun startLocationMonitoringForAllMeetups() = runBlockingTest {
        val meetup1 = mockk<Meetup>()
        val meetup2 = mockk<Meetup>()
        val meetup3 = mockk<Meetup>()
        val meetup4 = mockk<Meetup>()

        val meetupSet = setOf(meetup1, meetup2, meetup3, meetup4)

        coEvery { mockGroupStorageManager.loadMeetups() } returns meetupSet
        coEvery { mockGroupStorageManager.isMember(any(), any()) } returnsMany listOf(true, true, false, true)
    }

    @Test
    fun participationStatus_NOT_PARTICIPATING() = runBlockingTest {
        coEvery { mockGroupStorageManager.isMember(TEST_SIGNED_IN_USER_ID, TEST_MEETUP_ID) } returnsMany listOf(false, true, true)
        coEvery { mockGroupStorageManager.loadMembership(TEST_SIGNED_IN_USER_ID, TEST_MEETUP_ID) } returns mockMembership
        every { mockMembership.admin } returnsMany listOf(true, false)

        val resultParticipationStatus_NOT_PARTICIPATING = meetupManager.participationStatus(mockMeetup)
        val resultParticipationStatus_ADMIN = meetupManager.participationStatus(mockMeetup)
        val resultParticipationStatus_MEMBER = meetupManager.participationStatus(mockMeetup)

        Assertions.assertEquals(ParticipationStatus.NOT_PARTICIPATING, resultParticipationStatus_NOT_PARTICIPATING)
        Assertions.assertEquals(ParticipationStatus.ADMIN, resultParticipationStatus_ADMIN)
        Assertions.assertEquals(ParticipationStatus.MEMBER, resultParticipationStatus_MEMBER)
    }

    @Test
    fun createMeetup_MeetupAlreadyRunning() = runBlockingTest {
        coEvery { mockGroupStorageManager.meetupInTeam(TEST_TEAM_ID) } returns mockk<Meetup>()

        Assertions.assertThrows(MeetupManagerException.MeetupAlreadyRunning::class.java) {
            runBlocking { meetupManager.createMeetup(mockTeam, null, TEST_JOIN_MODE, TEST_PERMISSION_MODE) }
        }
    }

    @Nested
    inner class CreateMeetup {

        @Test
        fun success() = runBlockingTest {
            val groupIdSlot = slot<UUID>()
            val meetupSlot = slot<Meetup>()

            val TEST_GROUP_SETTINGS = GroupSettings(TEST_SIGNED_IN_USER_ID)
            val TEST_GROUP_SETTINGS_DATA = Json.encodeToString(GroupSettings.serializer(), TEST_GROUP_SETTINGS).toByteArray()

            val internalSettings = Meetup.InternalSettings(null)
            val internalSettingsData = Json.encodeToString(Meetup.InternalSettings.serializer(), internalSettings).toByteArray()

            val TEST_CREATE_GROUP_RESPONSE = CreateGroupResponse(mockUrl, TEST_SERVER_CERTIFICATE, TEST_MEETUP_TAG1)
            val TEST_PAIR = Pair(mockMembership, TEST_MEETUP_TAG1)

            every { mockLocalizationProvider.getString(LocalizationId(R.string.chat_metaInfo_self_meetup_created_title)) } returns TEST_LOC_CHAT_STRING
            coEvery { mockMeetupManagerDelegate.reload(mockTeam) } returns mockTeam
            coEvery { mockGroupStorageManager.meetupInTeam(TEST_TEAM_ID) } returns null
            every { mockCryptoManager.generateGroupKey() } returns TEST_MEETUP_KEY
            every {
                mockAuthManager.createUserSignedMembershipCertificate(
                    TEST_SIGNED_IN_USER_ID,
                    capture(groupIdSlot),
                    true,
                    TEST_SIGNED_IN_USER_ID,
                    TEST_SIGNED_IN_PRIVATE_KEY
                )
            } returns TEST_USER_CERTIFICATE

            coEvery {
                mockBackend.createGroup(
                    any(),
                    TEST_MEETUP_TYPE,
                    TEST_JOIN_MODE,
                    TEST_PERMISSION_MODE,
                    TEST_USER_CERTIFICATE,
                    TEST_GROUP_SETTINGS_DATA,
                    internalSettingsData,
                    ParentGroup(TEST_TEAM_ID, TEST_MEETUP_KEY)
                )
            } returns TEST_CREATE_GROUP_RESPONSE

            coEvery { mockGroupManager.addUserMember(capture(meetupSlot), true, TEST_SERVER_CERTIFICATE, emptySet()) } returns TEST_PAIR

            val resultMeetup = meetupManager.createMeetup(mockTeam, null, TEST_JOIN_MODE, TEST_PERMISSION_MODE)

            val EXPECTED_MEETUP = Meetup(
                groupIdSlot.captured,
                TEST_MEETUP_KEY,
                TEST_SIGNED_IN_USER_ID,
                TEST_JOIN_MODE,
                TEST_PERMISSION_MODE,
                TEST_MEETUP_TAG1,
                TEST_TEAM_ID,
                null
            )

            Assertions.assertEquals(EXPECTED_MEETUP, resultMeetup)

            coVerify(exactly = 1) {
                mockGroupStorageManager.storeMeetup(
                    EXPECTED_MEETUP,
                    Add(listOf(mockMembership))
                )
            }
            coVerify(exactly = 1) { mockGroupManager.sendGroupUpdateNotification(mockTeam, GroupUpdate.Action.CHILD_GROUP_CREATED) }
            coVerify(exactly = 1) { mockMeetupManagerDelegate.reload(mockTeam) }

            val chatEventSlot = slot<List<Message>>()
            coVerify(exactly = 1) { mockChatStorageManager.store(capture(chatEventSlot)) }

            val resultMessage = chatEventSlot.captured.first() as Message.MetaMessage
            assertMetaMessage(resultMessage)
        }

        @Test
        fun groupOutdated() = runBlockingTest {
            val groupIdSlot = slot<UUID>()

            val TEST_GROUP_SETTINGS = GroupSettings(TEST_SIGNED_IN_USER_ID)
            val TEST_GROUP_SETTINGS_DATA = Json.encodeToString(GroupSettings.serializer(), TEST_GROUP_SETTINGS).toByteArray()

            val internalSettings = Meetup.InternalSettings(null)
            val internalSettingsData = Json.encodeToString(Meetup.InternalSettings.serializer(), internalSettings).toByteArray()

            coEvery { mockMeetupManagerDelegate.reload(mockTeam) } returns mockTeam
            coEvery { mockGroupStorageManager.meetupInTeam(TEST_TEAM_ID) } returns null
            every { mockCryptoManager.generateGroupKey() } returns TEST_MEETUP_KEY
            every {
                mockAuthManager.createUserSignedMembershipCertificate(
                    TEST_SIGNED_IN_USER_ID,
                    capture(groupIdSlot),
                    true,
                    TEST_SIGNED_IN_USER_ID,
                    TEST_SIGNED_IN_PRIVATE_KEY
                )
            } returns TEST_USER_CERTIFICATE

            coEvery {
                mockBackend.createGroup(
                    any(),
                    TEST_MEETUP_TYPE,
                    TEST_JOIN_MODE,
                    TEST_PERMISSION_MODE,
                    TEST_USER_CERTIFICATE,
                    TEST_GROUP_SETTINGS_DATA,
                    internalSettingsData,
                    ParentGroup(TEST_TEAM_ID, TEST_MEETUP_KEY)
                )
            } throws BackendException.GroupOutdated

            Assertions.assertThrows(BackendException.GroupOutdated::class.java) {
                runBlockingTest { meetupManager.createMeetup(mockTeam, null, TEST_JOIN_MODE, TEST_PERMISSION_MODE) }
            }

            coVerify(exactly = 1) { mockMeetupManagerDelegate.reload(mockTeam) }
        }
    }

    @Test
    fun meetupStateOfTeam() = runBlockingTest {
        coEvery { mockGroupStorageManager.meetupInTeam(TEST_TEAM_ID) } returnsMany listOf(null, mockMeetup)
        coEvery { mockGroupStorageManager.isMember(TEST_SIGNED_IN_USER_ID, TEST_MEETUP_ID) } returnsMany listOf(true, false)

        val resultMeetupState_None = meetupManager.meetupState(mockTeam)
        val resultMeetupState_Participating = meetupManager.meetupState(mockTeam)
        val resultMeetupState_Invited = meetupManager.meetupState(mockTeam)

        Assertions.assertEquals(MeetupState.None, resultMeetupState_None)
        Assertions.assertEquals(MeetupState.Participating(mockMeetup), resultMeetupState_Participating)
        Assertions.assertEquals(MeetupState.Invited(mockMeetup), resultMeetupState_Invited)
    }

    @Test
    fun meetupStateOfMeetup() = runBlockingTest {
        coEvery { mockGroupStorageManager.isMember(TEST_SIGNED_IN_USER_ID, TEST_MEETUP_ID) } returnsMany listOf(true, false)

        val resultMeetupState_Participating = meetupManager.meetupState(mockMeetup)
        val resultMeetupState_Invited = meetupManager.meetupState(mockMeetup)

        Assertions.assertEquals(MeetupState.Participating(mockMeetup), resultMeetupState_Participating)
        Assertions.assertEquals(MeetupState.Invited(mockMeetup), resultMeetupState_Invited)
    }

    @Nested
    inner class AddOrReload {

        @Test
        fun hasMeetup() = runBlockingTest {
            coEvery { mockGroupStorageManager.loadMeetup(TEST_MEETUP_ID) } returns mockMeetup

            Assertions.assertThrows(Exception::class.java) {
                runBlocking { meetupManager.addOrReload(TEST_MEETUP_ID, TEST_TEAM_ID) }
            }
        }

        @Test
        fun noMeetup() = runBlockingTest {
            coEvery { mockGroupStorageManager.loadMeetup(TEST_MEETUP_ID) } returns null
            coEvery { mockGroupStorageManager.loadTeam(TEST_TEAM_ID) } returns mockTeam
            coEvery { mockGroupStorageManager.loadMembership(TEST_SIGNED_IN_USER_ID, TEST_TEAM_ID) } returns mockMembership

            val internalSettings = Team.InternalTeamSettings()
            val internalSettingsData = Json.encodeToString(Team.InternalTeamSettings.serializer(), internalSettings).toByteArray()

            val emptyCiphertext = emptyArray<Ciphertext>()
            val TEST_CHILDREN_IDS = emptyArray<GroupId>()

            val TEST_GROUP_INTERNALS_RESPONSE = GroupInternalsResponse(
                TEST_MEETUP_ID,
                TEST_TEAM_ID,
                GroupType.Meetup,
                TEST_JOIN_MODE,
                TEST_PERMISSION_MODE,
                mockUrl,
                TEST_GROUP_SETTINGS_DATA,
                internalSettingsData,
                emptyCiphertext,
                TEST_MEETUP_KEY,
                TEST_CHILDREN_IDS,
                TEST_MEETUP_TAG1
            )

            val EXPECTED_MEETUP = Meetup(
                TEST_MEETUP_ID,
                TEST_MEETUP_KEY,
                TEST_SIGNED_IN_USER_ID,
                TEST_GROUP_INTERNALS_RESPONSE.joinMode,
                TEST_GROUP_INTERNALS_RESPONSE.permissionMode,
                TEST_GROUP_INTERNALS_RESPONSE.groupTag,
                TEST_TEAM_ID,
                null
            )

            coEvery { mockBackend.getGroupInternals(TEST_MEETUP_ID, TEST_SERVER_CERTIFICATE, null) } returns TEST_GROUP_INTERNALS_RESPONSE

            meetupManager.addOrReload(TEST_MEETUP_ID, TEST_TEAM_ID)

            coVerify(exactly = 1) {
                mockGroupStorageManager.storeMeetup(
                    EXPECTED_MEETUP,
                    Replace(emptyList())
                )
            }
        }
    }

    @Nested
    inner class Reload {

        @Test
        fun isMember_Success() = runBlockingTest {
            coEvery { mockGroupStorageManager.isMember(TEST_SIGNED_IN_USER_ID, TEST_MEETUP_ID) } returns true
            coEvery { mockGroupStorageManager.loadMembership(TEST_SIGNED_IN_USER_ID, TEST_MEETUP_ID) } returns mockMembership
            coEvery { mockGroupStorageManager.loadMembershipsOfGroup(TEST_MEETUP_ID) } returns emptySet()

            val internalSettings = Team.InternalTeamSettings()
            val internalSettingsData = Json.encodeToString(Team.InternalTeamSettings.serializer(), internalSettings).toByteArray()

            val emptyCiphertext = emptyArray<Ciphertext>()
            val TEST_CHILDREN_IDS = emptyArray<GroupId>()

            val TEST_GROUP_INTERNALS_RESPONSE = GroupInternalsResponse(
                TEST_MEETUP_ID,
                TEST_TEAM_ID,
                GroupType.Meetup,
                TEST_JOIN_MODE,
                TEST_PERMISSION_MODE,
                mockUrl,
                TEST_GROUP_SETTINGS_DATA,
                internalSettingsData,
                emptyCiphertext,
                TEST_TEAM_KEY,
                TEST_CHILDREN_IDS,
                TEST_MEETUP_TAG2
            )

            val EXPECTED_MEETUP = Meetup(
                TEST_MEETUP_ID,
                TEST_MEETUP_KEY,
                TEST_SIGNED_IN_USER_ID,
                TEST_GROUP_INTERNALS_RESPONSE.joinMode,
                TEST_GROUP_INTERNALS_RESPONSE.permissionMode,
                TEST_GROUP_INTERNALS_RESPONSE.groupTag,
                TEST_TEAM_ID,
                null
            )

            coEvery {
                mockBackend.getGroupInternals(
                    TEST_MEETUP_ID,
                    TEST_SERVER_CERTIFICATE,
                    TEST_MEETUP_TAG1
                )
            } returns TEST_GROUP_INTERNALS_RESPONSE

            meetupManager.reload(mockMeetup)

            coVerify(exactly = 1) {
                mockGroupStorageManager.storeMeetup(
                    EXPECTED_MEETUP,
                    Replace(emptyList())
                )
            }
        }

        @Test
        fun notMember_Success() = runBlockingTest {
            coEvery { mockGroupStorageManager.isMember(TEST_SIGNED_IN_USER_ID, TEST_MEETUP_ID) } returns false
            coEvery { mockGroupStorageManager.loadMembership(TEST_SIGNED_IN_USER_ID, TEST_TEAM_ID) } returns mockMembership

            val internalSettings = Team.InternalTeamSettings()
            val internalSettingsData = Json.encodeToString(Team.InternalTeamSettings.serializer(), internalSettings).toByteArray()

            val emptyCiphertext = emptyArray<Ciphertext>()
            val TEST_CHILDREN_IDS = emptyArray<GroupId>()

            val TEST_GROUP_INTERNALS_RESPONSE = GroupInternalsResponse(
                TEST_MEETUP_ID,
                TEST_TEAM_ID,
                GroupType.Meetup,
                TEST_JOIN_MODE,
                TEST_PERMISSION_MODE,
                mockUrl,
                TEST_GROUP_SETTINGS_DATA,
                internalSettingsData,
                emptyCiphertext,
                TEST_TEAM_KEY,
                TEST_CHILDREN_IDS,
                TEST_MEETUP_TAG2
            )

            val EXPECTED_MEETUP = Meetup(
                TEST_MEETUP_ID,
                TEST_MEETUP_KEY,
                TEST_SIGNED_IN_USER_ID,
                TEST_GROUP_INTERNALS_RESPONSE.joinMode,
                TEST_GROUP_INTERNALS_RESPONSE.permissionMode,
                TEST_GROUP_INTERNALS_RESPONSE.groupTag,
                TEST_TEAM_ID,
                null
            )

            coEvery {
                mockBackend.getGroupInternals(
                    TEST_MEETUP_ID,
                    TEST_SERVER_CERTIFICATE,
                    TEST_MEETUP_TAG1
                )
            } returns TEST_GROUP_INTERNALS_RESPONSE
            coEvery { mockGroupStorageManager.loadMembershipsOfGroup(TEST_MEETUP_ID) } returns emptySet()

            meetupManager.reload(mockMeetup)

            coVerify(exactly = 1) {
                mockGroupStorageManager.storeMeetup(
                    EXPECTED_MEETUP,
                    Replace(emptyList())
                )
            }
        }

        @Test
        fun notModified() = runBlockingTest {
            coEvery { mockGroupStorageManager.isMember(TEST_SIGNED_IN_USER_ID, TEST_MEETUP_ID) } returns false
            coEvery { mockGroupStorageManager.loadMembership(TEST_SIGNED_IN_USER_ID, TEST_TEAM_ID) } returns mockMembership

            val internalSettings = Team.InternalTeamSettings()
            val internalSettingsData = Json.encodeToString(Team.InternalTeamSettings.serializer(), internalSettings).toByteArray()

            val emptyCiphertext = emptyArray<Ciphertext>()
            val TEST_CHILDREN_IDS = emptyArray<GroupId>()

            val TEST_GROUP_INTERNALS_RESPONSE = GroupInternalsResponse(
                TEST_MEETUP_ID,
                TEST_TEAM_ID,
                GroupType.Meetup,
                TEST_JOIN_MODE,
                TEST_PERMISSION_MODE,
                mockUrl,
                TEST_GROUP_SETTINGS_DATA,
                internalSettingsData,
                emptyCiphertext,
                TEST_TEAM_KEY,
                TEST_CHILDREN_IDS,
                TEST_MEETUP_TAG2
            )

            val EXPECTED_MEETUP = Meetup(
                TEST_MEETUP_ID,
                TEST_MEETUP_KEY,
                TEST_SIGNED_IN_USER_ID,
                TEST_GROUP_INTERNALS_RESPONSE.joinMode,
                TEST_GROUP_INTERNALS_RESPONSE.permissionMode,
                TEST_GROUP_INTERNALS_RESPONSE.groupTag,
                TEST_TEAM_ID,
                null
            )

            coEvery {
                mockBackend.getGroupInternals(
                    TEST_MEETUP_ID,
                    TEST_SERVER_CERTIFICATE,
                    TEST_MEETUP_TAG1
                )
            } throws BackendException.NotModified

            meetupManager.reload(mockMeetup)

            coVerify(inverse = true) {
                mockGroupStorageManager.storeMeetup(
                    EXPECTED_MEETUP,
                    null
                )
            }
        }

        @Test
        fun notFound() = runBlockingTest {
            coEvery { mockGroupStorageManager.isMember(TEST_SIGNED_IN_USER_ID, TEST_MEETUP_ID) } returns false
            coEvery { mockGroupStorageManager.loadMembership(TEST_SIGNED_IN_USER_ID, TEST_TEAM_ID) } returns mockMembership

            val internalSettings = Team.InternalTeamSettings()
            val internalSettingsData = Json.encodeToString(Team.InternalTeamSettings.serializer(), internalSettings).toByteArray()

            val emptyCiphertext = emptyArray<Ciphertext>()
            val TEST_CHILDREN_IDS = emptyArray<GroupId>()

            val TEST_GROUP_INTERNALS_RESPONSE = GroupInternalsResponse(
                TEST_MEETUP_ID,
                TEST_TEAM_ID,
                GroupType.Meetup,
                TEST_JOIN_MODE,
                TEST_PERMISSION_MODE,
                mockUrl,
                TEST_GROUP_SETTINGS_DATA,
                internalSettingsData,
                emptyCiphertext,
                TEST_TEAM_KEY,
                TEST_CHILDREN_IDS,
                TEST_MEETUP_TAG2
            )

            val EXPECTED_MEETUP = Meetup(
                TEST_MEETUP_ID,
                TEST_MEETUP_KEY,
                TEST_SIGNED_IN_USER_ID,
                TEST_GROUP_INTERNALS_RESPONSE.joinMode,
                TEST_GROUP_INTERNALS_RESPONSE.permissionMode,
                TEST_GROUP_INTERNALS_RESPONSE.groupTag,
                TEST_TEAM_ID,
                null
            )

            coEvery {
                mockBackend.getGroupInternals(
                    TEST_MEETUP_ID,
                    TEST_SERVER_CERTIFICATE,
                    TEST_MEETUP_TAG1
                )
            } throws BackendException.NotFound

            Assertions.assertThrows(BackendException.NotFound::class.java)
            { runBlocking { meetupManager.reload(mockMeetup) } }

            coVerify(inverse = true) {
                mockGroupStorageManager.storeMeetup(
                    EXPECTED_MEETUP,
                    null
                )
            }
            coVerify(exactly = 1) { mockGroupStorageManager.removeMeetup(mockMeetup) }
        }

        @Test
        fun unauthorized() = runBlockingTest {
            coEvery { mockGroupStorageManager.isMember(TEST_SIGNED_IN_USER_ID, TEST_MEETUP_ID) } returns false
            coEvery { mockGroupStorageManager.loadMembership(TEST_SIGNED_IN_USER_ID, TEST_TEAM_ID) } returns mockMembership

            val internalSettings = Team.InternalTeamSettings()
            val internalSettingsData = Json.encodeToString(Team.InternalTeamSettings.serializer(), internalSettings).toByteArray()

            val emptyCiphertext = emptyArray<Ciphertext>()
            val TEST_CHILDREN_IDS = emptyArray<GroupId>()

            val TEST_GROUP_INTERNALS_RESPONSE = GroupInternalsResponse(
                TEST_MEETUP_ID,
                TEST_TEAM_ID,
                GroupType.Meetup,
                TEST_JOIN_MODE,
                TEST_PERMISSION_MODE,
                mockUrl,
                TEST_GROUP_SETTINGS_DATA,
                internalSettingsData,
                emptyCiphertext,
                TEST_TEAM_KEY,
                TEST_CHILDREN_IDS,
                TEST_MEETUP_TAG2
            )

            val EXPECTED_MEETUP = Meetup(
                TEST_MEETUP_ID,
                TEST_MEETUP_KEY,
                TEST_SIGNED_IN_USER_ID,
                TEST_GROUP_INTERNALS_RESPONSE.joinMode,
                TEST_GROUP_INTERNALS_RESPONSE.permissionMode,
                TEST_GROUP_INTERNALS_RESPONSE.groupTag,
                TEST_TEAM_ID,
                null
            )

            coEvery {
                mockBackend.getGroupInternals(
                    TEST_MEETUP_ID,
                    TEST_SERVER_CERTIFICATE,
                    TEST_MEETUP_TAG1
                )
            } throws BackendException.Unauthorized

            Assertions.assertThrows(BackendException.Unauthorized::class.java)
            { runBlocking { meetupManager.reload(mockMeetup) } }

            coVerify(inverse = true) {
                mockGroupStorageManager.storeMeetup(
                    EXPECTED_MEETUP,
                    null
                )
            }
            coVerify(exactly = 1) { mockGroupStorageManager.removeMeetup(mockMeetup) }
        }
    }

    @Nested
    inner class Join {

        @Test
        fun success() = runBlockingTest {
            every { mockLocalizationProvider.getString(LocalizationId(R.string.chat_metaInfo_self_meetup_joined_title)) } returns TEST_LOC_CHAT_STRING
            every {
                mockAuthManager.createUserSignedMembershipCertificate(
                    TEST_SIGNED_IN_USER_ID,
                    TEST_MEETUP_ID,
                    false,
                    TEST_SIGNED_IN_USER_ID,
                    TEST_SIGNED_IN_PRIVATE_KEY
                )
            } returns TEST_SELF_CERTIFICATE
            coEvery { mockBackend.joinGroup(TEST_MEETUP_ID, TEST_MEETUP_TAG1, TEST_SELF_CERTIFICATE) }
                .returns(JoinGroupResponse(TEST_SERVER_CERTIFICATE))
            coEvery { mockGroupManager.addUserMember(mockMeetup, false, TEST_SERVER_CERTIFICATE, filteredNotify.toSet()) }
                .returns(Pair(mockMembership, TEST_MEETUP_TAG2))

            meetupManager.join(mockMeetup)

            verify(exactly = 1) { mockMeetup.tag = TEST_MEETUP_TAG2 }
            coVerify(exactly = 1) { mockGroupStorageManager.storeMeetup(mockMeetup, Add(listOf(mockMembership))) }
            coVerify(exactly = 1) { mockGroupManager.addUserMember(mockMeetup, false, TEST_SERVER_CERTIFICATE, filteredNotify.toSet()) }

            val chatEventSlot = slot<List<Message>>()
            coVerify(exactly = 1) { mockChatStorageManager.store(capture(chatEventSlot)) }

            val resultMessage = chatEventSlot.captured.first() as Message.MetaMessage
            assertMetaMessage(resultMessage)
        }

        @Test
        fun groupOutdated() = runBlockingTest {
            every {
                mockAuthManager.createUserSignedMembershipCertificate(
                    TEST_SIGNED_IN_USER_ID,
                    TEST_MEETUP_ID,
                    false,
                    TEST_SIGNED_IN_USER_ID,
                    TEST_SIGNED_IN_PRIVATE_KEY
                )
            } returns TEST_SELF_CERTIFICATE
            coEvery {
                mockBackend.joinGroup(
                    TEST_MEETUP_ID,
                    TEST_MEETUP_TAG1,
                    TEST_SELF_CERTIFICATE,
                    null,
                    null
                )
            } throws BackendException.GroupOutdated

            coEvery { mockGroupManager.addUserMember(mockMeetup, false, TEST_SERVER_CERTIFICATE, emptySet()) } returns Pair(
                mockMembership,
                TEST_MEETUP_TAG2
            )
            coEvery { mockGroupStorageManager.loadMembershipsOfGroup(TEST_MEETUP_ID) } returns emptySet()

            coEvery { mockGroupStorageManager.teamOfMeetup(mockMeetup) } returns mockTeam
            coEvery { mockMeetupManagerDelegate.reload(mockTeam) } returns (mockTeam)

            Assertions.assertThrows(BackendException.GroupOutdated::class.java) {
                runBlockingTest { meetupManager.join(mockMeetup) }
            }

            coVerify(exactly = 1) { mockMeetupManagerDelegate.reload(mockTeam) }
        }
    }

    @Nested
    inner class Leave {

        @Test
        fun success() = runBlockingTest {
            every { mockLocalizationProvider.getString(LocalizationId(R.string.chat_metaInfo_self_meetup_left_title)) } returns TEST_LOC_CHAT_STRING
            coEvery { mockGroupManager.leave(mockMeetup, filteredNotify.toSet()) } returns TEST_MEETUP_TAG2
            coEvery { mockGroupStorageManager.teamOfMeetup(mockMeetup) } returns mockTeam
            coEvery { mockGroupStorageManager.loadMembership(TEST_SIGNED_IN_USER_ID, TEST_MEETUP_ID) } returns mockMembership

            meetupManager.leave(mockMeetup)

            coVerify(exactly = 1) { mockGroupStorageManager.storeMeetup(mockMeetup, Remove(listOf(mockMembership))) }

            val chatEventSlot = slot<List<Message>>()
            coVerify(exactly = 1) { mockChatStorageManager.store(capture(chatEventSlot)) }

            val resultMessage = chatEventSlot.captured.first() as Message.MetaMessage
            assertMetaMessage(resultMessage)
        }

        @Test
        fun groupOutdated() = runBlockingTest {
            coEvery { mockGroupManager.leave(mockMeetup, filteredNotify.toSet()) } throws BackendException.GroupOutdated
            coEvery { mockGroupStorageManager.teamOfMeetup(mockMeetup) } returns mockTeam
            coEvery { mockMeetupManagerDelegate.reload(mockTeam) } returns (mockTeam)

            Assertions.assertThrows(BackendException.GroupOutdated::class.java) {
                runBlocking { meetupManager.leave(mockMeetup) }
            }

            coVerify(exactly = 1) { mockMeetupManagerDelegate.reload(mockTeam) }
        }
    }

    @Nested
    inner class Delete {

        @Test
        fun success() = runBlockingTest {
            every { mockLocalizationProvider.getString(LocalizationId(R.string.chat_metaInfo_self_meetup_deleted_title)) } returns TEST_LOC_CHAT_STRING

            coEvery { mockGroupStorageManager.loadMembership(TEST_SIGNED_IN_USER_ID, TEST_MEETUP_ID) } returns mockMembership
            every { mockMembership.admin } returns true
            every { mockMembership.serverSignedMembershipCertificate } returns TEST_SERVER_CERTIFICATE
            coEvery { mockGroupStorageManager.teamOfMeetup(mockMeetup) } returns mockTeam
            coEvery { mockMeetupManagerDelegate.reload(mockTeam) } returns mockTeam

            meetupManager.delete(mockMeetup)

            coVerify(exactly = 1) { mockBackend.deleteGroup(TEST_MEETUP_ID, TEST_SERVER_CERTIFICATE, TEST_MEETUP_TAG1, filteredNotify) }
            coVerify(exactly = 1) { mockGroupStorageManager.removeMeetup(mockMeetup) }
            coVerify(exactly = 1) { mockGroupManager.sendGroupUpdateNotification(mockTeam, GroupUpdate.Action.CHILD_GROUP_DELETED) }
            coVerify(exactly = 1) { mockMeetupManagerDelegate.reload(mockTeam) }

            val chatEventSlot = slot<List<Message>>()
            coVerify(exactly = 1) { mockChatStorageManager.store(capture(chatEventSlot)) }

            val resultMessage = chatEventSlot.captured.first() as Message.MetaMessage
            assertMetaMessage(resultMessage)
        }

        @Test
        fun delete_PermissionDenied() = runBlockingTest {
            coEvery { mockGroupStorageManager.loadMembership(TEST_SIGNED_IN_USER_ID, TEST_MEETUP_ID) } returns mockMembership
            every { mockMembership.admin } returns false

            Assertions.assertThrows(MeetupManagerException.PermissionDenied::class.java) {
                runBlocking { meetupManager.delete(mockMeetup) }
            }
        }

        @Test
        fun groupOutdated() = runBlockingTest {
            val emptyNotificationRecipient = emptyList<NotificationRecipient>()

            coEvery { mockGroupStorageManager.loadMembership(TEST_SIGNED_IN_USER_ID, TEST_MEETUP_ID) } returns mockMembership
            every { mockMembership.admin } returns true
            every { mockMembership.serverSignedMembershipCertificate } returns TEST_SERVER_CERTIFICATE
            coEvery { mockGroupManager.notificationRecipients(TEST_MEETUP_ID, mockk()) } returns emptyNotificationRecipient
            coEvery { mockGroupStorageManager.teamOfMeetup(mockMeetup) } returns mockTeam
            coEvery { mockMeetupManagerDelegate.reload(mockTeam) } returns mockTeam
            coEvery { mockBackend.deleteGroup(TEST_MEETUP_ID, TEST_SERVER_CERTIFICATE, TEST_MEETUP_TAG1, filteredNotify) }
                .throws(BackendException.GroupOutdated)

            Assertions.assertThrows(BackendException.GroupOutdated::class.java) {
                runBlocking { meetupManager.delete(mockMeetup) }
            }

            coVerify(exactly = 1) { mockBackend.deleteGroup(TEST_MEETUP_ID, TEST_SERVER_CERTIFICATE, TEST_MEETUP_TAG1, filteredNotify) }
            coVerify(exactly = 1) { mockMeetupManagerDelegate.reload(mockTeam) }
        }
    }

    @Nested
    inner class deleteGroupMember {

        @Test
        fun success() = runBlockingTest {
            val membershipId = UUID.randomUUID()
            val mockUserMembership = mockk<Membership> { every { userId } returns membershipId }

            every { mockLocalizationProvider.getString(LocalizationId(R.string.chat_metaInfo_self_meetup_deleteGroupMember_title_unknown)) } returns TEST_LOC_CHAT_STRING
            coEvery { mockGroupStorageManager.loadMembership(TEST_SIGNED_IN_USER_ID, TEST_MEETUP_ID) } returns mockMembership
            every { mockMembership.serverSignedMembershipCertificate } returns TEST_SERVER_CERTIFICATE
            coEvery {
                mockGroupManager.deleteGroupMember(mockUserMembership, mockMeetup, TEST_SERVER_CERTIFICATE, filteredNotify.toSet())
            } returns TEST_MEETUP_TAG2

            meetupManager.deleteGroupMember(mockUserMembership, mockMeetup)

            coVerify(exactly = 1) { mockGroupStorageManager.storeMeetup(mockMeetup, Remove(listOf(mockUserMembership))) }

            val chatEventSlot = slot<List<Message>>()
            coVerify(exactly = 1) { mockChatStorageManager.store(capture(chatEventSlot)) }
            confirmVerified(mockChatStorageManager)

            val resultMessage = chatEventSlot.captured.first() as Message.MetaMessage
            assertMetaMessage(resultMessage)
        }

        @Test
        fun groupOutdated() = runBlockingTest {
            val membershipId = UUID.randomUUID()
            val mockUserMembership = mockk<Membership> { every { userId } returns membershipId }

            coEvery { mockGroupStorageManager.loadMembership(TEST_SIGNED_IN_USER_ID, TEST_MEETUP_ID) } returns mockMembership
            every { mockMembership.serverSignedMembershipCertificate } returns TEST_SERVER_CERTIFICATE
            coEvery { mockGroupManager.deleteGroupMember(mockUserMembership, mockMeetup, TEST_SERVER_CERTIFICATE, filteredNotify.toSet()) }
                .throws(BackendException.GroupOutdated)
            coEvery { mockGroupStorageManager.teamOfMeetup(mockMeetup) } returns mockTeam
            coEvery { mockMeetupManagerDelegate.reload(mockTeam) } returns mockTeam

            Assertions.assertThrows(BackendException.GroupOutdated::class.java) {
                runBlocking { meetupManager.deleteGroupMember(mockUserMembership, mockMeetup) }
            }

            coVerify(exactly = 1) { mockMeetupManagerDelegate.reload(mockTeam) }
        }
    }

    @Nested
    inner class SetMeetingPoint {

        @Test
        fun success() = runBlockingTest {
            val TEST_LAT = Random().nextDouble()
            val TEST_LNG = Random().nextDouble()
            val mockLatLng = Coordinates(TEST_LAT, TEST_LNG)

            val cipherSlot = slot<Ciphertext>()

            val dateThreshold = Date()

            coEvery { mockGroupStorageManager.loadMembership(TEST_SIGNED_IN_USER_ID, TEST_MEETUP_ID) } returns mockMembership
            every { mockMembership.serverSignedMembershipCertificate } returns TEST_SERVER_CERTIFICATE
            coEvery {
                mockBackend.updateGroupInternalSettings(
                    TEST_MEETUP_ID,
                    capture(cipherSlot),
                    TEST_SERVER_CERTIFICATE,
                    TEST_MEETUP_TAG1,
                    filteredNotify.toList()
                )
            }.returns(UpdatedETagResponse(TEST_MEETUP_TAG2))

            meetupManager.setMeetingPoint(mockLatLng, mockMeetup)

            val resultLocation = Json.decodeFromString(Meetup.InternalSettings.serializer(), String(cipherSlot.captured))
            Assertions.assertEquals(TEST_LAT, resultLocation.location?.latitude)
            Assertions.assertEquals(TEST_LNG, resultLocation.location?.longitude)
            Assertions.assertEquals(0.0, resultLocation.location?.altitude)
            Assertions.assertEquals(0.0f, resultLocation.location?.horizontalAccuracy)
            Assertions.assertEquals(0.0f, resultLocation.location?.verticalAccuracy)
            Assertions.assertTrue(dateThreshold <= resultLocation.location!!.timestamp)
            Assertions.assertTrue(Date() >= resultLocation.location!!.timestamp)

            coVerify(exactly = 1) {
                mockGroupStorageManager.storeMeetup(mockMeetup, null)
            }
        }

        @Test
        fun groupOutdated() = runBlockingTest {
            val TEST_LAT = Random().nextDouble()
            val TEST_LNG = Random().nextDouble()
            val mockLatLng = Coordinates(TEST_LAT, TEST_LNG)

            val cipherSlot = slot<Ciphertext>()

            val dateThreshold = Date()

            coEvery { mockGroupStorageManager.loadMembership(TEST_SIGNED_IN_USER_ID, TEST_MEETUP_ID) } returns mockMembership
            every { mockMembership.serverSignedMembershipCertificate } returns TEST_SERVER_CERTIFICATE
            coEvery {
                mockBackend.updateGroupInternalSettings(
                    TEST_MEETUP_ID,
                    capture(cipherSlot),
                    TEST_SERVER_CERTIFICATE,
                    TEST_MEETUP_TAG1,
                    filteredNotify.toList()
                )
            }.throws(BackendException.GroupOutdated)
            coEvery { mockGroupStorageManager.teamOfMeetup(mockMeetup) } returns mockTeam
            coEvery { mockMeetupManagerDelegate.reload(mockTeam) } returns mockTeam

            Assertions.assertThrows(BackendException.GroupOutdated::class.java) {
                runBlocking { meetupManager.setMeetingPoint(mockLatLng, mockMeetup) }
            }

            val resultLocation = Json.decodeFromString(Meetup.InternalSettings.serializer(), String(cipherSlot.captured))
            Assertions.assertEquals(TEST_LAT, resultLocation.location?.latitude)
            Assertions.assertEquals(TEST_LNG, resultLocation.location?.longitude)
            Assertions.assertEquals(0.0, resultLocation.location?.altitude)
            Assertions.assertEquals(0.0f, resultLocation.location?.horizontalAccuracy)
            Assertions.assertEquals(0.0f, resultLocation.location?.verticalAccuracy)
            Assertions.assertTrue(dateThreshold <= resultLocation.location!!.timestamp)
            Assertions.assertTrue(Date() >= resultLocation.location!!.timestamp)

            coVerify(exactly = 1) { mockMeetupManagerDelegate.reload(mockTeam) }
        }
    }

    private fun assertMetaMessage(resultMessage: Message.MetaMessage) {
        Assertions.assertEquals(TEST_TEAM_ID, resultMessage.groupId)
        Assertions.assertEquals(TEST_SIGNED_IN_USER_ID, resultMessage.senderId)
        Assertions.assertEquals(TEST_LOC_CHAT_STRING, resultMessage.text)
        Assertions.assertEquals(true, resultMessage.read)
        Assertions.assertEquals(MessageStatus.Success, resultMessage.status)

    }
}
