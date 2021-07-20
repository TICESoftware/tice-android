package tice.models.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import tice.models.GroupId
import tice.models.SecretKey

@Entity
data class GroupKeyEntity(@PrimaryKey val groupId: GroupId, val groupKey: SecretKey)

@Dao
interface GroupKeyInterface {
    @Query("SELECT groupKey FROM groupKeyEntity WHERE groupId=:groupId limit 1")
    suspend fun getGroupKey(groupId: GroupId): SecretKey

    @Query("INSERT INTO groupKeyEntity (groupId,groupKey) VALUES (:groupId,:groupKey)")
    suspend fun insert(groupId: GroupId, groupKey: SecretKey)

    @Query("DELETE FROM groupKeyEntity WHERE groupId=:groupId")
    suspend fun deleteGroupKey(groupId: GroupId)

    @Query("DELETE FROM groupKeyEntity")
    suspend fun deleteAll()
}
