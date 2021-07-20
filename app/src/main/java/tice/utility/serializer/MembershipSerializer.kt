package tice.utility.serializer

import kotlinx.serialization.*
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.internal.NullableSerializer
import tice.crypto.*
import tice.models.*

@Serializer(forClass = Membership::class)
object MembershipSerializer : KSerializer<Membership> {

    override val descriptor: SerialDescriptor
        get() = buildClassSerialDescriptor("Membership") {
            element<String>("userId")
            element<String>("groupId")
            element<String>("publicSigningKey")
            element<Boolean>("admin")
            element<String>("selfSignedMembershipCertificate")
            element<String>("serverSignedMembershipCertificate")
            element<String>("adminSignedMembershipCertificate")
        }

    override fun serialize(encoder: Encoder, value: Membership) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(descriptor, 0, UUIDSerializer, value.userId)
        composite.encodeSerializableElement(descriptor, 1, UUIDSerializer, value.groupId)
        composite.encodeSerializableElement(descriptor, 2, DataSerializer, value.publicSigningKey)
        composite.encodeBooleanElement(descriptor, 3, value.admin)
        composite.encodeNullableSerializableElement(descriptor, 4, String.serializer(), value.selfSignedMembershipCertificate)
        composite.encodeSerializableElement(descriptor, 5, String.serializer(), value.serverSignedMembershipCertificate)
        composite.encodeNullableSerializableElement(descriptor, 6, String.serializer(), value.adminSignedMembershipCertificate)
        composite.endStructure(descriptor)
    }

    @InternalSerializationApi
    override fun deserialize(decoder: Decoder): Membership {
        val dec: CompositeDecoder = decoder.beginStructure(descriptor)

        var userId: UserId? = null
        var groupId: GroupId? = null
        var publicSigningKey: PublicKey? = null
        var admin: Boolean? = null
        var selfSignedMembershipCertificate: Certificate? = null
        var serverSignedMembershipCertificate: Certificate? = null
        var adminSignedMembershipCertificate: Certificate? = null

        loop@ while (true) {
            when (val i = dec.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> userId = dec.decodeSerializableElement(descriptor, i, UUIDSerializer)
                1 -> groupId = dec.decodeSerializableElement(descriptor, i, UUIDSerializer)
                2 -> publicSigningKey = dec.decodeSerializableElement(descriptor, i, DataSerializer)
                3 -> admin = dec.decodeBooleanElement(descriptor, i)
                4 -> selfSignedMembershipCertificate = dec.decodeSerializableElement(descriptor, i, String.serializer())
                5 -> serverSignedMembershipCertificate = dec.decodeSerializableElement(descriptor, i, String.serializer())
                6 -> adminSignedMembershipCertificate = dec.decodeNullableSerializableElement(descriptor, i, NullableSerializer(String.serializer()))
                else -> throw SerializationException("Unknown index $i")
            }
        }

        return Membership(
            userId ?: throw Exception("userId"),
            groupId ?: throw Exception("groupId"),
            publicSigningKey ?: throw Exception("publicSigningKey"),
            admin ?: throw Exception("admin"),
            selfSignedMembershipCertificate,
            serverSignedMembershipCertificate ?: throw Exception("serverSignedMembershipCertificate"),
            adminSignedMembershipCertificate
        )
    }
}
