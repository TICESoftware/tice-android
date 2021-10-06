package tice.managers.group

import androidx.lifecycle.MutableLiveData
import com.google.android.gms.maps.model.LatLng
import com.ticeapp.TICE.R
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.serialization.json.Json
import org.junit.Assert
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tice.backend.BackendType
import tice.crypto.AuthManagerType
import tice.crypto.CryptoManagerType
import tice.exceptions.BackendException
import tice.exceptions.TeamManagerException
import tice.managers.LocationManagerType
import tice.managers.SignedInUserManagerType
import tice.managers.UserManagerType
import tice.managers.storageManagers.ChatStorageManagerType
import tice.managers.storageManagers.GroupStorageManagerType
import tice.managers.storageManagers.LocationSharingStorageManagerType
import tice.managers.storageManagers.MembershipsDiff
import tice.managers.storageManagers.MembershipsDiff.Add
import tice.managers.storageManagers.MembershipsDiff.Replace
import tice.models.*
import tice.models.chat.Message
import tice.models.chat.MessageStatus
import tice.models.messaging.*
import tice.models.responses.*
import tice.utility.dataFromBase64
import tice.utility.provider.LocalizationProviderType
import tice.utility.serializer.MembershipSerializer
import tice.utility.toBase64String
import java.lang.ref.WeakReference
import java.net.URL
import java.util.*

internal class TeamManagerTest {

    private lateinit var teamManager: TeamManager

    private val mockLocationManager: LocationManagerType = mockk(relaxUnitFun = true)
    private val mockLocationSharingStorageManager: LocationSharingStorageManagerType = mockk(relaxUnitFun = true)
    private val mockGroupManager: GroupManagerType = mockk(relaxUnitFun = true)
    private val mockMeetupManager: MeetupManagerType = mockk(relaxUnitFun = true)
    private val mockGroupStorageManager: GroupStorageManagerType = mockk(relaxUnitFun = true)
    private val mockSignedInUserManager: SignedInUserManagerType = mockk(relaxUnitFun = true)
    private val mockUserManager: UserManagerType = mockk(relaxUnitFun = true)
    private val mockCryptoManager: CryptoManagerType = mockk(relaxUnitFun = true)
    private val mockAuthManager: AuthManagerType = mockk(relaxUnitFun = true)
    private val mockBackend: BackendType = mockk(relaxUnitFun = true)
    private val mockChatStorageManager: ChatStorageManagerType = mockk(relaxUnitFun = true)
    private val mockLocalizationProvider: LocalizationProviderType = mockk(relaxUnitFun = true)

    private val mockSignedInUser: SignedInUser = mockk(relaxUnitFun = true)
    private val mockMembership: Membership = mockk()
    private val mockTeam: Team = mockk(relaxUnitFun = true)

    private val TEST_SIGNED_IN_USER_ID = UUID.randomUUID()
    private val TEST_SIGNED_IN_PRIVATE_KEY = "PrivateKey".encodeToByteArray()
    private val TEST_JOIN_MODE = JoinMode.Open
    private val TEST_PERMISSION_MODE = PermissionMode.Everyone
    private val TEST_GROUP_NAME = "GroupName"

    private val TEST_LOCATION: Location = Location(1.0, 1.0, 1.0, 1.0f, 1.0f, Date())
    private val TEST_GROUP_ID = UUID.randomUUID()
    private val TEST_GROUP_KEY = "groupKey".toByteArray()
    private val TEST_URL = URL("https://tice.app/group/groupId")
    private val TEST_GROUP_TYPE = GroupType.Team
    private val TEST_GROUP_TAG1 = "GroupTag1"
    private val TEST_GROUP_TAG2 = "GroupTag2"

    private val TEST_SELF_CERTIFICATE = "selfSignedMembershipCertificate"
    private val TEST_SERVER_CERTIFICATE = "serverSignedMembershipCertificate"
    private val TEST_ADMIN_CERTIFICATE = "selfSignedAdminCertificate"

    private val TEST_GROUP_SETTINGS = GroupSettings(TEST_SIGNED_IN_USER_ID, TEST_GROUP_NAME)
    private val TEST_GROUP_SETTINGS_DATA = Json.encodeToString(GroupSettings.serializer(), TEST_GROUP_SETTINGS).toByteArray()
    private val TEST_LOC_CHAT_STRING = "locChatString"

    private val user1Id = UUID.randomUUID()
    private val user2Id = UUID.randomUUID()

    private val user1ServerSigned = "user1Server"
    private val user2ServerSigned = "user2Server"

    private val user1Membership = mockk<Membership>()
    private val user2Membership = mockk<Membership>()

    private val user1NotifyReceipt = NotificationRecipient(user1Id, user1ServerSigned, MessagePriority.Alert)
    private val user2NotifyReceipt = NotificationRecipient(user2Id, user2ServerSigned, MessagePriority.Alert)

    private val teamNotification = listOf(user1NotifyReceipt, user2NotifyReceipt)

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        teamManager = TeamManager(
            mockLocationManager,
            mockGroupManager,
            mockMeetupManager,
            mockGroupStorageManager,
            mockSignedInUserManager,
            mockUserManager,
            mockCryptoManager,
            mockAuthManager,
            mockBackend,
            mockChatStorageManager,
            mockLocalizationProvider,
            mockLocationSharingStorageManager
        )

        every { mockSignedInUserManager.signedInUser } returns mockSignedInUser
        every { mockSignedInUser.userId } returns TEST_SIGNED_IN_USER_ID
        every { mockSignedInUser.privateSigningKey } returns TEST_SIGNED_IN_PRIVATE_KEY
        every { mockMembership.serverSignedMembershipCertificate } returns TEST_SERVER_CERTIFICATE
        every { mockTeam.meetingPoint } returns TEST_LOCATION
        every { mockTeam.groupId } returns TEST_GROUP_ID
        every { mockTeam.tag } returns TEST_GROUP_TAG1
        every { mockTeam.groupKey } returns TEST_GROUP_KEY
        every { mockTeam.owner } returns TEST_SIGNED_IN_USER_ID
        every { mockTeam.joinMode } returns TEST_JOIN_MODE
        every { mockTeam.permissionMode } returns TEST_PERMISSION_MODE
        every { mockTeam.url } returns TEST_URL
        every { mockTeam.name } returns TEST_GROUP_NAME
        every { mockTeam.meetupId } returns null
        every { mockTeam.meetupId } returns null
        every { mockCryptoManager.encrypt(any(), any()) } answers { arg(0) as Ciphertext }
        every { mockCryptoManager.decrypt(any(), any()) } answers { arg(0) as Ciphertext }
        coEvery { mockGroupStorageManager.loadMembership(TEST_SIGNED_IN_USER_ID, TEST_GROUP_ID) } returns mockMembership

        every { user1Membership.userId } returns user1Id
        every { user1Membership.serverSignedMembershipCertificate } returns user1ServerSigned
        every { user2Membership.userId } returns user2Id
        every { user2Membership.serverSignedMembershipCertificate } returns user2ServerSigned

        coEvery { mockGroupManager.notificationRecipients(TEST_GROUP_ID, MessagePriority.Alert) } returns teamNotification
    }

    @Test
    fun initDelegate() {
        val slot = slot<WeakReference<MeetupManagerDelegate>>()

        teamManager.registerForDelegate()

        verify(exactly = 1) { mockMeetupManager.delegate = capture(slot) }

        Assertions.assertEquals(teamManager, slot.captured.get())
    }

    @Test
    fun createTeam_Success() = runBlockingTest {
        val internalSettings = Team.InternalTeamSettings(TEST_LOCATION)
        val internalSettingsData = Json.encodeToString(Team.InternalTeamSettings.serializer(), internalSettings).toByteArray()

        lateinit var groupId: UUID
        lateinit var EXPECTED_TEAM: Team

        val TEST_ENABLE_LOCATION_SHARING = true
        val TEST_CREATE_GROUP_RESPONSE = CreateGroupResponse(TEST_URL, TEST_SERVER_CERTIFICATE, TEST_GROUP_TAG1)
        val TEST_PAIR = Pair(mockMembership, TEST_GROUP_TAG2)

        val locationSharingStateSlot = slot<LocationSharingState>()

        coEvery { mockLocationSharingStorageManager.storeLocationSharingState(capture(locationSharingStateSlot)) } returns Unit
        every { mockCryptoManager.generateGroupKey() } returns TEST_GROUP_KEY
        every { mockLocalizationProvider.getString(LocalizationId(R.string.chat_metaInfo_self_group_created_title)) } returns TEST_LOC_CHAT_STRING
        every { mockAuthManager.createUserSignedMembershipCertificate(any(), any(), any(), any(), any()) } answers {
            Assertions.assertEquals(TEST_SIGNED_IN_USER_ID, arg(0) as UUID)
            groupId = arg(1) as UUID
            Assertions.assertTrue(arg(2) as Boolean)
            Assertions.assertEquals(TEST_SIGNED_IN_USER_ID, arg(3) as UUID)
            Assertions.assertEquals(TEST_SIGNED_IN_PRIVATE_KEY, arg(4) as PrivateKey)

            coEvery {
                mockBackend.createGroup(
                    groupId,
                    TEST_GROUP_TYPE,
                    TEST_JOIN_MODE,
                    TEST_PERMISSION_MODE,
                    TEST_ADMIN_CERTIFICATE,
                    TEST_GROUP_SETTINGS_DATA,
                    internalSettingsData,
                    null
                )
            } returns TEST_CREATE_GROUP_RESPONSE

            EXPECTED_TEAM = Team(
                groupId,
                TEST_GROUP_KEY,
                TEST_SIGNED_IN_USER_ID,
                TEST_JOIN_MODE,
                TEST_PERMISSION_MODE,
                TEST_GROUP_TAG1,
                TEST_URL,
                TEST_GROUP_NAME,
                null,
                TEST_LOCATION
            )

            coEvery { mockGroupManager.addUserMember(EXPECTED_TEAM, true, TEST_SERVER_CERTIFICATE, listOf()) } returns TEST_PAIR

            TEST_ADMIN_CERTIFICATE
        }

        val resultTeam = teamManager.createTeam(
            TEST_JOIN_MODE,
            TEST_PERMISSION_MODE,
            TEST_GROUP_NAME,
            TEST_ENABLE_LOCATION_SHARING,
            TEST_LOCATION
        )

        EXPECTED_TEAM.tag = TEST_GROUP_TAG2

        Assertions.assertEquals(EXPECTED_TEAM, resultTeam)

        verify(exactly = 1) { mockCryptoManager.encrypt(TEST_GROUP_SETTINGS_DATA, TEST_GROUP_KEY) }
        coVerify(exactly = 1) { mockGroupStorageManager.storeTeam(EXPECTED_TEAM, Add(listOf(mockMembership))) }
        coVerify(exactly = 1) { mockLocationSharingStorageManager.storeLocationSharingState(any()) }

        val chatEventSlot = slot<List<Message>>()
        coVerify(exactly = 1) { mockChatStorageManager.store(capture(chatEventSlot)) }

        val resultMessage = chatEventSlot.captured.first() as Message.MetaMessage

        Assertions.assertEquals(groupId, resultMessage.groupId)
        Assertions.assertEquals(TEST_SIGNED_IN_USER_ID, resultMessage.senderId)
        Assertions.assertEquals(TEST_LOC_CHAT_STRING, resultMessage.text)
        Assertions.assertEquals(true, resultMessage.read)
        Assertions.assertEquals(MessageStatus.Success, resultMessage.status)

        val capturedLocationSharingstate = locationSharingStateSlot.captured

        Assertions.assertEquals(TEST_SIGNED_IN_USER_ID, capturedLocationSharingstate.userId)
        Assertions.assertEquals(groupId, capturedLocationSharingstate.groupId)
        Assertions.assertEquals(TEST_ENABLE_LOCATION_SHARING, capturedLocationSharingstate.sharingEnabled)
    }

    @Test
    fun getTeam() = runBlockingTest {
        val TEST_LIVE_DATA = MutableLiveData<Team>()
        every { mockGroupStorageManager.getTeamObservable(TEST_GROUP_ID) } returns TEST_LIVE_DATA

        val resultLiveData = teamManager.getTeamLiveData(TEST_GROUP_ID)

        verify(exactly = 1) { mockGroupStorageManager.getTeamObservable(TEST_GROUP_ID) }
        Assertions.assertEquals(TEST_LIVE_DATA, resultLiveData)
    }

    @Nested
    inner class Reload {

        @Test
        fun reloadTeam_WithoutMembership() = runBlockingTest {
            val internalSettings = Team.InternalTeamSettings()
            val internalSettingsData = Json.encodeToString(Team.InternalTeamSettings.serializer(), internalSettings).toByteArray()

            val emptyCiphertext = emptyArray<Ciphertext>()
            val TEST_CHILDREN_IDS = emptyArray<GroupId>()

            val TEST_GROUP_INTERNALS_RESPONSE = GroupInternalsResponse(
                TEST_GROUP_ID,
                null,
                GroupType.Team,
                TEST_JOIN_MODE,
                TEST_PERMISSION_MODE,
                TEST_URL,
                TEST_GROUP_SETTINGS_DATA,
                internalSettingsData,
                emptyCiphertext,
                null,
                TEST_CHILDREN_IDS,
                TEST_GROUP_TAG1
            )

            val TEST_TEAM = Team(
                TEST_GROUP_ID,
                TEST_GROUP_KEY,
                TEST_SIGNED_IN_USER_ID,
                TEST_JOIN_MODE,
                TEST_PERMISSION_MODE,
                TEST_GROUP_TAG1,
                TEST_URL,
                TEST_GROUP_NAME,
                null,
                null
            )

            coEvery {
                mockBackend.getGroupInternals(
                    TEST_GROUP_ID,
                    TEST_SERVER_CERTIFICATE,
                    TEST_GROUP_TAG1
                )
            } returns TEST_GROUP_INTERNALS_RESPONSE

            coEvery { mockGroupStorageManager.loadMembershipsOfGroup(TEST_GROUP_ID) } returns emptySet()

            teamManager.reload(TEST_TEAM)

            confirmVerified(mockUserManager)
            coVerify(exactly = 1) { mockGroupStorageManager.storeTeam(TEST_TEAM, Replace(emptyList())) }
        }

        @Test
        fun `but the new Team has no Meetup`() = runBlockingTest {
            val internalSettings = Team.InternalTeamSettings()
            val internalSettingsData = Json.encodeToString(Team.InternalTeamSettings.serializer(), internalSettings).toByteArray()

            val emptyCiphertext = emptyArray<Ciphertext>()
            val TEST_CHILDREN_IDS = emptyArray<GroupId>()

            val TEST_GROUP_INTERNALS_RESPONSE = GroupInternalsResponse(
                TEST_GROUP_ID,
                null,
                GroupType.Team,
                TEST_JOIN_MODE,
                TEST_PERMISSION_MODE,
                TEST_URL,
                TEST_GROUP_SETTINGS_DATA,
                internalSettingsData,
                emptyCiphertext,
                null,
                TEST_CHILDREN_IDS,
                TEST_GROUP_TAG1
            )

            val TEST_TEAM = Team(
                TEST_GROUP_ID,
                TEST_GROUP_KEY,
                TEST_SIGNED_IN_USER_ID,
                TEST_JOIN_MODE,
                TEST_PERMISSION_MODE,
                TEST_GROUP_TAG1,
                TEST_URL,
                TEST_GROUP_NAME,
                null,
                null
            )

            coEvery {
                mockBackend.getGroupInternals(
                    TEST_GROUP_ID,
                    TEST_SERVER_CERTIFICATE,
                    TEST_GROUP_TAG1
                )
            } returns TEST_GROUP_INTERNALS_RESPONSE

            coEvery { mockGroupStorageManager.loadMembershipsOfGroup(TEST_GROUP_ID) } returns emptySet()

            teamManager.reload(TEST_TEAM)

            confirmVerified(mockUserManager)
            coVerify(exactly = 1) { mockGroupStorageManager.storeTeam(TEST_TEAM, any()) }
        }

        @Test
        fun reloadTeam_WithMemberships() = runBlockingTest {
            mockkStatic("tice.utility.Base64ConvertFunctionsKt")
            val slotString = slot<String>()
            val slotByteArray = slot<ByteArray>()
            every { capture(slotString).dataFromBase64() } answers { slotString.captured.toByteArray() }
            every { capture(slotByteArray).toBase64String() } answers { String(slotByteArray.captured) }

            val internalSettings = Team.InternalTeamSettings()
            val internalSettingsData = Json.encodeToString(Team.InternalTeamSettings.serializer(), internalSettings).toByteArray()

            val TEST_CHILDREN_IDS = emptyArray<GroupId>()

            val membership1 = Membership(
                UUID.randomUUID(),
                TEST_GROUP_ID,
                "PublicKey".toByteArray(),
                true,
                "selfSignedMembershipCertificate1",
                TEST_SERVER_CERTIFICATE,
                "adminSignedMembershipCertificate1"
            )

            val membership2 = Membership(
                UUID.randomUUID(),
                TEST_GROUP_ID,
                "PublicKey".toByteArray(),
                true,
                "selfSignedMembershipCertificate2",
                TEST_SERVER_CERTIFICATE,
                "adminSignedMembershipCertificate2"
            )

            val encryptedMembership = mutableListOf<Ciphertext>()
            encryptedMembership.add(Json.encodeToString(MembershipSerializer, membership1).toByteArray())
            encryptedMembership.add(Json.encodeToString(MembershipSerializer, membership2).toByteArray())
            val encryptedMembershipArray = encryptedMembership.toTypedArray()
            val membershipSet = setOf(membership1, membership2)

            val TEST_GROUP_INTERNALS_RESPONSE = GroupInternalsResponse(
                TEST_GROUP_ID,
                null,
                GroupType.Team,
                TEST_JOIN_MODE,
                TEST_PERMISSION_MODE,
                TEST_URL,
                TEST_GROUP_SETTINGS_DATA,
                internalSettingsData,
                encryptedMembershipArray,
                null,
                TEST_CHILDREN_IDS,
                TEST_GROUP_TAG1
            )

            val TEST_TEAM = Team(
                TEST_GROUP_ID,
                TEST_GROUP_KEY,
                TEST_SIGNED_IN_USER_ID,
                TEST_JOIN_MODE,
                TEST_PERMISSION_MODE,
                TEST_GROUP_TAG1,
                TEST_URL,
                TEST_GROUP_NAME,
                null,
                null
            )

            coEvery {
                mockBackend.getGroupInternals(
                    TEST_GROUP_ID,
                    TEST_SERVER_CERTIFICATE,
                    TEST_GROUP_TAG1
                )
            } returns TEST_GROUP_INTERNALS_RESPONSE
            coEvery { mockGroupStorageManager.storeTeam(any(), any()) } answers {
                val storedTeam = arg(0) as Team
                val membershipsDiff = arg(1) as MembershipsDiff

                Assertions.assertTrue(membershipsDiff is Replace)
                Assertions.assertTrue(membershipsDiff.memberships.containsAll(membershipSet))
                Assertions.assertEquals(TEST_GROUP_ID, storedTeam.groupId)
            }
            coEvery { mockUserManager.getOrFetchUser(any()) } returns mockk()
            coEvery { mockGroupStorageManager.loadMembershipsOfGroup(TEST_GROUP_ID) } returns emptySet()

            val resultTeam = teamManager.reload(TEST_TEAM)

            Assertions.assertEquals(TEST_TEAM, resultTeam)

            coVerify(exactly = 1) { mockUserManager.getOrFetchUser(membership1.userId) }
            coVerify(exactly = 1) { mockUserManager.getOrFetchUser(membership2.userId) }
        }

        @Test
        fun reloadTeam_BackendException_NotModified() = runBlockingTest {
            coEvery {
                mockBackend.getGroupInternals(
                    TEST_GROUP_ID,
                    TEST_SERVER_CERTIFICATE,
                    TEST_GROUP_TAG1
                )
            } throws BackendException.NotModified

            val resultTeam = teamManager.reload(mockTeam)

            Assertions.assertEquals(mockTeam, resultTeam)
            coVerify(exactly = 1) { mockGroupStorageManager.loadMembership(TEST_SIGNED_IN_USER_ID, TEST_GROUP_ID) }
            confirmVerified(mockGroupStorageManager)
        }

        @Test
        fun reloadTeam_BackendException_NotFound() = runBlockingTest {
            coEvery {
                mockBackend.getGroupInternals(
                    TEST_GROUP_ID,
                    TEST_SERVER_CERTIFICATE,
                    TEST_GROUP_TAG1
                )
            } throws BackendException.NotFound

            Assertions.assertThrows(BackendException.NotFound::class.java) {
                runBlocking { teamManager.reload(mockTeam) }
            }

            coVerify(exactly = 1) { mockGroupStorageManager.removeTeam(TEST_GROUP_ID) }
            coVerify(exactly = 1) { mockGroupStorageManager.loadMembership(TEST_SIGNED_IN_USER_ID, TEST_GROUP_ID) }
            confirmVerified(mockGroupStorageManager)
        }

        @Test
        fun reloadTeam_BackendException_Unauthorized() = runBlockingTest {
            coEvery {
                mockBackend.getGroupInternals(
                    TEST_GROUP_ID,
                    TEST_SERVER_CERTIFICATE,
                    TEST_GROUP_TAG1
                )
            } throws BackendException.Unauthorized

            Assertions.assertThrows(BackendException.Unauthorized::class.java) {
                runBlocking { teamManager.reload(mockTeam) }
            }

            coVerify(exactly = 1) { mockGroupStorageManager.removeTeam(TEST_GROUP_ID) }
            coVerify(exactly = 1) { mockGroupStorageManager.loadMembership(TEST_SIGNED_IN_USER_ID, TEST_GROUP_ID) }
            confirmVerified(mockGroupStorageManager)
        }
    }

    @Test
    fun getOrFetchTeam_GotTeam() = runBlockingTest {
        coEvery { mockGroupStorageManager.loadTeam(TEST_GROUP_ID) } returns mockTeam

        val resultTeam = teamManager.getOrFetchTeam(TEST_GROUP_ID, TEST_GROUP_KEY)

        Assertions.assertEquals(mockTeam, resultTeam)
        coVerify(exactly = 1) { mockGroupStorageManager.loadTeam(TEST_GROUP_ID) }
        confirmVerified(mockCryptoManager)
        confirmVerified(mockBackend)
    }

    @Test
    fun getOrFetchTeam_FetchTeam() = runBlockingTest {
        val TEST_GROUP_INFORMATION_RESPONSE = GroupInformationResponse(
            TEST_GROUP_ID,
            null,
            GroupType.Team,
            TEST_JOIN_MODE,
            TEST_PERMISSION_MODE,
            TEST_URL,
            TEST_GROUP_SETTINGS_DATA,
            TEST_GROUP_TAG1
        )

        coEvery { mockBackend.getGroupInformation(TEST_GROUP_ID, null) } returns TEST_GROUP_INFORMATION_RESPONSE
        coEvery { mockGroupStorageManager.loadTeam(TEST_GROUP_ID) } returns null
        val resultTeam = teamManager.getOrFetchTeam(TEST_GROUP_ID, TEST_GROUP_KEY)

        val TEST_TEAM = Team(
            TEST_GROUP_ID,
            TEST_GROUP_KEY,
            TEST_SIGNED_IN_USER_ID,
            TEST_JOIN_MODE,
            TEST_PERMISSION_MODE,
            TEST_GROUP_TAG1,
            TEST_URL,
            TEST_GROUP_NAME,
            null,
            null
        )

        Assertions.assertEquals(TEST_TEAM, resultTeam)

        coVerify(exactly = 1) { mockGroupStorageManager.loadTeam(TEST_GROUP_ID) }
        coVerify(exactly = 1) { mockBackend.getGroupInformation(TEST_GROUP_ID, null) }
        verify(exactly = 1) { mockCryptoManager.decrypt(TEST_GROUP_SETTINGS_DATA, TEST_GROUP_KEY) }
    }

    @Nested
    inner class Join {

        @Test
        fun success() = runBlockingTest {
            val internalSettings = Team.InternalTeamSettings()
            val internalSettingsData = Json.encodeToString(Team.InternalTeamSettings.serializer(), internalSettings).toByteArray()

            val emptyCiphertext = emptyArray<Ciphertext>()
            val TEST_CHILDREN_IDS = emptyArray<GroupId>()

            val TEST_GROUP_INTERNALS_RESPONSE = GroupInternalsResponse(
                TEST_GROUP_ID,
                null,
                GroupType.Team,
                TEST_JOIN_MODE,
                TEST_PERMISSION_MODE,
                TEST_URL,
                TEST_GROUP_SETTINGS_DATA,
                internalSettingsData,
                emptyCiphertext,
                null,
                TEST_CHILDREN_IDS,
                TEST_GROUP_TAG1
            )

            val TEST_TEAM = Team(
                TEST_GROUP_ID,
                TEST_GROUP_KEY,
                TEST_SIGNED_IN_USER_ID,
                TEST_JOIN_MODE,
                TEST_PERMISSION_MODE,
                TEST_GROUP_TAG1,
                TEST_URL,
                TEST_GROUP_NAME,
                null,
                null
            )

            every { mockLocalizationProvider.getString(LocalizationId(R.string.chat_metaInfo_self_group_joined_title)) } returns TEST_LOC_CHAT_STRING
            coEvery { mockGroupStorageManager.isMember(TEST_SIGNED_IN_USER_ID, TEST_GROUP_ID) } returns false
            every {
                mockAuthManager.createUserSignedMembershipCertificate(
                    TEST_SIGNED_IN_USER_ID,
                    TEST_GROUP_ID,
                    false,
                    TEST_SIGNED_IN_USER_ID,
                    TEST_SIGNED_IN_PRIVATE_KEY
                )
            } returns TEST_SELF_CERTIFICATE
            coEvery { mockBackend.joinGroup(TEST_GROUP_ID, TEST_GROUP_TAG1, TEST_SELF_CERTIFICATE, null, null) }
                .returns(JoinGroupResponse(TEST_SERVER_CERTIFICATE))
            coEvery { mockBackend.getGroupInternals(TEST_GROUP_ID, TEST_SERVER_CERTIFICATE, null) } returns TEST_GROUP_INTERNALS_RESPONSE
            coEvery { mockGroupManager.addUserMember(TEST_TEAM, false, TEST_SERVER_CERTIFICATE, teamNotification) } returns Pair(
                mockMembership,
                TEST_GROUP_TAG2
            )

            teamManager.join(mockTeam)

            TEST_TEAM.tag = TEST_GROUP_TAG2

            coVerify(exactly = 1) { mockGroupStorageManager.isMember(TEST_SIGNED_IN_USER_ID, TEST_GROUP_ID) }
            coVerify(exactly = 1) { mockGroupStorageManager.storeTeam(TEST_TEAM, Add(listOf(mockMembership))) }
            coVerify(exactly = 1) { mockGroupManager.addUserMember(TEST_TEAM, false, TEST_SERVER_CERTIFICATE, teamNotification) }

            val chatEventSlot = slot<List<Message>>()
            coVerify(exactly = 1) { mockChatStorageManager.store(capture(chatEventSlot)) }

            val resultMessage = chatEventSlot.captured.first() as Message.MetaMessage
            Assertions.assertEquals(TEST_GROUP_ID, resultMessage.groupId)
            Assertions.assertEquals(TEST_SIGNED_IN_USER_ID, resultMessage.senderId)
            Assertions.assertEquals(TEST_LOC_CHAT_STRING, resultMessage.text)
            Assertions.assertEquals(true, resultMessage.read)
            Assertions.assertEquals(MessageStatus.Success, resultMessage.status)
        }

        @Test
        fun userAlreadyMember() = runBlockingTest {
            coEvery { mockGroupStorageManager.isMember(TEST_SIGNED_IN_USER_ID, TEST_GROUP_ID) } returns true

            Assert.assertThrows(TeamManagerException.UserAlreadyMember::class.java) {
                runBlocking { teamManager.join(mockTeam) }
            }

            coVerify(exactly = 1) { mockGroupStorageManager.isMember(TEST_SIGNED_IN_USER_ID, TEST_GROUP_ID) }
        }


        @Test
        fun groupOutdated() = runBlockingTest {
            coEvery { mockGroupStorageManager.isMember(TEST_SIGNED_IN_USER_ID, TEST_GROUP_ID) } returns false
            every {
                mockAuthManager.createUserSignedMembershipCertificate(
                    TEST_SIGNED_IN_USER_ID,
                    TEST_GROUP_ID,
                    false,
                    TEST_SIGNED_IN_USER_ID,
                    TEST_SIGNED_IN_PRIVATE_KEY
                )
            } returns TEST_SELF_CERTIFICATE
            coEvery { mockBackend.joinGroup(TEST_GROUP_ID, TEST_GROUP_TAG1, TEST_SELF_CERTIFICATE, null, null) }
                .throws(BackendException.GroupOutdated)
            coEvery { mockBackend.getGroupInternals(TEST_GROUP_ID, TEST_SERVER_CERTIFICATE, TEST_GROUP_TAG1) }
                .throws(BackendException.NotModified)

            Assert.assertThrows(BackendException.GroupOutdated::class.java) {
                runBlocking { teamManager.join(mockTeam) }
            }

            coVerify(exactly = 1) { mockGroupStorageManager.isMember(TEST_SIGNED_IN_USER_ID, TEST_GROUP_ID) }
            coVerify(exactly = 1) { mockBackend.getGroupInternals(TEST_GROUP_ID, TEST_SERVER_CERTIFICATE, TEST_GROUP_TAG1) }
        }
    }

    @Nested
    inner class Leave {

        @Test
        fun hasMeetup() = runBlockingTest {
            val TEST_MEETUP_ID = UUID.randomUUID()
            val mockMeetup = mockk<Meetup> { every { groupId } returns TEST_MEETUP_ID }

            coEvery { mockGroupStorageManager.meetupInTeam(TEST_GROUP_ID) } returns mockMeetup
            coEvery { mockGroupStorageManager.isMember(TEST_SIGNED_IN_USER_ID, TEST_MEETUP_ID) } returns true
            coEvery { mockGroupManager.leave(mockTeam, teamNotification) } returns TEST_GROUP_TAG1

            teamManager.leave(mockTeam)

            coVerify(exactly = 1) { mockMeetupManager.leave(mockMeetup) }
            coVerify(exactly = 1) { mockGroupManager.leave(mockTeam, teamNotification) }
            coVerify(exactly = 1) { mockGroupStorageManager.isMember(TEST_SIGNED_IN_USER_ID, TEST_MEETUP_ID) }
            coVerify(exactly = 1) { mockGroupStorageManager.removeTeam(TEST_GROUP_ID) }
            coVerify(exactly = 1) { mockGroupStorageManager.meetupInTeam(TEST_GROUP_ID) }
            coVerify(exactly = 1) { mockGroupManager.notificationRecipients(TEST_GROUP_ID, MessagePriority.Alert) }
            confirmVerified(mockGroupManager)
            confirmVerified(mockGroupStorageManager)
        }

        @Test
        fun noMeetup() = runBlockingTest {
            coEvery { mockGroupStorageManager.meetupInTeam(TEST_GROUP_ID) } returns null
            coEvery { mockGroupManager.leave(mockTeam, teamNotification) } returns TEST_GROUP_TAG1

            teamManager.leave(mockTeam)

            coVerify(exactly = 1) { mockGroupManager.leave(mockTeam, teamNotification) }
            coVerify(exactly = 1) { mockGroupStorageManager.removeTeam(TEST_GROUP_ID) }
            coVerify(exactly = 1) { mockGroupStorageManager.meetupInTeam(TEST_GROUP_ID) }
            coVerify(exactly = 1) { mockGroupManager.notificationRecipients(TEST_GROUP_ID, MessagePriority.Alert) }
            confirmVerified(mockGroupManager)
            confirmVerified(mockGroupStorageManager)
        }

        @Test
        fun `team is outdated`() = runBlockingTest {
            coEvery { mockGroupStorageManager.meetupInTeam(TEST_GROUP_ID) } returns null
            coEvery { mockGroupManager.leave(mockTeam, teamNotification) } throws BackendException.GroupOutdated
            coEvery { mockBackend.getGroupInternals(TEST_GROUP_ID, TEST_SERVER_CERTIFICATE, TEST_GROUP_TAG1) }
                .throws(BackendException.NotModified)

            Assert.assertThrows(BackendException.GroupOutdated::class.java) {
                runBlocking { teamManager.leave(mockTeam) }
            }

            coVerify(exactly = 1) { mockGroupManager.leave(mockTeam, teamNotification) }
            coVerify(exactly = 1) { mockGroupStorageManager.meetupInTeam(TEST_GROUP_ID) }
            coVerify(exactly = 1) { mockBackend.getGroupInternals(TEST_GROUP_ID, TEST_SERVER_CERTIFICATE, TEST_GROUP_TAG1) }
            coVerify(exactly = 1) { mockGroupManager.notificationRecipients(TEST_GROUP_ID, MessagePriority.Alert) }
        }

        @Test
        fun `meetup of team is outdated`() = runBlockingTest {
            val TEST_MEETUP_ID = UUID.randomUUID()
            val mockMeetup = mockk<Meetup> {
                every { groupId } returns TEST_MEETUP_ID
            }

            coEvery { mockGroupStorageManager.meetupInTeam(TEST_GROUP_ID) } returns mockMeetup
            coEvery { mockGroupStorageManager.isMember(TEST_SIGNED_IN_USER_ID, TEST_MEETUP_ID) } returns true
            coEvery { mockMeetupManager.leave(mockMeetup) } throws BackendException.GroupOutdated
            coEvery { mockBackend.getGroupInternals(TEST_GROUP_ID, TEST_SERVER_CERTIFICATE, TEST_GROUP_TAG1) }
                .throws(BackendException.NotModified)

            Assert.assertThrows(BackendException.GroupOutdated::class.java) {
                runBlocking { teamManager.leave(mockTeam) }
            }

            coVerify(exactly = 1) { mockMeetupManager.leave(mockMeetup) }
            coVerify(exactly = 1) { mockGroupStorageManager.meetupInTeam(TEST_GROUP_ID) }
            coVerify(exactly = 1) { mockBackend.getGroupInternals(TEST_GROUP_ID, TEST_SERVER_CERTIFICATE, TEST_GROUP_TAG1) }
        }
    }

    @Nested
    inner class Delete {

        @Test
        fun meetupRunning() = runBlockingTest {
            val mockMeetup = mockk<Meetup>()

            coEvery { mockGroupStorageManager.meetupInTeam(TEST_GROUP_ID) } returns mockMeetup

            Assertions.assertThrows(TeamManagerException.MeetupRunning::class.java) {
                runBlocking { teamManager.delete(mockTeam) }
            }

            coVerify(exactly = 1) { mockGroupStorageManager.meetupInTeam(TEST_GROUP_ID) }
            confirmVerified(mockGroupManager)
            confirmVerified(mockGroupStorageManager)
        }

        @Test
        fun permissionDenied() = runBlockingTest {
            coEvery { mockGroupStorageManager.meetupInTeam(TEST_GROUP_ID) } returns null
            every { mockMembership.admin } returns false

            Assertions.assertThrows(TeamManagerException.PermissionDenied::class.java) {
                runBlocking { teamManager.delete(mockTeam) }
            }

            coVerify(exactly = 1) { mockGroupStorageManager.meetupInTeam(TEST_GROUP_ID) }
            coVerify(exactly = 1) { mockGroupStorageManager.loadMembership(TEST_SIGNED_IN_USER_ID, TEST_GROUP_ID) }
            confirmVerified(mockGroupManager)
            confirmVerified(mockGroupStorageManager)
        }

        @Test
        fun success() = runBlockingTest {
            coEvery { mockGroupStorageManager.meetupInTeam(TEST_GROUP_ID) } returns null
            every { mockMembership.admin } returns true

            teamManager.delete(mockTeam)

            coVerify(exactly = 1) { mockGroupStorageManager.meetupInTeam(TEST_GROUP_ID) }
            coVerify(exactly = 1) { mockGroupStorageManager.loadMembership(TEST_SIGNED_IN_USER_ID, TEST_GROUP_ID) }
            coVerify(exactly = 1) {
                mockBackend.deleteGroup(TEST_GROUP_ID, TEST_SERVER_CERTIFICATE, TEST_GROUP_TAG1, teamNotification)
            }
            coVerify(exactly = 1) { mockGroupStorageManager.removeTeam(TEST_GROUP_ID) }
            coVerify(exactly = 1) { mockGroupManager.notificationRecipients(TEST_GROUP_ID, MessagePriority.Alert) }
            confirmVerified(mockGroupManager)
            confirmVerified(mockGroupStorageManager)
        }

        @Test
        fun groupOutdated() = runBlockingTest {
            coEvery { mockGroupStorageManager.meetupInTeam(TEST_GROUP_ID) } returns null
            every { mockMembership.admin } returns true
            coEvery { mockBackend.deleteGroup(TEST_GROUP_ID, TEST_SERVER_CERTIFICATE, TEST_GROUP_TAG1, teamNotification) }
                .throws(BackendException.GroupOutdated)
            coEvery { mockBackend.getGroupInternals(TEST_GROUP_ID, TEST_SERVER_CERTIFICATE, TEST_GROUP_TAG1) }
                .throws(BackendException.NotModified)

            Assert.assertThrows(BackendException.GroupOutdated::class.java) {
                runBlocking { teamManager.delete(mockTeam) }
            }

            coVerify(exactly = 1) { mockGroupStorageManager.meetupInTeam(TEST_GROUP_ID) }
            coVerify(exactly = 2) { mockGroupStorageManager.loadMembership(TEST_SIGNED_IN_USER_ID, TEST_GROUP_ID) }
            coVerify(exactly = 1) {
                mockBackend.deleteGroup(TEST_GROUP_ID, TEST_SERVER_CERTIFICATE, TEST_GROUP_TAG1, teamNotification)
            }
            coVerify(exactly = 1) { mockGroupManager.notificationRecipients(TEST_GROUP_ID, MessagePriority.Alert) }
            coVerify(exactly = 1) { mockBackend.getGroupInternals(TEST_GROUP_ID, TEST_SERVER_CERTIFICATE, TEST_GROUP_TAG1) }
        }
    }

    @Test
    fun setName() = runBlockingTest {
        coEvery {
            mockBackend.updateGroupSettings(
                TEST_GROUP_ID,
                TEST_GROUP_SETTINGS_DATA,
                TEST_SERVER_CERTIFICATE,
                TEST_GROUP_TAG1,
                teamNotification
            )
        } returns UpdatedETagResponse(TEST_GROUP_TAG2)
        every { mockLocalizationProvider.getString(LocalizationId(R.string.chat_metaInfo_self_group_updated_title)) } returns TEST_LOC_CHAT_STRING

        teamManager.setTeamName(mockTeam, TEST_GROUP_NAME)

        coVerify(exactly = 1) { mockGroupStorageManager.loadMembership(TEST_SIGNED_IN_USER_ID, TEST_GROUP_ID) }
        coVerify(exactly = 1) {
            mockBackend.updateGroupSettings(
                TEST_GROUP_ID,
                TEST_GROUP_SETTINGS_DATA,
                TEST_SERVER_CERTIFICATE,
                TEST_GROUP_TAG1,
                teamNotification
            )
        }

        val updatedTeam = Team(
            mockTeam.groupId,
            mockTeam.groupKey,
            mockTeam.owner,
            mockTeam.joinMode,
            mockTeam.permissionMode,
            TEST_GROUP_TAG2,
            mockTeam.url,
            TEST_GROUP_NAME,
            mockTeam.meetupId,
            mockTeam.meetingPoint
        )

        coVerify(exactly = 1) { mockGroupStorageManager.storeTeam(updatedTeam, any()) }
        coVerify(exactly = 1) { mockGroupManager.notificationRecipients(TEST_GROUP_ID, MessagePriority.Alert) }
        confirmVerified(mockBackend)
        confirmVerified(mockGroupManager)
        confirmVerified(mockGroupStorageManager)

        val chatEventSlot = slot<List<Message>>()
        coVerify(exactly = 1) { mockChatStorageManager.store(capture(chatEventSlot)) }

        val resultMessage = chatEventSlot.captured.first() as Message.MetaMessage
        Assertions.assertEquals(TEST_GROUP_ID, resultMessage.groupId)
        Assertions.assertEquals(TEST_SIGNED_IN_USER_ID, resultMessage.senderId)
        Assertions.assertEquals(TEST_LOC_CHAT_STRING, resultMessage.text)
        Assertions.assertEquals(true, resultMessage.read)
        Assertions.assertEquals(MessageStatus.Success, resultMessage.status)
    }

    @Test
    fun reloadAllTeams() = runBlockingTest {
        val mockTeam2 = mockk<Team>()
        val mockTeam3 = mockk<Team>()

        every { mockTeam2.groupId } returns TEST_GROUP_ID
        every { mockTeam2.tag } returns TEST_GROUP_TAG1
        every { mockTeam2.groupKey } returns TEST_GROUP_KEY
        every { mockTeam2.owner } returns TEST_SIGNED_IN_USER_ID

        every { mockTeam3.groupId } returns TEST_GROUP_ID
        every { mockTeam3.tag } returns TEST_GROUP_TAG1
        every { mockTeam3.groupKey } returns TEST_GROUP_KEY
        every { mockTeam3.owner } returns TEST_SIGNED_IN_USER_ID

        val teamSet = setOf<Team>(mockTeam, mockTeam2, mockTeam3)

        coEvery { mockGroupStorageManager.loadTeams() } returns teamSet
        coEvery {
            mockBackend.getGroupInternals(
                TEST_GROUP_ID,
                TEST_SERVER_CERTIFICATE,
                TEST_GROUP_TAG1
            )
        } throws BackendException.NotModified

        teamManager.reloadAllTeams()

        coVerify(exactly = 1) { mockGroupStorageManager.loadTeams() }
        verify(atLeast = 1) { mockTeam.groupId }
        verify(atLeast = 1) { mockTeam2.groupId }
        verify(atLeast = 1) { mockTeam3.groupId }
        coVerify(exactly = 3) { mockBackend.getGroupInternals(TEST_GROUP_ID, TEST_SERVER_CERTIFICATE, TEST_GROUP_TAG1) }
    }

    @Test
    fun reloadAllTeams_NoTeams() = runBlockingTest {
        val teamSet = emptySet<Team>()

        coEvery { mockGroupStorageManager.loadTeams() } returns teamSet

        teamManager.reloadAllTeams()

        coVerify(exactly = 1) { mockGroupStorageManager.loadTeams() }
        confirmVerified(mockBackend)
    }

    @Test
    fun setLocationSharing() = runBlockingTest {
        val TEST_ENABLED_STATUS = true
        val locationSharingSlot = slot<LocationSharingState>()

        coEvery { mockLocationSharingStorageManager.storeLocationSharingState(capture(locationSharingSlot)) } returns Unit

        teamManager.setLocationSharing(mockTeam, TEST_ENABLED_STATUS)

        val payload = LocationSharingUpdate(TEST_GROUP_ID, TEST_ENABLED_STATUS)
        val expectedPayloadContainer = PayloadContainer(Payload.PayloadType.LocationSharingUpdateV1, payload)

        val capturedStatus = locationSharingSlot.captured
        coVerify(exactly = 1) { mockLocationSharingStorageManager.storeLocationSharingState(capturedStatus) }
        coVerify(exactly = 1) { mockGroupManager.send(expectedPayloadContainer, mockTeam, null, MessagePriority.Alert) }

        Assertions.assertEquals(capturedStatus.userId, TEST_SIGNED_IN_USER_ID)
        Assertions.assertEquals(capturedStatus.groupId, TEST_GROUP_ID)
        Assertions.assertEquals(capturedStatus.sharingEnabled, TEST_ENABLED_STATUS)
    }

    @Test
    fun setMeetingPoint_Success() = runBlockingTest {
        val TEST_LAT = Random().nextDouble()
        val TEST_LNG = Random().nextDouble()
        val mockCoords = Coordinates(TEST_LAT, TEST_LNG)

        val cipherSlot = slot<Ciphertext>()

        val dateThreshold = Date()

        coEvery { mockGroupStorageManager.loadMembership(TEST_SIGNED_IN_USER_ID, TEST_GROUP_ID) } returns mockMembership
        every { mockMembership.serverSignedMembershipCertificate } returns TEST_SERVER_CERTIFICATE
        coEvery {
            mockBackend.updateGroupInternalSettings(
                TEST_GROUP_ID,
                capture(cipherSlot),
                TEST_SERVER_CERTIFICATE,
                TEST_GROUP_TAG1,
                teamNotification.toList()
            )
        }.returns(UpdatedETagResponse(TEST_GROUP_TAG2))

        teamManager.setMeetingPoint(mockCoords, mockTeam)

        val internalSettings = Json.decodeFromString(Team.InternalTeamSettings.serializer(), String(cipherSlot.captured))
        Assertions.assertEquals(TEST_LAT, internalSettings.meetingPoint?.latitude)
        Assertions.assertEquals(TEST_LNG, internalSettings.meetingPoint?.longitude)
        Assertions.assertEquals(0.0, internalSettings.meetingPoint?.altitude)
        Assertions.assertEquals(0.0f, internalSettings.meetingPoint?.horizontalAccuracy)
        Assertions.assertEquals(0.0f, internalSettings.meetingPoint?.verticalAccuracy)
        Assertions.assertTrue(dateThreshold <= internalSettings.meetingPoint!!.timestamp)
        Assertions.assertTrue(Date() >= internalSettings.meetingPoint!!.timestamp)

        coVerify(exactly = 1) { mockGroupStorageManager.storeTeam(mockTeam, null) }
    }

    @Test
    fun processLocationUpdate_Success() = runBlockingTest {
        val mockLocation = mockk<Location>()

        val TEST_GROUP_ID_1 = UUID.randomUUID()
        val TEST_GROUP_ID_2 = UUID.randomUUID()
        val TEST_GROUP_ID_3 = UUID.randomUUID()

        val payloadContainer1 = PayloadContainer(Payload.PayloadType.LocationUpdateV2, LocationUpdateV2(mockLocation, TEST_GROUP_ID_1))
        val payloadContainer2 = PayloadContainer(Payload.PayloadType.LocationUpdateV2, LocationUpdateV2(mockLocation, TEST_GROUP_ID_2))

        val mockTeam1 = mockk<Team> { every { groupId } returns TEST_GROUP_ID_1 }
        val mockTeam2 = mockk<Team> { every { groupId } returns TEST_GROUP_ID_2 }

        val TEST_LOCATION_SHARING_STATE_1 = LocationSharingState(TEST_SIGNED_IN_USER_ID, TEST_GROUP_ID_1, true, Date())
        val TEST_LOCATION_SHARING_STATE_2 = LocationSharingState(TEST_SIGNED_IN_USER_ID, TEST_GROUP_ID_2, true, Date())
        val TEST_LOCATION_SHARING_STATE_3 = LocationSharingState(TEST_SIGNED_IN_USER_ID, TEST_GROUP_ID_3, false, Date())

        val collapsSlot1 = slot<CollapseIdentifier>()
        val collapsSlot2 = slot<CollapseIdentifier>()

        coEvery { mockGroupManager.send(payloadContainer1, mockTeam1, capture(collapsSlot1), MessagePriority.Deferred) } returns Unit
        coEvery { mockGroupManager.send(payloadContainer2, mockTeam2, capture(collapsSlot2), MessagePriority.Deferred) } returns Unit

        coEvery { mockLocationSharingStorageManager.getAllStatesOfUser(TEST_SIGNED_IN_USER_ID) } returns listOf(
            TEST_LOCATION_SHARING_STATE_1,
            TEST_LOCATION_SHARING_STATE_2,
            TEST_LOCATION_SHARING_STATE_3
        )

        coEvery { mockGroupStorageManager.loadTeam(TEST_GROUP_ID_1) } returns mockTeam1
        coEvery { mockGroupStorageManager.loadTeam(TEST_GROUP_ID_2) } returns mockTeam2

        teamManager.processLocationUpdate(mockLocation)

        val capturedCollaps1 = collapsSlot1.captured
        val capturedCollaps2 = collapsSlot2.captured

        coVerify(exactly = 1) { mockGroupManager.send(payloadContainer1, mockTeam1, capturedCollaps1, MessagePriority.Deferred) }
        coVerify(exactly = 1) { mockGroupManager.send(payloadContainer2, mockTeam2, capturedCollaps2, MessagePriority.Deferred) }
    }
}
