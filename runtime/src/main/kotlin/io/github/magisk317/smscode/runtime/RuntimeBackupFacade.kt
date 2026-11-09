package io.github.magisk317.smscode.runtime

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.magisk317.smscode.feature.backup.BackupManager
import io.github.magisk317.smscode.runtime.bridge.UiBackupAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    override fun queryBackupRules(context: Context): List<RuntimeBackupRule> {
        return RuntimeStorageFacade.dbManager(context).queryAllSmsCodeRules().map { it.toBackupRule() }
    }

    override fun queryBackupSmsRecords(context: Context): List<RuntimeBackupSmsRecord> {
        return RuntimeStorageFacade.dbManager(context).queryAllSmsMsg().map { it.toBackupRecord() }
    }

    override suspend fun restoreRulesFromBackup(context: Context, rules: List<RuntimeBackupRule>) {
        if (rules.isEmpty()) return
        withContext(Dispatchers.IO) {
            RuntimeStorageFacade.dbManager(context).addSmsCodeRules(rules.map { it.toRuleEntity() })
        }
    }

    override suspend fun restoreRecordsFromBackup(
        context: Context,
        records: List<RuntimeBackupSmsRecord>,
    ): RuntimeRecordRestoreStats = withContext(Dispatchers.IO) {
        val dbManager = RuntimeStorageFacade.dbManager(context)
        val beforeCount = dbManager.queryAllSmsMsg().size
        if (records.isNotEmpty()) {
            dbManager.addSmsMsgList(records.map { it.toSmsMsgEntity() })
        }
        val afterCount = dbManager.queryAllSmsMsg().size
        RuntimeRecordRestoreStats(beforeCount = beforeCount, afterCount = afterCount)
    }
}
