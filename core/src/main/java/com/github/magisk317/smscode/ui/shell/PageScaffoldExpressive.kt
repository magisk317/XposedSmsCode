@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.github.magisk317.smscode.ui.shell

import androidx.compose.foundation.layout.Box
import io.github.magisk317.uikit.surface.uiKitSurfaceGlassSample
import io.github.magisk317.uikit.surface.rememberUiKitGlassTopBar
import io.github.magisk317.uikit.theme.LocalUiKitSurfaceBlur
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import io.github.magisk317.uikit.surface.AppIconButton
import io.github.magisk317.uikit.surface.AppTopBar

/**
 * Expressive/Material secondary-page chrome (Architecture A): material3
 * `Scaffold` + collapsing top app bar driven by
 * `exitUntilCollapsedScrollBehavior` (the Overview/KernelSU `HomeMaterial`
 * template). The bar itself goes through uikit `AppTopBar` (which, on this
 * guaranteed path, is the material3 bar plus chrome colors and surface blur).
 * The nested-scroll connection is handed to page bodies via the injected
 * scroll modifier so bodies stay style-agnostic.
 */
@Composable
internal fun PageScaffoldExpressive(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (listPadding: PaddingValues, scrollModifier: Modifier) -> Unit,
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
                containerColor = if (glassOn) Color.Transparent else io.github.magisk317.uikit.surface.chromeSurfaceColor(),
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
                windowInsets = WindowInsets.statusBars,
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().then(if (glassOn) topGlass.contentRecorder() else Modifier)) {
            content(
                PaddingValues(top = innerPadding.calculateTopPadding()),
                Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
