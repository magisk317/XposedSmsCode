@file:Suppress("LocalContextGetResourceValueCall")

package com.github.magisk317.smscode.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
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
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
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

internal data class OverviewUiState(
    val activationStatus: ActivationStatusState,
    val mobileAutomationAllowed: Boolean,
    val showStatusDiagnostics: Boolean,
    val statusDiagnostics: List<Pair<String, String>>,
    val frameworkType: String,
    val frameworkVersion: String,
    val hasRootAccess: Boolean,
    val appVersionName: String,
    val appVersionCode: String,
)

private data class FrameworkDiagnostics(
    val moduleInfo: Pair<String, String>? = null,
    val managerVersion: Pair<String, Long>? = null,
    val managerInstalled: Boolean = false,
)

internal class OverviewActions(
    val onActivateClick: () -> Unit,
    val onToggleDiagnostics: () -> Unit,
    val onRootHintClick: () -> Unit,
    val onJoinTelegram: () -> Unit,
    val onJoinQqChannel: () -> Unit,
    val onSourceCode: () -> Unit,
    val onDonate: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    isActive: Boolean = true,
    keepDataActive: Boolean = isActive,
    onPageDataReady: (cacheHit: Boolean) -> Unit = {},
) {
    val currentOnPageDataReady by rememberUpdatedState(onPageDataReady)
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
    var showStatusDiagnostics by remember { mutableStateOf(false) }
    val mobileAutomationAllowed = rememberEntitlementState(
        isActive = isActive,
        dataStoreReader = ::readEntitlementAutomationAllowed,
    )
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showLatestSnackbar(message) }
    }

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

    val rootHint = stringResource(id = R.string.root_permission_hint)

    val state = OverviewUiState(
        activationStatus = activationStatus,
        mobileAutomationAllowed = mobileAutomationAllowed,
        showStatusDiagnostics = showStatusDiagnostics,
        statusDiagnostics = buildStatusDiagnostics(
            context = context,
            snapshot = activationStatus.diagnostics,
            runtimeConnected = activationStatus.runtimeConnected,
        ),
        frameworkType = frameworkType,
        frameworkVersion = frameworkVersion,
        hasRootAccess = hasRootAccessState,
        appVersionName = appVersionName,
        appVersionCode = appVersionCode,
    )

    val actions = OverviewActions(
        onActivateClick = {
            context.startActivity(
                Intent().setClassName(
                    context,
                    "com.github.magisk317.smscode.entitlement.MobileEntitlementActivity",
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
        onToggleDiagnostics = {
            showStatusDiagnostics = !showStatusDiagnostics
            showMessage(
                if (showStatusDiagnostics) {
                    context.getString(R.string.status_diag_shown)
                } else {
                    context.getString(R.string.status_diag_hidden)
                },
            )
        },
        onRootHintClick = { showMessage(rootHint) },
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

    when (currentUiKitStyle()) {
        UiKitStyle.Miuix -> OverviewScreenMiuix(
            state = state,
            actions = actions,
        )

        UiKitStyle.Expressive -> OverviewScreenMaterial(
            state = state,
            actions = actions,
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
            onDonate099 = {
                val owner = activityOwner ?: (context as? ComponentActivity)
                owner?.let { billingProvider.launchDonation(it, "donate_099") }
            },
            onDonate200 = {
                val owner = activityOwner ?: (context as? ComponentActivity)
                owner?.let { billingProvider.launchDonation(it, "donate_200") }
            },
            onDonate999 = {
                val owner = activityOwner ?: (context as? ComponentActivity)
                owner?.let { billingProvider.launchDonation(it, "donate_999") }
            },
            onDonate1999 = {
                val owner = activityOwner ?: (context as? ComponentActivity)
                owner?.let { billingProvider.launchDonation(it, "donate_1999") }
            },
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
    onActivateClick: (() -> Unit)? = null,
    onDiagnosticsToggle: (() -> Unit)? = null,
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
        isEntitled = isEntitled,
        onActivateClick = onActivateClick,
        onDiagnosticsToggle = onDiagnosticsToggle,
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
