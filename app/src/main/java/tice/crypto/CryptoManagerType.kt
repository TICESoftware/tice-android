package tice.crypto

import tice.models.Ciphertext
import tice.models.KeyPair
import tice.models.SecretKey
import tice.models.UserType

typealias Data = ByteArray

interface CryptoManagerType {
    fun generateDatabaseKey(length: Int): SecretKey
    fun generateSigningKeyPair(): KeyPair
    fun generateGroupKey(): SecretKey

    fun tokenKeyForGroup(groupKey: SecretKey, user: UserType): SecretKey

    fun encrypt(data: Data): Pair<Ciphertext, SecretKey>
    fun encrypt(data: Data, secretKey: SecretKey): Ciphertext
    fun decrypt(encryptedData: Data, secretKey: SecretKey): Data
}
