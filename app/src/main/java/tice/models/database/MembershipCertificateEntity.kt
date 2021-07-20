package tice.models.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import tice.models.Certificate
import tice.models.GroupId

@Entity
data class MembershipCertificateEntity(@PrimaryKey val groupId: GroupId, val certificate: Certificate)

@Dao
interface MembershipCertificateInterface {
    @Query("SELECT certificate FROM membershipCertificateEntity WHERE groupId=:groupId limit 1")
    suspend fun getMembershipCertificate(groupId: GroupId): Certificate

    @Query("INSERT INTO membershipCertificateEntity (groupId,certificate) VALUES (:groupId,:certificate)")
    suspend fun insert(groupId: GroupId, certificate: Certificate)

    @Query("DELETE FROM membershipCertificateEntity WHERE groupId=:groupId")
    suspend fun deleteMembershipCertificate(groupId: GroupId)

    @Query("DELETE FROM membershipCertificateEntity")
    suspend fun deleteAll()
}
