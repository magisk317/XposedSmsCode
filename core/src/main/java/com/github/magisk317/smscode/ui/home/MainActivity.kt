@file:Suppress("LocalContextGetResourceValueCall")

package com.github.magisk317.smscode.ui.home

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import com.github.magisk317.smscode.common.utils.HookPreferenceMirror
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import com.github.magisk317.smscode.core.BuildConfig
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.common.constant.Const
import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.common.constant.TransitionConst
import com.github.magisk317.smscode.common.utils.AppPreferencesDataStore
import io.github.magisk317.smscode.runtime.common.utils.FrameworkCompatibilityMonitor
import com.github.magisk317.smscode.runtime.RuntimePrefsFacade as PrefsReader
import com.github.magisk317.smscode.common.utils.XLog
import com.github.magisk317.smscode.common.utils.SPUtils
import com.github.magisk317.smscode.common.utils.PackageUtils
import io.github.magisk317.smscode.runtime.common.utils.StringUtils
import com.github.magisk317.smscode.common.utils.Utils
import com.github.magisk317.smscode.runtime.RuntimeGithubReleaseInfo
import com.github.magisk317.smscode.runtime.RuntimeStartupTarget
import com.github.magisk317.smscode.runtime.RuntimeUpgradeApkAsset
import com.github.magisk317.smscode.runtime.RuntimeUpgradeCheckResult
import com.github.magisk317.smscode.runtime.RuntimeUpgradeInfo
import com.github.magisk317.smscode.runtime.RuntimeUpdateFacade
import com.github.magisk317.smscode.runtime.RuntimeUpgradeDownloadProgress
import com.github.magisk317.smscode.ui.app.base.UpdateSystemBars
import com.github.magisk317.smscode.ui.app.base.applyEdgeToEdge
import com.github.magisk317.smscode.ui.app.base.rememberHazeStyle
import io.github.magisk317.uikit.common.DismissibleSnackbarHost
import io.github.magisk317.uikit.foundation.LocalSnackbarHostState
import com.github.magisk317.smscode.ui.home.update.FlavorPlayUpdateDelegate
import com.github.magisk317.smscode.ui.home.update.PlayUpdateDelegate
import com.github.magisk317.smscode.ui.nav.SmsCodeNavHost
import com.github.magisk317.smscode.ui.privacy.PrivacyPolicyPage
import com.github.magisk317.smscode.ui.theme.AppTheme
import dev.chrisbanes.haze.HazeState
import io.github.magisk317.uikit.surface.AppAlertDialog
import io.github.magisk317.uikit.surface.AppLinearProgressIndicator
import io.github.magisk317.uikit.surface.AppPrimaryButton
import io.github.magisk317.uikit.surface.AppSecondaryButton
import io.github.magisk317.uikit.surface.AppTextButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import org.koin.androidx.compose.koinViewModel
import java.io.File
import kotlin.math.hypot

class MainActivity : ComponentActivity() {

    private val playUpdateDelegate: PlayUpdateDelegate = FlavorPlayUpdateDelegate()
    private var autoUpdateChecked = false
    private val snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private fun enqueueSnackbar(message: String) {
        snackbarMessages.tryEmit(message)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    @Suppress("CyclomaticComplexMethod")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.w("XSmsCode", "MainActivity.onCreate() called pid=${android.os.Process.myPid()}")
        applyEdgeToEdge(this)
        playUpdateDelegate.onCreate(this) {
            PackageUtils.openPlayStoreOrGithub(this)?.let(::enqueueSnackbar)
        }
        triggerAutoUpdateIfEnabled()

        setContent {
            val viewModel: SettingsViewModel = koinViewModel()
            val themeState by viewModel.themeState.collectAsStateWithLifecycle()
            val navController = rememberNavController()
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val appSnackbarHostState = remember { SnackbarHostState() }
            var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
            var showPrivacyPolicyPage by remember { mutableStateOf(false) }
            var blockingStartupDialog by remember { mutableStateOf<BlockingStartupDialog?>(null) }
            var startupBlockingCheckComplete by remember { mutableStateOf(false) }
            var githubUpdateUiState by remember { mutableStateOf<GithubUpdateUiState?>(null) }
            var downloadState by remember { mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle) }
            var unknownSourceApk by remember { mutableStateOf<File?>(null) }
            var downloadJob by remember { mutableStateOf<Job?>(null) }
            var snackbarBottomOverlayPadding by remember { mutableStateOf(0.dp) }

            fun startStructuredDownload(update: GithubStructuredUpdate) {
                downloadJob?.cancel()
                downloadState = UpdateDownloadState.Downloading(progress = 0f, progressText = "0%")
                downloadJob = scope.launch {
                    try {
                        val downloadedFile = RuntimeUpdateFacade.download(
                            context = this@MainActivity,
                            versionCode = update.info.versionCode,
                            asset = update.asset,
                        ) { progress ->
                            runOnUiThread {
                                downloadState = UpdateDownloadState.Downloading(
                                    progress = progress.percent,
                                    progressText = formatDownloadProgress(progress),
                                )
                            }
                        }
                        val verifyResult = RuntimeUpdateFacade.verifyDownloadedApk(
                            context = this@MainActivity,
                            apkFile = downloadedFile,
                            expectedSha256 = update.asset.sha256,
                            expectedSigningCertSha256 = update.info.signingCertSha256,
                        )
                        if (!verifyResult.success) {
                            runCatching { downloadedFile.delete() }
                            downloadState = UpdateDownloadState.Failed(
                                message = getString(
                                    R.string.update_security_check_failed,
                                    verifyResult.reason ?: "unknown",
                                ),
                                retry = update,
                            )
                            return@launch
                        }
                        downloadState = UpdateDownloadState.Downloaded(
                            file = downloadedFile,
                            update = update,
                        )
                    } catch (_: CancellationException) {
                        downloadState = UpdateDownloadState.Idle
                    } catch (t: Throwable) {
                        downloadState = UpdateDownloadState.Failed(
                            message = t.message ?: t.javaClass.simpleName,
                            retry = update,
                        )
                    } finally {
                        downloadJob = null
                    }
                }
            }

            // Circular Reveal Animation State
            var currentThemeMode by remember { mutableIntStateOf(themeState.mode) }
            var currentUiKitStyle by remember { mutableIntStateOf(themeState.uiKitStyle) }
            var screenshotBitmap by remember { mutableStateOf<Bitmap?>(null) }
            val revealAnim = remember { Animatable(0f) }
            var isAnimating by remember { mutableStateOf(false) }
            var animationCenter by remember { mutableStateOf(Offset.Zero) }
            val view = LocalView.current
            var requestedTab by remember { mutableStateOf<Any?>(null) }

            fun clearScreenshotBitmap() {
                screenshotBitmap?.let { bitmap ->
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
                screenshotBitmap = null
            }

            LaunchedEffect(Unit) {
                if (!SPUtils.isPrivacyPolicyAccepted(context)) {
                    showPrivacyPolicyDialog = true
                }
            }
            LaunchedEffect(Unit) {
                startupBlockingCheckComplete = false
                try {
                    if (BuildConfig.ALLOW_CONFLICT_BYPASS) {
                        XLog.w(
                            "Relay conflict guard bypassed by build flag allowConflictBypass=true",
                        )
                    } else if (TransitionConst.isRelayInstalled(context)) {
                        blockingStartupDialog = BlockingStartupDialog.RelayConflict
                        return@LaunchedEffect
                    }
                    val frameworkIssue = withContext(Dispatchers.IO) {
                        PackageUtils.inspectFrameworkIssue(context)
                    }
                    if (frameworkIssue != null) {
                        blockingStartupDialog = BlockingStartupDialog.FrameworkIncompatibility(frameworkIssue)
                    }
                } finally {
                    startupBlockingCheckComplete = true
                }
            }
            LaunchedEffect(Unit) {
                githubUpdateUiState = checkStartupGithubUpdateIfNeeded()
            }
            LaunchedEffect(Unit) {
                snackbarMessages.collect { message ->
                    appSnackbarHostState.showSnackbar(message)
                }
            }

            // Effect to trigger logic when ThemeState changes
            LaunchedEffect(themeState) {
                if (themeState.mode != currentThemeMode) {
                    val width = view.width
                    val height = view.height
                    val pixelCount = width.toLong() * height.toLong()
                    val exceedsLimits = width <= 0 ||
                        height <= 0 ||
                        width > MAX_CAPTURE_SIDE_PX ||
                        height > MAX_CAPTURE_SIDE_PX ||
                        pixelCount > MAX_CAPTURE_PIXELS
                    if (exceedsLimits) {
                        XLog.w(
                            "Skip theme capture due to size: width=%d height=%d pixels=%d",
                            width,
                            height,
                            pixelCount,
                        )
                        clearScreenshotBitmap()
                        isAnimating = false
                        currentThemeMode = themeState.mode
                        return@LaunchedEffect
                    }

                    try {
                        clearScreenshotBitmap()
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bitmap)
                        view.draw(canvas)
                        screenshotBitmap = bitmap

                        val centerX = if (themeState.centerX >= 0) themeState.centerX else width / 2f
                        val centerY = if (themeState.centerY >= 0) themeState.centerY else height / 2f
                        animationCenter = Offset(centerX, centerY)

                        isAnimating = true
                        currentThemeMode = themeState.mode
                        currentUiKitStyle = themeState.uiKitStyle

                        revealAnim.snapTo(0f)
                        revealAnim.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 600),
                        )
                    } catch (oom: OutOfMemoryError) {
                        XLog.w("Theme capture OOM, fallback to direct mode switch", oom)
                        currentThemeMode = themeState.mode
                        currentUiKitStyle = themeState.uiKitStyle
                    } catch (e: RuntimeException) {
                        if (e.message?.contains(LARGE_BITMAP_ERROR_KEYWORD, ignoreCase = true) == true) {
                            XLog.w("Theme capture too large bitmap, fallback to direct mode switch")
                        } else {
                            XLog.w("Theme capture runtime exception: %s", e.message ?: "unknown")
                        }
                        currentThemeMode = themeState.mode
                        currentUiKitStyle = themeState.uiKitStyle
                    } catch (t: Throwable) {
                        XLog.w("Theme capture failed: %s", t.message ?: "unknown")
                        currentThemeMode = themeState.mode
                        currentUiKitStyle = themeState.uiKitStyle
                    } finally {
                        isAnimating = false
                        clearScreenshotBitmap()
                    }
                } else if (themeState.uiKitStyle != currentUiKitStyle) {
                    currentUiKitStyle = themeState.uiKitStyle
                } else {
                    // Initial load
                    currentThemeMode = themeState.mode
                    currentUiKitStyle = themeState.uiKitStyle
                }
            }

            // Collect navigation events
            LaunchedEffect(viewModel.eventsFlow) {
                viewModel.eventsFlow.collect { event ->
                    when (event) {
                        is SettingsEvent.ShowPrivacyPolicy -> showPrivacyPolicyDialog = true
                        is SettingsEvent.SmsCodeTestResult -> {
                            val message = if (event.code.isBlank()) {
                                context.getString(R.string.cannot_parse_smscode)
                            } else {
                                val base = context.getString(R.string.current_sms_code, event.code)
                                val hitRule = event.matchedRuleLabel?.takeIf { it.isNotBlank() }?.let {
                                    context.getString(R.string.hit_rule_label, it)
                                }
                                if (hitRule == null) {
                                    base
                                } else {
                                    context.getString(R.string.sms_code_test_result_with_rule, base, hitRule)
                                }
                            }
                            XLog.i(
                                "Sms code test result delivered in MainActivity: code=%s matchedRule=%s",
                                if (PrefsReader.isSensitiveDebugLogMode(context)) {
                                    StringUtils.escape(event.code)
                                } else {
                                    StringUtils.summarizeCode(event.code)
                                },
                                event.matchedRuleLabel ?: "",
                            )
                            scope.launch { appSnackbarHostState.showSnackbar(message) }
                        }
                        is SettingsEvent.NavigateToRules -> {
                            requestedTab = com.github.magisk317.smscode.ui.nav.SmsCodeRulesRoute()
                        }
                        is SettingsEvent.NavigateToRecords -> requestedTab = com.github.magisk317.smscode.ui.nav.RecordsRoute
                        is SettingsEvent.NavigateToSettings -> requestedTab = com.github.magisk317.smscode.ui.nav.SettingsRoute
                        is SettingsEvent.StartPlayUpdate -> requestPlayUpdate()
                        is SettingsEvent.StartGithubUpdateCheck -> {
                            requestGithubUpdateCheck(showNoUpdateSnackbar = true) { update ->
                                githubUpdateUiState = update
                            }
                        }
                        is SettingsEvent.ShowSnackbar -> {
                            scope.launch { appSnackbarHostState.showSnackbar(event.message) }
                        }
                        else -> {}
                    }
                }
            }

            CompositionLocalProvider(LocalSnackbarHostState provides appSnackbarHostState) {
                AppTheme(
                    themeMode = currentThemeMode,
                    uiKitStyle = currentUiKitStyle,
                ) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                    LaunchedEffect(Unit) {
                        viewModel.setInternalFilesWritable()
                    }
                    LaunchedEffect(intent) {
                        viewModel.handleArguments(intent.extras)
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        val shouldShowRegularUi = startupBlockingCheckComplete && blockingStartupDialog == null
                        val hazeBlurRadius by AppPreferencesDataStore.getIntFlow(
                            context,
                            PrefConst.KEY_HAZE_BLUR_RADIUS,
                            PrefConst.HAZE_BLUR_RADIUS_DEFAULT,
                        ).collectAsStateWithLifecycle(initialValue = PrefConst.HAZE_BLUR_RADIUS_DEFAULT)

                        val hazeTintAlpha by AppPreferencesDataStore.getFloatFlow(
                            context,
                            PrefConst.KEY_HAZE_TINT_ALPHA,
                            PrefConst.HAZE_TINT_ALPHA_DEFAULT,
                        ).collectAsStateWithLifecycle(initialValue = PrefConst.HAZE_TINT_ALPHA_DEFAULT)

                        val hazeState = remember { HazeState() }
                        val hazeStyle = rememberHazeStyle(blurRadius = hazeBlurRadius.dp, tintAlpha = hazeTintAlpha)
                        if (shouldShowRegularUi) {
                            SmsCodeNavHost(
                                navController = navController,
                                onBack = { finish() },
                                initialTab = requestedTab,
                                onInitialTabConsumed = { requestedTab = null },
                                onBottomOverlayPaddingChanged = { snackbarBottomOverlayPadding = it },
                                modifier = Modifier,
                                hazeState = hazeState,
                                hazeStyle = hazeStyle,
                            )
                        }

                        if (shouldShowRegularUi && showPrivacyPolicyDialog) {
                            PrivacyPolicyDialog(
                                onDismiss = {},
                                onConfirm = {
                                    scope.launch { SPUtils.setPrivacyPolicyAccepted(context, true) }
                                    showPrivacyPolicyDialog = false
                                },
                                onCancel = {
                                    scope.launch { SPUtils.setPrivacyPolicyAccepted(context, false) }
                                    showPrivacyPolicyDialog = false
                                    finish()
                                },
                                onViewPolicy = {
                                    showPrivacyPolicyDialog = false
                                    showPrivacyPolicyPage = true
                                },
                                dismissOnBackPress = false,
                                dismissOnClickOutside = false,
                            )
                        }

                        if (shouldShowRegularUi && showPrivacyPolicyPage) {
                            PrivacyPolicyPage(
                                onDismiss = {
                                    showPrivacyPolicyPage = false
                                    scope.launch {
                                        if (!SPUtils.isPrivacyPolicyAccepted(context)) {
                                            showPrivacyPolicyDialog = true
                                        }
                                    }
                                },
                            )
                        }

                        blockingStartupDialog?.let { dialog ->
                            ExitOnlyConflictDialog(
                                title = when (dialog) {
                                    BlockingStartupDialog.RelayConflict ->
                                        getString(R.string.relay_conflict_dialog_title)
                                    is BlockingStartupDialog.FrameworkIncompatibility ->
                                        getString(R.string.framework_incompatibility_title)
                                },
                                text = when (dialog) {
                                    BlockingStartupDialog.RelayConflict -> {
                                        buildAnnotatedString {
                                            append(getString(R.string.relay_conflict_dialog_prefix))
                                            withStyle(
                                                SpanStyle(
                                                    color = MaterialTheme.colorScheme.error,
                                                    fontWeight = FontWeight.Bold,
                                                ),
                                            ) {
                                                append(getString(R.string.relay_conflict_other_app_name))
                                            }
                                            append(" (")
                                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                                append(TransitionConst.TARGET_RELAY_PACKAGE)
                                            }
                                            append(")")
                                            append(getString(R.string.relay_conflict_dialog_middle))
                                            withStyle(
                                                SpanStyle(
                                                    color = MaterialTheme.colorScheme.error,
                                                    fontWeight = FontWeight.Bold,
                                                ),
                                            ) {
                                                append(getString(R.string.app_name))
                                            }
                                            append(getString(R.string.relay_conflict_dialog_suffix))
                                        }
                                    }
                                    is BlockingStartupDialog.FrameworkIncompatibility -> {
                                        val issue = dialog.issue
                                        buildAnnotatedString {
                                            append(
                                                when (issue.issueType) {
                                                    FrameworkCompatibilityMonitor.FrameworkIssueType.HOOKER_ANNOTATION_INCOMPATIBLE ->
                                                        getString(R.string.framework_incompatibility_hooker_annotation_message)
                                                },
                                            )
                                        }
                                    }
                                },
                                confirmText = getString(R.string.relay_conflict_dialog_exit),
                                onExit = {
                                    blockingStartupDialog = null
                                    finish()
                                },
                            )
                        }

                        if (shouldShowRegularUi) {
                            githubUpdateUiState?.let { updateState ->
                            AppAlertDialog(
                                onDismissRequest = { githubUpdateUiState = null },
                                title = { Text(getString(R.string.github_update_dialog_title)) },
                                text = {
                                    Text(
                                        text = buildUpdateDialogText(updateState),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 400.dp)
                                            .verticalScroll(rememberScrollState()),
                                    )
                                },
                                confirmButton = {
                                    AppPrimaryButton(
                                        text = getString(R.string.github_update_download),
                                        onClick = {
                                            when (updateState) {
                                                is GithubUpdateUiState.Legacy -> {
                                                    Utils.showWebPage(
                                                        this@MainActivity,
                                                        updateState.release.htmlUrl,
                                                    )?.let(::enqueueSnackbar)
                                                    githubUpdateUiState = null
                                                }

                                                is GithubUpdateUiState.Structured -> {
                                                    githubUpdateUiState = null
                                                    startStructuredDownload(updateState.update)
                                                }
                                            }
                                        },
                                    )
                                },
                                dismissButton = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        AppSecondaryButton(
                                            text = getString(R.string.github_update_ignore_this_version),
                                            onClick = {
                                                val versionName = when (updateState) {
                                                    is GithubUpdateUiState.Legacy -> updateState.release.versionName
                                                    is GithubUpdateUiState.Structured -> updateState.update.info.versionName
                                                }
                                                lifecycleScope.launch {
                                                    AppPreferencesDataStore.setString(
                                                        this@MainActivity,
                                                        PrefConst.KEY_GITHUB_IGNORED_VERSION,
                                                        versionName,
                                                    )
                                                    HookPreferenceMirror.publish(this@MainActivity)
                                                }
                                                githubUpdateUiState = null
                                            },
                                        )
                                        AppSecondaryButton(
                                            text = getString(R.string.cancel),
                                            onClick = { githubUpdateUiState = null },
                                        )
                                    }
                                },
                            )
                        }
                        }

                        when (val state = downloadState) {
                            is UpdateDownloadState.Downloading -> {
                                AppAlertDialog(
                                    onDismissRequest = {},
                                    title = { Text(getString(R.string.update_download_in_progress_title)) },
                                    text = {
                                        Column {
                                            AppLinearProgressIndicator(progress = state.progress, modifier = Modifier.fillMaxWidth())
                                            Text(state.progressText)
                                        }
                                    },
                                    confirmButton = {
                                        AppTextButton(
                                            text = getString(R.string.update_download_cancel),
                                            onClick = {
                                                downloadJob?.cancel()
                                                downloadState = UpdateDownloadState.Idle
                                            },
                                        )
                                    },
                                )
                            }

                            is UpdateDownloadState.Failed -> {
                                AppAlertDialog(
                                    onDismissRequest = { downloadState = UpdateDownloadState.Idle },
                                    title = { Text(getString(R.string.update_download_failed_title)) },
                                    text = { Text(state.message) },
                                    dismissButton = {
                                        AppSecondaryButton(
                                            text = getString(R.string.cancel),
                                            onClick = { downloadState = UpdateDownloadState.Idle },
                                        )
                                    },
                                    confirmButton = {
                                        if (state.retry != null) {
                                            AppPrimaryButton(
                                                text = getString(R.string.update_retry),
                                                onClick = { startStructuredDownload(state.retry) },
                                            )
                                        }
                                    },
                                )
                            }

                            is UpdateDownloadState.Downloaded -> {
                                AppAlertDialog(
                                    onDismissRequest = {},
                                    title = { Text(getString(R.string.update_download_completed_title)) },
                                    text = { Text(getString(R.string.update_download_completed_message)) },
                                    dismissButton = {
                                        AppSecondaryButton(
                                            text = getString(R.string.cancel),
                                            onClick = { downloadState = UpdateDownloadState.Idle },
                                        )
                                    },
                                    confirmButton = {
                                        AppPrimaryButton(
                                            text = getString(R.string.update_install),
                                            onClick = {
                                                if (!RuntimeUpdateFacade.canRequestPackageInstalls(this@MainActivity)) {
                                                    unknownSourceApk = state.file
                                                } else {
                                                    val installResult = RuntimeUpdateFacade.installApk(this@MainActivity, state.file)
                                                    if (installResult.isSuccess) {
                                                        downloadState = UpdateDownloadState.Idle
                                                    } else {
                                                        downloadState = UpdateDownloadState.Failed(
                                                            message = installResult.exceptionOrNull()?.message
                                                                ?: "install_failed",
                                                            retry = state.update,
                                                        )
                                                    }
                                                }
                                            },
                                        )
                                    },
                                )
                            }

                            UpdateDownloadState.Idle -> Unit
                        }

                        unknownSourceApk?.let {
                            AppAlertDialog(
                                onDismissRequest = { unknownSourceApk = null },
                                title = { Text(getString(R.string.update_unknown_source_title)) },
                                text = { Text(getString(R.string.update_unknown_source_message)) },
                                dismissButton = {
                                    AppSecondaryButton(
                                        text = getString(R.string.cancel),
                                        onClick = { unknownSourceApk = null },
                                    )
                                },
                                confirmButton = {
                                    AppPrimaryButton(
                                        text = getString(R.string.update_open_settings),
                                        onClick = {
                                            startActivity(RuntimeUpdateFacade.buildUnknownSourceSettingsIntent(this@MainActivity))
                                            unknownSourceApk = null
                                        },
                                    )
                                },
                            )
                        }

                        // Overlay for Circular Reveal
                        if (isAnimating && screenshotBitmap != null && !screenshotBitmap!!.isRecycled) {
                            val bitmap = screenshotBitmap!!.asImageBitmap()
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        // Use Offscreen to allow BlendMode.Clear to punch a hole
                                        compositingStrategy = CompositingStrategy.Offscreen
                                    }
                                    .drawWithContent {
                                        drawContent() // Draw the Old Screenshot

                                        // Calculate specific radius for time t
                                        val maxRadius = hypot(size.width.toDouble(), size.height.toDouble()).toFloat()
                                        val radius = maxRadius * revealAnim.value

                                        // Draw a transparent circle to reveal the new content underneath
                                        drawCircle(
                                            color = androidx.compose.ui.graphics.Color.Transparent,
                                            radius = radius,
                                            center = animationCenter,
                                            blendMode = BlendMode.Clear,
                                        )
                                    },
                            )
                        }
                        DismissibleSnackbarHost(
                            hostState = appSnackbarHostState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .then(
                                    if (snackbarBottomOverlayPadding > 0.dp) {
                                        Modifier.padding(bottom = snackbarBottomOverlayPadding)
                                    } else {
                                        Modifier.navigationBarsPadding()
                                    },
                                ),
                        )
                    }
                }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        playUpdateDelegate.onResume(this) {
            PackageUtils.openPlayStoreOrGithub(this)?.let(::enqueueSnackbar)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playUpdateDelegate.onDestroy()
    }

    private fun requestPlayUpdate() {
        requestPlayUpdateInternal(silentIfNoUpdate = false, fallbackOnQueryFailure = true)
    }

    private fun requestPlayUpdateInternal(silentIfNoUpdate: Boolean, fallbackOnQueryFailure: Boolean) {
        playUpdateDelegate.requestUpdate(
            activity = this,
            silentIfNoUpdate = silentIfNoUpdate,
            fallbackOnQueryFailure = fallbackOnQueryFailure,
        ) {
            PackageUtils.openPlayStoreOrGithub(this)?.let(::enqueueSnackbar)
        }
    }

    private fun triggerAutoUpdateIfEnabled() {
        if (autoUpdateChecked) return
        autoUpdateChecked = true

        lifecycleScope.launch {
            val enabled = AppPreferencesDataStore.getBoolean(
                this@MainActivity,
                PrefConst.KEY_AUTO_UPDATE_ON_START,
                true,
            )
            if (!enabled) return@launch

            val wifiOnly = AppPreferencesDataStore.getBoolean(
                this@MainActivity,
                PrefConst.KEY_AUTO_UPDATE_WIFI_ONLY,
                false,
            )
            val onWifi = PackageUtils.isOnWifi(this@MainActivity)
            if (!RuntimeUpdateFacade.shouldRunAutoCheck(enabled, wifiOnly, onWifi)) return@launch

            when (RuntimeUpdateFacade.resolveStartupTarget(PackageUtils.isInstalledFromPlay(this@MainActivity))) {
                RuntimeStartupTarget.PLAY -> {
                    requestPlayUpdateInternal(silentIfNoUpdate = true, fallbackOnQueryFailure = false)
                }
                RuntimeStartupTarget.GITHUB -> {
                    // Startup GitHub check is handled by checkStartupGithubUpdateIfNeeded()
                }
            }
        }
    }

    private suspend fun checkStartupGithubUpdateIfNeeded(): GithubUpdateUiState? {
        if (!autoUpdateChecked) triggerAutoUpdateIfEnabled()
        val result = findGithubUpdate(
            isAutoCheck = true,
            respectIgnoredVersion = true,
        )
        return when (result) {
            is GithubUpdateQueryResult.Available -> result.update
            else -> null
        }
    }

    private fun requestGithubUpdateCheck(
        showNoUpdateSnackbar: Boolean,
        onUpdateFound: (GithubUpdateUiState) -> Unit,
    ) {
        lifecycleScope.launch {
            when (
                val result = findGithubUpdate(
                    isAutoCheck = false,
                    respectIgnoredVersion = false,
                )
            ) {
                is GithubUpdateQueryResult.Failed -> {
                    enqueueSnackbar(getString(R.string.check_update_failed))
                }

                GithubUpdateQueryResult.NoUpdate -> {
                    if (showNoUpdateSnackbar) {
                        enqueueSnackbar(getString(R.string.app_already_newest))
                    }
                }

                is GithubUpdateQueryResult.Available -> {
                    onUpdateFound(result.update)
                }
            }
        }
    }

    private suspend fun findGithubUpdate(
        isAutoCheck: Boolean,
        respectIgnoredVersion: Boolean,
    ): GithubUpdateQueryResult {
        val installedFromPlay = PackageUtils.isInstalledFromPlay(this)
        if (isAutoCheck) {
            val enabled = AppPreferencesDataStore.getBoolean(
                this,
                PrefConst.KEY_AUTO_UPDATE_ON_START,
                true,
            )
            if (!enabled) return GithubUpdateQueryResult.NoUpdate

            val wifiOnly = AppPreferencesDataStore.getBoolean(
                this,
                PrefConst.KEY_AUTO_UPDATE_WIFI_ONLY,
                false,
            )
            val onWifi = PackageUtils.isOnWifi(this)
            if (
                RuntimeUpdateFacade.shouldSkipGithubCheckOnStartup(
                    installedFromPlay = installedFromPlay,
                    autoCheckEnabled = enabled,
                    wifiOnly = wifiOnly,
                    onWifi = onWifi,
                )
            ) {
                return GithubUpdateQueryResult.NoUpdate
            }
        } else if (installedFromPlay) {
            return GithubUpdateQueryResult.NoUpdate
        }

        val checkResult = RuntimeUpdateFacade.fetchUpgradeInfo()
        val updateState = when (checkResult) {
            is RuntimeUpgradeCheckResult.CheckFailed -> {
                return if (isAutoCheck) {
                    GithubUpdateQueryResult.NoUpdate
                } else {
                    GithubUpdateQueryResult.Failed(checkResult.message)
                }
            }

            RuntimeUpgradeCheckResult.NoUpdate -> return GithubUpdateQueryResult.NoUpdate
            is RuntimeUpgradeCheckResult.LegacyLink -> {
                if (!RuntimeUpdateFacade.isNewer(BuildConfig.VERSION_NAME, checkResult.release.versionName)) {
                    return GithubUpdateQueryResult.NoUpdate
                }
                GithubUpdateUiState.Legacy(checkResult.release)
            }

            is RuntimeUpgradeCheckResult.Structured -> {
                val info = checkResult.info
                val newer = if (info.versionCode > 0L) {
                    RuntimeUpdateFacade.isNewer(BuildConfig.VERSION_CODE.toLong(), info.versionCode)
                } else {
                    RuntimeUpdateFacade.isNewer(BuildConfig.VERSION_NAME, info.versionName)
                }
                if (!newer) {
                    return GithubUpdateQueryResult.NoUpdate
                }

                val selectedApk = RuntimeUpdateFacade.selectBestApkForDevice(
                    apks = info.apks,
                )
                if (selectedApk != null && selectedApk.sha256.isNotBlank() && info.signingCertSha256.isNotBlank()) {
                    GithubUpdateUiState.Structured(
                        update = GithubStructuredUpdate(
                            info = info,
                            asset = selectedApk,
                        ),
                    )
                } else {
                    GithubUpdateUiState.Legacy(
                        RuntimeGithubReleaseInfo(
                            versionName = info.versionName,
                            htmlUrl = info.htmlUrl.ifBlank { Const.PROJECT_GITHUB_LATEST_RELEASE_URL },
                        ),
                    )
                }
            }
        }

        if (respectIgnoredVersion) {
            val ignoredVersion = AppPreferencesDataStore.getString(
                this,
                PrefConst.KEY_GITHUB_IGNORED_VERSION,
                "",
            )
            val latestVersionName = when (updateState) {
                is GithubUpdateUiState.Legacy -> updateState.release.versionName
                is GithubUpdateUiState.Structured -> updateState.update.info.versionName
            }
            if (
                RuntimeUpdateFacade.shouldSkipIgnoredVersion(
                    respectIgnoredVersion = respectIgnoredVersion,
                    ignoredVersion = ignoredVersion,
                    latestVersion = latestVersionName,
                )
            ) {
                return GithubUpdateQueryResult.NoUpdate
            }
        }
        return GithubUpdateQueryResult.Available(updateState)
    }

    private fun buildUpdateDialogText(updateState: GithubUpdateUiState): String {
        return when (updateState) {
            is GithubUpdateUiState.Legacy -> {
                getString(R.string.github_update_dialog_message, updateState.release.versionName)
            }

            is GithubUpdateUiState.Structured -> {
                val info = updateState.update.info
                val title = "v${BuildConfig.VERSION_NAME} -> v${info.versionName}"
                val matchedLogs = info.versionLogs.filter { it.code > BuildConfig.VERSION_CODE.toLong() }
                val content = when {
                    matchedLogs.size > 1 -> matchedLogs.joinToString("\n\n") { log ->
                        "v${log.name}\n${log.desc}"
                    }
                    matchedLogs.isNotEmpty() -> matchedLogs.first().desc
                    info.changelog.isNotBlank() -> info.changelog
                    else -> getString(R.string.github_update_dialog_message, info.versionName)
                }
                "$title\n\n$content".trim()
            }
        }
    }

    private fun formatDownloadProgress(progress: RuntimeUpgradeDownloadProgress): String {
        val percent = (progress.percent * 100f).toInt().coerceIn(0, 100)
        val current = formatBytes(progress.bytesRead)
        val total = if (progress.totalBytes > 0L) formatBytes(progress.totalBytes) else "?"
        return getString(R.string.update_download_progress_text, percent, current, total)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= BYTES_PER_UNIT && index < units.lastIndex) {
            value /= BYTES_PER_UNIT
            index++
        }
        return if (index == 0) {
            "${value.toInt()}${units[index]}"
        } else {
            String.format("%.1f%s", value, units[index])
        }
    }

}

private sealed interface BlockingStartupDialog {
    data object RelayConflict : BlockingStartupDialog
    data class FrameworkIncompatibility(
        val issue: FrameworkCompatibilityMonitor.FrameworkIssue,
    ) : BlockingStartupDialog
}

@Composable
private fun ExitOnlyConflictDialog(
    title: String,
    text: androidx.compose.ui.text.AnnotatedString,
    confirmText: String,
    onExit: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            AppPrimaryButton(
                text = confirmText,
                onClick = onExit,
            )
        },
    )
}

private data class GithubStructuredUpdate(
    val info: RuntimeUpgradeInfo,
    val asset: RuntimeUpgradeApkAsset,
)

private sealed class GithubUpdateUiState {
    data class Legacy(val release: RuntimeGithubReleaseInfo) : GithubUpdateUiState()
    data class Structured(val update: GithubStructuredUpdate) : GithubUpdateUiState()
}

private sealed class GithubUpdateQueryResult {
    data object NoUpdate : GithubUpdateQueryResult()
    data class Failed(val message: String?) : GithubUpdateQueryResult()
    data class Available(val update: GithubUpdateUiState) : GithubUpdateQueryResult()
}

private sealed class UpdateDownloadState {
    data object Idle : UpdateDownloadState()
    data class Downloading(val progress: Float, val progressText: String) : UpdateDownloadState()
    data class Failed(val message: String, val retry: GithubStructuredUpdate?) : UpdateDownloadState()
    data class Downloaded(val file: File, val update: GithubStructuredUpdate) : UpdateDownloadState()
}

private const val BYTES_PER_UNIT = 1024.0
private const val MAX_CAPTURE_PIXELS = 8_388_608L
private const val MAX_CAPTURE_SIDE_PX = 4096
private const val LARGE_BITMAP_ERROR_KEYWORD = "trying to draw too large"
