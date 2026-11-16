package io.github.magisk317.smscode.feature.backup

import android.content.Context
import android.net.Uri
import io.github.magisk317.smscode.runtime.RuntimeBackupExportResult
import io.github.magisk317.smscode.runtime.RuntimeBackupFacade
import io.github.magisk317.smscode.runtime.RuntimeBackupImportStatus
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import io.github.magisk317.xposed.backup.BackupPayloadSource
import io.github.magisk317.xposed.logging.XLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Produces the full SmsCode backup archive (rules, records, preferences and the
 * app database) for the cloud transport and applies a downloaded archive back.
 *
 * The archive is written through the same [RuntimeBackupFacade] pipeline the SAF
 * export uses; the produced file name carries [ARCHIVE_PREFIX] plus a timestamp
 * because the transport uploads it verbatim and filters remote listings by that
 * prefix. Restoring replays the SAF restore path: payload import with version
 * guards, database replacement, then the typed preference restore.
 */
class SmsCodeCloudBackupPayloadSource(
    private val restoreHooks: CloudBackupRestoreHooks = CloudBackupRestoreHooks(),
) : BackupPayloadSource {

    override val archivePrefix: String = ARCHIVE_PREFIX

    override suspend fun produceBackup(context: Context): File = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val tempFile = File(context.cacheDir, "$ARCHIVE_PREFIX$timestamp.zip")
        XLog.i(message = "Cloud backup export start: file=%s", tempFile.name)
        val result = RuntimeBackupFacade.exportBackup(
            context = context,
            uri = Uri.fromFile(tempFile),
            ruleList = RuntimeBackupFacade.queryBackupRules(context),
            preferences = AppPreferencesDataStore.snapshotForBackup(context),
            records = RuntimeBackupFacade.queryBackupSmsRecords(context),
            appVersion = currentAppVersion(context),
            includeDatabase = true,
        )
        if (result != RuntimeBackupExportResult.SUCCESS) {
            tempFile.delete()
            throw IllegalStateException("Local backup export failed: $result")
        }
        if (!tempFile.isFile || tempFile.length() <= 0L) {
            tempFile.delete()
            throw IllegalStateException("Local backup file missing or empty")
        }
        tempFile
    }

    override suspend fun applyRestore(context: Context, backup: File) = withContext(Dispatchers.IO) {
        XLog.i(message = "Cloud backup restore start: file=%s", backup.name)
        val uri = Uri.fromFile(backup)
        val importResult = RuntimeBackupFacade.importRuleList(context, uri, currentAppVersion(context))
        if (importResult.result != RuntimeBackupImportStatus.SUCCESS) {
            throw IllegalStateException("Restore import failed: ${importResult.result.name}")
        }
        val restored = RuntimeBackupFacade.restoreDatabaseFromBackup(context, uri)
        if (!restored) {
            throw IllegalStateException("Restore database failed: backup zip has no database files")
        }
        XLog.i(
            message = "Cloud backup restore database enabled: skip logical restore rules=%d records=%d",
            importResult.rules.size,
            importResult.records?.size ?: 0,
        )
        CloudBackupRestorePreferences.restore(context, importResult.preferences.orEmpty(), restoreHooks)
        XLog.i(message = "Cloud backup restore apply finished")
    }

    private fun currentAppVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "0.0.0"

    companion object {
        /** Marks cloud archives on the WebDAV server and scopes remote listings. */
        const val ARCHIVE_PREFIX = "SmsCode-Cloud-"
    }
}
