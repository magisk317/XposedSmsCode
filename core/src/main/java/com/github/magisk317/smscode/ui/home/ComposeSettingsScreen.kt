@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("LocalContextGetResourceValueCall")

package com.github.magisk317.smscode.ui.home

import android.annotation.SuppressLint
import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
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
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import com.github.magisk317.smscode.common.utils.HookPreferenceMirror
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
import com.github.magisk317.smscode.common.utils.ModuleUtils
import com.github.magisk317.smscode.core.BuildConfig
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.xposed.diagnostics.DiagnosticExportMode
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.common.constant.Const
import com.github.magisk317.smscode.common.constant.PrefConst
import io.github.magisk317.smscode.runtime.common.diagnostics.ActivationDiagnosticsStore
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import com.github.magisk317.smscode.common.utils.PackageUtils
import com.github.magisk317.smscode.common.utils.RuntimeDiagnosticsBridge
import io.github.magisk317.smscode.runtime.common.diagnostics.LogBundleExporter
import io.github.magisk317.smscode.runtime.common.diagnostics.VerboseLogEnableTracker
import com.github.magisk317.smscode.runtime.RuntimeBackupImportStatus
import com.github.magisk317.smscode.runtime.bridge.UiBackupAccess
import com.github.magisk317.smscode.runtime.bridge.UiNotificationAccess
import org.koin.compose.koinInject
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogStore
import com.github.magisk317.smscode.common.utils.AppPreferences
import com.github.magisk317.smscode.common.utils.XLog
import io.github.magisk317.xposed.permission.PermissionBridge
import io.github.magisk317.uikit.foundation.LoadingIndicatorTokens
import io.github.magisk317.uikit.foundation.LocalSnackbarHostState
import io.github.magisk317.uikit.common.DismissibleSnackbarHost
import io.github.magisk317.uikit.foundation.PolygonMorphLoadingIndicator
import io.github.magisk317.uikit.foundation.SessionLoadingRegistry
import io.github.magisk317.uikit.foundation.rememberMinDurationLoading
import io.github.magisk317.uikit.preference.GeneralSettingsSection
import io.github.magisk317.uikit.preference.NonNegativeIntegerInputDialog
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsCallbacks
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsItem
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsItems
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsLabels
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsLayout
import io.github.magisk317.uikit.preference.RuntimeLogDiagnosticsState
import io.github.magisk317.uikit.preference.RuntimeLogShareEntryMode
import io.github.magisk317.uikit.surface.ConfirmActionDialog
import io.github.magisk317.uikit.surface.SectionColumn
import io.github.magisk317.uikit.preference.SingleChoiceOptionDialog
import io.github.magisk317.uikit.preference.SingleChoicePositionDialog
import io.github.magisk317.uikit.preference.SingleChoiceValueDialog
import io.github.magisk317.uikit.preference.StatusSettingsSection
import io.github.magisk317.uikit.preference.TextInputDialog
import com.github.magisk317.smscode.ui.privacy.PrivacyPolicyPage
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel

private data class SettingsPageRuntime(
    val isActive: Boolean = true,
    val keepDataActive: Boolean = isActive,
    val onPageDataReady: (cacheHit: Boolean) -> Unit = {},
)

private val LocalSettingsPageRuntime = staticCompositionLocalOf { SettingsPageRuntime() }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Suppress("CyclomaticComplexMethod")
@Composable
fun ComposeSettingsScreen(
    viewModel: SettingsViewModel? = null,
    refreshTrigger: Int = 0,
    isActive: Boolean = true,
    keepDataActive: Boolean = isActive,
    onPageDataReady: (cacheHit: Boolean) -> Unit = {},
    onExit: () -> Unit = {},
) {
    CompositionLocalProvider(
        LocalSettingsPageRuntime provides SettingsPageRuntime(
            isActive = isActive,
            keepDataActive = keepDataActive,
            onPageDataReady = onPageDataReady,
        ),
    ) {
        when (currentUiKitStyle()) {
            UiKitStyle.Miuix -> ComposeSettingsScreenMiuix(
                viewModel = viewModel,
                refreshTrigger = refreshTrigger,
                onExit = onExit,
            )

            UiKitStyle.Expressive -> ComposeSettingsScreenMaterial(
                viewModel = viewModel,
                refreshTrigger = refreshTrigger,
                onExit = onExit,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Suppress("CyclomaticComplexMethod")
@SuppressLint("InlinedApi")
@Composable
internal fun ComposeSettingsScreenBody(
    viewModel: SettingsViewModel? = null,
    refreshTrigger: Int = 0,
    onExit: () -> Unit = {},
    listPadding: PaddingValues,
    scrollModifier: Modifier,
) {
    val pageRuntime = LocalSettingsPageRuntime.current
    val isActive = pageRuntime.isActive
    val keepDataActive = pageRuntime.keepDataActive
    val currentOnPageDataReady by rememberUpdatedState(pageRuntime.onPageDataReady)
    val context = LocalContext.current
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

    val themeState by produceState(
        initialValue = SettingsViewModel.ThemeState(mode = 0),
        settingsViewModel,
        keepDataActive,
    ) {
        if (!keepDataActive) return@produceState
        settingsViewModel.themeState.collect { value = it }
    }
    val themeMode = themeState.mode

    var autoInputDelay by remember { mutableStateOf(PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT) }
    var autoInputInterval by remember { mutableStateOf(PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL_DEFAULT) }
    var retentionTime by remember { mutableStateOf(PrefConst.NOTIFICATION_RETENTION_TIME_DEFAULT) }
    val showCodeNotificationEnabled = remember { mutableStateOf(true) }
    val autoCancelNotificationEnabled = remember { mutableStateOf(false) }
    var smsCodeKeywords by remember { mutableStateOf(PrefConst.SMSCODE_KEYWORDS_DEFAULT) }
    var simSlot1Remark by remember { mutableStateOf("") }
    var simSlot2Remark by remember { mutableStateOf("") }
    var showAutoInputDialog by remember { mutableStateOf(false) }
    var showAutoInputIntervalDialog by remember { mutableStateOf(false) }
    var showRetentionDialog by remember { mutableStateOf(false) }
    var showSmsTestDialog by remember { mutableStateOf(false) }
    var smsTestInput by remember { mutableStateOf("") }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyPage by remember { mutableStateOf(false) }
    var showKeywordsDialog by remember { mutableStateOf(false) }
    var showSimSlotRemarkDialog by remember { mutableStateOf<Int?>(null) }
    var isActivated by remember { mutableStateOf(false) }
    val supportsAccessibilityAutoInput = BuildConfig.ENABLE_ACCESSIBILITY_AUTO_INPUT
    var autoInputAccessibilityEnabled by remember { mutableStateOf(false) }
    var settingsDataLoaded by remember { mutableStateOf(false) }
    var manualRefreshing by remember { mutableStateOf(false) }
    var expandGeneral by remember { mutableStateOf(false) }
    var expandSmsCode by remember { mutableStateOf(false) }
    var expandAutoInput by remember { mutableStateOf(false) }
    var expandNotification by remember { mutableStateOf(false) }
    var expandExperimental by remember { mutableStateOf(false) }
    var expandOthers by remember { mutableStateOf(false) }
    val launcherIconVisible = remember { mutableStateOf(true) }
    var runtimeLogRetentionDays by remember { mutableIntStateOf(PrefConst.RUNTIME_LOG_RETENTION_DAYS_DEFAULT) }
    val verboseLogEnabled = rememberPrefBoolean(PrefConst.KEY_VERBOSE_LOG_MODE, false)
    val analyticsEnabled = rememberPrefBoolean(PrefConst.KEY_ENABLE_ANALYTICS, true)
    var showRuntimeLogRetentionDialog by remember { mutableStateOf(false) }
    var showClearLogConfirmDialog by remember { mutableStateOf(false) }

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
            false,
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

    suspend fun runManualRefresh() {
        val startedAt = SystemClock.elapsedRealtime()
        manualRefreshing = true
        reloadSettingsData()
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        val remaining = (LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS - elapsed).coerceAtLeast(0L)
        if (remaining > 0L) delay(remaining)
        manualRefreshing = false
    }

    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var backupFlags by remember { mutableStateOf(BackupSelectionFlags()) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val pickedUri = data?.data ?: data?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        XLog.i(
            "Backup picker result: code=%d uri=%s clipCount=%d",
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
            "Restore picker result: code=%d uri=%s clipCount=%d",
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
                        "Persistable grant not returned by picker: uri=%s flags=0x%x",
                        pickedUri.toString(),
                        dataFlags,
                    )
                }
            }.onFailure {
                XLog.w(
                    "takePersistableUriPermission failed: uri=%s err=%s",
                    pickedUri.toString(),
                    it.message ?: it.javaClass.simpleName,
                )
            }
            // Show restore confirm dialog directly to avoid one-shot event loss
            // when Activity lifecycle transitions around document picker return.
            restoreUri = pickedUri
            showRestoreDialog = true
            XLog.i("Restore confirm dialog requested directly: uri=%s", pickedUri.toString())
        } else if (result.resultCode == android.app.Activity.RESULT_OK) {
            XLog.w("Restore picker returned OK but uri is null")
        }
    }

    val refreshTriggerConsumer = remember { PageRefreshTriggerConsumer() }
    LaunchedEffect(isActive, refreshTrigger) {
        val action = refreshTriggerConsumer.consume(isActive, refreshTrigger)
            ?: return@LaunchedEffect
        when (action) {
            PageRefreshAction.FORCE_REFRESH -> runManualRefresh()
            PageRefreshAction.INITIAL_LOAD -> {
                if (!settingsDataLoaded) reloadSettingsData()
            }
            PageRefreshAction.NO_OP -> Unit
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
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
                val bundle = LogBundleExporter.buildLogBundle(
                    context = context,
                    mode = DiagnosticExportMode.fromDebugLogging(verboseLogEnabled.value),
                )
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
        val blockReason = LogBundleExporter.checkPreExport(verboseLogEnabled.value)
        if (blockReason != null) {
            val resId = context.resources.getIdentifier(blockReason, "string", context.packageName)
            val message = if (resId != 0) context.getString(resId) else blockReason
            scope.launch { snackbarHostState.showSnackbar(message) }
            return
        }
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.US)
            .format(java.util.Date())
        saveRuntimeLogLauncher.launch("smscode_logs_$timestamp.zip")
    }

    fun clearRuntimeLogFolders() {
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

    LaunchedEffect(lifecycleOwner, keepDataActive) {
        if (!keepDataActive) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            isActivated = ActivationDiagnosticsStore.isModuleActivated(context)
            autoInputAccessibilityEnabled =
                supportsAccessibilityAutoInput && isAutoInputAccessibilityServiceEnabled(context)
            // Reconcile the notification switch with the real system delivery state:
            // if the permission (or the master switch) was revoked outside the app,
            // turn the preference back off.
            if (showCodeNotificationEnabled.value &&
                !(NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                    notificationAccess.hasPostNotificationsPermission(context))
            ) {
                showCodeNotificationEnabled.value = false
                AppPreferencesDataStore.setBoolean(
                    context,
                    PrefConst.KEY_SHOW_CODE_NOTIFICATION,
                    false,
                )
                HookPreferenceMirror.publish(context)
            }
            delay(1000L)
            isActivated = ActivationDiagnosticsStore.isModuleActivated(context)
            autoInputAccessibilityEnabled =
                supportsAccessibilityAutoInput && isAutoInputAccessibilityServiceEnabled(context)
        }
    }
    val accessibilitySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        autoInputAccessibilityEnabled =
            supportsAccessibilityAutoInput && isAutoInputAccessibilityServiceEnabled(context)
    }

    suspend fun toggleAccessibilityServiceViaRoot(context: android.content.Context, enable: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            val component = ComponentName(
                context,
                "com.github.magisk317.smscode.service.AutoInputAccessibilityService",
            ).flattenToString()
            val currentServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )
            // An empty list means the service is already in the requested state, so no su round trip.
            val commands = PermissionBridge.accessibilityCommands(currentServices, component, enable)
            commands.isEmpty() || PermissionBridge.runRoot(commands) { message ->
                XLog.w("Root accessibility toggle: %s", message)
            }
        }
    }

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        val componentName = ComponentName(context, "com.github.magisk317.smscode.service.AutoInputAccessibilityService").flattenToString()
        intent.putExtra(":settings:fragment_args_key", componentName)
        intent.putExtra(":settings:show_fragment_args", Bundle())
        if (activityOwner != null) {
            runCatching {
                accessibilitySettingsLauncher.launch(intent)
            }.onFailure {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.pref_auto_input_accessibility_service_open_failed),
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
                    context.getString(R.string.pref_auto_input_accessibility_service_open_failed),
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
                    context.getString(R.string.pref_code_notification_owner_permission_settings_hint),
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
                    context.getString(R.string.pref_code_notification_owner_permission_settings_hint),
                )
            }
        }
    }

    LaunchedEffect(settingsViewModel, lifecycleOwner, keepDataActive) {
        if (!keepDataActive) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            settingsViewModel.eventsFlow.collect { event ->
                handleSettingsEvent(
                    event = event,
                    context = context,
                    activity = activityOwner ?: (context as? Activity),
                    scope = scope,
                    snackbarHostState = snackbarHostState,
                    onShowPrivacyPolicy = {},
                    onShowRestoreConfirm = { uri ->
                        restoreUri = uri
                        showRestoreDialog = true
                    },
                )
            }
        }
    }

    val scrollState = rememberScrollState()
    val shouldShowInitialLoading = remember { SessionLoadingRegistry.shouldShowInitial("settings") }
    val showLoading = rememberMinDurationLoading(
        actualLoading = isActive && shouldShowInitialLoading && !settingsDataLoaded,
        minDurationMillis = LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS,
    )
    val pullToRefreshState = rememberPullToRefreshState()
    val autoInputEnabled = rememberPrefBoolean(PrefConst.KEY_ENABLE_AUTO_INPUT_CODE, true)
    val autoUpdateEnabled = rememberPrefBoolean(PrefConst.KEY_AUTO_UPDATE_ON_START, true)
    val moduleEnabled = rememberPrefBoolean(PrefConst.KEY_ENABLE, true)
    val accordionMode = rememberPrefBoolean(PrefConst.KEY_SETTINGS_ACCORDION_MODE, true)

    LaunchedEffect(isActive, settingsDataLoaded, showLoading, shouldShowInitialLoading) {
        if (isActive && shouldShowInitialLoading && settingsDataLoaded && !showLoading) {
            SessionLoadingRegistry.markShown("settings")
        }
    }

    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        val cacheHit = settingsDataLoaded
        if (!cacheHit) {
            snapshotFlow { settingsDataLoaded }.first { it }
        }
        currentOnPageDataReady(cacheHit)
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Box(modifier = Modifier.fillMaxSize()) {
        val isCompact = with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.width.toDp() < 600.dp
        }
        val bottomPadding =
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                if (isCompact) Const.BOTTOM_SPACE_HEIGHT.dp else 0.dp

        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = manualRefreshing,
            onRefresh = {
                scope.launch { runManualRefresh() }
            },
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = listPadding.calculateTopPadding() + LoadingIndicatorTokens.OverlayTopSpacing),
                    isRefreshing = manualRefreshing,
                    state = pullToRefreshState,
                )
            },
            modifier = Modifier
                .fillMaxSize()
        ) {
            if (showLoading && !manualRefreshing) {
                Box(modifier = Modifier.fillMaxSize()) {
                    PolygonMorphLoadingIndicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = listPadding.calculateTopPadding() + LoadingIndicatorTokens.OverlayTopSpacing),
                    )
                }
            } else {
                SectionColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .then(scrollModifier),
                    contentPadding = PaddingValues(
                        top = listPadding.calculateTopPadding(),
                        bottom = bottomPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Const.SPACING_SMALL.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Const.PADDING_SMALL.dp),
                    ) {
                        StatusSettingsSection(
                            title = stringResource(id = R.string.mobile_entitlement_settings_title),
                            summary = stringResource(id = R.string.mobile_entitlement_settings_summary),
                        ) {
                            context.startActivity(
                                Intent().setClassName(
                                    context,
                                    "com.github.magisk317.smscode.entitlement.MobileEntitlementActivity",
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Const.PADDING_SMALL.dp),
                    ) {
                        GeneralSettingsSection(
                            title = stringResource(id = R.string.settings_group_general),
                            summary = stringResource(id = R.string.settings_group_general_summary),
                            expanded = expandGeneral,
                            onExpandedChange = { expandGeneral = !expandGeneral },
                            accordionMode = accordionMode.value,
                            moduleEnabled = moduleEnabled.value,
                            onModuleEnabledChange = { checked ->
                                moduleEnabled.value = checked
                                scope.launch {
                                    AppPreferencesDataStore.setBoolean(
                                        context,
                                        PrefConst.KEY_ENABLE,
                                        checked,
                                    )
                                    HookPreferenceMirror.publish(context)
                                    markPrefsSaved()
                                }
                            },
                            moduleTitle = stringResource(id = R.string.pref_enable_title),
                            moduleSummary = stringResource(id = R.string.pref_enable_summary),
                            themeMode = null,
                            onThemeSelected = null,
                        ) {
                            val navigateToThemeSettings = LocalThemeSettingsNavigation.current
                            Item(
                                title = stringResource(id = R.string.pref_theme_details_title),
                                summary = stringResource(id = R.string.pref_theme_details_summary),
                                onClick = {
                                    navigateToThemeSettings?.invoke()
                                },
                            )
                            SwitchItem(
                                title = stringResource(id = R.string.pref_settings_display_mode_title),
                                summary = stringResource(id = R.string.pref_settings_display_mode_summary),
                                key = PrefConst.KEY_SETTINGS_ACCORDION_MODE,
                                defaultValue = true,
                                stateOverride = accordionMode,
                                onSaved = markPrefsSaved,
                            )
                            SwitchItem(
                                title = stringResource(id = R.string.pref_show_launcher_icon_title),
                                summary = stringResource(id = R.string.pref_show_launcher_icon_summary),
                                key = PrefConst.KEY_SHOW_LAUNCHER_ICON,
                                defaultValue = true,
                                stateOverride = launcherIconVisible,
                                onToggle = { visible ->
                                    val success = settingsViewModel.setLauncherIconVisible(visible)
                                    if (!success) {
                                        launcherIconVisible.value = !visible
                                        scope.launch {
                                            AppPreferencesDataStore.setBoolean(
                                                context,
                                                PrefConst.KEY_SHOW_LAUNCHER_ICON,
                                                !visible,
                                            )
                                            HookPreferenceMirror.publish(context)
                                        }
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                context.getString(R.string.pref_show_launcher_icon_failed),
                                            )
                                        }
                                    }
                                },
                                onSaved = markPrefsSaved,
                            )
                        }
                    }

                    ExpandableSettingsSection(
                        title = stringResource(id = R.string.settings_group_smscode),
                        summary = stringResource(id = R.string.settings_group_smscode_summary),
                        expanded = expandSmsCode,
                        onExpandedChange = { expandSmsCode = !expandSmsCode },
                        accordionMode = accordionMode.value,
                    ) {
                        SwitchItem(
                            title = stringResource(id = R.string.pref_copy_to_clipboard_title),
                            summary = stringResource(id = R.string.pref_copy_to_clipboard_summary),
                            key = PrefConst.KEY_COPY_TO_CLIPBOARD,
                            defaultValue = false,
                            onSaved = markPrefsSaved,
                        )
                        Item(
                            title = stringResource(id = R.string.pref_smscode_keywords_title),
                            summary = stringResource(id = R.string.pref_smscode_keywords_summary),
                        ) { showKeywordsDialog = true }
                        Item(
                            title = stringResource(id = R.string.pref_smscode_test_title),
                            summary = stringResource(id = R.string.pref_smscode_test_summary),
                        ) { showSmsTestDialog = true }
                        Item(
                            title = stringResource(id = R.string.pref_sim_slot1_remark_title),
                            summary = simSlotRemarkSummary(simSlot1Remark),
                        ) { showSimSlotRemarkDialog = 0 }
                        Item(
                            title = stringResource(id = R.string.pref_sim_slot2_remark_title),
                            summary = simSlotRemarkSummary(simSlot2Remark),
                        ) { showSimSlotRemarkDialog = 1 }
                        Item(
                            title = stringResource(id = R.string.pref_code_rules_title),
                            summary = stringResource(id = R.string.pref_code_rules_summary),
                        ) {
                            settingsViewModel.openSmsCodeRules()
                        }
                    }

                    ExpandableSettingsSection(
                        title = stringResource(id = R.string.settings_group_auto_input),
                        summary = stringResource(id = R.string.settings_group_auto_input_summary),
                        expanded = expandAutoInput,
                        onExpandedChange = { expandAutoInput = !expandAutoInput },
                        accordionMode = accordionMode.value,
                    ) {
                        if (supportsAccessibilityAutoInput) {
                            io.github.magisk317.uikit.preference.ActionSwitchItem(
                                title = stringResource(id = R.string.pref_auto_input_accessibility_service_title),
                                summary = stringResource(id = R.string.pref_auto_input_accessibility_service_summary),
                                checked = autoInputAccessibilityEnabled,
                                onClick = {
                                    scope.launch {
                                        val success = toggleAccessibilityServiceViaRoot(context, !autoInputAccessibilityEnabled)
                                        if (success) {
                                            autoInputAccessibilityEnabled = !autoInputAccessibilityEnabled
                                        } else {
                                            openAccessibilitySettings()
                                        }
                                    }
                                },
                                onCheckedChange = { isChecked ->
                                    scope.launch {
                                        val success = toggleAccessibilityServiceViaRoot(context, isChecked)
                                        if (success) {
                                            autoInputAccessibilityEnabled = isChecked
                                        } else {
                                            openAccessibilitySettings()
                                        }
                                    }
                                },
                            )
                        }
                        SwitchItem(
                            title = stringResource(id = R.string.pref_enable_auto_input_code_title),
                            summary = stringResource(id = R.string.pref_enable_auto_input_code_summary),
                            key = PrefConst.KEY_ENABLE_AUTO_INPUT_CODE,
                            defaultValue = true,
                            stateOverride = autoInputEnabled,
                            onSaved = markPrefsSaved,
                        )
                        SwitchItem(
                            title = stringResource(id = R.string.pref_enable_auto_enter_code_title),
                            summary = stringResource(id = R.string.pref_enable_auto_enter_code_summary),
                            key = PrefConst.KEY_ENABLE_AUTO_ENTER_CODE,
                            defaultValue = false,
                            onSaved = markPrefsSaved,
                        )
                        Item(
                            title = stringResource(id = R.string.pref_auto_input_code_delay_title),
                            summary = stringResource(id = R.string.pref_auto_input_code_delay_summary, autoInputDelay),
                        ) { showAutoInputDialog = true }
                        Item(
                            title = stringResource(id = R.string.pref_auto_input_code_interval_title),
                            summary = stringResource(
                                id = R.string.pref_auto_input_code_interval_summary,
                                autoInputInterval,
                            ),
                        ) { showAutoInputIntervalDialog = true }
                    }

                    ExpandableSettingsSection(
                        title = stringResource(id = R.string.settings_group_notification),
                        summary = stringResource(id = R.string.settings_group_notification_summary),
                        expanded = expandNotification,
                        onExpandedChange = { expandNotification = !expandNotification },
                        accordionMode = accordionMode.value,
                    ) {
                        SwitchItem(
                            title = stringResource(id = R.string.pref_show_toast_title),
                            summary = stringResource(id = R.string.pref_show_toast_summary),
                            key = PrefConst.KEY_SHOW_TOAST,
                            defaultValue = true,
                            onSaved = markPrefsSaved,
                        )
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
                        io.github.magisk317.uikit.preference.ActionSwitchItem(
                            title = stringResource(id = R.string.pref_show_code_notification_title),
                            summary = stringResource(id = R.string.pref_show_code_notification_summary),
                            checked = showCodeNotificationEnabled.value,
                            onClick = {
                                handleCodeNotificationToggle(!showCodeNotificationEnabled.value)
                            },
                            onCheckedChange = handleCodeNotificationToggle,
                        )
                        if (showCodeNotificationEnabled.value) {
                            SwitchItem(
                                title = stringResource(id = R.string.pref_auto_cancel_notification_title),
                                summary = stringResource(id = R.string.pref_auto_cancel_notification_summary),
                                key = PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION,
                                defaultValue = false,
                                stateOverride = autoCancelNotificationEnabled,
                                onToggle = { autoCancelNotificationEnabled.value = it },
                                onSaved = markPrefsSaved,
                            )
                            if (autoCancelNotificationEnabled.value) {
                                Item(
                                    title = stringResource(id = R.string.pref_notification_retention_time_title),
                                    summary = notificationRetentionEntryLabel(retentionTime),
                                ) { showRetentionDialog = true }
                            }
                        }
                    }

                    ExpandableSettingsSection(
                        title = stringResource(id = R.string.settings_group_experimental),
                        summary = stringResource(id = R.string.settings_group_experimental_summary),
                        expanded = expandExperimental,
                        onExpandedChange = { expandExperimental = !expandExperimental },
                        accordionMode = accordionMode.value,
                    ) {
                        SwitchItem(
                            title = stringResource(id = R.string.pref_block_sms_title),
                            summary = stringResource(id = R.string.pref_block_sms_summary),
                            key = PrefConst.KEY_BLOCK_SMS,
                            defaultValue = false,
                            onSaved = markPrefsSaved,
                        )
                        SwitchItem(
                            title = stringResource(id = R.string.pref_kill_me_title),
                            summary = stringResource(id = R.string.pref_kill_me_summary),
                            key = PrefConst.KEY_KILL_ME,
                            defaultValue = false,
                            onSaved = markPrefsSaved,
                        )
                    }

                    ExpandableSettingsSection(
                        title = stringResource(id = R.string.settings_group_others),
                        summary = stringResource(id = R.string.settings_group_others_summary),
                        expanded = expandOthers,
                        onExpandedChange = { expandOthers = !expandOthers },
                        accordionMode = accordionMode.value,
                    ) {
                        Item(
                            title = stringResource(id = R.string.pref_backup_title),
                            summary = stringResource(id = R.string.pref_backup_summary),
                        ) { showBackupDialog = true }
                        Item(
                            title = stringResource(id = R.string.pref_restore_title),
                            summary = stringResource(id = R.string.pref_restore_summary),
                        ) {
                            val intent = backupAccess.getImportRuleListSAFIntent(context)
                            restoreLauncher.launch(intent)
                        }
                        if (!BuildConfig.DEBUG) {
                            io.github.magisk317.uikit.preference.StateSwitchItem(
                                title = stringResource(id = R.string.pref_enable_analytics_title),
                                summary = stringResource(id = R.string.pref_enable_analytics_summary),
                                checked = analyticsEnabled.value,
                                onCheckedChange = { enabled ->
                                    analyticsEnabled.value = enabled
                                    scope.launch {
                                        AppPreferencesDataStore.setBoolean(
                                            context,
                                            PrefConst.KEY_ENABLE_ANALYTICS,
                                            enabled,
                                        )
                                        HookPreferenceMirror.publish(context)
                                        markPrefsSaved()
                                    }
                                    MagiskOtel.configure(
                                        MagiskOtel.Config(
                                            enabled = BuildConfig.DEBUG || enabled,
                                            serviceName = "xposedsmscode",
                                            serviceVersion = BuildConfig.VERSION_NAME,
                                            projectId = "83955172",
                                            projectName = "XposedSmsCode",
                                            environment = if (BuildConfig.DEBUG) "debug" else "release",
                                        ),
                                    )
                                },
                            )
                        }
                        RuntimeLogDiagnosticsItems(
                            labels = RuntimeLogDiagnosticsLabels(
                                shareLogTitle = stringResource(id = R.string.pref_share_log_title),
                                shareLogSummary = stringResource(id = R.string.pref_share_log_summary),
                                verboseLogTitle = stringResource(id = R.string.pref_verbose_log_mode_title),
                                verboseLogSummary = stringResource(id = R.string.pref_verbose_log_mode_summary),
                                retentionTitle = stringResource(id = R.string.pref_runtime_log_retention_days_title),
                                retentionSummary = pluralStringResource(
                                    id = R.plurals.pref_runtime_log_retention_days_summary,
                                    count = runtimeLogRetentionDays,
                                    runtimeLogRetentionDays,
                                ),
                                clearLogTitle = stringResource(id = R.string.runtime_log_clear_confirm_title),
                                clearLogSummary = stringResource(id = R.string.runtime_log_clear_summary),
                            ),
                            state = RuntimeLogDiagnosticsState(
                                verboseLogEnabled = verboseLogEnabled.value,
                            ),
                            callbacks = RuntimeLogDiagnosticsCallbacks(
                                onShareLog = ::saveRuntimeLogBundle,
                                onVerboseLogEnabledChange = { enabled ->
                                    verboseLogEnabled.value = enabled
                                    VerboseLogEnableTracker.onVerboseLogToggled(enabled)
                                    io.github.magisk317.xposed.logging.LogSanitizerConfig
                                        .syncFromVerboseMode(enabled)
                                    scope.launch {
                                        AppPreferencesDataStore.setBoolean(
                                            context,
                                            PrefConst.KEY_VERBOSE_LOG_MODE,
                                            enabled,
                                        )
                                        HookPreferenceMirror.publish(context)
                                        markPrefsSaved()
                                    }
                                    RuntimeDiagnosticsBridge.ensureInstalled()
                                    RuntimeLogStore.setEnabled(enabled)
                                    XLog.setLogLevel(
                                        if (enabled) Log.VERBOSE else com.github.magisk317.smscode.runtime.BuildConfig.LOG_LEVEL,
                                    )
                                },
                                onRetentionClick = { showRuntimeLogRetentionDialog = true },
                                onClearLogClick = { showClearLogConfirmDialog = true },

                            ),
                            layout = RuntimeLogDiagnosticsLayout(
                                shareEntryMode = RuntimeLogShareEntryMode.SEPARATE_ITEM,
                                itemOrder = listOf(
                                    RuntimeLogDiagnosticsItem.SHARE_LOG,
                                    RuntimeLogDiagnosticsItem.VERBOSE_LOG,
                                    RuntimeLogDiagnosticsItem.SENSITIVE_LOG,
                                    RuntimeLogDiagnosticsItem.RETENTION,
                                    RuntimeLogDiagnosticsItem.CLEAR_LOG,
                                ),
                            ),
                        )
                        SwitchItem(
                            title = stringResource(id = R.string.pref_auto_update_on_start_title),
                            summary = stringResource(id = R.string.pref_auto_update_on_start_summary),
                            key = PrefConst.KEY_AUTO_UPDATE_ON_START,
                            defaultValue = true,
                            stateOverride = autoUpdateEnabled,
                            onSaved = markPrefsSaved,
                        )
                        if (autoUpdateEnabled.value) {
                            SwitchItem(
                                title = stringResource(id = R.string.pref_auto_update_wifi_only_title),
                                summary = stringResource(id = R.string.pref_auto_update_wifi_only_summary),
                                key = PrefConst.KEY_AUTO_UPDATE_WIFI_ONLY,
                                defaultValue = false,
                                onSaved = markPrefsSaved,
                            )
                        }
                        Item(
                            title = stringResource(id = R.string.pref_privacy_policy_title),
                            summary = "",
                        ) { showPrivacyPolicyPage = true }
                    }

                    Spacer(modifier = Modifier.height(Const.SPACING_SMALL.dp))
                }
            }
        }

        DismissibleSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (isCompact) Const.BOTTOM_SPACE_HEIGHT.dp else 0.dp)
                .navigationBarsPadding(),
        )
    }

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
        showPrivacyPolicyPage = showPrivacyPolicyPage,
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
        onShowPrivacyPolicyPageChange = { showPrivacyPolicyPage = it },
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
                    R.string.pref_sim_slot1_remark_title
                } else {
                    R.string.pref_sim_slot2_remark_title
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
        val runtimeLogRetentionDaysError = stringResource(id = R.string.pref_runtime_log_retention_days_error)
        NonNegativeIntegerInputDialog(
            title = stringResource(id = R.string.pref_runtime_log_retention_days_title),
            initialValue = runtimeLogRetentionDays,
            errorText = runtimeLogRetentionDaysError,
            onDismiss = { showRuntimeLogRetentionDialog = false },
            minimumValue = PrefConst.RUNTIME_LOG_RETENTION_DAYS_MIN,
            supportingText = stringResource(id = R.string.pref_runtime_log_retention_days_hint),
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
}

private fun handleSettingsEvent(
    event: SettingsEvent,
    context: android.content.Context,
    activity: Activity?,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState,
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

@Composable
private fun SettingsDialogs(
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
    showPrivacyPolicyPage: Boolean,
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
    onShowPrivacyPolicyPageChange: (Boolean) -> Unit,
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
            title = stringResource(id = R.string.pref_auto_input_code_delay_title),
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
            title = stringResource(id = R.string.pref_auto_input_code_interval_title),
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
                onShowPrivacyPolicyDialogChange(false)
                onShowPrivacyPolicyPageChange(true)
            },
        )
    }

    if (showPrivacyPolicyPage) {
        PrivacyPolicyPage(onDismiss = { onShowPrivacyPolicyPageChange(false) })
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
private fun ExpandableSettingsSection(
    title: String,
    summary: String = "",
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    accordionMode: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sectionExpanded = if (accordionMode) expanded else true

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Const.PADDING_SMALL.dp),
    ) {
        io.github.magisk317.uikit.preference.SectionCard(
            title = title,
            summary = summary,
            accordionMode = accordionMode,
            sectionExpanded = sectionExpanded,
            onExpandedChange = onExpandedChange,
            content = content,
        )
    }
}

private const val AUTO_INPUT_ACCESSIBILITY_SERVICE_CLASS_NAME =
    "com.github.magisk317.smscode.service.AutoInputAccessibilityService"

private fun isAutoInputAccessibilityServiceEnabled(context: android.content.Context): Boolean {
    val expectedService = ComponentName(
        context.packageName,
        AUTO_INPUT_ACCESSIBILITY_SERVICE_CLASS_NAME,
    ).flattenToString()
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()
    if (enabledServices.isBlank()) return false
    return enabledServices.split(':').any { candidate ->
        candidate.equals(expectedService, ignoreCase = true)
    }
}

@Composable
fun Item(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    io.github.magisk317.uikit.preference.Item(
        title = title,
        summary = summary,
        modifier = modifier,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
fun SwitchItem(
    title: String,
    summary: String,
    key: String,
    defaultValue: Boolean,
    modifier: Modifier = Modifier,
    stateOverride: MutableState<Boolean>? = null,
    enabled: Boolean = true,
    onItemClick: (() -> Unit)? = null,
    onToggle: ((Boolean) -> Unit)? = null,
    onSaved: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val checkedState = stateOverride ?: rememberPrefBoolean(key, defaultValue)
    val defaultSavedToast = context.getString(R.string.pref_sync_toast)

    fun toggle(checked: Boolean) {
        if (!enabled) return
        checkedState.value = checked
        scope.launch {
            AppPreferencesDataStore.setBoolean(context, key, checked)
            HookPreferenceMirror.publish(context)
            if (onSaved != null) {
                onSaved()
            } else {
                snackbarHostState.showSnackbar(defaultSavedToast)
            }
        }
        onToggle?.invoke(checked)
    }

    if (onItemClick != null) {
        io.github.magisk317.uikit.preference.ActionSwitchItem(
            title = title,
            summary = summary,
            checked = checkedState.value,
            enabled = enabled,
            modifier = modifier,
            onClick = onItemClick,
            onCheckedChange = { toggle(it) },
        )
    } else {
        io.github.magisk317.uikit.preference.StateSwitchItem(
            title = title,
            summary = summary,
            checked = checkedState.value,
            enabled = enabled,
            modifier = modifier,
            onCheckedChange = { toggle(it) },
        )
    }
}

private val booleanPreferenceStateCache =
    io.github.magisk317.uikit.state.RetainedValueCache<String, Boolean>()

@Composable
fun rememberPrefBoolean(key: String, defaultValue: Boolean): MutableState<Boolean> {
    val context = LocalContext.current
    val isActive = LocalSettingsPageRuntime.current.keepDataActive
    val flow = remember(context, key, defaultValue) {
        AppPreferencesDataStore.getBooleanFlow(context, key, defaultValue)
    }
    return io.github.magisk317.uikit.state.rememberRetainedFlowState(
        cache = booleanPreferenceStateCache,
        key = key,
        initialValue = defaultValue,
        isActive = isActive,
        flow = flow,
    )
}

@Composable
private fun notificationRetentionEntryLabel(value: String): String {
    val entries = stringArrayResource(id = R.array.notification_retention_time_entry_list)
    val values = stringArrayResource(id = R.array.notification_retention_time_list)
    val index = values.indexOf(value)
    if (index >= 0) {
        return entries[index]
    }
    return value.takeIf { it.isNotBlank() } ?: "0"
}

private fun normalizeNumericInput(raw: String): String {
    val normalized = StringBuilder(raw.length)
    raw.forEach { ch ->
        when {
            ch.isWhitespace() || Character.getType(ch) == Character.FORMAT.toInt() -> Unit
            ch.digitToIntOrNull() != null -> normalized.append(ch.digitToInt())
            ch in setOf('-', '－', '﹣', '—', '–') && normalized.isEmpty() -> normalized.append('-')
            else -> normalized.append(ch)
        }
    }
    return normalized.toString()
}

private fun parseNonNegativeLong(raw: String): Long? {
    return normalizeNumericInput(raw)
        .toLongOrNull()
        ?.takeIf { it >= 0L }
}

/**
 * Migrates legacy seconds-formatted delay values to milliseconds.
 * Stored values ≤ 60 are legacy seconds from before the unit change; convert to ms.
 */
@Composable
private fun simSlotRemarkSummary(remark: String): String {
    return remark.takeIf { it.isNotBlank() } ?: stringResource(id = R.string.pref_sim_slot_remark_empty)
}

@Composable
fun RetentionDialog(
    selectedValue: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    titleId: Int = R.string.pref_notification_retention_time_title,
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
        title = { Text(stringResource(id = R.string.privacy_dialog_title)) },
        text = {
            Column {
                Text(stringResource(id = R.string.privacy_dialog_content))
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

private data class BackupSelectionFlags(
    val includeConfig: Boolean = true,
    val includeRules: Boolean = true,
    val includeRecords: Boolean = true,
    val includeDatabase: Boolean = false,
)

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun BackupDialog(onDismiss: () -> Unit, onConfirm: (BackupSelectionFlags) -> Unit) {
    var checkConfig by remember { mutableStateOf(true) }
    var checkRules by remember { mutableStateOf(true) }
    var checkRecords by remember { mutableStateOf(true) }
    var checkDatabase by remember { mutableStateOf(false) }
    val cancelLabel = stringResource(id = R.string.cancel)
    val confirmLabel = stringResource(id = R.string.confirm)

    io.github.magisk317.uikit.surface.AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.dialog_backup_title)) },
        text = {
            Column {
                Text(stringResource(id = R.string.dialog_backup_msg), modifier = Modifier.padding(bottom = 8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { checkConfig = !checkConfig },
                ) {
                    io.github.magisk317.uikit.preference.AppCheckbox(
                        checked = checkConfig,
                        onCheckedChange = { checkConfig = it },
                    )
                    Text(stringResource(id = R.string.item_config))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { checkRules = !checkRules },
                ) {
                    io.github.magisk317.uikit.preference.AppCheckbox(
                        checked = checkRules,
                        onCheckedChange = { checkRules = it },
                    )
                    Text(stringResource(id = R.string.item_rules))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { checkRecords = !checkRecords },
                ) {
                    io.github.magisk317.uikit.preference.AppCheckbox(
                        checked = checkRecords,
                        onCheckedChange = { checkRecords = it },
                    )
                    Text(stringResource(id = R.string.item_records))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { checkDatabase = !checkDatabase },
                ) {
                    io.github.magisk317.uikit.preference.AppCheckbox(
                        checked = checkDatabase,
                        onCheckedChange = { checkDatabase = it },
                    )
                    Text(stringResource(id = R.string.item_database_with_note))
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
private fun RestoreConfirmDialog(onDismiss: () -> Unit, onConfirm: (BackupSelectionFlags) -> Unit) {
    var checkConfig by remember { mutableStateOf(true) }
    var checkRules by remember { mutableStateOf(true) }
    var checkRecords by remember { mutableStateOf(true) }
    var checkDatabase by remember { mutableStateOf(false) }
    val cancelLabel = stringResource(id = R.string.cancel)
    val confirmLabel = stringResource(id = R.string.confirm)

    io.github.magisk317.uikit.surface.AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.dialog_restore_title)) },
        text = {
            Column {
                Text(stringResource(id = R.string.dialog_restore_msg), modifier = Modifier.padding(bottom = 8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { checkConfig = !checkConfig },
                ) {
                    io.github.magisk317.uikit.preference.AppCheckbox(
                        checked = checkConfig,
                        onCheckedChange = { checkConfig = it },
                    )
                    Text(stringResource(id = R.string.item_config))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { checkRules = !checkRules },
                ) {
                    io.github.magisk317.uikit.preference.AppCheckbox(
                        checked = checkRules,
                        onCheckedChange = { checkRules = it },
                    )
                    Text(stringResource(id = R.string.item_rules))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { checkRecords = !checkRecords },
                ) {
                    io.github.magisk317.uikit.preference.AppCheckbox(
                        checked = checkRecords,
                        onCheckedChange = { checkRecords = it },
                    )
                    Text(stringResource(id = R.string.item_records))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { checkDatabase = !checkDatabase },
                ) {
                    io.github.magisk317.uikit.preference.AppCheckbox(
                        checked = checkDatabase,
                        onCheckedChange = { checkDatabase = it },
                    )
                    Text(stringResource(id = R.string.item_database_with_note))
                }
                Text(
                    text = stringResource(id = R.string.restore_warning_msg),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
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
fun rememberPrefInt(key: String, defaultValue: Int): MutableIntState {
    val context = LocalContext.current
    val isActive = LocalSettingsPageRuntime.current.keepDataActive
    val state = remember { mutableIntStateOf(defaultValue) }
    LaunchedEffect(key, isActive) {
        if (!isActive) return@LaunchedEffect
        state.intValue = AppPreferencesDataStore.getInt(context, key, defaultValue)
    }
    return state
}

@Composable
fun rememberPrefFloat(key: String, defaultValue: Float): MutableFloatState {
    val context = LocalContext.current
    val isActive = LocalSettingsPageRuntime.current.keepDataActive
    val state = remember { mutableFloatStateOf(defaultValue) }
    LaunchedEffect(key, isActive) {
        if (!isActive) return@LaunchedEffect
        state.floatValue = AppPreferencesDataStore.getFloat(context, key, defaultValue)
    }
    return state
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
        title = { Text(text = title) },
        text = {
            Column {
                Text(
                    text = valueFormatter(sliderState.value),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Slider(
                    state = sliderState,
                    onValueChange = { sliderState.value = it },
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
internal val LocalThemeSettingsNavigation = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }
