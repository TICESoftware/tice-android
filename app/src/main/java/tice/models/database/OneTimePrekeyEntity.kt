package tice.models.database

import androidx.room.*
import tice.models.PrivateKey
import tice.models.PublicKey

@Entity
data class OneTimePrekeyEntity(
    @PrimaryKey @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val publicKey: PublicKey,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val privateKey: PrivateKey
)

@Dao
interface OneTimePrekeyInterface {
    @Query("SELECT * FROM oneTimePrekeyEntity")
    suspend fun loadAll(): List<OneTimePrekeyEntity>

    @Query("SELECT privateKey FROM oneTimePrekeyEntity WHERE publicKey=:publicKey limit 1")
    suspend fun loadPrivateKey(publicKey: PublicKey): PrivateKey

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(oneTimePrekey: List<OneTimePrekeyEntity>)

    @Query("DELETE FROM oneTimePrekeyEntity WHERE publicKey=:publicKey")
    suspend fun deleteGroupKey(publicKey: PublicKey)

    @Query("DELETE FROM oneTimePrekeyEntity")
    suspend fun deleteAll()
}
