package tice.managers.storageManagers

import com.ticeapp.androiddoubleratchet.MessageKeyCache
import tice.models.ConversationId
import tice.models.database.MessageKeyCacheEntry
import java.util.*

class MessageKeyCache(val conversationId: ConversationId, db: AppDatabase) : MessageKeyCache {
    private val messageKeyCacheInterface = db.messageKeyCacheInterface()

    override suspend fun add(messageKey: ByteArray, messageNumber: Int, publicKey: ByteArray) {
        messageKeyCacheInterface.insert(MessageKeyCacheEntry(conversationId, messageKey, messageNumber, publicKey, Date()))
    }

    override suspend fun getMessageKey(messageNumber: Int, publicKey: ByteArray): ByteArray? {
        return messageKeyCacheInterface.getMessageKey(conversationId, messageNumber, publicKey)
    }

    override suspend fun remove(publicKey: ByteArray, messageNumber: Int) {
        messageKeyCacheInterface.deleteMessageKey(conversationId, messageNumber, publicKey)
    }

    suspend fun removeAll() {
        messageKeyCacheInterface.deleteAll()
    }
}
