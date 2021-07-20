package tice.managers.storageManagers

import android.content.SharedPreferences
import tice.dagger.scopes.AppScope
import javax.inject.Inject

@AppScope
class StorageLocker @Inject constructor(private val sharedPreferences: SharedPreferences) : StorageLockerType {

    override fun store(storageKey: StorageLockerType.StorageKey, value: String) {
        sharedPreferences.edit().putString(storageKey.value, value).apply()
    }

    override fun load(storageKey: StorageLockerType.StorageKey): String? =
        sharedPreferences.getString(storageKey.value, null)

    override fun remove(storageKey: StorageLockerType.StorageKey) {
        sharedPreferences.edit().remove(storageKey.value).apply()
    }
}
