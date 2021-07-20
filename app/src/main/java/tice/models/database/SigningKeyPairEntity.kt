package tice.models.database

import androidx.room.*
import tice.models.PrivateKey
import tice.models.PublicKey

@Entity
data class SigningKeyPairEntity(@PrimaryKey val publicKey: PublicKey, val privateKey: PrivateKey)

@Dao
interface SigningKeyPairInterface {
    @Query("SELECT * FROM signingKeyPairEntity limit 1")
    suspend fun getOne(): SigningKeyPairEntity

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SigningKeyPairEntity)

    @Query("DELETE FROM signingKeyPairEntity")
    suspend fun deleteAll()
}
