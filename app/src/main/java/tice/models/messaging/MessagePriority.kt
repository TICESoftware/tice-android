package tice.models.messaging

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MessagePriority {
    @SerialName("deferred")
    Deferred,
    @SerialName("background")
    Background,
    @SerialName("alert")
    Alert
}
