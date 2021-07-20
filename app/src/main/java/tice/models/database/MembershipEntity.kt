package tice.models.database

import androidx.room.Entity
import tice.models.*

@Entity(primaryKeys = ["userId", "groupId"])
data class MembershipEntity(
    val userId: UserId,
    val groupId: GroupId,
    val publicSigningKey: PublicKey,
    val admin: Boolean,
    val selfSignedMembershipCertificate: Certificate?,
    val serverSignedMembershipCertificate: Certificate,
    val adminSignedMembershipCertificate: Certificate?
) {
    fun membership(): Membership = Membership(
        userId,
        groupId,
        publicSigningKey,
        admin,
        selfSignedMembershipCertificate,
        serverSignedMembershipCertificate,
        adminSignedMembershipCertificate
    )
}

fun Membership.databaseEntity(): MembershipEntity = MembershipEntity(
    userId,
    groupId,
    publicSigningKey,
    admin,
    selfSignedMembershipCertificate,
    serverSignedMembershipCertificate,
    adminSignedMembershipCertificate
)
