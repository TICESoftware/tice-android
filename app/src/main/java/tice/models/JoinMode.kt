package tice.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class JoinMode {
    @SerialName("open")
    Open,
    @SerialName("closed")
    Closed
}
