package tice.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL
import androidx.core.database.getStringOrNull
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.Assertions
import tice.managers.storageManagers.AppDatabase
import tice.managers.storageManagers.migration.MigrationManager
import tice.models.*
import tice.utility.uuidString
import java.net.URL
import java.util.*

internal class MigrationManagerTest {
    private val TEST_DB = "migration-test"

    @Rule
    @JvmField
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    private fun getAppDatabase(migrations: Array<Migration> = emptyArray()): AppDatabase {
        return Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            TEST_DB
        ).addMigrations(*migrations).build().apply {
            openHelper.readableDatabase
            close()
        }
    }

    @Test
    fun migration1To2() = runBlocking {
        data class OldConversationStateEntity(
            val userId: UserId,
            val conversationId: ConversationId,
            val rootKey: SecretKey,
            val rootChainPublicKey: PublicKey,
            val rootChainPrivateKey: PrivateKey,
            val rootChainRemotePublicKey: PublicKey?,
            val sendingChainKey: SecretKey?,
            val receivingChainKey: SecretKey?,
            val sendMessageNumber: Int,
            val receivedMessageNumber: Int,
            val previousSendingChanLength: Int,
            val messageKeyCache: String
        )

        val oldConversationStateEntity = OldConversationStateEntity(
            UserId.randomUUID(),
            ConversationId.randomUUID(),
            "rootKey".encodeToByteArray(),
            "rootChainPublicKey".encodeToByteArray(),
            "rootChainPrivateKey".encodeToByteArray(),
            "rootChainRemotePublicKey".encodeToByteArray(),
            "sendingChainKey".encodeToByteArray(),
            "receivingChainKey".encodeToByteArray(),
            815,
            42,
            1337,
            "messageKeyCache"
        )

        helper.createDatabase(TEST_DB, 1).apply {
            val contentValues = ContentValues(12)
            contentValues.put("userId", oldConversationStateEntity.userId.uuidString())
            contentValues.put("conversationId", oldConversationStateEntity.conversationId.uuidString())
            contentValues.put("rootKey", oldConversationStateEntity.rootKey)
            contentValues.put("rootChainPublicKey", oldConversationStateEntity.rootChainPublicKey)
            contentValues.put("rootChainPrivateKey", oldConversationStateEntity.rootChainPrivateKey)
            contentValues.put("rootChainRemotePublicKey", oldConversationStateEntity.rootChainRemotePublicKey)
            contentValues.put("sendingChainKey", oldConversationStateEntity.sendingChainKey)
            contentValues.put("receivingChainKey", oldConversationStateEntity.receivingChainKey)
            contentValues.put("sendMessageNumber", oldConversationStateEntity.sendMessageNumber)
            contentValues.put("receivedMessageNumber", oldConversationStateEntity.receivedMessageNumber)
            contentValues.put("previousSendingChanLength", oldConversationStateEntity.previousSendingChanLength)
            contentValues.put("messageKeyCache", oldConversationStateEntity.messageKeyCache)

            insert("ConversationStateEntity", CONFLICT_FAIL, contentValues)

            close()
        }

        val database = helper.runMigrationsAndValidate(TEST_DB, 2, true, MigrationManager.migration1To2)

        Assertions.assertEquals(2, database.version)

        val conversationStateCursor = database.query("SELECT * FROM ConversationStateEntity")
        conversationStateCursor.moveToNext()
        var columnIndex = conversationStateCursor.getColumnIndex("rootKey")
        Assertions.assertArrayEquals(oldConversationStateEntity.rootKey, conversationStateCursor.getBlob(columnIndex))

        columnIndex = conversationStateCursor.getColumnIndex("rootChainPublicKey")
        Assertions.assertArrayEquals(oldConversationStateEntity.rootChainPublicKey, conversationStateCursor.getBlob(columnIndex))

        columnIndex = conversationStateCursor.getColumnIndex("rootChainPrivateKey")
        Assertions.assertArrayEquals(oldConversationStateEntity.rootChainPrivateKey, conversationStateCursor.getBlob(columnIndex))

        columnIndex = conversationStateCursor.getColumnIndex("rootChainRemotePublicKey")
        Assertions.assertArrayEquals(oldConversationStateEntity.rootChainRemotePublicKey, conversationStateCursor.getBlob(columnIndex))

        columnIndex = conversationStateCursor.getColumnIndex("sendingChainKey")
        Assertions.assertArrayEquals(oldConversationStateEntity.sendingChainKey, conversationStateCursor.getBlob(columnIndex))

        columnIndex = conversationStateCursor.getColumnIndex("receivingChainKey")
        Assertions.assertArrayEquals(oldConversationStateEntity.receivingChainKey, conversationStateCursor.getBlob(columnIndex))

        columnIndex = conversationStateCursor.getColumnIndex("sendMessageNumber")
        Assertions.assertEquals(oldConversationStateEntity.sendMessageNumber, conversationStateCursor.getInt(columnIndex))

        columnIndex = conversationStateCursor.getColumnIndex("receivedMessageNumber")
        Assertions.assertEquals(oldConversationStateEntity.receivedMessageNumber, conversationStateCursor.getInt(columnIndex))

        columnIndex = conversationStateCursor.getColumnIndex("previousSendingChanLength")
        Assertions.assertEquals(oldConversationStateEntity.previousSendingChanLength, conversationStateCursor.getInt(columnIndex))
    }

    @Test
    fun migration2To3() = runBlocking {
        data class OldTeam(
            val groupId: GroupId,
            val groupKey: SecretKey,
            val owner: UserId,
            val joinMode: JoinMode,
            val permissionMode: PermissionMode,
            var tag: GroupTag,
            val url: URL,
            var name: String? = null,
            var meetupId: GroupId? = null,
        )

        val oldTeam = OldTeam(
            UUID.randomUUID(),
            "secretKey".toByteArray(),
            UUID.randomUUID(),
            JoinMode.Open,
            PermissionMode.Admin,
            "tag",
            URL("https://www.test.com"),
            "name",
            UUID.randomUUID()
        )

        helper.createDatabase(TEST_DB, 2).apply {
            val contentValues = ContentValues(9)
            contentValues.put("groupId", oldTeam.groupId.uuidString())
            contentValues.put("groupKey", oldTeam.groupKey)
            contentValues.put("owner", oldTeam.owner.uuidString())
            contentValues.put("joinMode", oldTeam.joinMode.toString())
            contentValues.put("permissionMode", oldTeam.permissionMode.toString())
            contentValues.put("tag", oldTeam.tag)
            contentValues.put("url", oldTeam.url.toString())
            contentValues.put("name", oldTeam.name)
            contentValues.put("meetupId", oldTeam.meetupId?.uuidString())

            insert("Team", CONFLICT_FAIL, contentValues)

            close()
        }

        val database = helper.runMigrationsAndValidate(TEST_DB, 3, true, MigrationManager.migration2To3)

        Assertions.assertEquals(3, database.version)

        val cursor = database.query("SELECT * FROM Team WHERE groupId='${oldTeam.groupId.uuidString()}'")
        cursor.moveToNext()
        val index = cursor.getColumnIndex("meetingPoint")
        Assertions.assertNull(cursor.getStringOrNull(index))

        val cursor2 = database.query("SELECT * FROM sqlite_master WHERE type='table' AND tbl_name='LocationSharingState'")
        cursor2.moveToNext()
        val index2 = cursor2.getColumnIndex("tbl_name")
        val value = cursor2.getString(index2)
        Assertions.assertEquals("LocationSharingState", value)

        val masterTableCursor = database.query("SELECT * FROM sqlite_master WHERE type='table' AND tbl_name='LocationSharingState'")
        masterTableCursor.moveToNext()
        val tableNameIndex = masterTableCursor.getColumnIndex("tbl_name")
        val tableName = masterTableCursor.getString(tableNameIndex)
        Assertions.assertEquals("LocationSharingState", tableName)
    }

    @Test
    fun allMigrations() {
        helper.createDatabase(TEST_DB, 1).apply {
            close()
        }

        getAppDatabase(MigrationManager.allMigrations())
    }
}
