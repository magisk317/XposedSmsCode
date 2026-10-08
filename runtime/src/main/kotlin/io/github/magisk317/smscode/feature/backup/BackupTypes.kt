package io.github.magisk317.smscode.feature.backup

typealias BackupImportResult = io.github.magisk317.smscode.runtime.contract.backup.BackupImportResult
typealias BackupInvalidException = io.github.magisk317.smscode.runtime.common.backup.BackupInvalidException
typealias BackupParseResult = io.github.magisk317.smscode.runtime.contract.backup.BackupParseResult
typealias BackupPayload = io.github.magisk317.smscode.runtime.contract.backup.BackupPayload
typealias BackupRule = io.github.magisk317.smscode.runtime.contract.backup.BackupRule
typealias BackupSmsRecord = io.github.magisk317.smscode.runtime.contract.backup.BackupSmsRecord
typealias ExportResult = io.github.magisk317.smscode.runtime.contract.backup.ExportResult
typealias ImportResult = io.github.magisk317.smscode.runtime.contract.backup.ImportResult
typealias ImportWarning = io.github.magisk317.smscode.runtime.contract.backup.ImportWarning
typealias RuleExporter = io.github.magisk317.smscode.runtime.common.backup.RuleExporter
typealias RuleImporter = io.github.magisk317.smscode.runtime.common.backup.RuleImporter
typealias VersionInvalidException = io.github.magisk317.smscode.runtime.common.backup.VersionInvalidException
typealias VersionMissedException = io.github.magisk317.smscode.runtime.common.backup.VersionMissedException

object BackupConst {
    const val BACKUP_VERSION = io.github.magisk317.smscode.runtime.contract.backup.BackupConst.BACKUP_VERSION

    const val KEY_VERSION = io.github.magisk317.smscode.runtime.contract.backup.BackupConst.KEY_VERSION
    const val KEY_SCHEMA_VERSION = io.github.magisk317.smscode.runtime.contract.backup.BackupConst.KEY_SCHEMA_VERSION
    const val KEY_APP_VERSION = io.github.magisk317.smscode.runtime.contract.backup.BackupConst.KEY_APP_VERSION
    const val KEY_TIMESTAMP = io.github.magisk317.smscode.runtime.contract.backup.BackupConst.KEY_TIMESTAMP

    const val KEY_RULES = io.github.magisk317.smscode.runtime.contract.backup.BackupConst.KEY_RULES
    const val KEY_PREFERENCES = io.github.magisk317.smscode.runtime.contract.backup.BackupConst.KEY_PREFERENCES
    const val KEY_RECORDS = io.github.magisk317.smscode.runtime.contract.backup.BackupConst.KEY_RECORDS

    const val KEY_COMPANY = io.github.magisk317.smscode.runtime.contract.backup.BackupConst.KEY_COMPANY
    const val KEY_CODE_KEYWORD = io.github.magisk317.smscode.runtime.contract.backup.BackupConst.KEY_CODE_KEYWORD
    const val KEY_CODE_REGEX = io.github.magisk317.smscode.runtime.contract.backup.BackupConst.KEY_CODE_REGEX
}
