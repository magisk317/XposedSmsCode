package com.tianma.xsmscode.feature.backup

enum class ImportResult {
    /**
     * Success
     */
    SUCCESS,

    /**
     * Backup version missed
     */
    VERSION_MISSED,

    /**
     * Backup version unknown
     */
    VERSION_UNKNOWN,

    /**
     * Backup invalid
     */
    BACKUP_INVALID,

    /**
     * Read error
     */
    READ_FAILED
}
