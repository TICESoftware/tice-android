package tice.crypto

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.interfaces.AEAD
import com.ticeapp.androidhkdf.deriveHKDFKey
import tice.models.Ciphertext
import tice.models.KeyPair
import tice.models.SecretKey
import tice.models.UserType
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.*
import javax.inject.Inject

typealias JWTId = UUID

open class CryptoManager @Inject constructor(val sodium: LazySodiumAndroid) : CryptoManagerType {
    override fun generateDatabaseKey(length: Int): SecretKey = sodium.randomBytesBuf(length)

    override fun generateSigningKeyPair(): KeyPair {
        val ecSpec = ECGenParameterSpec("secp521r1")
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ecSpec)
        val keyPair = keyPairGenerator.generateKeyPair()

        return keyPair.dataKeyPair()
    }

    override fun generateGroupKey(): SecretKey = sodium.keygen(AEAD.Method.XCHACHA20_POLY1305_IETF).dataKey()

    override fun tokenKeyForGroup(groupKey: SecretKey, user: UserType): SecretKey {
        var inputKeyingMaterial = groupKey.clone()
        inputKeyingMaterial += user.publicSigningKey.clone()

        return deriveHKDFKey(inputKeyingMaterial, L = 32, sodium = sodium)
    }

    override fun encrypt(data: ByteArray): Pair<Ciphertext, SecretKey> {
        val secretKey = sodium.keygen(AEAD.Method.XCHACHA20_POLY1305_IETF)
        val ciphertext = encrypt(data, secretKey.dataKey())
        return Pair(ciphertext, secretKey.dataKey())
    }

    override fun encrypt(data: ByteArray, secretKey: SecretKey): Ciphertext {
        val nonce = sodium.nonce(AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES)
        val cipher = ByteArray(data.size + AEAD.XCHACHA20POLY1305_IETF_ABYTES)
        sodium.cryptoAeadXChaCha20Poly1305IetfEncrypt(cipher, null, data, data.size.toLong(), null, 0, null, nonce, secretKey)

        return nonce + cipher
    }

    override fun decrypt(encryptedData: ByteArray, secretKey: SecretKey): ByteArray {
        val nonce = encryptedData.sliceArray(0 until AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES)
        val cipher = encryptedData.sliceArray(AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES until encryptedData.size)

        val plaintextLength = cipher.size - AEAD.XCHACHA20POLY1305_IETF_ABYTES
        val plaintext = ByteArray(plaintextLength)
        sodium.cryptoAeadXChaCha20Poly1305IetfDecrypt(plaintext, null, null, cipher, cipher.size.toLong(), null, 0, nonce, secretKey)

        return plaintext
    }
}
