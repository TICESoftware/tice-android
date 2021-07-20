package tice.utility.serializer

import com.ticeapp.androiddoubleratchet.Header
import com.ticeapp.androiddoubleratchet.Message
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object MessageSerializer : KSerializer<Message> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Message") {
        element<String>("header")
        element<String>("cipher")
    }

    @ExperimentalUnsignedTypes
    override fun serialize(encoder: Encoder, value: Message) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, HeaderSerializer, value.header)
        composite.encodeSerializableElement(descriptor, 1, ListSerializer(UnsignedByteSerializer()), value.cipher.toUByteArray().asList())
        composite.endStructure(descriptor)
    }

    @ExperimentalUnsignedTypes
    override fun deserialize(decoder: Decoder): Message {
        val composite = decoder.beginStructure(descriptor)

        var header: Header? = null
        var cipher: ByteArray? = null
        for (i in 0..2) {
            when (composite.decodeElementIndex(descriptor)) {
                0 -> header = composite.decodeSerializableElement(descriptor, 0, HeaderSerializer)
                1 -> cipher = composite.decodeSerializableElement(descriptor, 1, ListSerializer(UnsignedByteSerializer())).toUByteArray().asByteArray()
            }
        }

        composite.endStructure(descriptor)

        return Message(header!!, cipher!!)
    }

    @ExperimentalUnsignedTypes
    override fun patch(decoder: Decoder, old: Message): Message = deserialize(decoder)
}
