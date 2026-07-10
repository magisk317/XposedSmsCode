package com.github.magisk317.smscode.runtime

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.github.magisk317.smscode.feature.backup.BackupManager
import com.github.magisk317.smscode.runtime.bridge.UiBackupAccess

object RuntimeBackupFacade : UiBackupAccess {
    override fun getExportRuleListSAFIntent(
        context: Context,
        includeDatabase: Boolean,
    ): Intent {
        return BackupManager.getExportRuleListSAFIntent(context, includeDatabase)
    }

    override fun getImportRuleListSAFIntent(context: Context): Intent {
        return BackupManager.getImportRuleListSAFIntent(context)
    }

    override fun exportBackup(
        context: Context,
        uri: Uri,
        ruleList: List<RuntimeBackupRule>,
        preferences: Map<String, String?>?,
        records: List<RuntimeBackupSmsRecord>?,
        appVersion: String,
        includeDatabase: Boolean,
    ): RuntimeBackupExportResult {
        return BackupManager.exportBackup(
            context = context,
            uri = uri,
            ruleList = ruleList.map(RuntimeBackupRule::toInternal),
            preferences = preferences,
            records = records?.map(RuntimeBackupSmsRecord::toInternal),
            appVersion = appVersion,
            includeDatabase = includeDatabase,
        ).toRuntime()
    }

    override fun importRuleList(
        context: Context,
        uri: Uri,
        currentAppVersion: String,
    ): RuntimeBackupImportResult {
        return BackupManager.importRuleList(context, uri, currentAppVersion).toRuntime()
    }

    override fun restoreDatabaseFromBackup(context: Context, uri: Uri): Boolean {
        return BackupManager.restoreDatabaseFromBackup(context, uri)
    }
}
