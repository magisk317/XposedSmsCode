@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("LocalContextGetResourceValueCall")

package io.github.magisk317.smscode.ui.home




import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import io.github.magisk317.smscode.common.utils.HookPreferenceMirror
import androidx.compose.ui.res.stringResource
import io.github.magisk317.smscode.presentation.R
import io.github.magisk317.uikit.R as UiKitR
import io.github.magisk317.smscode.common.constant.PrefConst
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import io.github.magisk317.smscode.common.utils.RuntimeDiagnosticsBridge
import io.github.magisk317.smscode.runtime.RuntimeBackupImportStatus
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogStore
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.preference.NonNegativeIntegerInputDialog
import io.github.magisk317.uikit.surface.ConfirmActionDialog
import io.github.magisk317.uikit.preference.TextInputDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun SettingsDialogsHost(state: SettingsBodyState) {
    val context = state.context
    val scope = state.scope
    val settingsViewModel = state.settingsViewModel
    val markPrefsSaved = state.markPrefsSaved
    val backupLauncher = state.backupLauncher
    val onExit = state.onExit
    var autoInputDelay by state.autoInputDelay
    var autoInputInterval by state.autoInputInterval
    var retentionTime by state.retentionTime
    var smsTestInput by state.smsTestInput
    var smsCodeKeywords by state.smsCodeKeywords
    var showAutoInputDialog by state.showAutoInputDialog
    var showAutoInputIntervalDialog by state.showAutoInputIntervalDialog
    var showRetentionDialog by state.showRetentionDialog
    var showSmsTestDialog by state.showSmsTestDialog
    var showKeywordsDialog by state.showKeywordsDialog
    var showPrivacyPolicyDialog by state.showPrivacyPolicyDialog
    var showBackupDialog by state.showBackupDialog
    var showRestoreDialog by state.showRestoreDialog
    var restoreUri by state.restoreUri
    var backupFlags by state.backupFlags
    var showSimSlotRemarkDialog by state.showSimSlotRemarkDialog
    var simSlot1Remark by state.simSlot1Remark
    var simSlot2Remark by state.simSlot2Remark
    var showClearLogConfirmDialog by state.showClearLogConfirmDialog
    var showRuntimeLogRetentionDialog by state.showRuntimeLogRetentionDialog
    var runtimeLogRetentionDays by state.runtimeLogRetentionDays
    val clearRuntimeLogFolders = state.clearRuntimeLogFolders
    SettingsDialogs(
        context = context,
        scope = scope,
        autoInputDelay = autoInputDelay,
        autoInputInterval = autoInputInterval,
        retentionTime = retentionTime,
        smsTestInput = smsTestInput,
        smsCodeKeywords = smsCodeKeywords,
        showAutoInputDialog = showAutoInputDialog,
        showAutoInputIntervalDialog = showAutoInputIntervalDialog,
        showRetentionDialog = showRetentionDialog,
        showSmsTestDialog = showSmsTestDialog,
        showKeywordsDialog = showKeywordsDialog,
        showPrivacyPolicyDialog = showPrivacyPolicyDialog,
        showBackupDialog = showBackupDialog,
        showRestoreDialog = showRestoreDialog,
        restoreUri = restoreUri,
        onAutoInputDelayChange = { autoInputDelay = it },
        onAutoInputIntervalChange = { autoInputInterval = it },
        onRetentionTimeChange = { retentionTime = it },
        onSmsTestInputChange = { smsTestInput = it },
        onSmsKeywordsChange = { smsCodeKeywords = it },
        onShowAutoInputDialogChange = { showAutoInputDialog = it },
        onShowAutoInputIntervalDialogChange = { showAutoInputIntervalDialog = it },
        onShowRetentionDialogChange = { showRetentionDialog = it },
        onShowSmsTestDialogChange = { showSmsTestDialog = it },
        onShowKeywordsDialogChange = { showKeywordsDialog = it },
        onShowPrivacyPolicyDialogChange = { showPrivacyPolicyDialog = it },
        onShowBackupDialogChange = { showBackupDialog = it },
        onShowRestoreDialogChange = { showRestoreDialog = it },
        onBackupFlagsChange = { backupFlags = it },
        onPendingSavedToast = markPrefsSaved,
        backupLauncher = backupLauncher,
        settingsViewModel = settingsViewModel,
        onExit = onExit,
    )

    showSimSlotRemarkDialog?.let { simSlot ->
        val isFirstSlot = simSlot == 0
        val key = if (isFirstSlot) PrefConst.KEY_SIM_SLOT1_REMARK else PrefConst.KEY_SIM_SLOT2_REMARK
        TextInputDialog(
            title = stringResource(
                id = if (isFirstSlot) {
                    UiKitR.string.pref_sim_slot1_remark_title
                } else {
                    UiKitR.string.pref_sim_slot2_remark_title
                },
            ),
            initialValue = if (isFirstSlot) simSlot1Remark else simSlot2Remark,
            selectAllOnOpen = true,
            onDismiss = { showSimSlotRemarkDialog = null },
            supportingText = stringResource(id = R.string.pref_sim_slot_remark_summary),
            showClearButton = true,
        ) { value ->
            val updated = value.trim()
            if (isFirstSlot) {
                simSlot1Remark = updated
            } else {
                simSlot2Remark = updated
            }
            scope.launch {
                AppPreferencesDataStore.setString(context, key, updated)
                HookPreferenceMirror.publish(context)
                markPrefsSaved()
            }
            showSimSlotRemarkDialog = null
        }
    }
    if (showClearLogConfirmDialog) {
        ConfirmActionDialog(
            title = stringResource(id = R.string.runtime_log_clear_confirm_title),
            message = stringResource(id = R.string.runtime_log_clear_confirm_message),
            confirmText = stringResource(id = R.string.action_clear),
            cancelText = stringResource(id = R.string.cancel),
            onDismissRequest = { showClearLogConfirmDialog = false },
            onConfirm = {
                showClearLogConfirmDialog = false
                clearRuntimeLogFolders()
            },
        )
    }

    if (showRuntimeLogRetentionDialog) {
        val runtimeLogRetentionDaysError = stringResource(id = UiKitR.string.pref_runtime_log_retention_days_error)
        NonNegativeIntegerInputDialog(
            title = stringResource(id = UiKitR.string.pref_runtime_log_retention_days_title),
            initialValue = runtimeLogRetentionDays,
            errorText = runtimeLogRetentionDaysError,
            onDismiss = { showRuntimeLogRetentionDialog = false },
            minimumValue = PrefConst.RUNTIME_LOG_RETENTION_DAYS_MIN,
            supportingText = stringResource(id = UiKitR.string.pref_runtime_log_retention_days_hint),
            showClearButton = true,
        ) { days ->
            showRuntimeLogRetentionDialog = false
            scope.launch {
                runtimeLogRetentionDays = days
                AppPreferencesDataStore.setInt(context, PrefConst.KEY_RUNTIME_LOG_RETENTION_DAYS, days)
                RuntimeDiagnosticsBridge.ensureInstalled()
                RuntimeLogStore.setRetentionDays(days)
                HookPreferenceMirror.publish(context)
                markPrefsSaved()
            }
        }
    }
}

internal fun handleSettingsEvent(
    event: SettingsEvent,
    context: android.content.Context,
    activity: Activity?,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: AppSnackbarHostState,
    onShowPrivacyPolicy: () -> Unit,
    onShowRestoreConfirm: (android.net.Uri) -> Unit,
) {
    when (event) {
        is SettingsEvent.ShowPrivacyPolicy -> onShowPrivacyPolicy()
        is SettingsEvent.BackupResultEvent -> {
            val msg = if (event.success) R.string.backup_success else R.string.backup_failed
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(msg))
            }
        }

        is SettingsEvent.RestoreResultEvent -> {
            val msg = when (event.result.result) {
                RuntimeBackupImportStatus.SUCCESS -> R.string.restore_success
                RuntimeBackupImportStatus.VERSION_TOO_NEW -> R.string.import_failed_version_too_new
                RuntimeBackupImportStatus.VERSION_TOO_OLD -> R.string.import_failed_version_too_old
                else -> R.string.restore_failed
            }
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(msg))
            }

            if (event.result.result == RuntimeBackupImportStatus.SUCCESS) {
                scope.launch {
                    delay(1200L)
                    if (activity != null) {
                        val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            activity.startActivity(intent)
                        }
                        activity.finish()
                    }
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
            }
        }

        is SettingsEvent.ImportDialogConfirm -> onShowRestoreConfirm(event.uri)
        else -> Unit
    }
}

