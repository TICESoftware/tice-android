package tice.crypto

import tice.models.*
import java.util.*

interface AuthManagerType {
    fun createUserSignedMembershipCertificate(
        userId: UserId,
        groupId: GroupId,
        admin: Boolean,
        issuerUserId: UserId,
        signingKey: PrivateKey
    ): Certificate

    fun validateUserSignedMembershipCertificate(certificate: Certificate, userId: UserId, groupId: GroupId, admin: Boolean, issuerUserId: UserId, publicKey: PublicKey)
    fun validateServerSignedMembershipCertificate(certificate: Certificate, userId: UserId, groupId: GroupId, admin: Boolean, publicKey: PublicKey)
    fun membershipCertificateExpirationDate(certificate: Certificate, publicKey: PublicKey): Date

    fun generateAuthHeader(signingKey: PrivateKey, userId: UserId): Certificate
}
