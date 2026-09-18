@file:Suppress("LocalContextGetResourceValueCall")

package com.github.magisk317.smscode.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.core.BuildConfig
import io.github.magisk317.uikit.R as UiKitR
import io.github.magisk317.smscode.runtime.contract.diagnostics.ActivationStatusState
import com.github.magisk317.smscode.common.constant.Const
import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.common.utils.AppPreferencesDataStore
import io.github.magisk317.smscode.runtime.contract.diagnostics.ActivationDiagnosticsSnapshot
import io.github.magisk317.smscode.runtime.common.diagnostics.ActivationDiagnosticsStore
import io.github.magisk317.uikit.entitlement.rememberEntitlementState
import com.github.magisk317.smscode.common.utils.PackageUtils
import io.github.magisk317.smscode.runtime.common.utils.BrowserUtils
import io.github.magisk317.uikit.common.showLatestSnackbar
import io.github.magisk317.uikit.foundation.LocalSnackbarHostState
import io.github.magisk317.uikit.surface.AppTopBar
import io.github.magisk317.uikit.surface.StatusHeroCard
import io.github.magisk317.uikit.surface.SummaryRow
import io.github.magisk317.uikit.surface.SummarySectionCard
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.Toast
import io.github.magisk317.uikit.surface.DonateDialog
import io.github.magisk317.uikit.surface.QRCodeDialog
import io.github.magisk317.uikit.surface.startAlipayPlatformDonate
import io.github.magisk317.uikit.surface.saveImageToGalleryAsync

private suspend fun readEntitlementAutomationAllowed(context: Context): Boolean =
    AppPreferencesDataStore.getBoolean(
        context,
        PrefConst.KEY_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED,
        false,
    )

private data class OverviewPageRuntime(
    val isActive: Boolean = true,
    val keepDataActive: Boolean = isActive,
    val onPageDataReady: (cacheHit: Boolean) -> Unit = {},
)

private data class FrameworkDiagnostics(
    val moduleInfo: Pair<String, String>? = null,
    val managerVersion: Pair<String, Long>? = null,
    val managerInstalled: Boolean = false,
)

private val LocalOverviewPageRuntime = staticCompositionLocalOf { OverviewPageRuntime() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    isActive: Boolean = true,
    keepDataActive: Boolean = isActive,
    onPageDataReady: (cacheHit: Boolean) -> Unit = {},
) {
    CompositionLocalProvider(
        LocalOverviewPageRuntime provides OverviewPageRuntime(
            isActive = isActive,
            keepDataActive = keepDataActive,
            onPageDataReady = onPageDataReady,
        ),
    ) {
        when (currentUiKitStyle()) {
            UiKitStyle.Miuix -> OverviewScreenMiuix()
            UiKitStyle.Expressive -> OverviewScreenMaterial()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@SuppressLint("AutoboxingStateCreation")
internal fun OverviewScreenShared() {
    val pageRuntime = LocalOverviewPageRuntime.current
    val isActive = pageRuntime.isActive
    val keepDataActive = pageRuntime.keepDataActive
    val currentOnPageDataReady by rememberUpdatedState(pageRuntime.onPageDataReady)
    val context = LocalContext.current
    val activityOwner = context as? ComponentActivity
    val settingsViewModel = if (keepDataActive) {
        if (activityOwner != null) {
            koinViewModel<SettingsViewModel>(viewModelStoreOwner = activityOwner)
        } else {
            koinViewModel()
        }
    } else {
        null
    }
    var showDonateDialog by remember { mutableStateOf(false) }
    val billingProvider: com.github.magisk317.smscode.billing.BillingProvider = org.koin.compose.koinInject()
    var showQRCodeDialog by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var statusTapCount by remember { mutableStateOf(0) }
    var statusTapStartedAtMs by remember { mutableStateOf(0L) }
    var showStatusDiagnostics by remember { mutableStateOf(false) }
    val mobileAutomationAllowed = rememberEntitlementState(
        isActive = isActive,
        dataStoreReader = ::readEntitlementAutomationAllowed,
    )
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

    fun showMessage(message: String) {
        scope.launch {
            snackbarHostState.showLatestSnackbar(message)
        }
    }

    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var activationStatus by remember { mutableStateOf(ActivationStatusState()) }
    var frameworkDiagnostics by remember { mutableStateOf(FrameworkDiagnostics()) }
    var hasRootAccessState by remember { mutableStateOf(false) }
    var appVersionState by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var hasActivationSnapshot by remember { mutableStateOf(false) }
    var hasFrameworkSnapshot by remember { mutableStateOf(false) }
    var hasRootSnapshot by remember { mutableStateOf(false) }
    var hasAppVersionSnapshot by remember { mutableStateOf(false) }
    val hasDiagnosticsSnapshot = hasActivationSnapshot && hasFrameworkSnapshot &&
        hasRootSnapshot && hasAppVersionSnapshot

    LaunchedEffect(context, keepDataActive) {
        if (!keepDataActive) return@LaunchedEffect
        launch {
            ActivationDiagnosticsStore.observeStatus(context).collect {
                activationStatus = it
                hasActivationSnapshot = true
            }
        }
        if (!hasFrameworkSnapshot) {
            launch {
                frameworkDiagnostics = withContext(Dispatchers.IO) {
                    val moduleInfo = PackageUtils.getLsposedModuleInfo(context)
                    FrameworkDiagnostics(
                        moduleInfo = moduleInfo,
                        managerVersion = if (moduleInfo == null) {
                            PackageUtils.getPackageVersion(context, Const.LSPOSED_MANAGER_PACKAGE_NAME)
                        } else {
                            null
                        },
                        managerInstalled = moduleInfo != null || PackageUtils.isPackageInstalled(
                            context,
                            Const.LSPOSED_MANAGER_PACKAGE_NAME,
                        ),
                    )
                }
                hasFrameworkSnapshot = true
            }
        }
        if (!hasRootSnapshot) {
            launch {
                hasRootAccessState = withContext(Dispatchers.IO) { PackageUtils.hasRootAccess() }
                hasRootSnapshot = true
            }
        }
        if (!hasAppVersionSnapshot) {
            launch {
                appVersionState = withContext(Dispatchers.IO) {
                    PackageUtils.getPackageVersion(context, context.packageName)
                }
                hasAppVersionSnapshot = true
            }
        }
    }

    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        val cacheHit = hasDiagnosticsSnapshot
        if (!cacheHit) {
            snapshotFlow {
                hasActivationSnapshot && hasFrameworkSnapshot && hasRootSnapshot && hasAppVersionSnapshot
            }.first { it }
        }
        currentOnPageDataReady(cacheHit)
    }

    val frameworkType = frameworkDiagnostics.moduleInfo?.first ?: stringResource(id = R.string.unknown)
    val frameworkVersion = frameworkDiagnostics.moduleInfo?.second ?: run {
        val lsposedVersion = frameworkDiagnostics.managerVersion
        when {
            lsposedVersion != null && lsposedVersion.first.isNotBlank() ->
                "${lsposedVersion.first} (${lsposedVersion.second})"

            frameworkDiagnostics.managerInstalled ->
                stringResource(id = R.string.unknown)

            else -> stringResource(id = R.string.not_installed)
        }
    }
    val appVersionName = appVersionState?.first?.takeIf { it.isNotBlank() } ?: stringResource(id = R.string.unknown)
    val appVersionCode = BuildConfig.COMMIT_HASH.takeIf { it.isNotBlank() && it != "unknown" }
        ?: (appVersionState?.second?.toString() ?: stringResource(id = R.string.unknown))

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 16.dp),
            state = listState,
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp + 8.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                StatusCard(
                    isEnabled = activationStatus.isEnabled,
                    isEntitled = mobileAutomationAllowed,
                    showDiagnostics = showStatusDiagnostics,
                    diagnostics = buildStatusDiagnostics(
                        context = context,
                        snapshot = activationStatus.diagnostics,
                        runtimeConnected = activationStatus.runtimeConnected,
                    ),
                    onClick = {
                        val now = SystemClock.uptimeMillis()
                        val withinWindow = now - statusTapStartedAtMs <= 1800L
                        statusTapCount = if (withinWindow) statusTapCount + 1 else 1
                        statusTapStartedAtMs = now
                        if (statusTapCount >= 5) {
                            showStatusDiagnostics = !showStatusDiagnostics
                            statusTapCount = 0
                            statusTapStartedAtMs = 0L
                            showMessage(
                                if (showStatusDiagnostics) {
                                    context.getString(R.string.status_diag_shown)
                                } else {
                                    context.getString(R.string.status_diag_hidden)
                                },
                            )
                        }
                    },
                )
            }
            item {
                val rootHint = stringResource(id = R.string.root_permission_hint)
                io.github.magisk317.uikit.surface.OverviewAppInfoCard(
                    appVersionName = appVersionName,
                    appVersionCode = appVersionCode,
                    appVersionCodeLabel = stringResource(id = UiKitR.string.uikit_version_code),
                    frameworkType = frameworkType,
                    frameworkVersion = frameworkVersion,
                    interactive = true,
                    onRootHint = if (hasRootAccessState) null else { { showMessage(rootHint) } },
                )
            }

            item {
                io.github.magisk317.uikit.surface.OverviewDeviceInfoCard()
            }

            item {
                io.github.magisk317.uikit.surface.OverviewLinksCard(
                    onJoinTelegram = {
                        BrowserUtils.openWebPage(
                            context,
                            Const.TELEGRAM_GROUP_URL,
                            R.string.browser_install_or_enable_prompt,
                        )?.let(::showMessage)
                    },
                    onJoinQqChannel = {
                        BrowserUtils.openWebPage(
                            context,
                            Const.QQ_CHANNEL_URL,
                            R.string.browser_install_or_enable_prompt,
                        )?.let(::showMessage)
                    },
                    onSourceCode = {
                        BrowserUtils.openWebPage(
                            context,
                            Const.PROJECT_SOURCE_CODE_URL,
                            R.string.browser_install_or_enable_prompt,
                        )?.let(::showMessage)
                    },
                    onDonate = { showDonateDialog = true },
                )
            }
        }

        AppTopBar(
            title = stringResource(id = R.string.app_name),
            scrollBehavior = scrollBehavior,
            windowInsets = WindowInsets.statusBars,
            modifier = Modifier
                .align(Alignment.TopCenter),
        )
    }

    if (showDonateDialog) {
        DonateDialog(
            onDismiss = { showDonateDialog = false },
            onAlipay = {
                showDonateDialog = false
                Toast.makeText(
                    context,
                    UiKitR.string.alipay_platform_opening,
                    Toast.LENGTH_SHORT,
                ).show()
                scope.launch {
                    val error = startAlipayPlatformDonate(context)
                    if (error != null) {
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                        showQRCodeDialog = Pair(UiKitR.drawable.alipay, "alipay")
                    }
                }
            },
            onWechat = {
                showDonateDialog = false
                showQRCodeDialog = Pair(UiKitR.drawable.wx, "wechat")
            },
            showPlayDonations = com.github.magisk317.smscode.core.BuildConfig.HAS_BILLING,
            onDonate099 = { activityOwner?.let { billingProvider.launchDonation(it, "donate_099") } },
            onDonate200 = { activityOwner?.let { billingProvider.launchDonation(it, "donate_200") } },
            onDonate999 = { activityOwner?.let { billingProvider.launchDonation(it, "donate_999") } },
            onDonate1999 = { activityOwner?.let { billingProvider.launchDonation(it, "donate_1999") } },
        )
    }

    showQRCodeDialog?.let { pair ->
        QRCodeDialog(
            resId = pair.first,
            type = pair.second,
            onDismiss = { showQRCodeDialog = null },
            onSave = {
                scope.launch {
                    saveImageToGalleryAsync(context, pair.first, "${pair.second}_qrcode")
                        .forEach(::showMessage)
                }
            },
        )
    }
}

@Composable
fun StatusCard(
    isEnabled: Boolean,
    isEntitled: Boolean,
    showDiagnostics: Boolean,
    diagnostics: List<Pair<String, String>>,
    onClick: (() -> Unit)? = null,
) {
    val moduleStatusText = if (isEnabled) {
        stringResource(id = R.string.status_module_activated)
    } else {
        stringResource(id = R.string.status_module_not_activated)
    }
    val entitlementStatusText = if (isEntitled) {
        stringResource(id = R.string.status_entitlement_verified)
    } else {
        stringResource(id = R.string.status_entitlement_unverified)
    }
    val title = "$moduleStatusText\n$entitlementStatusText"

    val isAllOk = isEnabled && isEntitled
    val summary = if (!isEnabled) {
        stringResource(id = R.string.status_activate_hint)
    } else {
        null
    }

    StatusHeroCard(
        title = title,
        summary = summary,
        icon = if (isAllOk) Icons.Default.CheckCircle else Icons.Default.Warning,
        highlighted = isAllOk,
        diagnostics = if (showDiagnostics) diagnostics else emptyList(),
        onClick = onClick,
    )
}

private fun buildStatusDiagnostics(
    context: android.content.Context,
    snapshot: ActivationDiagnosticsSnapshot,
    runtimeConnected: Boolean,
): List<Pair<String, String>> {
    val serviceValue = buildString {
        append(
            context.getString(
                if (runtimeConnected) {
                    R.string.status_diag_connected
                } else {
                    R.string.status_diag_disconnected
                },
            ),
        )
        if (snapshot.lastServiceBindAtMs > 0L) {
            append(" · ")
            append(context.getString(R.string.status_diag_last_service_prefix))
            append(" ")
            append(formatStatusDiagnosticTime(context, snapshot.lastServiceBindAtMs))
        }
        if (snapshot.lastServiceFrameworkName.isNotBlank() || snapshot.lastServiceFrameworkVersion.isNotBlank()) {
            append(" · ")
            append(snapshot.lastServiceFrameworkName.ifBlank { context.getString(R.string.unknown) })
            append(" ")
            append(snapshot.lastServiceFrameworkVersion.ifBlank { context.getString(R.string.unknown) })
        }
    }
    val hookProcess = listOf(
        snapshot.lastHookPackage.ifBlank { context.getString(R.string.status_diag_none) },
        snapshot.lastHookProcess.ifBlank { context.getString(R.string.status_diag_none) },
    ).joinToString(" / ")
    val hookTime = buildString {
        append(formatStatusDiagnosticTime(context, snapshot.lastHookAtMs))
        if (snapshot.lastHookSource.isNotBlank()) {
            append(" · ")
            append(snapshot.lastHookSource)
        }
    }
    return listOf(
        context.getString(R.string.status_diag_service_title) to serviceValue,
        context.getString(R.string.status_diag_hook_process_title) to hookProcess,
        context.getString(R.string.status_diag_hook_time_title) to hookTime,
    )
}

private fun formatStatusDiagnosticTime(context: android.content.Context, timestampMs: Long): String {
    if (timestampMs <= 0L) return context.getString(R.string.status_diag_none)
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestampMs))
}
