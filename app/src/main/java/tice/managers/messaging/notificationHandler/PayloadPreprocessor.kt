package tice.managers.messaging.notificationHandler

import tice.models.messaging.PayloadContainerBundle

interface PayloadPreprocessor {
    suspend fun preprocess(payloadContainerBundle: PayloadContainerBundle): PayloadContainerBundle
    fun registerPayloadProcessor()
}
