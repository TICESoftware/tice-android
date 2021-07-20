package tice.managers.storageManagers

import io.mockk.*
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tice.models.ConversationId
import tice.models.database.MessageKeyCacheEntry
import tice.models.database.MessageKeyCacheInterface
import java.util.*
import kotlin.random.Random

internal class MessageKeyCacheTest {

    private lateinit var messageKeyCache: MessageKeyCache

    private val mockDatabase: AppDatabase = mockk(relaxUnitFun = true)
    private val mockMessageKeyCacheInterface: MessageKeyCacheInterface = mockk(relaxUnitFun = true)
    private val TEST_CONVERSATION_ID = ConversationId.randomUUID()

    val TEST_MESSAGE_KEY = "messageKey".toByteArray()
    val TEST_MESSAGE_NUMBER = Random.nextInt()
    val TEST_PUBLIC_KEY = "publicKey".toByteArray()
    val TEST_NUMBER = Random.nextInt()

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        every { mockDatabase.messageKeyCacheInterface() } returns mockMessageKeyCacheInterface

        messageKeyCache = MessageKeyCache(TEST_CONVERSATION_ID, mockDatabase)
    }

    @Test
    fun add() = runBlockingTest {
        val startDate = Date()
        messageKeyCache.add(TEST_MESSAGE_KEY, TEST_MESSAGE_NUMBER, TEST_PUBLIC_KEY)

        val entrySlot = slot<MessageKeyCacheEntry>()

        coVerify(exactly = 1) { mockMessageKeyCacheInterface.insert(capture(entrySlot)) }

        val result = entrySlot.captured

        assertEquals(TEST_CONVERSATION_ID, result.conversationId)
        assertEquals(TEST_MESSAGE_KEY, result.messageKey)
        assertEquals(TEST_MESSAGE_NUMBER, result.messageNumber)
        assertEquals(TEST_PUBLIC_KEY, result.publicKey)

        assertTrue(startDate.time <= result.timestamp.time)
        assertTrue(Date().time >= result.timestamp.time)
    }

    @Test
    fun getMessageKey() = runBlockingTest {

        coEvery { mockMessageKeyCacheInterface.getMessageKey(TEST_CONVERSATION_ID, TEST_NUMBER, TEST_PUBLIC_KEY) } returns TEST_MESSAGE_KEY

        val result = messageKeyCache.getMessageKey(TEST_NUMBER, TEST_PUBLIC_KEY)

        Assertions.assertEquals(TEST_MESSAGE_KEY, result)
    }

    @Test
    fun remove() = runBlockingTest {

        messageKeyCache.remove(TEST_PUBLIC_KEY, TEST_MESSAGE_NUMBER)

        coVerify(exactly = 1) { mockMessageKeyCacheInterface.deleteMessageKey(TEST_CONVERSATION_ID, TEST_MESSAGE_NUMBER, TEST_PUBLIC_KEY) }
    }

    @Test
    fun removeAll() = runBlockingTest {
        messageKeyCache.removeAll()

        coVerify(exactly = 1) { mockMessageKeyCacheInterface.deleteAll() }

    }
}