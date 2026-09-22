@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.github.magisk317.smscode.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.magisk317.smscode.core.R
import io.github.magisk317.uikit.R as UiKitR
import io.github.magisk317.uikit.surface.AppTopBar
import io.github.magisk317.uikit.surface.OverviewAppInfoCard
import io.github.magisk317.uikit.surface.OverviewDeviceInfoCard
import io.github.magisk317.uikit.surface.OverviewLinksCard
import io.github.magisk317.uikit.surface.rememberUiKitGlassTopBar
import io.github.magisk317.uikit.surface.uiKitSurfaceGlassSample
import io.github.magisk317.uikit.theme.LocalUiKitSurfaceBlur
import io.github.magisk317.uikit.theme.spacing

/**
 * Expressive/Material implementation of the Overview page. Follows KernelSU
 * `HomeMaterial` and MiPush `OverviewMaterial` composition model: page-owned
 * `Scaffold` + collapsing top app bar driven by `exitUntilCollapsedScrollBehavior`.
 */
@Composable
internal fun OverviewScreenMaterial(
    state: OverviewUiState,
    actions: OverviewActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val scrollState = rememberScrollState()

    val topGlass = rememberUiKitGlassTopBar()
    val glassOn = LocalUiKitSurfaceBlur.current.usesBackdrop
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                modifier = Modifier.uiKitSurfaceGlassSample(topGlass),
                containerColor = if (glassOn) Color.Transparent else io.github.magisk317.uikit.surface.chromeSurfaceColor(),
                title = stringResource(id = R.string.app_name),
                scrollBehavior = scrollBehavior,
                windowInsets = WindowInsets.statusBars,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (glassOn) topGlass.contentRecorder() else Modifier),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(
                        PaddingValues(
                            start = MaterialTheme.spacing.medium,
                            end = MaterialTheme.spacing.medium,
                            top = innerPadding.calculateTopPadding() + MaterialTheme.spacing.medium,
                            bottom = innerPadding.calculateBottomPadding() + 80.dp,
                        ),
                    ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                StatusCard(
                    isEnabled = state.activationStatus.isEnabled,
                    isEntitled = state.mobileAutomationAllowed,
                    showDiagnostics = state.showStatusDiagnostics,
                    diagnostics = state.statusDiagnostics,
                    onActivateClick = actions.onActivateClick,
                    onDiagnosticsToggle = actions.onToggleDiagnostics,
                )

                OverviewAppInfoCard(
                    appVersionName = state.appVersionName,
                    appVersionCode = state.appVersionCode,
                    appVersionCodeLabel = stringResource(id = UiKitR.string.uikit_version_code),
                    frameworkType = state.frameworkType,
                    frameworkVersion = state.frameworkVersion,
                    interactive = true,
                    onRootHint = if (state.hasRootAccess) null else actions.onRootHintClick,
                )

                OverviewDeviceInfoCard()

                OverviewLinksCard(
                    onJoinTelegram = actions.onJoinTelegram,
                    onJoinQqChannel = actions.onJoinQqChannel,
                    onSourceCode = actions.onSourceCode,
                    onDonate = actions.onDonate,
                )
            }
        }
    }
}
