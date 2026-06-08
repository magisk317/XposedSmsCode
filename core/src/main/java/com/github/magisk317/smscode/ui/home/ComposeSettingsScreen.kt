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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import com.github.magisk317.smscode.common.utils.HookPreferenceMirror
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.github.magisk317.smscode.core.BuildConfig
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.common.constant.CodeNotificationOwner
import com.github.magisk317.smscode.common.constant.Const
import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.common.utils.ActivationDiagnosticsStore
import com.github.magisk317.smscode.common.utils.AppPreferencesDataStore
import com.github.magisk317.smscode.common.utils.PackageUtils
import com.github.magisk317.smscode.common.utils.LogBundleExporter
import com.github.magisk317.smscode.runtime.RuntimeBackupFacade
import com.github.magisk317.smscode.runtime.RuntimeBackupImportStatus
import com.github.magisk317.smscode.runtime.RuntimeNotificationFacade as NotificationUtils
import com.github.magisk317.smscode.common.utils.RuntimeLogFileContent
import com.github.magisk317.smscode.common.utils.RuntimeLogFileInfo
import com.github.magisk317.smscode.common.utils.RuntimeLogFileSummary
import com.github.magisk317.smscode.common.utils.RuntimeLogStore
import com.github.magisk317.smscode.common.utils.SPUtils
import com.github.magisk317.smscode.common.utils.Utils
import com.github.magisk317.smscode.common.utils.XLog
import com.github.magisk317.smscode.ui.common.LoadingIndicatorTokens
import io.github.magisk317.uikit.foundation.LocalSnackbarHostState
import io.github.magisk317.uikit.common.DismissibleSnackbarHost
import com.github.magisk317.smscode.ui.common.PolygonMorphLoadingIndicator
import com.github.magisk317.smscode.ui.common.SessionLoadingRegistry
import com.github.magisk317.smscode.ui.common.rememberMinDurationLoading
import com.github.magisk317.smscode.ui.privacy.PrivacyPolicyPage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeSource
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Suppress("CyclomaticComplexMethod")
@Composable
fun ComposeSettingsScreen(
    hazeState: HazeState,
    hazeStyle: HazeBlurStyle,
    viewModel: SettingsViewModel? = null,
    refreshTrigger: Int = 0,
    onExit: () -> Unit = {},
) {
    when (currentUiKitStyle()) {
        UiKitStyle.Miuix -> ComposeSettingsScreenMiuix(
            hazeState = hazeState,
            hazeStyle = hazeStyle,
            viewModel = viewModel,
            refreshTrigger = refreshTrigger,
            onExit = onExit,
        )

        UiKitStyle.Expressive -> ComposeSettingsScreenMaterial(
            hazeState = hazeState,
            hazeStyle = hazeStyle,
            viewModel = viewModel,
            refreshTrigger = refreshTrigger,
            onExit = onExit,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Suppress("CyclomaticComplexMethod")
@Composable
internal fun ComposeSettingsScreenShared(
    hazeState: HazeState,
    hazeStyle: HazeBlurStyle,
    viewModel: SettingsViewModel? = null,
    refreshTrigger: Int = 0,
    onExit: () -> Unit = {},
) {
    val context = LocalContext.current
    val activityOwner = context as? ComponentActivity
    val settingsViewModel = viewModel ?: if (activityOwner != null) {
        koinViewModel(viewModelStoreOwner = activityOwner)
    } else {
        koinViewModel()
    }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val themeState by settingsViewModel.themeState.collectAsStateWithLifecycle()
    val themeMode = themeState.mode
    val uiKitStyle = themeState.uiKitStyle

    var autoInputDelay by remember { mutableStateOf(PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT) }
    var autoInputInterval by remember { mutableStateOf(PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL_DEFAULT) }
    var retentionTime by remember { mutableStateOf(PrefConst.NOTIFICATION_RETENTION_TIME_DEFAULT) }
    val showCodeNotificationEnabled = remember { mutableStateOf(true) }
    var codeNotificationOwner by remember { mutableStateOf("") }
    var smsCodeKeywords by remember { mutableStateOf(PrefConst.SMSCODE_KEYWORDS_DEFAULT) }
    var simSlot1Remark by remember { mutableStateOf("") }
    var simSlot2Remark by remember { mutableStateOf("") }
    var showAutoInputDialog by remember { mutableStateOf(false) }
    var showAutoInputIntervalDialog by remember { mutableStateOf(false) }
    var showRetentionDialog by remember { mutableStateOf(false) }
    var showNotificationOwnerDialog by remember { mutableStateOf(false) }
    var pendingEnableNotification by remember { mutableStateOf(false) }
    var pendingNotificationOwnerPermissionSelection by remember { mutableStateOf<String?>(null) }
    var pendingNotificationPermissionEnable by remember { mutableStateOf(false) }
    var showSmsTestDialog by remember { mutableStateOf(false) }
    var smsTestInput by remember { mutableStateOf("") }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showUiKitStyleDialog by remember { mutableStateOf(false) }
    var showQRCodeDialog by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyPage by remember { mutableStateOf(false) }
    var showKeywordsDialog by remember { mutableStateOf(false) }
    var showSimSlotRemarkDialog by remember { mutableStateOf<Int?>(null) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var isActivated by remember { mutableStateOf(ActivationDiagnosticsStore.isModuleActivated(context)) }
    val supportsAccessibilityAutoInput = BuildConfig.ENABLE_ACCESSIBILITY_AUTO_INPUT
    var autoInputAccessibilityEnabled by remember {
        mutableStateOf(
            supportsAccessibilityAutoInput && isAutoInputAccessibilityServiceEnabled(context),
        )
    }
    var settingsDataLoaded by remember { mutableStateOf(false) }
    var manualRefreshing by remember { mutableStateOf(false) }
    var expandGeneral by remember { mutableStateOf(false) }
    var expandSmsCode by remember { mutableStateOf(false) }
    var expandAutoInput by remember { mutableStateOf(false) }
    var expandNotification by remember { mutableStateOf(false) }
    var expandExperimental by remember { mutableStateOf(false) }
    var expandOthers by remember { mutableStateOf(false) }
    val launcherIconVisible = remember { mutableStateOf(settingsViewModel.isLauncherIconVisible()) }
    var runtimeLogRetentionDays by remember { mutableIntStateOf(PrefConst.RUNTIME_LOG_RETENTION_DAYS_DEFAULT) }
    var showRuntimeLogRetentionDialog by remember { mutableStateOf(false) }
    var showClearLogConfirmDialog by remember { mutableStateOf(false) }
    var runtimeLogWrapLines by rememberSaveable { mutableStateOf(false) }

    val reloadSettingsData: suspend () -> Unit = {
        autoInputDelay = AppPreferencesDataStore.getString(
            context,
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY,
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT,
        )
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
        showCodeNotificationEnabled.value = AppPreferencesDataStore.getBoolean(
            context,
            PrefConst.KEY_SHOW_CODE_NOTIFICATION,
            true,
        )
        codeNotificationOwner = CodeNotificationOwner.normalize(
            AppPreferencesDataStore.getString(
                context,
                PrefConst.KEY_CODE_NOTIFICATION_OWNER,
                "",
            ),
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

    LaunchedEffect(Unit) {
        reloadSettingsData()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val markPrefsSaved: () -> Unit = {
        scope.launch {
            snackbarHostState.showSnackbar(context.getString(R.string.pref_sync_toast))
        }
    }

    fun shareRuntimeLogBundle() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                LogBundleExporter.buildLogBundle(context)
            }
            val file = result.file
            if (file == null) {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.runtime_log_export_failed, result.details),
                )
                return@launch
            }
            runCatching {
                LogBundleExporter.shareLogBundle(context, file)
            }.onFailure {
                snackbarHostState.showSnackbar(
                    context.getString(
                        R.string.runtime_log_share_failed,
                        it.message ?: it.javaClass.simpleName,
                    ),
                )
            }
        }
    }

    suspend fun persistNotificationOwnerSelection(owner: String, enableNotification: Boolean) {
        codeNotificationOwner = owner
        AppPreferencesDataStore.setString(
            context,
            PrefConst.KEY_CODE_NOTIFICATION_OWNER,
            owner,
        )
        if (enableNotification) {
            showCodeNotificationEnabled.value = true
            AppPreferencesDataStore.setBoolean(
                context,
                PrefConst.KEY_SHOW_CODE_NOTIFICATION,
                true,
            )
        }
        HookPreferenceMirror.publish(context)
        scope.launch {
            snackbarHostState.showSnackbar(context.getString(R.string.pref_sync_toast))
        }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            isActivated = ActivationDiagnosticsStore.isModuleActivated(context)
            autoInputAccessibilityEnabled =
                supportsAccessibilityAutoInput && isAutoInputAccessibilityServiceEnabled(context)
            if (
                pendingNotificationOwnerPermissionSelection == CodeNotificationOwner.APP &&
                NotificationUtils.hasPostNotificationsPermission(context)
            ) {
                val enableNotification = pendingNotificationPermissionEnable
                pendingNotificationOwnerPermissionSelection = null
                pendingNotificationPermissionEnable = false
                persistNotificationOwnerSelection(
                    owner = CodeNotificationOwner.APP,
                    enableNotification = enableNotification,
                )
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
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val component = ComponentName(
                    context,
                    "com.github.magisk317.smscode.service.AutoInputAccessibilityService",
                ).flattenToString()
                val currentServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                ).orEmpty()
                val newServices = if (enable) {
                    if (currentServices.contains(component)) return@withContext true
                    if (currentServices.isEmpty()) component else "$currentServices:$component"
                } else {
                    if (!currentServices.contains(component)) return@withContext true
                    currentServices.split(":").filter { it.isNotEmpty() && it != component }.joinToString(":")
                }

                val process = Runtime.getRuntime().exec("su")
                val os = java.io.DataOutputStream(process.outputStream)
                os.writeBytes("settings put secure enabled_accessibility_services $newServices\n")
                if (enable) {
                    os.writeBytes("settings put secure accessibility_enabled 1\n")
                }
                os.writeBytes("exit\n")
                os.flush()
                process.waitFor() == 0
            } catch (e: java.io.IOException) {
                XLog.w("Root accessibility toggle failed: %s", e.message ?: e.javaClass.simpleName)
                false
            } catch (e: SecurityException) {
                XLog.w("Root accessibility toggle denied: %s", e.message ?: e.javaClass.simpleName)
                false
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                XLog.w("Root accessibility toggle interrupted: %s", e.message ?: e.javaClass.simpleName)
                false
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
    val notificationSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        scope.launch {
            if (
                pendingNotificationOwnerPermissionSelection == CodeNotificationOwner.APP &&
                NotificationUtils.hasPostNotificationsPermission(context)
            ) {
                val enableNotification = pendingNotificationPermissionEnable
                pendingNotificationOwnerPermissionSelection = null
                pendingNotificationPermissionEnable = false
                persistNotificationOwnerSelection(
                    owner = CodeNotificationOwner.APP,
                    enableNotification = enableNotification,
                )
            } else if (pendingNotificationOwnerPermissionSelection == CodeNotificationOwner.APP) {
                pendingNotificationOwnerPermissionSelection = null
                pendingNotificationPermissionEnable = false
                snackbarHostState.showSnackbar(
                    context.getString(R.string.pref_code_notification_owner_permission_denied),
                )
            }
        }
    }
    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        val fallbackIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
        if (activityOwner != null) {
            runCatching {
                notificationSettingsLauncher.launch(intent)
            }.recoverCatching {
                notificationSettingsLauncher.launch(fallbackIntent)
            }.onFailure {
                pendingNotificationOwnerPermissionSelection = null
                pendingNotificationPermissionEnable = false
                scope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.pref_code_notification_owner_permission_denied),
                    )
                }
            }
            return
        }
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.recoverCatching {
            context.startActivity(fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            pendingNotificationOwnerPermissionSelection = null
            pendingNotificationPermissionEnable = false
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.pref_code_notification_owner_permission_denied),
                )
            }
        }
    }
    val requestNotificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingNotificationOwnerPermissionSelection == CodeNotificationOwner.APP) {
            scope.launch {
                val enableNotification = pendingNotificationPermissionEnable
                pendingNotificationOwnerPermissionSelection = null
                pendingNotificationPermissionEnable = false
                persistNotificationOwnerSelection(
                    owner = CodeNotificationOwner.APP,
                    enableNotification = enableNotification,
                )
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.pref_code_notification_owner_permission_settings_hint),
                )
            }
            openNotificationSettings()
        }
    }
    fun requestNotificationPermissionIfNeeded(enableNotification: Boolean): Boolean {
        val permissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationUtils.hasPostNotificationsPermission(context)
        if (!permissionRequired) {
            return false
        }
        pendingNotificationOwnerPermissionSelection = CodeNotificationOwner.APP
        pendingNotificationPermissionEnable = enableNotification
        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        return true
    }

    LaunchedEffect(settingsViewModel, lifecycleOwner) {
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
    val showTopDivider by remember {
        derivedStateOf { scrollState.value > 0 }
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val shouldShowInitialLoading = remember { SessionLoadingRegistry.shouldShowInitial("settings") }
    val showLoading = rememberMinDurationLoading(
        actualLoading = shouldShowInitialLoading && !settingsDataLoaded,
        minDurationMillis = LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS,
    )
    val pullToRefreshState = rememberPullToRefreshState()
    val blurRadius = rememberPrefInt(PrefConst.KEY_HAZE_BLUR_RADIUS, 25)
    val tintAlpha = rememberPrefFloat(PrefConst.KEY_HAZE_TINT_ALPHA, 0.2f)
    var showBlurRadiusDialog by remember { mutableStateOf(false) }
    var showTintAlphaDialog by remember { mutableStateOf(false) }
    val autoInputEnabled = rememberPrefBoolean(PrefConst.KEY_ENABLE_AUTO_INPUT_CODE, true)
    val autoUpdateEnabled = rememberPrefBoolean(PrefConst.KEY_AUTO_UPDATE_ON_START, true)
    val moduleEnabled = rememberPrefBoolean(PrefConst.KEY_ENABLE, true)
    val accordionMode = rememberPrefBoolean(PrefConst.KEY_SETTINGS_ACCORDION_MODE, true)

    LaunchedEffect(settingsDataLoaded, showLoading, shouldShowInitialLoading) {
        if (shouldShowInitialLoading && settingsDataLoaded && !showLoading) {
            SessionLoadingRegistry.markShown("settings")
        }
    }

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) {
            runManualRefresh()
        }
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Box(modifier = Modifier.fillMaxSize()) {
        val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
            Const.TOP_BAR_HEIGHT.dp // TopBar height
        val isCompact = LocalConfiguration.current.screenWidthDp < 600
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
                        .padding(top = topPadding + LoadingIndicatorTokens.OverlayTopSpacing),
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
                            .padding(top = topPadding + LoadingIndicatorTokens.OverlayTopSpacing),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState)
                        .padding(bottom = bottomPadding)
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(Const.SPACING_SMALL.dp),
                ) {
                    Spacer(modifier = Modifier.height(topPadding))

                    SwitchItem(
                        title = stringResource(id = R.string.pref_enable_title),
                        summary = stringResource(id = R.string.pref_enable_summary),
                        key = PrefConst.KEY_ENABLE,
                        defaultValue = true,
                        stateOverride = moduleEnabled,
                        modifier = Modifier.padding(horizontal = Const.PADDING_SMALL.dp),
                        onSaved = markPrefsSaved,
                    )
                    SwitchItem(
                        title = stringResource(id = R.string.pref_settings_display_mode_title),
                        summary = stringResource(id = R.string.pref_settings_display_mode_summary),
                        key = PrefConst.KEY_SETTINGS_ACCORDION_MODE,
                        defaultValue = true,
                        stateOverride = accordionMode,
                        modifier = Modifier.padding(horizontal = Const.PADDING_SMALL.dp),
                        onSaved = markPrefsSaved,
                    )

                    ExpandableSettingsSection(
                        title = stringResource(id = R.string.settings_group_general),
                        expanded = expandGeneral,
                        onExpandedChange = { expandGeneral = !expandGeneral },
                        accordionMode = accordionMode.value,
                    ) {
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
                        Item(
                            title = stringResource(id = R.string.pref_language_title),
                            summary = stringResource(id = R.string.pref_language_summary),
                        ) { showLanguageDialog = true }
                        Item(
                            title = stringResource(id = R.string.pref_haze_blur_radius_title),
                            summary = "${blurRadius.intValue}dp",
                        ) { showBlurRadiusDialog = true }
                        Item(
                            title = stringResource(id = R.string.pref_haze_tint_alpha_title),
                            summary = "%.2f".format(tintAlpha.floatValue),
                        ) { showTintAlphaDialog = true }
                    }

                    ExpandableSettingsSection(
                        title = stringResource(id = R.string.settings_group_smscode),
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
                                pendingEnableNotification = true
                                showNotificationOwnerDialog = true
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
                        Item(
                            title = stringResource(id = R.string.pref_code_notification_owner_title),
                            summary = codeNotificationOwnerItemSummary(codeNotificationOwner),
                        ) {
                            pendingEnableNotification = false
                            showNotificationOwnerDialog = true
                        }
                        SwitchItem(
                            title = stringResource(id = R.string.pref_auto_cancel_notification_title),
                            summary = stringResource(id = R.string.pref_auto_cancel_notification_summary),
                            key = PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION,
                            defaultValue = false,
                            enabled = showCodeNotificationEnabled.value,
                            onSaved = markPrefsSaved,
                        )
                        Item(
                            title = stringResource(id = R.string.pref_notification_retention_time_title),
                            summary = notificationRetentionEntryLabel(retentionTime),
                            enabled = showCodeNotificationEnabled.value,
                        ) { showRetentionDialog = true }
                    }

                    ExpandableSettingsSection(
                        title = stringResource(id = R.string.settings_group_experimental),
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
                            val intent = RuntimeBackupFacade.getImportRuleListSAFIntent(context)
                            restoreLauncher.launch(intent)
                        }
                        SwitchItem(
                            title = stringResource(id = R.string.pref_verbose_log_mode_title),
                            summary = stringResource(id = R.string.pref_verbose_log_mode_summary),
                            key = PrefConst.KEY_VERBOSE_LOG_MODE,
                            defaultValue = false,
                            onItemClick = {
                                shareRuntimeLogBundle()
                            },
                            onToggle = { on ->
                                RuntimeLogStore.setEnabled(on)
                                XLog.setLogLevel(if (on) Log.VERBOSE else com.github.magisk317.smscode.runtime.BuildConfig.LOG_LEVEL)
                            },
                            onSaved = markPrefsSaved,
                        )
                        Item(
                            title = stringResource(id = R.string.pref_runtime_log_retention_days_title),
                            summary = stringResource(
                                id = R.string.pref_runtime_log_retention_days_summary,
                                runtimeLogRetentionDays,
                            ),
                        ) { showRuntimeLogRetentionDialog = true }
                        Item(
                            title = stringResource(id = R.string.runtime_log_clear_confirm_title),
                            summary = stringResource(id = R.string.runtime_log_clear_summary),
                        ) { showClearLogConfirmDialog = true }
                        if (BuildConfig.DEBUG) {
                            SwitchItem(
                                title = stringResource(id = R.string.pref_sensitive_debug_log_mode_title),
                                summary = stringResource(id = R.string.pref_sensitive_debug_log_mode_summary),
                                key = PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE,
                                defaultValue = false,
                                onSaved = markPrefsSaved,
                            )
                        }
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

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter),
        ) {
            io.github.magisk317.uikit.surface.AppTopBar(
                title = stringResource(id = R.string.pref_general_title),
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                scrollBehavior = scrollBehavior,
                windowInsets = WindowInsets.statusBars,
                modifier = Modifier
                    .hazeEffect(hazeState) {
                    blurEffect { style = hazeStyle }
                        forceInvalidateOnPreDraw = true
                    },
            )
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
        themeMode = themeMode,
        uiKitStyle = uiKitStyle,
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
        showThemeDialog = showThemeDialog,
        showUiKitStyleDialog = showUiKitStyleDialog,
        showQRCodeDialog = showQRCodeDialog,
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
        onShowThemeDialogChange = { showThemeDialog = it },
        onShowUiKitStyleDialogChange = { showUiKitStyleDialog = it },
        onShowQrCodeDialogChange = { showQRCodeDialog = it },
        onShowPrivacyPolicyDialogChange = { showPrivacyPolicyDialog = it },
        onShowPrivacyPolicyPageChange = { showPrivacyPolicyPage = it },
        onShowBackupDialogChange = { showBackupDialog = it },
        onShowRestoreDialogChange = { showRestoreDialog = it },
        onBackupFlagsChange = { backupFlags = it },
        onPendingSavedToast = markPrefsSaved,
        backupLauncher = backupLauncher,
        settingsViewModel = settingsViewModel,
        onExit = onExit,
        onSetTheme = { mode, x, y -> settingsViewModel.setThemeMode(mode, x, y) },
        onSetUiKitStyle = { style -> settingsViewModel.setUiKitStyle(style) },
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
            onDismiss = { showSimSlotRemarkDialog = null },
            supportingText = stringResource(id = R.string.pref_sim_slot_remark_summary),
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


    if (showRuntimeLogRetentionDialog) {
        val runtimeLogRetentionDaysError = stringResource(id = R.string.pref_runtime_log_retention_days_error)
        TextInputDialog(
            title = stringResource(id = R.string.pref_runtime_log_retention_days_title),
            initialValue = runtimeLogRetentionDays.toString(),
            onDismiss = { showRuntimeLogRetentionDialog = false },
            supportingText = stringResource(id = R.string.pref_runtime_log_retention_days_hint),
            validator = {
                if (parseIntAtLeastInput(it, PrefConst.RUNTIME_LOG_RETENTION_DAYS_MIN) != null) {
                    null
                } else {
                    runtimeLogRetentionDaysError
                }
            },
        ) { updated ->
            showRuntimeLogRetentionDialog = false
            scope.launch {
                val days = parseIntAtLeastInput(
                    updated,
                    PrefConst.RUNTIME_LOG_RETENTION_DAYS_MIN,
                ) ?: PrefConst.RUNTIME_LOG_RETENTION_DAYS_MIN
                runtimeLogRetentionDays = days
                AppPreferencesDataStore.setInt(context, PrefConst.KEY_RUNTIME_LOG_RETENTION_DAYS, days)
                RuntimeLogStore.setRetentionDays(days)
                HookPreferenceMirror.publish(context)
                markPrefsSaved()
            }
        }
    }

    if (showLanguageDialog) {
        LanguageChooserDialog(
            onDismiss = { showLanguageDialog = false },
            onLanguageSelected = { tag ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    val localeManager = context.getSystemService(android.app.LocaleManager::class.java)
                    localeManager?.applicationLocales = if (tag.isEmpty()) {
                        android.os.LocaleList.getEmptyLocaleList()
                    } else {
                        android.os.LocaleList.forLanguageTags(tag)
                    }
                }
                showLanguageDialog = false
            },
        )
    }

    if (showBlurRadiusDialog) {
        SliderDialog(
            title = stringResource(id = R.string.pref_haze_blur_radius_title),
            value = blurRadius.intValue.toFloat(),
            valueRange = 0f..100f,
            steps = 0,
            onDismiss = { showBlurRadiusDialog = false },
            onValueChange = {
                val newVal = it.toInt()
                blurRadius.intValue = newVal
                scope.launch {
                    AppPreferencesDataStore.setInt(context, PrefConst.KEY_HAZE_BLUR_RADIUS, newVal)
                    HookPreferenceMirror.publish(context)
                    markPrefsSaved()
                }
                showBlurRadiusDialog = false
            },
            valueFormatter = { "${it.toInt()}dp" },
        )
    }

    if (showNotificationOwnerDialog) {
        NotificationOwnerDialog(
            owner = codeNotificationOwner,
            onDismiss = {
                showNotificationOwnerDialog = false
                pendingEnableNotification = false
            },
        ) { owner ->
            val enableNotification = pendingEnableNotification
            showNotificationOwnerDialog = false
            pendingEnableNotification = false
            if (owner == CodeNotificationOwner.APP &&
                requestNotificationPermissionIfNeeded(enableNotification)
            ) {
                return@NotificationOwnerDialog
            }
            scope.launch {
                persistNotificationOwnerSelection(owner, enableNotification)
            }
        }
    }

    if (showTintAlphaDialog) {
        SliderDialog(
            title = stringResource(id = R.string.pref_haze_tint_alpha_title),
            value = tintAlpha.floatValue,
            valueRange = 0f..1f,
            steps = 0,
            onDismiss = { showTintAlphaDialog = false },
            onValueChange = {
                tintAlpha.floatValue = it
                scope.launch {
                    AppPreferencesDataStore.setFloat(context, PrefConst.KEY_HAZE_TINT_ALPHA, it)
                    HookPreferenceMirror.publish(context)
                    markPrefsSaved()
                }
                showTintAlphaDialog = false
            },
        )
    }

    }
}

private data class RuntimeLogDialogData(
    val summary: RuntimeLogFileSummary,
    val selectedFileName: String?,
    val content: RuntimeLogFileContent?,
    val formattedPreview: String,
)

@Composable
private fun RuntimeLogInfoDialog(
    data: RuntimeLogDialogData?,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onSelectFile: (String) -> Unit,
    onOpenPreview: () -> Unit,
    onClear: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.runtime_log_viewer_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (data == null) {
                    Text(text = stringResource(id = R.string.runtime_log_info_loading))
                    return@Column
                }
                val summary = data.summary
                if (summary.fileCount == 0) {
                    Text(text = stringResource(id = R.string.runtime_log_info_empty))
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = stringResource(
                                id = R.string.runtime_log_info_summary,
                                summary.fileCount,
                                formatLogSize(summary.totalBytes),
                                summary.entryCount,
                            ),
                            maxLines = 1,
                            softWrap = false,
                        )
                        val first = summary.firstTimestamp
                        val last = summary.lastTimestamp
                        if (first != null && last != null) {
                            Text(
                                text = stringResource(
                                    id = R.string.runtime_log_info_range,
                                    formatLogTimestamp(first),
                                    formatLogTimestamp(last),
                                ),
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        summary.files.forEach { file ->
                            val selected = file.name == data.selectedFileName
                            Text(
                                text = formatRuntimeLogFileListLine(file, selected),
                                modifier = Modifier
                                    .clickable { onSelectFile(file.name) }
                                    .padding(vertical = 2.dp),
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(id = R.string.runtime_log_info_preview_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = data.formattedPreview.ifBlank { stringResource(id = R.string.runtime_log_info_empty) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                        .clickable(enabled = data.content != null, onClick = onOpenPreview),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    softWrap = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onShare, enabled = data != null) {
                Text(text = stringResource(id = R.string.action_share))
            }
        },
        dismissButton = {
            TextButton(onClick = onClear, enabled = data != null) {
                Text(text = stringResource(id = R.string.action_clear))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuntimeLogFullScreenPreviewDialog(
    fileName: String,
    text: String,
    wrapLines: Boolean,
    onWrapLinesChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(text = fileName, maxLines = 1, softWrap = false) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(id = android.R.string.cancel),
                                )
                            }
                        },
                        actions = {
                            TextButton(onClick = { onWrapLinesChange(!wrapLines) }) {
                                Text(
                                    text = stringResource(
                                        id = if (wrapLines) {
                                            R.string.runtime_log_action_no_wrap
                                        } else {
                                            R.string.runtime_log_action_wrap
                                        },
                                    ),
                                )
                            }
                        },
                    )
                },
            ) { padding ->
                val vertical = rememberScrollState()
                val horizontal = rememberScrollState()
                Text(
                    text = text.ifBlank { stringResource(id = R.string.runtime_log_info_empty) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(12.dp)
                        .verticalScroll(vertical)
                        .then(if (wrapLines) Modifier else Modifier.horizontalScroll(horizontal)),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    softWrap = wrapLines,
                )
            }
        }
    }
}

private fun loadRuntimeLogDialogData(selectedFileName: String? = null): RuntimeLogDialogData {
    val summary = runCatching {
        RuntimeLogStore.summarizeFiles()
    }.getOrElse {
        RuntimeLogFileSummary(
            fileCount = 0,
            totalBytes = 0L,
            entryCount = 0,
            firstTimestamp = null,
            lastTimestamp = null,
            files = emptyList(),
        )
    }
    val selected = selectRuntimeLogFile(summary, selectedFileName)
    val content = selected?.let { fileName ->
        runCatching { RuntimeLogStore.readLogFile(fileName) }.getOrNull()
    }
    val preview = content?.let { formatRuntimeLogContent(it.name, it.text) }.orEmpty()
    return RuntimeLogDialogData(
        summary = summary,
        selectedFileName = selected,
        content = content,
        formattedPreview = preview,
    )
}

private fun selectRuntimeLogFile(summary: RuntimeLogFileSummary, selectedFileName: String?): String? {
    val files = summary.files
    if (files.any { it.name == selectedFileName }) return selectedFileName
    return files.lastOrNull { it.name.matches(Regex("""runtime\.\d{4}-\d{2}-\d{2}\.jsonl""")) }?.name
        ?: files.lastOrNull()?.name
}

private fun formatRuntimeLogFileListLine(file: RuntimeLogFileInfo, selected: Boolean): String {
    val marker = if (selected) "*" else " "
    val lines = file.lineCount.toString().padStart(5)
    val size = formatLogSize(file.sizeBytes).padStart(8)
    val modified = file.lastTimestamp?.let(::formatLogTimestamp).orEmpty().padEnd(19)
    return "$marker -rw------- $lines $size $modified ${file.name}"
}

private fun formatRuntimeLogContent(fileName: String, text: String): String {
    if (!fileName.endsWith(".jsonl")) return text
    return text.lineSequence()
        .filter { it.isNotBlank() }
        .joinToString(separator = "\n\n") { line ->
            formatJsonLine(line)
        }
}

private fun formatJsonLine(line: String): String {
    return runCatching {
        when (val value = JSONTokener(line).nextValue()) {
            is JSONObject -> value.toString(2)
            is JSONArray -> value.toString(2)
            else -> line
        }
    }.getOrDefault(line)
}

private fun formatLogTimestamp(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

private fun formatLogSize(bytes: Long): String {
    if (bytes < BYTES_PER_KIB) return "$bytes B"
    var value = bytes.toDouble() / BYTES_PER_KIB.toDouble()
    var unitIndex = 0
    while (value >= BYTES_PER_KIB.toDouble() && unitIndex < LOG_SIZE_UNITS.lastIndex) {
        value /= BYTES_PER_KIB.toDouble()
        unitIndex += 1
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, LOG_SIZE_UNITS[unitIndex])
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
    themeMode: Int,
    uiKitStyle: Int,
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
    showThemeDialog: Boolean,
    showUiKitStyleDialog: Boolean,
    showQRCodeDialog: Pair<Int, String>?,
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
    onShowThemeDialogChange: (Boolean) -> Unit,
    onShowUiKitStyleDialogChange: (Boolean) -> Unit,
    onShowQrCodeDialogChange: (Pair<Int, String>?) -> Unit,
    onShowPrivacyPolicyDialogChange: (Boolean) -> Unit,
    onShowPrivacyPolicyPageChange: (Boolean) -> Unit,
    onShowBackupDialogChange: (Boolean) -> Unit,
    onShowRestoreDialogChange: (Boolean) -> Unit,
    onBackupFlagsChange: (BackupSelectionFlags) -> Unit,
    onPendingSavedToast: () -> Unit,
    backupLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    settingsViewModel: SettingsViewModel,
    onExit: () -> Unit,
    onSetTheme: (Int, Float, Float) -> Unit,
    onSetUiKitStyle: (Int) -> Unit,
) {
    val activityOwner = context as? Activity
    val snackbarHostState = LocalSnackbarHostState.current
    if (showAutoInputDialog) {
        val nonNegativeNumberError = stringResource(id = R.string.pref_number_non_negative_error)
        TextInputDialog(
            title = stringResource(id = R.string.pref_auto_input_code_delay_title),
            initialValue = normalizeNumericInput(autoInputDelay),
            onDismiss = { onShowAutoInputDialogChange(false) },
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
            onDismiss = { onShowAutoInputIntervalDialogChange(false) },
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
            onDismiss = { onShowKeywordsDialogChange(false) },
            singleLine = false,
            maxLines = 10,
            resetValue = PrefConst.SMSCODE_KEYWORDS_DEFAULT,
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

    if (showThemeDialog) {
        ThemeChooserDialog(
            currentMode = themeMode,
            onDismiss = { onShowThemeDialogChange(false) },
            onThemeSelected = { mode, x, y ->
                onSetTheme(mode, x, y)
                onShowThemeDialogChange(false)
            },
        )
    }

    if (BuildConfig.ENABLE_UI_KIT_STYLE_SWITCH && showUiKitStyleDialog) {
        UiKitStyleChooserDialog(
            currentStyle = uiKitStyle,
            onDismiss = { onShowUiKitStyleDialogChange(false) },
            onStyleSelected = {
                onSetUiKitStyle(it)
                onShowUiKitStyleDialogChange(false)
            },
        )
    }

    showQRCodeDialog?.let { pair ->
        QRCodeDialog(
            resId = pair.first,
            type = pair.second,
            onDismiss = { onShowQrCodeDialogChange(null) },
            onSave = {
                scope.launch {
                    Utils.saveImageToGallery(context, pair.first, "${pair.second}_qrcode")
                        .forEach { snackbarHostState.showSnackbar(it) }
                }
            },
        )
    }

    if (showPrivacyPolicyDialog) {
        PrivacyPolicyDialog(
            onDismiss = { onShowPrivacyPolicyDialogChange(false) },
            onConfirm = {
                scope.launch { SPUtils.setPrivacyPolicyAccepted(context, true) }
                onShowPrivacyPolicyDialogChange(false)
            },
            onCancel = {
                scope.launch { SPUtils.setPrivacyPolicyAccepted(context, false) }
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
                val intent = RuntimeBackupFacade.getExportRuleListSAFIntent(
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

// Helper Composables (extracted and made standalone)

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = Const.PADDING_MEDIUM.dp, vertical = Const.SPACING_SMALL.dp),
    )
}

@Composable
private fun ExpandableSettingsSection(
    title: String,
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
            accordionMode = accordionMode,
            sectionExpanded = sectionExpanded,
            onExpandedChange = onExpandedChange,
            content = content,
        )
    }
}

private const val AUTO_INPUT_ACCESSIBILITY_SERVICE_CLASS_NAME =
    "com.github.magisk317.smscode.service.AutoInputAccessibilityService"
private const val BYTES_PER_KIB = 1024L
private val LOG_SIZE_UNITS = listOf("KB", "MB", "GB")

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

@Composable
fun rememberPrefBoolean(key: String, defaultValue: Boolean): MutableState<Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(defaultValue) }
    LaunchedEffect(key) {
        state.value = AppPreferencesDataStore.getBoolean(context, key, defaultValue)
    }
    return state
}

@Composable
private fun NotificationOwnerDialog(
    owner: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val selectedOwner = when (owner) {
        CodeNotificationOwner.PHONE -> CodeNotificationOwner.PHONE
        else -> CodeNotificationOwner.APP
    }
    io.github.magisk317.uikit.surface.AppBasicDialog(
        onDismissRequest = onDismiss,
    ) {
        SingleChoiceDialogSurface(title = stringResource(id = R.string.pref_code_notification_owner_title)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NotificationOwnerOption(
                    selected = selectedOwner == CodeNotificationOwner.APP,
                    text = stringResource(id = R.string.pref_code_notification_owner_app_option),
                    onClick = { onConfirm(CodeNotificationOwner.APP) },
                )
                NotificationOwnerOption(
                    selected = selectedOwner == CodeNotificationOwner.PHONE,
                    text = stringResource(id = R.string.pref_code_notification_owner_phone_option),
                    onClick = { onConfirm(CodeNotificationOwner.PHONE) },
                )
            }
        }
    }
}

@Composable
private fun NotificationOwnerOption(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        io.github.magisk317.uikit.preference.AppRadioButton(selected = selected, onClick = onClick)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun codeNotificationOwnerItemSummary(owner: String): String {
    val ownerLabel = when (owner) {
        CodeNotificationOwner.PHONE -> stringResource(id = R.string.pref_code_notification_owner_phone)
        CodeNotificationOwner.APP -> stringResource(id = R.string.pref_code_notification_owner_app)
        else -> stringResource(id = R.string.pref_code_notification_owner_unselected)
    }
    return stringResource(id = R.string.pref_code_notification_owner_summary, ownerLabel)
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

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun TextInputDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 6,
    supportingText: String? = null,
    resetValue: String? = null,
    validator: ((String) -> String?)? = null,
    onFocusLost: ((String) -> Unit)? = null,
    onDismissWithValue: ((String) -> Unit)? = null,
    onConfirm: (String) -> Unit,
) {
    var fieldValue by remember(title, initialValue) { mutableStateOf(TextFieldValue(initialValue)) }
    var hadFocus by remember(title, initialValue) { mutableStateOf(false) }
    val errorMessage = validator?.invoke(fieldValue.text)
    val cancelLabel = stringResource(id = R.string.cancel)
    val confirmLabel = stringResource(id = R.string.confirm)
    io.github.magisk317.uikit.surface.AppAlertDialog(
        onDismissRequest = {
            onDismissWithValue?.invoke(fieldValue.text)
            onDismiss()
        },
        modifier = modifier,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                )
                if (resetValue != null) {
                    io.github.magisk317.uikit.surface.AppTextButton(
                        text = stringResource(id = R.string.reset),
                        onClick = {
                            fieldValue = TextFieldValue(
                                text = resetValue,
                                selection = TextRange(resetValue.length),
                            )
                        },
                    )
                }
            }
        },
        text = {
            io.github.magisk317.uikit.surface.AppTextField(
                value = fieldValue,
                onValueChange = {
                    fieldValue = it
                },
                label = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            hadFocus = true
                        } else if (hadFocus) {
                            onFocusLost?.invoke(fieldValue.text)
                        }
                    },
                singleLine = singleLine,
                maxLines = maxLines,
                trailingIcon = {
                    if (fieldValue.text.isNotEmpty()) {
                        IconButton(onClick = { fieldValue = TextFieldValue("") }) {
                            Icon(imageVector = Icons.Filled.Clear, contentDescription = null)
                        }
                    }
                },
                supportingText = if (errorMessage != null || supportingText != null) {
                    { Text(text = errorMessage ?: supportingText!!) }
                } else null,
            )
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
                        if (errorMessage == null) {
                            onConfirm(fieldValue.text)
                        }
                    },
                    label = confirmLabel,
                    weight = 1f,
                )
            }
        },
        dismissButton = {},
    )
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

private fun parseIntAtLeastInput(raw: String, minValue: Int): Int? {
    return normalizeNumericInput(raw)
        .toIntOrNull()
        ?.takeIf { it >= minValue }
}

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
    io.github.magisk317.uikit.surface.AppBasicDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        SingleChoiceDialogSurface(title = stringResource(id = titleId), modifier = modifier) {
            Column {
                entries.forEachIndexed { index, entry ->
                    val value = values.getOrNull(index) ?: return@forEachIndexed
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConfirm(value) }
                            .padding(vertical = Const.PADDING_MEDIUM.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        io.github.magisk317.uikit.preference.AppRadioButton(
                            selected = value == selectedValue,
                            onClick = { onConfirm(value) },
                        )
                        Text(text = entry, modifier = Modifier.padding(start = Const.SPACING_MEDIUM.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeChooserDialog(currentMode: Int, onDismiss: () -> Unit, onThemeSelected: (Int, Float, Float) -> Unit) {
    val modes = listOf(
        stringResource(id = R.string.theme_follow_system) to 0,
        stringResource(id = R.string.theme_light) to 1,
        stringResource(id = R.string.theme_dark) to 2,
        stringResource(id = R.string.theme_black) to 3,
    )
    io.github.magisk317.uikit.surface.AppBasicDialog(
        onDismissRequest = onDismiss,
    ) {
        SingleChoiceDialogSurface(title = stringResource(id = R.string.pref_choose_theme_title)) {
            Column {
                modes.forEach { (label, mode) ->
                    var rowCoords: LayoutCoordinates? by remember { mutableStateOf(null) }
                    val view = LocalView.current

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .onGloballyPositioned { rowCoords = it }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { tapOffset ->
                                        val locationOnScreen = IntArray(2)
                                        view.getLocationOnScreen(locationOnScreen)

                                        val rootCoords =
                                            rowCoords?.positionInRoot() ?: androidx.compose.ui.geometry.Offset.Zero

                                        // Dialog Window Offset + Item Offset in Dialog + Tap Offset
                                        val finalX = locationOnScreen[0] + rootCoords.x + tapOffset.x
                                        val finalY = locationOnScreen[1] + rootCoords.y + tapOffset.y

                                        onThemeSelected(mode, finalX, finalY)
                                    },
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        io.github.magisk317.uikit.preference.AppRadioButton(
                            selected = mode == currentMode,
                            onClick = null,
                        )
                        Text(text = label, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun UiKitStyleChooserDialog(
    currentStyle: Int,
    onDismiss: () -> Unit,
    onStyleSelected: (Int) -> Unit,
) {
    val styles = listOf(
        stringResource(id = R.string.ui_kit_style_expressive) to UiKitStyle.Expressive.value,
        stringResource(id = R.string.ui_kit_style_miuix) to UiKitStyle.Miuix.value,
    )
    io.github.magisk317.uikit.surface.AppBasicDialog(
        onDismissRequest = onDismiss,
    ) {
        SingleChoiceDialogSurface(title = stringResource(id = R.string.pref_ui_kit_style_title)) {
            Column {
                styles.forEach { (label, style) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStyleSelected(style) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        io.github.magisk317.uikit.preference.AppRadioButton(
                            selected = style == currentStyle,
                            onClick = null,
                        )
                        Text(text = label, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun uiKitStyleLabel(style: Int): String {
    return when (UiKitStyle.fromValue(style)) {
        UiKitStyle.Miuix -> stringResource(id = R.string.ui_kit_style_miuix)
        UiKitStyle.Expressive -> stringResource(id = R.string.ui_kit_style_expressive)
    }
}

@Composable
fun LanguageChooserDialog(onDismiss: () -> Unit, onLanguageSelected: (String) -> Unit) {
    val context = LocalContext.current
    val currentTag = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val localeManager = context.getSystemService(android.app.LocaleManager::class.java)
        val appLocales = localeManager?.applicationLocales ?: android.os.LocaleList.getEmptyLocaleList()
        if (appLocales.isEmpty) "" else appLocales.get(0)?.toLanguageTag() ?: ""
    } else {
        ""
    }

    val languages = listOf(
        stringResource(id = R.string.language_follow_system) to "",
        stringResource(id = R.string.language_en) to "en",
        stringResource(id = R.string.language_zh_cn) to "zh-CN",
        stringResource(id = R.string.language_zh_tw) to "zh-TW",
    )

    io.github.magisk317.uikit.surface.AppBasicDialog(
        onDismissRequest = onDismiss,
    ) {
        SingleChoiceDialogSurface(title = stringResource(id = R.string.pref_language_title)) {
            Column {
                languages.forEach { (label, tag) ->
                    val selected = if (tag.isEmpty()) currentTag.isEmpty() else currentTag.startsWith(tag)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLanguageSelected(tag) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        io.github.magisk317.uikit.preference.AppRadioButton(
                            selected = selected,
                            onClick = { onLanguageSelected(tag) },
                        )
                        Text(text = label, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleChoiceDialogSurface(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    io.github.magisk317.uikit.surface.AppDialogSurface(
        title = title,
        modifier = modifier,
        content = content,
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
    val state = remember { mutableIntStateOf(defaultValue) }
    LaunchedEffect(key) {
        state.intValue = AppPreferencesDataStore.getInt(context, key, defaultValue)
    }
    return state
}

@Composable
fun rememberPrefFloat(key: String, defaultValue: Float): MutableFloatState {
    val context = LocalContext.current
    val state = remember { mutableFloatStateOf(defaultValue) }
    LaunchedEffect(key) {
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
    var sliderValue by remember { mutableFloatStateOf(value) }
    val cancelLabel = stringResource(id = R.string.cancel)
    val confirmLabel = stringResource(id = R.string.confirm)
    io.github.magisk317.uikit.surface.AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column {
                Text(
                    text = valueFormatter(sliderValue),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = valueRange,
                    steps = steps
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
                    onClick = { onValueChange(sliderValue) },
                    label = confirmLabel,
                    weight = 1f,
                )
            }
        },
        dismissButton = {},
    )
}
