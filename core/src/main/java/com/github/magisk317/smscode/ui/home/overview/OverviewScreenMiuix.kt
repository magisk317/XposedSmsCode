package com.github.magisk317.smscode.ui.home

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.magisk317.smscode.core.R
import io.github.magisk317.uikit.R as UiKitR
import io.github.magisk317.uikit.surface.AppCard
import io.github.magisk317.uikit.surface.rememberUiKitGlassTopBar
import io.github.magisk317.uikit.surface.uiKitSurfaceGlassSample
import io.github.magisk317.uikit.theme.LocalUiKitSurfaceBlur
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * Miuix implementation of the Overview page. Follows KernelSU `HomeMiuix`
 * and MiPush `OverviewMiuix` composition model: page-owned miuix `Scaffold` +
 * collapsing `TopAppBar` driven by `MiuixScrollBehavior`.
 */
@Composable
internal fun OverviewScreenMiuix(
    state: OverviewUiState,
    actions: OverviewActions,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val scrollState = rememberScrollState()
    val unknown = stringResource(id = R.string.unknown)

    val topGlass = rememberUiKitGlassTopBar()
    val glassOn = LocalUiKitSurfaceBlur.current.usesBackdrop
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.uiKitSurfaceGlassSample(topGlass),
                title = stringResource(id = R.string.app_name),
                scrollBehavior = scrollBehavior,
                color = if (glassOn) Color.Transparent else io.github.magisk317.uikit.surface.chromeSurfaceColor(),
                defaultWindowInsetsPadding = true,
            )
        },
        contentWindowInsets = WindowInsets.systemBars
            .union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (glassOn) topGlass.contentRecorder() else Modifier)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .scrollEndHaptic()
                    .padding(
                        PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = innerPadding.calculateTopPadding() + 12.dp,
                            bottom = innerPadding.calculateBottomPadding() + 80.dp,
                        ),
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatusCard(
                    isEnabled = state.activationStatus.isEnabled,
                    isEntitled = state.mobileAutomationAllowed,
                    showDiagnostics = state.showStatusDiagnostics,
                    diagnostics = state.statusDiagnostics,
                    onActivateClick = actions.onActivateClick,
                    onDiagnosticsToggle = actions.onToggleDiagnostics,
                )

                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        MiuixInfoRow(
                            label = UiKitR.string.uikit_version_name,
                            value = state.appVersionName.ifBlank { unknown },
                        )
                        MiuixInfoRow(
                            label = UiKitR.string.uikit_version_code,
                            value = state.appVersionCode.ifBlank { unknown },
                        )
                        MiuixActionRow(
                            label = UiKitR.string.uikit_framework_type,
                            value = state.frameworkType.ifBlank { unknown },
                            onClick = if (state.hasRootAccess) null else actions.onRootHintClick,
                        )
                        MiuixActionRow(
                            label = UiKitR.string.uikit_framework_version,
                            value = state.frameworkVersion.ifBlank { unknown },
                            onClick = if (state.hasRootAccess) null else actions.onRootHintClick,
                        )
                    }
                }

                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        MiuixInfoRow(
                            label = UiKitR.string.uikit_manufacturer,
                            value = Build.MANUFACTURER,
                        )
                        MiuixInfoRow(
                            label = UiKitR.string.uikit_model,
                            value = io.github.magisk317.uikit.platform.resolveAndroidDeviceDisplayName(),
                        )
                        MiuixInfoRow(
                            label = UiKitR.string.uikit_android_version,
                            value = Build.VERSION.RELEASE,
                        )
                        MiuixInfoRow(
                            label = UiKitR.string.uikit_api_level,
                            value = Build.VERSION.SDK_INT.toString(),
                        )
                        MiuixInfoRow(
                            label = UiKitR.string.uikit_android_codename,
                            value = Build.VERSION.CODENAME,
                        )
                    }
                }

                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        MiuixActionRow(
                            label = UiKitR.string.uikit_pref_join_telegram_group_title,
                            summary = UiKitR.string.uikit_pref_join_telegram_group_summary,
                            onClick = actions.onJoinTelegram,
                        )
                        MiuixActionRow(
                            label = UiKitR.string.uikit_pref_join_qq_channel_title,
                            summary = UiKitR.string.uikit_pref_join_qq_channel_summary,
                            onClick = actions.onJoinQqChannel,
                        )
                        MiuixActionRow(
                            label = UiKitR.string.uikit_pref_source_code_title,
                            summary = UiKitR.string.uikit_pref_source_code_summary,
                            onClick = actions.onSourceCode,
                        )
                        MiuixActionRow(
                            label = UiKitR.string.uikit_pref_donate_by_alipay_title,
                            summary = UiKitR.string.uikit_dialog_donate_summary,
                            onClick = actions.onDonate,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiuixInfoRow(@StringRes label: Int, value: String) {
    BasicComponent(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(label),
        summary = value,
    )
}

@Composable
private fun MiuixActionRow(
    @StringRes label: Int,
    value: String,
    onClick: (() -> Unit)?,
) {
    BasicComponent(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(label),
        summary = value,
        onClick = onClick ?: {},
    )
}

@Composable
private fun MiuixActionRow(
    @StringRes label: Int,
    @StringRes summary: Int,
    onClick: () -> Unit,
) {
    BasicComponent(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(label),
        summary = stringResource(summary),
        onClick = onClick,
    )
}
