package tice.models.messaging.conversation

import tice.models.*

data class ConversationState(
    val userId: UserId,
    val conversationId: ConversationId,
    val rootKey: SecretKey,
    val rootChainPublicKey: PublicKey,
    val rootChainPrivateKey: PrivateKey,
    val rootChainRemotePublicKey: PublicKey?,
    val sendingChainKey: SecretKey?,
    val receivingChainKey: SecretKey?,
    val sendMessageNumber: Int,
    val receivedMessageNumber: Int,
    val previousSendingChanLength: Int
) {
    val rootChainKeyPair: KeyPair
        get() = KeyPair(rootChainPrivateKey, rootChainPublicKey)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConversationState

        if (userId != other.userId) return false
        if (conversationId != other.conversationId) return false
        if (!rootKey.contentEquals(other.rootKey)) return false
        if (!rootChainPublicKey.contentEquals(other.rootChainPublicKey)) return false
        if (!rootChainPrivateKey.contentEquals(other.rootChainPrivateKey)) return false
        if (rootChainRemotePublicKey != null) {
            if (other.rootChainRemotePublicKey == null) return false
            if (!rootChainRemotePublicKey.contentEquals(other.rootChainRemotePublicKey)) return false
        } else if (other.rootChainRemotePublicKey != null) return false
        if (sendingChainKey != null) {
            if (other.sendingChainKey == null) return false
            if (!sendingChainKey.contentEquals(other.sendingChainKey)) return false
        } else if (other.sendingChainKey != null) return false
        if (receivingChainKey != null) {
            if (other.receivingChainKey == null) return false
            if (!receivingChainKey.contentEquals(other.receivingChainKey)) return false
        } else if (other.receivingChainKey != null) return false
        if (sendMessageNumber != other.sendMessageNumber) return false
        if (receivedMessageNumber != other.receivedMessageNumber) return false
        if (previousSendingChanLength != other.previousSendingChanLength) return false

        return true
    }

    override fun hashCode(): Int {
        var result = userId.hashCode()
        result = 31 * result + conversationId.hashCode()
        result = 31 * result + rootKey.contentHashCode()
        result = 31 * result + rootChainPublicKey.contentHashCode()
        result = 31 * result + rootChainPrivateKey.contentHashCode()
        result = 31 * result + (rootChainRemotePublicKey?.contentHashCode() ?: 0)
        result = 31 * result + (sendingChainKey?.contentHashCode() ?: 0)
        result = 31 * result + (receivingChainKey?.contentHashCode() ?: 0)
        result = 31 * result + sendMessageNumber
        result = 31 * result + receivedMessageNumber
        result = 31 * result + previousSendingChanLength
        return result
    }
}
