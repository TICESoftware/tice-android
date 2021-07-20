package tice.models.messaging

import tice.models.Ciphertext
import tice.models.SecretKey

data class EncryptionResult(
    val ciphertext: Ciphertext,
    val messageKey: SecretKey
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptionResult

        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (!messageKey.contentEquals(other.messageKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + messageKey.contentHashCode()
        return result
    }
}
