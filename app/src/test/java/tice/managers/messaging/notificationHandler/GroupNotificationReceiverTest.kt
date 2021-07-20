package tice.managers.messaging.notificationHandler

import com.ticeapp.TICE.R
import io.mockk.*
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.*
import tice.exceptions.GroupNotificationReceiverException
import tice.exceptions.LocationSharingException
import tice.exceptions.UnexpectedPayloadTypeException
import tice.managers.PopupNotificationManagerType
import tice.managers.UserManagerType
import tice.managers.group.MeetupManagerType
import tice.managers.group.TeamManagerType
import tice.managers.messaging.PostOfficeType
import tice.managers.storageManagers.ChatStorageManagerType
import tice.managers.storageManagers.GroupStorageManagerType
import tice.managers.storageManagers.LocationSharingStorageManagerType
import tice.models.LocalizationId
import tice.models.LocationSharingState
import tice.models.Meetup
import tice.models.Team
import tice.models.chat.Message
import tice.models.chat.MessageStatus
import tice.models.messaging.*
import tice.utility.provider.LocalizationProviderType
import java.util.*

internal class GroupNotificationReceiverTest {

    private lateinit var groupNotificationReceiver: GroupNotificationReceiver

    private val mockPostOffice: PostOfficeType = mockk(relaxUnitFun = true)
    private val mockGroupStorageManager: GroupStorageManagerType = mockk(relaxUnitFun = true)
    private val mockTeamManager: TeamManagerType = mockk(relaxUnitFun = true)
    private val mockMeetupManager: MeetupManagerType = mockk(relaxUnitFun = true)
    private val mockLocalizationProvider: LocalizationProviderType = mockk(relaxUnitFun = true)
    private val mockPopupNotificationManager: PopupNotificationManagerType = mockk(relaxUnitFun = true)
    private val mockChatStorageManager: ChatStorageManagerType = mockk(relaxUnitFun = true)
    private val mockUserManager: UserManagerType = mockk(relaxUnitFun = true)
    private val mockLocationSharingStorageManager: LocationSharingStorageManagerType = mockk(relaxUnitFun = true)

    private val mockTeam: Team = mockk()
    private val mockMeetup: Meetup = mockk()

    private val TEST_USER_ID = UUID.randomUUID()
    private val TEST_TEAM_ID = UUID.randomUUID()
    private val TEST_MEETUP_ID = UUID.randomUUID()
    private val TEST_SENDER_ID = UUID.randomUUID()
    private val TEST_LOC_NOTI_STRING = "locNotiString"
    private val TEST_LOC_CHAT_STRING = "locChatString"
    private val TEST_DATE = Date()
    private val TEST_OLD_DATE = Date(TEST_DATE.time - 1000L)

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        groupNotificationReceiver = GroupNotificationReceiver(
            mockPostOffice,
            mockGroupStorageManager,
            mockTeamManager,
            mockMeetupManager,
            mockPopupNotificationManager,
            mockLocalizationProvider,
            mockChatStorageManager,
            mockUserManager,
            mockLocationSharingStorageManager
        )

        every { mockTeam.groupId } returns TEST_TEAM_ID
        every { mockMeetup.teamId } returns TEST_TEAM_ID

        coEvery { mockGroupStorageManager.loadTeam(TEST_TEAM_ID) } returns mockTeam
        coEvery { mockGroupStorageManager.loadTeam(TEST_MEETUP_ID) } returns null
        coEvery { mockGroupStorageManager.loadMeetup(TEST_MEETUP_ID) } returns mockMeetup
        coEvery { mockGroupStorageManager.loadMeetup(TEST_TEAM_ID) } returns null
    }

    @Test
    fun `Init and registerEnvelopeReceiver at the PostOffice`() = runBlockingTest {
        groupNotificationReceiver.registerEnvelopeReceiver()

        verify { mockPostOffice.registerEnvelopeReceiver(Payload.PayloadType.GroupUpdateV1, groupNotificationReceiver) }
        verify { mockPostOffice.registerEnvelopeReceiver(Payload.PayloadType.LocationSharingUpdateV1, groupNotificationReceiver) }
    }

    @Nested
    inner class `Receive PayloadContainerBundle` {

        @Test
        fun `wrong payload`() = runBlockingTest {
            val mockPayloadContainerBundle = mockk<PayloadContainerBundle>()
            val mockPayload = mockk<UserUpdate>()

            every { mockPayloadContainerBundle.payload } returns mockPayload

            Assertions.assertThrows(UnexpectedPayloadTypeException::class.java) {
                runBlockingTest {
                    groupNotificationReceiver.handlePayloadContainerBundle(mockPayloadContainerBundle)
                }
            }
        }

        @Test
        fun `GroupUpdate unknown GroupId`() = runBlockingTest {
            val unnknownGroupId = UUID.randomUUID()
            val action = GroupUpdate.Action.CHILD_GROUP_DELETED

            val mockPayloadContainerBundle = mockk<PayloadContainerBundle>()
            val mockPayload = GroupUpdate(unnknownGroupId, action)
            val mockMetaInfo = mockk<PayloadMetaInfo>()

            every { mockPayloadContainerBundle.payload } returns mockPayload
            every { mockPayloadContainerBundle.metaInfo } returns mockMetaInfo

            coEvery { mockGroupStorageManager.loadTeam(unnknownGroupId) } returns null
            coEvery { mockGroupStorageManager.loadMeetup(unnknownGroupId) } returns null

            groupNotificationReceiver.handlePayloadContainerBundle(mockPayloadContainerBundle)

            coVerify(exactly = 1) { mockGroupStorageManager.loadMeetup(unnknownGroupId) }
            coVerify(exactly = 1) { mockGroupStorageManager.loadTeam(unnknownGroupId) }
            confirmVerified(mockMeetupManager)
            confirmVerified(mockTeamManager)
            confirmVerified(mockGroupStorageManager)
        }

        @Test
        fun `CHILD_GROUP_CREATED`() = runBlockingTest {
            val action = GroupUpdate.Action.CHILD_GROUP_CREATED

            val mockPayloadContainerBundle = mockk<PayloadContainerBundle>()
            val mockPayload = GroupUpdate(TEST_TEAM_ID, action)
            val mockMetaInfo = mockk<PayloadMetaInfo>()

            val reloadedTeam = mockk<Team>()

            every { mockPayloadContainerBundle.payload } returns mockPayload
            every { mockPayloadContainerBundle.metaInfo } returns mockMetaInfo
            every { mockLocalizationProvider.getString(LocalizationId(R.string.notification_meetup_created_title)) } returns TEST_LOC_NOTI_STRING
            every { mockLocalizationProvider.getString(LocalizationId(R.string.chat_metaInfo_other_meetup_created_title)) } returns TEST_LOC_CHAT_STRING
            every { mockMetaInfo.senderId } returns TEST_SENDER_ID
            every { mockMetaInfo.timestamp } returns TEST_DATE
            coEvery { mockTeamManager.reload(mockTeam) } returns reloadedTeam

            every { reloadedTeam.meetupId } returns TEST_MEETUP_ID
            every { reloadedTeam.groupId } returns TEST_TEAM_ID

            groupNotificationReceiver.handlePayloadContainerBundle(mockPayloadContainerBundle)


            coVerify(exactly = 1) { mockTeamManager.reload(mockTeam) }
            coVerify(exactly = 1) { mockMeetupManager.addOrReload(TEST_MEETUP_ID, TEST_TEAM_ID) }

            val chatEventSlot = slot<List<Message>>()
            coVerify(exactly = 1) { mockPopupNotificationManager.showPopUpNotification(TEST_LOC_NOTI_STRING, "") }
            coVerify(exactly = 1) { mockChatStorageManager.store(capture(chatEventSlot)) }

            val resultMessage = chatEventSlot.captured.first() as Message.MetaMessage
            assertMetaMessage(resultMessage)
        }

        @Test
        fun `CHILD_GROUP_CREATED but the reloaded Team has no MeetupId`() = runBlockingTest {
            val action = GroupUpdate.Action.CHILD_GROUP_CREATED

            val mockPayloadContainerBundle = mockk<PayloadContainerBundle>()
            val mockPayload = GroupUpdate(TEST_TEAM_ID, action)
            val mockMetaInfo = mockk<PayloadMetaInfo>()

            val reloadedTeam = mockk<Team>()

            every { mockPayloadContainerBundle.payload } returns mockPayload
            every { mockPayloadContainerBundle.metaInfo } returns mockMetaInfo
            coEvery { mockTeamManager.reload(mockTeam) } returns reloadedTeam

            every { reloadedTeam.meetupId } returns null
            every { reloadedTeam.groupId } returns TEST_TEAM_ID

            Assertions.assertThrows(GroupNotificationReceiverException.GroupNotFound::class.java) {
                runBlockingTest {
                    groupNotificationReceiver.handlePayloadContainerBundle(mockPayloadContainerBundle)
                }
            }

            coVerify(exactly = 1) { mockTeamManager.reload(mockTeam) }
            confirmVerified(mockMeetupManager)
            confirmVerified(mockTeamManager)
        }

        @Test
        fun `CHILD_GROUP_CREATED and succeeds`() = runBlockingTest {
            val action = GroupUpdate.Action.CHILD_GROUP_CREATED

            val mockPayloadContainerBundle = mockk<PayloadContainerBundle>()
            val mockPayload = GroupUpdate(TEST_TEAM_ID, action)
            val mockMetaInfo = mockk<PayloadMetaInfo>()

            val reloadedTeam = mockk<Team>()

            every { mockPayloadContainerBundle.payload } returns mockPayload
            every { mockPayloadContainerBundle.metaInfo } returns mockMetaInfo
            coEvery { mockTeamManager.reload(mockTeam) } returns reloadedTeam
            every { mockLocalizationProvider.getString(LocalizationId(R.string.notification_meetup_created_title)) } returns TEST_LOC_NOTI_STRING
            every { mockLocalizationProvider.getString(LocalizationId(R.string.chat_metaInfo_other_meetup_created_title)) } returns TEST_LOC_CHAT_STRING
            every { mockMetaInfo.senderId } returns TEST_SENDER_ID
            every { mockMetaInfo.timestamp } returns TEST_DATE

            every { reloadedTeam.meetupId } returns TEST_MEETUP_ID
            every { reloadedTeam.groupId } returns TEST_TEAM_ID

            groupNotificationReceiver.handlePayloadContainerBundle(mockPayloadContainerBundle)


            coVerify(exactly = 1) { mockTeamManager.reload(mockTeam) }
            coVerify(exactly = 1) { mockMeetupManager.addOrReload(TEST_MEETUP_ID, TEST_TEAM_ID) }
            confirmVerified(mockMeetupManager)
            confirmVerified(mockTeamManager)

            val chatEventSlot = slot<List<Message>>()
            coVerify(exactly = 1) { mockPopupNotificationManager.showPopUpNotification(TEST_LOC_NOTI_STRING, "") }
            coVerify(exactly = 1) { mockChatStorageManager.store(capture(chatEventSlot)) }

            val resultMessage = chatEventSlot.captured.first() as Message.MetaMessage
            assertMetaMessage(resultMessage)
        }

        @Test
        fun `CHILD_GROUP_DELETED and succeeds`() = runBlockingTest {
            val action = GroupUpdate.Action.CHILD_GROUP_DELETED

            val mockPayloadContainerBundle = mockk<PayloadContainerBundle>()
            val mockPayload = GroupUpdate(TEST_TEAM_ID, action)
            val mockMetaInfo = mockk<PayloadMetaInfo>()

            every { mockPayloadContainerBundle.payload } returns mockPayload
            every { mockPayloadContainerBundle.metaInfo } returns mockMetaInfo
            coEvery { mockTeamManager.reload(mockTeam) } returns mockTeam
            every { mockLocalizationProvider.getString(LocalizationId(R.string.notification_meetup_deleted_title)) } returns TEST_LOC_NOTI_STRING
            every { mockLocalizationProvider.getString(LocalizationId(R.string.chat_metaInfo_other_meetup_deleted_title)) } returns TEST_LOC_CHAT_STRING
            every { mockMetaInfo.senderId } returns TEST_SENDER_ID
            every { mockMetaInfo.timestamp } returns TEST_DATE

            groupNotificationReceiver.handlePayloadContainerBundle(mockPayloadContainerBundle)

            coVerify(exactly = 1) { mockTeamManager.reload(mockTeam) }
            confirmVerified(mockMeetupManager)
            confirmVerified(mockTeamManager)

            val chatEventSlot = slot<List<Message>>()
            coVerify(exactly = 1) { mockPopupNotificationManager.showPopUpNotification(TEST_LOC_NOTI_STRING, "") }
            coVerify(exactly = 1) { mockChatStorageManager.store(capture(chatEventSlot)) }

            val resultMessage = chatEventSlot.captured.first() as Message.MetaMessage
            assertMetaMessage(resultMessage)

        }

        @Test
        fun `GROUP_DELETED for a Team`() = runBlockingTest {
            val action = GroupUpdate.Action.GROUP_DELETED

            val mockPayloadContainerBundle = mockk<PayloadContainerBundle>()
            val mockPayload = GroupUpdate(TEST_TEAM_ID, action)
            val mockMetaInfo = mockk<PayloadMetaInfo>()

            every { mockPayloadContainerBundle.payload } returns mockPayload
            every { mockPayloadContainerBundle.metaInfo } returns mockMetaInfo
            every { mockLocalizationProvider.getString(LocalizationId(R.string.notification_group_deleted_title)) } returns TEST_LOC_NOTI_STRING
            every { mockMetaInfo.senderId } returns TEST_SENDER_ID
            every { mockMetaInfo.timestamp } returns TEST_DATE

            groupNotificationReceiver.handlePayloadContainerBundle(mockPayloadContainerBundle)

            coVerify(exactly = 1) { mockGroupStorageManager.removeTeam(TEST_TEAM_ID) }
            confirmVerified(mockMeetupManager)
            confirmVerified(mockTeamManager)
        }

        @Test
        fun `GROUP_DELETED for a Meetup`() = runBlockingTest {
            val action = GroupUpdate.Action.GROUP_DELETED

            val mockPayloadContainerBundle = mockk<PayloadContainerBundle>()
            val mockPayload = GroupUpdate(TEST_MEETUP_ID, action)
            val mockMetaInfo = mockk<PayloadMetaInfo>()

            every { mockPayloadContainerBundle.payload } returns mockPayload
            every { mockPayloadContainerBundle.metaInfo } returns mockMetaInfo
            coEvery { mockGroupStorageManager.teamOfMeetup(mockMeetup) } returns mockTeam
            coEvery { mockTeamManager.reload(mockTeam) } returns mockTeam
            every { mockLocalizationProvider.getString(LocalizationId(R.string.notification_meetup_deleted_title)) } returns TEST_LOC_NOTI_STRING
            every { mockLocalizationProvider.getString(LocalizationId(R.string.chat_metaInfo_other_meetup_deleted_title)) } returns TEST_LOC_CHAT_STRING
            every { mockMetaInfo.senderId } returns TEST_SENDER_ID
            every { mockMetaInfo.timestamp } returns TEST_DATE

            groupNotificationReceiver.handlePayloadContainerBundle(mockPayloadContainerBundle)

            coVerify(exactly = 1) { mockGroupStorageManager.removeMeetup(mockMeetup) }
            coVerify(exactly = 1) { mockGroupStorageManager.teamOfMeetup(mockMeetup) }
            coVerify(exactly = 1) { mockTeamManager.reload(mockTeam) }
            confirmVerified(mockMeetupManager)
            confirmVerified(mockTeamManager)

            val chatEventSlot = slot<List<Message>>()
            coVerify(exactly = 1) { mockPopupNotificationManager.showPopUpNotification(TEST_LOC_NOTI_STRING, "") }
            coVerify(exactly = 1) { mockChatStorageManager.store(capture(chatEventSlot)) }

            val resultMessage = chatEventSlot.captured.first() as Message.MetaMessage
            assertMetaMessage(resultMessage)

        }

        @Test
        fun `MEMBER_ADDED for a Team`() = runBlockingTest {
            val action = GroupUpdate.Action.MEMBER_ADDED

            val mockPayloadContainerBundle = mockk<PayloadContainerBundle>()
            val mockPayload = GroupUpdate(TEST_TEAM_ID, action)
            val mockMetaInfo = mockk<PayloadMetaInfo>()

            every { mockPayloadContainerBundle.payload } returns mockPayload
            every { mockPayloadContainerBundle.metaInfo } returns mockMetaInfo
            coEvery { mockTeamManager.reload(mockTeam) } returns mockTeam
            every { mockLocalizationProvider.getString(LocalizationId(R.string.notification_group_memberAdded_title_unknown)) } returns TEST_LOC_NOTI_STRING
            every { mockLocalizationProvider.getString(LocalizationId(R.string.chat_metaInfo_other_group_memberAdded_title_unknown)) } returns TEST_LOC_CHAT_STRING
            every { mockMetaInfo.senderId } returns TEST_SENDER_ID
            every { mockMetaInfo.timestamp } returns TEST_DATE

            groupNotificationReceiver.handlePayloadContainerBundle(mockPayloadContainerBundle)

            coVerify(exactly = 1) { mockTeamManager.reload(mockTeam) }
            confirmVerified(mockMeetupManager)
            confirmVerified(mockTeamManager)

            val chatEventSlot = slot<List<Message>>()
            coVerify(exactly = 1) { mockPopupNotificationManager.showPopUpNotification(TEST_LOC_NOTI_STRING, "") }
            coVerify(exactly = 1) { mockChatStorageManager.store(capture(chatEventSlot)) }

            val resultMessage = chatEventSlot.captured.first() as Message.MetaMessage
            assertMetaMessage(resultMessage)
        }

        @Test
        fun `MEMBER_ADDED for a Meetup`() = runBlockingTest {
            val action = GroupUpdate.Action.MEMBER_ADDED

            val mockPayloadContainerBundle = mockk<PayloadContainerBundle>()
            val mockPayload = GroupUpdate(TEST_MEETUP_ID, action)
            val mockMetaInfo = mockk<PayloadMetaInfo>()

            every { mockPayloadContainerBundle.payload } returns mockPayload
            every { mockPayloadContainerBundle.metaInfo } returns mockMetaInfo
            every { mockLocalizationProvider.getString(LocalizationId(R.string.notification_group_memberAdded_title_unknown)) } returns TEST_LOC_NOTI_STRING
            every { mockLocalizationProvider.getString(LocalizationId(R.string.chat_metaInfo_other_group_memberAdded_title_unknown)) } returns TEST_LOC_CHAT_STRING
            every { mockMetaInfo.senderId } returns TEST_SENDER_ID
            every { mockMetaInfo.timestamp } returns TEST_DATE

            groupNotificationReceiver.handlePayloadContainerBundle(mockPayloadContainerBundle)

            coVerify(exactly = 1) { mockMeetupManager.reload(mockMeetup) }
            confirmVerified(mockMeetupManager)
            confirmVerified(mockTeamManager)

            val chatEventSlot = slot<List<Message>>()
            coVerify(exactly = 1) { mockPopupNotificationManager.showPopUpNotification(TEST_LOC_NOTI_STRING, "") }
            coVerify(exactly = 1) { mockChatStorageManager.store(capture(chatEventSlot)) }

            val resultMessage = chatEventSlot.captured.first() as Message.MetaMessage
            assertMetaMessage(resultMessage)
        }

        @Test
        fun `MEMBER_DELETED for a Team`() = runBlockingTest {
            val action = GroupUpdate.Action.MEMBER_DELETED

            val mockPayloadContainerBundle = mockk<PayloadContainerBundle>()
            val mockPayload = GroupUpdate(TEST_TEAM_ID, action)
            val mockMetaInfo = mockk<PayloadMetaInfo>()

            every { mockPayloadContainerBundle.payload } returns mockPayload
            every { mockPayloadContainerBundle.metaInfo } returns mockMetaInfo
            coEvery { mockTeamManager.reload(mockTeam) } returns mockTeam
            every { mockLocalizationProvider.getString(LocalizationId(R.string.notification_group_memberDeleted_title_unknown)) } returns TEST_LOC_NOTI_STRING
            every { mockLocalizationProvider.getString(LocalizationId(R.string.chat_metaInfo_other_group_memberDeleted_title_unknown)) } returns TEST_LOC_CHAT_STRING
            every { mockMetaInfo.senderId } returns TEST_SENDER_ID
            every { mockMetaInfo.timestamp } returns TEST_DATE

            groupNotificationReceiver.handlePayloadContainerBundle(mockPayloadContainerBundle)

            coVerify(exactly = 1) { mockTeamManager.reload(mockTeam) }
            confirmVerified(mockMeetupManager)
            confirmVerified(mockTeamManager)

            val chatEventSlot = slot<List<Message>>()
            coVerify(exactly = 1) { mockPopupNotificationManager.showPopUpNotification(TEST_LOC_NOTI_STRING, "") }
            coVerify(exactly = 1) { mockChatStorageManager.store(capture(chatEventSlot)) }

            val resultMessage = chatEventSlot.captured.first() as Message.MetaMessage
            assertMetaMessage(resultMessage)

        }

        @Test
        fun `MEMBER_DELETED for a Meetup`() = runBlockingTest {
            val action = GroupUpdate.Action.MEMBER_DELETED

            val mockPayloadContainerBundle = mockk<PayloadContainerBundle>()
            val mockPayload = GroupUpdate(TEST_MEETUP_ID, action)
            val mockMetaInfo = mockk<PayloadMetaInfo>()

            every { mockPayloadContainerBundle.payload } returns mockPayload
            every { mockPayloadContainerBundle.metaInfo } returns mockMetaInfo
            every { mockLocalizationProvider.getString(LocalizationId(R.string.notification_group_memberDeleted_title_unknown)) } returns TEST_LOC_NOTI_STRING
            every { mockLocalizationProvider.getString(LocalizationId(R.string.chat_metaInfo_other_group_memberDeleted_title_unknown)) } returns TEST_LOC_CHAT_STRING
            every { mockMetaInfo.senderId } returns TEST_SENDER_ID
            every { mockMetaInfo.timestamp } returns TEST_DATE

            groupNotificationReceiver.handlePayloadContainerBundle(mockPayloadContainerBundle)

            coVerify(exactly = 1) { mockMeetupManager.reload(mockMeetup) }
            confirmVerified(mockMeetupManager)
            confirmVerified(mockTeamManager)

            val chatEventSlot = slot<List<Message>>()
            coVerify(exactly = 1) { mockPopupNotificationManager.showPopUpNotification(TEST_LOC_NOTI_STRING, "") }
            coVerify(exactly = 1) { mockChatStorageManager.store(capture(chatEventSlot)) }

            val resultMessage = chatEventSlot.captured.first() as Message.MetaMessage
            assertMetaMessage(resultMessage)

        }

        @Test
        fun `MEMBER_UPDATED from a Meetup is not implemented`() = runBlockingTest {
            val action = GroupUpdate.Action.MEMBER_UPDATED

            val mockPayloadContainerBundle = mockk<PayloadContainerBundle>()
            val mockPayload = GroupUpdate(TEST_MEETUP_ID, action)
            val mockMetaInfo = mockk<PayloadMetaInfo>()

            every { mockPayloadContainerBundle.payload } returns mockPayload
            every { mockPayloadContainerBundle.metaInfo } returns mockMetaInfo

            Assertions.assertThrows(Exception::class.java) {
                runBlockingTest {
                    groupNotificationReceiver.handlePayloadContainerBundle(mockPayloadContainerBundle)
                }
            }

            confirmVerified(mockMeetupManager)
            confirmVerified(mockTeamManager)
        }

        @Test
        fun `MEMBER_UPDATED from a Team is not implemented`() = runBlockingTest {
            val action = GroupUpdate.Action.MEMBER_UPDATED

            val mockPayloadContainerBundle = mockk<PayloadContainerBundle>()
            val mockPayload = GroupUpdate(TEST_TEAM_ID, action)
            val mockMetaInfo = mockk<PayloadMetaInfo>()

            every { mockPayloadContainerBundle.payload } returns mockPayload
            every { mockPayloadContainerBundle.metaInfo } returns mockMetaInfo
            every { mockLocalizationProvider.getString(LocalizationId(R.string.notification_group_memberUpdated_title_unknown)) } returns TEST_LOC_NOTI_STRING
            every { mockMetaInfo.senderId } returns TEST_SENDER_ID
            every { mockMetaInfo.timestamp } returns TEST_DATE


            Assertions.assertThrows(Exception::class.java) {
                runBlockingTest {
                    groupNotificationReceiver.handlePayloadContainerBundle(mockPayloadContainerBundle)
                }
            }

            confirmVerified(mockMeetupManager)
            confirmVerified(mockTeamManager)
        }

        @Test
        fun `SETTINGS_UPDATED for a Team`() = runBlockingTest {
            val action = GroupUpdate.Action.SETTINGS_UPDATED

            val mockPayloadContainerBundle = mockk<PayloadContainerBundle>()
            val mockPayload = GroupUpdate(TEST_TEAM_ID, action)
            val mockMetaInfo = mockk<PayloadMetaInfo>()

            every { mockPayloadContainerBundle.payload } returns mockPayload
            every { mockPayloadContainerBundle.metaInfo } returns mockMetaInfo
            coEvery { mockTeamManager.reload(mockTeam) } returns mockTeam
            every { mockLocalizationProvider.getString(LocalizationId(R.string.notification_group_updated_title)) } returns TEST_LOC_NOTI_STRING
            every { mockLocalizationProvider.getString(LocalizationId(R.string.chat_metaInfo_other_group_updated_title)) } returns TEST_LOC_CHAT_STRING
            every { mockMetaInfo.senderId } returns TEST_SENDER_ID
            every { mockMetaInfo.timestamp } returns TEST_DATE

            groupNotificationReceiver.handlePayloadContainerBundle(mockPayloadContainerBundle)

            coVerify(exactly = 1) { mockTeamManager.reload(mockTeam) }
            confirmVerified(mockTeamManager)

            val chatEventSlot = slot<List<Message>>()
            coVerify(exactly = 1) { mockPopupNotificationManager.showPopUpNotification(TEST_LOC_NOTI_STRING, "") }
            coVerify(exactly = 1) { mockChatStorageManager.store(capture(chatEventSlot)) }

            val resultMessage = chatEventSlot.captured.first() as Message.MetaMessage
            assertMetaMessage(resultMessage)
        }

        @Test
        fun `SETTINGS_UPDATED for a Meetup`() = runBlockingTest {
            val action = GroupUpdate.Action.SETTINGS_UPDATED

            val mockPayloadContainerBundle = mockk<PayloadContainerBundle>()
            val mockPayload = GroupUpdate(TEST_MEETUP_ID, action)
            val mockMetaInfo = mockk<PayloadMetaInfo>()

            every { mockPayloadContainerBundle.payload } returns mockPayload
            every { mockPayloadContainerBundle.metaInfo } returns mockMetaInfo
            every { mockLocalizationProvider.getString(LocalizationId(R.string.notification_group_updated_title)) } returns TEST_LOC_NOTI_STRING
            every { mockLocalizationProvider.getString(LocalizationId(R.string.chat_metaInfo_other_group_updated_title)) } returns TEST_LOC_CHAT_STRING
            every { mockMetaInfo.senderId } returns TEST_SENDER_ID
            every { mockMetaInfo.timestamp } returns TEST_DATE

            groupNotificationReceiver.handlePayloadContainerBundle(mockPayloadContainerBundle)

            coVerify(exactly = 1) { mockMeetupManager.reload(mockMeetup) }
            confirmVerified(mockMeetupManager)

            val chatEventSlot = slot<List<Message>>()
            coVerify(exactly = 1) { mockPopupNotificationManager.showPopUpNotification(TEST_LOC_NOTI_STRING, "") }
            coVerify(exactly = 1) { mockChatStorageManager.store(capture(chatEventSlot)) }

            val resultMessage = chatEventSlot.captured.first() as Message.MetaMessage
            assertMetaMessage(resultMessage)

        }

        @Test
        fun `success without prior status`() = runBlockingTest {
            val TEST_STATE_ENABLED = true

            val mockMetaInfo: PayloadMetaInfo = mockk {
                every { senderId } returns TEST_USER_ID
                every { timestamp } returns TEST_DATE
            }

            val TEST_PAYLOAD = LocationSharingUpdate(TEST_TEAM_ID, TEST_STATE_ENABLED)
            val TEST_PAYLOAD_CONTAINER_BUNDLE = PayloadContainerBundle(Payload.PayloadType.LocationSharingUpdateV1, TEST_PAYLOAD, mockMetaInfo)

            coEvery { mockLocationSharingStorageManager.getStateOfUserInGroup(TEST_USER_ID, TEST_TEAM_ID) } returns null
            coEvery { mockGroupStorageManager.loadTeam(TEST_TEAM_ID) } returns mockk()
            coEvery { mockUserManager.getUser(TEST_USER_ID) } returns mockk()

            groupNotificationReceiver.handlePayloadContainerBundle(TEST_PAYLOAD_CONTAINER_BUNDLE)

            val expectedLocationSharingstate = LocationSharingState(TEST_USER_ID, TEST_TEAM_ID, TEST_STATE_ENABLED, TEST_DATE)

            coVerify(exactly = 1) { mockLocationSharingStorageManager.getStateOfUserInGroup(TEST_USER_ID, TEST_TEAM_ID) }
            coVerify(exactly = 1) { mockLocationSharingStorageManager.storeLocationSharingState(expectedLocationSharingstate) }
        }

        @Test
        fun `success update new status`() = runBlockingTest {
            val TEST_STATE_ENABLED = true

            val mockMetaInfo: PayloadMetaInfo = mockk {
                every { senderId } returns TEST_USER_ID
                every { timestamp } returns TEST_DATE
            }

            val TEST_PAYLOAD = LocationSharingUpdate(TEST_TEAM_ID, TEST_STATE_ENABLED)
            val TEST_PAYLOAD_CONTAINER_BUNDLE = PayloadContainerBundle(Payload.PayloadType.LocationSharingUpdateV1, TEST_PAYLOAD, mockMetaInfo)
            val TEST_PRIOR_STATUS = LocationSharingState(TEST_USER_ID, TEST_TEAM_ID, TEST_STATE_ENABLED, TEST_OLD_DATE)

            coEvery { mockLocationSharingStorageManager.getStateOfUserInGroup(TEST_USER_ID, TEST_TEAM_ID) } returns TEST_PRIOR_STATUS
            coEvery { mockGroupStorageManager.loadTeam(TEST_TEAM_ID) } returns mockk()
            coEvery { mockUserManager.getUser(TEST_USER_ID) } returns mockk()

            groupNotificationReceiver.handlePayloadContainerBundle(TEST_PAYLOAD_CONTAINER_BUNDLE)

            val expectedLocationSharingstate = LocationSharingState(TEST_USER_ID, TEST_TEAM_ID, TEST_STATE_ENABLED, TEST_DATE)

            coVerify(exactly = 1) { mockLocationSharingStorageManager.getStateOfUserInGroup(TEST_USER_ID, TEST_TEAM_ID) }
            coVerify(exactly = 1) { mockLocationSharingStorageManager.storeLocationSharingState(expectedLocationSharingstate) }
        }

        @Test
        fun `status not updated`() = runBlockingTest {
            val TEST_STATE_ENABLED = true

            val mockMetaInfo: PayloadMetaInfo = mockk {
                every { senderId } returns TEST_USER_ID
                every { timestamp } returns TEST_OLD_DATE
            }

            val TEST_PAYLOAD = LocationSharingUpdate(TEST_TEAM_ID, TEST_STATE_ENABLED)
            val TEST_PAYLOAD_CONTAINER_BUNDLE = PayloadContainerBundle(Payload.PayloadType.LocationSharingUpdateV1, TEST_PAYLOAD, mockMetaInfo)
            val TEST_PRIOR_STATUS = LocationSharingState(TEST_USER_ID, TEST_TEAM_ID, TEST_STATE_ENABLED, TEST_DATE)

            coEvery { mockLocationSharingStorageManager.getStateOfUserInGroup(TEST_USER_ID, TEST_TEAM_ID) } returns TEST_PRIOR_STATUS
            coEvery { mockGroupStorageManager.loadTeam(TEST_TEAM_ID) } returns mockk()
            coEvery { mockUserManager.getUser(TEST_USER_ID) } returns mockk()

            groupNotificationReceiver.handlePayloadContainerBundle(TEST_PAYLOAD_CONTAINER_BUNDLE)

            coVerify(exactly = 1) { mockLocationSharingStorageManager.getStateOfUserInGroup(TEST_USER_ID, TEST_TEAM_ID) }
            confirmVerified(mockLocationSharingStorageManager)
        }

        @Test
        fun `throws UnexpectedPayloadTypeException`() = runBlockingTest {
            val TEST_PAYLOAD = ResetConversation
            val TEST_PAYLOAD_CONTAINER_BUNDLE = PayloadContainerBundle(Payload.PayloadType.LocationSharingUpdateV1, TEST_PAYLOAD, mockk())

            assertThrows<UnexpectedPayloadTypeException> {
                runBlockingTest {
                    groupNotificationReceiver.handlePayloadContainerBundle(TEST_PAYLOAD_CONTAINER_BUNDLE)
                }
            }

            confirmVerified(mockLocationSharingStorageManager)
        }

        @Test
        fun `LocationSharingUpdate throws UnknownUser`() = runBlockingTest {
            val TEST_STATE_ENABLED = true

            val mockMetaInfo: PayloadMetaInfo = mockk {
                every { senderId } returns TEST_USER_ID
            }

            val TEST_PAYLOAD = LocationSharingUpdate(TEST_TEAM_ID, TEST_STATE_ENABLED)
            val TEST_PAYLOAD_CONTAINER_BUNDLE = PayloadContainerBundle(Payload.PayloadType.LocationSharingUpdateV1, TEST_PAYLOAD, mockMetaInfo)
            val TEST_PRIOR_STATUS = LocationSharingState(TEST_USER_ID, TEST_TEAM_ID, TEST_STATE_ENABLED, TEST_DATE)

            coEvery { mockLocationSharingStorageManager.getStateOfUserInGroup(TEST_USER_ID, TEST_TEAM_ID) } returns TEST_PRIOR_STATUS
            coEvery { mockGroupStorageManager.loadTeam(TEST_TEAM_ID) } returns mockk()
            coEvery { mockUserManager.getUser(TEST_USER_ID) } returns null

            assertThrows<LocationSharingException.UnknownUser> {
                runBlockingTest {
                    groupNotificationReceiver.handlePayloadContainerBundle(TEST_PAYLOAD_CONTAINER_BUNDLE)
                }
            }

            confirmVerified(mockLocationSharingStorageManager)
        }
    }

    private fun assertMetaMessage(resultMessage: Message.MetaMessage) {
        Assertions.assertEquals(TEST_TEAM_ID, resultMessage.groupId)
        Assertions.assertEquals(TEST_SENDER_ID, resultMessage.senderId)
        Assertions.assertEquals(TEST_LOC_CHAT_STRING, resultMessage.text)
        Assertions.assertEquals(TEST_DATE, resultMessage.date)
        Assertions.assertEquals(false, resultMessage.read)
        Assertions.assertEquals(MessageStatus.Success, resultMessage.status)
    }
}
