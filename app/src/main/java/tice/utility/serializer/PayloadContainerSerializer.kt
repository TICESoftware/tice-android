package tice.utility.serializer

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import tice.models.messaging.*

@Serializer(forClass = PayloadContainer::class)
object PayloadContainerSerializer : KSerializer<PayloadContainer> {

    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("PayloadContainer") {
            element<String>("payloadType")
            element<String>("payload")
        }

    override fun serialize(encoder: Encoder, value: PayloadContainer) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, Payload.PayloadType.serializer(), value.payloadType)

        when (value.payload) {
            is VerificationMessage -> composite.encodeSerializableElement(descriptor, 1, VerificationMessage.serializer(), value.payload)
            is EncryptedPayloadContainer -> composite.encodeSerializableElement(descriptor, 1, EncryptedPayloadContainer.serializer(), value.payload)
            is GroupInvitation -> composite.encodeSerializableElement(descriptor, 1, GroupInvitation.serializer(), value.payload)
            is GroupUpdate -> composite.encodeSerializableElement(descriptor, 1, GroupUpdate.serializer(), value.payload)
            is LocationUpdateV2 -> composite.encodeSerializableElement(descriptor, 1, LocationUpdateV2.serializer(), value.payload)
            is FewOneTimePrekeys -> composite.encodeSerializableElement(descriptor, 1, FewOneTimePrekeys.serializer(), value.payload)
            is UserUpdate -> composite.encodeSerializableElement(descriptor, 1, UserUpdate.serializer(), value.payload)
            is ResetConversation -> composite.encodeSerializableElement(descriptor, 1, ResetConversation.serializer(), value.payload)
            is ChatMessage -> composite.encodeSerializableElement(descriptor, 1, ChatMessage.serializer(), value.payload)
            is LocationSharingUpdate -> composite.encodeSerializableElement(descriptor, 1, LocationSharingUpdate.serializer(), value.payload)
        }

        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): PayloadContainer {
        val dec: CompositeDecoder = decoder.beginStructure(descriptor)

        var payloadType: Payload.PayloadType? = null

        var payload: Payload? = null

        loop@ while (true) {
            when (val i = dec.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> payloadType = dec.decodeSerializableElement(descriptor, i, Payload.PayloadType.serializer())
                1 -> payload = when (payloadType) {
                    Payload.PayloadType.VerificationMessageV1 -> dec.decodeSerializableElement(descriptor, i, VerificationMessage.serializer())
                    Payload.PayloadType.EncryptedPayloadContainerV1 -> dec.decodeSerializableElement(descriptor, i, EncryptedPayloadContainer.serializer())
                    Payload.PayloadType.GroupInvitationV1 -> dec.decodeSerializableElement(descriptor, i, GroupInvitation.serializer())
                    Payload.PayloadType.GroupUpdateV1 -> dec.decodeSerializableElement(descriptor, i, GroupUpdate.serializer())
                    Payload.PayloadType.LocationUpdateV2 -> dec.decodeSerializableElement(descriptor, i, LocationUpdateV2.serializer())
                    Payload.PayloadType.FewOneTimePrekeysV1 -> dec.decodeSerializableElement(descriptor, i, FewOneTimePrekeys.serializer())
                    Payload.PayloadType.UserUpdateV1 -> dec.decodeSerializableElement(descriptor, i, UserUpdate.serializer())
                    Payload.PayloadType.ResetConversationV1 -> dec.decodeSerializableElement(descriptor, i, ResetConversation.serializer())
                    Payload.PayloadType.ChatMessageV1 -> dec.decodeSerializableElement(descriptor, i, ChatMessage.serializer())
                    Payload.PayloadType.LocationSharingUpdateV1 -> dec.decodeSerializableElement(descriptor, i, LocationSharingUpdate.serializer())
                    else -> continue@loop
                }

                else -> throw SerializationException("Unknown index $i")
            }
        }

        return PayloadContainer(
            payloadType ?: throw Exception("payloadType"),
            payload ?: throw Exception("payload")
        )
    }
}
