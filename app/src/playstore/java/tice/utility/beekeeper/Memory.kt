package tice.utility.beekeeper

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Named

class Memory @Inject constructor(
    @Named("SETTINGS_PREFS") private val sharedPrefs: SharedPreferences,
) {
    private val BEEKEEPER_PREVIOUS_EVENTS_KEY = "BEEKEEPER_PREVIOUS_EVENTS_KEY"
    private val BEEKEEPER_OPT_OUT_KEY = "BEEKEEPER_OPT_OUT_KEY"
    private val BEEKEEPER_INSTALL_DAY_KEY = "BEEKEEPER_INSTALL_DAY_KEY"
    private val BEEKEEPER_LAST_DAYS_KEY = "BEEKEEPER_LAST_DAYS_KEY"
    private val BEEKEEPER_CUSTOM_KEY = "BEEKEEPER_CUSTOM_KEY"

    internal val previousEvent: MutableMap<String, String>
        get() {
            val string: String = sharedPrefs.getString(BEEKEEPER_PREVIOUS_EVENTS_KEY, "{}") ?: "{}"
            return Json.decodeFromString(string)
        }

    internal val lastDay: MutableMap<String, MutableMap<String, Day>>
        get() {
            val string: String = sharedPrefs.getString(BEEKEEPER_LAST_DAYS_KEY, "{}") ?: "{}"
            return Json.decodeFromString(string)
        }

    internal var optedOut: Boolean
        get() = sharedPrefs.getBoolean(BEEKEEPER_OPT_OUT_KEY, false)
        set(value) = sharedPrefs.edit().putBoolean(BEEKEEPER_OPT_OUT_KEY, value).apply()

    internal var installDay: Day?
        get() = sharedPrefs.getString(BEEKEEPER_INSTALL_DAY_KEY, null)
        set(value) = sharedPrefs.edit().putString(BEEKEEPER_INSTALL_DAY_KEY, value).apply()

    internal val custom: MutableList<String?>
        get() {
            val string: String = sharedPrefs.getString(BEEKEEPER_CUSTOM_KEY, "[]") ?: "[]"
            return Json.decodeFromString(string)
        }

    fun remember(event: Event) {
        val previousEventMap = previousEvent
        previousEventMap[event.group] = event.name

        val day = event.timestamp.toDay()
        val map = lastDay

        if (map[event.group] == null) {
            map[event.group] = mutableMapOf()
        }
        map[event.group]?.put(event.name, day)

        sharedPrefs.edit {
            putString(BEEKEEPER_PREVIOUS_EVENTS_KEY, Json.encodeToString(previousEventMap))
            putString(BEEKEEPER_LAST_DAYS_KEY, Json.encodeToString(map))
        }
    }

    fun setProperty(index: Int, value: String?) {
        if (index >= custom.count()) {
            setPropertyCount(index + 1)
        }

        val newCustom = custom
        newCustom[index] = value
        sharedPrefs.edit {
            putString(BEEKEEPER_CUSTOM_KEY, Json.encodeToString(newCustom))
        }
    }

    fun setPropertyCount(count: Int) {
        var newCustom = custom
        if (count >= newCustom.count()) {
            val toAppend = List(count - newCustom.count()) { null }
            newCustom.addAll(toAppend)
        } else if (count < newCustom.count()) {
            newCustom = newCustom.subList(0, count)
        }
        sharedPrefs.edit {
            putString(BEEKEEPER_CUSTOM_KEY, Json.encodeToString(newCustom))
        }
    }

    fun clear() {
        sharedPrefs.edit {
            remove(BEEKEEPER_OPT_OUT_KEY)
            remove(BEEKEEPER_INSTALL_DAY_KEY)
            remove(BEEKEEPER_PREVIOUS_EVENTS_KEY)
            remove(BEEKEEPER_LAST_DAYS_KEY)
            remove(BEEKEEPER_CUSTOM_KEY)
        }
    }
}
