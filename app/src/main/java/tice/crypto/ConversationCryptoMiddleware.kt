package tice.crypto

import com.goterl.lazysodium.LazySodiumAndroid
import com.ticeapp.androiddoubleratchet.DoubleRatchet
import com.ticeapp.androiddoubleratchet.SessionState
import com.ticeapp.androidx3dh.X3DH
import kotlinx.serialization.json.Json
import tice.dagger.provides.ConfigModule
import tice.exceptions.ConversationCryptoMiddlewareException
import tice.exceptions.CryptoStorageManagerException
import tice.managers.DoubleRatchetProviderType
import tice.managers.storageManagers.CryptoStorageManagerType
import tice.models.*
import tice.models.messaging.conversation.ConversationInvitation
import tice.models.messaging.conversation.ConversationState
import tice.utility.getLogger
import tice.utility.serializer.MessageSerializer
import javax.inject.Inject

class ConversationCryptoMiddleware @Inject constructor(
    private val cryptoManager: CryptoManagerType,
    private val cryptoStorageManager: CryptoStorageManagerType,
    private val doubleRatchetProvider: DoubleRatchetProviderType,
    private val sodium: LazySodiumAndroid,
    cryptoParams: ConfigModule.CryptoParams
) : ConversationCryptoMiddlewareType {
    private val logger by getLogger()

    private val handshake: X3DH
        get() = X3DH(sodium = sodium)

    private val INFO: String = cryptoParams.info
    private val MAX_SKIP: Int = cryptoParams.maxSkip
    private val ONE_TIME_PREKEY_COUNT: Int = cryptoParams.oneTimePrekeyCount
    private val SIGNING_ALGORITHM: String = cryptoParams.signingAlgorithm

    override suspend fun renewHandshakeKeyMaterial(privateSigningKey: PrivateKey, publicSigningKey: PublicKey): UserPublicKeys {
        var identityKeyPair: KeyPair
        var prekeyPair: KeyPair
        var prekeySignature: Signature

        try {
            identityKeyPair = cryptoStorageManager.loadIdentityKeyPair()
            prekeyPair = cryptoStorageManager.loadPrekeyPair()
            prekeySignature = cryptoStorageManager.loadPrekeySignature()
        } catch (e: CryptoStorageManagerException.NoDataStored) {
            logger.info("No keys stored in database.")

            identityKeyPair = handshake.generateIdentityKeyPair().dataKeyPair()
            cryptoStorageManager.saveIdentityKeyPair(identityKeyPair)

            val signedPrekeyPair = handshake.generateSignedPrekeyPair { sign(it.asBytes, privateSigningKey) }
            prekeyPair = signedPrekeyPair.keyPair.dataKeyPair()
            prekeySignature = signedPrekeyPair.signature

            cryptoStorageManager.savePrekeyPair(prekeyPair, prekeySignature)
        }

        val oneTimePrekeyPairs =
            handshake.generateOneTimePrekeyPairs(ONE_TIME_PREKEY_COUNT).map(com.goterl.lazysodium.utils.KeyPair::dataKeyPair)
        cryptoStorageManager.saveOneTimePrekeyPairs(oneTimePrekeyPairs)

        return UserPublicKeys(
            publicSigningKey,
            identityKeyPair.publicKey,
            prekeyPair.publicKey,
            prekeySignature,
            oneTimePrekeyPairs.map(KeyPair::publicKey)
        )
    }

    private suspend fun saveConversationState(userId: UserId, conversationId: ConversationId, doubleRatchet: DoubleRatchet) {
        val sessionState = doubleRatchet.sessionState

        val conversationState = ConversationState(
            userId,
            conversationId,
            sessionState.rootKey.dataKey(),
            sessionState.rootChainKeyPair.publicKey.dataKey(),
            sessionState.rootChainKeyPair.secretKey.dataKey(),
            sessionState.rootChainRemotePublicKey?.dataKey(),
            sessionState.sendingChainKey?.dataKey(),
            sessionState.receivingChainKey?.dataKey(),
            sessionState.sendMessageNumber,
            sessionState.receivedMessageNumber,
            sessionState.previousSendingChainLength
        )

        cryptoStorageManager.saveConversationState(conversationState)
    }

    @ExperimentalStdlibApi
    private suspend fun recoverConversationState(conversationState: ConversationState): DoubleRatchet {
        val rootChainKeyPair = KeyPair(conversationState.rootChainPrivateKey, conversationState.rootChainPublicKey).cryptoKeyPair()
        val sessionState = SessionState(
            conversationState.rootKey.cryptoKey(),
            rootChainKeyPair,
            conversationState.rootChainRemotePublicKey?.cryptoKey(),
            conversationState.sendingChainKey?.cryptoKey(),
            conversationState.receivingChainKey?.cryptoKey(),
            conversationState.sendMessageNumber,
            conversationState.receivedMessageNumber,
            conversationState.previousSendingChanLength,
            INFO,
            MAX_SKIP
        )

        val messageKeyCache = cryptoStorageManager.messageKeyCache(conversationState.conversationId)
        return doubleRatchetProvider.provideDoubleRatchet(sessionState, messageKeyCache, sodium)
    }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun initConversation(
        userId: UserId,
        conversationId: ConversationId,
        remoteIdentityKey: PublicKey,
        remoteSignedPrekey: PublicKey,
        remotePrekeySignature: Signature,
        remoteOneTimePrekey: PublicKey?,
        remoteSigningKey: PublicKey
    ): ConversationInvitation {
        val identityKeyPair = cryptoStorageManager.loadIdentityKeyPair()
        val prekey = cryptoStorageManager.loadPrekeyPair().publicKey

        val keyAgreementInitiation = handshake.initiateKeyAgreement(
            remoteIdentityKey.cryptoKey(),
            remoteSignedPrekey.cryptoKey(),
            remotePrekeySignature,
            remoteOneTimePrekey?.cryptoKey(),
            identityKeyPair.cryptoKeyPair(),
            prekey.cryptoKey(),
            { verify(it, remoteSignedPrekey, remoteSigningKey) },
            INFO
        )

        val messageKeyCache = cryptoStorageManager.messageKeyCache(conversationId)
        val doubleRatchet = doubleRatchetProvider.provideDoubleRatchet(
            null,
            remoteSignedPrekey.cryptoKey(),
            keyAgreementInitiation.sharedSecret,
            MAX_SKIP,
            INFO,
            messageKeyCache,
            sodium
        )
        saveConversationState(userId, conversationId, doubleRatchet)

        return ConversationInvitation(identityKeyPair.publicKey, keyAgreementInitiation.ephemeralPublicKey.dataKey(), remoteOneTimePrekey)
    }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun processConversationInvitation(
        conversationInvitation: ConversationInvitation,
        userId: UserId,
        conversationId: ConversationId
    ) {
        val publicOneTimePrekey = conversationInvitation.usedOneTimePrekey ?: throw ConversationCryptoMiddlewareException.OneTimePrekeyMissingException
        val privateOneTimePrekey = cryptoStorageManager.loadPrivateOneTimePrekey(publicOneTimePrekey)

        val identityKeyPair = cryptoStorageManager.loadIdentityKeyPair().cryptoKeyPair()
        val prekeyPair = cryptoStorageManager.loadPrekeyPair().cryptoKeyPair()
        val oneTimePrekeyPair = KeyPair(privateOneTimePrekey, publicOneTimePrekey).cryptoKeyPair()

        val sharedSecret = handshake.sharedSecretFromKeyAgreement(
            conversationInvitation.identityKey.cryptoKey(),
            conversationInvitation.ephemeralKey.cryptoKey(),
            oneTimePrekeyPair,
            identityKeyPair,
            prekeyPair,
            INFO
        )

        val messageKeyCache = cryptoStorageManager.messageKeyCache(conversationId)
        val doubleRatchet = doubleRatchetProvider.provideDoubleRatchet(prekeyPair, null, sharedSecret, MAX_SKIP, INFO, messageKeyCache, sodium)

        saveConversationState(userId, conversationId, doubleRatchet)

        cryptoStorageManager.deleteOneTimePrekeyPair(publicOneTimePrekey)
    }

    override suspend fun conversationExisting(userId: UserId, conversationId: ConversationId): Boolean {
        return cryptoStorageManager.loadConversationState(userId, conversationId) != null
    }

    override fun conversationFingerprint(ciphertext: Ciphertext): ConversationFingerprint {
        val message = Json.decodeFromString(MessageSerializer, ciphertext.decodeToString())
        return sodium.sodiumBin2Hex(message.header.publicKey.asBytes)
    }

    @ExperimentalStdlibApi
    @OptIn(ExperimentalUnsignedTypes::class)
    override suspend fun encrypt(data: ByteArray, userId: UserId, conversationId: ConversationId): Ciphertext {
        val conversationState =
            cryptoStorageManager.loadConversationState(userId, conversationId) ?: throw ConversationCryptoMiddlewareException.ConversationNotInitializedException
        val doubleRatchet = recoverConversationState(conversationState)

        val message = doubleRatchet.encrypt(data)
        saveConversationState(userId, conversationId, doubleRatchet)

        return Json.encodeToString(MessageSerializer, message).encodeToByteArray()
    }

    @ExperimentalUnsignedTypes
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun decrypt(encryptedMessage: Ciphertext, userId: UserId, conversationId: ConversationId): ByteArray {
        val conversationState =
            cryptoStorageManager.loadConversationState(userId, conversationId) ?: throw ConversationCryptoMiddlewareException.ConversationNotInitializedException
        val doubleRatchet = recoverConversationState(conversationState)

        val encryptedRawMessage = Json.decodeFromString(MessageSerializer, encryptedMessage.decodeToString())

        val plaintext = doubleRatchet.decrypt(encryptedRawMessage)

        saveConversationState(userId, conversationId, doubleRatchet)

        return plaintext
    }

    @ExperimentalUnsignedTypes
    override suspend fun decrypt(
        encryptedData: Ciphertext,
        encryptedSecretKey: Ciphertext,
        userId: UserId,
        conversationId: ConversationId
    ): ByteArray {
        val secretKey = decrypt(encryptedSecretKey, userId, conversationId)
        return cryptoManager.decrypt(encryptedData, secretKey)
    }

    // Signing / verifying
    private fun sign(prekey: PublicKey, privateSigningKey: PrivateKey): Signature {
        val signingInstance = java.security.Signature.getInstance(SIGNING_ALGORITHM)
        signingInstance.initSign(privateSigningKey.signingKey())
        signingInstance.update(prekey)
        return signingInstance.sign()
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun verify(prekeySignature: Signature, prekey: PublicKey, verificationPublicKey: PublicKey): Boolean {
        val verifyingInstance = java.security.Signature.getInstance(SIGNING_ALGORITHM)
        verifyingInstance.initVerify(verificationPublicKey.verificationKey())
        verifyingInstance.update(prekey)
        return verifyingInstance.verify(prekeySignature)
    }
}
