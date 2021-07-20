package tice.models.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import tice.models.GroupId
import tice.models.LocationSharingState
import tice.models.UserId

@Dao
interface LocationSharingInterface {

    @Query("SELECT * FROM LocationSharingState")
    fun getAllStatesFlow(): Flow<List<LocationSharingState>>

    @Query("SELECT * FROM LocationSharingState")
    fun getAllStates(): List<LocationSharingState>

    @Query("SELECT * FROM LocationSharingState WHERE userId=:userId")
    suspend fun getAllStatesOfUser(userId: UserId): List<LocationSharingState>

    @Query("SELECT * FROM LocationSharingState WHERE userId=:userId AND groupId=:groupId LIMIT 1")
    suspend fun getStateOfUserInGroup(userId: UserId, groupId: GroupId): LocationSharingState?

    @Query("SELECT * FROM LocationSharingState WHERE userId=:userId AND groupId=:groupId")
    fun getStateFlowOfUserInGroup(userId: UserId, groupId: GroupId): Flow<LocationSharingState?>

    @Query("SELECT * FROM LocationSharingState WHERE userId=:userId")
    fun getAllStatesFlowOfUser(userId: UserId): Flow<List<LocationSharingState>>

    @Query("SELECT * FROM LocationSharingState WHERE groupId=:groupId")
    suspend fun getAllUserStatesOfGroup(groupId: GroupId): List<LocationSharingState>

    @Query("SELECT * FROM LocationSharingState WHERE groupId=:groupId")
    fun getStatesFlowOfAllUserInGroup(groupId: GroupId): Flow<List<LocationSharingState>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(locationSharingState: LocationSharingState)

    @Query("DELETE FROM LocationSharingState WHERE groupId=:groupId")
    suspend fun deleteAll(groupId: GroupId)

    @Query("SELECT * FROM LocationSharingState WHERE sharingEnabled=:sharingEnabled")
    suspend fun getAllStatesEnabled(sharingEnabled: Boolean): List<LocationSharingState>
}
