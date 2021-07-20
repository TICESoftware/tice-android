package tice.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PermissionMode {
    @SerialName("everyone")
    Everyone,

    @SerialName("admin")
    Admin
}
