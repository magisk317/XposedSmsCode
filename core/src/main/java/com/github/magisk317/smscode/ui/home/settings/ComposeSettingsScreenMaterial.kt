@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.github.magisk317.smscode.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.github.magisk317.smscode.core.R
import io.github.magisk317.uikit.surface.AppTopBar
import io.github.magisk317.uikit.surface.chromeSurfaceColor
import io.github.magisk317.uikit.surface.rememberUiKitGlassTopBar
import io.github.magisk317.uikit.surface.uiKitSurfaceGlassSample
import io.github.magisk317.uikit.theme.LocalUiKitSurfaceBlur

/**
 * Expressive/Material chrome for the Settings tab. Follows the KernelSU
 * `HomeMaterial` / MiPush `SettingsExpressive` model: page-owned M3 Scaffold +
 * collapsing AppTopBar driven by exitUntilCollapsedScrollBehavior. The body
 * only receives (listPadding, scrollModifier) and stays style-agnostic.
 */
@Composable
internal fun ComposeSettingsScreenMaterial(
    viewModel: SettingsViewModel? = null,
    refreshTrigger: Int = 0,
    onExit: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val topGlass = rememberUiKitGlassTopBar()
    val glassOn = LocalUiKitSurfaceBlur.current.usesBackdrop
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                modifier = Modifier.uiKitSurfaceGlassSample(topGlass),
                containerColor = if (glassOn) Color.Transparent else chromeSurfaceColor(),
                title = stringResource(id = R.string.pref_general_title),
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
            ComposeSettingsScreenBody(
                viewModel = viewModel,
                refreshTrigger = refreshTrigger,
                onExit = onExit,
                listPadding = PaddingValues(top = innerPadding.calculateTopPadding()),
                scrollModifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            )
        }
    }
}
