package com.github.magisk317.smscode.ui.home

import androidx.compose.runtime.Composable
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle

@Composable
internal fun OverviewScreenMiuix(
    hazeState: HazeState,
    hazeStyle: HazeBlurStyle,
) {
    OverviewScreenShared(
        hazeState = hazeState,
        hazeStyle = hazeStyle,
    )
}
