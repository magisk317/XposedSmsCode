@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.github.magisk317.smscode.ui.record

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.magisk317.smscode.core.R
import io.github.magisk317.uikit.scroll.ScrollChromeState
import io.github.magisk317.uikit.surface.AppTopBar
import io.github.magisk317.uikit.surface.DoubleTapToTopOverlay
import io.github.magisk317.uikit.surface.ScrollToTopFAB
import io.github.magisk317.uikit.surface.chromeSurfaceColor
import io.github.magisk317.uikit.surface.rememberUiKitGlassTopBar
import io.github.magisk317.uikit.surface.uiKitSurfaceGlassSample
import io.github.magisk317.uikit.theme.LocalUiKitSurfaceBlur
import kotlinx.coroutines.launch

/**
 * Expressive/Material chrome for [CodeRecordScreen]: collapsing M3 top bar
 * (exitUntilCollapsed) floating over the pager-coordinated record list. The
 * measured bar height is fed back as the list's top padding so content never
 * hides under the overlay header, and the pager header offset keeps
 * translating the bar while swiping tabs. When the theme backdrop is active
 * the bar frosts over the recorded list (same glass model as
 * OverviewScreenMaterial / MiPush EventListExpressive).
 * Quick return-to-top: double-tap the bar title strip or use the FAB that
 * appears once the bar has collapsed.
 */
@Composable
internal fun CodeRecordScreenMaterial(
    title: String,
    isSelectionMode: Boolean,
    onBack: (() -> Unit)?,
    onExitSelectionMode: () -> Unit,
    onSelectAllVisible: () -> Unit,
    onDeleteSelected: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExport: () -> Unit,
    scrollChromeState: ScrollChromeState?,
    listState: LazyListState,
    body: @Composable (PaddingValues, Modifier) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val topGlass = rememberUiKitGlassTopBar()
    val glassOn = LocalUiKitSurfaceBlur.current.usesBackdrop
    val scrollScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    val headerOffset = with(density) {
        (scrollChromeState?.animatedHeaderOffsetY ?: 0f).coerceAtMost(0f).toDp()
    }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val defaultTopPadding = statusBarTop + 120.dp
    val measuredTopHeight = if (topBarHeightPx > 0) {
        with(density) { topBarHeightPx.toDp() }
    } else {
        defaultTopPadding
    }
    val topPadding = (measuredTopHeight + headerOffset).coerceAtLeast(0.dp)
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (glassOn) topGlass.contentRecorder() else Modifier),
        ) {
            body(
                PaddingValues(top = topPadding, bottom = bottomPadding),
                Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .offset(y = headerOffset)
                .onSizeChanged {
                    topBarHeightPx = it.height
                    scrollChromeState?.headerHeightPx = it.height.toFloat()
                },
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                AppTopBar(
                    modifier = Modifier.uiKitSurfaceGlassSample(topGlass),
                    title = title,
                    navigationIcon = {
                        if (isSelectionMode) {
                            IconButton(onClick = onExitSelectionMode) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back),
                                )
                            }
                        } else if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back),
                                )
                            }
                        }
                    },
                    actions = {
                        if (isSelectionMode) {
                            IconButton(onClick = onSelectAllVisible) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = stringResource(R.string.action_select_all),
                                )
                            }
                            IconButton(onClick = onDeleteSelected) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                )
                            }
                        } else {
                            IconButton(onClick = onOpenSettings) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = stringResource(R.string.pref_code_records_title),
                                )
                            }
                            IconButton(onClick = onOpenExport) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_export),
                                    contentDescription = stringResource(R.string.action_export_rules),
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    windowInsets = WindowInsets.statusBars,
                    // The bar paints its own surface instead of the surrounding Column:
                    // the Column is measured taller than the bar (the 64dp double-tap
                    // hotspot below it inflates the height that feeds the list's top
                    // padding), so a Column-wide background would extend past the bar and
                    // leave the first record flush against the title. Painting only the
                    // bar keeps the same safe distance the app-list page gets from its
                    // overlay header.
                    containerColor = if (glassOn) Color.Transparent else chromeSurfaceColor(),
                    scrolledContainerColor = if (glassOn) Color.Transparent else chromeSurfaceColor(),
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
            }
        }
        ScrollToTopFAB(
            listState = listState,
            visible = scrollChromeState?.isChromeVisible != true,
            extraBottomPadding = bottomPadding,
        )
    }
}
