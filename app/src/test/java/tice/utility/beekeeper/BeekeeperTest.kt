package tice.utility.beekeeper

import io.mockk.*
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tice.utility.provider.CoroutineContextProviderType
import java.util.*

internal class BeekeeperTest {

    private lateinit var beekeeper: Beekeeper

    private val mockMemory: Memory = mockk(relaxUnitFun = true)
    private val mockDispatcher: Dispatcher = mockk(relaxUnitFun = true)
    private val mockCoroutineContextProvider: CoroutineContextProviderType = mockk(relaxUnitFun = true)

    @BeforeEach
    fun before() {
        clearAllMocks()

        beekeeper = Beekeeper(mockDispatcher, mockMemory, mockCoroutineContextProvider)

        every { mockMemory.optedOut } returns false
        every { mockDispatcher.dispatchInterval } returns 1_000
        every { mockDispatcher.maxBatchSize } returns 10
    }

    @Nested
    inner class TrackEvent {
        @Test
        fun `test start and stop`() = runBlockingTest {
            Assertions.assertEquals(false, beekeeper.isActive)
            beekeeper.dispatch()
            coVerify(exactly = 0) { mockDispatcher.dispatch(any()) }

            every { mockMemory.installDay } returns null
            beekeeper.start()
            Assertions.assertEquals(true, beekeeper.isActive)

            beekeeper.stop()
            Assertions.assertEquals(false, beekeeper.isActive)
        }

        @Test
        fun `test track adds to queue`() = runBlockingTest {
            val testDispatcher = TestCoroutineDispatcher()
            every { mockCoroutineContextProvider.IO } returns testDispatcher

            every { mockMemory.previousEvent } returns mutableMapOf()
            every { mockMemory.lastDay } returns mutableMapOf()
            every { mockMemory.installDay } returns "2000-01-01"
            every { mockMemory.custom } returns mutableListOf()

            val eventsSlot = slot<List<Event>>()

            beekeeper.track("TestName", "TestGroup", "TestDetail")

            beekeeper.start()
            beekeeper.dispatch()

            testDispatcher.advanceUntilIdle()
            beekeeper.reset()

            coVerify(exactly = 1) { mockDispatcher.dispatch(capture(eventsSlot)) }

            val result = eventsSlot.captured.first()
            Assertions.assertEquals("TICE-development", result.product)
            Assertions.assertEquals("TestName", result.name)
            Assertions.assertEquals("TestGroup", result.group)
            Assertions.assertEquals("TestDetail", result.detail)
            Assertions.assertEquals(null, result.value)
            Assertions.assertEquals(null, result.previousEvent)
            Assertions.assertEquals(null, result.previousEventTimestamp)
            Assertions.assertEquals("2000-01-01", result.install)
            Assertions.assertEquals(emptyList<String>(), result.custom)
        }

        @Test
        fun `test previous event`() = runBlockingTest {
            val testDispatcher = TestCoroutineDispatcher()
            every { mockCoroutineContextProvider.IO } returns testDispatcher

            every { mockMemory.optedOut } returns false
            every { mockMemory.previousEvent } returns mutableMapOf(Pair("TestGroup", "PreviousEventName"))
            every { mockMemory.lastDay } returns mutableMapOf(Pair("TestGroup", mutableMapOf(Pair("TestName", "2020-04-01"))))
            every { mockMemory.installDay } returns "2020-04-01"
            every { mockMemory.custom } returns mutableListOf()
            every { mockDispatcher.dispatchInterval } returns 1_000
            every { mockDispatcher.maxBatchSize } returns 10

            val eventsSlot = slot<List<Event>>()

            beekeeper.track("TestName", "TestGroup")

            beekeeper.start()
            beekeeper.dispatch()

            testDispatcher.advanceUntilIdle()
            beekeeper.reset()

            coVerify(exactly = 1) { mockDispatcher.dispatch(capture(eventsSlot)) }

            val capturedEvent = eventsSlot.captured.first()
            Assertions.assertEquals("PreviousEventName", capturedEvent.previousEvent)
            Assertions.assertEquals("2020-04-01", capturedEvent.previousEventTimestamp)
        }
    }

    @Nested
    inner class InstallDate {

        @Test
        fun `set in memory`() = runBlockingTest {
            beekeeper.setInstallDay("2018-04-20")

            verify(exactly = 1) { mockMemory.installDay = "2018-04-20" }
        }

        @Test
        fun `used from memory`() = runBlockingTest {
            val testDispatcher = TestCoroutineDispatcher()
            every { mockCoroutineContextProvider.IO } returns testDispatcher

            every { mockMemory.optedOut } returns false
            every { mockMemory.previousEvent } returns mutableMapOf()
            every { mockMemory.lastDay } returns mutableMapOf()
            every { mockMemory.installDay } returns "2018-04-20"
            every { mockMemory.custom } returns mutableListOf()
            every { mockDispatcher.dispatchInterval } returns 1_000
            every { mockDispatcher.maxBatchSize } returns 10

            beekeeper.track("TestName", "TestGroup")

            beekeeper.start()
            beekeeper.dispatch()

            testDispatcher.advanceUntilIdle()
            beekeeper.reset()

            val eventsSlot = slot<List<Event>>()
            coVerify(exactly = 1) { mockDispatcher.dispatch(capture(eventsSlot)) }

            Assertions.assertEquals("2018-04-20", eventsSlot.captured.first().install)
        }

        @Test
        fun `memory has non`() = runBlockingTest {
            val testDispatcher = TestCoroutineDispatcher()
            every { mockCoroutineContextProvider.IO } returns testDispatcher

            every { mockMemory.optedOut } returns false
            every { mockMemory.previousEvent } returns mutableMapOf()
            every { mockMemory.lastDay } returns mutableMapOf()
            every { mockMemory.installDay } returns null
            every { mockMemory.custom } returns mutableListOf()
            every { mockDispatcher.dispatchInterval } returns 1_000
            every { mockDispatcher.maxBatchSize } returns 10

            beekeeper.track("TestName", "TestGroup")

            beekeeper.start()
            beekeeper.dispatch()

            testDispatcher.advanceUntilIdle()
            beekeeper.reset()

            val eventsSlot = slot<List<Event>>()
            coVerify(exactly = 1) { mockDispatcher.dispatch(capture(eventsSlot)) }

            Assertions.assertEquals(Date().toDay(), eventsSlot.captured.first().install)
        }
    }

    @Test
    fun `test time zone`() {
        val timestamp = Date(1524268799_000) // 2018-04-20T23:59:59Z
        val day = timestamp.toDay()
        Assertions.assertEquals("2018-04-20", day)
    }
}
