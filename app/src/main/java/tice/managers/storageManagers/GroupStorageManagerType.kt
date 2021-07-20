package tice.managers.storageManagers

import androidx.lifecycle.LiveData
import tice.models.*

interface GroupStorageManagerType {
    val teams: LiveData<List<Team>>

    suspend fun loadTeam(groupId: GroupId): Team?
    fun getTeamObservable(groupId: GroupId): LiveData<Team?>
    suspend fun loadTeams(): Set<Team>

    suspend fun loadMeetups(): Set<Meetup>
    suspend fun loadMeetup(meetUpId: GroupId): Meetup?
    fun getMeetupObservableForTeam(teamId: GroupId): LiveData<Meetup?>
    suspend fun teamOfMeetup(meetup: Meetup): Team

    suspend fun meetupInTeam(teamId: GroupId): Meetup?

    suspend fun loadMembership(userId: UserId, groupId: GroupId): Membership
    suspend fun loadNullableMembership(userId: UserId, groupId: GroupId): Membership?
    suspend fun loadMembershipsOfGroup(groupId: GroupId): Set<Membership>
    suspend fun isUserInMeetups(userId: UserId): Boolean
    fun getMeetupIdParticipating(userId: UserId): LiveData<List<GroupId>>
    suspend fun isMember(userId: UserId, groupId: GroupId): Boolean

    suspend fun members(groupId: GroupId): Set<Member>
    suspend fun loadUser(membership: Membership): User

    suspend fun storeTeam(team: Team, membershipsDiff: MembershipsDiff?)
    suspend fun storeMeetup(meetup: Meetup, membershipsDiff: MembershipsDiff?)
    suspend fun removeTeam(teamId: GroupId)
    suspend fun removeMeetup(meetup: Meetup)

    suspend fun deleteAllData()
    suspend fun loadMembershipsOfUser(userId: UserId): Set<Membership>
}
