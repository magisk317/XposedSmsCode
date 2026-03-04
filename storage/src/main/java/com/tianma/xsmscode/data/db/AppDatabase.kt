package com.tianma.xsmscode.data.db

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.github.magisk317.smscode.forwarder.database.dao.RuleDao
import com.github.magisk317.smscode.forwarder.database.dao.SenderDao
import com.github.magisk317.smscode.forwarder.database.ext.ConvertersDate
import com.github.magisk317.smscode.forwarder.database.ext.ConvertersSenderList
import com.github.magisk317.smscode.forwarder.entity.Rule
import com.github.magisk317.smscode.forwarder.entity.Sender
import com.tianma.xsmscode.data.db.dao.AppInfoDao
import com.tianma.xsmscode.data.db.dao.SmsCodeRuleDao
import com.tianma.xsmscode.data.db.dao.SmsMsgDao
import com.tianma.xsmscode.data.db.entity.AppInfo
import com.tianma.xsmscode.data.db.entity.SmsCodeRule
import com.tianma.xsmscode.data.db.entity.SmsMsg
import com.tianma.xsmscode.common.utils.XLog

@Database(entities = [
    SmsCodeRule::class,
    SmsMsg::class,
    AppInfo::class,
    Sender::class,
    Rule::class
], version = 14, exportSchema = false)
@TypeConverters(ConvertersDate::class, ConvertersSenderList::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun smsCodeRuleDao(): SmsCodeRuleDao
    abstract fun smsMsgDao(): SmsMsgDao
    abstract fun appInfoDao(): AppInfoDao
    abstract fun ruleDao(): RuleDao
    abstract fun senderDao(): SenderDao

    companion object {
        private const val DATABASE_NAME = "xsmscode_room.db"

        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sms_msg ADD COLUMN package_name TEXT",
                    migration = "1_2",
                )
            }
        }

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "DELETE FROM sms_msg WHERE id NOT IN " +
                        "(SELECT MIN(id) FROM sms_msg GROUP BY sender, body, date)",
                    migration = "2_3",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE UNIQUE INDEX IF NOT EXISTS index_sms_msg_sender_body_date " +
                        "ON sms_msg(sender, body, date)",
                    migration = "2_3",
                )
            }
        }

        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sms_msg ADD COLUMN forward_status INTEGER NOT NULL DEFAULT 0",
                    migration = "3_4",
                )
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sms_msg ADD COLUMN forward_target TEXT",
                    migration = "3_4",
                )
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sms_msg ADD COLUMN forward_message TEXT",
                    migration = "3_4",
                )
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sms_msg ADD COLUMN forward_time INTEGER NOT NULL DEFAULT 0",
                    migration = "3_4",
                )
            }
        }

        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "CREATE TABLE IF NOT EXISTS Sender (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "type INTEGER NOT NULL DEFAULT 1, " +
                        "name TEXT NOT NULL DEFAULT '', " +
                        "json_setting TEXT NOT NULL DEFAULT '', " +
                        "status INTEGER NOT NULL DEFAULT 1, " +
                        "time INTEGER NOT NULL" +
                        ")",
                    migration = "4_5",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE TABLE IF NOT EXISTS Rule (" +
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
                    migration = "4_5",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE UNIQUE INDEX IF NOT EXISTS index_Rule_id ON Rule(id)",
                    migration = "4_5",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_Rule_sender_id ON Rule(sender_id)",
                    migration = "4_5",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_Rule_sender_list ON Rule(sender_list)",
                    migration = "4_5",
                )
            }
        }

        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE Sender ADD COLUMN receive_non_code INTEGER NOT NULL DEFAULT 0",
                    migration = "5_6",
                )
            }
        }

        private val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Bridge migration kept as no-op so legacy databases can connect to the 7+ chain.
            }
        }

        private val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sms_msg ADD COLUMN msg_type INTEGER NOT NULL DEFAULT 0",
                    migration = "7_8",
                )
            }
        }

        private val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE app_info ADD COLUMN forwarding INTEGER NOT NULL DEFAULT 0",
                    migration = "8_9",
                )
            }
        }


        private val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Ensure forwarding column exists in app_info if it wasn't added in 8_9
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE app_info ADD COLUMN forwarding INTEGER NOT NULL DEFAULT 0",
                    migration = "9_10",
                )
            }
        }

        private val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE app_info ADD COLUMN notify_template TEXT NOT NULL DEFAULT ''",
                    migration = "10_11",
                )
            }
        }

        private val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE Sender ADD COLUMN receive_app_notify INTEGER NOT NULL DEFAULT 1",
                    migration = "11_12",
                )
            }
        }

        private val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "DROP INDEX IF EXISTS index_sms_msg_sender_body_date",
                    migration = "12_13",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE UNIQUE INDEX IF NOT EXISTS index_sms_msg_sender_body_date_msg_type " +
                        "ON sms_msg(sender, body, date, msg_type)",
                    migration = "12_13",
                )
            }
        }

        private val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE Sender ADD COLUMN receive_code INTEGER NOT NULL DEFAULT 1",
                    migration = "13_14",
                )
            }
        }

        private fun execSqlSafely(
            db: androidx.sqlite.db.SupportSQLiteDatabase,
            sql: String,
            migration: String,
        ) {
            try {
                db.execSQL(sql)
            } catch (e: SQLiteException) {
                XLog.w("Room migration %s skipped SQL: %s (%s)", migration, sql, e.message ?: "unknown")
            }
        }

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            val dbContext = context.applicationContext ?: context
            instance ?: Room.databaseBuilder(
                dbContext,
                AppDatabase::class.java,
                DATABASE_NAME,
            )
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                )
                .fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4, 5, 6)
                .enableMultiInstanceInvalidation()
                .build().also { instance = it }
        }

        @JvmStatic
        fun closeInstance() {
            synchronized(this) {
                runCatching { instance?.close() }
                instance = null
            }
        }
    }
}
