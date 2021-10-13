package tice.managers.group

import com.ticeapp.TICE.R
import kotlinx.serialization.json.Json
import tice.backend.BackendType
import tice.crypto.AuthManagerType
import tice.crypto.CryptoManagerType
import tice.dagger.scopes.AppScope
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
import tice.utility.getLogger
import tice.utility.provider.LocalizationProviderType
import tice.utility.safeParse
import tice.utility.serializer.MembershipSerializer
import java.lang.ref.WeakReference
import java.util.*
import javax.inject.Inject

@AppScope
class MeetupManager @Inject constructor(
    private val groupManager: GroupManagerType,
    private val groupStorageManager: GroupStorageManagerType,
    private val cryptoManager: CryptoManagerType,
    private val authManager: AuthManagerType,
    private val signedInUserManager: SignedInUserManagerType,
    private val backend: BackendType,
    private val chatStorageManager: ChatStorageManagerType,
    private val localizationProvider: LocalizationProviderType
) : MeetupManagerType {
    val logger by getLogger()

    override var delegate: WeakReference<MeetupManagerDelegate>? = null

    override suspend fun participationStatus(meetup: Meetup): ParticipationStatus {
        val signedInUser = signedInUserManager.signedInUser

        return if (groupStorageManager.isMember(signedInUser.userId, meetup.groupId)) {
            val membership = groupStorageManager.loadMembership(signedInUser.userId, meetup.groupId)
            if (membership.admin) ParticipationStatus.ADMIN else ParticipationStatus.MEMBER
        } else {
            ParticipationStatus.NOT_PARTICIPATING
        }
    }

    override suspend fun createMeetup(team: Team, location: Location?, joinMode: JoinMode, permissionMode: PermissionMode): Meetup {
        if (groupStorageManager.meetupInTeam(team.groupId) != null) {
            throw MeetupManagerException.MeetupAlreadyRunning
        }

        val groupId = UUID.randomUUID()
        val groupKey = cryptoManager.generateGroupKey()
        val signedInUser = signedInUserManager.signedInUser

        val groupSettings = GroupSettings(signedInUser.userId)
        val internalSettings = Meetup.InternalSettings(location)

        val groupSettingsData = Json.encodeToString(GroupSettings.serializer(), groupSettings).toByteArray()
        val internalSettingsData = Json.encodeToString(Meetup.InternalSettings.serializer(), internalSettings).toByteArray()

        val encryptedGroupSettings = cryptoManager.encrypt(groupSettingsData, groupKey)
        val encryptedInternalSettings = cryptoManager.encrypt(internalSettingsData, groupKey)

        val selfSignedAdminCertificate = authManager.createUserSignedMembershipCertificate(
            signedInUser.userId,
            groupId,
            true,
            signedInUser.userId,
            signedInUser.privateSigningKey
        )

        val parentEncryptedChildGroupKey = cryptoManager.encrypt(groupKey, team.groupKey)
        val parentGroup = ParentGroup(team.groupId, parentEncryptedChildGroupKey)

        val createGroupResponse = requestAndHandleGroupOutdated(team) {
            backend.createGroup(
                groupId,
                GroupType.Meetup,
                joinMode,
                permissionMode,
                selfSignedAdminCertificate,
                encryptedGroupSettings,
                encryptedInternalSettings,
                parentGroup
            )
        }

        val meetup = Meetup(
            groupId,
            groupKey,
            signedInUser.userId,
            joinMode,
            permissionMode,
            createGroupResponse.groupTag,
            team.groupId,
            location
        )

        val addUserMemberResult = groupManager.addUserMember(
            meetup,
            true,
            createGroupResponse.serverSignedAdminCertificate,
            emptySet()
        )

        meetup.tag = addUserMemberResult.second

        groupStorageManager.storeMeetup(meetup, Add(listOf(addUserMemberResult.first)))

        groupManager.sendGroupUpdateNotification(team, GroupUpdate.Action.CHILD_GROUP_CREATED)

        val metaText = localizationProvider.getString(LocalizationId(R.string.chat_metaInfo_self_meetup_created_title))
        val metaChatInfo = Message.MetaMessage(
            UUID.randomUUID(),
            meetup.teamId,
            signedInUserManager.signedInUser.userId,
            Date(),
            true,
            MessageStatus.Success,
            metaText
        )
        chatStorageManager.store(listOf(metaChatInfo))

        delegate?.get()?.reload(team)

        return meetup
    }

    override suspend fun meetupState(team: Team): MeetupState {
        val meetup = groupStorageManager.meetupInTeam(team.groupId) ?: return MeetupState.None
        return meetupState(meetup)
    }

    override suspend fun meetupState(meetup: Meetup): MeetupState {
        return if (groupStorageManager.isMember(signedInUserManager.signedInUser.userId, meetup.groupId)) {
            MeetupState.Participating(meetup)
        } else {
            MeetupState.Invited(meetup)
        }
    }

    private suspend fun fetchMeetup(
        groupId: GroupId,
        decryptionKey: MeetupDecryptionKey,
        serverSignedMembershipCertificate: Certificate,
        tag: GroupTag?
    ): Pair<Meetup, Set<Membership>> {
        val groupInternalsResponse = backend.getGroupInternals(groupId, serverSignedMembershipCertificate, tag)

        val groupKey = when (decryptionKey) {
            is MeetupDecryptionKey.MeetupKey -> decryptionKey.key
            is MeetupDecryptionKey.ParentKey -> {
                val encryptedGroupKey = groupInternalsResponse.parentEncryptedGroupKey ?: throw MeetupManagerException.ParentKeyMissing
                cryptoManager.decrypt(encryptedGroupKey, decryptionKey.key)
            }
        }

        val settingsPlaintextData = cryptoManager.decrypt(groupInternalsResponse.encryptedSettings, groupKey)
        val settings = Json.safeParse(GroupSettings.serializer(), String(settingsPlaintextData))

        val internalSettingsPlaintextData = cryptoManager.decrypt(groupInternalsResponse.encryptedInternalSettings, groupKey)
        val internalSettings = Json.safeParse(Meetup.InternalSettings.serializer(), String(internalSettingsPlaintextData))

        val memberships = groupInternalsResponse.encryptedMemberships.map {
            val membershipPlaintextData = cryptoManager.decrypt(it, groupKey)
            Json.safeParse(MembershipSerializer, String(membershipPlaintextData))
        }

        val teamId = groupInternalsResponse.parentGroupId ?: throw MeetupManagerException.TeamNotFound

        val meetup = Meetup(
            groupId,
            groupKey,
            settings.owner,
            groupInternalsResponse.joinMode,
            groupInternalsResponse.permissionMode,
            groupInternalsResponse.groupTag,
            teamId,
            internalSettings.location
        )

        return Pair(meetup, memberships.toSet())
    }

    override suspend fun addOrReload(meetupId: GroupId, teamId: GroupId) {
        groupStorageManager.loadMeetup(meetupId)?.let { reload(it); return }

        val team = groupStorageManager.loadTeam(teamId) ?: throw MeetupManagerException.TeamNotFound

        val teamMembership = groupStorageManager.loadMembership(signedInUserManager.signedInUser.userId, teamId)
        val fetchMeetupResult =
            fetchMeetup(meetupId, MeetupDecryptionKey.ParentKey(team.groupKey), teamMembership.serverSignedMembershipCertificate, null)

        groupStorageManager.storeMeetup(fetchMeetupResult.first, Replace(fetchMeetupResult.second.toList()))
    }

    override suspend fun reload(meetup: Meetup) {
        val signedInUser = signedInUserManager.signedInUser

        val membership = if (groupStorageManager.isMember(signedInUser.userId, meetup.groupId)) {
            groupStorageManager.loadMembership(signedInUser.userId, meetup.groupId)
        } else {
            groupStorageManager.loadMembership(signedInUser.userId, meetup.teamId)
        }

        try {
            val fetchMeetupResult = fetchMeetup(
                meetup.groupId,
                MeetupDecryptionKey.MeetupKey(meetup.groupKey),
                membership.serverSignedMembershipCertificate,
                meetup.tag
            )

            groupStorageManager.storeMeetup(fetchMeetupResult.first, Replace(fetchMeetupResult.second.toList()))
        } catch (error: BackendException.NotModified) {
            logger.debug("Meetup not modified.")
        } catch (error: BackendException.NotFound) {
            logger.info("Meetup ${meetup.groupId} not found. Removing meetup.")
            groupStorageManager.removeMeetup(meetup)
            throw error
        } catch (error: BackendException.Unauthorized) {
            logger.info("User not member of meetup ${meetup.groupId}. Removing meetup.")
            groupStorageManager.removeMeetup(meetup)
            throw error
        }
    }

    override suspend fun join(meetup: Meetup) {
        val signedInUser = signedInUserManager.signedInUser
        val selfSignedMembershipCertificate = authManager.createUserSignedMembershipCertificate(
            signedInUser.userId,
            meetup.groupId,
            false,
            signedInUser.userId,
            signedInUser.privateSigningKey
        )

        val joinGroupResponse = requestAndHandleGroupOutdated(meetup) {
            backend.joinGroup(meetup.groupId, meetup.tag, selfSignedMembershipCertificate)
        }

        val notificationRecipients = meetupNotificationRecipients(meetup, MessagePriority.Alert, MessagePriority.Deferred)
        val addUserMemberResult = groupManager.addUserMember(
            meetup,
            false,
            joinGroupResponse.serverSignedMembershipCertificate,
            notificationRecipients
        )

        meetup.tag = addUserMemberResult.second

        groupStorageManager.storeMeetup(meetup, Add(listOf(addUserMemberResult.first)))

        val metaText = localizationProvider.getString(LocalizationId(R.string.chat_metaInfo_self_meetup_joined_title))
        val metaChatInfo = Message.MetaMessage(
            UUID.randomUUID(),
            meetup.teamId,
            signedInUserManager.signedInUser.userId,
            Date(),
            true,
            MessageStatus.Success,
            metaText
        )
        chatStorageManager.store(listOf(metaChatInfo))
    }

    override suspend fun leave(meetup: Meetup) {
        val team = groupStorageManager.teamOfMeetup(meetup)

        val notificationRecipients = meetupNotificationRecipients(meetup, MessagePriority.Alert, MessagePriority.Deferred)
        val updatedTag = requestAndHandleGroupOutdated(team) {
            groupManager.leave(meetup, notificationRecipients)
        }
        val ownMembership = groupStorageManager.loadMembership(signedInUserManager.signedInUser.userId, meetup.groupId)

        meetup.tag = updatedTag

        groupStorageManager.storeMeetup(meetup, Remove(listOf(ownMembership)))

        val metaText = localizationProvider.getString(LocalizationId(R.string.chat_metaInfo_self_meetup_left_title))
        val metaChatInfo = Message.MetaMessage(
            UUID.randomUUID(),
            team.groupId,
            signedInUserManager.signedInUser.userId,
            Date(),
            true,
            MessageStatus.Success,
            metaText
        )
        chatStorageManager.store(listOf(metaChatInfo))
    }

    override suspend fun delete(meetup: Meetup) {
        val signedInUser = signedInUserManager.signedInUser
        val membership = groupStorageManager.loadMembership(signedInUser.userId, meetup.groupId)

        if (!membership.admin) {
            throw MeetupManagerException.PermissionDenied
        }

        val notificationRecipients = meetupNotificationRecipients(meetup, MessagePriority.Alert, MessagePriority.Deferred)
        val team = groupStorageManager.teamOfMeetup(meetup)

        requestAndHandleGroupOutdated(team) {
            backend.deleteGroup(meetup.groupId, membership.serverSignedMembershipCertificate, meetup.tag, notificationRecipients.toList())
        }

        groupStorageManager.removeMeetup(meetup)

        groupManager.sendGroupUpdateNotification(team, GroupUpdate.Action.CHILD_GROUP_DELETED)

        val metaText = localizationProvider.getString(LocalizationId(R.string.chat_metaInfo_self_meetup_deleted_title))
        val metaChatInfo = Message.MetaMessage(
            UUID.randomUUID(),
            meetup.teamId,
            signedInUserManager.signedInUser.userId,
            Date(),
            true,
            MessageStatus.Success,
            metaText
        )
        chatStorageManager.store(listOf(metaChatInfo))

        delegate?.get()?.reload(team)
    }

    override suspend fun deleteGroupMember(membership: Membership, meetup: Meetup) {
        val signedInUserMembership = groupStorageManager.loadMembership(signedInUserManager.signedInUser.userId, meetup.groupId)

        val notificationRecipients = meetupNotificationRecipients(meetup, MessagePriority.Alert, MessagePriority.Deferred)
        val updatedTag = requestAndHandleGroupOutdated(meetup) {
            groupManager.deleteGroupMember(
                membership,
                meetup,
                signedInUserMembership.serverSignedMembershipCertificate,
                notificationRecipients
            )
        }

        meetup.tag = updatedTag

        val metaText = localizationProvider.getString(LocalizationId(R.string.chat_metaInfo_self_meetup_deleteGroupMember_title_unknown))
        val metaChatInfo = Message.MetaMessage(
            UUID.randomUUID(),
            meetup.teamId,
            signedInUserManager.signedInUser.userId,
            Date(),
            true,
            MessageStatus.Success,
            metaText
        )
        chatStorageManager.store(listOf(metaChatInfo))

        groupStorageManager.storeMeetup(meetup, Remove(listOf(membership)))
    }

    override suspend fun setMeetingPoint(meetingPoint: Coordinates, meetup: Meetup) {
        val membership = groupStorageManager.loadMembership(signedInUserManager.signedInUser.userId, meetup.groupId)

        val location = Location(meetingPoint.latitude, meetingPoint.longitude, 0.0, 0.0f, 0.0f, Date())
        val internalSettings = Meetup.InternalSettings(location)

        val internalSettingsData = Json.encodeToString(Meetup.InternalSettings.serializer(), internalSettings).toByteArray()
        val encryptedInternalSettings = cryptoManager.encrypt(internalSettingsData, meetup.groupKey)

        val notificationRecipients = meetupNotificationRecipients(meetup, MessagePriority.Alert, MessagePriority.Deferred)

        val updatedTagResponse = requestAndHandleGroupOutdated(meetup) {
            backend.updateGroupInternalSettings(
                meetup.groupId,
                encryptedInternalSettings,
                membership.serverSignedMembershipCertificate,
                meetup.tag,
                notificationRecipients.toList()
            )
        }

        meetup.tag = updatedTagResponse.groupTag
        meetup.meetingPoint = location

        groupStorageManager.storeMeetup(meetup, null)
    }

    override suspend fun meetupNotificationRecipients(
        meetup: Meetup,
        meetupPriority: MessagePriority,
        teamPriority: MessagePriority
    ): Set<NotificationRecipient> {
        val signedInUser = signedInUserManager.signedInUser

        val meetupMemberships = groupStorageManager.loadMembershipsOfGroup(meetup.groupId).filter { it.userId != signedInUser.userId }

        val meetupIds = meetupMemberships.map { it.userId }

        val teamMemberships = groupStorageManager.loadMembershipsOfGroup(meetup.teamId).filter { it.userId != signedInUser.userId }
        val filteredTeamMemberships = teamMemberships.filter { !meetupIds.contains(it.userId) }

        val meetupNotificationReceipt =
            meetupMemberships.map { NotificationRecipient(it.userId, it.serverSignedMembershipCertificate, meetupPriority) }.toSet()
        val teamNotificationRecipient =
            filteredTeamMemberships.map { NotificationRecipient(it.userId, it.serverSignedMembershipCertificate, teamPriority) }.toSet()
        return meetupNotificationReceipt.plus(teamNotificationRecipient)
    }

    private sealed class MeetupDecryptionKey {
        abstract val key: SecretKey

        data class MeetupKey(override val key: SecretKey) : MeetupDecryptionKey()
        data class ParentKey(override val key: SecretKey) : MeetupDecryptionKey()
    }

    private suspend fun <T> requestAndHandleGroupOutdated(group: Group, block: suspend () -> T): T {
        try {
            return block()
        } catch (e: BackendException.GroupOutdated) {
            val team = when (group) {
                is Meetup -> groupStorageManager.teamOfMeetup(group)
                is Team -> group
            }

            delegate?.get()?.reload(team)
            throw e
        }
    }
}
