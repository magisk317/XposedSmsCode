package com.github.magisk317.smscode.ui.smscoderule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.magisk317.smscode.core.R
import io.github.magisk317.uikit.surface.DoubleTapToTopOverlay
import io.github.magisk317.uikit.surface.PageScaffoldExpressive
import io.github.magisk317.uikit.surface.ScrollToTopFAB
import kotlinx.coroutines.launch

/**
 * Expressive/Material chrome for the SMS code rule list. Wraps the kit page
 * scaffold with the F7 quick return-to-top affordances: a transparent
 * double-tap hotspot over the top bar title strip (clear of the back arrow
 * and the actions) plus a scroll-to-top FAB lifted above the page's own
 * add FAB.
 */
@Composable
internal fun SmsCodeRuleListScreenMaterial(
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    listState: LazyListState,
    body: @Composable (PaddingValues, Modifier) -> Unit,
) {
    val scrollScope = rememberCoroutineScope()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(modifier = Modifier.fillMaxSize()) {
        PageScaffoldExpressive(
            title = stringResource(id = R.string.rule_list),
            onBack = onBack,
            actions = actions,
            snackbarHost = snackbarHost,
            floatingActionButton = floatingActionButton,
            content = body,
        )
        DoubleTapToTopOverlay(
            onDoubleTap = {
                scrollScope.launch { listState.animateScrollToItem(0) }
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 72.dp, top = statusBarTop, end = 112.dp)
                .fillMaxWidth()
                .height(64.dp),
        )
        // The page owns a bottom-end add FAB; lift the scroll-to-top FAB above it.
        ScrollToTopFAB(
            listState = listState,
            visible = true,
            extraBottomPadding = 96.dp,
        )
    }
}

@Composable
internal fun SmsCodeRuleEditorScreenMaterial(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    body: @Composable (PaddingValues, Modifier) -> Unit,
) {
    PageScaffoldExpressive(
        title = title,
        onBack = onBack,
        actions = actions,
        snackbarHost = snackbarHost,
        content = body,
    )
}
