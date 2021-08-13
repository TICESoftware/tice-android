package tice.managers.storageManagers

import android.content.Context
import android.content.SharedPreferences
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class VersionCodeStorageManagerTest {

    private lateinit var versionCodeStorageManager: VersionCodeStorageManager

    private val mockContext: Context = mockk(relaxUnitFun = true)
    private val mockMigrationPrefs: SharedPreferences = mockk(relaxUnitFun = true)
    private val mockTicePrefs: SharedPreferences = mockk(relaxUnitFun = true)

    private val MIGRATION_PREFS_KEY = "migration"
    private val VERSION_CODE_KEY = "versionCode"
    private val DEPRECATED_MIGRATION_VERSION_KEY = "version"

    private val TICE_SHARED_PREFS_KEY = "tice"
    private val SIGNED_IN_USER_KEY = "signedInUser"

    private val NO_VERSION_CODE = -1

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        versionCodeStorageManager = VersionCodeStorageManager(mockContext)
    }

    @Test
    fun updateStoredVersionCode() {
        val versionCode = 42
        val mockTicePrefsEdit: SharedPreferences.Editor = mockk(relaxUnitFun = true)

        every { mockContext.getSharedPreferences(TICE_SHARED_PREFS_KEY, Context.MODE_PRIVATE) } returns mockTicePrefs
        every { mockTicePrefs.edit() } returns mockTicePrefsEdit
        every { mockTicePrefsEdit.putInt(VERSION_CODE_KEY, versionCode) } returns mockTicePrefsEdit

        versionCodeStorageManager.storeVersionCode(versionCode)

        verify(exactly = 1) { mockTicePrefsEdit.putInt(VERSION_CODE_KEY, versionCode) }
        verify(exactly = 1) { mockTicePrefsEdit.apply() }
    }

    @Test
    fun getStoredVersionCode() {
        val versionCode = 42

        every { mockContext.getSharedPreferences(TICE_SHARED_PREFS_KEY, Context.MODE_PRIVATE) } returns mockTicePrefs
        every { mockTicePrefs.getInt(VERSION_CODE_KEY, NO_VERSION_CODE) } returns versionCode

        val storedVersionCode = versionCodeStorageManager.getStoredVersionCode()
        Assertions.assertEquals(versionCode, storedVersionCode)
    }

    @Test
    fun getStoredVersionCodeDeprecatedSharedPrefs() {
        val versionCode = 42

        every { mockContext.getSharedPreferences(TICE_SHARED_PREFS_KEY, Context.MODE_PRIVATE) } returns mockTicePrefs
        every { mockTicePrefs.getInt(VERSION_CODE_KEY, NO_VERSION_CODE) } returns NO_VERSION_CODE
        every { mockContext.getSharedPreferences(MIGRATION_PREFS_KEY, Context.MODE_PRIVATE) } returns mockMigrationPrefs
        every { mockMigrationPrefs.getInt("version", NO_VERSION_CODE) } returns versionCode

        val storedVersionCode = versionCodeStorageManager.getStoredVersionCode()
        Assertions.assertEquals(versionCode, storedVersionCode)
    }

    @Test
    fun getStoredVersionCodeNothingStored() {
        every { mockContext.getSharedPreferences(TICE_SHARED_PREFS_KEY, Context.MODE_PRIVATE) } returns mockTicePrefs
        every { mockTicePrefs.getInt(VERSION_CODE_KEY, NO_VERSION_CODE) } returns NO_VERSION_CODE
        every { mockContext.getSharedPreferences(MIGRATION_PREFS_KEY, Context.MODE_PRIVATE) } returns mockMigrationPrefs
        every { mockMigrationPrefs.getInt("version", NO_VERSION_CODE) } returns NO_VERSION_CODE

        val storedVersionCode = versionCodeStorageManager.getStoredVersionCode()
        Assertions.assertEquals(30, storedVersionCode)
    }
}