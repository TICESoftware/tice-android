package tice.utility.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import tice.models.PublicKey
import tice.models.messaging.conversation.ConversationInvitation

@Serializer(forClass = ConversationInvitation::class)
object ConversationInvitationSerializer : KSerializer<ConversationInvitation> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("ConversationInvitation") {
            element<String>("identityKey")
            element<String>("ephemeralKey")
            element<String>("usedOneTimePrekey")
        }

    override fun serialize(encoder: Encoder, value: ConversationInvitation) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, DataSerializer, value.identityKey)
        composite.encodeSerializableElement(descriptor, 1, DataSerializer, value.ephemeralKey)
        composite.encodeNullableSerializableElement(descriptor, 2, DataSerializer, value.usedOneTimePrekey)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): ConversationInvitation {
        val dec: CompositeDecoder = decoder.beginStructure(descriptor)

        var identityKey: PublicKey? = null
        var ephemeralKey: PublicKey? = null
        var usedOneTimePrekey: PublicKey? = null

        loop@ while (true) {
            when (val i = dec.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> identityKey = dec.decodeSerializableElement(descriptor, i, DataSerializer)
                1 -> ephemeralKey = dec.decodeSerializableElement(descriptor, i, DataSerializer)
                2 -> usedOneTimePrekey = dec.decodeSerializableElement(descriptor, i, DataSerializer)

                else -> throw SerializationException("Unknown index $i")
            }
        }

        return ConversationInvitation(
            identityKey ?: throw Exception("identityKey"),
            ephemeralKey ?: throw Exception("ephemeralKey"),
            usedOneTimePrekey
        )
    }
}
