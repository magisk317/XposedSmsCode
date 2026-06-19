@file:Suppress("LocalContextGetResourceValueCall")

package com.github.magisk317.smscode.ui.home

import android.os.Build
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.common.utils.ActivationStatusState
import com.github.magisk317.smscode.common.constant.Const
import com.github.magisk317.smscode.common.utils.ActivationDiagnosticsSnapshot
import com.github.magisk317.smscode.common.utils.ActivationDiagnosticsStore
import com.github.magisk317.smscode.common.utils.PackageUtils
import com.github.magisk317.smscode.common.utils.Utils
import io.github.magisk317.uikit.foundation.LocalSnackbarHostState
import io.github.magisk317.uikit.surface.AppTopBar
import io.github.magisk317.uikit.surface.StatusHeroCard
import io.github.magisk317.uikit.surface.SummaryRow
import io.github.magisk317.uikit.surface.SummarySectionCard
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import io.github.magisk317.uikit.surface.DonateDialog
import io.github.magisk317.uikit.surface.QRCodeDialog
import io.github.magisk317.uikit.R as UiKitR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(hazeState: HazeState, hazeStyle: HazeBlurStyle) {
    when (currentUiKitStyle()) {
        UiKitStyle.Miuix -> OverviewScreenMiuix(hazeState = hazeState, hazeStyle = hazeStyle)
        UiKitStyle.Expressive -> OverviewScreenMaterial(hazeState = hazeState, hazeStyle = hazeStyle)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OverviewScreenShared(hazeState: HazeState, hazeStyle: HazeBlurStyle) {
    val context = LocalContext.current
    val activityOwner = context as? ComponentActivity
    val settingsViewModel = if (activityOwner != null) {
        koinViewModel<SettingsViewModel>(viewModelStoreOwner = activityOwner)
    } else {
        koinViewModel()
    }
    var showDonateDialog by remember { mutableStateOf(false) }
    val billingProvider: com.github.magisk317.smscode.billing.BillingProvider = org.koin.compose.koinInject()
    var showQRCodeDialog by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var statusTapCount by remember { mutableStateOf(0) }
    var statusTapStartedAtMs by remember { mutableStateOf(0L) }
    var showStatusDiagnostics by remember { mutableStateOf(false) }
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

    fun showMessage(message: String) {
        scope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val activationStatus by ActivationDiagnosticsStore.observeStatus(context)
        .collectAsStateWithLifecycle(initialValue = ActivationStatusState())
    val frameworkInfoState by produceState<Pair<String, String>?>(
        initialValue = null,
    ) {
        value = withContext(Dispatchers.IO) {
            PackageUtils.getLsposedModuleInfo(context)
        }
    }
    val frameworkType = frameworkInfoState?.first ?: stringResource(id = R.string.unknown)
    val frameworkVersion = frameworkInfoState?.second ?: run {
        val lsposedVersion = PackageUtils.getPackageVersion(context, Const.LSPOSED_MANAGER_PACKAGE_NAME)
        when {
            lsposedVersion != null && lsposedVersion.first.isNotBlank() ->
                "${lsposedVersion.first} (${lsposedVersion.second})"

            PackageUtils.isPackageInstalled(context, Const.LSPOSED_MANAGER_PACKAGE_NAME) ->
                stringResource(id = R.string.unknown)

            else -> stringResource(id = R.string.not_installed)
        }
    }
    val hasRootAccessState by produceState(
        initialValue = false,
    ) {
        value = withContext(Dispatchers.IO) {
            PackageUtils.hasRootAccess()
        }
    }
    val appVersionState by produceState<Pair<String, Long>?>(
        initialValue = null,
    ) {
        value = withContext(Dispatchers.IO) {
            PackageUtils.getPackageVersion(context, context.packageName)
        }
    }
    val appVersionName = appVersionState?.first?.takeIf { it.isNotBlank() } ?: stringResource(id = R.string.unknown)
    val appVersionCode = appVersionState?.second?.toString() ?: stringResource(id = R.string.unknown)

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
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
                    onCheckUpdate = { settingsViewModel.requestPreferredUpdate() },
                    onJoinQQ = { PackageUtils.joinQQGroup(context)?.let(::showMessage) },
                    onJoinTelegram = { Utils.showWebPage(context, Const.TELEGRAM_GROUP_URL)?.let(::showMessage) },
                    onSourceCode = { Utils.showWebPage(context, Const.PROJECT_SOURCE_CODE_URL)?.let(::showMessage) },
                    onDonate = { showDonateDialog = true },
                )
            }
        }

        AppTopBar(
            title = stringResource(id = R.string.app_name),
            scrollBehavior = scrollBehavior,
            windowInsets = WindowInsets.statusBars,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .hazeEffect(hazeState) {
                    blurEffect { style = hazeStyle }
                    forceInvalidateOnPreDraw = true
                },
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        )
    }

    if (showDonateDialog) {
        DonateDialog(
            onDismiss = { showDonateDialog = false },
            onAlipay = {
                showDonateDialog = false
                showQRCodeDialog = Pair(UiKitR.drawable.alipay, "alipay")
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
                Utils.saveImageToGallery(context, pair.first, "${pair.second}_qrcode")
                    .forEach(::showMessage)
            },
        )
    }
}

@Composable
fun StatusCard(
    isEnabled: Boolean,
    showDiagnostics: Boolean,
    diagnostics: List<Pair<String, String>>,
    onClick: (() -> Unit)? = null,
) {
    StatusHeroCard(
        title = if (isEnabled) stringResource(id = R.string.status_working) else stringResource(id = R.string.status_not_active),
        summary = if (isEnabled) null else stringResource(id = R.string.status_tip),
        icon = if (isEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
        highlighted = isEnabled,
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