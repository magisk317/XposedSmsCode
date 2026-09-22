package com.github.magisk317.smscode.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.github.magisk317.smscode.core.R
import io.github.magisk317.uikit.surface.chromeSurfaceColor
import io.github.magisk317.uikit.surface.rememberUiKitGlassTopBar
import io.github.magisk317.uikit.surface.uiKitSurfaceGlassSample
import io.github.magisk317.uikit.theme.LocalUiKitSurfaceBlur
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * Miuix chrome for the Settings tab. Follows the KernelSU `HomeMiuix` /
 * MiPush `SettingsMiuix` model: page-owned miuix Scaffold + collapsing
 * TopAppBar driven by MiuixScrollBehavior. The body only receives
 * (listPadding, scrollModifier) and stays style-agnostic.
 */
@Composable
internal fun ComposeSettingsScreenMiuix(
    viewModel: SettingsViewModel? = null,
    refreshTrigger: Int = 0,
    onExit: () -> Unit = {},
) {
    val scrollBehavior = MiuixScrollBehavior()
    val topGlass = rememberUiKitGlassTopBar()
    val glassOn = LocalUiKitSurfaceBlur.current.usesBackdrop
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.uiKitSurfaceGlassSample(topGlass),
                title = stringResource(id = R.string.pref_general_title),
                scrollBehavior = scrollBehavior,
                color = if (glassOn) Color.Transparent else chromeSurfaceColor(),
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
                .then(if (glassOn) topGlass.contentRecorder() else Modifier),
        ) {
            ComposeSettingsScreenBody(
                viewModel = viewModel,
                refreshTrigger = refreshTrigger,
                onExit = onExit,
                listPadding = PaddingValues(top = innerPadding.calculateTopPadding()),
                scrollModifier = Modifier
                    .scrollEndHaptic()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            )
        }
    }
}
