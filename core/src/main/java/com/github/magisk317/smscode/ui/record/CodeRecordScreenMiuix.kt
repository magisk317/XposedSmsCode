package com.github.magisk317.smscode.ui.record

import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun CodeRecordScreenMiuix(
    onBack: (() -> Unit)? = null,
    refreshTrigger: Int = 0,
    viewModel: CodeRecordViewModel = koinViewModel(),
    scrollChromeState: io.github.magisk317.uikit.scroll.ScrollChromeState? = null,
) {
    CodeRecordScreenShared(
        onBack = onBack,
        refreshTrigger = refreshTrigger,
        viewModel = viewModel,
        scrollChromeState = scrollChromeState,
    )
}
