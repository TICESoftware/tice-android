package tice.utility

import android.content.Context

enum class BuildFlavorStore {
    PLAY_STORE {
        override fun gmsAvailable(context: Context): Boolean = GMSAvailability.gmsAvailable(context)
    },
    FDROID {
        override fun gmsAvailable(context: Context): Boolean = false
    };

    abstract fun gmsAvailable(context: Context): Boolean

    companion object {
        fun fromFlavorString(string: String): BuildFlavorStore =
            when (string) {
                "playstore" -> PLAY_STORE
                "fdroid" -> FDROID
                else -> throw IllegalArgumentException("Invalid flavor string $string.")
            }
    }
}
