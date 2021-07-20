package tice.utility.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.net.URL

@Serializer(forClass = URL::class)
object URLSerializer : KSerializer<URL> {
    override fun deserialize(decoder: Decoder): URL = URL(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: URL) = encoder.encodeString(value.toString())
}
