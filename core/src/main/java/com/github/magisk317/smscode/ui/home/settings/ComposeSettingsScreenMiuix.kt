package com.github.magisk317.smscode.ui.home

import androidx.compose.runtime.Composable
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle

@Composable
internal fun ComposeSettingsScreenMiuix(
    hazeState: HazeState,
    hazeStyle: HazeBlurStyle,
    viewModel: SettingsViewModel? = null,
    refreshTrigger: Int = 0,
    onExit: () -> Unit = {},
) {
    ComposeSettingsScreenShared(
        hazeState = hazeState,
        hazeStyle = hazeStyle,
        viewModel = viewModel,
        refreshTrigger = refreshTrigger,
        onExit = onExit,
    )
}
