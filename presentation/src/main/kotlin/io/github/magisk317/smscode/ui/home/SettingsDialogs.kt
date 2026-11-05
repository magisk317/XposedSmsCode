@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("LocalContextGetResourceValueCall")

package io.github.magisk317.smscode.ui.home


import org.koin.compose.koinInject
import androidx.compose.foundation.layout.PaddingValues
import android.annotation.SuppressLint
import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import io.github.magisk317.smscode.common.utils.HookPreferenceMirror
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.magisk317.smscode.presentation.BuildConfig
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.xposed.diagnostics.DiagnosticBundleNames
import io.github.magisk317.smscode.presentation.R
import io.github.magisk317.uikit.R as UiKitR
import io.github.magisk317.smscode.common.constant.Const
import io.github.magisk317.smscode.common.constant.PrefConst
import io.github.magisk317.xposed.runtime.state.RuntimeStateStore
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import io.github.magisk317.smscode.common.utils.RuntimeDiagnosticsBridge
import io.github.magisk317.smscode.runtime.common.diagnostics.LogBundleExporter
import io.github.magisk317.smscode.runtime.RuntimeBackupImportStatus
import io.github.magisk317.smscode.runtime.bridge.UiBackupAccess
import io.github.magisk317.smscode.runtime.bridge.UiNotificationAccess
import org.koin.compose.koinInject
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeDiagnosticsEnvironment
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogStore
import io.github.magisk317.smscode.common.utils.AppPreferences
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.xposed.permission.PermissionBridge
import io.github.magisk317.uikit.foundation.LoadingIndicatorTokens
import io.github.magisk317.uikit.surface.AppPullToRefresh
import io.github.magisk317.uikit.foundation.LocalSnackbarHostState
import io.github.magisk317.uikit.common.AppSnackbarHost
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.foundation.PolygonMorphLoadingIndicator
import io.github.magisk317.uikit.foundation.SessionLoadingRegistry
import io.github.magisk317.uikit.foundation.rememberMinDurationLoading
import io.github.magisk317.uikit.preference.AppArrowItem
import io.github.magisk317.uikit.preference.GeneralSettingsSection
import io.github.magisk317.uikit.preference.NonNegativeIntegerInputDialog
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsCallbacks
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsItem
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsItems
import io.github.magisk317.xposed.logging.MagiskLogPolicy
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsLabels
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsLayout
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsState
import io.github.magisk317.uikit.preference.RuntimeLogShareEntryMode
import io.github.magisk317.uikit.surface.ConfirmActionDialog
import io.github.magisk317.uikit.surface.AppSlider
import io.github.magisk317.uikit.surface.SectionColumn
import io.github.magisk317.uikit.preference.SingleChoiceOptionDialog
import io.github.magisk317.uikit.preference.SingleChoicePositionDialog
import io.github.magisk317.uikit.preference.SingleChoiceValueDialog
import io.github.magisk317.uikit.preference.StatusSettingsSection
import io.github.magisk317.uikit.preference.TextInputDialog
import io.github.magisk317.smscode.runtime.common.utils.BrowserUtils
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel


@Composable
internal fun SettingsDialogs(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    autoInputDelay: String,
    autoInputInterval: String,
    retentionTime: String,
    smsTestInput: String,
    smsCodeKeywords: String,
    showAutoInputDialog: Boolean,
    showAutoInputIntervalDialog: Boolean,
    showRetentionDialog: Boolean,
    showSmsTestDialog: Boolean,
    showKeywordsDialog: Boolean,
    showPrivacyPolicyDialog: Boolean,
    showBackupDialog: Boolean,
    showRestoreDialog: Boolean,
    restoreUri: android.net.Uri?,
    onAutoInputDelayChange: (String) -> Unit,
    onAutoInputIntervalChange: (String) -> Unit,
    onRetentionTimeChange: (String) -> Unit,
    onSmsTestInputChange: (String) -> Unit,
    onSmsKeywordsChange: (String) -> Unit,
    onShowAutoInputDialogChange: (Boolean) -> Unit,
    onShowAutoInputIntervalDialogChange: (Boolean) -> Unit,
    onShowRetentionDialogChange: (Boolean) -> Unit,
    onShowSmsTestDialogChange: (Boolean) -> Unit,
    onShowKeywordsDialogChange: (Boolean) -> Unit,
    onShowPrivacyPolicyDialogChange: (Boolean) -> Unit,
    onShowBackupDialogChange: (Boolean) -> Unit,
    onShowRestoreDialogChange: (Boolean) -> Unit,
    onBackupFlagsChange: (BackupSelectionFlags) -> Unit,
    onPendingSavedToast: () -> Unit,
    backupLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    settingsViewModel: SettingsViewModel,
    onExit: () -> Unit,
) {
    val activityOwner = context as? Activity
    val backupAccess = koinInject<UiBackupAccess>()
    val snackbarHostState = LocalSnackbarHostState.current
    if (showAutoInputDialog) {
        val nonNegativeNumberError = stringResource(id = R.string.pref_number_non_negative_error)
        TextInputDialog(
            title = stringResource(id = UiKitR.string.pref_auto_input_code_delay_title),
            initialValue = normalizeNumericInput(autoInputDelay),
            selectAllOnOpen = true,
            onDismiss = { onShowAutoInputDialogChange(false) },
            showClearButton = true,
            validator = {
                if (parseNonNegativeLong(it) != null) null else nonNegativeNumberError
            },
        ) { value ->
            val normalized = normalizeNumericInput(value)
            onAutoInputDelayChange(normalized)
            scope.launch {
                AppPreferencesDataStore.setString(context, PrefConst.KEY_AUTO_INPUT_CODE_DELAY, normalized)
                HookPreferenceMirror.publish(context)
                onPendingSavedToast()
            }
            onShowAutoInputDialogChange(false)
        }
    }

    if (showAutoInputIntervalDialog) {
        val nonNegativeNumberError = stringResource(id = R.string.pref_number_non_negative_error)
        TextInputDialog(
            title = stringResource(id = UiKitR.string.pref_auto_input_code_interval_title),
            initialValue = normalizeNumericInput(autoInputInterval),
            selectAllOnOpen = true,
            onDismiss = { onShowAutoInputIntervalDialogChange(false) },
            showClearButton = true,
            validator = {
                if (parseNonNegativeLong(it) != null) null else nonNegativeNumberError
            },
        ) { value ->
            val normalized = normalizeNumericInput(value)
            onAutoInputIntervalChange(normalized)
            scope.launch {
                AppPreferencesDataStore.setString(context, PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL, normalized)
                HookPreferenceMirror.publish(context)
                onPendingSavedToast()
            }
            onShowAutoInputIntervalDialogChange(false)
        }
    }

    if (showRetentionDialog) {
        RetentionDialog(
            selectedValue = retentionTime,
            onDismiss = { onShowRetentionDialogChange(false) },
        ) { value ->
            onRetentionTimeChange(value)
            scope.launch {
                AppPreferencesDataStore.setString(context, PrefConst.KEY_NOTIFICATION_RETENTION_TIME, value)
                HookPreferenceMirror.publish(context)
                onPendingSavedToast()
            }
            onShowRetentionDialogChange(false)
        }
    }

    if (showSmsTestDialog) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_smscode_test_title),
            initialValue = smsTestInput,
            onDismiss = { onShowSmsTestDialogChange(false) },
            singleLine = false,
            maxLines = 8,
            showClearButton = true,
        ) { value ->
            settingsViewModel.performSmsCodeTest(value)
            onSmsTestInputChange("")
            onShowSmsTestDialogChange(false)
        }
    }

    if (showKeywordsDialog) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_smscode_keywords_title),
            initialValue = smsCodeKeywords,
            selectAllOnOpen = true,
            onDismiss = { onShowKeywordsDialogChange(false) },
            singleLine = false,
            maxLines = 10,
            resetValue = PrefConst.SMSCODE_KEYWORDS_DEFAULT,
            showClearButton = true,
        ) { value ->
            val updated = if (value.isBlank()) PrefConst.SMSCODE_KEYWORDS_DEFAULT else value
            onSmsKeywordsChange(updated)
            scope.launch {
                AppPreferencesDataStore.setString(context, PrefConst.KEY_SMSCODE_KEYWORDS, updated)
                HookPreferenceMirror.publish(context)
                onPendingSavedToast()
            }
            onShowKeywordsDialogChange(false)
        }
    }

    if (showPrivacyPolicyDialog) {
        PrivacyPolicyDialog(
            onDismiss = { onShowPrivacyPolicyDialogChange(false) },
            onConfirm = {
                scope.launch { AppPreferences.setPrivacyPolicyAccepted(context, true) }
                onShowPrivacyPolicyDialogChange(false)
            },
            onCancel = {
                scope.launch { AppPreferences.setPrivacyPolicyAccepted(context, false) }
                onShowPrivacyPolicyDialogChange(false)
                onExit()
            },
            onViewPolicy = {
                BrowserUtils.openWebPage(
                    context,
                    Const.PRIVACY_POLICY_URL,
                    R.string.browser_install_or_enable_prompt,
                )
            },
        )
    }

    if (showBackupDialog) {
        BackupDialog(
            onDismiss = { onShowBackupDialogChange(false) },
            onConfirm = { flags ->
                onBackupFlagsChange(flags)
                onShowBackupDialogChange(false)
                val intent = backupAccess.getExportRuleListSAFIntent(
                    context,
                    includeDatabase = flags.includeDatabase,
                )
                backupLauncher.launch(intent)
            },
        )
    }

    if (showRestoreDialog && restoreUri != null) {
        RestoreConfirmDialog(
            onDismiss = { onShowRestoreDialogChange(false) },
            onConfirm = { flags ->
                settingsViewModel.performRestore(
                    uri = restoreUri,
                    restoreConfig = flags.includeConfig,
                    restoreRules = flags.includeRules,
                    restoreRecords = flags.includeRecords,
                    restoreDatabase = flags.includeDatabase,
                )
                onShowRestoreDialogChange(false)
            },
        )
    }
}

@Composable
fun RetentionDialog(
    selectedValue: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    titleId: Int = UiKitR.string.pref_notification_retention_time_title,
    entriesId: Int = R.array.notification_retention_time_entry_list,
    valuesId: Int = R.array.notification_retention_time_list,
    onConfirm: (String) -> Unit,
) {
    val entries = stringArrayResource(id = entriesId)
    val values = stringArrayResource(id = valuesId)
    SingleChoiceValueDialog(
        title = stringResource(id = titleId),
        options = entries.toList(),
        values = values.toList(),
        selectedValue = selectedValue,
        onValueChange = onConfirm,
        onDismissRequest = onDismiss,
        modifier = modifier,
    )
}

@Composable
fun PrivacyPolicyDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onViewPolicy: () -> Unit,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
) {
    io.github.magisk317.uikit.surface.AppAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside,
        ),
        title = { AppText(stringResource(id = R.string.privacy_dialog_title)) },
        text = {
            Column {
                AppText(stringResource(id = R.string.privacy_dialog_content))
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    io.github.magisk317.uikit.surface.AppSecondaryButton(
                        text = stringResource(id = R.string.privacy_policy_button),
                        onClick = onViewPolicy,
                    )
                }
            }
        },
        confirmButton = {
            io.github.magisk317.uikit.surface.AppPrimaryButton(
                text = stringResource(id = R.string.privacy_dialog_confirm),
                onClick = onConfirm,
            )
        },
        dismissButton = {
            io.github.magisk317.uikit.surface.AppSecondaryButton(
                text = stringResource(id = R.string.privacy_dialog_cancel),
                onClick = onCancel,
            )
        },
    )
}

internal data class BackupSelectionFlags(
    val includeConfig: Boolean = true,
    val includeRules: Boolean = true,
    val includeRecords: Boolean = true,
    val includeDatabase: Boolean = false,
)

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun BackupDialog(onDismiss: () -> Unit, onConfirm: (BackupSelectionFlags) -> Unit) {
    var checkConfig by remember { mutableStateOf(true) }
    var checkRules by remember { mutableStateOf(true) }
    var checkRecords by remember { mutableStateOf(true) }
    var checkDatabase by remember { mutableStateOf(false) }
    val cancelLabel = stringResource(id = R.string.cancel)
    val confirmLabel = stringResource(id = R.string.confirm)

    io.github.magisk317.uikit.surface.AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(stringResource(id = R.string.dialog_backup_title)) },
        text = {
            Column {
                AppText(stringResource(id = R.string.dialog_backup_msg), modifier = Modifier.padding(bottom = 8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { checkConfig = !checkConfig },
                ) {
                    io.github.magisk317.uikit.preference.AppCheckbox(
                        checked = checkConfig,
                        onCheckedChange = { checkConfig = it },
                    )
                    AppText(stringResource(id = R.string.item_config))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { checkRules = !checkRules },
                ) {
                    io.github.magisk317.uikit.preference.AppCheckbox(
                        checked = checkRules,
                        onCheckedChange = { checkRules = it },
                    )
                    AppText(stringResource(id = R.string.item_rules))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { checkRecords = !checkRecords },
                ) {
                    io.github.magisk317.uikit.preference.AppCheckbox(
                        checked = checkRecords,
                        onCheckedChange = { checkRecords = it },
                    )
                    AppText(stringResource(id = R.string.item_records))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { checkDatabase = !checkDatabase },
                ) {
                    io.github.magisk317.uikit.preference.AppCheckbox(
                        checked = checkDatabase,
                        onCheckedChange = { checkDatabase = it },
                    )
                    AppText(stringResource(id = R.string.item_database_with_note))
                }
            }
        },
        confirmButton = {
            ButtonGroup(
                overflowIndicator = { menuState ->
                    ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
                },
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                clickableItem(
                    onClick = onDismiss,
                    label = cancelLabel,
                    weight = 1f,
                )
                clickableItem(
                    onClick = {
                        onConfirm(
                            BackupSelectionFlags(
                                includeConfig = checkConfig,
                                includeRules = checkRules,
                                includeRecords = checkRecords,
                                includeDatabase = checkDatabase,
                            ),
                        )
                    },
                    label = confirmLabel,
                    weight = 1f,
                )
            }
        },
        dismissButton = {},
    )
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun RestoreConfirmDialog(onDismiss: () -> Unit, onConfirm: (BackupSelectionFlags) -> Unit) {
    var checkConfig by remember { mutableStateOf(true) }
    var checkRules by remember { mutableStateOf(true) }
    var checkRecords by remember { mutableStateOf(true) }
    var checkDatabase by remember { mutableStateOf(false) }
    val cancelLabel = stringResource(id = R.string.cancel)
    val confirmLabel = stringResource(id = R.string.confirm)

    io.github.magisk317.uikit.surface.AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(stringResource(id = R.string.dialog_restore_title)) },
        text = {
            Column {
                AppText(stringResource(id = R.string.dialog_restore_msg), modifier = Modifier.padding(bottom = 8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { checkConfig = !checkConfig },
                ) {
                    io.github.magisk317.uikit.preference.AppCheckbox(
                        checked = checkConfig,
                        onCheckedChange = { checkConfig = it },
                    )
                    AppText(stringResource(id = R.string.item_config))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { checkRules = !checkRules },
                ) {
                    io.github.magisk317.uikit.preference.AppCheckbox(
                        checked = checkRules,
                        onCheckedChange = { checkRules = it },
                    )
                    AppText(stringResource(id = R.string.item_rules))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { checkRecords = !checkRecords },
                ) {
                    io.github.magisk317.uikit.preference.AppCheckbox(
                        checked = checkRecords,
                        onCheckedChange = { checkRecords = it },
                    )
                    AppText(stringResource(id = R.string.item_records))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { checkDatabase = !checkDatabase },
                ) {
                    io.github.magisk317.uikit.preference.AppCheckbox(
                        checked = checkDatabase,
                        onCheckedChange = { checkDatabase = it },
                    )
                    AppText(stringResource(id = R.string.item_database_with_note))
                }
                AppText(
                    text = stringResource(id = R.string.restore_warning_msg),
                    role = AppTextRole.BodySmall,
                    color = appColor(AppColorRole.Error),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            ButtonGroup(
                overflowIndicator = { menuState ->
                    ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
                },
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                clickableItem(
                    onClick = onDismiss,
                    label = cancelLabel,
                    weight = 1f,
                )
                clickableItem(
                    onClick = {
                        onConfirm(
                            BackupSelectionFlags(
                                includeConfig = checkConfig,
                                includeRules = checkRules,
                                includeRecords = checkRecords,
                                includeDatabase = checkDatabase,
                            ),
                        )
                    },
                    label = confirmLabel,
                    weight = 1f,
                )
            }
        },
        dismissButton = {},
    )
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun SliderDialog(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onDismiss: () -> Unit,
    onValueChange: (Float) -> Unit,
    valueFormatter: (Float) -> String = { "%.2f".format(it) }
) {
    // rememberSliderState(value, steps, valueRange): the three-argument overload is
    // the only non-deprecated one (the one taking onValueChangeFinished is not).
    val sliderState = rememberSliderState(value, steps, valueRange)
    val cancelLabel = stringResource(id = R.string.cancel)
    val confirmLabel = stringResource(id = R.string.confirm)
    io.github.magisk317.uikit.surface.AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(text = title) },
        text = {
            Column {
                AppText(
                    text = valueFormatter(sliderState.value),
                    role = AppTextRole.Body,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                AppSlider(
                    value = sliderState.value,
                    onValueChange = { sliderState.value = it },
                    valueRange = valueRange,
                    steps = steps,
                )
            }
        },
        confirmButton = {
            ButtonGroup(
                overflowIndicator = { menuState ->
                    ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
                },
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                clickableItem(
                    onClick = onDismiss,
                    label = cancelLabel,
                    weight = 1f,
                )
                clickableItem(
                    onClick = { onValueChange(sliderState.value) },
                    label = confirmLabel,
                    weight = 1f,
                )
            }
        },
        dismissButton = {},
    )
}

/**
 * Navigation into the dedicated theme-details page, provided by the host that
 * owns the NavHost (MainScreen). Settings lives inside the tab graph, so the
 * callback is composed there instead of being threaded through four signature
 * layers.
 */
