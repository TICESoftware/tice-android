package tice.managers.storageManagers

import android.content.SharedPreferences
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class StorageLockerTest {

    private lateinit var storageLocker: StorageLocker

    private val mockSharedPreferences: SharedPreferences = mockk(relaxUnitFun = true)
    private val mockSharedPreferencesEditor: SharedPreferences.Editor = mockk(relaxUnitFun = true)

    val TEST_KEY = StorageLockerType.StorageKey.GROUPS
    val TEST_VALUE = "value"

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        storageLocker = StorageLocker(mockSharedPreferences)
    }

    @Test
    fun store() = runBlockingTest {
        every { mockSharedPreferences.edit() } returns mockSharedPreferencesEditor
        every { mockSharedPreferencesEditor.putString(TEST_KEY.value, TEST_VALUE) } returns mockSharedPreferencesEditor

        storageLocker.store(TEST_KEY, TEST_VALUE)

        verify(exactly = 1) { mockSharedPreferencesEditor.putString(TEST_KEY.value, TEST_VALUE) }
        verify(exactly = 1) { mockSharedPreferencesEditor.apply() }
    }

    @Test
    fun load() {
        every { mockSharedPreferences.getString(TEST_KEY.value, null) } returns TEST_VALUE

        val result = storageLocker.load(TEST_KEY)

        Assertions.assertEquals(TEST_VALUE, result)
    }

    @Test
    fun remove() {
        every { mockSharedPreferences.edit() } returns mockSharedPreferencesEditor
        every { mockSharedPreferencesEditor.remove(TEST_KEY.value) } returns mockSharedPreferencesEditor

        storageLocker.remove(TEST_KEY)

        verify(exactly = 1) { mockSharedPreferencesEditor.remove(TEST_KEY.value) }
        verify(exactly = 1) { mockSharedPreferencesEditor.apply() }
    }
}