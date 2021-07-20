package tice.models.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MessageStatus {
    @SerialName("sending")
    Sending,

    @SerialName("success")
    Success,

    @SerialName("failed")
    Failed
}
