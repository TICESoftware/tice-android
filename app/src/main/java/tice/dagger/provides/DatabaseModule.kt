package tice.dagger.provides

import android.content.Context
import dagger.Module
import dagger.Provides
import tice.managers.storageManagers.AppDatabase
import tice.managers.storageManagers.DatabaseManager

@Module
class DatabaseModule {
    @Provides
    fun provideAppDatabase(databaseManager: DatabaseManager, context: Context): AppDatabase = databaseManager.setupDatabase(context)
}
