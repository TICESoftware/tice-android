package tice.models

data class Membership(
    val userId: UserId,
    val groupId: GroupId,
    val publicSigningKey: PublicKey,
    val admin: Boolean,
    val selfSignedMembershipCertificate: Certificate? = null,
    val serverSignedMembershipCertificate: Certificate,
    val adminSignedMembershipCertificate: Certificate? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Membership

        if (userId != other.userId) return false
        if (groupId != other.groupId) return false
        if (!publicSigningKey.contentEquals(other.publicSigningKey)) return false
        if (admin != other.admin) return false
        if (selfSignedMembershipCertificate != other.selfSignedMembershipCertificate) return false
        if (serverSignedMembershipCertificate != other.serverSignedMembershipCertificate) return false
        if (adminSignedMembershipCertificate != other.adminSignedMembershipCertificate) return false

        return true
    }

    override fun hashCode(): Int {
        var result = userId.hashCode()
        result = 31 * result + groupId.hashCode()
        result = 31 * result + publicSigningKey.contentHashCode()
        result = 31 * result + admin.hashCode()
        result = 31 * result + (selfSignedMembershipCertificate?.hashCode() ?: 0)
        result = 31 * result + serverSignedMembershipCertificate.hashCode()
        result = 31 * result + (adminSignedMembershipCertificate?.hashCode() ?: 0)
        return result
    }
}
