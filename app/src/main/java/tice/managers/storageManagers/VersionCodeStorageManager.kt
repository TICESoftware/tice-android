package tice.managers.storageManagers

import android.content.Context
import com.ticeapp.TICE.BuildConfig
import javax.inject.Inject

class VersionCodeStorageManager @Inject constructor(private val context: Context) {

    private val migrationPrefsName = "migration"
    private val migrationVersionKey = "version"

    private val ticeSharedPrefsName = "tice"
    private val signedInUserKey = "signedInUser"

    fun migrationRequired(): Boolean {
        return getStoredVersionCode() < BuildConfig.VERSION_CODE
    }

    fun updateStoredVersionCode() {
        val prefs = context.getSharedPreferences(migrationPrefsName, Context.MODE_PRIVATE)
        prefs.edit().putInt(migrationVersionKey, BuildConfig.VERSION_CODE).apply()
    }

    fun getStoredVersionCode(): Int {
        val prefs = context.getSharedPreferences(migrationPrefsName, Context.MODE_PRIVATE)
        val noVersionCode = -1
        val storedVersion = prefs.getInt(migrationVersionKey, noVersionCode)

        if (storedVersion == noVersionCode) {
            // Get SharedPreferences to differentiate if this is an old version without stored VersionCodes or a fresh installed and firstly started app
            // It's an old version if there is a signed-in user
            val ticePrefs = context.getSharedPreferences(ticeSharedPrefsName, Context.MODE_PRIVATE)
            return if (ticePrefs.contains(signedInUserKey)) {
                24
            } else {
                BuildConfig.VERSION_CODE
            }
        }

        return storedVersion
    }
}
