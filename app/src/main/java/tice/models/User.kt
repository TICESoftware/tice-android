@file:UseSerializers(
    UUIDSerializer::class,
    DataSerializer::class
)

package tice.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import tice.utility.serializer.DataSerializer
import tice.utility.serializer.UUIDSerializer

@Entity
@Serializable
open class User(
    @PrimaryKey
    override val userId: UserId,
    override var publicSigningKey: PublicKey,
    override var publicName: String? = null
) : UserType {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as User

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
