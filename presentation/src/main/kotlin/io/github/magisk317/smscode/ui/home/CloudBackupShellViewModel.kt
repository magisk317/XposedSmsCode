package io.github.magisk317.smscode.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.smscode.presentation.R
import io.github.magisk317.smscode.runtime.bridge.UiCloudBackupAccess
import io.github.magisk317.smscode.runtime.bridge.UiWebDavConfig
import io.github.magisk317.uikit.backup.BackupEntry
import io.github.magisk317.uikit.backup.BackupProviderKind
import io.github.magisk317.uikit.backup.BackupUiActions
import io.github.magisk317.uikit.backup.BackupUiGateway
import io.github.magisk317.uikit.backup.BackupUiState
import io.github.magisk317.uikit.backup.BackupUiTexts
import io.github.magisk317.uikit.backup.BackupWebDavConfig
import io.github.magisk317.uikit.backup.CloudBackupUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Thin shell over the shared [CloudBackupUiModel]: supplies the app's strings,
 * a [UiCloudBackupAccess]-backed gateway and the ViewModel scope. All cloud
 * backup state lives in the shared model. WebDAV remains the only source and no
 * automatic-backup trigger is wired in this app, so the switch stays hidden.
 */
class CloudBackupShellViewModel(
    application: Application,
    cloudBackup: UiCloudBackupAccess,
) : AndroidViewModel(application) {

    private val model = CloudBackupUiModel(
        scope = viewModelScope,
        gateway = UiCloudBackupGateway(application, cloudBackup),
        texts = BackupUiTexts(
            backupSuccess = application.getString(R.string.backup_success),
            backupFailed = application.getString(R.string.backup_failed),
            restoreSuccess = application.getString(R.string.restore_success),
            restoreFailed = application.getString(R.string.restore_failed),
            deleteFailed = application.getString(R.string.cloud_backup_delete_failed),
            listFailed = application.getString(R.string.cloud_backup_list_failed),
            webDavRequired = application.getString(R.string.cloud_backup_webdav_required),
            webDavSaved = application.getString(R.string.cloud_backup_webdav_saved),
            webDavSaveFailed = application.getString(R.string.cloud_backup_webdav_save_failed),
            webDavRemoved = application.getString(R.string.cloud_backup_webdav_removed),
            webDavTestSuccess = application.getString(R.string.cloud_backup_webdav_connection_success),
            webDavTestFailed = application.getString(R.string.cloud_backup_webdav_connection_failed),
        ),
    )

    val uiState: StateFlow<BackupUiState> get() = model.uiState

    val actions: BackupUiActions get() = model.actions

    fun restoreBackup(backupId: String) = model.restoreBackup(backupId)
}

/**
 * Maps [UiCloudBackupAccess] onto the kit gateway contract. Every suspending
 * member pins background dispatching so the runtime's Keystore/SharedPreferences
 * work never runs on the main thread.
 */
private class UiCloudBackupGateway(
    private val application: Application,
    private val access: UiCloudBackupAccess,
) : BackupUiGateway {

    override suspend fun currentWebDavConfig(): BackupWebDavConfig? = withContext(Dispatchers.IO) {
        access.webDavConfig(application)?.toUiKit()
    }

    override suspend fun persistWebDavConfig(draft: BackupWebDavConfig): BackupWebDavConfig? =
        withContext(Dispatchers.IO) {
            access.saveWebDavConfig(application, draft.toUi())?.toUiKit()
        }

    override suspend fun removeWebDavConfig() {
        withContext(Dispatchers.IO) { access.removeWebDavConfig(application) }
    }

    override suspend fun testWebDavConnection(draft: BackupWebDavConfig): Boolean =
        access.testWebDavConnection(application, draft.toUi())

    override suspend fun uploadBackup(): Boolean = access.uploadBackup(application)

    override suspend fun listBackups(): List<BackupEntry>? =
        access.listBackups(application)?.map { entry ->
            BackupEntry(
                id = entry.id,
                name = entry.name,
                sizeBytes = entry.sizeBytes,
                provider = BackupProviderKind.WebDav,
            )
        }

    override suspend fun restoreBackup(backupId: String): Boolean =
        access.restoreBackup(application, backupId)

    override suspend fun deleteBackup(backupId: String): Boolean =
        access.deleteBackup(application, backupId)

    override fun isAutoBackupSupported(): Boolean = false

    override suspend fun isAutoBackupEnabled(): Boolean = access.isAutoBackupEnabled(application)

    override suspend fun setAutoBackupEnabled(enabled: Boolean) =
        access.setAutoBackupEnabled(application, enabled)

    private fun UiWebDavConfig.toUiKit(): BackupWebDavConfig = BackupWebDavConfig(
        serverUrl = serverUrl,
        username = username,
        password = password,
        remotePath = remotePath,
    )

    private fun BackupWebDavConfig.toUi(): UiWebDavConfig = UiWebDavConfig(
        serverUrl = serverUrl,
        username = username,
        password = password,
        remotePath = remotePath,
    )
}
