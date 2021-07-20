package tice.models.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import tice.models.PrivateKey
import tice.models.PublicKey
import tice.models.Signature

@Entity
data class PrekeyEntity(@PrimaryKey val publicKey: PublicKey, val privateKey: PrivateKey, val signature: Signature)

@Dao
interface PrekeyInterface {
    @Query("SELECT * FROM prekeyEntity limit 1")
    suspend fun getOne(): PrekeyEntity?

    @Query("INSERT INTO prekeyEntity (publicKey,privateKey,signature) VALUES (:publicKey,:privateKey,:signature)")
    suspend fun insert(publicKey: PublicKey, privateKey: PrivateKey, signature: Signature)

    @Query("DELETE FROM prekeyEntity")
    suspend fun deleteAll()
}
