package tice.managers.storageManagers.migration

import android.content.Context
import android.content.Context.MODE_PRIVATE
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ticeapp.TICE.BuildConfig
import net.sqlcipher.database.SupportFactory
import tice.exceptions.DatabaseManagerException
import tice.managers.storageManagers.*
import tice.models.SecretKey
import tice.utility.dataFromBase64
import tice.utility.getLogger
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

class MigrationManager constructor(context: Context, private val versionCodeStorageManager: VersionCodeStorageManagerType) {
    private val logger by getLogger()

    private val keyStore: KeyStore = KeyStore.getInstance(DatabaseManager.KEYSTORE_PROVIDER)
    private val storageLocker: StorageLockerType

    init {
        keyStore.load(null)

        val sharedPreferences = context.getSharedPreferences("tice", MODE_PRIVATE)
        storageLocker = StorageLocker(sharedPreferences)
    }

    private fun loadDatabaseKey(): SecretKey? {
        logger.debug("Loading encrypted database key.")

        storageLocker.load(StorageLockerType.StorageKey.ENCRYPTED_DATABASE_KEY)?.dataFromBase64()
            ?.let { ciphertext ->
                val iv = storageLocker.load(StorageLockerType.StorageKey.DATABASE_KEY_ENCRYPTION_IV)
                    ?.dataFromBase64()
                    ?: throw DatabaseManagerException.DatabaseEncryptionIVMissing

                val secretKeyEntry = keyStore.getEntry(
                    DatabaseManager.MASTER_KEY_ALIAS,
                    null
                ) as KeyStore.SecretKeyEntry
                val secretKey = secretKeyEntry.secretKey

                val cipher = Cipher.getInstance(DatabaseManager.ALGORITHM_SPEC)
                val gcmParameterSpec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)

                return cipher.doFinal(ciphertext)
            }

        logger.debug("Did not find stored encrypted database key. Checking for key stored in plaintext.")
        return storageLocker.load(StorageLockerType.StorageKey.PLAINTEXT_DATABASE_KEY)
            ?.dataFromBase64()
    }

    fun executeMigrationsBlocking(context: Context) {
        if (!versionCodeStorageManager.outdatedVersion) {
            logger.debug("Version up-to-date. Migration not necessary.")
            return
        }

        logger.debug("Stored version number is outdated. Check for migrations.")

        executeDatabaseMigrations(context)
        executeAppMigrations(context)

        versionCodeStorageManager.storeVersionCode(BuildConfig.VERSION_CODE)
    }

    private fun executeDatabaseMigrations(context: Context) {
        val databaseKey = loadDatabaseKey()

        if (databaseKey == null) {
            logger.info("No database key found. Skipping migration.")
            return
        }

        val factory = SupportFactory(databaseKey)
        Room.databaseBuilder(context, AppDatabase::class.java, "db")
            .openHelperFactory(factory)
            .addMigrations(*allDatabaseMigrations())
            .build()
            .openHelper.readableDatabase
            .close()

        logger.debug("Migrating database done.")
    }

    private fun executeAppMigrations(context: Context) {
        val previousVersion = versionCodeStorageManager.getStoredVersionCode()
        val appMigrations = allAppMigrations.filter { it.versionCode > previousVersion }

        for (migration in appMigrations) {
            logger.debug("Executing migration to version code ${migration.versionCode}.")
            migration.migrate(context)
            versionCodeStorageManager.storeVersionCode(migration.versionCode)
            logger.debug("Migration to version code ${migration.versionCode} done.")
        }
    }

    companion object {
        val allAppMigrations: Array<AppMigration> = arrayOf(MigrationTo31())

        fun allDatabaseMigrations(): Array<Migration> = arrayOf(
            migration1To2,
            migration2To3
        )

        val migration1To2 = object : Migration(1, 2) {
            private val logger by getLogger()

            override fun migrate(database: SupportSQLiteDatabase) {
                logger.debug("Executing migration from $startVersion to $endVersion.")

                database.execSQL("CREATE TEMP TABLE ConversationStateEntityBackup (`userId` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `rootKey` BLOB NOT NULL, `rootChainPublicKey` BLOB NOT NULL, `rootChainPrivateKey` BLOB NOT NULL, `rootChainRemotePublicKey` BLOB, `sendingChainKey` BLOB, `receivingChainKey` BLOB, `sendMessageNumber` INTEGER NOT NULL, `receivedMessageNumber` INTEGER NOT NULL, `previousSendingChanLength` INTEGER NOT NULL, `messageKeyCache` TEXT NOT NULL, PRIMARY KEY(`userId`, `conversationId`))")
                database.execSQL("INSERT INTO ConversationStateEntityBackup SELECT * FROM ConversationStateEntity")
                database.execSQL("DROP TABLE ConversationStateEntity")
                database.execSQL("CREATE TABLE ConversationStateEntity (`userId` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `rootKey` BLOB NOT NULL, `rootChainPublicKey` BLOB NOT NULL, `rootChainPrivateKey` BLOB NOT NULL, `rootChainRemotePublicKey` BLOB, `sendingChainKey` BLOB, `receivingChainKey` BLOB, `sendMessageNumber` INTEGER NOT NULL, `receivedMessageNumber` INTEGER NOT NULL, `previousSendingChanLength` INTEGER NOT NULL, PRIMARY KEY(`userId`, `conversationId`))")
                database.execSQL("INSERT INTO ConversationStateEntity SELECT userId,conversationId,rootKey,rootChainPublicKey,rootChainPrivateKey,rootChainRemotePublicKey,sendingChainKey,receivingChainKey,sendMessageNumber,receivedMessageNumber,previousSendingChanLength FROM ConversationStateEntityBackup")
                database.execSQL("DROP TABLE ConversationStateEntityBackup")

                database.execSQL("CREATE TABLE MessageKeyCacheEntry (`conversationId` TEXT NOT NULL, `messageKey` BLOB NOT NULL, `messageNumber` INTEGER NOT NULL, `publicKey` BLOB NOT NULL, `timestamp` TEXT NOT NULL, PRIMARY KEY(`conversationId`, `messageNumber`, `publicKey`))")
            }
        }

        val migration2To3 = object : Migration(2, 3) {
            private val logger by getLogger()

            override fun migrate(database: SupportSQLiteDatabase) {
                logger.debug("Executing migration from $startVersion to $endVersion.")

                database.execSQL("ALTER TABLE team ADD COLUMN meetingPoint TEXT")
                database.execSQL("CREATE TABLE IF NOT EXISTS LocationSharingState (`userId` TEXT NOT NULL, `groupId` TEXT NOT NULL, `sharingEnabled` INTEGER NOT NULL, `lastUpdate` TEXT NOT NULL, PRIMARY KEY(`userId`, `groupId`))")
            }
        }
    }
}
