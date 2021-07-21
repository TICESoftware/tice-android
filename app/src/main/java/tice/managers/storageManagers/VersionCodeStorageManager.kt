package tice.managers.storageManagers

import android.content.Context
import com.ticeapp.TICE.BuildConfig
import javax.inject.Inject

class VersionCodeStorageManager @Inject constructor(private val context: Context) : VersionCodeStorageManagerType {

    private val migrationPrefsName = "migration"
    private val migrationVersionKey = "versionCode"

    private val sharedPrefsName = "tice"

    override val outdatedVersion: Boolean
        get() = getStoredVersionCode() < BuildConfig.VERSION_CODE

    override fun storeVersionCode(versionCode: Int) {
        val prefs = context.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE)
        prefs.edit().putInt(migrationVersionKey, versionCode).apply()
    }

    override fun getStoredVersionCode(): Int {
        val prefs = context.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE)
        val noVersionCode = -1
        var storedVersion = prefs.getInt(migrationVersionKey, noVersionCode)

        if (storedVersion != noVersionCode) {
            return storedVersion
        } else {
            // For a certain transition period we need to fall back to the deprecated migration related shared preferences.
            val migrationPrefs = context.getSharedPreferences(migrationPrefsName, Context.MODE_PRIVATE)
            storedVersion = migrationPrefs.getInt("version", noVersionCode)

            return if (storedVersion != noVersionCode) storedVersion else 30
        }
    }
}
