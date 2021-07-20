package tice.utility.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import tice.models.PublicKey
import tice.models.Signature
import tice.models.UserPublicKeys

object UserPublicKeysSerializer : KSerializer<UserPublicKeys> {

    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("UserPublicKeys") {
            element<String>("signingKey")
            element<String>("identityKey")
            element<String>("signedPrekey")
            element<String>("prekeySignature")
            element<String>("oneTimePrekeys")
        }

    override fun serialize(encoder: Encoder, value: UserPublicKeys) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, DataSerializer, value.signingKey)
        composite.encodeSerializableElement(descriptor, 1, DataSerializer, value.identityKey)
        composite.encodeSerializableElement(descriptor, 2, DataSerializer, value.signedPrekey)
        composite.encodeSerializableElement(descriptor, 3, DataSerializer, value.prekeySignature)
        composite.encodeSerializableElement(descriptor, 4, ListSerializer(DataSerializer), value.oneTimePrekeys)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): UserPublicKeys {
        val dec: CompositeDecoder = decoder.beginStructure(descriptor)

        var signingKey: PublicKey? = null
        var identityKey: PublicKey? = null
        var signedPrekey: PublicKey? = null
        var prekeySignature: Signature? = null
        var oneTimePrekeys: List<PublicKey>? = null

        loop@ while (true) {
            when (val i = dec.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> signingKey = dec.decodeSerializableElement(descriptor, i, DataSerializer)
                1 -> identityKey = dec.decodeSerializableElement(descriptor, i, DataSerializer)
                2 -> signedPrekey = dec.decodeSerializableElement(descriptor, i, DataSerializer)
                3 -> prekeySignature = dec.decodeSerializableElement(descriptor, i, DataSerializer)
                4 -> oneTimePrekeys = dec.decodeSerializableElement(descriptor, i, ListSerializer(DataSerializer))
                else -> throw SerializationException("Unknown index $i")
            }
        }
        return UserPublicKeys(
            signingKey ?: throw Exception("signingKey"),
            identityKey ?: throw Exception("identityKey"),
            signedPrekey ?: throw Exception("signedPrekey"),
            prekeySignature ?: throw Exception("prekeySignature"),
            oneTimePrekeys ?: throw Exception("oneTimePrekeys")
        )
    }
}
