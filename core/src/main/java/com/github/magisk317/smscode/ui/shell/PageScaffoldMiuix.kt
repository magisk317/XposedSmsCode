package com.github.magisk317.smscode.ui.shell

import androidx.compose.foundation.layout.Box
import top.yukonga.miuix.kmp.theme.MiuixTheme
import io.github.magisk317.uikit.surface.uiKitSurfaceGlassSample
import io.github.magisk317.uikit.surface.rememberUiKitGlassTopBar
import io.github.magisk317.uikit.theme.LocalUiKitSurfaceBlur
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import io.github.magisk317.uikit.surface.AppIconButton
import io.github.magisk317.uikit.surface.chromeSurfaceColor
import io.github.magisk317.uikit.surface.surfaceBlurContainerColor
import io.github.magisk317.uikit.surface.uiKitSurfaceBlur
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * Miuix secondary-page chrome (Architecture A): page-owned miuix `Scaffold` +
 * collapsing `TopAppBar` driven by `MiuixScrollBehavior`, mirroring the KernelSU
 * `HomeMiuix` model. `content` receives the resolved list padding and the
 * style-owned scroll modifier (end-of-scroll haptics + nested-scroll connection)
 * that the page body must apply to its scrollable container.
 *
 * `overScrollVertical()` + `overscrollEffect = null` are intentionally omitted:
 * the global `MiuixOverscrollFactory` under `MagiskUiKitTheme` already provides
 * the bounce (copying KernelSU verbatim would stack a second overscroll).
 */
@Composable
internal fun PageScaffoldMiuix(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (listPadding: PaddingValues, scrollModifier: Modifier) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val topGlass = rememberUiKitGlassTopBar()
    val glassOn = LocalUiKitSurfaceBlur.current.usesBackdrop
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.uiKitSurfaceGlassSample(topGlass),
                                title = title,
                navigationIcon = {
                    onBack?.let { callback ->
                        AppIconButton(onClick = callback) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    }
                },
                actions = actions,
                scrollBehavior = scrollBehavior,
                color = if (glassOn) Color.Transparent else chromeSurfaceColor(),
                defaultWindowInsetsPadding = true,
            )
        },
        contentWindowInsets = WindowInsets.systemBars
            .union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().then(if (glassOn) topGlass.contentRecorder() else Modifier).nestedScroll(scrollBehavior.nestedScrollConnection)) {
            content(
                PaddingValues(top = innerPadding.calculateTopPadding()),
                Modifier.scrollEndHaptic(),
            )
            Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                floatingActionButton()
            }
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                snackbarHost()
            }
        }
    }
}
