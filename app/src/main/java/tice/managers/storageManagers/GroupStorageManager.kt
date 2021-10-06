package tice.managers.storageManagers

import androidx.lifecycle.LiveData
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import tice.dagger.scopes.AppScope
import tice.managers.storageManagers.MembershipsDiff.*
import tice.models.*
import tice.models.database.MembershipEntity
import tice.models.database.databaseEntity
import javax.inject.Inject

sealed class MembershipsDiff {
    abstract val memberships: List<Membership>

    data class Add(override val memberships: List<Membership>) : MembershipsDiff()
    data class Remove(override val memberships: List<Membership>) : MembershipsDiff()
    data class Replace(override val memberships: List<Membership>) : MembershipsDiff()
}

@AppScope
class GroupStorageManager @Inject constructor(private val db: AppDatabase) : GroupStorageManagerType {

    private val groupInterface = db.groupInterface()
    private val userInterface = db.userInterface()
    private val chatInterface = db.chatMessageInterface()
    private val locationSharingInterface = db.locationSharingInterface()

    override val teams: LiveData<List<Team>>
        get() = groupInterface.getTeamsObservable()

    override suspend fun loadTeam(groupId: GroupId): Team? {
        return groupInterface.getTeam(groupId)
    }

    override fun getTeamObservable(groupId: GroupId): LiveData<Team?> {
        return groupInterface.getTeamObservable(groupId)
    }

    override fun getTeamFlow(groupId: GroupId): Flow<Team?> {
        return groupInterface.getTeamFlow(groupId)
    }

    override fun getTeamsFlow(): Flow<List<Team>> {
        return groupInterface.getTeamsFlow()
    }

    override fun getMeetingPointFlow(groupId: GroupId): Flow<Location?> {
        return groupInterface.getMeetingPointFlow(groupId)
    }

    override suspend fun loadTeams(): Set<Team> {
        return groupInterface.getAllTeams().toSet()
    }

    override suspend fun loadMeetups(): Set<Meetup> {
        return groupInterface.getAllMeetups().toSet()
    }

    override suspend fun loadMeetup(meetUpId: GroupId): Meetup? {
        return groupInterface.getMeetup(meetUpId)
    }

    override fun getMeetupObservableForTeam(teamId: GroupId): LiveData<Meetup?> {
        return groupInterface.getMeetupObservableForTeam(teamId)
    }

    override suspend fun teamOfMeetup(meetup: Meetup): Team {
        return groupInterface.getTeam(meetup.teamId)!!
    }

    override suspend fun meetupInTeam(teamId: GroupId): Meetup? {
        return groupInterface.getTeamAndMeetup(teamId).meetup
    }

    private suspend fun applyMembershipsDiff(groupId: GroupId, diff: MembershipsDiff) {
        val membershipEntities = diff.memberships.map(Membership::databaseEntity)
        when (diff) {
            is Add -> groupInterface.insert(membershipEntities)
            is Remove -> groupInterface.delete(membershipEntities)
            is Replace -> {
                val existingMemberships = groupInterface.getMembershipsOfGroup(groupId)
                val invalidMemberships = existingMemberships - membershipEntities
                groupInterface.delete(invalidMemberships)
                groupInterface.insert(membershipEntities)
            }
        }
    }

    override suspend fun storeTeam(team: Team, membershipsDiff: MembershipsDiff?) {
        db.withTransaction {
            if (team.meetupId == null) {
                groupInterface.deleteMeetupOfTeam(team.groupId)
            }

            groupInterface.insert(team)
            membershipsDiff?.let { applyMembershipsDiff(team.groupId, membershipsDiff) }
        }
    }

    override suspend fun storeMeetup(meetup: Meetup, membershipsDiff: MembershipsDiff?) {
        db.withTransaction {
            groupInterface.insert(meetup)
            membershipsDiff?.let { applyMembershipsDiff(meetup.groupId, membershipsDiff) }
        }
    }

    override suspend fun removeMeetup(meetup: Meetup) {
        db.withTransaction {
            groupInterface.deleteMembership(meetup.groupId)
            groupInterface.updateMeetup(meetup.teamId, null)
        }
    }

    override suspend fun removeTeam(teamId: GroupId) {
        db.withTransaction {
            groupInterface.deleteMeetupOfTeam(teamId)
            groupInterface.deleteMembership(teamId)
            groupInterface.deleteTeam(teamId)
            chatInterface.deleteAll(teamId)
            locationSharingInterface.deleteAll(teamId)
        }
    }

    override suspend fun loadMembership(userId: UserId, groupId: GroupId): Membership {
        return groupInterface.getMembership(userId, groupId).membership()
    }

    override suspend fun loadNullableMembership(userId: UserId, groupId: GroupId): Membership? {
        return groupInterface.getNullableMembership(userId, groupId)?.membership()
    }

    override suspend fun loadMembershipsOfGroup(groupId: GroupId): Set<Membership> {
        return groupInterface.getMembershipsOfGroup(groupId).map(MembershipEntity::membership).toSet()
    }

    override fun getMembershipUserIdFlowOfGroup(groupId: GroupId): Flow<List<UserId>> {
        return groupInterface.getMembershipUserIdFlowOfGroup(groupId)
    }

    override suspend fun loadMembershipsOfUser(userId: UserId): Set<Membership> {
        return groupInterface.getMembershipsOfUser(userId).map(MembershipEntity::membership).toSet()
    }

    override fun getMeetupIdParticipating(userId: UserId): LiveData<List<GroupId>> {
        return groupInterface.getMeetupIdParticipating(userId)
    }

    override suspend fun isUserInMeetups(userId: UserId): Boolean {
        val meetups = groupInterface.getMeetupsForUser(userId)
        return meetups.isNotEmpty()
    }

    override suspend fun isMember(userId: UserId, groupId: GroupId): Boolean {
        return try {
            loadMembership(userId, groupId)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun loadUser(membership: Membership): User {
        return userInterface.get(membership.userId)
    }

    override suspend fun members(groupId: GroupId): Set<Member> {
        val memberships = loadMembershipsOfGroup(groupId)

        return memberships.map { Member(userInterface.get(it.userId), it) }.toSet()
    }

    override suspend fun deleteAllData() {
        groupInterface.deleteAllGroupData()
    }
}
