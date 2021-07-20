package tice.models.messaging.conversation

import tice.models.PublicKey

data class ConversationInvitation(val identityKey: PublicKey, val ephemeralKey: PublicKey, val usedOneTimePrekey: PublicKey?) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConversationInvitation

        if (!identityKey.contentEquals(other.identityKey)) return false
        if (!ephemeralKey.contentEquals(other.ephemeralKey)) return false
        if (usedOneTimePrekey != null) {
            if (other.usedOneTimePrekey == null) return false
            if (!usedOneTimePrekey.contentEquals(other.usedOneTimePrekey)) return false
        } else if (other.usedOneTimePrekey != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = identityKey.contentHashCode()
        result = 31 * result + ephemeralKey.contentHashCode()
        result = 31 * result + (usedOneTimePrekey?.contentHashCode() ?: 0)
        return result
    }
}
