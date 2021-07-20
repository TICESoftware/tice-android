package tice.utility.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.text.SimpleDateFormat
import java.util.*

@Serializer(forClass = Date::class)
object DateSerializer : KSerializer<Date> {
    const val formatString = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
    val formatter by lazy {
        val formatter = SimpleDateFormat(formatString, Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        formatter
    }

    override fun serialize(encoder: Encoder, value: Date) {
        encoder.encodeString(formatter.format(value))
    }

    override fun deserialize(decoder: Decoder): Date {
        val newDateString = decoder.decodeString().replace("Z", "+0000")
        return formatter.parse(newDateString)!!
    }
}
