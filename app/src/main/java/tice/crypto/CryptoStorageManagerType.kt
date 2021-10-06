package tice.crypto

import com.ticeapp.androiddoubleratchet.MessageKeyCache
import tice.exceptions.CryptoStorageManagerException
import tice.models.*
import tice.models.messaging.conversation.ConversationState

interface CryptoStorageManagerType {
    suspend fun saveIdentityKeyPair(keyPair: KeyPair)
    suspend fun savePrekeyPair(keyPair: KeyPair, signature: Signature)
    suspend fun saveOneTimePrekeyPairs(keyPairs: List<KeyPair>)
    @Throws(CryptoStorageManagerException.NoDataStored::class) suspend fun loadIdentityKeyPair(): KeyPair
    @Throws(CryptoStorageManagerException.NoDataStored::class) suspend fun loadPrekeyPair(): KeyPair
    @Throws(CryptoStorageManagerException.NoDataStored::class) suspend fun loadPrekeySignature(): Signature
    suspend fun loadPrivateOneTimePrekey(publicKey: PublicKey): PrivateKey
    suspend fun deleteOneTimePrekeyPair(publicKey: PublicKey)

    suspend fun saveConversationState(conversationState: ConversationState)
    suspend fun loadConversationState(userId: UserId, conversationId: ConversationId): ConversationState?
    suspend fun loadConversationStates(): List<ConversationState>
    suspend fun messageKeyCache(conversationId: ConversationId): MessageKeyCache
}
