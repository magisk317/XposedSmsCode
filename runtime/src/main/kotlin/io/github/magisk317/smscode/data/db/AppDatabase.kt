package io.github.magisk317.smscode.data.db

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.magisk317.smscode.db.entity.ForwardFilterRule
import io.github.magisk317.smscode.db.dao.RuleDao
import io.github.magisk317.smscode.db.dao.SenderDao
import io.github.magisk317.smscode.db.ext.ConvertersDate
import io.github.magisk317.smscode.db.ext.ConvertersSenderList
import io.github.magisk317.smscode.db.entity.Rule
import io.github.magisk317.smscode.db.entity.Sender
import io.github.magisk317.smscode.db.dao.AppInfoDao
import io.github.magisk317.smscode.db.dao.AutoInputEventDao
import io.github.magisk317.smscode.db.dao.ForwardFilterRuleDao
import io.github.magisk317.smscode.db.dao.NotifyRouteRuleDao
import io.github.magisk317.smscode.db.dao.SmsCodeRuleDao
import io.github.magisk317.smscode.db.dao.SmsMsgDao
import io.github.magisk317.smscode.db.entity.AppInfo
import io.github.magisk317.smscode.db.entity.AutoInputEvent
import io.github.magisk317.smscode.db.entity.NotifyRouteRule
import io.github.magisk317.smscode.db.entity.SmsCodeRule
import io.github.magisk317.smscode.db.entity.SmsMsg
import io.github.magisk317.smscode.xposed.utils.XLog

@Database(entities = [
    SmsCodeRule::class,
    SmsMsg::class,
    AppInfo::class,
    AutoInputEvent::class,
    NotifyRouteRule::class,
    ForwardFilterRule::class,
    Sender::class,
    Rule::class
], version = 24, exportSchema = true)
@TypeConverters(ConvertersDate::class, ConvertersSenderList::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun smsCodeRuleDao(): SmsCodeRuleDao
    abstract fun smsMsgDao(): SmsMsgDao
    abstract fun appInfoDao(): AppInfoDao
    abstract fun autoInputEventDao(): AutoInputEventDao
    abstract fun notifyRouteRuleDao(): NotifyRouteRuleDao
    abstract fun forwardFilterRuleDao(): ForwardFilterRuleDao
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
                        "`check` TEXT NOT NULL DEFAULT 'is', " +
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

        private val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE Sender ADD COLUMN receive_call_notify INTEGER NOT NULL DEFAULT 0",
                    migration = "14_15",
                )
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sms_msg ADD COLUMN call_type INTEGER NOT NULL DEFAULT 0",
                    migration = "14_15",
                )
            }
        }

        private val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "CREATE TABLE IF NOT EXISTS notify_route_rule (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "scope INTEGER NOT NULL, " +
                        "package_name TEXT NOT NULL, " +
                        "sender_id INTEGER NOT NULL, " +
                        "update_time INTEGER NOT NULL DEFAULT 0" +
                        ")",
                    migration = "15_16",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE UNIQUE INDEX IF NOT EXISTS index_notify_route_rule_scope_package_sender " +
                        "ON notify_route_rule(scope, package_name, sender_id)",
                    migration = "15_16",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_notify_route_rule_package_scope " +
                        "ON notify_route_rule(package_name, scope)",
                    migration = "15_16",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_notify_route_rule_sender_scope " +
                        "ON notify_route_rule(sender_id, scope)",
                    migration = "15_16",
                )
            }
        }

        private val MIGRATION_16_17 = object : androidx.room.migration.Migration(16, 17) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "CREATE TABLE IF NOT EXISTS forward_filter_rule (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "msg_type TEXT NOT NULL, " +
                        "scope_type TEXT NOT NULL, " +
                        "scope_key TEXT NOT NULL DEFAULT '', " +
                        "sender_id INTEGER NOT NULL DEFAULT 0, " +
                        "policy TEXT NOT NULL, " +
                        "match_mode TEXT NOT NULL, " +
                        "pattern TEXT NOT NULL, " +
                        "enabled INTEGER NOT NULL DEFAULT 1, " +
                        "update_time INTEGER NOT NULL DEFAULT 0" +
                        ")",
                    migration = "16_17",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE UNIQUE INDEX IF NOT EXISTS index_forward_filter_rule_unique " +
                        "ON forward_filter_rule(msg_type, scope_type, scope_key, sender_id, policy, match_mode, pattern)",
                    migration = "16_17",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_forward_filter_rule_msg_scope_key " +
                        "ON forward_filter_rule(msg_type, scope_type, scope_key)",
                    migration = "16_17",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_forward_filter_rule_msg_scope_sender " +
                        "ON forward_filter_rule(msg_type, scope_type, sender_id)",
                    migration = "16_17",
                )
            }
        }

        private val MIGRATION_17_18 = object : androidx.room.migration.Migration(17, 18) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sms_msg ADD COLUMN notify_channel_id TEXT NOT NULL DEFAULT ''",
                    migration = "17_18",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_sms_msg_pkg_type_channel_date " +
                        "ON sms_msg(package_name, msg_type, notify_channel_id, date)",
                    migration = "17_18",
                )
            }
        }

        private val MIGRATION_18_19 = object : androidx.room.migration.Migration(18, 19) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sms_msg ADD COLUMN processed_time INTEGER NOT NULL DEFAULT 0",
                    migration = "18_19",
                )
            }
        }

        private val MIGRATION_19_20 = object : androidx.room.migration.Migration(19, 20) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sms_msg ADD COLUMN sim_slot INTEGER NOT NULL DEFAULT -1",
                    migration = "19_20",
                )
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sms_msg ADD COLUMN sub_id INTEGER NOT NULL DEFAULT 0",
                    migration = "19_20",
                )
            }
        }

        private val MIGRATION_20_21 = object : androidx.room.migration.Migration(20, 21) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "CREATE TABLE IF NOT EXISTS auto_input_event (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "record_id INTEGER, " +
                        "package_name TEXT, " +
                        "code_length INTEGER NOT NULL DEFAULT 0, " +
                        "attempt_at INTEGER NOT NULL, " +
                        "success INTEGER, " +
                        "fail_reason TEXT" +
                        ")",
                    migration = "20_21",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_auto_input_attempt_at " +
                        "ON auto_input_event(attempt_at)",
                    migration = "20_21",
                )
                execSqlSafely(
                    db = db,
                    sql = "CREATE INDEX IF NOT EXISTS index_auto_input_record " +
                        "ON auto_input_event(record_id)",
                    migration = "20_21",
                )
            }
        }

        private val MIGRATION_21_22 = object : androidx.room.migration.Migration(21, 22) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sms_msg ADD COLUMN contact_name TEXT NOT NULL DEFAULT ''",
                    migration = "21_22",
                )
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sms_msg ADD COLUMN phone_area TEXT NOT NULL DEFAULT ''",
                    migration = "21_22",
                )
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE sms_msg ADD COLUMN session_key TEXT NOT NULL DEFAULT ''",
                    migration = "21_22",
                )
                execSqlSafely(
                    db = db,
                    sql = "ALTER TABLE app_info ADD COLUMN forwarding_configured INTEGER NOT NULL DEFAULT 0",
                    migration = "21_22",
                )
            }
        }

        /**
         * v22 shipped with sms_msg still carrying the legacy DEFAULT '0' on
         * forward_status / forward_time (added by MIGRATION_3_4), while the
         * entity declares no defaultValue. Room 2.2+ compares column defaults
         * during migration validation, so every database that passed through
         * 3_4 failed "Migration didn't properly handle: sms_msg" on first
         * open. Rebuild the table with the canonical entity DDL to heal any
         * historical drift, preserving all rows and indices.
         */
        private val MIGRATION_22_23 = object : androidx.room.migration.Migration(22, 23) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                rebuildTable(
                    db,
                    "sms_msg",
                    "CREATE TABLE IF NOT EXISTS `sms_msg_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `sender` TEXT, `body` TEXT, `date` INTEGER " +
                        "NOT NULL, `processed_time` INTEGER NOT NULL DEFAULT 0, `company` TEXT, `sms_code` TEXT, `package_name` TEXT, " +
                        "`notify_channel_id` TEXT NOT NULL DEFAULT '', `sim_slot` INTEGER NOT NULL DEFAULT -1, `sub_id` INTEGER NOT NULL DEFAULT " +
                        "0, `contact_name` TEXT NOT NULL DEFAULT '', `phone_area` TEXT NOT NULL DEFAULT '', `forward_status` INTEGER NOT NULL, " +
                        "`forward_target` TEXT, `forward_message` TEXT, `forward_time` INTEGER NOT NULL, `msg_type` INTEGER NOT NULL DEFAULT 0, " +
                        "`call_type` INTEGER NOT NULL DEFAULT 0, `session_key` TEXT NOT NULL DEFAULT '')",
                    "`id`, `sender`, `body`, `date`, `processed_time`, `company`, `sms_code`, `package_name`, `notify_channel_id`, `sim_slot`, " +
                        "`sub_id`, `contact_name`, `phone_area`, `forward_status`, `forward_target`, `forward_message`, `forward_time`, " +
                        "`msg_type`, `call_type`, `session_key`",
                    listOf(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_sms_msg_sender_body_date_msg_type` ON `sms_msg` (`sender`, `body`, `date`, `msg_type`)",
                        "CREATE INDEX IF NOT EXISTS `index_sms_msg_pkg_type_channel_date` ON `sms_msg` (`package_name`, `msg_type`, " +
                            "`notify_channel_id`, `date`)",
                        "CREATE INDEX IF NOT EXISTS `index_sms_msg_type_session_key` ON `sms_msg` (`msg_type`, `session_key`)",
                    ),
                )
            }
        }

        /**
         * Room validation on upgraded databases kept failing one table after
         * another (sms_msg, then Sender) because historical migrations and
         * entity refactors drifted from the canonical schema without bumping
         * the version. Notably no migration ever added
         * `Sender.active_schedule_json` / `priority` / `custom_template`, so
         * devices upgraded through the chain lack those columns entirely.
         * Rebuild every table from the exported canonical DDL (schemas/24.json)
         * so the database is guaranteed to match the entities, preserving all
         * rows and indices.
         */
        private val MIGRATION_23_24 = object : androidx.room.migration.Migration(23, 24) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // FKs are enforced during Room migrations; defer checks to commit.
                db.execSQL("PRAGMA defer_foreign_keys = TRUE")
                // Rule must be staged before Sender is rebuilt, otherwise
                // dropping Sender cascade-deletes every rule. See stageTable.
                stageTable(db, "Rule")
                rebuildTable(
                    db,
                    "sms_code_rule",
                    "CREATE TABLE IF NOT EXISTS `sms_code_rule_new` (`company` TEXT, `code_keyword` TEXT NOT NULL, `code_regex` TEXT NOT NULL, " +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT)",
                    "`company`, `code_keyword`, `code_regex`, `id`",
                    listOf(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_sms_code_rule_company_code_keyword_code_regex` ON `sms_code_rule` (`company`, " +
                            "`code_keyword`, `code_regex`)",
                    ),
                )
                rebuildTable(
                    db,
                    "sms_msg",
                    "CREATE TABLE IF NOT EXISTS `sms_msg_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `sender` TEXT, `body` TEXT, `date` INTEGER " +
                        "NOT NULL, `processed_time` INTEGER NOT NULL DEFAULT 0, `company` TEXT, `sms_code` TEXT, `package_name` TEXT, " +
                        "`notify_channel_id` TEXT NOT NULL DEFAULT '', `sim_slot` INTEGER NOT NULL DEFAULT -1, `sub_id` INTEGER NOT NULL DEFAULT " +
                        "0, `contact_name` TEXT NOT NULL DEFAULT '', `phone_area` TEXT NOT NULL DEFAULT '', `forward_status` INTEGER NOT NULL, " +
                        "`forward_target` TEXT, `forward_message` TEXT, `forward_time` INTEGER NOT NULL, `msg_type` INTEGER NOT NULL DEFAULT 0, " +
                        "`call_type` INTEGER NOT NULL DEFAULT 0, `session_key` TEXT NOT NULL DEFAULT '')",
                    "`id`, `sender`, `body`, `date`, `processed_time`, `company`, `sms_code`, `package_name`, `notify_channel_id`, `sim_slot`, " +
                        "`sub_id`, `contact_name`, `phone_area`, `forward_status`, `forward_target`, `forward_message`, `forward_time`, " +
                        "`msg_type`, `call_type`, `session_key`",
                    listOf(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_sms_msg_sender_body_date_msg_type` ON `sms_msg` (`sender`, `body`, `date`, `msg_type`)",
                        "CREATE INDEX IF NOT EXISTS `index_sms_msg_pkg_type_channel_date` ON `sms_msg` (`package_name`, `msg_type`, " +
                            "`notify_channel_id`, `date`)",
                        "CREATE INDEX IF NOT EXISTS `index_sms_msg_type_session_key` ON `sms_msg` (`msg_type`, `session_key`)",
                    ),
                )
                rebuildTable(
                    db,
                    "app_info",
                    "CREATE TABLE IF NOT EXISTS `app_info_new` (`package_name` TEXT NOT NULL, `label` TEXT, `blocked` INTEGER NOT NULL, " +
                        "`forwarding` INTEGER NOT NULL DEFAULT 0, `forwarding_configured` INTEGER NOT NULL DEFAULT 0, `notify_template` TEXT NOT " +
                        "NULL DEFAULT '', PRIMARY KEY(`package_name`))",
                    "`package_name`, `label`, `blocked`, `forwarding`, `forwarding_configured`, `notify_template`",
                    emptyList(),
                )
                rebuildTable(
                    db,
                    "auto_input_event",
                    "CREATE TABLE IF NOT EXISTS `auto_input_event_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `record_id` INTEGER, " +
                        "`package_name` TEXT, `code_length` INTEGER NOT NULL, `attempt_at` INTEGER NOT NULL, `success` INTEGER, `fail_reason` TEXT)",
                    "`id`, `record_id`, `package_name`, `code_length`, `attempt_at`, `success`, `fail_reason`",
                    listOf(
                        "CREATE INDEX IF NOT EXISTS `index_auto_input_attempt_at` ON `auto_input_event` (`attempt_at`)",
                        "CREATE INDEX IF NOT EXISTS `index_auto_input_record` ON `auto_input_event` (`record_id`)",
                    ),
                )
                rebuildTable(
                    db,
                    "notify_route_rule",
                    "CREATE TABLE IF NOT EXISTS `notify_route_rule_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `scope` INTEGER NOT " +
                        "NULL, `package_name` TEXT NOT NULL, `sender_id` INTEGER NOT NULL, `update_time` INTEGER NOT NULL DEFAULT 0)",
                    "`id`, `scope`, `package_name`, `sender_id`, `update_time`",
                    listOf(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_notify_route_rule_scope_package_sender` ON `notify_route_rule` (`scope`, " +
                            "`package_name`, `sender_id`)",
                        "CREATE INDEX IF NOT EXISTS `index_notify_route_rule_package_scope` ON `notify_route_rule` (`package_name`, `scope`)",
                        "CREATE INDEX IF NOT EXISTS `index_notify_route_rule_sender_scope` ON `notify_route_rule` (`sender_id`, `scope`)",
                    ),
                )
                rebuildTable(
                    db,
                    "forward_filter_rule",
                    "CREATE TABLE IF NOT EXISTS `forward_filter_rule_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `msg_type` TEXT NOT " +
                        "NULL, `scope_type` TEXT NOT NULL, `scope_key` TEXT NOT NULL, `sender_id` INTEGER NOT NULL DEFAULT 0, `policy` TEXT NOT " +
                        "NULL, `match_mode` TEXT NOT NULL, `pattern` TEXT NOT NULL, `enabled` INTEGER NOT NULL DEFAULT 1, `update_time` INTEGER " +
                        "NOT NULL DEFAULT 0)",
                    "`id`, `msg_type`, `scope_type`, `scope_key`, `sender_id`, `policy`, `match_mode`, `pattern`, `enabled`, `update_time`",
                    listOf(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_forward_filter_rule_unique` ON `forward_filter_rule` (`msg_type`, `scope_type`, " +
                            "`scope_key`, `sender_id`, `policy`, `match_mode`, `pattern`)",
                        "CREATE INDEX IF NOT EXISTS `index_forward_filter_rule_msg_scope_key` ON `forward_filter_rule` (`msg_type`, `scope_type`, `scope_key`)",
                        "CREATE INDEX IF NOT EXISTS `index_forward_filter_rule_msg_scope_sender` ON `forward_filter_rule` (`msg_type`, " +
                            "`scope_type`, `sender_id`)",
                    ),
                )
                rebuildTable(
                    db,
                    "Sender",
                    "CREATE TABLE IF NOT EXISTS `Sender_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` INTEGER NOT NULL DEFAULT 1, " +
                        "`name` TEXT NOT NULL DEFAULT '', `json_setting` TEXT NOT NULL DEFAULT '', `status` INTEGER NOT NULL DEFAULT 1, `time` " +
                        "INTEGER NOT NULL, `receive_code` INTEGER NOT NULL DEFAULT 1, `receive_non_code` INTEGER NOT NULL DEFAULT 0, " +
                        "`receive_app_notify` INTEGER NOT NULL DEFAULT 1, `receive_call_notify` INTEGER NOT NULL DEFAULT 0, `active_schedule_json` " +
                        "TEXT NOT NULL DEFAULT '', `priority` INTEGER NOT NULL DEFAULT 0, `custom_template` TEXT NOT NULL DEFAULT '')",
                    "`id`, `type`, `name`, `json_setting`, `status`, `time`, `receive_code`, `receive_non_code`, `receive_app_notify`, " +
                        "`receive_call_notify`, `active_schedule_json`, `priority`, `custom_template`",
                    emptyList(),
                )
                rebuildTable(
                    db,
                    "Rule",
                    "CREATE TABLE IF NOT EXISTS `Rule_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL DEFAULT 'sms', " +
                        "`filed` TEXT NOT NULL DEFAULT 'transpond_all', `check` TEXT NOT NULL DEFAULT 'is', `value` TEXT NOT NULL DEFAULT '', " +
                        "`sender_id` INTEGER NOT NULL DEFAULT 0, `sms_template` TEXT NOT NULL DEFAULT '', `regex_replace` TEXT NOT NULL DEFAULT " +
                        "'', `sim_slot` TEXT NOT NULL DEFAULT 'ALL', `status` INTEGER NOT NULL DEFAULT 1, `time` INTEGER NOT NULL, `sender_list` " +
                        "TEXT NOT NULL DEFAULT '', `sender_logic` TEXT NOT NULL DEFAULT 'ALL', `silent_period_start` INTEGER NOT NULL DEFAULT 0, " +
                        "`silent_period_end` INTEGER NOT NULL DEFAULT 0, `silent_day_of_week` TEXT NOT NULL DEFAULT '', `title` TEXT NOT NULL " +
                        "DEFAULT '', FOREIGN KEY(`sender_id`) REFERENCES `Sender`(`id`) ON UPDATE CASCADE ON DELETE CASCADE )",
                    "`id`, `type`, `filed`, `check`, `value`, `sender_id`, `sms_template`, `regex_replace`, `sim_slot`, `status`, `time`, " +
                        "`sender_list`, `sender_logic`, `silent_period_start`, `silent_period_end`, `silent_day_of_week`, `title`",
                    listOf(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_Rule_id` ON `Rule` (`id`)",
                        "CREATE INDEX IF NOT EXISTS `index_Rule_sender_id` ON `Rule` (`sender_id`)",
                        "CREATE INDEX IF NOT EXISTS `index_Rule_sender_list` ON `Rule` (`sender_list`)",
                    ),
                    sourceTable = "Rule_stage",
                )
                db.execSQL("DROP TABLE IF EXISTS `Rule_stage`")
            }
        }

        /**
         * Rebuild [table] from its canonical [createSql], copying rows from
         * [sourceTable]. Historical migrations drifted from the entity schema
         * without bumping the version, so the copy is column-aware: only
         * columns that actually exist on the old table are copied, and missing
         * ones are filled from the canonical DEFAULT (or a type-appropriate
         * zero value when the column declares none). Columns present on the
         * old table but absent from the canonical DDL are dropped, which is
         * what Room validation requires.
         */
        private fun rebuildTable(
            db: androidx.sqlite.db.SupportSQLiteDatabase,
            table: String,
            createSql: String,
            columns: String,
            indices: List<String>,
            sourceTable: String = table,
        ) {
            db.execSQL(createSql)
            if (!tableExists(db, sourceTable)) {
                db.execSQL("ALTER TABLE `${table}_new` RENAME TO `$table`")
                indices.forEach { db.execSQL(it) }
                return
            }
            val present = mutableSetOf<String>()
            db.query("PRAGMA table_info(`$sourceTable`)").use { cursor ->
                while (cursor.moveToNext()) {
                    present += cursor.getString(1)
                }
            }
            val columnSql = StringBuilder()
            val selectSql = StringBuilder()
            columns.split(",").forEach { column ->
                val name = column.trim().trim('`')
                if (columnSql.isNotEmpty()) {
                    columnSql.append(", ")
                    selectSql.append(", ")
                }
                columnSql.append('`').append(name).append('`')
                selectSql.append(if (name in present) "`$name`" else fallbackValue(createSql, name))
            }
            db.execSQL("INSERT INTO `${table}_new` ($columnSql) SELECT $selectSql FROM `$sourceTable`")
            db.execSQL("DROP TABLE IF EXISTS `$table`")
            db.execSQL("ALTER TABLE `${table}_new` RENAME TO `$table`")
            indices.forEach { db.execSQL(it) }
        }

        /**
         * Rule references Sender with ON DELETE CASCADE, and DROP TABLE runs an
         * implicit DELETE on the parent table that fires the cascade even when
         * foreign-key checks are deferred — rebuilding Sender would wipe every
         * rule. Stage the child's rows in a constraint-free table, drop it,
         * rebuild the parent, then restore the child from the stage.
         */
        private fun stageTable(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String) {
            if (!tableExists(db, table)) {
                return
            }
            db.execSQL("DROP TABLE IF EXISTS `${table}_stage`")
            db.execSQL("CREATE TABLE `${table}_stage` AS SELECT * FROM `$table`")
            db.execSQL("DROP TABLE `$table`")
        }

        private fun tableExists(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Boolean {
            db.query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)).use { cursor ->
                return cursor.moveToFirst()
            }
        }

        /** SQL literal used when [column] is missing from the table being rebuilt. */
        private fun fallbackValue(createSql: String, column: String): String {
            if (column == "id") {
                // INTEGER PRIMARY KEY AUTOINCREMENT assigns a fresh rowid for NULL.
                return "NULL"
            }
            val withDefault = Regex("`$column`\\s+\\w+[^,]*?DEFAULT\\s+('[^']*'|-?[0-9.]+)").find(createSql)
            if (withDefault != null) {
                return withDefault.groupValues[1]
            }
            val type = Regex("`$column`\\s+(\\w+)").find(createSql)?.groupValues?.get(1)
            return when (type) {
                "TEXT" -> "''"
                "REAL" -> "0"
                "BLOB" -> "X''"
                else -> "0"
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
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18,
                    MIGRATION_18_19,
                    MIGRATION_19_20,
                    MIGRATION_20_21,
                    MIGRATION_21_22,
                    MIGRATION_22_23,
                    MIGRATION_23_24,
                )
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
