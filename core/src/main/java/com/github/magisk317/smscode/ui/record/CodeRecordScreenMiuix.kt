package com.github.magisk317.smscode.ui.record

import androidx.compose.runtime.Composable
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun CodeRecordScreenMiuix(
    hazeState: HazeState,
    hazeStyle: HazeBlurStyle,
    onBack: (() -> Unit)? = null,
    refreshTrigger: Int = 0,
    viewModel: CodeRecordViewModel = koinViewModel(),
    scrollChromeState: io.github.magisk317.uikit.scroll.ScrollChromeState? = null,
) {
    CodeRecordScreenShared(
        hazeState = hazeState,
        hazeStyle = hazeStyle,
        onBack = onBack,
        refreshTrigger = refreshTrigger,
        viewModel = viewModel,
        scrollChromeState = scrollChromeState,
    )
}
