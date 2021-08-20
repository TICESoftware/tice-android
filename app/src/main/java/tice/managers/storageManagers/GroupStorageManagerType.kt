package tice.managers.storageManagers

import androidx.lifecycle.LiveData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import tice.models.*

interface GroupStorageManagerType {
    val teams: LiveData<List<Team>>

    suspend fun loadTeam(groupId: GroupId): Team?
    fun getTeamObservable(groupId: GroupId): LiveData<Team?>
    fun getTeamFlow(groupId: GroupId): Flow<Team?>
    fun getTeamsFlow(): Flow<List<Team>>
    suspend fun loadTeams(): Set<Team>

    suspend fun loadMeetups(): Set<Meetup>
    suspend fun loadMeetup(meetUpId: GroupId): Meetup?
    fun getMeetupObservableForTeam(teamId: GroupId): LiveData<Meetup?>
    fun getMeetingPointFlow(groupId: GroupId): Flow<Location?>
    suspend fun teamOfMeetup(meetup: Meetup): Team

    suspend fun meetupInTeam(teamId: GroupId): Meetup?

    suspend fun loadMembership(userId: UserId, groupId: GroupId): Membership
    suspend fun loadNullableMembership(userId: UserId, groupId: GroupId): Membership?
    suspend fun loadMembershipsOfGroup(groupId: GroupId): Set<Membership>
    fun getMembershipUserIdFlowOfGroup(groupId: GroupId): Flow<List<UserId>>
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
