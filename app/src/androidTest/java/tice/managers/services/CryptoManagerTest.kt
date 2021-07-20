package tice.managers.services

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.AEAD
import com.goterl.lazysodium.interfaces.SecretStream
import com.ticeapp.androiddoubleratchet.Base64Coder
import okio.ByteString.Companion.decodeHex
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions
import tice.crypto.CryptoManager
import tice.crypto.signingKey
import tice.crypto.verificationKey
import tice.models.Data
import tice.models.SecretKey
import tice.models.User
import tice.models.UserId
import tice.utility.toBase64String

class CryptoManagerTest {

    private lateinit var sodium: LazySodiumAndroid
    private lateinit var cryptoManager: CryptoManager

    private lateinit var payloadData: Data
    private lateinit var secretKey: SecretKey

    @Before
    fun before() {
        sodium = LazySodiumAndroid(SodiumAndroid(), Base64Coder)

        payloadData = "data".encodeToByteArray()
        secretKey = "bcb44bea011976cf5f5ee846dd9377ffe68ff103272d08448abac40fa2bc75c0".decodeHex().toByteArray()

        cryptoManager = CryptoManager(sodium)
    }

    @Test
    fun signingKeyPair() {
        val keyPair = cryptoManager.generateSigningKeyPair()

        val signaturePayload = "sigPayload".encodeToByteArray()
        val signingInstance = java.security.Signature.getInstance("SHA512withECDSA")
        signingInstance.initSign(keyPair.privateKey.signingKey())
        signingInstance.update(signaturePayload)

        val signature = signingInstance.sign()

        val verifyingInstance = java.security.Signature.getInstance("SHA512withECDSA")
        verifyingInstance.initVerify(keyPair.publicKey.verificationKey())
        verifyingInstance.update(signaturePayload)

        Assertions.assertTrue(verifyingInstance.verify(signature))
    }

    @Test
    fun generateDatabaseKey() {
        var length = 16
        val databaseKey1 = cryptoManager.generateDatabaseKey(length)
        Assertions.assertEquals(databaseKey1.size, length)

        length = 32
        val databaseKey2 = cryptoManager.generateDatabaseKey(length)
        Assertions.assertEquals(databaseKey2.size, length)
    }

    @Test
    fun generateGroupKey() {
        val groupKey1 = cryptoManager.generateGroupKey()
        val groupKey2 = cryptoManager.generateGroupKey()

        Assertions.assertNotEquals(groupKey1, groupKey2)
        Assertions.assertEquals(groupKey1.size, AEAD.XCHACHA20POLY1305_IETF_KEYBYTES)
    }

    @Test
    fun tokenKeyForGroup() {
        val publicKey = """
                -----BEGIN PUBLIC KEY-----
                MIGbMBAGByqGSM49AgEGBSuBBAAjA4GGAAQAFbpq9AOJt5HstjXkiUPX+cpD6Tgk
                nd5SeeD1u3JlfWuY5EScfuuWrJ3VP+B5OCKImfBl2n53Q4ICg2D34N1b80wA4cPV
                02thuiz97m2SNI1jODJ09QHlNQqhbc7pFxhwgAIbTLZXaR3yBw4UbGt7R/VkpC+x
                HWx9m1on3n5MaYhATPc=
                -----END PUBLIC KEY-----
        """.trimIndent().encodeToByteArray()

        val user = User(UserId.randomUUID(), publicKey)
        val tokenKey = cryptoManager.tokenKeyForGroup(secretKey, user)

        Assertions.assertEquals(tokenKey.toBase64String(), "fQZJ2TM9lPbEDyW77N2e0lCDU2bjU+WzHUGEwHZ3kTI=")
    }

    @Test
    fun encryption() {
        val encryptedData = cryptoManager.encrypt(payloadData, secretKey)

        val nonce = encryptedData.sliceArray(0 until AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES)
        val cipher = encryptedData.sliceArray(AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES until encryptedData.size)
        val plaintext = ByteArray(cipher.size - AEAD.CHACHA20POLY1305_IETF_ABYTES)
        sodium.cryptoAeadXChaCha20Poly1305IetfDecrypt(plaintext, null, null, cipher, cipher.size.toLong(), null, 0, nonce, secretKey)

        Assertions.assertTrue(plaintext.contentEquals(payloadData))
    }

    @Test
    fun encryptionWithGeneratedKey() {
        val (encryptedData1, secretKey1) = cryptoManager.encrypt(payloadData)
        val (encryptedData2, secretKey2) = cryptoManager.encrypt(payloadData)

        val nonce1 = encryptedData1.sliceArray(0 until AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES)
        val cipher1 = encryptedData1.sliceArray(AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES until encryptedData1.size)
        val plaintext1 = ByteArray(cipher1.size - AEAD.CHACHA20POLY1305_IETF_ABYTES)
        sodium.cryptoAeadXChaCha20Poly1305IetfDecrypt(plaintext1, null, null, cipher1, cipher1.size.toLong(), null, 0, nonce1, secretKey1)

        val nonce2 = encryptedData2.sliceArray(0 until AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES)
        val cipher2 = encryptedData2.sliceArray(AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES until encryptedData2.size)
        val plaintext2 = ByteArray(cipher2.size - AEAD.CHACHA20POLY1305_IETF_ABYTES)
        sodium.cryptoAeadXChaCha20Poly1305IetfDecrypt(plaintext2, null, null, cipher2, cipher2.size.toLong(), null, 0, nonce2, secretKey2)

        Assertions.assertTrue(plaintext1.contentEquals(payloadData))
        Assertions.assertTrue(plaintext2.contentEquals(payloadData))
        Assertions.assertFalse(secretKey1.contentEquals(secretKey2))
    }

    @Test
    fun decryption() {
        val nonce = sodium.nonce(AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES)
        val cipher = ByteArray(payloadData.size + AEAD.XCHACHA20POLY1305_IETF_ABYTES)
        sodium.cryptoAeadXChaCha20Poly1305IetfEncrypt(cipher, null, payloadData, payloadData.size.toLong(), null, 0, null, nonce, secretKey)

        val plaintext = cryptoManager.decrypt(nonce + cipher, secretKey)

        Assertions.assertTrue(plaintext.contentEquals(payloadData))
    }
}