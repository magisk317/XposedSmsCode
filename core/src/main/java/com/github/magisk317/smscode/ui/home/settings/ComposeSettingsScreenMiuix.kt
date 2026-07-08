package com.github.magisk317.smscode.ui.home

import androidx.compose.runtime.Composable

@Composable
internal fun ComposeSettingsScreenMiuix(
    viewModel: SettingsViewModel? = null,
    refreshTrigger: Int = 0,
    onExit: () -> Unit = {},
) {
    ComposeSettingsScreenShared(
        viewModel = viewModel,
        refreshTrigger = refreshTrigger,
        onExit = onExit,
    )
}
