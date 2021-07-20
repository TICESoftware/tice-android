package tice.managers

import io.mockk.*
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tice.backend.BackendType
import tice.crypto.ConversationCryptoMiddlewareType
import tice.exceptions.ConversationManagerException
import tice.helper.joinAllChildren
import tice.managers.messaging.PostOfficeType
import tice.managers.storageManagers.ConversationStorageManagerType
import tice.models.ConversationFingerprint
import tice.models.ConversationId
import tice.models.UserId
import tice.models.UserPublicKeys
import tice.models.messaging.*
import tice.models.messaging.conversation.ConversationInvitation
import tice.models.messaging.conversation.InboundConversationInvitation
import tice.models.messaging.conversation.InvalidConversation
import tice.models.responses.GetUserPublicKeysResponse
import tice.utility.provider.CoroutineContextProviderType
import tice.utility.serializer.PayloadContainerSerializer
import java.util.*

internal class ConversationManagerTest {

    lateinit var conversationManager: ConversationManager

    private val mockPostOffice: PostOfficeType = mockk(relaxUnitFun = true)
    private val mockConversationCryptoMiddleware: ConversationCryptoMiddlewareType = mockk(relaxUnitFun = true)
    private val mockBackend: BackendType = mockk(relaxUnitFun = true)
    private val mockMailbox: ConversationManagerDelegate = mockk(relaxUnitFun = true)
    private val mockConversationStorageManager: ConversationStorageManagerType = mockk(relaxUnitFun = true)
    private val mockCoroutineContextProvider: CoroutineContextProviderType = mockk(relaxUnitFun = true)

    private val collapsingConversationId: ConversationId = UUID.fromString("00000000-0000-0000-0000-000000000000")
    private val nonCollapsingConversationId: ConversationId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    val conversationFingerprint: ConversationFingerprint = "conversationFingerprint"

    lateinit var testJob: CompletableJob

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        testJob = Job()

        conversationManager = ConversationManager(
            mockPostOffice,
            mockConversationCryptoMiddleware,
            mockBackend,
            mockCoroutineContextProvider,
            mockConversationStorageManager,
            collapsingConversationId,
            nonCollapsingConversationId
        )
    }

    @Test
    fun init_RegisterDelegates() = runBlockingTest {
        conversationManager.registerEnvelopeReceiver()
        conversationManager.registerPayloadProcessor()

        verify(exactly = 1) {
            mockPostOffice.registerPayloadPreprocessor(
                Payload.PayloadType.EncryptedPayloadContainerV1,
                conversationManager
            )
        }
        verify(exactly = 1) { mockPostOffice.registerEnvelopeReceiver(Payload.PayloadType.ResetConversationV1, conversationManager) }
    }

    @Test
    fun encryptNewConversation() = runBlockingTest {
        val receiverId = UserId.randomUUID()

        val receiverPublicKeys = UserPublicKeys(
            "signingKey".toByteArray(),
            "identityKey".toByteArray(),
            "signedPrekey".toByteArray(),
            "prekeySignature".toByteArray(),
            listOf("oneTimePrekey".toByteArray())
        )

        val getUserPublicKeysResponse = GetUserPublicKeysResponse(
            receiverPublicKeys.signingKey,
            receiverPublicKeys.identityKey,
            receiverPublicKeys.signedPrekey,
            receiverPublicKeys.prekeySignature,
            receiverPublicKeys.oneTimePrekeys.first()
        )

        coEvery { mockBackend.getUserKey(receiverId) } returns getUserPublicKeysResponse

        val conversationInvitation = ConversationInvitation(
            "senderIdentityKey".toByteArray(),
            "senderEphemeralKey".toByteArray(),
            "usedOneTimePrekey".toByteArray()
        )

        coEvery { mockConversationCryptoMiddleware.conversationExisting(receiverId, nonCollapsingConversationId) } returns false
        coEvery {
            mockConversationCryptoMiddleware.initConversation(
                receiverId, nonCollapsingConversationId,
                receiverPublicKeys.identityKey,
                receiverPublicKeys.signedPrekey,
                receiverPublicKeys.prekeySignature,
                receiverPublicKeys.oneTimePrekeys.first(),
                receiverPublicKeys.signingKey
            )
        } returns conversationInvitation

        val plaintext = "plaintext".toByteArray()
        val ciphertext = "ciphertext".toByteArray()
        coEvery { mockConversationCryptoMiddleware.encrypt(plaintext, receiverId, nonCollapsingConversationId) } returns ciphertext

        val encryptedMessage = conversationManager.encrypt(plaintext, receiverId, false)

        Assertions.assertEquals(encryptedMessage, ciphertext)

        coVerify {
            mockConversationStorageManager.storeOutboundConversationInvitation(
                receiverId,
                nonCollapsingConversationId,
                conversationInvitation
            )
        }
    }

    @Test
    fun encryptInitializedConversation() = runBlockingTest {
        val receiverId = UserId.randomUUID()

        coEvery { mockConversationCryptoMiddleware.conversationExisting(receiverId, nonCollapsingConversationId) } returns true

        val plaintext = "plaintext".toByteArray()
        val ciphertext = "ciphertext".toByteArray()
        coEvery { mockConversationCryptoMiddleware.encrypt(plaintext, receiverId, nonCollapsingConversationId) } returns ciphertext

        val encryptedMessage = conversationManager.encrypt(plaintext, receiverId, false)

        Assertions.assertEquals(encryptedMessage, ciphertext)
    }

    @Test
    fun discardOutgoingConversationInvitation() = runBlockingTest {
        val userId = UserId.randomUUID()

        val conversationInvitation = ConversationInvitation(
            "senderIdentityKey".toByteArray(),
            "senderEphemeralKey".toByteArray(),
            "usedOneTimePrekey".toByteArray()
        )

        coEvery {
            mockConversationStorageManager.outboundConversationInvitation(
                userId,
                nonCollapsingConversationId
            )
        } returns conversationInvitation

        val payloadContainer = PayloadContainer(Payload.PayloadType.ResetConversationV1, ResetConversation)
        val payloadMetaInfo = PayloadMetaInfo(
            userId,
            Date(),
            null,
            null,
            null,
            false,
            conversationInvitation
        )
        val encryptedPayload = Json.encodeToString(PayloadContainerSerializer, payloadContainer).toByteArray()
        val encryptedKey = "encryptedKey".toByteArray()
        val encryptedPayloadContainer = EncryptedPayloadContainer(encryptedPayload, encryptedKey)
        val serializedPayloadContainer = Json.encodeToString(PayloadContainerSerializer, payloadContainer).toByteArray()

        coEvery { mockConversationCryptoMiddleware.conversationExisting(userId, nonCollapsingConversationId) } returns true
        every { mockConversationCryptoMiddleware.conversationFingerprint(encryptedKey) } returns conversationFingerprint
        coEvery { mockConversationStorageManager.inboundConversationInvitation(userId, nonCollapsingConversationId) } returns null
        coEvery { mockConversationStorageManager.invalidConversation(userId, nonCollapsingConversationId) } returns null
        coEvery { mockConversationStorageManager.receivedReset(userId, nonCollapsingConversationId) } returns null
        coEvery {
            mockConversationCryptoMiddleware.decrypt(
                encryptedPayload,
                encryptedKey,
                userId,
                nonCollapsingConversationId
            )
        } returns serializedPayloadContainer

        val payloadContainerBundle =
            PayloadContainerBundle(Payload.PayloadType.EncryptedPayloadContainerV1, encryptedPayloadContainer, payloadMetaInfo)

        conversationManager.decrypt(payloadContainerBundle)

        coVerify { mockConversationStorageManager.deleteOutboundConversationInvitation(userId, nonCollapsingConversationId) }
    }

    @Test
    fun decryptUninitializedConversationWithoutConversationInvitation() = runBlockingTest {
        val userId = UserId.randomUUID()
        val payloadMetaInfo = PayloadMetaInfo(
            userId,
            Date(),
            null,
            null,
            null,
            false,
            null
        )
        val encryptedPayload = "encryptedPayload".toByteArray()
        val encryptedKey = "encryptedKey".toByteArray()
        val encryptedPayloadContainer = EncryptedPayloadContainer(encryptedPayload, encryptedKey)

        val payloadContainerBundle =
            PayloadContainerBundle(Payload.PayloadType.EncryptedPayloadContainerV1, encryptedPayloadContainer, payloadMetaInfo)

        every { mockConversationCryptoMiddleware.conversationFingerprint(encryptedKey) } returns conversationFingerprint
        coEvery { mockConversationCryptoMiddleware.conversationExisting(userId, nonCollapsingConversationId) } returns false
        coEvery { mockConversationStorageManager.receivedReset(userId, nonCollapsingConversationId) } returns null

        Assertions.assertThrows(ConversationManagerException.ConversationNotInitialized::class.java) {
            runBlockingTest {
                conversationManager.decrypt(payloadContainerBundle)
            }
        }
    }

    @Test
    fun testDecryptionWithConversationInvitation() = runBlockingTest {
        val payloadContainer = PayloadContainer(Payload.PayloadType.ResetConversationV1, ResetConversation)
        val userId = UserId.randomUUID()
        val conversationInvitation = ConversationInvitation(
            "senderIdentityKey".toByteArray(),
            "senderEphemeralKey".toByteArray(),
            "usedOneTimePrekey".toByteArray()
        )
        val payloadMetaInfo = PayloadMetaInfo(
            userId,
            Date(),
            null,
            null,
            null,
            false,
            conversationInvitation
        )

        val encryptedPayload = Json.encodeToString(PayloadContainerSerializer, payloadContainer).toByteArray()
        val encryptedKey = "encryptedKey".toByteArray()
        val encryptedPayloadContainer = EncryptedPayloadContainer(encryptedPayload, encryptedKey)
        val serializedPayloadContainer = Json.encodeToString(PayloadContainerSerializer, payloadContainer).toByteArray()

        coEvery { mockConversationCryptoMiddleware.conversationExisting(userId, nonCollapsingConversationId) } returns true
        every { mockConversationCryptoMiddleware.conversationFingerprint(encryptedKey) } returns conversationFingerprint
        coEvery {
            mockConversationCryptoMiddleware.decrypt(
                encryptedPayload,
                encryptedKey,
                userId,
                nonCollapsingConversationId
            )
        } returns serializedPayloadContainer

        coEvery { mockConversationStorageManager.receivedReset(userId, nonCollapsingConversationId) } returns null
        coEvery { mockConversationStorageManager.inboundConversationInvitation(userId, nonCollapsingConversationId) } returns
            InboundConversationInvitation(
                userId,
                nonCollapsingConversationId,
                conversationInvitation.identityKey,
                conversationInvitation.ephemeralKey,
                conversationInvitation.usedOneTimePrekey,
                Date()
            )
        coEvery { mockConversationStorageManager.invalidConversation(userId, nonCollapsingConversationId) } returns null

        val payloadContainerBundle =
            PayloadContainerBundle(Payload.PayloadType.EncryptedPayloadContainerV1, encryptedPayloadContainer, payloadMetaInfo)
        val plaintext = conversationManager.decrypt(payloadContainerBundle)

        Assertions.assertEquals(payloadContainer.payloadType, plaintext.payloadType)
        Assertions.assertEquals(payloadContainer.payload, plaintext.payload)
    }

    @Test
    fun conversationInvalidation() = runBlocking {
        val userId = UserId.randomUUID()
        val payloadMetaInfo = PayloadMetaInfo(
            userId,
            Date(),
            null,
            "senderCertificate",
            "receiverCertificate",
            false,
            null
        )
        val encryptedPayload = "ciphertext".toByteArray()
        val encryptedKey = "encryptedKey".toByteArray()
        val encryptedPayloadContainer = EncryptedPayloadContainer(encryptedPayload, encryptedKey)

        val userKeysResponse = GetUserPublicKeysResponse(
            "signignKey".toByteArray(),
            "identityKey".toByteArray(),
            "signedPrekey".toByteArray(),
            "prekeySignature".toByteArray(),
            "oneTimePrekey".toByteArray()
        )

        val conversationInvitation = ConversationInvitation(
            "identityKey".toByteArray(),
            "ephemeralKey".toByteArray(),
            "usedOneTimePrekey".toByteArray()
        )

        every { mockCoroutineContextProvider.IO } returns Dispatchers.Default + testJob
        coEvery { mockBackend.getUserKey(userId) } returns userKeysResponse
        every { mockConversationCryptoMiddleware.conversationFingerprint(encryptedKey) } returns conversationFingerprint
        coEvery { mockConversationCryptoMiddleware.conversationExisting(userId, nonCollapsingConversationId) } returns true
        coEvery { mockConversationCryptoMiddleware.decrypt(encryptedPayload, encryptedKey, userId, nonCollapsingConversationId) } throws RuntimeException()
        coEvery {
            mockConversationCryptoMiddleware.initConversation(
                userId,
                nonCollapsingConversationId,
                userKeysResponse.identityKey,
                userKeysResponse.signedPrekey,
                userKeysResponse.prekeySignature,
                userKeysResponse.oneTimePrekey,
                userKeysResponse.signingKey
            )
        } returns conversationInvitation

        every { mockConversationCryptoMiddleware.conversationFingerprint(encryptedKey) } returns conversationFingerprint
        coEvery { mockConversationStorageManager.inboundConversationInvitation(userId, nonCollapsingConversationId) } returns null
        coEvery { mockConversationStorageManager.invalidConversation(userId, nonCollapsingConversationId) } returns null
        coEvery { mockConversationStorageManager.receivedReset(userId, nonCollapsingConversationId) } returns null

        conversationManager.setDelegate(mockMailbox)

        val payloadContainerBundle =
            PayloadContainerBundle(Payload.PayloadType.EncryptedPayloadContainerV1, encryptedPayloadContainer, payloadMetaInfo)

        Assertions.assertThrows(ConversationManagerException.ConversationResynced::class.java) {
            runBlockingTest {
                conversationManager.decrypt(payloadContainerBundle)
            }
        }

        testJob.joinAllChildren()

        val resetThresholdSlot = slot<Date>()
        coVerify {
            mockConversationStorageManager.storeInvalidConversation(
                userId,
                nonCollapsingConversationId,
                conversationFingerprint,
                payloadMetaInfo.timestamp,
                capture(resetThresholdSlot)
            )
        }
        Assertions.assertEquals(resetThresholdSlot.captured.time.toDouble(), Date().time.toDouble(), 2000.0)

        coVerify {
            mockConversationStorageManager.storeOutboundConversationInvitation(
                userId,
                nonCollapsingConversationId,
                conversationInvitation
            )
        }

        coVerify {
            mockMailbox.sendResetReply(
                userId,
                payloadMetaInfo.senderServerSignedMembershipCertificate!!,
                payloadMetaInfo.receiverSeverSignedMembershipCertificate!!,
                null
            )
        }
    }

    @Test
    fun receiveConversationInvitationAfterInvalidation() = runBlockingTest {
        val userId = UserId.randomUUID()

        val conversationInvitation = ConversationInvitation(
            "identityKey".toByteArray(),
            "ephemeralKey".toByteArray(),
            "usedOneTimePrekey".toByteArray()
        )
        val inboundConversationInvitation = InboundConversationInvitation(
            userId,
            nonCollapsingConversationId,
            conversationInvitation.identityKey,
            conversationInvitation.ephemeralKey,
            conversationInvitation.usedOneTimePrekey,
            Date(Date().time - 10)
        )

        coEvery {
            mockConversationStorageManager.inboundConversationInvitation(
                userId,
                nonCollapsingConversationId
            )
        } returns inboundConversationInvitation

        val payloadMetaInfo = PayloadMetaInfo(
            userId,
            Date(),
            null,
            null,
            null,
            false,
            conversationInvitation
        )

        val encryptedPayload = "ciphertext".toByteArray()
        val encryptedKey = "encryptedKey".toByteArray()
        val encryptedPayloadContainer = EncryptedPayloadContainer(encryptedPayload, encryptedKey)

        val conversationFingerprint = "conversationFingerprint"
        val invalidConversation = InvalidConversation(
            userId,
            nonCollapsingConversationId,
            conversationFingerprint,
            Date(Date().time - 10),
            Date(Date().time + 50)
        )

        coEvery { mockConversationStorageManager.invalidConversation(userId, nonCollapsingConversationId) } returns invalidConversation
        coEvery { mockConversationStorageManager.receivedReset(userId, nonCollapsingConversationId) } returns null

        every { mockConversationCryptoMiddleware.conversationFingerprint(encryptedKey) } returns conversationFingerprint
        coEvery { mockConversationCryptoMiddleware.conversationExisting(userId, nonCollapsingConversationId) } returns true

        val payloadContainerBundle =
            PayloadContainerBundle(Payload.PayloadType.EncryptedPayloadContainerV1, encryptedPayloadContainer, payloadMetaInfo)

        Assertions.assertThrows(ConversationManagerException.InvalidConversation::class.java) {
            runBlockingTest {
                conversationManager.decrypt(payloadContainerBundle)
            }
        }

//        Receive older conversation invitation

        val olderConversationInvitation = ConversationInvitation(
            "identityKey".toByteArray(),
            "odlerEphemeralKey".toByteArray(),
            "olderUsedOneTimePrekey".toByteArray()
        )
        val olderPayloadMetaInfo = PayloadMetaInfo(
            userId,
            Date(Date().time - 60),
            null,
            null,
            null,
            false,
            olderConversationInvitation
        )
        val olderPayloadContainerBundle =
            PayloadContainerBundle(Payload.PayloadType.EncryptedPayloadContainerV1, encryptedPayloadContainer, olderPayloadMetaInfo)

        Assertions.assertThrows(ConversationManagerException.InvalidConversation::class.java) {
            runBlockingTest {
                conversationManager.decrypt(olderPayloadContainerBundle)
            }
        }

//        Receive newer conversation invitation

        val newerConversationInvitation = ConversationInvitation(
            "identityKey".toByteArray(),
            "newerEphemeralKey".toByteArray(),
            "newerUsedOneTimePrekey".toByteArray()
        )
        val newerPayloadMetaInfo = PayloadMetaInfo(
            userId,
            Date(),
            null,
            null,
            null,
            false,
            newerConversationInvitation
        )

        val serializedPayloadContainer = "serializedPayloadContainer".toByteArray()

        coEvery { mockConversationCryptoMiddleware.conversationExisting(userId, nonCollapsingConversationId) } returns true
        coEvery {
            mockConversationCryptoMiddleware.decrypt(
                encryptedPayload,
                encryptedKey,
                userId,
                nonCollapsingConversationId
            )
        } returns serializedPayloadContainer

        val newerPayloadContainerBundle =
            PayloadContainerBundle(Payload.PayloadType.EncryptedPayloadContainerV1, encryptedPayloadContainer, newerPayloadMetaInfo)

        Assertions.assertThrows(ConversationManagerException.InvalidConversation::class.java) {
            runBlockingTest {
                conversationManager.decrypt(newerPayloadContainerBundle)
            }
        }

        coVerify { mockConversationCryptoMiddleware.processConversationInvitation(newerConversationInvitation, userId, nonCollapsingConversationId) }
    }

    @Test
    fun conversationInvitationProcessingFailure() = runBlocking {
        val userId = UserId.randomUUID()

        val conversationFingerprint = "conversationFingerprint"
        val conversationInvitation = ConversationInvitation(
            "identityKey".toByteArray(),
            "ephemeralKey".toByteArray(),
            "usedOneTimePrekey".toByteArray()
        )

        val payloadMetaInfo = PayloadMetaInfo(
            userId,
            Date(),
            null,
            "senderCertificate",
            "receiverCertificate",
            false,
            conversationInvitation
        )
        val encryptedPayload = "ciphertext".toByteArray()
        val encryptedKey = "encryptedKey".toByteArray()
        val encryptedPayloadContainer = EncryptedPayloadContainer(encryptedPayload, encryptedKey)

        val userKeysResponse = GetUserPublicKeysResponse(
            "signignKey".toByteArray(),
            "identityKey".toByteArray(),
            "signedPrekey".toByteArray(),
            "prekeySignature".toByteArray(),
            "oneTimePrekey".toByteArray()
        )

        coEvery { mockBackend.getUserKey(userId) } returns userKeysResponse
        every { mockConversationCryptoMiddleware.conversationFingerprint(encryptedKey) } returns conversationFingerprint
        coEvery { mockConversationCryptoMiddleware.conversationExisting(userId, nonCollapsingConversationId) } returns true
        coEvery {
            mockConversationCryptoMiddleware.processConversationInvitation(
                conversationInvitation,
                userId,
                nonCollapsingConversationId
            )
        } throws RuntimeException()
        every { mockCoroutineContextProvider.IO } returns Dispatchers.Default + testJob

        coEvery { mockConversationStorageManager.inboundConversationInvitation(userId, nonCollapsingConversationId) } returns null
        coEvery { mockConversationStorageManager.receivedReset(userId, nonCollapsingConversationId) } returns null

        val createdConversationInvitation = ConversationInvitation(
            "ownIdentityKey".toByteArray(),
            "ownEphemeralKey".toByteArray(),
            "ownUsedOneTimePrekey".toByteArray()
        )
        coEvery {
            mockConversationCryptoMiddleware.initConversation(
                userId,
                nonCollapsingConversationId,
                userKeysResponse.identityKey,
                userKeysResponse.signedPrekey,
                userKeysResponse.prekeySignature,
                userKeysResponse.oneTimePrekey,
                userKeysResponse.signingKey
            )
        } returns createdConversationInvitation

        conversationManager.setDelegate(mockMailbox)
        val payloadContainerBundle =
            PayloadContainerBundle(Payload.PayloadType.EncryptedPayloadContainerV1, encryptedPayloadContainer, payloadMetaInfo)

        Assertions.assertThrows(ConversationManagerException.ConversationResynced::class.java) {
            runBlockingTest {
                conversationManager.decrypt(payloadContainerBundle)
            }
        }

        testJob.joinAllChildren()

        val resetThresholdCaptor = slot<Date>()
        coVerify {
            mockConversationStorageManager.storeInvalidConversation(
                userId,
                nonCollapsingConversationId,
                conversationFingerprint,
                payloadMetaInfo.timestamp,
                capture(resetThresholdCaptor)
            )
        }

        Assertions.assertEquals(resetThresholdCaptor.captured.time.toDouble(), Date().time.toDouble(), 2000.0)

        coVerify {
            mockConversationStorageManager.storeOutboundConversationInvitation(
                userId,
                nonCollapsingConversationId,
                createdConversationInvitation
            )
        }

        coVerify {
            mockMailbox.sendResetReply(
                userId,
                payloadMetaInfo.senderServerSignedMembershipCertificate!!,
                payloadMetaInfo.receiverSeverSignedMembershipCertificate!!,
                null
            )
        }
    }
}