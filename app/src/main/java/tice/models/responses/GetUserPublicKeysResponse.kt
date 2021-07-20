@file:UseSerializers(DataSerializer::class)

package tice.models.responses

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import tice.models.PublicKey
import tice.models.Signature
import tice.utility.serializer.DataSerializer

@Serializable
data class GetUserPublicKeysResponse(
    val signingKey: PublicKey,
    val identityKey: PublicKey,
    val signedPrekey: PublicKey,
    val prekeySignature: Signature,
    val oneTimePrekey: PublicKey
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GetUserPublicKeysResponse

        if (!signingKey.contentEquals(other.signingKey)) return false
        if (!identityKey.contentEquals(other.identityKey)) return false
        if (!signedPrekey.contentEquals(other.signedPrekey)) return false
        if (!prekeySignature.contentEquals(other.prekeySignature)) return false
        if (!oneTimePrekey.contentEquals(other.oneTimePrekey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = signingKey.contentHashCode()
        result = 31 * result + identityKey.contentHashCode()
        result = 31 * result + signedPrekey.contentHashCode()
        result = 31 * result + prekeySignature.contentHashCode()
        result = 31 * result + oneTimePrekey.contentHashCode()
        return result
    }
}
