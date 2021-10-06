package tice.managers.services

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.utils.Key
import com.goterl.lazysodium.utils.KeyPair
import com.ticeapp.androiddoubleratchet.*
import com.ticeapp.androidx3dh.PrekeySignatureVerifier
import com.ticeapp.androidx3dh.PrekeySigner
import com.ticeapp.androidx3dh.X3DH
import io.mockk.*
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.assertThrows
import tice.crypto.*
import tice.dagger.provides.ConfigModule
import tice.exceptions.ConversationCryptoMiddlewareException
import tice.exceptions.CryptoStorageManagerException
import tice.managers.DoubleRatchetProviderType
import tice.managers.storageManagers.CryptoStorageManagerType
import tice.models.ConversationId
import tice.models.UserId
import tice.models.messaging.conversation.ConversationInvitation
import tice.models.messaging.conversation.ConversationState
import tice.utility.dataFromBase64
import tice.utility.serializer.MessageSerializer
import java.security.SignatureException

class ConversationCryptoMiddlewareTest {
    private lateinit var sodium: LazySodiumAndroid
    private lateinit var cryptoParams: ConfigModule.CryptoParams
    private val cryptoManager: CryptoManagerType = mockk()
    private val cryptoStorageManager: CryptoStorageManagerType = mockk(relaxUnitFun = true)
    private val messageKeyCache: MessageKeyCache = mockk()
    private val doubleRatchetProvider: DoubleRatchetProviderType = mockk()
    private val doubleRatchet: DoubleRatchet = mockk()

    private lateinit var conversationCryptoMiddleware: ConversationCryptoMiddleware

    private val privateKey = """
            MIHuAgEAMBAGByqGSM49AgEGBSuBBAAjBIHWMIHTAgEBBEIAH2+9c/Ouynji3BVX
            oBrwn80mdjosZ3waeS/iIwhYzMVH570m7GsvGEs/BDH5j6PwjD58qAHJbxX5CLM2
            OXTrRPuhgYkDgYYABAE4Jd29BVQC3kclPMKFvZoWSrdZXXmKGB7aLBAYvaUSAHSR
            LzIbUKvK0RsL50Bq8x4wwPpc1jamJojD1vyuh8UcIgD2qB9bITqraARX4k5GFdhg
            LMfCDzqPQSvLXi1/DDPRGxx7Kc2s3/TeOvz6/4tzfNg+qPErlOr+NE7HK0cU06SI
            8g==
    """.trimIndent().replace("\n", "").dataFromBase64()

    private val publicKey = """
            -----BEGIN PUBLIC KEY-----
            MIGbMBAGByqGSM49AgEGBSuBBAAjA4GGAAQBOCXdvQVUAt5HJTzChb2aFkq3WV15
            ihge2iwQGL2lEgB0kS8yG1CrytEbC+dAavMeMMD6XNY2piaIw9b8rofFHCIA9qgf
            WyE6q2gEV+JORhXYYCzHwg86j0Ery14tfwwz0RsceynNrN/03jr8+v+Lc3zYPqjx
            K5Tq/jROxytHFNOkiPI=
            -----END PUBLIC KEY-----
    """.trimIndent().encodeToByteArray()

    @Before
    fun before() {
        sodium = LazySodiumAndroid(SodiumAndroid())

        mockkConstructor(X3DH::class)

        cryptoParams = ConfigModule.CryptoParams(100, 100, "TICE", 100, "SHA512withECDSA", 100, 10)
        conversationCryptoMiddleware = ConversationCryptoMiddleware(cryptoManager, cryptoStorageManager, doubleRatchetProvider, sodium, cryptoParams)
    }

    @Test
    fun createNewHandshakeKeyMaterial() = runBlockingTest {
        val identityKeyPair = KeyPair(Key.fromPlainString("publicIdentityKey"), Key.fromPlainString("privateIdentityKey"))
        val prekeyPair = KeyPair(Key.fromPlainString("publicPrekey"), Key.fromPlainString("privatePrekey"))
        val signedPrekeyPair = X3DH.SignedPrekeyPair(prekeyPair, "prekeySignature".encodeToByteArray())
        val oneTimePrekeyPair = KeyPair(Key.fromPlainString("oneTimePublicKey"), Key.fromPlainString("oneTimePrivatePrekey"))

        val signerSlot = slot<PrekeySigner>()
        every { anyConstructed<X3DH>().generateIdentityKeyPair() } returns identityKeyPair
        every { anyConstructed<X3DH>().generateOneTimePrekeyPairs(100) } returns arrayOf(oneTimePrekeyPair)
        every { anyConstructed<X3DH>().generateSignedPrekeyPair(capture(signerSlot)) } answers {
            val dummyKey = "test".encodeToByteArray()
            val signature = signerSlot.captured(Key.fromBytes(dummyKey))

            val verifyingInstance = java.security.Signature.getInstance(cryptoParams.signingAlgorithm)
            verifyingInstance.initVerify(publicKey.verificationKey())
            verifyingInstance.update(dummyKey)
            Assertions.assertTrue(verifyingInstance.verify(signature))

            signedPrekeyPair
        }

        coEvery { cryptoStorageManager.loadIdentityKeyPair() } throws CryptoStorageManagerException.NoDataStored

        val publicKeys = conversationCryptoMiddleware.renewHandshakeKeyMaterial(privateKey, publicKey)
        Assertions.assertEquals(publicKeys.signingKey, publicKey)
        Assertions.assertEquals(publicKeys.identityKey, identityKeyPair.publicKey.dataKey())
        Assertions.assertEquals(publicKeys.signedPrekey, prekeyPair.publicKey.dataKey())
        Assertions.assertEquals(publicKeys.prekeySignature, signedPrekeyPair.signature)
        Assertions.assertTrue(publicKeys.oneTimePrekeys.first().contentEquals(oneTimePrekeyPair.publicKey.dataKey()))

        coVerify { cryptoStorageManager.saveIdentityKeyPair(identityKeyPair.dataKeyPair()) }
        coVerify { cryptoStorageManager.savePrekeyPair(prekeyPair.dataKeyPair(), signedPrekeyPair.signature) }
        coVerify { cryptoStorageManager.saveOneTimePrekeyPairs(listOf(oneTimePrekeyPair.dataKeyPair())) }
    }

    @Test
    fun renewHandshakeKeyMaterial() = runBlockingTest {
        val identityKeyPair = tice.models.KeyPair("publicIdentityKey".encodeToByteArray(), "privateIdentityKey".encodeToByteArray())
        val prekeyPair = tice.models.KeyPair("publicPrekey".encodeToByteArray(), "privatePrekey".encodeToByteArray())
        val prekeySignature = "prekeySignature".encodeToByteArray()
        val oneTimePrekeyPair = KeyPair(Key.fromPlainString("oneTimePublicKey"), Key.fromPlainString("oneTimePrivatePrekey"))

        every { anyConstructed<X3DH>().generateOneTimePrekeyPairs(100) } returns arrayOf(oneTimePrekeyPair)

        coEvery { cryptoStorageManager.loadIdentityKeyPair() } returns identityKeyPair
        coEvery { cryptoStorageManager.loadPrekeyPair() } returns prekeyPair
        coEvery { cryptoStorageManager.loadPrekeySignature() } returns prekeySignature

        val publicKeys = conversationCryptoMiddleware.renewHandshakeKeyMaterial(privateKey, publicKey)
        Assertions.assertEquals(publicKeys.signingKey, publicKey)
        Assertions.assertEquals(publicKeys.identityKey, identityKeyPair.publicKey)
        Assertions.assertEquals(publicKeys.signedPrekey, prekeyPair.publicKey)
        Assertions.assertEquals(publicKeys.prekeySignature, prekeySignature)
        Assertions.assertTrue(publicKeys.oneTimePrekeys.first().contentEquals(oneTimePrekeyPair.publicKey.dataKey()))

        coVerify { cryptoStorageManager.saveOneTimePrekeyPairs(listOf(oneTimePrekeyPair.dataKeyPair())) }
    }

    @Test
    fun initConversation() = runBlockingTest {
        val userId = UserId.randomUUID()
        val conversationId = ConversationId.randomUUID()

        val identityKeyPair = KeyPair(Key.fromPlainString("publicIdentityKey"), Key.fromPlainString("privateIdentityKey"))
        val prekeyPair = KeyPair(Key.fromPlainString("publicPrekey"), Key.fromPlainString("privatePrekey"))

        val remoteIdentityKey = "remoteIdentityKey".encodeToByteArray()
        val remotePrekey = "remotePrekey".encodeToByteArray()
        val remoteOneTimePrekey = "remoteOneTimePrekey".encodeToByteArray()

        val signingInstance = java.security.Signature.getInstance(cryptoParams.signingAlgorithm)
        signingInstance.initSign(privateKey.signingKey())
        signingInstance.update(remotePrekey)
        val remotePrekeySignature = signingInstance.sign()

        val keyAgreementInitiation = X3DH.KeyAgreementInitiation("sharedSecret".encodeToByteArray(), ByteArray(0), Key.fromPlainString("ephemeralPublicKey"))

        val verifierSlot = slot<PrekeySignatureVerifier>()
        every { anyConstructed<X3DH>().initiateKeyAgreement(
            Key.fromBytes(remoteIdentityKey),
            Key.fromBytes(remotePrekey),
            remotePrekeySignature,
            Key.fromBytes(remoteOneTimePrekey),
            identityKeyPair,
            prekeyPair.publicKey,
            capture(verifierSlot),
            cryptoParams.info
        ) } answers {
            Assertions.assertTrue(verifierSlot.captured(remotePrekeySignature))
            keyAgreementInitiation
        }

        val sessionState = SessionState(
            Key.fromPlainString("rootKey"),
            KeyPair(Key.fromPlainString("rootChainPublicKey"), Key.fromPlainString("rootChainPrivateKey")),
            Key.fromPlainString("rootChainRemotePublicKey"),
            Key.fromPlainString("sendingChainKey"),
            Key.fromPlainString("receivingChainKey"),
            42,
            1337,
            4711,
            cryptoParams.info,
            cryptoParams.maxSkip
        )
        every { doubleRatchetProvider.provideDoubleRatchet(
            null,
            remotePrekey.cryptoKey(),
            keyAgreementInitiation.sharedSecret,
            cryptoParams.maxSkip,
            cryptoParams.info,
            messageKeyCache,
            sodium
        ) } returns doubleRatchet
        every { doubleRatchet.sessionState } returns sessionState

        coEvery { cryptoStorageManager.loadIdentityKeyPair() } returns identityKeyPair.dataKeyPair()
        coEvery { cryptoStorageManager.loadPrekeyPair() } returns prekeyPair.dataKeyPair()
        coEvery { cryptoStorageManager.messageKeyCache(conversationId) } returns messageKeyCache

        val conversationInvitation = conversationCryptoMiddleware.initConversation(
            userId,
            conversationId,
            remoteIdentityKey,
            remotePrekey,
            remotePrekeySignature,
            remoteOneTimePrekey,
            publicKey
        )

        Assertions.assertTrue(conversationInvitation.identityKey.contentEquals(identityKeyPair.publicKey.dataKey()))
        Assertions.assertTrue(conversationInvitation.ephemeralKey.contentEquals(keyAgreementInitiation.ephemeralPublicKey.dataKey()))
        Assertions.assertEquals(conversationInvitation.usedOneTimePrekey, remoteOneTimePrekey)

        coVerify { cryptoStorageManager.saveConversationState(ConversationState(
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
        )) }
    }

    @Test
    fun initConversationInvalidPrekeySignature() = runBlockingTest {
        val userId = UserId.randomUUID()
        val conversationId = ConversationId.randomUUID()

        val identityKeyPair = KeyPair(Key.fromPlainString("publicIdentityKey"), Key.fromPlainString("privateIdentityKey"))
        val prekeyPair = KeyPair(Key.fromPlainString("publicPrekey"), Key.fromPlainString("privatePrekey"))

        val remoteIdentityKey = "remoteIdentityKey".encodeToByteArray()
        val remotePrekey = "remotePrekey".encodeToByteArray()
        val remoteOneTimePrekey = "remoteOneTimePrekey".encodeToByteArray()

        val remotePrekeySignature = "invalidSignature".encodeToByteArray()

        val verifierSlot = slot<PrekeySignatureVerifier>()
        every { anyConstructed<X3DH>().initiateKeyAgreement(
            Key.fromBytes(remoteIdentityKey),
            Key.fromBytes(remotePrekey),
            remotePrekeySignature,
            Key.fromBytes(remoteOneTimePrekey),
            identityKeyPair,
            prekeyPair.publicKey,
            capture(verifierSlot),
            cryptoParams.info
        ) } answers {
            Assertions.assertFalse(verifierSlot.captured(remotePrekeySignature))
            throw SignatureException()
        }

        coEvery { cryptoStorageManager.loadIdentityKeyPair() } returns identityKeyPair.dataKeyPair()
        coEvery { cryptoStorageManager.loadPrekeyPair() } returns prekeyPair.dataKeyPair()

        assertThrows<SignatureException> {
            runBlockingTest {
                conversationCryptoMiddleware.initConversation(
                    userId,
                    conversationId,
                    remoteIdentityKey,
                    remotePrekey,
                    remotePrekeySignature,
                    remoteOneTimePrekey,
                    publicKey
                )
            }
        }
    }

    @Test
    fun processConversationInvitation() = runBlockingTest {
        val userId = UserId.randomUUID()
        val conversationId = ConversationId.randomUUID()

        val identityKeyPair = tice.models.KeyPair("privateIdentityKey".encodeToByteArray(), "publicIdentityKey".encodeToByteArray())
        val prekeyPair = tice.models.KeyPair("privatePrekey".encodeToByteArray(), "publicPrekey".encodeToByteArray())
        val oneTimePrekeyPair = tice.models.KeyPair("oneTimePrivateKey".encodeToByteArray(), "oneTimePublicKey".encodeToByteArray())

        val remoteIdentityKey = "remoteIdentityKey".encodeToByteArray()
        val remoteEphemeralKey = "remoteEphemeralKey".encodeToByteArray()

        val conversationInvitation = ConversationInvitation(remoteIdentityKey, remoteEphemeralKey, oneTimePrekeyPair.publicKey)
        val sharedSecret = "sharedSecret".encodeToByteArray()

        every { anyConstructed<X3DH>().sharedSecretFromKeyAgreement(
            remoteIdentityKey.cryptoKey(),
            remoteEphemeralKey.cryptoKey(),
            oneTimePrekeyPair.cryptoKeyPair(),
            identityKeyPair.cryptoKeyPair(),
            prekeyPair.cryptoKeyPair(),
            cryptoParams.info
        ) } returns sharedSecret

        val sessionState = SessionState(
            Key.fromPlainString("rootKey"),
            KeyPair(Key.fromPlainString("rootChainPublicKey"), Key.fromPlainString("rootChainPrivateKey")),
            Key.fromPlainString("rootChainRemotePublicKey"),
            Key.fromPlainString("sendingChainKey"),
            Key.fromPlainString("receivingChainKey"),
            42,
            1337,
            4711,
            cryptoParams.info,
            cryptoParams.maxSkip
        )
        every { doubleRatchetProvider.provideDoubleRatchet(
            prekeyPair.cryptoKeyPair(),
            null,
            sharedSecret,
            cryptoParams.maxSkip,
            cryptoParams.info,
            messageKeyCache,
            sodium
        ) } returns doubleRatchet
        every { doubleRatchet.sessionState } returns sessionState

        coEvery { cryptoStorageManager.loadIdentityKeyPair() } returns identityKeyPair
        coEvery { cryptoStorageManager.loadPrekeyPair() } returns prekeyPair
        coEvery { cryptoStorageManager.loadPrivateOneTimePrekey(oneTimePrekeyPair.publicKey) } returns oneTimePrekeyPair.privateKey
        coEvery { cryptoStorageManager.messageKeyCache(conversationId) } returns messageKeyCache

        conversationCryptoMiddleware.processConversationInvitation(conversationInvitation, userId, conversationId)

        coVerify { cryptoStorageManager.saveConversationState(ConversationState(
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
        )) }
        coVerify { cryptoStorageManager.deleteOneTimePrekeyPair(oneTimePrekeyPair.publicKey) }
    }

    @Test
    fun processConversationInvitationWithoutOneTimePrekey() {
        val conversationInvitation = ConversationInvitation(ByteArray(0), ByteArray(0), null)

        assertThrows<ConversationCryptoMiddlewareException.OneTimePrekeyMissingException> {
            runBlockingTest {
                conversationCryptoMiddleware.processConversationInvitation(conversationInvitation, UserId.randomUUID(), ConversationId.randomUUID())
            }
        }
    }

    @Test
    fun conversationExisting() = runBlockingTest {
        val userId = UserId.randomUUID()
        val conversationId = ConversationId.randomUUID()

        val conversationState = ConversationState(
            userId,
            conversationId,
            ByteArray(0),
            ByteArray(0),
            ByteArray(0),
            null,
            null,
            null,
            0,
            0,
            0
        )
        coEvery { cryptoStorageManager.loadConversationState(userId, conversationId) } returnsMany listOf(null, conversationState)

        Assertions.assertFalse(conversationCryptoMiddleware.conversationExisting(userId, conversationId))
        Assertions.assertTrue(conversationCryptoMiddleware.conversationExisting(userId, conversationId))
    }

    @ExperimentalUnsignedTypes
    @Test
    fun conversationFingerprint() {
        val header = Header(Key.fromPlainString("publicKey"), 10, 10)
        val message = Message(header, "cipher".encodeToByteArray())
        val encodedMessage = Json.encodeToString(MessageSerializer, message).encodeToByteArray()

        Assertions.assertEquals(conversationCryptoMiddleware.conversationFingerprint(encodedMessage), sodium.sodiumBin2Hex(message.header.publicKey.asBytes))
    }

    @ExperimentalUnsignedTypes
    @ExperimentalStdlibApi
    @Test
    fun encryption() = runBlockingTest {
        val userId = UserId.randomUUID()
        val conversationId = ConversationId.randomUUID()
        val plaintext = "plaintext".encodeToByteArray()

        val header = Header(Key.fromPlainString("publicKey"), 10, 10)
        val message = Message(header, "cipher".encodeToByteArray())

        val conversationStateBeforeEncryption = ConversationState(
            userId,
            conversationId,
            "rootKey".encodeToByteArray(),
            "rootChainPublicKey".encodeToByteArray(),
            "rootChainPrivateKey".encodeToByteArray(),
            "rootChainRemotePublicKey".encodeToByteArray(),
            "sendingChainKey".encodeToByteArray(),
            "receivingChainKey".encodeToByteArray(),
            2,
            2,
            2
        )

        val expectedSessionStateBeforeEncryption = SessionState(
            conversationStateBeforeEncryption.rootKey.cryptoKey(),
            conversationStateBeforeEncryption.rootChainKeyPair.cryptoKeyPair(),
            conversationStateBeforeEncryption.rootChainRemotePublicKey?.cryptoKey(),
            conversationStateBeforeEncryption.sendingChainKey?.cryptoKey(),
            conversationStateBeforeEncryption.receivingChainKey?.cryptoKey(),
            conversationStateBeforeEncryption.sendMessageNumber,
            conversationStateBeforeEncryption.receivedMessageNumber,
            conversationStateBeforeEncryption.previousSendingChanLength,
            cryptoParams.info,
            cryptoParams.maxSkip
        )

        val sessionStateAfterEncryption = SessionState(
            Key.fromPlainString("rootKey2"),
            KeyPair(Key.fromPlainString("rootChainPublicKey2"), Key.fromPlainString("rootChainPrivateKey2")),
            Key.fromPlainString("rootChainRemotePublicKey2"),
            Key.fromPlainString("sendingChainKey2"),
            Key.fromPlainString("receivingChainKey2"),
            11,
            11,
            11,
            cryptoParams.info,
            cryptoParams.maxSkip
        )

        val expectedConversationStateAfterEncryption = ConversationState(
            userId,
            conversationId,
            sessionStateAfterEncryption.rootKey.dataKey(),
            sessionStateAfterEncryption.rootChainKeyPair.publicKey.dataKey(),
            sessionStateAfterEncryption.rootChainKeyPair.secretKey.dataKey(),
            sessionStateAfterEncryption.rootChainRemotePublicKey?.dataKey(),
            sessionStateAfterEncryption.sendingChainKey?.dataKey(),
            sessionStateAfterEncryption.receivingChainKey?.dataKey(),
            sessionStateAfterEncryption.sendMessageNumber,
            sessionStateAfterEncryption.receivedMessageNumber,
            sessionStateAfterEncryption.previousSendingChainLength
        )

        coEvery { cryptoStorageManager.messageKeyCache(conversationId) } returns messageKeyCache
        coEvery { cryptoStorageManager.loadConversationState(userId, conversationId) } returns conversationStateBeforeEncryption
        every { doubleRatchetProvider.provideDoubleRatchet(expectedSessionStateBeforeEncryption, messageKeyCache, sodium) } returns doubleRatchet
        every { doubleRatchet.encrypt(plaintext) } returns message
        every { doubleRatchet.sessionState } returns sessionStateAfterEncryption

        val ciphertext = conversationCryptoMiddleware.encrypt(plaintext, userId, conversationId)

        Assertions.assertTrue(ciphertext.contentEquals(Json.encodeToString(MessageSerializer, message).encodeToByteArray()))

        coVerify { cryptoStorageManager.saveConversationState(expectedConversationStateAfterEncryption) }
    }

    @ExperimentalStdlibApi
    @Test
    fun encryptionUnknownConversation() {
        coEvery { cryptoStorageManager.loadConversationState(any(), any()) } returns null

        assertThrows<ConversationCryptoMiddlewareException.ConversationNotInitializedException> {
            runBlockingTest {
                conversationCryptoMiddleware.encrypt(ByteArray(0), UserId.randomUUID(), ConversationId.randomUUID())
            }
        }
    }

    @ExperimentalStdlibApi
    @ExperimentalUnsignedTypes
    @Test
    fun decryption() = runBlockingTest {
        val userId = UserId.randomUUID()
        val conversationId = ConversationId.randomUUID()

        val plaintext = "plaintext".encodeToByteArray()
        val header = Header(Key.fromPlainString("publicKey"), 10, 10)
        val message = Message(header, "cipher".encodeToByteArray())
        val encryptedSecretKey = Json.encodeToString(MessageSerializer, message).encodeToByteArray()
        val secretKey = "secretKey".encodeToByteArray()
        val encryptedMessage = "encryptedMessage".encodeToByteArray()

        val conversationStateBeforeDecryption = ConversationState(
            userId,
            conversationId,
            "rootKey".encodeToByteArray(),
            "rootChainPublicKey".encodeToByteArray(),
            "rootChainPrivateKey".encodeToByteArray(),
            "rootChainRemotePublicKey".encodeToByteArray(),
            "sendingChainKey".encodeToByteArray(),
            "receivingChainKey".encodeToByteArray(),
            2,
            2,
            2
        )

        val expectedSessionStateBeforeDecryption = SessionState(
            conversationStateBeforeDecryption.rootKey.cryptoKey(),
            conversationStateBeforeDecryption.rootChainKeyPair.cryptoKeyPair(),
            conversationStateBeforeDecryption.rootChainRemotePublicKey?.cryptoKey(),
            conversationStateBeforeDecryption.sendingChainKey?.cryptoKey(),
            conversationStateBeforeDecryption.receivingChainKey?.cryptoKey(),
            conversationStateBeforeDecryption.sendMessageNumber,
            conversationStateBeforeDecryption.receivedMessageNumber,
            conversationStateBeforeDecryption.previousSendingChanLength,
            cryptoParams.info,
            cryptoParams.maxSkip
        )

        val sessionStateAfterDecryption = SessionState(
            Key.fromPlainString("rootKey2"),
            KeyPair(Key.fromPlainString("rootChainPublicKey2"), Key.fromPlainString("rootChainPrivateKey2")),
            Key.fromPlainString("rootChainRemotePublicKey2"),
            Key.fromPlainString("sendingChainKey2"),
            Key.fromPlainString("receivingChainKey2"),
            11,
            11,
            11,
            cryptoParams.info,
            cryptoParams.maxSkip
        )

        val expectedConversationStateAfterDecryption = ConversationState(
            userId,
            conversationId,
            sessionStateAfterDecryption.rootKey.dataKey(),
            sessionStateAfterDecryption.rootChainKeyPair.publicKey.dataKey(),
            sessionStateAfterDecryption.rootChainKeyPair.secretKey.dataKey(),
            sessionStateAfterDecryption.rootChainRemotePublicKey?.dataKey(),
            sessionStateAfterDecryption.sendingChainKey?.dataKey(),
            sessionStateAfterDecryption.receivingChainKey?.dataKey(),
            sessionStateAfterDecryption.sendMessageNumber,
            sessionStateAfterDecryption.receivedMessageNumber,
            sessionStateAfterDecryption.previousSendingChainLength
        )

        coEvery { cryptoStorageManager.messageKeyCache(conversationId) } returns messageKeyCache
        coEvery { cryptoStorageManager.loadConversationState(userId, conversationId) } returns conversationStateBeforeDecryption
        every { doubleRatchetProvider.provideDoubleRatchet(expectedSessionStateBeforeDecryption, messageKeyCache, sodium) } returns doubleRatchet
        every { doubleRatchet.sessionState } returns sessionStateAfterDecryption
        every { cryptoManager.decrypt(encryptedMessage, secretKey) } returns plaintext
        val messageSlot = slot<Message>()
        coEvery { doubleRatchet.decrypt(capture(messageSlot)) } answers {
            Assertions.assertEquals(messageSlot.captured.header.messageNumber, message.header.messageNumber)
            Assertions.assertEquals(messageSlot.captured.header.numberOfMessagesInPreviousSendingChain, message.header.numberOfMessagesInPreviousSendingChain)
            Assertions.assertEquals(messageSlot.captured.header.publicKey, message.header.publicKey)
            Assertions.assertTrue(messageSlot.captured.cipher.contentEquals(message.cipher))

            secretKey
        }

        val decryptedPlaintext = conversationCryptoMiddleware.decrypt(encryptedMessage, encryptedSecretKey, userId, conversationId)

        Assertions.assertTrue(decryptedPlaintext.contentEquals(plaintext))

        coVerify { cryptoStorageManager.saveConversationState(expectedConversationStateAfterDecryption) }
    }

    @ExperimentalUnsignedTypes
    @Test
    fun decryptionUnknownConversation() {
        coEvery { cryptoStorageManager.loadConversationState(any(), any()) } returns null

        val header = Header(Key.fromPlainString("publicKey"), 10, 10)
        val message = Message(header, "cipher".encodeToByteArray())
        val encryptedSecretKey = Json.encodeToString(MessageSerializer, message).encodeToByteArray()

        assertThrows<ConversationCryptoMiddlewareException.ConversationNotInitializedException> {
            runBlockingTest {
                conversationCryptoMiddleware.decrypt(ByteArray(0), encryptedSecretKey, UserId.randomUUID(), ConversationId.randomUUID())
            }
        }
    }

    @ExperimentalStdlibApi
    @ExperimentalUnsignedTypes
    @Test
    fun decryptionMaxSkipExceeded() {
        val userId = UserId.randomUUID()
        val conversationId = ConversationId.randomUUID()

        val plaintext = "plaintext".encodeToByteArray()
        val header = Header(Key.fromPlainString("publicKey"), 10, 10)
        val message = Message(header, "cipher".encodeToByteArray())
        val encryptedSecretKey = Json.encodeToString(MessageSerializer, message).encodeToByteArray()
        val encryptedMessage = "encryptedMessage".encodeToByteArray()

        val conversationStateBeforeDecryption = ConversationState(
            userId,
            conversationId,
            "rootKey".encodeToByteArray(),
            "rootChainPublicKey".encodeToByteArray(),
            "rootChainPrivateKey".encodeToByteArray(),
            "rootChainRemotePublicKey".encodeToByteArray(),
            "sendingChainKey".encodeToByteArray(),
            "receivingChainKey".encodeToByteArray(),
            2,
            2,
            2
        )

        val expectedSessionStateBeforeDecryption = SessionState(
            conversationStateBeforeDecryption.rootKey.cryptoKey(),
            conversationStateBeforeDecryption.rootChainKeyPair.cryptoKeyPair(),
            conversationStateBeforeDecryption.rootChainRemotePublicKey?.cryptoKey(),
            conversationStateBeforeDecryption.sendingChainKey?.cryptoKey(),
            conversationStateBeforeDecryption.receivingChainKey?.cryptoKey(),
            conversationStateBeforeDecryption.sendMessageNumber,
            conversationStateBeforeDecryption.receivedMessageNumber,
            conversationStateBeforeDecryption.previousSendingChanLength,
            cryptoParams.info,
            cryptoParams.maxSkip
        )

        coEvery { cryptoStorageManager.messageKeyCache(conversationId) } returns messageKeyCache
        coEvery { cryptoStorageManager.loadConversationState(userId, conversationId) } returns conversationStateBeforeDecryption
        every { doubleRatchetProvider.provideDoubleRatchet(expectedSessionStateBeforeDecryption, messageKeyCache, sodium) } returns doubleRatchet
        coEvery { doubleRatchet.decrypt(any()) } throws DRError.ExceededMaxSkipException()

        assertThrows<DRError.ExceededMaxSkipException> {
            runBlockingTest {
                conversationCryptoMiddleware.decrypt(encryptedMessage, encryptedSecretKey, userId, conversationId)
            }
        }
    }

    @ExperimentalStdlibApi
    @ExperimentalUnsignedTypes
    @Test
    fun decryptionDiscardedOldMessage() {
        val userId = UserId.randomUUID()
        val conversationId = ConversationId.randomUUID()

        val plaintext = "plaintext".encodeToByteArray()
        val header = Header(Key.fromPlainString("publicKey"), 10, 10)
        val message = Message(header, "cipher".encodeToByteArray())
        val encryptedSecretKey = Json.encodeToString(MessageSerializer, message).encodeToByteArray()
        val encryptedMessage = "encryptedMessage".encodeToByteArray()

        val conversationStateBeforeDecryption = ConversationState(
            userId,
            conversationId,
            "rootKey".encodeToByteArray(),
            "rootChainPublicKey".encodeToByteArray(),
            "rootChainPrivateKey".encodeToByteArray(),
            "rootChainRemotePublicKey".encodeToByteArray(),
            "sendingChainKey".encodeToByteArray(),
            "receivingChainKey".encodeToByteArray(),
            2,
            2,
            2
        )

        val expectedSessionStateBeforeDecryption = SessionState(
            conversationStateBeforeDecryption.rootKey.cryptoKey(),
            conversationStateBeforeDecryption.rootChainKeyPair.cryptoKeyPair(),
            conversationStateBeforeDecryption.rootChainRemotePublicKey?.cryptoKey(),
            conversationStateBeforeDecryption.sendingChainKey?.cryptoKey(),
            conversationStateBeforeDecryption.receivingChainKey?.cryptoKey(),
            conversationStateBeforeDecryption.sendMessageNumber,
            conversationStateBeforeDecryption.receivedMessageNumber,
            conversationStateBeforeDecryption.previousSendingChanLength,
            cryptoParams.info,
            cryptoParams.maxSkip
        )

        coEvery { cryptoStorageManager.messageKeyCache(conversationId) } returns messageKeyCache
        coEvery { cryptoStorageManager.loadConversationState(userId, conversationId) } returns conversationStateBeforeDecryption
        every { doubleRatchetProvider.provideDoubleRatchet(expectedSessionStateBeforeDecryption, messageKeyCache, sodium) } returns doubleRatchet
        coEvery { doubleRatchet.decrypt(any()) } throws DRError.DiscardOldMessageException()

        assertThrows<DRError.DiscardOldMessageException> {
            runBlockingTest {
                conversationCryptoMiddleware.decrypt(encryptedMessage, encryptedSecretKey, userId, conversationId)
            }
        }
    }
}