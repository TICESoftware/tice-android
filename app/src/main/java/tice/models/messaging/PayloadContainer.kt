package tice.models.messaging

import kotlinx.serialization.Serializable
import tice.utility.serializer.PayloadContainerSerializer

@Serializable(with = PayloadContainerSerializer::class)
data class PayloadContainer(val payloadType: Payload.PayloadType, val payload: Payload)
