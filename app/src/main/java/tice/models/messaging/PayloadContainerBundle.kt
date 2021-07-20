package tice.models.messaging

data class PayloadContainerBundle(
    val payloadType: Payload.PayloadType,
    val payload: Payload,
    val metaInfo: PayloadMetaInfo
)
