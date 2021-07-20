package tice.managers.messaging

import tice.managers.messaging.notificationHandler.PayloadPreprocessor
import tice.managers.messaging.notificationHandler.PayloadReceiver
import tice.models.messaging.Envelope
import tice.models.messaging.Payload.PayloadType

interface PostOfficeType {

    fun receiveEnvelope(envelope: Envelope)
    fun registerPayloadPreprocessor(type: PayloadType, preprocessor: PayloadPreprocessor)
    fun registerEnvelopeReceiver(type: PayloadType, handler: PayloadReceiver)
    suspend fun fetchMessages()
}
