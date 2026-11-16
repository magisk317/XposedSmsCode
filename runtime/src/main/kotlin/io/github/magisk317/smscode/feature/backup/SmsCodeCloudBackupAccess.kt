package io.github.magisk317.smscode.feature.backup

import android.content.Context
import io.github.magisk317.smscode.runtime.bridge.UiCloudBackupAccess
import io.github.magisk317.smscode.runtime.bridge.UiCloudBackupEntry
import io.github.magisk317.smscode.runtime.bridge.UiWebDavConfig
import io.github.magisk317.xposed.backup.webdav.WebDavCloudBackupAccess
import io.github.magisk317.xposed.backup.webdav.WebDavConfig

/**
 * Runtime implementation of [UiCloudBackupAccess]: delegates to the kit's
 * [WebDavCloudBackupAccess] so the provider-resolve loop lives in exactly one
 * place, and maps between the runtime's bridge types and the kit types.
 */
class SmsCodeCloudBackupAccess(
    private val delegate: WebDavCloudBackupAccess,
) : UiCloudBackupAccess {

    override fun webDavConfig(context: Context): UiWebDavConfig? =
        delegate.webDavConfig(context)?.toUi()

    override fun saveWebDavConfig(context: Context, draft: UiWebDavConfig): UiWebDavConfig? =
        delegate.saveWebDavConfig(context, draft.toKit())?.toUi()

    override fun removeWebDavConfig(context: Context) {
        delegate.removeWebDavConfig(context)
    }

    override suspend fun testWebDavConnection(context: Context, draft: UiWebDavConfig): Boolean =
        delegate.testWebDavConnection(context, draft.toKit())

    override suspend fun uploadBackup(context: Context): Boolean =
        delegate.uploadBackup(context)

    override suspend fun listBackups(context: Context): List<UiCloudBackupEntry>? =
        delegate.listBackups(context)?.map { meta ->
            UiCloudBackupEntry(
                id = meta.id,
                name = meta.name,
                sizeBytes = meta.size.takeIf { it > 0L },
            )
        }

    override suspend fun restoreBackup(context: Context, backupId: String): Boolean =
        delegate.restoreBackup(context, backupId)

    override suspend fun deleteBackup(context: Context, backupId: String): Boolean =
        delegate.deleteBackup(context, backupId)

    override fun isAutoBackupEnabled(context: Context): Boolean =
        delegate.isAutoBackupEnabled(context)

    override suspend fun setAutoBackupEnabled(context: Context, enabled: Boolean) =
        delegate.setAutoBackupEnabled(context, enabled)

    private fun UiWebDavConfig.toKit(): WebDavConfig = WebDavConfig(
        serverUrl = serverUrl,
        username = username,
        password = password,
        remotePath = remotePath,
    )

    private fun WebDavConfig.toUi(): UiWebDavConfig = UiWebDavConfig(
        serverUrl = serverUrl,
        username = username,
        password = password,
        remotePath = remotePath,
    )

    companion object {
        /** Default remote folder for SmsCode cloud backups on the WebDAV server. */
        const val DEFAULT_CLOUD_REMOTE_PATH = "/xposed-sms-code/backups/"
    }
}
