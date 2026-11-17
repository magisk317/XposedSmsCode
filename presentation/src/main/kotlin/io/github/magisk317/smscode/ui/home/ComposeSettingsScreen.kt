@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("LocalContextGetResourceValueCall")

package io.github.magisk317.smscode.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle

internal data class SettingsPageRuntime(
    val isActive: Boolean = true,
    val keepDataActive: Boolean = isActive,
    val onPageDataReady: (cacheHit: Boolean) -> Unit = {},
)

internal val LocalSettingsPageRuntime = staticCompositionLocalOf { SettingsPageRuntime() }

internal val LocalThemeSettingsNavigation = staticCompositionLocalOf<(() -> Unit)?> { null }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Suppress("CyclomaticComplexMethod")
@Composable
fun ComposeSettingsScreen(
    viewModel: SettingsViewModel? = null,
    refreshTrigger: Int = 0,
    isActive: Boolean = true,
    keepDataActive: Boolean = isActive,
    onPageDataReady: (cacheHit: Boolean) -> Unit = {},
    onExit: () -> Unit = {},
) {
    CompositionLocalProvider(
        LocalSettingsPageRuntime provides SettingsPageRuntime(
            isActive = isActive,
            keepDataActive = keepDataActive,
            onPageDataReady = onPageDataReady,
        ),
    ) {
        when (currentUiKitStyle()) {
            UiKitStyle.Miuix -> ComposeSettingsScreenMiuix(
                viewModel = viewModel,
                refreshTrigger = refreshTrigger,
                onExit = onExit,
            )

            UiKitStyle.Expressive -> ComposeSettingsScreenMaterial(
                viewModel = viewModel,
                refreshTrigger = refreshTrigger,
                onExit = onExit,
            )
        }
    }
}

