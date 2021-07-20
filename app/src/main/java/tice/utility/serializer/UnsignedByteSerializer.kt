package tice.utility.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@OptIn(ExperimentalUnsignedTypes::class)
class UnsignedByteSerializer : KSerializer<UByte> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UByte", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: UByte) = encoder.encodeInt(value.toInt())
    override fun deserialize(decoder: Decoder): UByte = decoder.decodeInt().toUByte()
    override fun patch(decoder: Decoder, old: UByte): UByte = deserialize(decoder)
}
