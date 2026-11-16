package io.github.magisk317.smscode.runtime.bridge

import android.content.Context

/** WebDAV endpoint configuration as edited in the cloud backup settings UI. */
data class UiWebDavConfig(
    val serverUrl: String,
    val username: String,
    val password: String,
    val remotePath: String,
)

/** One cloud backup entry shown in the settings list. */
data class UiCloudBackupEntry(
    val id: String,
    val name: String,
    val sizeBytes: Long?,
)

/**
 * Cloud backup gateways for the settings UI: WebDAV configuration persistence,
 * connection checks and backup operations. The runtime resolves the configured
 * provider per call, so the UI never holds a provider instance or kit types.
 */
interface UiCloudBackupAccess {
    /** Currently persisted WebDAV configuration, or null when none is stored. */
    fun webDavConfig(context: Context): UiWebDavConfig?

    /** Normalizes and persists a draft; returns the stored config, or null when invalid or unpersistable. */
    fun saveWebDavConfig(context: Context, draft: UiWebDavConfig): UiWebDavConfig?

    /** Deletes the persisted WebDAV configuration. */
    fun removeWebDavConfig(context: Context)

    /** True when the endpoint behind [draft] answers the connection check. */
    suspend fun testWebDavConnection(context: Context, draft: UiWebDavConfig): Boolean

    /** Uploads a fresh backup; false when the provider is not configured or the upload fails. */
    suspend fun uploadBackup(context: Context): Boolean

    /** Lists remote backups; null when the provider is not configured or the listing fails. */
    suspend fun listBackups(context: Context): List<UiCloudBackupEntry>?

    /** Downloads and applies [backupId]; false when the provider is not configured or the restore fails. */
    suspend fun restoreBackup(context: Context, backupId: String): Boolean

    /** Deletes [backupId]; false when the provider is not configured or the deletion fails. */
    suspend fun deleteBackup(context: Context, backupId: String): Boolean

    /** Whether automatic WebDAV backups are enabled in the cloud backup settings store. */
    fun isAutoBackupEnabled(context: Context): Boolean

    /** Persists the automatic-backup switch for the WebDAV source. */
    suspend fun setAutoBackupEnabled(context: Context, enabled: Boolean)
}
