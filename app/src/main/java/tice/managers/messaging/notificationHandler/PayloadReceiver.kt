package tice.managers.messaging.notificationHandler

import tice.models.messaging.PayloadContainerBundle

interface PayloadReceiver {
    fun registerEnvelopeReceiver()
    suspend fun handlePayloadContainerBundle(bundle: PayloadContainerBundle)
}
