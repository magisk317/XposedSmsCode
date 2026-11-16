package io.github.magisk317.smscode.runtime.bridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.magisk317.smscode.runtime.RuntimeBackupExportResult
import io.github.magisk317.smscode.runtime.contract.record.SmsMsgRecordData
import io.github.magisk317.smscode.runtime.RuntimeBackupImportResult
import io.github.magisk317.smscode.runtime.RuntimeBackupRule
import io.github.magisk317.smscode.runtime.RuntimeBackupSmsRecord
import io.github.magisk317.smscode.runtime.RuntimeRecordRestoreStats
import kotlinx.coroutines.flow.Flow
import io.github.magisk317.smscode.rule.repository.SmsCodeRuleRepository

/**
 * UI-layer access gateways to runtime services.
 *
 * These interfaces decouple ViewModels / Compose / Activities from the concrete
 * Runtime*Facade objects, enabling constructor / Koin injection and testing.
 * The Facade objects implement these interfaces; only the composition root
 * (Application, DI module, XposedRuntimeInstaller) references the concrete objects.
 *
 * Signatures deal in UI contracts only: rule storage goes through the shared
 * smscode-core repository, app configs through [UiAppConfig], and backup payloads
 * through the RuntimeBackup* DTOs. No Room implementation type is exposed here.
 *
 * Method signatures intentionally omit default parameter values (Kotlin forbids
 * defaults on overrides); every existing call site passes them explicitly.
 */

/** Storage gateways for UI (app list, rules, record count). */
interface UiStorageAccess {
    /**
     * Rule storage for the screens, expressed in the shared [SmsCodeRuleRepository]
     * contract so neither app depends on the other's Room wiring.
     */
    fun smsCodeRuleRepository(context: Context): SmsCodeRuleRepository

    /** App config storage for the app list screens, expressed in [UiAppConfig]. */
    fun appConfigRepository(context: Context): UiAppConfigRepository

    /** Emits the total stored SMS record count and re-emits whenever it changes. */
    fun smsRecordCountFlow(context: Context): Flow<Long>
}

/** File-backed entity store (app blocked-config mirror). */
interface UiStoreAccess {
    fun persistAppConfigs(context: Context, appConfigs: List<UiAppConfig>): Boolean
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

    /** Reads every stored rule as a backup payload row. */
    fun queryBackupRules(context: Context): List<RuntimeBackupRule>

    /** Reads every stored SMS record as a backup payload row. */
    fun queryBackupSmsRecords(context: Context): List<RuntimeBackupSmsRecord>

    /** Re-inserts backup rules into storage, preserving import semantics. */
    suspend fun restoreRulesFromBackup(context: Context, rules: List<RuntimeBackupRule>)

    /**
     * Re-inserts backup records into storage and returns the row counts observed
     * before and after, for restore diagnostics.
     */
    suspend fun restoreRecordsFromBackup(
        context: Context,
        records: List<RuntimeBackupSmsRecord>,
    ): RuntimeRecordRestoreStats
}

/** SMS code record CRUD + export for the record screen/viewmodel. */
interface UiCodeRecordAccess {
    fun recordsFlow(context: Context): Flow<List<SmsMsgRecordData>>
    fun queryRecords(context: Context): List<SmsMsgRecordData>
    suspend fun removeRecords(context: Context, records: List<SmsMsgRecordData>)
    suspend fun restoreRecords(context: Context, records: List<SmsMsgRecordData>)
    fun exportCodeRecords(context: Context, uri: Uri, records: List<SmsMsgRecordData>): Boolean
}

/** Notification permission/diagnostics for the settings screen. */
interface UiNotificationAccess {
    fun hasPostNotificationsPermission(context: Context): Boolean
}

/** App-local preference reads needed by UI screens. */
interface UiPrefsAccess {
    suspend fun getSimSlotRemark(context: Context, simSlot: Int): String
}
