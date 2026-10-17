package io.github.magisk317.smscode.runtime.bridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.magisk317.smscode.data.db.AppDatabase
import io.github.magisk317.smscode.data.db.DBManager
import io.github.magisk317.smscode.db.entity.SmsMsg
import io.github.magisk317.smscode.runtime.RuntimeBackupExportResult
import io.github.magisk317.smscode.runtime.RuntimeBackupImportResult
import io.github.magisk317.smscode.runtime.RuntimeBackupRule
import io.github.magisk317.smscode.runtime.RuntimeBackupSmsRecord
import kotlinx.coroutines.flow.Flow
import io.github.magisk317.smscode.data.repository.RoomSmsCodeRuleRepository

/**
 * UI-layer access gateways to runtime services.
 *
 * These interfaces decouple ViewModels / Compose / Activities from the concrete
 * Runtime*Facade objects, enabling constructor / Koin injection and testing.
 * The Facade objects implement these interfaces; only the composition root
 * (Application, DI module, XposedRuntimeInstaller) references the concrete objects.
 *
 * Method signatures intentionally omit default parameter values (Kotlin forbids
 * defaults on overrides); every existing call site passes them explicitly.
 */

/** Database access for UI (app list, rules, records). */
interface UiStorageAccess {
    fun dbManager(context: Context): DBManager
    fun appDatabase(context: Context): AppDatabase

    /**
     * Rule storage for the screens.
     *
     * The UI goes through this instead of the DAO so it does not depend on Room, and
     * so both apps can hold their rules the same way even though XSC stores them in
     * Room and relay through its own repository.
     */
    fun smsCodeRuleRepository(context: Context): RoomSmsCodeRuleRepository
}

/** File-backed entity store (app blocked-config mirror). */
interface UiStoreAccess {
    fun persistAppConfigs(context: Context, appConfigs: List<io.github.magisk317.smscode.db.entity.AppInfo>): Boolean
}

/** Backup / restore SAF intents and payload import/export. */
interface UiBackupAccess {
    fun getExportRuleListSAFIntent(context: Context, includeDatabase: Boolean): Intent
    fun getImportRuleListSAFIntent(context: Context): Intent
    fun exportBackup(
        context: Context,
        uri: Uri,
        ruleList: List<RuntimeBackupRule>,
        preferences: Map<String, String?>?,
        records: List<RuntimeBackupSmsRecord>?,
        appVersion: String,
        includeDatabase: Boolean,
    ): RuntimeBackupExportResult
    fun importRuleList(context: Context, uri: Uri, currentAppVersion: String): RuntimeBackupImportResult
    fun restoreDatabaseFromBackup(context: Context, uri: Uri): Boolean
}

/** SMS code record CRUD + export for the record screen/viewmodel. */
interface UiCodeRecordAccess {
    fun recordsFlow(context: Context): Flow<List<SmsMsg>>
    fun queryRecords(context: Context): List<SmsMsg>
    suspend fun removeRecords(context: Context, records: List<SmsMsg>)
    suspend fun restoreRecords(context: Context, records: List<SmsMsg>)
    fun exportCodeRecords(context: Context, uri: Uri, records: List<SmsMsg>): Boolean
}

/** Notification permission/diagnostics for the settings screen. */
interface UiNotificationAccess {
    fun hasPostNotificationsPermission(context: Context): Boolean
}

/** App-local preference reads needed by UI screens (debug log flag, SIM remark). */
interface UiPrefsAccess {
    suspend fun isSensitiveDebugLogMode(context: Context): Boolean
    suspend fun getSimSlotRemark(context: Context, simSlot: Int): String
}
