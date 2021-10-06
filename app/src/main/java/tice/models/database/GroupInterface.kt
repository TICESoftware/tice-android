package tice.models.database

import androidx.lifecycle.LiveData
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import tice.models.*

@Dao
interface GroupInterface {
    // Teams

    @Query("SELECT * FROM team")
    suspend fun getAllTeams(): List<Team>

    @Query("SELECT * FROM team")
    fun getTeamsObservable(): LiveData<List<Team>>

    @Query("SELECT * FROM team")
    fun getTeamsFlow(): Flow<List<Team>>

    @Query("SELECT * FROM team WHERE groupId=:groupId")
    suspend fun getTeam(groupId: GroupId): Team?

    @Query("SELECT * FROM team WHERE groupId=:groupId")
    fun getTeamObservable(groupId: GroupId): LiveData<Team?>

    @Query("SELECT * FROM team WHERE groupId=:groupId")
    fun getTeamFlow(groupId: GroupId): Flow<Team?>

    @Query("SELECT meetingPoint FROM team WHERE groupId=:groupId")
    fun getMeetingPointFlow(groupId: GroupId): Flow<Location?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: Team)

    @Query("UPDATE team SET tag=:tag WHERE groupId=:groupId")
    suspend fun updateTeamTag(groupId: GroupId, tag: GroupTag)

    @Query("UPDATE team SET name=:name, tag=:tag WHERE groupId=:groupId")
    suspend fun updateName(groupId: GroupId, name: String?, tag: GroupTag)

    @Query("UPDATE team SET meetupId=:meetupId WHERE groupId=:teamId")
    suspend fun updateMeetup(teamId: GroupId, meetupId: GroupId?)

    @Query("DELETE FROM team WHERE groupId=:groupId")
    suspend fun deleteTeam(groupId: GroupId)

    @Query("DELETE FROM team")
    suspend fun deleteAllTeams()

    // Meetups

    @Transaction
    @Query("SELECT * FROM team WHERE groupId=:groupId")
    suspend fun getTeamAndMeetup(groupId: GroupId): TeamAndMeetup

    @Query("SELECT * FROM meetup")
    suspend fun getAllMeetups(): List<Meetup>

    @Query("SELECT groupId FROM meetup")
    fun getAllMeetupIds(): Flow<List<GroupId>>

    @Query("SELECT * FROM meetup WHERE teamId=:teamId")
    fun getMeetupObservableForTeam(teamId: GroupId): LiveData<Meetup?>

    @Query("SELECT * FROM meetup WHERE groupId=:groupId")
    suspend fun getMeetup(groupId: GroupId): Meetup?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: Meetup)

    @Query("DELETE FROM meetup WHERE groupId=:groupId")
    suspend fun deleteMeetup(groupId: GroupId)

    @Query("DELETE FROM meetup")
    suspend fun deleteAllMeetups()

    @Query("DELETE FROM meetup WHERE teamId=:teamId")
    suspend fun deleteMeetupOfTeam(teamId: GroupId)

    // Memberships

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MembershipEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entities: List<MembershipEntity>)

    @Query("SELECT * FROM membershipEntity WHERE userId=:userId and groupId=:groupId")
    suspend fun getMembership(userId: UserId, groupId: GroupId): MembershipEntity

    @Query("SELECT * FROM membershipEntity WHERE userId=:userId and groupId=:groupId")
    suspend fun getNullableMembership(userId: UserId, groupId: GroupId): MembershipEntity?

    @Query("SELECT * FROM membershipEntity WHERE groupId=:groupId")
    suspend fun getMembershipsOfGroup(groupId: GroupId): List<MembershipEntity>

    @Query("SELECT userId FROM membershipEntity WHERE groupId=:groupId")
    fun getMembershipUserIdFlowOfGroup(groupId: GroupId): Flow<List<UserId>>

    @Query("SELECT * FROM membershipEntity WHERE userId=:userId")
    suspend fun getMembershipsOfUser(userId: UserId): List<MembershipEntity>

    @Query("SELECT DISTINCT groupId FROM membershipEntity WHERE userId=:userId AND groupId IN (SELECT groupId FROM meetup)")
    fun getMeetupIdParticipating(userId: UserId): LiveData<List<GroupId>>

    @Query("SELECT DISTINCT groupId FROM membershipEntity WHERE userId=:userId AND groupId IN (SELECT groupId FROM meetup)")
    fun getMeetupsForUser(userId: UserId): List<GroupId>

    @Query("DELETE FROM membershipEntity WHERE userId=:userId and groupId=:groupId")
    suspend fun deleteMembership(userId: UserId, groupId: GroupId)

    @Query("DELETE FROM membershipEntity WHERE groupId=:groupId")
    suspend fun deleteMembership(groupId: GroupId)

    @Delete
    suspend fun delete(entities: List<MembershipEntity>)

    @Query("DELETE FROM membershipEntity")
    suspend fun deleteAllMemberships()

    @Transaction
    suspend fun deleteAllGroupData() {
        deleteAllTeams()
        deleteAllMeetups()
        deleteAllMemberships()
    }
}
