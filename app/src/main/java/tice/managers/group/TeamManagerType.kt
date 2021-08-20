package tice.managers.group

import androidx.lifecycle.LiveData
import tice.models.*

interface TeamManagerType {

    suspend fun createTeam(
        joinMode: JoinMode,
        permissionMode: PermissionMode,
        name: String?,
        shareLocation: Boolean,
        meetingPoint: Location?
    ): Team

    fun getTeamLiveData(groupId: GroupId): LiveData<Team?>
    suspend fun getOrFetchTeam(groupId: GroupId, groupKey: SecretKey): Team
    suspend fun join(team: Team)
    suspend fun leave(team: Team)
    suspend fun delete(team: Team)
    suspend fun setTeamName(team: Team, name: String?)

    suspend fun reload(team: Team): Team
    suspend fun reloadAllTeams()
    fun registerForDelegate()
    suspend fun setMeetingPoint(meetingPoint: Coordinates, team: Team)
    suspend fun setLocationSharing(team: Team, enabled: Boolean)
}
