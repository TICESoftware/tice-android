package tice.models.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import tice.models.User
import tice.models.UserId

@Dao
interface UserInterface {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: User)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entities: List<User>)

    @Query("SELECT * FROM user WHERE userId=:userId")
    suspend fun get(userId: UserId): User
}
