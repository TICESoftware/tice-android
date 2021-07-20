package tice.utility.beekeeper

import android.content.SharedPreferences
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class MemoryTest {

    private lateinit var memory: Memory

    private val mockSharedPreferences: SharedPreferences = mockk(relaxUnitFun = true)
    private val mockSharedPreferencesEditor =
        mockk<SharedPreferences.Editor>(relaxed = true, relaxUnitFun = true)

    @BeforeEach
    fun before() {
        clearAllMocks()

        memory = Memory(mockSharedPreferences)
        every { mockSharedPreferences.edit() } returns mockSharedPreferencesEditor
    }

    @Test
    fun `test properties`() = runBlockingTest {
        memory.installDay = "2020-04-20"
        memory.optedOut = true

        verify(exactly = 1) {
            mockSharedPreferencesEditor.putString(
                "BEEKEEPER_INSTALL_DAY_KEY",
                "2020-04-20"
            )
            mockSharedPreferencesEditor.putBoolean("BEEKEEPER_OPT_OUT_KEY", true)
        }

        every {
            mockSharedPreferences.getString(
                "BEEKEEPER_INSTALL_DAY_KEY",
                any()
            )
        } returns "2020-04-20"

        every {
            mockSharedPreferences.getBoolean(
                "BEEKEEPER_OPT_OUT_KEY",
                any()
            )
        } returns true

        Assertions.assertEquals("2020-04-20", memory.installDay)
        Assertions.assertEquals(true, memory.optedOut)
    }

}
