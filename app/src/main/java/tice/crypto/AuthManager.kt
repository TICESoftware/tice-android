package tice.crypto

import com.goterl.lazysodium.LazySodiumAndroid
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.PrematureJwtException
import tice.dagger.provides.ConfigModule
import tice.models.*
import tice.utility.uuidString
import java.util.*
import javax.inject.Inject

class AuthManager @Inject constructor(
    private val sodium: LazySodiumAndroid,
    cryptoParams: ConfigModule.CryptoParams
) : AuthManagerType {
    private val CERTIFICATE_VALIDITY_PERIOD: Int = cryptoParams.certificateValidityPeriod
    private val CERTIFICATE_VALIDATION_LEEWAY: Int = cryptoParams.certificationValidationLeeway

    override fun createUserSignedMembershipCertificate(
        userId: UserId,
        groupId: GroupId,
        admin: Boolean,
        issuerUserId: UserId,
        signingKey: PrivateKey
    ): Certificate {
        val issuer = JWTIssuer.User(issuerUserId)
        val issueDate = Date()
        val jwtId = JWTId.randomUUID()

        val calendar = Calendar.getInstance()
        calendar.time = issueDate
        calendar.add(Calendar.SECOND, CERTIFICATE_VALIDITY_PERIOD)
        val expirationDate = calendar.time

        return Jwts.builder()
            .setId(jwtId.uuidString())
            .setIssuer(issuer.claimString())
            .setSubject(userId.uuidString())
            .setIssuedAt(issueDate)
            .setExpiration(expirationDate)
            .claim("groupId", groupId.uuidString())
            .claim("admin", admin)
            .signWith(signingKey.signingKey())
            .compact()
    }

    override fun validateUserSignedMembershipCertificate(
        certificate: Certificate,
        userId: UserId,
        groupId: GroupId,
        admin: Boolean,
        issuerUserId: UserId,
        publicKey: PublicKey
    ) = validate(
        certificate,
        userId,
        groupId,
        admin,
        JWTIssuer.User(issuerUserId),
        publicKey
    )

    override fun validateServerSignedMembershipCertificate(
        certificate: Certificate,
        userId: UserId,
        groupId: GroupId,
        admin: Boolean,
        publicKey: PublicKey
    ) = validate(
        certificate,
        userId,
        groupId,
        admin,
        JWTIssuer.Server,
        publicKey
    )

    @OptIn(ExperimentalStdlibApi::class)
    private fun validate(certificate: Certificate, userId: UserId, groupId: GroupId, admin: Boolean, issuer: JWTIssuer, publicKey: PublicKey) {
        val jwts = Jwts
            .parserBuilder()
            .requireSubject(userId.uuidString())
            .requireIssuer(issuer.claimString())
            .require("groupId", groupId.uuidString())
            .require("admin", admin)
            .setAllowedClockSkewSeconds(CERTIFICATE_VALIDATION_LEEWAY.toLong())
            .setSigningKey(publicKey.verificationKey())
            .build()
            .parseClaimsJws(certificate)

        if (jwts.body.issuedAt.after(Date(Date().time + CERTIFICATE_VALIDATION_LEEWAY * 1000))) {
            throw PrematureJwtException(jwts.header, jwts.body, "JWT seems to be issued in the future.")
        }
    }

    override fun membershipCertificateExpirationDate(certificate: Certificate, publicKey: PublicKey): Date {
        val jwts = Jwts
            .parserBuilder()
            .setSigningKey(publicKey.verificationKey())
            .build()
            .parseClaimsJws(certificate)
        return jwts.body.expiration
    }

    // Auth signature

    override fun generateAuthHeader(signingKey: PrivateKey, userId: UserId): Certificate {
        val issueDate = Date()

        val calendar = Calendar.getInstance()
        calendar.time = issueDate
        calendar.add(Calendar.SECOND, 120)
        val expirationDate = calendar.time

        val nonce = sodium.nonce(16)

        return Jwts.builder()
            .setIssuer(userId.uuidString())
            .setIssuedAt(issueDate)
            .setExpiration(expirationDate)
            .claim("nonce", nonce)
            .signWith(signingKey.signingKey())
            .compact()
    }
}
