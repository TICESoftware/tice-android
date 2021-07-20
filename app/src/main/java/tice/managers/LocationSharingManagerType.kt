package tice.managers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import tice.models.*
import java.util.*

interface LocationSharingManagerType {

    suspend fun getAllLocationSharingStatesOfGroup(groupId: GroupId): List<LocationSharingState>
    fun getFlowOfAllLocationSharingStatesOfGroup(groupId: GroupId): Flow<List<LocationSharingState>>
    suspend fun checkOutdatedLocationSharingStates()
    fun startOutdatedLocationSharingStateCheck()

    suspend fun getLocationUpdateFlow(userIds: Collection<UserId>, groupId: GroupId): SharedFlow<UserLocation>
    fun lastLocation(userGroupIds: UserGroupIds): Location?
}
