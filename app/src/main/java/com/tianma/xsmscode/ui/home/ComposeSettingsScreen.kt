package com.tianma.xsmscode.ui.home

import android.app.Activity
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.common.constant.Const
import com.tianma.xsmscode.common.constant.PrefConst
import com.tianma.xsmscode.common.utils.AppPreferencesDataStore
import com.tianma.xsmscode.common.utils.ModuleActivationStore
import com.tianma.xsmscode.common.utils.ModuleUtils
import com.tianma.xsmscode.common.utils.PackageUtils
import com.tianma.xsmscode.common.utils.SPUtils
import com.tianma.xsmscode.common.utils.Utils
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.ui.privacy.PrivacyPolicyPage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeSettingsScreen(
    hazeState: HazeState,
    hazeStyle: HazeStyle,
    viewModel: SettingsViewModel? = null,
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

    var autoInputDelay by remember { mutableStateOf(PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT) }
    var retentionTime by remember { mutableStateOf(PrefConst.NOTIFICATION_RETENTION_TIME_DEFAULT) }
    var smsCodeKeywords by remember { mutableStateOf(PrefConst.SMSCODE_KEYWORDS_DEFAULT) }
    var showAutoInputDialog by remember { mutableStateOf(false) }
    var showRetentionDialog by remember { mutableStateOf(false) }
    var showSmsTestDialog by remember { mutableStateOf(false) }
    var smsTestInput by remember { mutableStateOf("") }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showDonateDialog by remember { mutableStateOf(false) }
    var showAlipayChoiceDialog by remember { mutableStateOf(false) }
    var showQRCodeDialog by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyPage by remember { mutableStateOf(false) }
    var showKeywordsDialog by remember { mutableStateOf(false) }
    var isActivated by remember { mutableStateOf(ModuleUtils.isModuleEnabled()) }
    var pendingSavedToast by remember { mutableStateOf(false) }

    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var backupFlags by remember { mutableStateOf(Triple(true, true, true)) } // config, rules, records

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                settingsViewModel.performBackup(uri, backupFlags.first, backupFlags.second, backupFlags.third)
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                settingsViewModel.handleBackupArguments(uri)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!SPUtils.isPrivacyPolicyAccepted(context)) {
            showPrivacyPolicyDialog = true
        }
    }

    LaunchedEffect(Unit) {
        autoInputDelay = AppPreferencesDataStore.getString(
            context,
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY,
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT,
        )
        retentionTime = AppPreferencesDataStore.getString(
            context,
            PrefConst.KEY_NOTIFICATION_RETENTION_TIME,
            PrefConst.NOTIFICATION_RETENTION_TIME_DEFAULT,
        )
        smsCodeKeywords = AppPreferencesDataStore.getString(
            context,
            PrefConst.KEY_SMSCODE_KEYWORDS,
            PrefConst.SMSCODE_KEYWORDS_DEFAULT,
        )
        settingsViewModel.setInternalFilesWritable()
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            isActivated = ModuleUtils.isModuleEnabled() || ModuleActivationStore.isActivatedRecently(context)
            delay(1000L)
            isActivated = ModuleUtils.isModuleEnabled() || ModuleActivationStore.isActivatedRecently(context)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && pendingSavedToast) {
                Toast.makeText(context, context.getString(R.string.pref_sync_toast), Toast.LENGTH_SHORT).show()
                pendingSavedToast = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val markPrefsSaved = { pendingSavedToast = true }

    LaunchedEffect(settingsViewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            settingsViewModel.eventsFlow.collect { event ->
                when (event) {
                    is SettingsEvent.SmsCodeTestResult -> {
                        val text = if (event.code.isBlank()) {
                            context.getString(R.string.cannot_parse_smscode)
                        } else {
                            context.getString(R.string.current_sms_code, event.code)
                        }
                        snackbarHostState.showSnackbar(text, duration = SnackbarDuration.Long)
                    }

                    is SettingsEvent.ShowPrivacyPolicy -> {
                        showPrivacyPolicyDialog = true
                    }

                    is SettingsEvent.ShowAlipayPacket -> {
                        showDonateDialog = true
                    }

                    is SettingsEvent.BackupResultEvent -> {
                        val msg = if (event.success) R.string.backup_success else R.string.backup_failed
                        snackbarHostState.showSnackbar(context.getString(msg))
                    }

                    is SettingsEvent.RestoreResultEvent -> {
                        val msg = if (event.result.result == com.tianma.xsmscode.feature.backup.ImportResult.SUCCESS) {
                            R.string.restore_success
                        } else {
                            R.string.restore_failed
                        }
                        snackbarHostState.showSnackbar(context.getString(msg))

                        if (event.result.result == com.tianma.xsmscode.feature.backup.ImportResult.SUCCESS) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.restore_success),
                                Toast.LENGTH_SHORT,
                            ).show()
                            scope.launch {
                                delay(1200L)
                                val activity = activityOwner ?: (context as? Activity)
                                if (activity != null) {
                                    val intent = activity.packageManager.getLaunchIntentForPackage(
                                        activity.packageName,
                                    )
                                    if (intent != null) {
                                        intent.addFlags(
                                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
                                        )
                                        activity.startActivity(intent)
                                    }
                                    activity.finish()
                                }
                                android.os.Process.killProcess(android.os.Process.myPid())
                            }
                        }
                    }

                    is SettingsEvent.ImportDialogConfirm -> {
                        restoreUri = event.uri
                        showRestoreDialog = true
                    }

                    else -> Unit
                }
            }
        }
    }

    val scrollState = rememberScrollState()
    val showTopDivider by remember {
        derivedStateOf { scrollState.value > 0 }
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Box(modifier = Modifier.fillMaxSize()) {
        val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
            Const.TOP_BAR_HEIGHT.dp // TopBar height
        val isCompact = LocalConfiguration.current.screenWidthDp < 600
        val bottomPadding =
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                if (isCompact) Const.BOTTOM_SPACE_HEIGHT.dp else 0.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .padding(bottom = bottomPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(Const.SPACING_EXTRA_SMALL.dp),
        ) {
            // Add top padding manually as the first item or Spacer
            Spacer(modifier = Modifier.height(topPadding))
            SectionHeader(text = stringResource(id = R.string.pref_general_title))
            SwitchItem(
                title = stringResource(id = R.string.pref_enable_title),
                summary = stringResource(id = R.string.pref_enable_summary),
                key = PrefConst.KEY_ENABLE,
                defaultValue = true,
                onSaved = markPrefsSaved,
            )
            SwitchItem(
                title = stringResource(id = R.string.pref_hide_launcher_icon_title),
                summary = stringResource(id = R.string.pref_hide_launcher_icon_summary),
                key = PrefConst.KEY_HIDE_LAUNCHER_ICON,
                defaultValue = false,
                onToggle = { enabled -> settingsViewModel.hideOrShowLauncherIcon(enabled) },
                onSaved = markPrefsSaved,
            )
            Item(
                title = stringResource(id = R.string.pref_choose_theme_title),
                summary = stringResource(id = R.string.pref_choose_theme_summary),
            ) { showThemeDialog = true }

            var showLanguageDialog by remember { mutableStateOf(false) }
            Item(
                title = stringResource(id = R.string.pref_language_title),
                summary = stringResource(id = R.string.pref_language_summary),
            ) { showLanguageDialog = true }

            if (showLanguageDialog) {
                LanguageChooserDialog(
                    onDismiss = { showLanguageDialog = false },
                    onLanguageSelected = { tag ->
                        val locales = if (tag.isEmpty()) {
                            androidx.core.os.LocaleListCompat.getEmptyLocaleList()
                        } else {
                            androidx.core.os.LocaleListCompat.forLanguageTags(tag)
                        }
                        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(locales)
                        showLanguageDialog = false
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = Const.SPACING_SMALL.dp))

            SectionHeader(text = stringResource(id = R.string.pref_sms_code_title))
            SwitchItem(
                title = stringResource(id = R.string.pref_show_toast_title),
                summary = stringResource(id = R.string.pref_show_toast_summary),
                key = PrefConst.KEY_SHOW_TOAST,
                defaultValue = true,
                onSaved = markPrefsSaved,
            )
            SwitchItem(
                title = stringResource(id = R.string.pref_copy_to_clipboard_title),
                summary = stringResource(id = R.string.pref_copy_to_clipboard_summary),
                key = PrefConst.KEY_COPY_TO_CLIPBOARD,
                defaultValue = false,
                onSaved = markPrefsSaved,
            )
            SwitchItem(
                title = stringResource(id = R.string.pref_block_sms_title),
                summary = stringResource(id = R.string.pref_block_sms_summary),
                key = PrefConst.KEY_BLOCK_SMS,
                defaultValue = false,
                onSaved = markPrefsSaved,
            )
            SwitchItem(
                title = stringResource(id = R.string.pref_deduplicate_sms_title),
                summary = stringResource(id = R.string.pref_deduplicate_sms_summary),
                key = PrefConst.KEY_DEDUPLICATE_SMS,
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

            HorizontalDivider(modifier = Modifier.padding(vertical = Const.SPACING_SMALL.dp))

            SectionHeader(text = stringResource(id = R.string.pref_experimental_title))
            SwitchItem(
                title = stringResource(id = R.string.pref_mark_as_read_title),
                summary = stringResource(id = R.string.pref_mark_as_read_summary),
                key = PrefConst.KEY_MARK_AS_READ,
                defaultValue = false,
                onSaved = markPrefsSaved,
            )
            SwitchItem(
                title = stringResource(id = R.string.pref_delete_sms_title),
                summary = stringResource(id = R.string.pref_delete_sms_summary),
                key = PrefConst.KEY_DELETE_SMS,
                defaultValue = false,
                onSaved = markPrefsSaved,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = Const.SPACING_SMALL.dp))

            SectionHeader(text = stringResource(id = R.string.pref_category_auto_input_title))
            val autoInputEnabled = rememberPrefBoolean(PrefConst.KEY_ENABLE_AUTO_INPUT_CODE, true)
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

            HorizontalDivider(modifier = Modifier.padding(vertical = Const.SPACING_SMALL.dp))

            SectionHeader(text = stringResource(id = R.string.pref_notification_title))
            SwitchItem(
                title = stringResource(id = R.string.pref_show_code_notification_title),
                summary = stringResource(id = R.string.pref_show_code_notification_summary),
                key = PrefConst.KEY_SHOW_CODE_NOTIFICATION,
                defaultValue = true,
                onSaved = markPrefsSaved,
            )
            SwitchItem(
                title = stringResource(id = R.string.pref_auto_cancel_notification_title),
                summary = stringResource(id = R.string.pref_auto_cancel_notification_summary),
                key = PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION,
                defaultValue = false,
                onSaved = markPrefsSaved,
            )
            Item(
                title = stringResource(id = R.string.pref_notification_retention_time_title),
                summary = run {
                    val entries = stringArrayResource(id = R.array.notification_retention_time_entry_list)
                    val values = stringArrayResource(id = R.array.notification_retention_time_list)
                    val index = values.indexOf(retentionTime)
                    if (index >= 0) entries[index] else retentionTime
                },
            ) { showRetentionDialog = true }

            HorizontalDivider(modifier = Modifier.padding(vertical = Const.SPACING_SMALL.dp))

            SectionHeader(text = stringResource(id = R.string.pref_backup_restore_title))
            Item(
                title = stringResource(id = R.string.pref_backup_title),
                summary = stringResource(id = R.string.pref_backup_summary),
            ) { showBackupDialog = true }
            Item(
                title = stringResource(id = R.string.pref_restore_title),
                summary = stringResource(id = R.string.pref_restore_summary),
            ) {
                val intent = com.tianma.xsmscode.feature.backup.BackupManager.getImportRuleListSAFIntent(context)
                restoreLauncher.launch(intent)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = Const.SPACING_SMALL.dp))
            SectionHeader(text = stringResource(id = R.string.pref_others_title))
            SwitchItem(
                title = stringResource(id = R.string.pref_verbose_log_mode_title),
                summary = stringResource(id = R.string.pref_verbose_log_mode_summary),
                key = PrefConst.KEY_VERBOSE_LOG_MODE,
                defaultValue = false,
                onToggle = { on -> XLog.setLogLevel(if (on) Log.VERBOSE else BuildConfig.LOG_LEVEL) },
                onSaved = markPrefsSaved,
            )
            val autoUpdateEnabled = rememberPrefBoolean(PrefConst.KEY_AUTO_UPDATE_ON_START, true)
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

            Spacer(modifier = Modifier.height(Const.SPACING_SMALL.dp))
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter),
        ) {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.pref_general_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                scrollBehavior = scrollBehavior,
                windowInsets = WindowInsets.statusBars,
                modifier = Modifier
                    .hazeEffect(hazeState, hazeStyle) {
                        forceInvalidateOnPreDraw = true
                    },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )
    }

    if (showAutoInputDialog) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_auto_input_code_delay_title),
            initialValue = autoInputDelay,
            onDismiss = { showAutoInputDialog = false },
        ) { value ->
            autoInputDelay = value
            scope.launch {
                AppPreferencesDataStore.setString(context, PrefConst.KEY_AUTO_INPUT_CODE_DELAY, value)
                AppPreferencesDataStore.syncToSharedPrefs(context)
                pendingSavedToast = true
            }
            showAutoInputDialog = false
        }
    }

    if (showRetentionDialog) {
        RetentionDialog(
            selectedValue = retentionTime,
            onDismiss = { showRetentionDialog = false },
        ) { value ->
            retentionTime = value
            scope.launch {
                AppPreferencesDataStore.setString(context, PrefConst.KEY_NOTIFICATION_RETENTION_TIME, value)
                AppPreferencesDataStore.syncToSharedPrefs(context)
                pendingSavedToast = true
            }
            showRetentionDialog = false
        }
    }

    if (showSmsTestDialog) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_smscode_test_title),
            initialValue = smsTestInput,
            onDismiss = { showSmsTestDialog = false },
            singleLine = false,
            maxLines = 8,
        ) { value ->
            smsTestInput = value
            settingsViewModel.performSmsCodeTest(value)
            showSmsTestDialog = false
        }
    }

    if (showKeywordsDialog) {
        TextInputDialog(
            title = stringResource(id = R.string.pref_smscode_keywords_title),
            initialValue = smsCodeKeywords,
            onDismiss = { showKeywordsDialog = false },
            singleLine = false,
            maxLines = 10,
        ) { value ->
            val updated = if (value.isBlank()) PrefConst.SMSCODE_KEYWORDS_DEFAULT else value
            smsCodeKeywords = updated
            scope.launch {
                AppPreferencesDataStore.setString(context, PrefConst.KEY_SMSCODE_KEYWORDS, updated)
                AppPreferencesDataStore.syncToSharedPrefs(context)
                pendingSavedToast = true
            }
            showKeywordsDialog = false
        }
    }

    if (showThemeDialog) {
        ThemeChooserDialog(
            currentMode = themeMode,
            onDismiss = { showThemeDialog = false },
            onThemeSelected = { mode, x, y ->
                settingsViewModel.setThemeMode(mode, x, y)
                showThemeDialog = false
            },
        )
    }

    if (showDonateDialog) {
        DonateDialog(
            onDismiss = { showDonateDialog = false },
            onAlipay = {
                showDonateDialog = false
                showAlipayChoiceDialog = true
            },
            onWechat = {
                showDonateDialog = false
                showQRCodeDialog = Pair(R.drawable.wx, "wechat")
            },
        )
    }

    if (showAlipayChoiceDialog) {
        AlipayChoiceDialog(
            onDismiss = { showAlipayChoiceDialog = false },
            onQRCode = {
                showAlipayChoiceDialog = false
                showQRCodeDialog = Pair(R.drawable.alipay, "alipay")
            },
            onToken = {
                showAlipayChoiceDialog = false
                PackageUtils.copyAlipayPocketToken(context)
                PackageUtils.startAlipayActivity(context)
            },
        )
    }

    showQRCodeDialog?.let { pair ->
        QRCodeDialog(
            resId = pair.first,
            type = pair.second,
            onDismiss = { showQRCodeDialog = null },
            onSave = { Utils.saveImageToGallery(context, pair.first, "${pair.second}_qrcode") },
        )
    }

    if (showPrivacyPolicyDialog) {
        PrivacyPolicyDialog(
            onDismiss = { showPrivacyPolicyDialog = false },
            onConfirm = {
                scope.launch { SPUtils.setPrivacyPolicyAccepted(context, true) }
                showPrivacyPolicyDialog = false
            },
            onCancel = {
                scope.launch { SPUtils.setPrivacyPolicyAccepted(context, false) }
                showPrivacyPolicyDialog = false
                onExit()
            },
            onViewPolicy = {
                showPrivacyPolicyPage = true
            },
        )
    }

    if (showPrivacyPolicyPage) {
        PrivacyPolicyPage(onDismiss = { showPrivacyPolicyPage = false })
    }

    if (showBackupDialog) {
        BackupDialog(
            onDismiss = { showBackupDialog = false },
            onConfirm = { config, rules, records ->
                backupFlags = Triple(config, rules, records)
                showBackupDialog = false
                val intent = com.tianma.xsmscode.feature.backup.BackupManager.getExportRuleListSAFIntent(context)
                backupLauncher.launch(intent)
            },
        )
    }

    if (showRestoreDialog && restoreUri != null) {
        RestoreConfirmDialog(
            onDismiss = { showRestoreDialog = false },
            onConfirm = { config, rules, records ->
                settingsViewModel.performRestore(restoreUri!!, config, rules, records)
                showRestoreDialog = false
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
fun Item(title: String, summary: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text = title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = if (summary.isNotEmpty()) {
            {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            null
        },
        modifier = modifier.clickable(onClick = onClick),
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
    onToggle: ((Boolean) -> Unit)? = null,
    onSaved: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val checkedState = stateOverride ?: rememberPrefBoolean(key, defaultValue)

    fun toggle(checked: Boolean) {
        checkedState.value = checked
        scope.launch {
            AppPreferencesDataStore.setBoolean(context, key, checked)
            AppPreferencesDataStore.syncToSharedPrefs(context)
            onSaved?.invoke()
        }
        onToggle?.invoke(checked)
    }

    ListItem(
        headlineContent = { Text(text = title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = if (summary.isNotEmpty()) {
            {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            null
        },
        trailingContent = {
            Switch(checked = checkedState.value, onCheckedChange = { toggle(it) })
        },
        modifier = modifier.clickable { toggle(!checkedState.value) },
    )
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
fun TextInputDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 6,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(text = title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = singleLine,
                maxLines = maxLines,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(stringResource(id = R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        },
    )
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
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(id = titleId)) },
        text = {
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
                        RadioButton(selected = value == selectedValue, onClick = { onConfirm(value) })
                        Text(text = entry, modifier = Modifier.padding(start = Const.SPACING_MEDIUM.dp))
                    }
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
fun ThemeChooserDialog(currentMode: Int, onDismiss: () -> Unit, onThemeSelected: (Int, Float, Float) -> Unit) {
    val modes = listOf(
        stringResource(id = R.string.theme_follow_system) to 0,
        stringResource(id = R.string.theme_light) to 1,
        stringResource(id = R.string.theme_dark) to 2,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.pref_choose_theme_title)) },
        text = {
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
                        RadioButton(selected = mode == currentMode, onClick = null)
                        Text(text = label, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
fun LanguageChooserDialog(onDismiss: () -> Unit, onLanguageSelected: (String) -> Unit) {
    val context = LocalContext.current
    val currentLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
    val currentTag = if (currentLocales.isEmpty) "" else currentLocales.get(0)?.toLanguageTag() ?: ""

    val languages = listOf(
        stringResource(id = R.string.language_follow_system) to "",
        stringResource(id = R.string.language_en) to "en",
        stringResource(id = R.string.language_zh_cn) to "zh-CN",
        stringResource(id = R.string.language_zh_tw) to "zh-TW",
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.pref_language_title)) },
        text = {
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
                        RadioButton(
                            selected = selected,
                            onClick = { onLanguageSelected(tag) },
                        )
                        Text(text = label, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
fun DonateDialog(onDismiss: () -> Unit, onAlipay: () -> Unit, onWechat: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.dialog_donate_title)) },
        text = { Text(stringResource(id = R.string.dialog_donate_content)) },
        confirmButton = {
            TextButton(onClick = onAlipay) { Text(stringResource(id = R.string.dialog_donate_alipay)) }
            TextButton(onClick = onWechat) { Text(stringResource(id = R.string.dialog_donate_wechat)) }
        },
    )
}

@Composable
fun AlipayChoiceDialog(onDismiss: () -> Unit, onQRCode: () -> Unit, onToken: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.dialog_donate_alipay)) },
        confirmButton = {
            TextButton(onClick = onQRCode) { Text(stringResource(id = R.string.dialog_donate_alipay_qrcode)) }
            TextButton(onClick = onToken) { Text(stringResource(id = R.string.dialog_donate_alipay_token)) }
        },
    )
}

@Composable
fun QRCodeDialog(resId: Int, type: String, onDismiss: () -> Unit, onSave: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (type == "alipay") {
                    stringResource(
                        id = R.string.dialog_donate_alipay,
                    )
                } else {
                    stringResource(id = R.string.dialog_donate_wechat)
                },
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = resId),
                    contentDescription = if (type == "alipay") {
                        stringResource(
                            id = R.string.dialog_donate_alipay,
                        )
                    } else {
                        stringResource(id = R.string.dialog_donate_wechat)
                    },
                    modifier = Modifier.size(200.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text(stringResource(id = R.string.save_to_gallery)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.cancel)) }
        },
    )
}

@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit, onConfirm: () -> Unit, onCancel: () -> Unit, onViewPolicy: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.privacy_dialog_title)) },
        text = {
            Column {
                Text(stringResource(id = R.string.privacy_dialog_content))
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = onViewPolicy,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(stringResource(id = R.string.privacy_policy_button))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(id = R.string.privacy_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(id = R.string.privacy_dialog_cancel))
            }
        },
    )
}

@Composable
fun BackupDialog(onDismiss: () -> Unit, onConfirm: (Boolean, Boolean, Boolean) -> Unit) {
    var checkConfig by remember { mutableStateOf(true) }
    var checkRules by remember { mutableStateOf(true) }
    var checkRecords by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.dialog_backup_title)) },
        text = {
            Column {
                Text(stringResource(id = R.string.dialog_backup_msg), modifier = Modifier.padding(bottom = 8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { checkConfig = !checkConfig },
                ) {
                    Checkbox(checked = checkConfig, onCheckedChange = { checkConfig = it })
                    Text(stringResource(id = R.string.item_config))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { checkRules = !checkRules },
                ) {
                    Checkbox(checked = checkRules, onCheckedChange = { checkRules = it })
                    Text(stringResource(id = R.string.item_rules))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { checkRecords = !checkRecords },
                ) {
                    Checkbox(checked = checkRecords, onCheckedChange = { checkRecords = it })
                    Text(stringResource(id = R.string.item_records))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(checkConfig, checkRules, checkRecords) }) {
                Text(stringResource(id = R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        },
    )
}

@Composable
fun RestoreConfirmDialog(onDismiss: () -> Unit, onConfirm: (Boolean, Boolean, Boolean) -> Unit) {
    var checkConfig by remember { mutableStateOf(true) }
    var checkRules by remember { mutableStateOf(true) }
    var checkRecords by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.dialog_restore_title)) },
        text = {
            Column {
                Text(stringResource(id = R.string.dialog_restore_msg), modifier = Modifier.padding(bottom = 8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { checkConfig = !checkConfig },
                ) {
                    Checkbox(checked = checkConfig, onCheckedChange = { checkConfig = it })
                    Text(stringResource(id = R.string.item_config))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { checkRules = !checkRules },
                ) {
                    Checkbox(checked = checkRules, onCheckedChange = { checkRules = it })
                    Text(stringResource(id = R.string.item_rules))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { checkRecords = !checkRecords },
                ) {
                    Checkbox(checked = checkRecords, onCheckedChange = { checkRecords = it })
                    Text(stringResource(id = R.string.item_records))
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
            TextButton(onClick = { onConfirm(checkConfig, checkRules, checkRecords) }) {
                Text(stringResource(id = R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        },
    )
}
