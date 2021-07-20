package tice.crypto

import android.util.Base64
import com.goterl.lazysodium.utils.Key
import tice.models.KeyPair
import tice.models.PrivateKey
import tice.models.PublicKey
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import com.goterl.lazysodium.utils.KeyPair as CryptoKeyPair
import java.security.KeyPair as SecurityKeyPair
import java.security.PrivateKey as SigningKey
import java.security.PublicKey as VerificationKey

fun CryptoKeyPair.dataKeyPair(): KeyPair = KeyPair(secretKey.asBytes, publicKey.asBytes)
fun KeyPair.cryptoKeyPair(): CryptoKeyPair = CryptoKeyPair(Key.fromBytes(publicKey), Key.fromBytes(privateKey))

fun Key.dataKey(): ByteArray = asBytes
fun ByteArray.cryptoKey(): Key = Key.fromBytes(this)

fun PrivateKey.signingKey(): SigningKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(this))

fun PublicKey.verificationKey(): VerificationKey {
    val publicKeyString =
        decodeToString()
            .removePrefix("-----BEGIN PUBLIC KEY-----")
            .removeSuffix("-----END PUBLIC KEY-----")
    return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.decode(publicKeyString, Base64.DEFAULT)))
}

fun VerificationKey.dataKey(): PublicKey {
    val publicKeyHeader = "-----BEGIN PUBLIC KEY-----"
    val publicKey =
        Base64
            .encodeToString(encoded, Base64.DEFAULT)
            .replace("\n", "")
            .chunked(64)
            .fold("") { string, line -> string + line + "\n" }
    val publicKeyFooter = "-----END PUBLIC KEY-----"

    val publicKeyString = publicKeyHeader + "\n" + publicKey + publicKeyFooter
    return publicKeyString.encodeToByteArray()
}

fun SecurityKeyPair.dataKeyPair(): KeyPair = KeyPair(private.encoded, public.dataKey())
