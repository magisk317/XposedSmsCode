@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.github.magisk317.smscode.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.magisk317.smscode.core.R
import io.github.magisk317.uikit.scroll.ScrollChromeState
import io.github.magisk317.uikit.surface.DoubleTapToTopOverlay
import io.github.magisk317.uikit.surface.OverlayHeaderScaffold
import io.github.magisk317.uikit.surface.ScrollToTopFAB
import io.github.magisk317.uikit.surface.SearchOverlayContent
import io.github.magisk317.uikit.surface.SearchOverlayState
import io.github.magisk317.uikit.surface.rememberUiKitGlassTopBar
import io.github.magisk317.uikit.theme.LocalUiKitSurfaceBlur
import kotlinx.coroutines.launch

/**
 * Expressive/Material chrome for [AppConfigScreen]: overlay search header
 * with a collapsing M3 top bar (exitUntilCollapsed) above the
 * pager-coordinated app list. The overlay height measured by
 * [OverlayHeaderScaffold] becomes the list's top padding, and the material
 * scroll behavior drives both the top bar collapse and the list's nested
 * scroll connection. When the theme backdrop is active the collapsed search
 * bar frosts over the recorded list (glass model shared with
 * OverviewScreenMaterial / MiPush ApplicationListExpressive).
 * Quick return-to-top: double-tap the bar title strip or use the FAB that
 * appears once the bar has collapsed.
 */
@Composable
internal fun AppConfigScreenMaterial(
    onBack: (() -> Unit)?,
    onOpenSettings: () -> Unit,
    scrollChromeState: ScrollChromeState?,
    searchState: SearchOverlayState,
    listState: LazyListState,
    body: @Composable (PaddingValues, Modifier) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val topGlass = rememberUiKitGlassTopBar()
    val glassOn = LocalUiKitSurfaceBlur.current.usesBackdrop
    val scrollScope = rememberCoroutineScope()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val defaultTopPadding = statusBarTop + 156.dp
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp
    OverlayHeaderScaffold(
        fallbackTopPadding = defaultTopPadding,
        bottomPadding = bottomPadding,
        headerOffsetY = scrollChromeState?.animatedHeaderOffsetY ?: 0f,
        onHeaderHeightChanged = { scrollChromeState?.headerHeightPx = it.toFloat() },
        overlayModifier = Modifier
            .fillMaxWidth(),
        overlay = {
            Box(modifier = Modifier.fillMaxWidth()) {
                SearchOverlayContent(
                    state = searchState,
                    title = stringResource(R.string.app_config_settings),
                    searchPlaceholder = stringResource(R.string.action_search),
                    navigationIcon = if (onBack != null) {
                        {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back),
                                )
                            }
                        }
                    } else {
                        null
                    },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Tune, contentDescription = null)
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    glassTopBar = topGlass,
                )
                // Hidden while the search field is expanded so the hotspot
                // never fights the text field for taps.
                if (!searchState.expanded) {
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
                }
            }
        },
        content = { overlayPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                body(
                    overlayPadding,
                    Modifier
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .then(if (glassOn) topGlass.contentRecorder() else Modifier),
                )
                ScrollToTopFAB(
                    listState = listState,
                    visible = scrollChromeState?.isChromeVisible != true,
                    extraBottomPadding = bottomPadding,
                )
            }
        },
    )
}
