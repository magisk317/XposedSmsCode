package com.github.magisk317.smscode.runtime

import io.github.magisk317.smscode.runtime.common.backup.BackupImportResult
import io.github.magisk317.smscode.runtime.common.backup.BackupRule
import io.github.magisk317.smscode.runtime.common.backup.BackupSmsRecord
import io.github.magisk317.smscode.runtime.common.backup.ExportResult
import io.github.magisk317.smscode.runtime.common.backup.ImportResult
import io.github.magisk317.smscode.runtime.common.backup.ImportWarning

data class RuntimeBackupRule(
    val company: String? = null,
    val codeKeyword: String = "",
    val codeRegex: String = "",
)

data class RuntimeBackupSmsRecord(
    val sender: String? = null,
    val body: String? = null,
    val date: Long = 0L,
    val processedTime: Long = 0L,
    val company: String? = null,
    val smsCode: String? = null,
    val packageName: String? = null,
    val simSlot: Int = -1,
    val subId: Int = 0,
    val msgType: Int = 0,
    val callType: Int = 0,
    val forwardStatus: Int = 0,
    val forwardTarget: String? = null,
    val forwardMessage: String? = null,
    val forwardTime: Long = 0L,
)

enum class RuntimeBackupExportResult {
    SUCCESS,
    FAILED,
}

enum class RuntimeBackupImportStatus {
    SUCCESS,
    VERSION_MISSED,
    VERSION_UNKNOWN,
    VERSION_TOO_NEW,
    VERSION_TOO_OLD,
    BACKUP_INVALID,
    READ_FAILED,
}

enum class RuntimeBackupImportWarning {
    APP_VERSION_MISMATCH,
}

data class RuntimeBackupImportResult(
    val result: RuntimeBackupImportStatus,
    val rules: List<RuntimeBackupRule> = emptyList(),
    val preferences: Map<String, String?>? = null,
    val records: List<RuntimeBackupSmsRecord>? = null,
    val warning: RuntimeBackupImportWarning? = null,
)

internal fun RuntimeBackupRule.toInternal(): BackupRule {
    return BackupRule(
        company = company,
        codeKeyword = codeKeyword,
        codeRegex = codeRegex,
    )
}

internal fun BackupRule.toRuntime(): RuntimeBackupRule {
    return RuntimeBackupRule(
        company = company,
        codeKeyword = codeKeyword,
        codeRegex = codeRegex,
    )
}

internal fun RuntimeBackupSmsRecord.toInternal(): BackupSmsRecord {
    return BackupSmsRecord(
        sender = sender,
        body = body,
        date = date,
        processedTime = processedTime,
        company = company,
        smsCode = smsCode,
        packageName = packageName,
        simSlot = simSlot,
        subId = subId,
        msgType = msgType,
        callType = callType,
        forwardStatus = forwardStatus,
        forwardTarget = forwardTarget,
        forwardMessage = forwardMessage,
        forwardTime = forwardTime,
    )
}

internal fun BackupSmsRecord.toRuntime(): RuntimeBackupSmsRecord {
    return RuntimeBackupSmsRecord(
        sender = sender,
        body = body,
        date = date,
        processedTime = processedTime,
        company = company,
        smsCode = smsCode,
        packageName = packageName,
        simSlot = simSlot,
        subId = subId,
        msgType = msgType,
        callType = callType,
        forwardStatus = forwardStatus,
        forwardTarget = forwardTarget,
        forwardMessage = forwardMessage,
        forwardTime = forwardTime,
    )
}

internal fun ExportResult.toRuntime(): RuntimeBackupExportResult {
    return when (this) {
        ExportResult.SUCCESS -> RuntimeBackupExportResult.SUCCESS
        ExportResult.FAILED -> RuntimeBackupExportResult.FAILED
    }
}

internal fun ImportResult.toRuntime(): RuntimeBackupImportStatus {
    return when (this) {
        ImportResult.SUCCESS -> RuntimeBackupImportStatus.SUCCESS
        ImportResult.VERSION_MISSED -> RuntimeBackupImportStatus.VERSION_MISSED
        ImportResult.VERSION_UNKNOWN -> RuntimeBackupImportStatus.VERSION_UNKNOWN
        ImportResult.VERSION_TOO_NEW -> RuntimeBackupImportStatus.VERSION_TOO_NEW
        ImportResult.VERSION_TOO_OLD -> RuntimeBackupImportStatus.VERSION_TOO_OLD
        ImportResult.BACKUP_INVALID -> RuntimeBackupImportStatus.BACKUP_INVALID
        ImportResult.READ_FAILED -> RuntimeBackupImportStatus.READ_FAILED
    }
}

internal fun ImportWarning.toRuntime(): RuntimeBackupImportWarning {
    return when (this) {
        ImportWarning.APP_VERSION_MISMATCH -> RuntimeBackupImportWarning.APP_VERSION_MISMATCH
    }
}

internal fun BackupImportResult.toRuntime(): RuntimeBackupImportResult {
    return RuntimeBackupImportResult(
        result = result.toRuntime(),
        rules = rules.map(BackupRule::toRuntime),
        preferences = preferences,
        records = records?.map(BackupSmsRecord::toRuntime),
        warning = warning?.toRuntime(),
    )
}
