package tice.models.database

import androidx.room.*
import tice.models.PrivateKey
import tice.models.PublicKey

@Entity
data class IdentityKeyPairEntity(@PrimaryKey val publicKey: PublicKey, val privateKey: PrivateKey)

@Dao
interface IdentityKeyPairInterface {
    @Query("SELECT * FROM identityKeyPairEntity limit 1")
    suspend fun get(): IdentityKeyPairEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: IdentityKeyPairEntity)

    @Query("DELETE FROM identityKeyPairEntity")
    suspend fun delete()
}
