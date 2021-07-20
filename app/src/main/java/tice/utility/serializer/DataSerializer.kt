package tice.utility.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import tice.utility.dataFromBase64
import tice.utility.toBase64String

@Serializer(forClass = ByteArray::class)
object DataSerializer : KSerializer<ByteArray> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("Data", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ByteArray) = encoder.encodeString(value.toBase64String())
    override fun deserialize(decoder: Decoder): ByteArray = decoder.decodeString().dataFromBase64()
}
