package tice.managers.storageManagers

import kotlinx.coroutines.flow.Flow
import tice.models.GroupId
import tice.models.LocationSharingState
import tice.models.UserId

interface LocationSharingStorageManagerType {

    fun getAllStatesFlow(): Flow<List<LocationSharingState>>

    suspend fun getAllStatesOfUser(userId: UserId): List<LocationSharingState>
    fun getAllStatesFlowOfUser(userId: UserId): Flow<List<LocationSharingState>>
    suspend fun getStateOfUserInGroup(userId: UserId, groupId: GroupId): LocationSharingState?

    fun getStateFlowOfUserInGroup(userId: UserId, groupId: GroupId): Flow<LocationSharingState?>
    suspend fun getAllUserStatesOfGroup(groupId: GroupId): List<LocationSharingState>
    fun getStatesFlowOfAllUserInGroup(groupId: GroupId): Flow<List<LocationSharingState>>

    suspend fun storeLocationSharingState(locationSharingState: LocationSharingState)
    suspend fun deleteAll(groupId: GroupId)
    suspend fun getAllStates(): List<LocationSharingState>

    suspend fun getAllStatesEnabled(sharingEnabled: Boolean): List<LocationSharingState>
}
