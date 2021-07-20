package tice.models

import kotlinx.serialization.Serializable
import tice.utility.serializer.SignedInUserSerializer

@Serializable(with = SignedInUserSerializer::class)
data class SignedInUser(
    override val userId: UserId,
    override var publicName: String? = null,
    override var publicSigningKey: PublicKey,
    val privateSigningKey: PrivateKey
) : User(userId, publicSigningKey, publicName) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SignedInUser

        if (userId != other.userId) return false
        if (publicName != other.publicName) return false
        if (!publicSigningKey.contentEquals(other.publicSigningKey)) return false
        if (!privateSigningKey.contentEquals(other.privateSigningKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = userId.hashCode()
        result = 31 * result + (publicName?.hashCode() ?: 0)
        result = 31 * result + publicSigningKey.contentHashCode()
        result = 31 * result + privateSigningKey.contentHashCode()
        return result
    }
}
