package tice.managers.group

import com.google.android.gms.maps.model.LatLng
import tice.models.*
import tice.models.messaging.MessagePriority
import java.lang.ref.WeakReference

interface MeetupManagerType {
    var delegate: WeakReference<MeetupManagerDelegate>?

    suspend fun participationStatus(meetup: Meetup): ParticipationStatus
    suspend fun createMeetup(team: Team, location: Location?, joinMode: JoinMode, permissionMode: PermissionMode): Meetup
    suspend fun join(meetup: Meetup)
    suspend fun leave(meetup: Meetup)
    suspend fun delete(meetup: Meetup)
    suspend fun deleteGroupMember(membership: Membership, meetup: Meetup)
    suspend fun setMeetingPoint(meetingPoint: LatLng, meetup: Meetup)

    suspend fun reload(meetup: Meetup)
    suspend fun addOrReload(meetupId: GroupId, teamId: GroupId)

    suspend fun meetupState(team: Team): MeetupState
    suspend fun meetupState(meetup: Meetup): MeetupState
    suspend fun meetupNotificationRecipients(
        meetup: Meetup,
        meetupPriority: MessagePriority,
        teamPriority: MessagePriority
    ): Set<NotificationRecipient>
}
