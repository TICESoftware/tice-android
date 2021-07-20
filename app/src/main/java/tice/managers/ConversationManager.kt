package tice.managers

import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import tice.backend.BackendType
import tice.crypto.ConversationCryptoMiddlewareType
import tice.dagger.scopes.AppScope
import tice.exceptions.ConversationManagerException
import tice.exceptions.UnexpectedPayloadTypeException
import tice.managers.messaging.PostOfficeType
import tice.managers.messaging.notificationHandler.PayloadPreprocessor
import tice.managers.messaging.notificationHandler.PayloadReceiver
import tice.managers.storageManagers.ConversationStorageManagerType
import tice.models.*
import tice.models.messaging.*
import tice.models.messaging.conversation.ConversationInvitation
import tice.utility.getLogger
import tice.utility.provider.CoroutineContextProviderType
import tice.utility.safeParse
import tice.utility.serializer.PayloadContainerSerializer
import java.lang.ref.WeakReference
import java.util.*
import javax.inject.Inject
import javax.inject.Named

@AppScope
class ConversationManager @Inject constructor(
    private val postOffice: PostOfficeType,
    private val conversationCryptoMiddleware: ConversationCryptoMiddlewareType,
    private val backend: BackendType,
    private val coroutineContextProvider: CoroutineContextProviderType,
    private val conversationStorageManager: ConversationStorageManagerType,
    @Named("COLLAPSING_CONVERSATION_ID") private val COLLAPSING_CONVERSATION_ID: ConversationId,
    @Named("NONCOLLAPSING_CONVERSATION_ID") private val NONCOLLAPSING_CONVERSATION_ID: ConversationId
) : ConversationManagerType, PayloadReceiver, PayloadPreprocessor {
    private val logger by getLogger()

    private lateinit var delegate: WeakReference<ConversationManagerDelegate>

    private val processingMutex: Mutex = Mutex()

    override fun registerEnvelopeReceiver() {
        postOffice.registerEnvelopeReceiver(Payload.PayloadType.ResetConversationV1, this)
    }

    override fun registerPayloadProcessor() {
        postOffice.registerPayloadPreprocessor(Payload.PayloadType.EncryptedPayloadContainerV1, this)
    }

    override fun setDelegate(conversationManagerDelegate: ConversationManagerDelegate) {
        delegate = WeakReference(conversationManagerDelegate)
    }

    private fun conversationId(collapsing: Boolean): ConversationId {
        return if (collapsing) COLLAPSING_CONVERSATION_ID else NONCOLLAPSING_CONVERSATION_ID
    }

    private suspend fun conversationInitialized(userId: UserId, collapsing: Boolean): Boolean {
        return conversationCryptoMiddleware.conversationExisting(userId, conversationId(collapsing))
    }

    private suspend fun initConversation(userId: UserId, collapsing: Boolean) {
        val userKeysResponse = backend.getUserKey(userId)

        val conversationId = conversationId(collapsing)
        val conversationInvitation = conversationCryptoMiddleware.initConversation(
            userId,
            conversationId,
            userKeysResponse.identityKey,
            userKeysResponse.signedPrekey,
            userKeysResponse.prekeySignature,
            userKeysResponse.oneTimePrekey,
            userKeysResponse.signingKey
        )

        conversationStorageManager.storeOutboundConversationInvitation(userId, conversationId, conversationInvitation)

        logger.debug("Created new conversation invitation to initialize conversation with user $userId, collapsing: $collapsing. Used public one-time prekey: ${Base64.encodeToString(userKeysResponse.oneTimePrekey, Base64.DEFAULT)}")
    }

    override suspend fun conversationInvitation(
        userId: UserId,
        collapsing: Boolean
    ): ConversationInvitation? {
        return conversationStorageManager.outboundConversationInvitation(userId, conversationId(collapsing))
    }

    override suspend fun encrypt(data: Data, userId: UserId, collapsing: Boolean): Ciphertext {
        val conversationId = conversationId(collapsing)

        processingMutex.withLock {
            if (!conversationInitialized(userId, collapsing)) {
                initConversation(userId, collapsing)
            }

            return conversationCryptoMiddleware.encrypt(data, userId, conversationId)
        }
    }

    override suspend fun decrypt(bundle: PayloadContainerBundle): PayloadContainerBundle {
        processingMutex.withLock {
            val metaInfo = bundle.metaInfo

            val conversationId = conversationId(metaInfo.collapseId != null)
            val encryptedPayloadContainer = bundle.payload as EncryptedPayloadContainer

            val fingerprint = conversationCryptoMiddleware.conversationFingerprint(encryptedPayloadContainer.encryptedKey)

            logger.debug("Decrypt message from user ${metaInfo.senderId}. Collapsing: ${metaInfo.collapseId != null}. Fingerprint: $fingerprint")

            conversationStorageManager.receivedReset(metaInfo.senderId, conversationId)?.let {
                if (metaInfo.timestamp < it) {
                    logger.debug("Discard message because it is older than the last reset received from the sender.")
                    throw ConversationManagerException.InvalidConversation
                }
            }

            if (metaInfo.conversationInvitation != null) {
                var processConversationInvitation = false

                val lastInvitation = conversationStorageManager.inboundConversationInvitation(metaInfo.senderId, conversationId)
                if (lastInvitation != null) {
                    if (lastInvitation.conversationInvitation() != metaInfo.conversationInvitation && metaInfo.timestamp > lastInvitation.timestamp) {
                        logger.debug("Conversation invitation has not been seen before.")
                        processConversationInvitation = true
                    } else {
                        logger.debug("Ignoring conversation invitation because it should have been processed before.")
                    }
                } else {
                    logger.debug("First conversation invitation for that conversation.")
                    processConversationInvitation = true
                }

                if (processConversationInvitation) {
                    logger.debug("Using conversation invitation to initialize conversation with user ${metaInfo.senderId}. Collapsing: ${metaInfo.collapseId != null}")

                    try {
                        logger.debug("Processing conversation invitation with public one-time prekey ${Base64.encodeToString(metaInfo.conversationInvitation.usedOneTimePrekey, Base64.DEFAULT)}.")
                        conversationCryptoMiddleware.processConversationInvitation(
                            metaInfo.conversationInvitation,
                            metaInfo.senderId,
                            conversationId
                        )
                    } catch (e: Exception) {
                        logger.error("Error processing conversation invitation. Resync conversation.", e)

                        resyncConversation(fingerprint, metaInfo)
                        replyWithReset(metaInfo)

                        throw ConversationManagerException.ConversationResynced
                    } finally {
                        conversationStorageManager.storeInboundConversationInvitation(
                            metaInfo.senderId,
                            conversationId,
                            metaInfo.conversationInvitation,
                            metaInfo.timestamp
                        )
                    }
                } else {
                    logger.debug("Don't process conversation invitation.")
                }
            }

            if (!conversationInitialized(metaInfo.senderId, metaInfo.collapseId != null)) {
                throw ConversationManagerException.ConversationNotInitialized
            }

            conversationStorageManager.invalidConversation(metaInfo.senderId, conversationId)?.let {
                if (it.conversationFingerprint == fingerprint) {
                    if (metaInfo.timestamp > it.resendResetTimeout) {
                        logger.debug("Message from invalid conversation received that is older than a minute after the last sent reset (${it.resendResetTimeout}). Resend reset.")
                        conversationStorageManager.updateInvalidConversation(metaInfo.senderId, conversationId, Date(Date().time + 60))
                        replyWithReset(metaInfo)
                    }
                    logger.debug("This conversation has been invalidated before.")
                    throw ConversationManagerException.InvalidConversation
                }

                if (it.timestamp > metaInfo.timestamp) {
                    throw ConversationManagerException.InvalidConversation
                }
            }

            try {
                val plaintext = conversationCryptoMiddleware.decrypt(
                    encryptedPayloadContainer.ciphertext,
                    encryptedPayloadContainer.encryptedKey,
                    metaInfo.senderId,
                    conversationId
                )

                conversationStorageManager.deleteOutboundConversationInvitation(metaInfo.senderId, conversationId)

                val payloadContainer = Json.safeParse(PayloadContainerSerializer, String(plaintext))
                metaInfo.authenticated = true

                return PayloadContainerBundle(
                    payloadContainer.payloadType,
                    payloadContainer.payload,
                    metaInfo
                )
            } catch (e: Exception) {
                logger.error("Decryption failed.", e)

                resyncConversation(fingerprint, metaInfo)
                replyWithReset(metaInfo)

                throw ConversationManagerException.ConversationResynced
            }
        }
    }

    private suspend fun replyWithReset(metaInfo: PayloadMetaInfo) {
        val receiverCertificate = metaInfo.senderServerSignedMembershipCertificate ?: throw ConversationManagerException.CertificateMissing
        val senderCertificate = metaInfo.receiverSeverSignedMembershipCertificate ?: throw ConversationManagerException.CertificateMissing

        CoroutineScope(coroutineContextProvider.IO).launch {
            try {
                delegate.get()?.sendResetReply(metaInfo.senderId, receiverCertificate, senderCertificate, metaInfo.collapseId)
            } catch (e: Exception) {
                logger.error("something went wrong", e)
            }
        }
    }

    private suspend fun resyncConversation(fingerprint: ConversationFingerprint, metaInfo: PayloadMetaInfo) {
        logger.trace("Invalidating conversation.")

        val conversationId = conversationId(metaInfo.collapseId != null)
        conversationStorageManager.storeInvalidConversation(metaInfo.senderId, conversationId, fingerprint, metaInfo.timestamp, Date())

        initConversation(metaInfo.senderId, metaInfo.collapseId != null)
    }

    override suspend fun handlePayloadContainerBundle(bundle: PayloadContainerBundle) {
        if (bundle.payload !is ResetConversation) {
            throw UnexpectedPayloadTypeException
        }
    }

    override suspend fun preprocess(payloadContainerBundle: PayloadContainerBundle): PayloadContainerBundle {
        if (payloadContainerBundle.payload !is EncryptedPayloadContainer) {
            throw UnexpectedPayloadTypeException
        }

        return decrypt(payloadContainerBundle)
    }
}
