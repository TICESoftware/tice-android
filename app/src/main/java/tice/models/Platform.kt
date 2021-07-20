package tice.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Platform {
    @SerialName("iOS")
    IOS,

    @SerialName("web")
    Web,

    @SerialName("android")
    Android
}
