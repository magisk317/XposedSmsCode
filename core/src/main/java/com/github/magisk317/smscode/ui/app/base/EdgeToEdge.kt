package com.github.magisk317.smscode.ui.app.base

import androidx.compose.runtime.Composable
import androidx.activity.ComponentActivity
import io.github.magisk317.uikit.theme.SystemBarsScrim as UiKitSystemBarsScrim
import io.github.magisk317.uikit.theme.UpdateSystemBars as UpdateUiKitSystemBars
import io.github.magisk317.uikit.theme.applyEdgeToEdge as applyUiKitEdgeToEdge

fun applyEdgeToEdge(activity: ComponentActivity) {
    applyUiKitEdgeToEdge(activity)
}

@Composable
fun SystemBarsScrim() {
    UiKitSystemBarsScrim()
}

@Composable
fun UpdateSystemBars(darkTheme: Boolean) {
    UpdateUiKitSystemBars(darkTheme)
}
