package tice.crypto

import tice.models.*
import tice.models.messaging.conversation.ConversationInvitation

interface ConversationCryptoMiddlewareType {
    suspend fun renewHandshakeKeyMaterial(privateSigningKey: PrivateKey, publicSigningKey: PublicKey): UserPublicKeys

    suspend fun conversationExisting(userId: UserId, conversationId: ConversationId): Boolean
    fun conversationFingerprint(ciphertext: Ciphertext): ConversationFingerprint
    suspend fun initConversation(
        userId: UserId,
        conversationId: ConversationId,
        remoteIdentityKey: PublicKey,
        remoteSignedPrekey: PublicKey,
        remotePrekeySignature: Signature,
        remoteOneTimePrekey: PublicKey?,
        remoteSigningKey: PublicKey
    ): ConversationInvitation

    suspend fun processConversationInvitation(
        conversationInvitation: ConversationInvitation,
        userId: UserId,
        conversationId: ConversationId
    )

    suspend fun encrypt(data: Data, userId: UserId, conversationId: ConversationId): Ciphertext
    suspend fun decrypt(encryptedData: Data, encryptedSecretKey: Data, userId: UserId, conversationId: ConversationId): Data
}
