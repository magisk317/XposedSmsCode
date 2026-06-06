package com.github.magisk317.smscode.ui.app.base

import androidx.compose.runtime.Composable
import androidx.activity.ComponentActivity
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import androidx.compose.ui.unit.Dp
import io.github.magisk317.uikit.theme.SystemBarsScrim as UiKitSystemBarsScrim
import io.github.magisk317.uikit.theme.UpdateSystemBars as UpdateUiKitSystemBars
import io.github.magisk317.uikit.theme.applyEdgeToEdge as applyUiKitEdgeToEdge
import io.github.magisk317.uikit.theme.rememberHazeStyle as rememberUiKitHazeStyle

fun applyEdgeToEdge(activity: ComponentActivity) {
    applyUiKitEdgeToEdge(activity)
}

@Composable
fun SystemBarsScrim(hazeState: HazeState, hazeStyle: HazeBlurStyle) {
    UiKitSystemBarsScrim(hazeState = hazeState, hazeStyle = hazeStyle)
}

@Composable
fun rememberHazeStyle(
    blurRadius: Dp = 25.dp,
    tintAlpha: Float = 0.2f,
): HazeBlurStyle = rememberUiKitHazeStyle(
    blurRadius = blurRadius,
    tintAlpha = tintAlpha,
)

@Composable
fun UpdateSystemBars(darkTheme: Boolean) {
    UpdateUiKitSystemBars(darkTheme)
}
