package tice.managers.storageManagers.migration

import android.content.Context

class MigrationTo31 : AppMigration {
    override val versionCode: Int = 31

    override fun migrate(context: Context) {
        val migrationPrefs = context.getSharedPreferences("migration", Context.MODE_PRIVATE)
        val versionCode = migrationPrefs.getInt("version", -1)

        val prefs = context.getSharedPreferences("tice", Context.MODE_PRIVATE)
        prefs.edit().putInt("versionCode", versionCode).apply()
    }
}
