package tice.utility.serializer

import com.goterl.lazysodium.utils.Key
import com.ticeapp.androiddoubleratchet.Header
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import tice.crypto.cryptoKey

object HeaderSerializer : KSerializer<Header> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Header") {
        element<String>("publicKey")
        element<String>("numberOfMessagesInPreviousSendingChain")
        element<String>("messageNumber")
    }

    @ExperimentalUnsignedTypes
    override fun serialize(encoder: Encoder, value: Header) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeSerializableElement(
            descriptor,
            0,
            ListSerializer(UnsignedByteSerializer()),
            value.publicKey.asBytes.toUByteArray().asList()
        )
        composite.encodeIntElement(descriptor, 1, value.numberOfMessagesInPreviousSendingChain)
        composite.encodeIntElement(descriptor, 2, value.messageNumber)
        composite.endStructure(descriptor)
    }

    @ExperimentalUnsignedTypes
    override fun deserialize(decoder: Decoder): Header {
        val composite = decoder.beginStructure(descriptor)

        var publicKey: Key? = null
        var numberOfMessagesInPreviousSendingChain: Int? = null
        var messageNumber: Int? = null
        for (i in 0..2) {
            when (composite.decodeElementIndex(descriptor)) {
                0 -> {
                    publicKey = composite.decodeSerializableElement(descriptor, 0, ListSerializer(UnsignedByteSerializer())).toUByteArray()
                        .asByteArray().cryptoKey()
                }
                1 -> numberOfMessagesInPreviousSendingChain = composite.decodeIntElement(descriptor, 1)
                2 -> messageNumber = composite.decodeIntElement(descriptor, 2)
            }
        }

        composite.endStructure(descriptor)

        return Header(publicKey!!, numberOfMessagesInPreviousSendingChain!!, messageNumber!!)
    }

    @ExperimentalUnsignedTypes
    override fun patch(decoder: Decoder, old: Header): Header = deserialize(decoder)
}
