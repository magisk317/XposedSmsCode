package com.tianma.xsmscode.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resetDatabase()
    }

    @After
    fun tearDown() {
        resetDatabase()
    }

    @Test
    fun migrate_3_to_14_success() {
        createVersion3Database()

        val db = openMigratedDatabase()
        assertFinalSchema(db)
    }

    @Test
    fun migrate_1_to_14_success() {
        createVersion1Database()

        val db = openMigratedDatabase()
        assertFinalSchema(db)
    }

    @Test
    fun migrate_6_to_14_success() {
        createVersion6Database()

        val db = openMigratedDatabase()
        assertFinalSchema(db)
    }

    private fun openMigratedDatabase(): SupportSQLiteDatabase {
        val db = AppDatabase.getInstance(context)
        return db.openHelper.writableDatabase
    }

    private fun assertFinalSchema(db: SupportSQLiteDatabase) {
        assertEquals(14, queryUserVersion(db))

        assertColumnExists(db, table = "sms_msg", column = "msg_type")

        assertColumnExists(db, table = "Sender", column = "receive_non_code")
        assertColumnExists(db, table = "Sender", column = "receive_app_notify")
        assertColumnExists(db, table = "Sender", column = "receive_code")

        assertColumnExists(db, table = "app_info", column = "forwarding")
        assertColumnExists(db, table = "app_info", column = "notify_template")

        assertIndexExists(db, table = "sms_msg", index = "index_sms_msg_sender_body_date_msg_type")
    }

    private fun queryUserVersion(db: SupportSQLiteDatabase): Int {
        db.query("PRAGMA user_version").use { cursor ->
            assertTrue("PRAGMA user_version should return one row", cursor.moveToFirst())
            return cursor.getInt(0)
        }
    }

    private fun assertColumnExists(
        db: SupportSQLiteDatabase,
        table: String,
        column: String,
    ) {
        val columns = mutableSetOf<String>()
        db.query("PRAGMA table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
            }
        }
        assertTrue("Column $table.$column should exist, actual=$columns", column in columns)
    }

    private fun assertIndexExists(
        db: SupportSQLiteDatabase,
        table: String,
        index: String,
    ) {
        val indexes = mutableSetOf<String>()
        db.query("PRAGMA index_list($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                indexes += cursor.getString(nameIndex)
            }
        }
        assertTrue("Index $index should exist on $table, actual=$indexes", index in indexes)
    }

    private fun createVersion1Database() {
        createLegacyDatabase(
            version = 1,
            createSenderAndRule = false,
            includePackageName = false,
            includeForwardColumns = false,
            includeReceiveNonCode = false,
        )
    }

    private fun createVersion3Database() {
        createLegacyDatabase(
            version = 3,
            createSenderAndRule = false,
            includePackageName = true,
            includeForwardColumns = false,
            includeReceiveNonCode = false,
        )
    }

    private fun createVersion6Database() {
        createLegacyDatabase(
            version = 6,
            createSenderAndRule = true,
            includePackageName = true,
            includeForwardColumns = true,
            includeReceiveNonCode = true,
        )
    }

    private fun createLegacyDatabase(
        version: Int,
        createSenderAndRule: Boolean,
        includePackageName: Boolean,
        includeForwardColumns: Boolean,
        includeReceiveNonCode: Boolean,
    ) {
        val dbPath = context.getDatabasePath(DATABASE_NAME)
        dbPath.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(dbPath, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS sms_code_rule (" +
                    "company TEXT, " +
                    "code_keyword TEXT NOT NULL, " +
                    "code_regex TEXT NOT NULL, " +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT" +
                    ")",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_sms_code_rule_company_code_keyword_code_regex " +
                    "ON sms_code_rule(company, code_keyword, code_regex)",
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS app_info (" +
                    "package_name TEXT NOT NULL, " +
                    "label TEXT, " +
                    "blocked INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(package_name)" +
                    ")",
            )

            val smsMsgColumns = mutableListOf(
                "id INTEGER PRIMARY KEY AUTOINCREMENT",
                "sender TEXT",
                "body TEXT",
                "date INTEGER NOT NULL DEFAULT 0",
                "company TEXT",
                "sms_code TEXT",
            ).apply {
                if (includePackageName) add("package_name TEXT")
                if (includeForwardColumns) {
                    add("forward_status INTEGER NOT NULL DEFAULT 0")
                    add("forward_target TEXT")
                    add("forward_message TEXT")
                    add("forward_time INTEGER NOT NULL DEFAULT 0")
                }
            }
            db.execSQL("CREATE TABLE IF NOT EXISTS sms_msg (${smsMsgColumns.joinToString(", ")})")

            if (version >= 3) {
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_sms_msg_sender_body_date " +
                        "ON sms_msg(sender, body, date)",
                )
            }

            if (createSenderAndRule) {
                val senderColumns = mutableListOf(
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL",
                    "type INTEGER NOT NULL DEFAULT 1",
                    "name TEXT NOT NULL DEFAULT ''",
                    "json_setting TEXT NOT NULL DEFAULT ''",
                    "status INTEGER NOT NULL DEFAULT 1",
                    "time INTEGER NOT NULL",
                ).apply {
                    if (includeReceiveNonCode) {
                        add("receive_non_code INTEGER NOT NULL DEFAULT 0")
                    }
                }
                db.execSQL("CREATE TABLE IF NOT EXISTS Sender (${senderColumns.joinToString(", ")})")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS Rule (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "type TEXT NOT NULL DEFAULT 'sms', " +
                        "filed TEXT NOT NULL DEFAULT 'transpond_all', " +
                        "check TEXT NOT NULL DEFAULT 'is', " +
                        "value TEXT NOT NULL DEFAULT '', " +
                        "sender_id INTEGER NOT NULL DEFAULT 0, " +
                        "sms_template TEXT NOT NULL DEFAULT '', " +
                        "regex_replace TEXT NOT NULL DEFAULT '', " +
                        "sim_slot TEXT NOT NULL DEFAULT 'ALL', " +
                        "status INTEGER NOT NULL DEFAULT 1, " +
                        "time INTEGER NOT NULL, " +
                        "sender_list TEXT NOT NULL DEFAULT '', " +
                        "sender_logic TEXT NOT NULL DEFAULT 'ALL', " +
                        "silent_period_start INTEGER NOT NULL DEFAULT 0, " +
                        "silent_period_end INTEGER NOT NULL DEFAULT 0, " +
                        "silent_day_of_week TEXT NOT NULL DEFAULT '', " +
                        "title TEXT NOT NULL DEFAULT '', " +
                        "FOREIGN KEY(sender_id) REFERENCES Sender(id) ON UPDATE CASCADE ON DELETE CASCADE" +
                        ")",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_Rule_id ON Rule(id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_Rule_sender_id ON Rule(sender_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_Rule_sender_list ON Rule(sender_list)")
            }

            db.version = version
        }
    }

    private fun resetDatabase() {
        AppDatabase.closeInstance()
        context.deleteDatabase(DATABASE_NAME)
        context.getDatabasePath("$DATABASE_NAME-wal").delete()
        context.getDatabasePath("$DATABASE_NAME-shm").delete()
    }

    companion object {
        private const val DATABASE_NAME = "xsmscode_room.db"
    }
}
