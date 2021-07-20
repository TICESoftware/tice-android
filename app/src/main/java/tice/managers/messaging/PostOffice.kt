package tice.managers.messaging

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tice.backend.BackendType
import tice.dagger.scopes.AppScope
import tice.managers.messaging.notificationHandler.PayloadPreprocessor
import tice.managers.messaging.notificationHandler.PayloadReceiver
import tice.models.messaging.Envelope
import tice.models.messaging.Payload.PayloadType
import tice.models.messaging.PayloadContainer
import tice.models.messaging.PayloadContainerBundle
import tice.models.messaging.PayloadMetaInfo
import tice.utility.getLogger
import tice.utility.provider.CoroutineContextProviderType
import java.lang.ref.WeakReference
import javax.inject.Inject

@AppScope
class PostOffice @Inject constructor(
    private val coroutineContextProvider: CoroutineContextProviderType,
    private val backend: BackendType
) : PostOfficeType {
    private val logger by getLogger()

    private val handlers = HashMap<PayloadType, WeakReference<PayloadReceiver>>()
    private val preprocessors = HashMap<PayloadType, WeakReference<PayloadPreprocessor>>()

    override fun registerPayloadPreprocessor(type: PayloadType, preprocessor: PayloadPreprocessor) {
        preprocessors[type] = WeakReference(preprocessor)
    }

    override fun registerEnvelopeReceiver(type: PayloadType, handler: PayloadReceiver) {
        if (handlers[type] != null) {
            logger.warn("A handler has already been registered: ${handlers[type]?.get()?.javaClass}. Overwriting.")
        }
        handlers[type] = WeakReference(handler)
    }

    override fun receiveEnvelope(envelope: Envelope) {
        CoroutineScope(coroutineContextProvider.IO).launch {
            receiveSync(envelope)
        }
    }

    override suspend fun fetchMessages() {
        val getMessagesResponse = backend.getMessages()
        logger.debug("Fetch ${getMessagesResponse.messages.size} messages")

        getMessagesResponse.messages.forEach { envelope ->
            try {
                receiveSync(envelope)
            } catch (e: Throwable) {
                logger.error("Receiving envelope failed with messageId: ${envelope.id}", e)
            }
        }
    }

    private suspend fun receiveSync(envelope: Envelope) {
        try {
            val payloadContainerBundle = unpackEnvelope(envelope)
            val type = payloadContainerBundle.payloadType

            logger.debug("Received envelope with payload type: $type")

            handlers[type]?.get()?.handlePayloadContainerBundle(payloadContainerBundle) ?: run {
                logger.warn("No handler registered for payload type $type.")
                return
            }
        } catch (e: Exception) {
            logger.error("Processing envelope failed.", e)
        }
    }

    private suspend fun unpackEnvelope(envelope: Envelope): PayloadContainerBundle {
        val metaInfo = PayloadMetaInfo(
            envelope.senderId,
            envelope.timestamp,
            envelope.collapseId,
            envelope.senderServerSignedMembershipCertificate,
            envelope.receiverServerSignedMembershipCertificate,
            false,
            envelope.conversationInvitation
        )

        return preprocess(envelope.payloadContainer, metaInfo)
    }

    private suspend fun preprocess(payloadContainer: PayloadContainer, metaInfo: PayloadMetaInfo): PayloadContainerBundle {
        val payloadContainerBundle = PayloadContainerBundle(payloadContainer.payloadType, payloadContainer.payload, metaInfo)

        return preprocessors[payloadContainer.payloadType]?.get()?.let {
            val extractedPayloadContainerBundle = it.preprocess(payloadContainerBundle)
            val extractedPayloadContainer =
                PayloadContainer(extractedPayloadContainerBundle.payloadType, extractedPayloadContainerBundle.payload)

            logger.debug("Decoded payload container of type ${extractedPayloadContainer.payloadType} from ${metaInfo.senderId}${(metaInfo.conversationInvitation?.let { " with conversation invitation" } ?: "")}.")

            preprocess(extractedPayloadContainer, extractedPayloadContainerBundle.metaInfo)
        } ?: payloadContainerBundle
    }
}
