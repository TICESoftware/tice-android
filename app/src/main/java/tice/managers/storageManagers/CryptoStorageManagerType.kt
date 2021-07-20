package tice.managers.storageManagers

import tice.crypto.CryptoStorageManagerType
import tice.models.*
import java.util.*

interface CryptoStorageManagerType : CryptoStorageManagerType {
    suspend fun saveSigningKeyPair(keyPair: KeyPair)
    suspend fun loadSigningKeyPair(): KeyPair?
    fun loadServerPublicSigningKey(): PublicKey

    suspend fun save(groupKey: SecretKey, groupId: GroupId)
    suspend fun loadGroupKey(groupId: GroupId): SecretKey
    suspend fun removeGroupKey(groupId: GroupId)
    suspend fun saveServerSignedMembershipCertificate(serverSignedMembershipCertificate: Certificate, groupId: GroupId)
    suspend fun loadServerSignedMembershipCertificate(groupId: GroupId): Certificate
    suspend fun removeServerSignedMembershipCertificate(groupId: GroupId)

    suspend fun cleanMessageKeyCache(threshold: Date)
    suspend fun cleanMessageKeyCacheOlderThanAnHour(threshold: Date)

    suspend fun removeAllData()
}
