package io.github.magisk317.smscode.feature.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.magisk317.smscode.data.db.AppDatabase
import io.github.magisk317.smscode.data.db.DBManager
import io.github.magisk317.smscode.runtime.BuildConfig
import io.github.magisk317.smscode.runtime.common.backup.BackupDatabaseHooks
import io.github.magisk317.smscode.runtime.common.backup.BackupManagerConfig
import io.github.magisk317.smscode.runtime.common.backup.BackupManagerCore
import java.io.File

object BackupManager {

    private val config = BackupManagerConfig(
        backupDirectoryName = "SmsCode",
        backupFileNamePrefix = "SmsCode-",
        databaseFileName = "xsmscode_room.db",
        fileProviderAuthority = "${BuildConfig.APPLICATION_ID}.files",
    )

    private val databaseHooks = BackupDatabaseHooks(
        beforeDatabaseSnapshot = { context ->
            runCatching {
                AppDatabase.getInstance(context).openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL)")
            }
        },
        beforeDatabaseRestore = {
            AppDatabase.closeInstance()
            DBManager.resetInstance()
        },
    )

    @JvmStatic
    fun getBackupDir(context: Context): File = BackupManagerCore.getBackupDir(context, config)

    @JvmStatic
    fun getBackupFileExtension(): String = BackupManagerCore.getBackupFileExtension()

    @JvmStatic
    fun getDefaultBackupFilename(context: Context, includeDatabase: Boolean = false): String =
        BackupManagerCore.getDefaultBackupFilename(context, config, includeDatabase)

    @JvmStatic
    fun getBackupFiles(context: Context): Array<File>? = BackupManagerCore.getBackupFiles(context, config)

    @JvmStatic
    fun exportRuleList(file: File, ruleList: List<BackupRule>, appVersion: String): ExportResult =
        BackupManagerCore.exportRuleList(file, ruleList, appVersion)

    @JvmStatic
    fun exportBackup(
        context: Context,
        uri: Uri,
        ruleList: List<BackupRule>,
        preferences: Map<String, String?>?,
        records: List<BackupSmsRecord>?,
        appVersion: String,
        includeDatabase: Boolean = false,
    ): ExportResult {
        return BackupManagerCore.exportBackup(
            context = context,
            uri = uri,
            ruleList = ruleList,
            preferences = preferences,
            records = records,
            appVersion = appVersion,
            config = config,
            hooks = databaseHooks,
            includeDatabase = includeDatabase,
        )
    }

    @JvmStatic
    fun exportRuleList(context: Context, uri: Uri, ruleList: List<BackupRule>, appVersion: String): ExportResult =
        BackupManagerCore.exportRuleList(context, uri, ruleList, appVersion, config)

    @JvmStatic
    fun getExportRuleListSAFIntent(context: Context, includeDatabase: Boolean = false): Intent =
        BackupManagerCore.getExportRuleListSAFIntent(context, config, includeDatabase)

    @JvmStatic
    fun importRuleList(context: Context, uri: Uri, currentAppVersion: String): BackupImportResult =
        BackupManagerCore.importRuleList(context, uri, currentAppVersion)

    @JvmStatic
    fun getImportRuleListSAFIntent(context: Context): Intent =
        BackupManagerCore.getImportRuleListSAFIntent(context, config)

    @JvmStatic
    fun restoreDatabaseFromBackup(context: Context, uri: Uri): Boolean =
        BackupManagerCore.restoreDatabaseFromBackup(context, uri, config, databaseHooks)

    @JvmStatic
    fun shareBackupFile(context: Context, file: File) {
        BackupManagerCore.shareBackupFile(context, file, config)
    }
}
