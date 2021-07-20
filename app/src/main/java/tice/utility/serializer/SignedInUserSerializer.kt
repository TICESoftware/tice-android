package tice.utility.serializer

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.internal.NullableSerializer
import tice.models.PrivateKey
import tice.models.PublicKey
import tice.models.SignedInUser
import tice.models.UserId

object SignedInUserSerializer : KSerializer<SignedInUser> {
    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("SignedInUsers") {
            element<String>("userId")
            element<String>("publicName")
            element<String>("publicSigningKey")
            element<String>("privateSigningKey")
        }

    override fun serialize(encoder: Encoder, value: SignedInUser) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, UUIDSerializer, value.userId)
        composite.encodeNullableSerializableElement(descriptor, 1, String.serializer(), value.publicName)
        composite.encodeNullableSerializableElement(descriptor, 2, DataSerializer, value.publicSigningKey)
        composite.encodeNullableSerializableElement(descriptor, 3, DataSerializer, value.privateSigningKey)
        composite.endStructure(descriptor)
    }

    @InternalSerializationApi
    override fun deserialize(decoder: Decoder): SignedInUser {
        val dec: CompositeDecoder = decoder.beginStructure(descriptor)

        var userId: UserId? = null
        var publicName: String? = null
        var publicSigningKey: PublicKey? = null
        var privateSigningKey: PrivateKey? = null

        loop@ while (true) {
            when (val i = dec.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> userId = dec.decodeSerializableElement(descriptor, i, UUIDSerializer)
                1 -> publicName = dec.decodeNullableSerializableElement(descriptor, i, NullableSerializer(String.serializer()))
                2 -> publicSigningKey = dec.decodeSerializableElement(descriptor, i, DataSerializer)
                3 -> privateSigningKey = dec.decodeSerializableElement(descriptor, i, DataSerializer)
                else -> throw SerializationException("Unknown index $i")
            }
        }

        return SignedInUser(
            userId ?: throw Exception("userId"),
            publicName,
            publicSigningKey ?: throw Exception("publicSigningKey"),
            privateSigningKey ?: throw Exception("privateSigningKey")
        )
    }
}
