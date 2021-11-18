package tice.managers.services

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.IncorrectClaimException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.PrematureJwtException
import io.jsonwebtoken.security.SignatureException
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.assertThrows
import tice.crypto.*
import tice.dagger.provides.ConfigModule
import tice.models.Certificate
import tice.models.GroupId
import tice.models.UserId
import tice.utility.dataFromBase64
import tice.utility.uuidString
import java.util.*

class AuthManagerTest {
    private lateinit var sodium: LazySodiumAndroid
    private lateinit var cryptoParams: ConfigModule.CryptoParams

    private lateinit var authManager: AuthManager

    private val groupId = GroupId.fromString("C621E1F8-C36C-495A-93FC-0C247A3E6E5F")
    private val userId = GroupId.fromString("D621E1F8-C36C-495A-93FC-0C247A3E6E5F")
    private val adminUserId = GroupId.fromString("E621E1F8-C36C-495A-93FC-0C247A3E6E5F")
    private val randomUUID = GroupId.fromString("F621E1F8-C36C-495A-93FC-0C247A3E6E5F")

    private val privateKey = """
            MIHuAgEAMBAGByqGSM49AgEGBSuBBAAjBIHWMIHTAgEBBEIBHglOe5uaeKYlw8zJ
            OPjKlZ0qCEjT6vTyZaKXQrmTqS/pXZIwuSOQGpAmt+mdLJBHrWexMfylIIkhDYB5
            f4WPfC6hgYkDgYYABABc3WjLGEvm/eNw92fhKhYOWXp1N/aNSez7keMEK0xBOuVk
            VE88lWUNFUptjn/24ad8xV9bJFIgrfVK9+1CtkFdowFi1qHBu5+W4t5BfOKWC8mY
            H9qkpXr3ZW3jU0c9Bzh+MdRw2YfYSQYHVRSm9b19YesSPLM/dOC5A6gBb5BRJePp
            Bg==
        """.trimIndent().replace("\n", "").dataFromBase64()

    private val publicKey = """
            -----BEGIN PUBLIC KEY-----
            MIGbMBAGByqGSM49AgEGBSuBBAAjA4GGAAQAXN1oyxhL5v3jcPdn4SoWDll6dTf2
            jUns+5HjBCtMQTrlZFRPPJVlDRVKbY5/9uGnfMVfWyRSIK31SvftQrZBXaMBYtah
            wbufluLeQXzilgvJmB/apKV692Vt41NHPQc4fjHUcNmH2EkGB1UUpvW9fWHrEjyz
            P3TguQOoAW+QUSXj6QY=
            -----END PUBLIC KEY-----
        """.trimIndent().encodeToByteArray()

    private val otherPrivateKey = """
            MIHuAgEAMBAGByqGSM49AgEGBSuBBAAjBIHWMIHTAgEBBEIBBXAAp4X98J1fllrr
            T4qYGj9Jyh1WFLXmozfyXeVMSb4WU/WBuwwKSWG0bgTobJBJGWler2WR1atpEkbb
            fUqzuPihgYkDgYYABAAGP8tkGGZAm9Si4tlvq4HgjQpiKfEXvQsye/pIRhj/RROQ
            jgKpLXmzzWajQAddS+re6Mx9tWfipd/jE48/t8LdMwAY6ZL2anPShsrdTk3K18Xh
            8yRlzwiDwZQ/SkY93Xo2rxdcNG5Lu3nt40YmKHjEJ05ouvQpYy+8vaIPhkKn1fYG
            gg==
        """.trimIndent().replace("\n", "").dataFromBase64()

    private val otherPublicKey = """
            -----BEGIN PUBLIC KEY-----
            MIGbMBAGByqGSM49AgEGBSuBBAAjA4GGAAQABj/LZBhmQJvUouLZb6uB4I0KYinx
            F70LMnv6SEYY/0UTkI4CqS15s81mo0AHXUvq3ujMfbVn4qXf4xOPP7fC3TMAGOmS
            9mpz0obK3U5NytfF4fMkZc8Ig8GUP0pGPd16Nq8XXDRuS7t57eNGJih4xCdOaLr0
            KWMvvL2iD4ZCp9X2BoI=
            -----END PUBLIC KEY-----
        """.trimIndent().encodeToByteArray()

    @Before
    fun before() {
        sodium = LazySodiumAndroid(SodiumAndroid())
        cryptoParams = ConfigModule.CryptoParams(100, 100, "TICE", 100, "SHA512withECDSA", 100, 60)

        authManager = AuthManager(sodium, cryptoParams)
    }

    @Test
    fun membershipCertificateValidation() {
        val cert = authManager.createUserSignedMembershipCertificate(userId, groupId, true, adminUserId, privateKey)
        authManager.validateUserSignedMembershipCertificate(cert, userId, groupId, true, adminUserId, publicKey)
    }

    @Test
    fun checkMembershipOnCertificateValidation() {
        val cert = authManager.createUserSignedMembershipCertificate(userId, groupId, true, adminUserId, privateKey)

        assertThrows<IncorrectClaimException> {
            authManager.validateUserSignedMembershipCertificate(cert, randomUUID, groupId, true, adminUserId, publicKey)
        }

        assertThrows<IncorrectClaimException> {
            authManager.validateUserSignedMembershipCertificate(cert, userId, randomUUID, true, adminUserId, publicKey)
        }
    }

    @Test
    fun checkAdminFlagOnCertificateValidation() {
        val certAdmin = authManager.createUserSignedMembershipCertificate(userId, groupId, true, adminUserId, privateKey)
        authManager.validateUserSignedMembershipCertificate(certAdmin, userId, groupId, true, adminUserId, publicKey)

        val certNotAdmin = authManager.createUserSignedMembershipCertificate(userId, groupId, false, adminUserId, privateKey)
        authManager.validateUserSignedMembershipCertificate(certNotAdmin, userId, groupId, false, adminUserId, publicKey)
        assertThrows<IncorrectClaimException> {
            authManager.validateUserSignedMembershipCertificate(certNotAdmin, userId, groupId, true, adminUserId, publicKey)
        }
    }

    @Test
    fun checkSignerOnCertificateValidation() {
        val cert = authManager.createUserSignedMembershipCertificate(userId, groupId, true, adminUserId, privateKey)

        assertThrows<IncorrectClaimException> {
            authManager.validateUserSignedMembershipCertificate(cert, userId, groupId, true, randomUUID, publicKey)
        }
    }

    @Test
    fun validateExpiredMembershipCertificate() {
        val cert = createMembershipCertificate(userId, groupId, true, adminUserId, Date(Date().time - 3600 * 1000), Date(Date().time - 70 * 1000), privateKey)
        val certWithLeeway = createMembershipCertificate(userId, groupId, true, adminUserId, Date(Date().time - 3600 * 1000), Date(Date().time - 50 * 1000), privateKey)

        assertThrows<ExpiredJwtException> {
            authManager.validateUserSignedMembershipCertificate(cert, userId, groupId, true, adminUserId, publicKey)
        }

        authManager.validateUserSignedMembershipCertificate(certWithLeeway, userId, groupId, true, adminUserId, publicKey)
    }

    @Test
    fun validateMembershipCertificateIssuedInFuture() {
        val cert = createMembershipCertificate(userId, groupId, true, adminUserId, Date(Date().time + 70 * 1000), Date(Date().time + 100 * 1000), privateKey)
        val certWithLeeway = createMembershipCertificate(userId, groupId, true, adminUserId, Date(Date().time + 50 * 1000), Date(Date().time + 100 * 1000), privateKey)

        assertThrows<PrematureJwtException> {
            authManager.validateUserSignedMembershipCertificate(cert, userId, groupId, true, adminUserId, publicKey)
        }

        authManager.validateUserSignedMembershipCertificate(certWithLeeway, userId, groupId, true, adminUserId, publicKey)
    }

    @Test
    fun membershipCertificateExpirationDate() {
        val exp = Date(Date().time + 100 * 1000)
        val cert = createMembershipCertificate(userId, groupId, true, adminUserId, Date(Date().time - 10 * 1000), exp, privateKey)

        Assertions.assertEquals(authManager.membershipCertificateExpirationDate(cert, publicKey).time.toFloat(), exp.time.toFloat(), 2000.0.toFloat())
    }

    @Test
    fun validateMembershipCertificateInvalidSignature() {
        val cert = createMembershipCertificate(userId, groupId, true, adminUserId, Date(Date().time - 3600 * 1000), Date(Date().time + 70 * 1000), privateKey)

        assertThrows<SignatureException> {
            authManager.validateUserSignedMembershipCertificate(cert, userId, groupId, true, adminUserId, otherPublicKey)
        }
    }

    @Test
    fun authHeaderGeneration() {
        val authHeader = authManager.generateAuthHeader(privateKey, userId)

        val jwts = Jwts
            .parserBuilder()
            .requireIssuer(userId.uuidString())
            .setSigningKey(publicKey.verificationKey())
            .build()
            .parseClaimsJws(authHeader)

        Assertions.assertEquals(jwts.body.issuedAt.time.toFloat(), Date().time.toFloat(), 1000.0.toFloat())
        Assertions.assertEquals(jwts.body.expiration.time.toFloat(), Date(Date().time + 120 * 1000).time.toFloat(), 1000.0.toFloat())
        Assertions.assertNotNull(jwts.body.get("nonce"))
    }

    private fun createMembershipCertificate(
        userId: UserId,
        groupId: GroupId,
        admin: Boolean,
        issuerUserId: UserId,
        iat: Date,
        exp: Date,
        signingKey: tice.models.PrivateKey
    ): Certificate = Jwts.builder()
        .setId(JWTId.randomUUID().uuidString())
        .setIssuer(JWTIssuer.User(issuerUserId).claimString())
        .setSubject(userId.uuidString())
        .setIssuedAt(iat)
        .setExpiration(exp)
        .claim("groupId", groupId.uuidString())
        .claim("admin", admin)
        .signWith(signingKey.signingKey())
        .compact()
}