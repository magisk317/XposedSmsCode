package com.tianma.xsmscode.ui.home

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.tianma.xsmscode.core.BuildConfig
import com.tianma.xsmscode.core.R
import com.tianma.xsmscode.common.constant.Const
import com.tianma.xsmscode.common.constant.PrefConst
import com.tianma.xsmscode.common.utils.AppPreferencesDataStore
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.common.utils.SPUtils
import com.tianma.xsmscode.common.utils.PackageUtils
import com.tianma.xsmscode.common.utils.Utils
import com.tianma.xsmscode.data.update.ApkSecurityVerifier
import com.tianma.xsmscode.data.update.GithubReleaseInfo
import com.tianma.xsmscode.data.update.GithubUpdateChecker
import com.tianma.xsmscode.data.update.UpgradeApkAsset
import com.tianma.xsmscode.data.update.UpgradeCheckResult
import com.tianma.xsmscode.data.update.UpgradeDownloader
import com.tianma.xsmscode.data.update.UpgradeInfo
import com.tianma.xsmscode.data.update.UpgradeInstaller
import com.tianma.xsmscode.data.update.UpdatePolicy
import com.tianma.xsmscode.ui.app.base.UpdateSystemBars
import com.tianma.xsmscode.ui.app.base.applyEdgeToEdge
import com.tianma.xsmscode.ui.app.base.rememberHazeStyle
import com.tianma.xsmscode.ui.home.update.FlavorPlayUpdateDelegate
import com.tianma.xsmscode.ui.home.update.PlayUpdateDelegate
import com.tianma.xsmscode.ui.nav.SmsCodeNavHost
import com.tianma.xsmscode.ui.privacy.PrivacyPolicyPage
import com.tianma.xsmscode.ui.theme.AppTheme
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import org.koin.androidx.compose.koinViewModel
import java.io.File
import kotlin.math.hypot

class MainActivity : AppCompatActivity() {

    private val playUpdateDelegate: PlayUpdateDelegate = FlavorPlayUpdateDelegate()
    private var autoUpdateChecked = false

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyEdgeToEdge(this)
        playUpdateDelegate.onCreate(this) {
            PackageUtils.openPlayStoreOrGithub(this)
        }
        triggerAutoUpdateIfEnabled()

        setContent {
            val viewModel: SettingsViewModel = koinViewModel()
            val themeState by viewModel.themeState.collectAsStateWithLifecycle()
            val navController = rememberNavController()
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
            var showPrivacyPolicyPage by remember { mutableStateOf(false) }
            var githubUpdateUiState by remember { mutableStateOf<GithubUpdateUiState?>(null) }
            var downloadState by remember { mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle) }
            var unknownSourceApk by remember { mutableStateOf<File?>(null) }
            var downloadJob by remember { mutableStateOf<Job?>(null) }

            fun startStructuredDownload(update: GithubStructuredUpdate) {
                downloadJob?.cancel()
                downloadState = UpdateDownloadState.Downloading(progress = 0f, progressText = "0%")
                downloadJob = scope.launch {
                    try {
                        val downloadedFile = UpgradeDownloader.download(
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
                        val verifyResult = ApkSecurityVerifier.verifyDownloadedApk(
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
                githubUpdateUiState = checkStartupGithubUpdateIfNeeded()
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

                        revealAnim.snapTo(0f)
                        revealAnim.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 600),
                        )
                    } catch (oom: OutOfMemoryError) {
                        XLog.w("Theme capture OOM, fallback to direct mode switch")
                        currentThemeMode = themeState.mode
                    } catch (e: RuntimeException) {
                        if (e.message?.contains(LARGE_BITMAP_ERROR_KEYWORD, ignoreCase = true) == true) {
                            XLog.w("Theme capture too large bitmap, fallback to direct mode switch")
                        } else {
                            XLog.w("Theme capture runtime exception: %s", e.message ?: "unknown")
                        }
                        currentThemeMode = themeState.mode
                    } catch (t: Throwable) {
                        XLog.w("Theme capture failed: %s", t.message ?: "unknown")
                        currentThemeMode = themeState.mode
                    } finally {
                        isAnimating = false
                        clearScreenshotBitmap()
                    }
                } else {
                    // Initial load
                    currentThemeMode = themeState.mode
                }
            }

            // Collect navigation events
            LaunchedEffect(viewModel.eventsFlow) {
                viewModel.eventsFlow.collect { event ->
                    when (event) {
                        is SettingsEvent.ShowPrivacyPolicy -> showPrivacyPolicyDialog = true
                        is SettingsEvent.NavigateToRules -> requestedTab = com.tianma.xsmscode.ui.nav.InterceptRoute
                        is SettingsEvent.NavigateToRecords -> requestedTab = com.tianma.xsmscode.ui.nav.RecordsRoute
                        is SettingsEvent.StartPlayUpdate -> requestPlayUpdate()
                        is SettingsEvent.StartGithubUpdateCheck -> {
                            requestGithubUpdateCheck(showNoUpdateToast = true) { update ->
                                githubUpdateUiState = update
                            }
                        }
                        else -> {}
                    }
                }
            }

            AppTheme(themeMode = currentThemeMode) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    LaunchedEffect(Unit) {
                        viewModel.setInternalFilesWritable()
                    }
                    LaunchedEffect(intent) {
                        viewModel.handleArguments(intent.extras)
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
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
                        SmsCodeNavHost(
                            navController = navController,
                            onBack = { finish() },
                            initialTab = requestedTab,
                            onInitialTabConsumed = { requestedTab = null },
                            modifier = Modifier,
                            hazeState = hazeState,
                            hazeStyle = hazeStyle,
                        )

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
                                    finish()
                                },
                                onViewPolicy = { showPrivacyPolicyPage = true },
                            )
                        }

                        if (showPrivacyPolicyPage) {
                            PrivacyPolicyPage(onDismiss = { showPrivacyPolicyPage = false })
                        }

                        githubUpdateUiState?.let { updateState ->
                            AlertDialog(
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
                                    FilledTonalButton(
                                        onClick = {
                                            when (updateState) {
                                                is GithubUpdateUiState.Legacy -> {
                                                    Utils.showWebPage(this@MainActivity, updateState.release.htmlUrl)
                                                    githubUpdateUiState = null
                                                }

                                                is GithubUpdateUiState.Structured -> {
                                                    githubUpdateUiState = null
                                                    startStructuredDownload(updateState.update)
                                                }
                                            }
                                        },
                                    ) {
                                        Text(getString(R.string.github_update_download))
                                    }
                                },
                                dismissButton = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
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
                                                    AppPreferencesDataStore.syncToSharedPrefs(this@MainActivity)
                                                }
                                                githubUpdateUiState = null
                                            },
                                        ) {
                                            Text(getString(R.string.github_update_ignore_this_version))
                                        }
                                        OutlinedButton(onClick = { githubUpdateUiState = null }) {
                                            Text(getString(R.string.cancel))
                                        }
                                    }
                                },
                            )
                        }

                        when (val state = downloadState) {
                            is UpdateDownloadState.Downloading -> {
                                AlertDialog(
                                    onDismissRequest = {},
                                    title = { Text(getString(R.string.update_download_in_progress_title)) },
                                    text = {
                                        Column {
                                            LinearProgressIndicator(
                                                progress = { state.progress },
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                            Text(state.progressText)
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                downloadJob?.cancel()
                                                downloadState = UpdateDownloadState.Idle
                                            },
                                        ) {
                                            Text(getString(R.string.update_download_cancel))
                                        }
                                    },
                                )
                            }

                            is UpdateDownloadState.Failed -> {
                                AlertDialog(
                                    onDismissRequest = { downloadState = UpdateDownloadState.Idle },
                                    title = { Text(getString(R.string.update_download_failed_title)) },
                                    text = { Text(state.message) },
                                    dismissButton = {
                                        OutlinedButton(onClick = { downloadState = UpdateDownloadState.Idle }) {
                                            Text(getString(R.string.cancel))
                                        }
                                    },
                                    confirmButton = {
                                        if (state.retry != null) {
                                            FilledTonalButton(onClick = { startStructuredDownload(state.retry) }) {
                                                Text(getString(R.string.update_retry))
                                            }
                                        }
                                    },
                                )
                            }

                            is UpdateDownloadState.Downloaded -> {
                                AlertDialog(
                                    onDismissRequest = {},
                                    title = { Text(getString(R.string.update_download_completed_title)) },
                                    text = { Text(getString(R.string.update_download_completed_message)) },
                                    dismissButton = {
                                        OutlinedButton(onClick = { downloadState = UpdateDownloadState.Idle }) {
                                            Text(getString(R.string.cancel))
                                        }
                                    },
                                    confirmButton = {
                                        FilledTonalButton(
                                            onClick = {
                                                if (!UpgradeInstaller.canRequestPackageInstalls(this@MainActivity)) {
                                                    unknownSourceApk = state.file
                                                    return@FilledTonalButton
                                                }
                                                val installResult = UpgradeInstaller.installApk(this@MainActivity, state.file)
                                                if (installResult.isSuccess) {
                                                    downloadState = UpdateDownloadState.Idle
                                                } else {
                                                    downloadState = UpdateDownloadState.Failed(
                                                        message = installResult.exceptionOrNull()?.message
                                                            ?: "install_failed",
                                                        retry = state.update,
                                                    )
                                                }
                                            },
                                        ) {
                                            Text(getString(R.string.update_install))
                                        }
                                    },
                                )
                            }

                            UpdateDownloadState.Idle -> Unit
                        }

                        unknownSourceApk?.let {
                            AlertDialog(
                                onDismissRequest = { unknownSourceApk = null },
                                title = { Text(getString(R.string.update_unknown_source_title)) },
                                text = { Text(getString(R.string.update_unknown_source_message)) },
                                dismissButton = {
                                    OutlinedButton(onClick = { unknownSourceApk = null }) {
                                        Text(getString(R.string.cancel))
                                    }
                                },
                                confirmButton = {
                                    FilledTonalButton(
                                        onClick = {
                                            startActivity(UpgradeInstaller.buildUnknownSourceSettingsIntent(this@MainActivity))
                                            unknownSourceApk = null
                                        },
                                    ) {
                                        Text(getString(R.string.update_open_settings))
                                    }
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
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        playUpdateDelegate.onResume(this) {
            PackageUtils.openPlayStoreOrGithub(this)
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
            PackageUtils.openPlayStoreOrGithub(this)
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
            if (!UpdatePolicy.shouldRunAutoCheck(enabled, wifiOnly, onWifi)) return@launch

            when (UpdatePolicy.resolveStartupTarget(PackageUtils.isInstalledFromPlay(this@MainActivity))) {
                UpdatePolicy.StartupTarget.PLAY -> {
                requestPlayUpdateInternal(silentIfNoUpdate = true, fallbackOnQueryFailure = false)
                }
                UpdatePolicy.StartupTarget.GITHUB -> {
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
        showNoUpdateToast: Boolean,
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
                    android.widget.Toast.makeText(
                        this@MainActivity,
                        getString(R.string.check_update_failed),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }

                GithubUpdateQueryResult.NoUpdate -> {
                    if (showNoUpdateToast) {
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            getString(R.string.app_already_newest),
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
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
            if (UpdatePolicy.shouldSkipGithubCheckOnStartup(installedFromPlay, enabled, wifiOnly, onWifi)) {
                return GithubUpdateQueryResult.NoUpdate
            }
        } else if (installedFromPlay) {
            return GithubUpdateQueryResult.NoUpdate
        }

        val checkResult = GithubUpdateChecker.fetchUpgradeInfo()
        val updateState = when (checkResult) {
            is UpgradeCheckResult.CheckFailed -> {
                return if (isAutoCheck) {
                    GithubUpdateQueryResult.NoUpdate
                } else {
                    GithubUpdateQueryResult.Failed(checkResult.message)
                }
            }

            UpgradeCheckResult.NoUpdate -> return GithubUpdateQueryResult.NoUpdate
            is UpgradeCheckResult.LegacyLink -> {
                if (!GithubUpdateChecker.isNewer(BuildConfig.VERSION_NAME, checkResult.release.versionName)) {
                    return GithubUpdateQueryResult.NoUpdate
                }
                GithubUpdateUiState.Legacy(checkResult.release)
            }

            is UpgradeCheckResult.Structured -> {
                val info = checkResult.info
                val newer = if (info.versionCode > 0L) {
                    GithubUpdateChecker.isNewer(BuildConfig.VERSION_CODE.toLong(), info.versionCode)
                } else {
                    GithubUpdateChecker.isNewer(BuildConfig.VERSION_NAME, info.versionName)
                }
                if (!newer) {
                    return GithubUpdateQueryResult.NoUpdate
                }

                val selectedApk = GithubUpdateChecker.selectBestApkForDevice(info.apks)
                if (selectedApk != null && selectedApk.sha256.isNotBlank() && info.signingCertSha256.isNotBlank()) {
                    GithubUpdateUiState.Structured(
                        update = GithubStructuredUpdate(
                            info = info,
                            asset = selectedApk,
                        ),
                    )
                } else {
                    GithubUpdateUiState.Legacy(
                        GithubReleaseInfo(
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
            if (UpdatePolicy.shouldSkipIgnoredVersion(respectIgnoredVersion, ignoredVersion, latestVersionName)) {
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

    private fun formatDownloadProgress(progress: UpgradeDownloader.Progress): String {
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

private data class GithubStructuredUpdate(
    val info: UpgradeInfo,
    val asset: UpgradeApkAsset,
)

private sealed class GithubUpdateUiState {
    data class Legacy(val release: GithubReleaseInfo) : GithubUpdateUiState()
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
