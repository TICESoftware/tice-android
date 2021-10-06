package tice.managers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import tice.models.*

interface LocationSharingManagerType {

    val memberLocationFlow: SharedFlow<UserLocation>

    suspend fun getAllLocationSharingStatesOfGroup(groupId: GroupId): List<LocationSharingState>
    fun getFlowOfAllLocationSharingStatesOfGroup(groupId: GroupId): Flow<List<LocationSharingState>>
    suspend fun checkOutdatedLocationSharingStates()
    fun startOutdatedLocationSharingStateCheck()
    fun lastLocation(userGroupIds: UserGroupIds): Location?
}
