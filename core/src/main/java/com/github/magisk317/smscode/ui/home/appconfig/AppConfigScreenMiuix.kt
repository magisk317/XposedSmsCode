package com.github.magisk317.smscode.ui.home

import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun AppConfigScreenMiuix(
    onBack: (() -> Unit)? = null,
    refreshTrigger: Int = 0,
    viewModel: AppConfigViewModel = koinViewModel(),
    scrollChromeState: io.github.magisk317.uikit.scroll.ScrollChromeState? = null,
) {
    AppConfigScreenShared(
        onBack = onBack,
        refreshTrigger = refreshTrigger,
        viewModel = viewModel,
        scrollChromeState = scrollChromeState,
    )
}
