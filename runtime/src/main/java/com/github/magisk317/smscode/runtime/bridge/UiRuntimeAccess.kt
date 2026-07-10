package com.github.magisk317.smscode.runtime.bridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.github.magisk317.smscode.data.db.AppDatabase
import com.github.magisk317.smscode.data.db.DBManager
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.runtime.RuntimeApkVerificationResult
import com.github.magisk317.smscode.runtime.RuntimeBackupExportResult
import com.github.magisk317.smscode.runtime.RuntimeBackupImportResult
import com.github.magisk317.smscode.runtime.RuntimeBackupRule
import com.github.magisk317.smscode.runtime.RuntimeBackupSmsRecord
import com.github.magisk317.smscode.runtime.RuntimePlayAction
import com.github.magisk317.smscode.runtime.RuntimeStartupTarget
import com.github.magisk317.smscode.runtime.RuntimeUpgradeApkAsset
import com.github.magisk317.smscode.runtime.RuntimeUpgradeCheckResult
import com.github.magisk317.smscode.runtime.RuntimeUpgradeDownloadProgress
import kotlinx.coroutines.flow.Flow
import java.io.File

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
}

/** File-backed entity store (app blocked-config mirror). */
interface UiStoreAccess {
    fun persistAppConfigs(context: Context, appConfigs: List<com.github.magisk317.smscode.data.db.entity.AppInfo>): Boolean
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

/** Preference reads needed by UI screens (debug log flag, SIM remark). */
interface UiPrefsAccess {
    fun isSensitiveDebugLogMode(context: Context): Boolean
    fun getSimSlotRemark(context: Context, simSlot: Int): String
}

/** App update: GitHub check/download/install + Play flow decisions. */
interface UiUpdateAccess {
    suspend fun fetchUpgradeInfo(): RuntimeUpgradeCheckResult
    fun isNewer(current: String, latest: String): Boolean
    fun isNewer(current: Long, latest: Long): Boolean
    fun selectBestApkForDevice(apks: List<RuntimeUpgradeApkAsset>): RuntimeUpgradeApkAsset?
    suspend fun download(
        context: Context,
        versionCode: Long,
        asset: RuntimeUpgradeApkAsset,
        onProgress: (RuntimeUpgradeDownloadProgress) -> Unit,
    ): File
    fun verifyDownloadedApk(
        context: Context,
        apkFile: File,
        expectedSha256: String,
        expectedSigningCertSha256: String,
    ): RuntimeApkVerificationResult
    fun canRequestPackageInstalls(context: Context): Boolean
    fun buildUnknownSourceSettingsIntent(context: Context): Intent
    fun installApk(context: Context, apkFile: File): Result<Unit>
    fun shouldRunAutoCheck(enabled: Boolean, wifiOnly: Boolean, onWifi: Boolean): Boolean
    fun resolveStartupTarget(installedFromPlay: Boolean): RuntimeStartupTarget
    fun shouldSkipGithubCheckOnStartup(
        installedFromPlay: Boolean,
        autoCheckEnabled: Boolean,
        wifiOnly: Boolean,
        onWifi: Boolean,
    ): Boolean
    fun shouldSkipIgnoredVersion(
        respectIgnoredVersion: Boolean,
        ignoredVersion: String,
        latestVersion: String,
    ): Boolean
    fun decidePlayAction(
        updateAvailable: Boolean,
        flexibleAllowed: Boolean,
        inProgress: Boolean,
        silentIfNoUpdate: Boolean,
    ): RuntimePlayAction
    fun decidePlayFailureAction(fallbackOnQueryFailure: Boolean): RuntimePlayAction
}
