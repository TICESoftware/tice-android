package tice.managers.storageManagers

import kotlinx.coroutines.flow.Flow
import tice.dagger.scopes.AppScope
import tice.models.GroupId
import tice.models.LocationSharingState
import tice.models.UserId
import javax.inject.Inject

@AppScope
class LocationSharingStorageManager @Inject constructor(
    appDatabase: AppDatabase
) : LocationSharingStorageManagerType {
    private val locationSharingInterface = appDatabase.locationSharingInterface()

    override suspend fun getAllStates(): List<LocationSharingState> {
        return locationSharingInterface.getAllStates()
    }

    override suspend fun getAllStatesEnabled(sharingEnabled: Boolean): List<LocationSharingState> {
        return locationSharingInterface.getAllStatesEnabled(sharingEnabled)
    }

    override fun getAllStatesFlow(): Flow<List<LocationSharingState>> {
        return locationSharingInterface.getAllStatesFlow()
    }

    override suspend fun getAllStatesOfUser(userId: UserId): List<LocationSharingState> {
        return locationSharingInterface.getAllStatesOfUser(userId)
    }

    override fun getAllStatesFlowOfUser(userId: UserId): Flow<List<LocationSharingState>> {
        return locationSharingInterface.getAllStatesFlowOfUser(userId)
    }

    override suspend fun getStateOfUserInGroup(userId: UserId, groupId: GroupId): LocationSharingState? {
        return locationSharingInterface.getStateOfUserInGroup(userId, groupId)
    }

    override fun getStateFlowOfUserInGroup(userId: UserId, groupId: GroupId): Flow<LocationSharingState?> {
        return locationSharingInterface.getStateFlowOfUserInGroup(userId, groupId)
    }

    override suspend fun getAllUserStatesOfGroup(groupId: GroupId): List<LocationSharingState> {
        return locationSharingInterface.getAllUserStatesOfGroup(groupId)
    }

    override fun getStatesFlowOfAllUserInGroup(groupId: GroupId): Flow<List<LocationSharingState>> {
        return locationSharingInterface.getStatesFlowOfAllUserInGroup(groupId)
    }

    override suspend fun storeLocationSharingState(locationSharingState: LocationSharingState) {
        return locationSharingInterface.insert(locationSharingState)
    }

    override suspend fun deleteAll(groupId: GroupId) {
        locationSharingInterface.deleteAll(groupId)
    }
}
