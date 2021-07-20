package tice.managers.storageManagers

import android.content.Context
import android.content.SharedPreferences
import com.ticeapp.TICE.BuildConfig
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class VersionCodeStorageManagerTest {

    private lateinit var versionCodeStorageManager: VersionCodeStorageManager

    private val mockContext: Context = mockk(relaxUnitFun = true)
    private val mockMigrationPrefs: SharedPreferences = mockk(relaxUnitFun = true)
    private val mockTicePrefs: SharedPreferences = mockk(relaxUnitFun = true)

    private val MIGRATION_PREFS_KEY = "migration"
    private val MIGRATION_VERSION_KEY = "version"

    private val TICE_SHARED_PREFS_KEY = "tice"
    private val SIGNED_IN_USER_KEY = "signedInUser"

    private val NO_VERSION_CODE = -1

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        versionCodeStorageManager = VersionCodeStorageManager(mockContext)
    }

    @Nested
    inner class MigrationRequired {

        @Test
        fun `not required`() = runBlockingTest {
            every { mockContext.getSharedPreferences(MIGRATION_PREFS_KEY, Context.MODE_PRIVATE) } returns mockMigrationPrefs

            every { mockMigrationPrefs.getInt(MIGRATION_VERSION_KEY, NO_VERSION_CODE) } returns BuildConfig.VERSION_CODE

            val result = versionCodeStorageManager.migrationRequired()

            Assertions.assertEquals(false, result)
        }

        @Test
        fun `required from minimum version`() = runBlockingTest {
            every { mockContext.getSharedPreferences(MIGRATION_PREFS_KEY, Context.MODE_PRIVATE) } returns mockMigrationPrefs
            every { mockContext.getSharedPreferences(TICE_SHARED_PREFS_KEY, Context.MODE_PRIVATE) } returns mockTicePrefs

            every { mockMigrationPrefs.getInt(MIGRATION_VERSION_KEY, NO_VERSION_CODE) } returns NO_VERSION_CODE
            every { mockTicePrefs.contains(SIGNED_IN_USER_KEY) } returns true

            val result = versionCodeStorageManager.migrationRequired()

            Assertions.assertEquals(true, result)
        }

        @Test
        fun `required from one version below`() = runBlockingTest {
            every { mockContext.getSharedPreferences(MIGRATION_PREFS_KEY, Context.MODE_PRIVATE) } returns mockMigrationPrefs
            every { mockContext.getSharedPreferences(TICE_SHARED_PREFS_KEY, Context.MODE_PRIVATE) } returns mockTicePrefs

            every { mockMigrationPrefs.getInt(MIGRATION_VERSION_KEY, NO_VERSION_CODE) } returns BuildConfig.VERSION_CODE - 1
            every { mockTicePrefs.contains(SIGNED_IN_USER_KEY) } returns true

            val result = versionCodeStorageManager.migrationRequired()

            Assertions.assertEquals(true, result)
        }

        @Test
        fun `not required because of first startup`() = runBlockingTest {
            every { mockContext.getSharedPreferences(MIGRATION_PREFS_KEY, Context.MODE_PRIVATE) } returns mockMigrationPrefs
            every { mockContext.getSharedPreferences(TICE_SHARED_PREFS_KEY, Context.MODE_PRIVATE) } returns mockTicePrefs

            every { mockMigrationPrefs.getInt(MIGRATION_VERSION_KEY, NO_VERSION_CODE) } returns NO_VERSION_CODE
            every { mockTicePrefs.contains(SIGNED_IN_USER_KEY) } returns false

            val result = versionCodeStorageManager.migrationRequired()

            Assertions.assertEquals(false, result)
        }
    }

    @Test
    fun updateStoredVersionCode() = runBlockingTest {
        val mockMigrationPrefsEdit: SharedPreferences.Editor = mockk(relaxUnitFun = true)

        every { mockContext.getSharedPreferences(MIGRATION_PREFS_KEY, Context.MODE_PRIVATE) } returns mockMigrationPrefs
        every { mockMigrationPrefs.edit() } returns mockMigrationPrefsEdit
        every { mockMigrationPrefsEdit.putInt(MIGRATION_VERSION_KEY, BuildConfig.VERSION_CODE) } returns mockMigrationPrefsEdit

        versionCodeStorageManager.updateStoredVersionCode()

        verify(exactly = 1) { mockMigrationPrefsEdit.putInt(MIGRATION_VERSION_KEY, BuildConfig.VERSION_CODE) }
        verify(exactly = 1) { mockMigrationPrefsEdit.apply() }
    }
}