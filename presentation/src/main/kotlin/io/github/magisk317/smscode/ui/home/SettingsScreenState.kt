@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("LocalContextGetResourceValueCall")

package io.github.magisk317.smscode.ui.home


import org.koin.compose.koinInject


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
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import io.github.magisk317.smscode.common.utils.HookPreferenceMirror
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.magisk317.smscode.presentation.BuildConfig
import io.github.magisk317.xposed.diagnostics.DiagnosticBundleNames
import io.github.magisk317.smscode.presentation.R
import io.github.magisk317.uikit.R as UiKitR
import io.github.magisk317.smscode.common.constant.PrefConst
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import io.github.magisk317.smscode.common.utils.RuntimeDiagnosticsBridge
import io.github.magisk317.smscode.runtime.common.diagnostics.LogBundleExporter
import io.github.magisk317.smscode.runtime.bridge.UiBackupAccess
import io.github.magisk317.smscode.runtime.bridge.UiNotificationAccess
import org.koin.compose.koinInject
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeDiagnosticsEnvironment
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.xposed.permission.PermissionBridge
import io.github.magisk317.uikit.foundation.LoadingIndicatorTokens
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.xposed.logging.MagiskLogPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import android.content.Context
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.MutableIntState

private val allowsLogExport: Boolean
    get() = MagiskLogPolicy.current().allowsLogExport

internal class SettingsBodyState(
    val context: Context,
    val scope: CoroutineScope,
    val lifecycleOwner: LifecycleOwner,
    val activityOwner: ComponentActivity?,
    val settingsViewModel: SettingsViewModel,
    val backupAccess: UiBackupAccess,
    val notificationAccess: UiNotificationAccess,
    val snackbarHostState: AppSnackbarHostState,
    val markPrefsSaved: () -> Unit,
    val supportsAccessibilityAutoInput: Boolean,
    val entitlementUiAvailable: Boolean,
    val onExit: () -> Unit,
    val allowsLogExport: Boolean,
    val autoInputDelay: androidx.compose.runtime.MutableState<String>,
    val autoInputInterval: androidx.compose.runtime.MutableState<String>,
    val retentionTime: androidx.compose.runtime.MutableState<String>,
    val showCodeNotificationEnabled: androidx.compose.runtime.MutableState<Boolean>,
    val autoCancelNotificationEnabled: androidx.compose.runtime.MutableState<Boolean>,
    val smsCodeKeywords: androidx.compose.runtime.MutableState<String>,
    val simSlot1Remark: androidx.compose.runtime.MutableState<String>,
    val simSlot2Remark: androidx.compose.runtime.MutableState<String>,
    val showAutoInputDialog: androidx.compose.runtime.MutableState<Boolean>,
    val showAutoInputIntervalDialog: androidx.compose.runtime.MutableState<Boolean>,
    val showRetentionDialog: androidx.compose.runtime.MutableState<Boolean>,
    val showSmsTestDialog: androidx.compose.runtime.MutableState<Boolean>,
    val smsTestInput: androidx.compose.runtime.MutableState<String>,
    val showPrivacyPolicyDialog: androidx.compose.runtime.MutableState<Boolean>,
    val showKeywordsDialog: androidx.compose.runtime.MutableState<Boolean>,
    val showSimSlotRemarkDialog: androidx.compose.runtime.MutableState<Int?>,
    val autoInputAccessibilityEnabled: androidx.compose.runtime.MutableState<Boolean>,
    val settingsDataLoaded: androidx.compose.runtime.MutableState<Boolean>,
    val manualRefreshing: androidx.compose.runtime.MutableState<Boolean>,
    val expandGeneral: androidx.compose.runtime.MutableState<Boolean>,
    val expandSmsCode: androidx.compose.runtime.MutableState<Boolean>,
    val expandAutoInput: androidx.compose.runtime.MutableState<Boolean>,
    val expandNotification: androidx.compose.runtime.MutableState<Boolean>,
    val expandExperimental: androidx.compose.runtime.MutableState<Boolean>,
    val expandOthers: androidx.compose.runtime.MutableState<Boolean>,
    val launcherIconVisible: androidx.compose.runtime.MutableState<Boolean>,
    val runtimeLogRetentionDays: MutableIntState,
    val analyticsEnabled: androidx.compose.runtime.MutableState<Boolean>,
    val showRuntimeLogRetentionDialog: androidx.compose.runtime.MutableState<Boolean>,
    val showClearLogConfirmDialog: androidx.compose.runtime.MutableState<Boolean>,
    val showBackupDialog: androidx.compose.runtime.MutableState<Boolean>,
    val showRestoreDialog: androidx.compose.runtime.MutableState<Boolean>,
    val restoreUri: androidx.compose.runtime.MutableState<android.net.Uri?>,
    val backupFlags: androidx.compose.runtime.MutableState<BackupSelectionFlags>,
    val backupLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    val restoreLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    val autoInputEnabled: androidx.compose.runtime.MutableState<Boolean>,
    val moduleEnabled: androidx.compose.runtime.MutableState<Boolean>,
    val accordionMode: androidx.compose.runtime.MutableState<Boolean>,
    val openAccessibilitySettings: () -> Unit,
    val saveRuntimeLogBundle: () -> Unit,
    val clearRuntimeLogFolders: () -> Unit,
    val handleCodeNotificationToggle: (Boolean) -> Unit,
    val reloadSettingsData: suspend () -> Unit,
    val runManualRefresh: suspend () -> Unit,
)


@Composable
internal fun rememberSettingsBodyState(
    viewModel: SettingsViewModel?,
    onExit: () -> Unit,
): SettingsBodyState {
    val context = LocalContext.current
    val entitlementUiAvailable = remember(context) {
        context.packageManager.resolveActivity(
            Intent().setClassName(
                context,
                "io.github.magisk317.smscode.entitlement.MobileEntitlementActivity",
            ),
            PackageManager.MATCH_DEFAULT_ONLY,
        ) != null
    }
    val activityOwner = context as? ComponentActivity
    val settingsViewModel = viewModel ?: if (activityOwner != null) {
        koinViewModel(viewModelStoreOwner = activityOwner)
    } else {
        koinViewModel()
    }
    val backupAccess = koinInject<UiBackupAccess>()
    val notificationAccess = koinInject<UiNotificationAccess>()
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val supportsAccessibilityAutoInput = BuildConfig.ENABLE_ACCESSIBILITY_AUTO_INPUT

    val autoInputDelayState = remember {mutableStateOf(PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT)}
    var autoInputDelay by autoInputDelayState
    val autoInputIntervalState = remember {mutableStateOf(PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL_DEFAULT)}
    var autoInputInterval by autoInputIntervalState
    val retentionTimeState = remember {mutableStateOf(PrefConst.NOTIFICATION_RETENTION_TIME_DEFAULT)}
    var retentionTime by retentionTimeState
    val showCodeNotificationEnabled = remember { mutableStateOf(true) }
    val autoCancelNotificationEnabled = remember { mutableStateOf(PrefConst.AUTO_CANCEL_CODE_NOTIFICATION_DEFAULT) }
    val smsCodeKeywordsState = remember {mutableStateOf(PrefConst.SMSCODE_KEYWORDS_DEFAULT)}
    var smsCodeKeywords by smsCodeKeywordsState
    val simSlot1RemarkState = remember {mutableStateOf("")}
    var simSlot1Remark by simSlot1RemarkState
    val simSlot2RemarkState = remember {mutableStateOf("")}
    var simSlot2Remark by simSlot2RemarkState
    val showAutoInputDialog = remember {mutableStateOf(false)}
    val showAutoInputIntervalDialog = remember {mutableStateOf(false)}
    val showRetentionDialog = remember {mutableStateOf(false)}
    val showSmsTestDialog = remember {mutableStateOf(false)}
    val smsTestInput = remember {mutableStateOf("")}
    val showPrivacyPolicyDialog = remember {mutableStateOf(false)}
    val showKeywordsDialog = remember {mutableStateOf(false)}
    val showSimSlotRemarkDialog = remember {mutableStateOf<Int?>(null)}
    val autoInputAccessibilityEnabledState = remember {mutableStateOf(false)}
    var autoInputAccessibilityEnabled by autoInputAccessibilityEnabledState
    val settingsDataLoadedState = remember {mutableStateOf(false)}
    var settingsDataLoaded by settingsDataLoadedState
    val manualRefreshingState = remember {mutableStateOf(false)}
    var manualRefreshing by manualRefreshingState
    val expandGeneral = remember {mutableStateOf(false)}
    val expandSmsCode = remember {mutableStateOf(false)}
    val expandAutoInput = remember {mutableStateOf(false)}
    val expandNotification = remember {mutableStateOf(false)}
    val expandExperimental = remember {mutableStateOf(false)}
    val expandOthers = remember {mutableStateOf(false)}
    val launcherIconVisible = remember { mutableStateOf(true) }
    val runtimeLogRetentionDaysState = remember {mutableIntStateOf(PrefConst.RUNTIME_LOG_RETENTION_DAYS_DEFAULT)}
    var runtimeLogRetentionDays by runtimeLogRetentionDaysState
    val analyticsEnabled = rememberPrefBoolean(PrefConst.KEY_ENABLE_ANALYTICS, true)
    val showRuntimeLogRetentionDialog = remember {mutableStateOf(false)}
    val showClearLogConfirmDialog = remember {mutableStateOf(false)}
    val autoInputEnabled = rememberPrefBoolean(PrefConst.KEY_ENABLE_AUTO_INPUT_CODE, true)
    val moduleEnabled = rememberPrefBoolean(PrefConst.KEY_ENABLE, true)
    val accordionMode = rememberPrefBoolean(PrefConst.KEY_SETTINGS_ACCORDION_MODE, true)

    val showBackupDialog = remember {mutableStateOf(false)}
    val showRestoreDialogState = remember {mutableStateOf(false)}
    var showRestoreDialog by showRestoreDialogState
    val restoreUriState = remember {mutableStateOf<android.net.Uri?>(null)}
    var restoreUri by restoreUriState
    val backupFlagsState = remember {mutableStateOf(BackupSelectionFlags())}
    var backupFlags by backupFlagsState


    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val pickedUri = data?.data ?: data?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        XLog.i(
            message = "Backup picker result: code=%d uri=%s clipCount=%d",
            result.resultCode,
            pickedUri?.toString() ?: "<null>",
            data?.clipData?.itemCount ?: 0,
        )
        if (result.resultCode == android.app.Activity.RESULT_OK && pickedUri != null) {
            settingsViewModel.performBackup(
                pickedUri,
                backupFlags.includeConfig,
                backupFlags.includeRules,
                backupFlags.includeRecords,
                backupFlags.includeDatabase,
            )
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        val pickedUri = data?.data ?: data?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        XLog.i(
            message = "Restore picker result: code=%d uri=%s clipCount=%d",
            result.resultCode,
            pickedUri?.toString() ?: "<null>",
            data?.clipData?.itemCount ?: 0,
        )
        if (result.resultCode == android.app.Activity.RESULT_OK && pickedUri != null) {
            val dataFlags = data?.flags ?: 0
            val grantFlags = dataFlags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            runCatching {
                if ((dataFlags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0) {
                    val flagsToPersist = if (grantFlags != 0) grantFlags else Intent.FLAG_GRANT_READ_URI_PERMISSION
                    @SuppressLint("WrongConstant")
                    context.contentResolver.takePersistableUriPermission(pickedUri, flagsToPersist)
                } else {
                    XLog.w(
                        message = "Persistable grant not returned by picker: uri=%s flags=0x%x",
                        pickedUri.toString(),
                        dataFlags,
                    )
                }
            }.onFailure {
                XLog.w(
                    message = "takePersistableUriPermission failed: uri=%s err=%s",
                    pickedUri.toString(),
                    it.message ?: it.javaClass.simpleName,
                )
            }
            // Show restore confirm dialog directly to avoid one-shot event loss
            // when Activity lifecycle transitions around document picker return.
            restoreUri = pickedUri
            showRestoreDialog = true
            XLog.i(message = "Restore confirm dialog requested directly: uri=%s", pickedUri.toString())
        } else if (result.resultCode == android.app.Activity.RESULT_OK) {
            XLog.w(message = "Restore picker returned OK but uri is null")
        }
    }
    val snackbarHostState = remember { AppSnackbarHostState() }
    val markPrefsSaved: () -> Unit = {
        scope.launch {
            snackbarHostState.showSnackbar(context.getString(R.string.pref_sync_toast))
        }
    }
    val saveRuntimeLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { destination ->
        if (destination == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                RuntimeDiagnosticsBridge.ensureInstalled()
                val bundle = LogBundleExporter.buildLogBundle(context = context)
                val file = bundle.file ?: return@withContext bundle.details
                context.contentResolver.openOutputStream(destination, "wt")?.use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                } ?: return@withContext "log_export_destination_open_failed"
                ""
            }
            if (result.isNotBlank()) {
                snackbarHostState.showSnackbar(context.getString(R.string.runtime_log_export_failed, result))
            } else {
                snackbarHostState.showSnackbar(context.getString(R.string.runtime_log_saved))
            }
        }
    }
    fun saveRuntimeLogBundle() {
        if (LogBundleExporter.checkPreExport() != null) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(io.github.magisk317.uikit.R.string.uikit_log_export_need_root),
                )
            }
            return
        }
        RuntimeDiagnosticsBridge.ensureInstalled()
        // The name offered here is the name the exporter will write, because both come from
        // the product token in the installed configuration.
        saveRuntimeLogLauncher.launch(
            DiagnosticBundleNames.bundleFileName(
                product = RuntimeDiagnosticsEnvironment.current().product,
                timestamp = DiagnosticBundleNames.exportTimestamp(),
            ),
        )
    }
    val clearRuntimeLogFolders: () -> Unit = {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                RuntimeDiagnosticsBridge.ensureInstalled()
                LogBundleExporter.clearLogFolders(context)
            }
            snackbarHostState.showSnackbar(
                if (result.success) {
                    context.getString(R.string.runtime_log_cleared)
                } else {
                    context.getString(R.string.runtime_log_clear_partial_failed, result.details)
                },
            )
        }
    }
    val accessibilitySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        autoInputAccessibilityEnabled =
            supportsAccessibilityAutoInput && isAutoInputAccessibilityServiceEnabled(context)
    }
    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        val componentName = ComponentName(context, "io.github.magisk317.smscode.service.AutoInputAccessibilityService").flattenToString()
        intent.putExtra(":settings:fragment_args_key", componentName)
        intent.putExtra(":settings:show_fragment_args", Bundle())
        if (activityOwner != null) {
            runCatching {
                accessibilitySettingsLauncher.launch(intent)
            }.onFailure {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(UiKitR.string.pref_auto_input_accessibility_service_open_failed),
                    )
                }
            }
            return
        }
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(UiKitR.string.pref_auto_input_accessibility_service_open_failed),
                )
            }
        }
    }
    // Enables the code-notification preference once the system permission is available.
    val enableCodeNotificationPref: () -> Unit = {
        showCodeNotificationEnabled.value = true
        scope.launch {
            AppPreferencesDataStore.setBoolean(
                context,
                PrefConst.KEY_SHOW_CODE_NOTIFICATION,
                true,
            )
            HookPreferenceMirror.publish(context)
            markPrefsSaved()
        }
    }
    val notificationSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // Returning from system notification settings: re-check the real delivery state.
        // Only flip the preference on when notifications can actually be posted now.
        if (NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            notificationAccess.hasPostNotificationsPermission(context)
        ) {
            enableCodeNotificationPref()
        }
    }
    // Opens the system notification settings page for this app as a fallback when the
    // runtime permission dialog can no longer be shown.
    val openAppNotificationSettings: () -> Unit = {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        runCatching { notificationSettingsLauncher.launch(intent) }.onFailure {
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(UiKitR.string.pref_code_notification_owner_permission_settings_hint),
                )
            }
        }
    }
    val requestNotificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            enableCodeNotificationPref()
            return@rememberLauncherForActivityResult
        }
        // Denied. If the system will no longer show the runtime dialog
        // (permanently denied or master switch off), fall back to the settings page.
        val activity = activityOwner ?: (context as? Activity)
        val canAskAgain = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        if (!canAskAgain) {
            openAppNotificationSettings()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(UiKitR.string.pref_code_notification_owner_permission_settings_hint),
                )
            }
        }
    }
    val reloadSettingsData: suspend () -> Unit = {
        autoInputDelay = AppPreferencesDataStore.getString(
            context,
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY,
            "",
        ).ifEmpty {
            val legacySeconds = AppPreferencesDataStore.getString(
                context,
                PrefConst.KEY_AUTO_INPUT_CODE_DELAY_LEGACY,
                PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT,
            ).toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            (legacySeconds * 1000L).toString()
        }
        autoInputInterval = AppPreferencesDataStore.getString(
            context,
            PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL,
            PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL_DEFAULT,
        )
        retentionTime = AppPreferencesDataStore.getString(
            context,
            PrefConst.KEY_NOTIFICATION_RETENTION_TIME,
            PrefConst.NOTIFICATION_RETENTION_TIME_DEFAULT,
        )
        runtimeLogRetentionDays = AppPreferencesDataStore.getInt(
            context,
            PrefConst.KEY_RUNTIME_LOG_RETENTION_DAYS,
            PrefConst.RUNTIME_LOG_RETENTION_DAYS_DEFAULT,
        ).coerceAtLeast(PrefConst.RUNTIME_LOG_RETENTION_DAYS_MIN)
        val storedShowCodeNotification = AppPreferencesDataStore.getBoolean(
            context,
            PrefConst.KEY_SHOW_CODE_NOTIFICATION,
            true,
        )
        // The switch only shows on when the system can actually deliver notifications.
        // If the user revoked permission (or the master switch is off) after enabling it,
        // reflect off and persist false so the runtime does not attempt to post.
        val canDeliverNotification = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            notificationAccess.hasPostNotificationsPermission(context)
        if (storedShowCodeNotification && !canDeliverNotification) {
            AppPreferencesDataStore.setBoolean(
                context,
                PrefConst.KEY_SHOW_CODE_NOTIFICATION,
                false,
            )
            HookPreferenceMirror.publish(context)
            showCodeNotificationEnabled.value = false
        } else {
            showCodeNotificationEnabled.value = storedShowCodeNotification
        }
        autoCancelNotificationEnabled.value = AppPreferencesDataStore.getBoolean(
            context,
            PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION,
            PrefConst.AUTO_CANCEL_CODE_NOTIFICATION_DEFAULT,
        )
        smsCodeKeywords = AppPreferencesDataStore.getString(
            context,
            PrefConst.KEY_SMSCODE_KEYWORDS,
            PrefConst.SMSCODE_KEYWORDS_DEFAULT,
        )
        simSlot1Remark = AppPreferencesDataStore.getString(
            context,
            PrefConst.KEY_SIM_SLOT1_REMARK,
            "",
        ).trim()
        simSlot2Remark = AppPreferencesDataStore.getString(
            context,
            PrefConst.KEY_SIM_SLOT2_REMARK,
            "",
        ).trim()
        autoInputAccessibilityEnabled =
            supportsAccessibilityAutoInput && isAutoInputAccessibilityServiceEnabled(context)
        val launcherVisible = settingsViewModel.isLauncherIconVisible()
        launcherIconVisible.value = launcherVisible
        val storedLauncherVisible = AppPreferencesDataStore.getBoolean(
            context,
            PrefConst.KEY_SHOW_LAUNCHER_ICON,
            true,
        )
        if (storedLauncherVisible != launcherVisible) {
            AppPreferencesDataStore.setBoolean(
                context,
                PrefConst.KEY_SHOW_LAUNCHER_ICON,
                launcherVisible,
            )
            HookPreferenceMirror.publish(context)
        }
        settingsViewModel.setInternalFilesWritable()
        settingsDataLoaded = true
    }

    val runManualRefresh: suspend () -> Unit = {
        val startedAt = SystemClock.elapsedRealtime()
        manualRefreshing = true
        reloadSettingsData()
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        val remaining = (LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS - elapsed).coerceAtLeast(0L)
        if (remaining > 0L) delay(remaining)
        manualRefreshing = false
    }

    val handleCodeNotificationToggle: (Boolean) -> Unit = { enabled ->
        if (!enabled) {
            // Turning off: no system interaction required.
            showCodeNotificationEnabled.value = false
            scope.launch {
                AppPreferencesDataStore.setBoolean(
                    context,
                    PrefConst.KEY_SHOW_CODE_NOTIFICATION,
                    false,
                )
                HookPreferenceMirror.publish(context)
                markPrefsSaved()
            }
        } else {
            // Turning on: only commit the preference once the system
            // will actually deliver notifications. Otherwise request the
            // permission first and let the callback flip it on.
            val notificationsEnabled =
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            val permissionGranted =
                notificationAccess.hasPostNotificationsPermission(context)
            when {
                notificationsEnabled && permissionGranted -> {
                    enableCodeNotificationPref()
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !permissionGranted -> {
                    requestNotificationPermissionLauncher.launch(
                        Manifest.permission.POST_NOTIFICATIONS,
                    )
                }
                else -> {
                    // Permission is granted but the master switch is off
                    // (or pre-Tiramisu with notifications disabled): the
                    // runtime dialog won't help, jump to settings.
                    openAppNotificationSettings()
                }
            }
        }
    }

    return SettingsBodyState(
        context = context,
        scope = scope,
        lifecycleOwner = lifecycleOwner,
        activityOwner = activityOwner,
        settingsViewModel = settingsViewModel,
        backupAccess = backupAccess,
        notificationAccess = notificationAccess,
        snackbarHostState = snackbarHostState,
        markPrefsSaved = markPrefsSaved,
        supportsAccessibilityAutoInput = supportsAccessibilityAutoInput,
        entitlementUiAvailable = entitlementUiAvailable,
        onExit = onExit,
        allowsLogExport = allowsLogExport,
        autoInputDelay = autoInputDelayState,
        autoInputInterval = autoInputIntervalState,
        retentionTime = retentionTimeState,
        showCodeNotificationEnabled = showCodeNotificationEnabled,
        autoCancelNotificationEnabled = autoCancelNotificationEnabled,
        smsCodeKeywords = smsCodeKeywordsState,
        simSlot1Remark = simSlot1RemarkState,
        simSlot2Remark = simSlot2RemarkState,
        showAutoInputDialog = showAutoInputDialog,
        showAutoInputIntervalDialog = showAutoInputIntervalDialog,
        showRetentionDialog = showRetentionDialog,
        showSmsTestDialog = showSmsTestDialog,
        smsTestInput = smsTestInput,
        showPrivacyPolicyDialog = showPrivacyPolicyDialog,
        showKeywordsDialog = showKeywordsDialog,
        showSimSlotRemarkDialog = showSimSlotRemarkDialog,
        autoInputAccessibilityEnabled = autoInputAccessibilityEnabledState,
        settingsDataLoaded = settingsDataLoadedState,
        manualRefreshing = manualRefreshingState,
        expandGeneral = expandGeneral,
        expandSmsCode = expandSmsCode,
        expandAutoInput = expandAutoInput,
        expandNotification = expandNotification,
        expandExperimental = expandExperimental,
        expandOthers = expandOthers,
        launcherIconVisible = launcherIconVisible,
        runtimeLogRetentionDays = runtimeLogRetentionDaysState,
        analyticsEnabled = analyticsEnabled,
        showRuntimeLogRetentionDialog = showRuntimeLogRetentionDialog,
        showClearLogConfirmDialog = showClearLogConfirmDialog,
        showBackupDialog = showBackupDialog,
        showRestoreDialog = showRestoreDialogState,
        restoreUri = restoreUriState,
        backupFlags = backupFlagsState,
        backupLauncher = backupLauncher,
        restoreLauncher = restoreLauncher,
        autoInputEnabled = autoInputEnabled,
        moduleEnabled = moduleEnabled,
        accordionMode = accordionMode,
        openAccessibilitySettings = ::openAccessibilitySettings,
        saveRuntimeLogBundle = ::saveRuntimeLogBundle,
        clearRuntimeLogFolders = clearRuntimeLogFolders,
        handleCodeNotificationToggle = handleCodeNotificationToggle,
        reloadSettingsData = reloadSettingsData,
        runManualRefresh = runManualRefresh,
    )
}

internal suspend fun toggleAccessibilityServiceViaRoot(context: android.content.Context, enable: Boolean): Boolean {
    return withContext(Dispatchers.IO) {
        val component = ComponentName(
            context,
            "io.github.magisk317.smscode.service.AutoInputAccessibilityService",
        ).flattenToString()
        val currentServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
        // An empty list means the service is already in the requested state, so no su round trip.
        val commands = PermissionBridge.accessibilityCommands(currentServices, component, enable)
        commands.isEmpty() || PermissionBridge.runRoot(commands) { message ->
            XLog.w(message = "Root accessibility toggle: %s", message)
        }
    }
}


