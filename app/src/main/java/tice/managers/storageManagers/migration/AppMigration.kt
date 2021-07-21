package tice.managers.storageManagers.migration

import android.content.Context

interface AppMigration {
    val versionCode: Int
    fun migrate(context: Context)
}
