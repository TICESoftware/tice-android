package tice.managers.group

import androidx.lifecycle.LiveData
import com.google.android.gms.maps.model.LatLng
import com.ticeapp.TICE.R
import kotlinx.serialization.json.Json
import tice.backend.BackendType
import tice.crypto.AuthManagerType
import tice.crypto.CryptoManagerType
import tice.dagger.scopes.AppScope
import tice.exceptions.BackendException
import tice.exceptions.TeamManagerException
import tice.managers.LocationManagerDelegate
import tice.managers.LocationManagerType
import tice.managers.SignedInUserManagerType
import tice.managers.UserManagerType
import tice.managers.storageManagers.ChatStorageManagerType
import tice.managers.storageManagers.GroupStorageManagerType
import tice.managers.storageManagers.LocationSharingStorageManagerType
import tice.managers.storageManagers.MembershipsDiff.Add
import tice.managers.storageManagers.MembershipsDiff.Replace
import tice.models.*
import tice.models.chat.Message
import tice.models.chat.MessageStatus
import tice.models.messaging.*
import tice.utility.getLogger
import tice.utility.provider.LocalizationProviderType
import tice.utility.safeParse
import tice.utility.serializer.MembershipSerializer
import tice.utility.uuidString
import java.lang.ref.WeakReference
import java.util.*
import javax.inject.Inject

@AppScope
class TeamManager @Inject constructor(
    private val locationManager: LocationManagerType,
    private val groupManager: GroupManagerType,
    private val meetupManager: MeetupManagerType,
    private val groupStorageManager: GroupStorageManagerType,
    private val signedInUserManager: SignedInUserManagerType,
    private val userManager: UserManagerType,
    private val cryptoManager: CryptoManagerType,
    private val authManager: AuthManagerType,
    private val backend: BackendType,
    private val chatStorageManager: ChatStorageManagerType,
    private val localizationProvider: LocalizationProviderType,
    private val locationSharingStorageManager: LocationSharingStorageManagerType
) : TeamManagerType, MeetupManagerDelegate, LocationManagerDelegate {
    val logger by getLogger()

    private val locationUpdateCollapseId = UUID.randomUUID().uuidString()

    override fun registerForDelegate() {
        meetupManager.delegate = WeakReference(this)
        locationManager.delegate = WeakReference(this)
    }

    override suspend fun createTeam(
        joinMode: JoinMode,
        permissionMode: PermissionMode,
        name: String?,
        shareLocation: Boolean,
        meetingPoint: Location?
    ): Team {
        val signedInUser = signedInUserManager.signedInUser
        val groupId: GroupId = UUID.randomUUID()
        val groupKey = cryptoManager.generateGroupKey()

        val groupSettings = GroupSettings(signedInUser.userId, name)
        val internalSettings = Team.InternalTeamSettings(meetingPoint)

        val groupSettingsData = Json.encodeToString(GroupSettings.serializer(), groupSettings).toByteArray()
        val internalSettingsData = Json.encodeToString(Team.InternalTeamSettings.serializer(), internalSettings).toByteArray()

        val encryptedGroupSettings = cryptoManager.encrypt(groupSettingsData, groupKey)
        val encryptedInternalSettings = cryptoManager.encrypt(internalSettingsData, groupKey)

        val selfSignedAdminCertificate =
            authManager.createUserSignedMembershipCertificate(signedInUser.userId, groupId, true, signedInUser.userId, signedInUser.privateSigningKey)

        val createGroupResponse = backend.createGroup(
            groupId,
            GroupType.Team,
            joinMode,
            permissionMode,
            selfSignedAdminCertificate,
            encryptedGroupSettings,
            encryptedInternalSettings,
            null
        )

        val team = Team(
            groupId,
            groupKey,
            signedInUser.userId,
            joinMode,
            permissionMode,
            createGroupResponse.groupTag,
            createGroupResponse.url,
            name,
            null,
            meetingPoint
        )

        val addUserMemberResult = groupManager.addUserMember(team, true, createGroupResponse.serverSignedAdminCertificate, listOf())
        team.tag = addUserMemberResult.second

        groupStorageManager.storeTeam(team, Add(listOf(addUserMemberResult.first)))
        locationSharingStorageManager.storeLocationSharingState(LocationSharingState(signedInUser.userId, groupId, shareLocation, Date()))

        val metaText = localizationProvider.getString(LocalizationId(R.string.chat_metaInfo_self_group_created_title))
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

        return team
    }

    override fun getTeamLiveData(groupId: GroupId): LiveData<Team?> {
        return groupStorageManager.getTeamObservable(groupId)
    }

    private suspend fun fetchTeam(
        groupId: GroupId,
        groupKey: SecretKey,
        serverSignedMembershipCertificate: Certificate,
        tag: GroupTag?
    ): Pair<Team, Set<Membership>> {
        val groupInternalsResponse = backend.getGroupInternals(groupId, serverSignedMembershipCertificate, tag)

        val settingsPlaintextData = cryptoManager.decrypt(groupInternalsResponse.encryptedSettings, groupKey)
        val settings = Json.safeParse(GroupSettings.serializer(), String(settingsPlaintextData))

        val internalSettingsPlaintextData = cryptoManager.decrypt(groupInternalsResponse.encryptedInternalSettings, groupKey)
        val internalSettings = Json.safeParse(Team.InternalTeamSettings.serializer(), String(internalSettingsPlaintextData))

        val memberships: List<Membership> = groupInternalsResponse.encryptedMemberships.map {
            val membershipPlaintextData = cryptoManager.decrypt(it, groupKey)

            val membership = Json.safeParse(MembershipSerializer, String(membershipPlaintextData))
            membership
        }

        val team = Team(
            groupId,
            groupKey,
            settings.owner,
            groupInternalsResponse.joinMode,
            groupInternalsResponse.permissionMode,
            groupInternalsResponse.groupTag,
            groupInternalsResponse.url,
            settings.name,
            groupInternalsResponse.children.firstOrNull(),
            internalSettings.meetingPoint,
        )

        memberships.forEach { userManager.getOrFetchUser(it.userId) }

        return Pair(team, memberships.toSet())
    }

    override suspend fun reload(team: Team): Team {
        logger.debug("Reloading team ${team.groupId}")

        val membership = groupStorageManager.loadMembership(signedInUserManager.signedInUser.userId, team.groupId)

        var reloadedTeam = team
        try {
            val fetchTeamResult = fetchTeam(team.groupId, team.groupKey, membership.serverSignedMembershipCertificate, team.tag)

            if (team.meetupId != fetchTeamResult.first.meetupId) team.meetupId?.let { oldId ->
                groupStorageManager.loadMeetup(oldId)?.let {
                    groupStorageManager.removeMeetup(it)
                }
            }

            groupStorageManager.storeTeam(fetchTeamResult.first, Replace(fetchTeamResult.second.toList()))
            reloadedTeam = fetchTeamResult.first
        } catch (error: BackendException.NotModified) {
            logger.debug("Team not modified.")
            return reloadedTeam
        } catch (error: BackendException.NotFound) {
            logger.info("Team ${team.groupId} not found. Removing team.")
            groupStorageManager.removeTeam(team.groupId)
            throw error
        } catch (error: BackendException.Unauthorized) {
            logger.info("User not member of team ${team.groupId}. Removing team.")
            groupStorageManager.removeTeam(team.groupId)
            throw error
        }

        reloadedTeam.meetupId?.let { meetupManager.addOrReload(it, team.groupId) }
        return reloadedTeam
    }

    override suspend fun reloadAllTeams() {
        groupStorageManager.loadTeams().forEach { reload(it) }
    }

    override suspend fun getOrFetchTeam(groupId: GroupId, groupKey: SecretKey): Team {
        groupStorageManager.loadTeam(groupId)?.let { return it }

        val groupInformationResponse = backend.getGroupInformation(groupId, null)

        val settingsPlaintextData = cryptoManager.decrypt(groupInformationResponse.encryptedSettings, groupKey)
        val settings = Json.safeParse(GroupSettings.serializer(), String(settingsPlaintextData))

        return Team(
            groupId,
            groupKey,
            settings.owner,
            groupInformationResponse.joinMode,
            groupInformationResponse.permissionMode,
            groupInformationResponse.groupTag,
            groupInformationResponse.url,
            settings.name,
            null,
            null
        )
    }

    override suspend fun join(team: Team) {
        val signedInUser = signedInUserManager.signedInUser

        if (groupStorageManager.isMember(signedInUser.userId, team.groupId)) {
            throw TeamManagerException.UserAlreadyMember
        }

        val selfSignedMembershipCertificate =
            authManager.createUserSignedMembershipCertificate(signedInUser.userId, team.groupId, false, signedInUser.userId, signedInUser.privateSigningKey)

        val joinGroupResponse = requestAndHandleGroupOutdated(team) {
            backend.joinGroup(team.groupId, team.tag, selfSignedMembershipCertificate)
        }

        val fetchTeamResult = fetchTeam(team.groupId, team.groupKey, joinGroupResponse.serverSignedMembershipCertificate, null)
        val updatedTeam = fetchTeamResult.first

        groupStorageManager.storeTeam(updatedTeam, Replace((fetchTeamResult.second).toList()))

        val notificationRecipients = groupManager.notificationRecipients(team.groupId, MessagePriority.Alert)
        val addUserMemberResult =
            groupManager.addUserMember(updatedTeam, false, joinGroupResponse.serverSignedMembershipCertificate, notificationRecipients)

        updatedTeam.tag = addUserMemberResult.second
        groupStorageManager.storeTeam(updatedTeam, Add(listOf(addUserMemberResult.first)))

        val metaText = localizationProvider.getString(LocalizationId(R.string.chat_metaInfo_self_group_joined_title))
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

        updatedTeam.meetupId?.let { meetupManager.addOrReload(it, updatedTeam.groupId) }
    }

    override suspend fun leave(team: Team) {
        requestAndHandleGroupOutdated(team) {
            groupStorageManager.meetupInTeam(team.groupId)?.let {
                if (groupStorageManager.isMember(signedInUserManager.signedInUser.userId, it.groupId)) {
                    meetupManager.leave(it)
                }
            }

            val notificationRecipients = groupManager.notificationRecipients(team.groupId, MessagePriority.Alert)
            groupManager.leave(team, notificationRecipients)
        }

        groupStorageManager.removeTeam(team.groupId)
    }

    override suspend fun delete(team: Team) {
        if (groupStorageManager.meetupInTeam(team.groupId) != null) {
            throw TeamManagerException.MeetupRunning
        }

        val membership = groupStorageManager.loadMembership(signedInUserManager.signedInUser.userId, team.groupId)

        if (!membership.admin) {
            throw TeamManagerException.PermissionDenied
        }

        val notificationRecipients = groupManager.notificationRecipients(team.groupId, MessagePriority.Alert)

        requestAndHandleGroupOutdated(team) {
            backend.deleteGroup(team.groupId, membership.serverSignedMembershipCertificate, team.tag, notificationRecipients)
        }

        groupStorageManager.removeTeam(team.groupId)
    }

    override suspend fun setTeamName(team: Team, name: String?) {
        val settings = GroupSettings(team.owner, name)
        val settingsPlaintextData = Json.encodeToString(GroupSettings.serializer(), settings).toByteArray()
        val encryptedSettings = cryptoManager.encrypt(settingsPlaintextData, team.groupKey)

        val membership = groupStorageManager.loadMembership(signedInUserManager.signedInUser.userId, team.groupId)
        val notificationRecipients = groupManager.notificationRecipients(team.groupId, MessagePriority.Alert)

        val updatedGroupTagResponse = requestAndHandleGroupOutdated(team) {
            backend.updateGroupSettings(
                team.groupId,
                encryptedSettings,
                membership.serverSignedMembershipCertificate,
                team.tag,
                notificationRecipients
            )
        }

        val newTeam = Team(
            team.groupId,
            team.groupKey,
            team.owner,
            team.joinMode,
            team.permissionMode,
            updatedGroupTagResponse.groupTag,
            team.url,
            name,
            team.meetupId,
            team.meetingPoint
        )

        groupStorageManager.storeTeam(newTeam, null)

        val metaText = localizationProvider.getString(LocalizationId(R.string.chat_metaInfo_self_group_updated_title))
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

    override suspend fun setMeetingPoint(meetingPoint: LatLng, team: Team) {
        val membership = groupStorageManager.loadMembership(signedInUserManager.signedInUser.userId, team.groupId)

        val location = Location(meetingPoint.latitude, meetingPoint.longitude, 0.0, 0.0f, 0.0f, Date())
        val internalSettings = Team.InternalTeamSettings(location)

        val internalSettingsData = Json.encodeToString(Team.InternalTeamSettings.serializer(), internalSettings).toByteArray()
        val encryptedInternalSettings = cryptoManager.encrypt(internalSettingsData, team.groupKey)

        val notificationRecipient = groupManager.notificationRecipients(team.groupId, MessagePriority.Alert)

        val updatedTagResponse = requestAndHandleGroupOutdated(team) {
            backend.updateGroupInternalSettings(
                team.groupId,
                encryptedInternalSettings,
                membership.serverSignedMembershipCertificate,
                team.tag,
                notificationRecipient.toList()
            )
        }

        team.tag = updatedTagResponse.groupTag
        team.meetingPoint = location

        groupStorageManager.storeTeam(team, null)
    }

    override suspend fun setLocationSharing(team: Team, enabled: Boolean) {
        val locationSharingState = LocationSharingState(signedInUserManager.signedInUser.userId, team.groupId, enabled, Date())

        locationSharingStorageManager.storeLocationSharingState(locationSharingState)

        val payload = LocationSharingUpdate(team.groupId, enabled)
        val payloadContainer = PayloadContainer(Payload.PayloadType.LocationSharingUpdateV1, payload)

        groupManager.send(payloadContainer, team, null, MessagePriority.Alert)
    }

    override suspend fun processLocationUpdate(location: Location) {
        try {
            sendLocationUpdate(location)
        } catch (e: Exception) {
            logger.error("Sending location update failed", e)
        }
    }

    private suspend fun sendLocationUpdate(location: Location) {
        locationSharingStorageManager.getAllStatesOfUser(signedInUserManager.signedInUser.userId).forEach { locationSharingState ->
            if (locationSharingState.sharingEnabled) {
                groupStorageManager.loadTeam(locationSharingState.groupId)?.let {
                    val payloadContainer = PayloadContainer(Payload.PayloadType.LocationUpdateV2, LocationUpdateV2(location, it.groupId))

                    groupManager.send(payloadContainer, it, locationUpdateCollapseId, MessagePriority.Deferred)
                }
            }
        }
    }

    private suspend fun <T> requestAndHandleGroupOutdated(team: Team, block: suspend () -> T): T {
        try {
            return block()
        } catch (e: BackendException.GroupOutdated) {
            reload(team)
            throw e
        }
    }
}
