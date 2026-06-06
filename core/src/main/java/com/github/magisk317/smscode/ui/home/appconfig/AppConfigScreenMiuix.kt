package com.github.magisk317.smscode.ui.home

import androidx.compose.runtime.Composable
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun AppConfigScreenMiuix(
    hazeState: HazeState,
    hazeStyle: HazeBlurStyle,
    onBack: (() -> Unit)? = null,
    refreshTrigger: Int = 0,
    viewModel: AppConfigViewModel = koinViewModel(),
) {
    AppConfigScreenShared(
        hazeState = hazeState,
        hazeStyle = hazeStyle,
        onBack = onBack,
        refreshTrigger = refreshTrigger,
        viewModel = viewModel,
    )
}
