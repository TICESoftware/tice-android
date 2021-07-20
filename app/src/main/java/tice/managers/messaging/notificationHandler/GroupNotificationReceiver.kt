package tice.managers.messaging.notificationHandler

import com.ticeapp.TICE.R
import tice.dagger.scopes.AppScope
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
import tice.models.*
import tice.models.chat.Message
import tice.models.chat.MessageStatus
import tice.models.messaging.*
import tice.utility.getLogger
import tice.utility.provider.LocalizationProviderType
import java.util.*
import javax.inject.Inject

@AppScope
class GroupNotificationReceiver @Inject constructor(
    private val postOffice: PostOfficeType,
    private val groupStorageManager: GroupStorageManagerType,
    private val teamManager: TeamManagerType,
    private val meetupManager: MeetupManagerType,
    private val popupNotificationManager: PopupNotificationManagerType,
    private val localizationProvider: LocalizationProviderType,
    private val chatStorageManager: ChatStorageManagerType,
    private val userManager: UserManagerType,
    private val locationSharingStorageManager: LocationSharingStorageManagerType
) : PayloadReceiver {
    private val logger by getLogger()

    override fun registerEnvelopeReceiver() {
        postOffice.registerEnvelopeReceiver(Payload.PayloadType.GroupUpdateV1, this)
        postOffice.registerEnvelopeReceiver(Payload.PayloadType.LocationSharingUpdateV1, this)
    }

    override suspend fun handlePayloadContainerBundle(bundle: PayloadContainerBundle) {
        when (bundle.payload) {
            is GroupUpdate -> handleGroupUpdate(bundle.payload, bundle.metaInfo)
            is LocationSharingUpdate -> handleLocationSharingUpdate(bundle.payload, bundle.metaInfo)
            else -> throw UnexpectedPayloadTypeException
        }
    }

    private suspend fun handleGroupUpdate(groupUpdate: GroupUpdate, metaInfo: PayloadMetaInfo) {
        groupStorageManager.loadTeam(groupUpdate.groupId)?.let {
            handleTeamUpdate(it, groupUpdate, metaInfo)
            return
        }

        groupStorageManager.loadMeetup(groupUpdate.groupId)?.let {
            handleMeetupUpdate(it, groupUpdate, metaInfo)
            return
        }

        logger.error("Received group update for unknown group: ${groupUpdate.groupId}.")
    }

    private suspend fun handleTeamUpdate(team: Team, update: GroupUpdate, metaInfo: PayloadMetaInfo) {
        when (update.action) {
            GroupUpdate.Action.GROUP_DELETED -> handleDeletion(team)
            GroupUpdate.Action.MEMBER_ADDED -> handleMemberAdded(team, metaInfo)
            GroupUpdate.Action.MEMBER_DELETED -> handleMemberDeleted(team, metaInfo)
            GroupUpdate.Action.MEMBER_UPDATED -> throw Exception("Not implemented")
            GroupUpdate.Action.CHILD_GROUP_CREATED -> handleChildGroupCreated(team, metaInfo)
            GroupUpdate.Action.CHILD_GROUP_DELETED -> handleChildGroupDeleted(team, metaInfo)
            GroupUpdate.Action.SETTINGS_UPDATED -> handleTeamUpdated(team, metaInfo)
        }
    }

    private suspend fun handleMeetupUpdate(
        meetup: Meetup,
        update: GroupUpdate,
        metaInfo: PayloadMetaInfo
    ) {
        when (update.action) {
            GroupUpdate.Action.GROUP_DELETED -> handleDeletion(meetup, metaInfo)
            GroupUpdate.Action.MEMBER_ADDED -> handleMemberAdded(meetup, metaInfo)
            GroupUpdate.Action.MEMBER_DELETED -> handleMemberDeleted(meetup, metaInfo)
            GroupUpdate.Action.MEMBER_UPDATED -> throw Exception("Not implemented")
            GroupUpdate.Action.SETTINGS_UPDATED -> handleMeetupUpdated(meetup, metaInfo)
            else -> throw GroupNotificationReceiverException.InvalidGroupAction
        }
    }

    private suspend fun handleDeletion(team: Team) {
        groupStorageManager.removeTeam(team.groupId)

        val locNotificationTitle = localizationProvider.getString(LocalizationId(R.string.notification_group_deleted_title))
        popupNotificationManager.showPopUpNotification(locNotificationTitle, "")
    }

    private suspend fun handleDeletion(meetup: Meetup, metaInfo: PayloadMetaInfo) {
        val team = groupStorageManager.teamOfMeetup(meetup)
        groupStorageManager.removeMeetup(meetup)
        teamManager.reload(team)

        val locNotificationTitle = localizationProvider.getString(LocalizationId(R.string.notification_meetup_deleted_title))
        val locChatTitle = localizationProvider.getString(LocalizationId(R.string.chat_metaInfo_other_meetup_deleted_title))
        chatStorageManager.store(buildMessage(team.groupId, metaInfo, locChatTitle))
        popupNotificationManager.showPopUpNotification(locNotificationTitle, "")
    }

    private suspend fun handleMemberAdded(team: Team, metaInfo: PayloadMetaInfo) {
        teamManager.reload(team)

        val locNotificationTitle = localizationProvider.getString(LocalizationId(R.string.notification_group_memberAdded_title_unknown))
        val locChatTitle = localizationProvider.getString(LocalizationId(R.string.chat_metaInfo_other_group_memberAdded_title_unknown))
        chatStorageManager.store(buildMessage(team.groupId, metaInfo, locChatTitle))
        popupNotificationManager.showPopUpNotification(locNotificationTitle, "")
    }

    private suspend fun handleMemberAdded(meetup: Meetup, metaInfo: PayloadMetaInfo) {
        meetupManager.reload(meetup)

        val locNotificationTitle = localizationProvider.getString(LocalizationId(R.string.notification_group_memberAdded_title_unknown))
        val locChatTitle = localizationProvider.getString(LocalizationId(R.string.chat_metaInfo_other_group_memberAdded_title_unknown))
        chatStorageManager.store(buildMessage(meetup.teamId, metaInfo, locChatTitle))
        popupNotificationManager.showPopUpNotification(locNotificationTitle, "")
    }

    private suspend fun handleMemberDeleted(team: Team, metaInfo: PayloadMetaInfo) {
        teamManager.reload(team)

        val locNotificationTitle = localizationProvider.getString(LocalizationId(R.string.notification_group_memberDeleted_title_unknown))
        val locChatTitle = localizationProvider.getString(LocalizationId(R.string.chat_metaInfo_other_group_memberDeleted_title_unknown))
        chatStorageManager.store(buildMessage(team.groupId, metaInfo, locChatTitle))
        popupNotificationManager.showPopUpNotification(locNotificationTitle, "")
    }

    private suspend fun handleMemberDeleted(meetup: Meetup, metaInfo: PayloadMetaInfo) {
        meetupManager.reload(meetup)

        val locNotificationTitle = localizationProvider.getString(LocalizationId(R.string.notification_group_memberDeleted_title_unknown))
        val locChatTitle = localizationProvider.getString(LocalizationId(R.string.chat_metaInfo_other_group_memberDeleted_title_unknown))
        chatStorageManager.store(buildMessage(meetup.teamId, metaInfo, locChatTitle))
        popupNotificationManager.showPopUpNotification(locNotificationTitle, "")
    }

    private suspend fun handleChildGroupCreated(team: Team, metaInfo: PayloadMetaInfo) {
        val reloadedTeam = teamManager.reload(team)
        val meetupId = reloadedTeam.meetupId ?: throw GroupNotificationReceiverException.GroupNotFound
        meetupManager.addOrReload(meetupId, reloadedTeam.groupId)

        val locNotificationTitle = localizationProvider.getString(LocalizationId(R.string.notification_meetup_created_title))
        val locChatTitle = localizationProvider.getString(LocalizationId(R.string.chat_metaInfo_other_meetup_created_title))
        chatStorageManager.store(buildMessage(reloadedTeam.groupId, metaInfo, locChatTitle))
        popupNotificationManager.showPopUpNotification(locNotificationTitle, "")
    }

    private suspend fun handleChildGroupDeleted(team: Team, metaInfo: PayloadMetaInfo) {
        teamManager.reload(team)

        val locNotificationTitle = localizationProvider.getString(LocalizationId(R.string.notification_meetup_deleted_title))
        val locChatTitle = localizationProvider.getString(LocalizationId(R.string.chat_metaInfo_other_meetup_deleted_title))
        chatStorageManager.store(buildMessage(team.groupId, metaInfo, locChatTitle))
        popupNotificationManager.showPopUpNotification(locNotificationTitle, "")
    }

    private suspend fun handleTeamUpdated(team: Team, metaInfo: PayloadMetaInfo) {
        teamManager.reload(team)

        val locNotificationTitle = localizationProvider.getString(LocalizationId(R.string.notification_group_updated_title))
        val locChatTitle = localizationProvider.getString(LocalizationId(R.string.chat_metaInfo_other_group_updated_title))
        chatStorageManager.store(buildMessage(team.groupId, metaInfo, locChatTitle))
        popupNotificationManager.showPopUpNotification(locNotificationTitle, "")
    }

    private suspend fun handleMeetupUpdated(meetup: Meetup, metaInfo: PayloadMetaInfo) {
        meetupManager.reload(meetup)

        val locNotificationTitle = localizationProvider.getString(LocalizationId(R.string.notification_group_updated_title))
        val locChatTitle = localizationProvider.getString(LocalizationId(R.string.chat_metaInfo_other_group_updated_title))
        chatStorageManager.store(buildMessage(meetup.teamId, metaInfo, locChatTitle))
        popupNotificationManager.showPopUpNotification(locNotificationTitle, "")
    }

    private fun buildMessage(teamId: GroupId, metaInfo: PayloadMetaInfo, text: String) = listOf(
        Message.MetaMessage(
            UUID.randomUUID(),
            teamId,
            metaInfo.senderId,
            metaInfo.timestamp,
            false,
            MessageStatus.Success,
            text
        )
    )

    private suspend fun handleLocationSharingUpdate(locationSharingUpdate: LocationSharingUpdate, metaInfo: PayloadMetaInfo) {
        groupStorageManager.loadTeam(locationSharingUpdate.groupId) ?: throw LocationSharingException.UnknownGroup
        userManager.getUser(metaInfo.senderId) ?: throw LocationSharingException.UnknownUser

        val newState = LocationSharingState(
            metaInfo.senderId,
            locationSharingUpdate.groupId,
            locationSharingUpdate.sharingEnabled,
            metaInfo.timestamp
        )

        locationSharingStorageManager.getStateOfUserInGroup(newState.userId, newState.groupId)?.let {
            if (it.lastUpdate > newState.lastUpdate) {
                logger.debug("Received outdated LocationSharingState update")
                return
            }
        }

        locationSharingStorageManager.storeLocationSharingState(newState)
    }
}
