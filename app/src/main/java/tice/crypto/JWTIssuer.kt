package tice.crypto

import tice.models.UserId
import tice.utility.uuidString

internal sealed class JWTIssuer {
    internal object Server : JWTIssuer()
    internal data class User(val userId: UserId) : JWTIssuer()

    fun claimString(): String = when (this) {
        Server -> "server"
        is User -> this.userId.uuidString()
    }
}
