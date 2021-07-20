package tice.managers.storageManagers

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.RequiresApi
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.sqlcipher.database.SupportFactory
import tice.crypto.CryptoManagerType
import tice.dagger.scopes.AppScope
import tice.exceptions.DatabaseManagerException
import tice.models.*
import tice.models.database.*
import tice.models.messaging.conversation.InboundConversationInvitation
import tice.models.messaging.conversation.InvalidConversation
import tice.models.messaging.conversation.OutboundConversationInvitation
import tice.utility.Converters
import tice.utility.dataFromBase64
import tice.utility.getLogger
import tice.utility.toBase64String
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Named

@Database(
    entities = [
        Team::class,
        Meetup::class,
        MembershipEntity::class,
        LocationSharingState::class,
        User::class,
        IdentityKeyPairEntity::class,
        SigningKeyPairEntity::class,
        GroupKeyEntity::class,
        PrekeyEntity::class,
        OneTimePrekeyEntity::class,
        MembershipCertificateEntity::class,
        ConversationStateEntity::class,
        InboundConversationInvitation::class,
        OutboundConversationInvitation::class,
        ReceivedReset::class,
        InvalidConversation::class,
        MessageEntity::class,
        MessageKeyCacheEntry::class
    ],
    version = 3
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationSharingInterface(): LocationSharingInterface
    abstract fun groupInterface(): GroupInterface
    abstract fun userInterface(): UserInterface
    abstract fun identityKeyPairInterface(): IdentityKeyPairInterface
    abstract fun signingKeyPairInterface(): SigningKeyPairInterface
    abstract fun groupKeyInterface(): GroupKeyInterface
    abstract fun prekeyInterface(): PrekeyInterface
    abstract fun oneTimePrekeyInterface(): OneTimePrekeyInterface
    abstract fun membershipCertificateInterface(): MembershipCertificateInterface
    abstract fun conversationStateInterface(): ConversationInterface
    abstract fun chatMessageInterface(): ChatMessageInterface
    abstract fun messageKeyCacheInterface(): MessageKeyCacheInterface
}

@AppScope
class DatabaseManager @Inject constructor(
    private val storageLocker: StorageLockerType,
    private val cryptoManager: CryptoManagerType,
    @Named("DATABASE_KEY_LENGTH") val databaseKeyLength: Int
) {
    companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val MASTER_KEY_ALIAS = "MASTER_KEY"
        const val ALGORITHM_SPEC = "AES/GCM/NoPadding"
    }

    private val logger by getLogger()

    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)

    init {
        this.keyStore.load(null)
    }

    fun setupDatabase(context: Context): AppDatabase {
        val databaseKey = getStoredDatabaseKey() ?: createDatabaseKey()

        val factory = SupportFactory(databaseKey)
        return Room.databaseBuilder(context, AppDatabase::class.java, "db")
            .openHelperFactory(factory)
            .build()
    }

    private fun getStoredDatabaseKey(): SecretKey? {
        logger.debug("Loading encrypted database key.")
        storageLocker.load(StorageLockerType.StorageKey.ENCRYPTED_DATABASE_KEY)?.dataFromBase64()?.let { return decryptWithMasterKey(it) }

        logger.debug("Did not find stored encrypted database key. Checking for key stored in plaintext.")
        return storageLocker.load(StorageLockerType.StorageKey.PLAINTEXT_DATABASE_KEY)?.dataFromBase64()
    }

    private fun createDatabaseKey(): SecretKey {
        logger.debug("Generate new database key.")
        val databaseKey = cryptoManager.generateDatabaseKey(databaseKeyLength)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            logger.debug("Encrypting database key and store it.")
            val databaseKeyCiphertext = encryptWithMasterKey(databaseKey)
            storageLocker.store(
                StorageLockerType.StorageKey.ENCRYPTED_DATABASE_KEY,
                Base64.encodeToString(databaseKeyCiphertext, Base64.NO_WRAP)
            )
        } else {
            logger.debug("Storing plaintext database key because the app is running on Android < 6 (M).")
            storageLocker.store(
                StorageLockerType.StorageKey.PLAINTEXT_DATABASE_KEY,
                Base64.encodeToString(databaseKey, Base64.NO_WRAP)
            )
        }

        return databaseKey
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun encryptWithMasterKey(plaintext: ByteArray): Ciphertext {
        val masterKey = loadMasterKey() ?: generateMasterKey()

        logger.debug("Encrypting with master key.")

        val cipher = Cipher.getInstance(ALGORITHM_SPEC)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)

        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)

        storageLocker.store(StorageLockerType.StorageKey.DATABASE_KEY_ENCRYPTION_IV, iv.toBase64String())

        return ciphertext
    }

    private fun decryptWithMasterKey(ciphertext: Ciphertext): SecretKey {
        logger.debug("Decrypting with master key.")

        val iv = storageLocker.load(StorageLockerType.StorageKey.DATABASE_KEY_ENCRYPTION_IV)?.dataFromBase64()
            ?: throw DatabaseManagerException.DatabaseEncryptionIVMissing

        val secretKeyEntry = keyStore.getEntry(MASTER_KEY_ALIAS, null) as KeyStore.SecretKeyEntry
        val secretKey = secretKeyEntry.secretKey

        val cipher = Cipher.getInstance(ALGORITHM_SPEC)
        val gcmParameterSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)

        return cipher.doFinal(ciphertext)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun generateMasterKey(): javax.crypto.SecretKey {
        logger.debug("Generating master key.")

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val keyGenParameterSpec = KeyGenParameterSpec
            .Builder(MASTER_KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT + KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    private fun loadMasterKey(): javax.crypto.SecretKey? {
        if (!keyStore.aliases().asSequence().contains(MASTER_KEY_ALIAS)) return null

        logger.debug("Loading master key.")
        val masterKeyEntry = keyStore.getEntry(MASTER_KEY_ALIAS, null) as KeyStore.SecretKeyEntry
        return masterKeyEntry.secretKey
    }
}
