package tice.managers.storageManagers

import android.util.Base64
import tice.dagger.provides.ConfigModule
import tice.dagger.scopes.AppScope
import tice.exceptions.CryptoStorageManagerException
import tice.models.*
import tice.models.database.ConversationStateEntity
import tice.models.database.IdentityKeyPairEntity
import tice.models.database.OneTimePrekeyEntity
import tice.models.database.SigningKeyPairEntity
import tice.models.messaging.conversation.ConversationState
import tice.utility.getLogger
import java.util.*
import javax.inject.Inject
import javax.inject.Named

@AppScope
class CryptoStorageManager @Inject constructor(
    private val db: AppDatabase,
    @Named(ConfigModule.PUBLIC_SERVER_KEY) private val publicServerKey: String
) : CryptoStorageManagerType {
    private val logger by getLogger()

    private val identityKeyPairInterface = db.identityKeyPairInterface()
    private val signingKeyPairInterface = db.signingKeyPairInterface()
    private val groupKeyInterface = db.groupKeyInterface()
    private val prekeyInterface = db.prekeyInterface()
    private val oneTimePrekeyInterface = db.oneTimePrekeyInterface()
    private val membershipCertificateInterface = db.membershipCertificateInterface()
    private val conversationStateInterface = db.conversationStateInterface()
    private val messageKeyCacheInterface = db.messageKeyCacheInterface()

    private val messageKeyCaches = mutableMapOf<ConversationId, MessageKeyCache>()

    override suspend fun saveIdentityKeyPair(keyPair: KeyPair) {
        val entity = IdentityKeyPairEntity(keyPair.publicKey, keyPair.privateKey)
        identityKeyPairInterface.insert(entity)
    }

    override suspend fun loadIdentityKeyPair(): KeyPair {
        val entity = identityKeyPairInterface.get() ?: throw CryptoStorageManagerException.NoDataStored
        return KeyPair(entity.privateKey, entity.publicKey)
    }

    override suspend fun saveSigningKeyPair(keyPair: KeyPair) {
        val entity = SigningKeyPairEntity(keyPair.publicKey, keyPair.privateKey)
        signingKeyPairInterface.insert(entity)
    }

    override suspend fun loadSigningKeyPair(): KeyPair {
        val entity = signingKeyPairInterface.getOne()
        return KeyPair(entity.privateKey, entity.publicKey)
    }

    override suspend fun savePrekeyPair(keyPair: KeyPair, signature: Signature) {
        prekeyInterface.insert(keyPair.publicKey, keyPair.privateKey, signature)
    }

    override suspend fun loadPrekeyPair(): KeyPair {
        val entity = prekeyInterface.getOne() ?: throw CryptoStorageManagerException.NoDataStored
        return KeyPair(entity.privateKey, entity.publicKey)
    }

    override suspend fun loadPrekeySignature(): Signature {
        return prekeyInterface.getOne()?.signature ?: throw CryptoStorageManagerException.NoDataStored
    }

    override suspend fun saveOneTimePrekeyPairs(keyPairs: List<KeyPair>) {
        oneTimePrekeyInterface.insert(keyPairs.map { OneTimePrekeyEntity(it.publicKey, it.privateKey) })
    }

    override suspend fun loadPrivateOneTimePrekey(publicKey: PublicKey): PrivateKey {
        logger.debug("Loading private one-time prekey for public one: ${Base64.encodeToString(publicKey, Base64.DEFAULT)}")
        val oneTimePrekeyEntities = oneTimePrekeyInterface.loadAll()
        oneTimePrekeyEntities.forEach {
            if (it.publicKey.contentEquals(publicKey)) {
                return it.privateKey
            }
        }

        throw CryptoStorageManagerException.InvalidOneTimePrekey
    }

    override suspend fun deleteOneTimePrekeyPair(publicKey: PublicKey) {
        oneTimePrekeyInterface.deleteGroupKey(publicKey)
    }

    override suspend fun save(groupKey: SecretKey, groupId: GroupId) {
        groupKeyInterface.insert(groupId, groupKey)
    }

    override suspend fun loadGroupKey(groupId: GroupId): SecretKey {
        return groupKeyInterface.getGroupKey(groupId)
    }

    override suspend fun removeGroupKey(groupId: GroupId) {
        groupKeyInterface.deleteGroupKey(groupId)
    }

    override fun loadServerPublicSigningKey(): PublicKey = publicServerKey.toByteArray()

    override suspend fun saveServerSignedMembershipCertificate(
        serverSignedMembershipCertificate: Certificate,
        groupId: GroupId
    ) {
        membershipCertificateInterface.insert(groupId, serverSignedMembershipCertificate)
    }

    override suspend fun loadServerSignedMembershipCertificate(groupId: GroupId): Certificate {
        return membershipCertificateInterface.getMembershipCertificate(groupId)
    }

    override suspend fun removeServerSignedMembershipCertificate(groupId: GroupId) {
        membershipCertificateInterface.deleteMembershipCertificate(groupId)
    }

    override suspend fun saveConversationState(conversationState: ConversationState) {
        val conversationStateEntity = ConversationStateEntity(
            conversationState.userId,
            conversationState.conversationId,
            conversationState.rootKey,
            conversationState.rootChainPublicKey,
            conversationState.rootChainPrivateKey,
            conversationState.rootChainRemotePublicKey,
            conversationState.sendingChainKey,
            conversationState.receivingChainKey,
            conversationState.sendMessageNumber,
            conversationState.receivedMessageNumber,
            conversationState.previousSendingChanLength
        )
        conversationStateInterface.insert(conversationStateEntity)
    }

    override suspend fun loadConversationState(
        userId: UserId,
        conversationId: ConversationId
    ): ConversationState? {
        val conversationStateEntity = conversationStateInterface.get(userId, conversationId) ?: return null
        return ConversationState(
            conversationStateEntity.userId,
            conversationStateEntity.conversationId,
            conversationStateEntity.rootKey,
            conversationStateEntity.rootChainPublicKey,
            conversationStateEntity.rootChainPrivateKey,
            conversationStateEntity.rootChainRemotePublicKey,
            conversationStateEntity.sendingChainKey,
            conversationStateEntity.receivingChainKey,
            conversationStateEntity.sendMessageNumber,
            conversationStateEntity.receivedMessageNumber,
            conversationStateEntity.previousSendingChanLength
        )
    }

    override suspend fun loadConversationStates(): List<ConversationState> {
        val conversationStateEntities = conversationStateInterface.getAll()
        return conversationStateEntities.map {
            ConversationState(
                it.userId,
                it.conversationId,
                it.rootKey,
                it.rootChainPublicKey,
                it.rootChainPrivateKey,
                it.rootChainRemotePublicKey,
                it.sendingChainKey,
                it.receivingChainKey,
                it.sendMessageNumber,
                it.receivedMessageNumber,
                it.previousSendingChanLength
            )
        }
    }

    override suspend fun messageKeyCache(conversationId: ConversationId): MessageKeyCache {
        messageKeyCaches[conversationId]?.let { return it }

        val cache = MessageKeyCache(conversationId, db)
        messageKeyCaches[conversationId] = cache
        return cache
    }

    override suspend fun cleanMessageKeyCache(threshold: Date) {
        messageKeyCacheInterface.deleteMessageKeys(threshold)
    }

    override suspend fun cleanMessageKeyCacheOlderThanAnHour(threshold: Date) {
        messageKeyCacheInterface.deleteMessageKeysOlderThanAnHour(threshold)
    }

    override suspend fun removeAllData() {
        identityKeyPairInterface.delete()
        signingKeyPairInterface.deleteAll()
        groupKeyInterface.deleteAll()
        prekeyInterface.deleteAll()
        oneTimePrekeyInterface.deleteAll()
        membershipCertificateInterface.deleteAll()
        conversationStateInterface.deleteAll()

        for (cache in messageKeyCaches.values) {
            cache.removeAll()
        }
    }
}
