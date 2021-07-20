package tice.utility.beekeeper

import java.text.SimpleDateFormat
import java.util.*

fun Base64.encodeToString(byteArray: ByteArray): String {
    return byteArray.toString()
}

fun Date.toDay(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(this)
}
