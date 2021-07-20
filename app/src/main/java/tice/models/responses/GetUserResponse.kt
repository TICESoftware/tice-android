@file:UseSerializers(
    UUIDSerializer::class,
    DataSerializer::class
)

package tice.models.responses

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import tice.models.PublicKey
import tice.models.UserId
import tice.utility.serializer.DataSerializer
import tice.utility.serializer.UUIDSerializer

@Serializable
data class GetUserResponse(
    val userId: UserId,
    val publicSigningKey: PublicKey,
    val publicName: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GetUserResponse

        if (userId != other.userId) return false
        if (!publicSigningKey.contentEquals(other.publicSigningKey)) return false
        if (publicName != other.publicName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = userId.hashCode()
        result = 31 * result + publicSigningKey.contentHashCode()
        result = 31 * result + (publicName?.hashCode() ?: 0)
        return result
    }
}
