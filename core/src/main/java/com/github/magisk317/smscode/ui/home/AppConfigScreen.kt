@file:Suppress("LocalContextGetResourceValueCall")

package com.github.magisk317.smscode.ui.home

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.data.db.entity.AppInfo
import io.github.magisk317.uikit.surface.AppIconImage
import io.github.magisk317.uikit.foundation.LoadingIndicatorTokens
import io.github.magisk317.uikit.foundation.PolygonMorphLoadingIndicator
import io.github.magisk317.uikit.foundation.SessionLoadingRegistry
import io.github.magisk317.uikit.foundation.rememberMinDurationLoading
import io.github.magisk317.uikit.surface.WorkspaceListItem
import io.github.magisk317.uikit.surface.rememberSearchOverlayState
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import org.koin.compose.viewmodel.koinViewModel

private const val APP_LIST_PREFETCH_DISTANCE = 12

internal enum class PageRefreshAction {
    INITIAL_LOAD,
    FORCE_REFRESH,
    NO_OP,
}

internal class PageRefreshTriggerConsumer {
    private var lastHandledRefreshTrigger = 0
    private var hasActivated = false

    fun consume(isActive: Boolean, refreshTrigger: Int): PageRefreshAction? {
        if (!isActive) return null
        val firstActivation = !hasActivated
        hasActivated = true
        val forceRefresh = refreshTrigger > 0 && refreshTrigger != lastHandledRefreshTrigger
        if (forceRefresh) {
            lastHandledRefreshTrigger = refreshTrigger
            return PageRefreshAction.FORCE_REFRESH
        }
        return if (firstActivation) PageRefreshAction.INITIAL_LOAD else PageRefreshAction.NO_OP
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppConfigScreen(
    onBack: (() -> Unit)? = null,
    refreshTrigger: Int = 0,
    isActive: Boolean = true,
    keepDataActive: Boolean = isActive,
    onPageDataReady: (cacheHit: Boolean) -> Unit = {},
    viewModel: AppConfigViewModel = koinViewModel(),
    scrollChromeState: io.github.magisk317.uikit.scroll.ScrollChromeState? = null,
) {
    val currentOnPageDataReady by rememberUpdatedState(onPageDataReady)
    val apps by viewModel.appsFlow.collectAsStateWithLifecycle()
    val isLoading by viewModel.loadingFlow.collectAsStateWithLifecycle()
    val hasMoreApps by viewModel.hasMoreAppsFlow.collectAsStateWithLifecycle()
    val hideSystemApps by viewModel.hideSystemAppsFlow.collectAsStateWithLifecycle()
    val currentSortOption by viewModel.sortOptionFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shouldShowInitialLoading = remember { SessionLoadingRegistry.shouldShowInitial("app_config") }
    val snackbarHostState = remember { SnackbarHostState() }

    var initialLoadingStarted by remember { mutableStateOf(false) }
    var manualRefreshing by remember { mutableStateOf(false) }
    var manualRefreshStartedAt by remember { mutableLongStateOf(0L) }
    var showUsagePermissionDialog by remember { mutableStateOf(false) }
    val searchState = rememberSearchOverlayState(
        onSearchChange = { viewModel.doFilter(it) },
    )
    var showSettingsMenu by remember { mutableStateOf(false) }

    BackHandler(enabled = searchState.expanded) { searchState.close() }
    LaunchedEffect(isActive) { if (!isActive) searchState.close() }

    val showLoading = rememberMinDurationLoading(
        actualLoading = isActive && isLoading && shouldShowInitialLoading,
        minDurationMillis = LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS,
    )

    DisposableEffect(viewModel, keepDataActive) {
        viewModel.setActive(keepDataActive)
        onDispose {
            if (keepDataActive) viewModel.setActive(false)
        }
    }

    LaunchedEffect(isActive, isLoading, shouldShowInitialLoading, initialLoadingStarted) {
        if (!isActive || !shouldShowInitialLoading) return@LaunchedEffect
        if (isLoading) {
            initialLoadingStarted = true
        } else if (initialLoadingStarted) {
            SessionLoadingRegistry.markShown("app_config")
        }
    }

    LaunchedEffect(isActive, isLoading, manualRefreshing) {
        if (!isActive) {
            manualRefreshing = false
            manualRefreshStartedAt = 0L
            return@LaunchedEffect
        }
        if (manualRefreshing && !isLoading) {
            val elapsed = if (manualRefreshStartedAt > 0L) {
                SystemClock.elapsedRealtime() - manualRefreshStartedAt
            } else {
                LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS
            }
            val remaining = (LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS - elapsed).coerceAtLeast(0L)
            if (remaining > 0L) delay(remaining)
            manualRefreshing = false
            manualRefreshStartedAt = 0L
        }
    }

    val refreshTriggerConsumer = remember { PageRefreshTriggerConsumer() }
    LaunchedEffect(isActive, refreshTrigger) {
        val action = refreshTriggerConsumer.consume(isActive, refreshTrigger)
            ?: return@LaunchedEffect
        val forceRefresh = action == PageRefreshAction.FORCE_REFRESH
        if (action == PageRefreshAction.INITIAL_LOAD || forceRefresh) {
            if (forceRefresh) {
                manualRefreshStartedAt = SystemClock.elapsedRealtime()
                manualRefreshing = true
            }
            viewModel.refreshData(force = forceRefresh)
        }
    }

    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        val cacheHit = viewModel.hasLoadedDataFlow.value
        if (!cacheHit) {
            viewModel.hasLoadedDataFlow.first { it }
        }
        currentOnPageDataReady(cacheHit)
    }

    LaunchedEffect(viewModel, isActive) {
        if (!isActive) return@LaunchedEffect
        viewModel.events.collect { event ->
            when (event) {
                is AppConfigViewModel.AppConfigEvent.Error -> {
                    snackbarHostState.showSnackbar(event.throwable.message ?: context.getString(R.string.save_failed))
                }

                is AppConfigViewModel.AppConfigEvent.ShowUsageStatsPermission -> {
                    showUsagePermissionDialog = true
                }
            }
        }
    }

    val listState = rememberLazyListState()
    io.github.magisk317.uikit.scroll.ReportLazyListScrollToChrome(listState, scrollChromeState)
    io.github.magisk317.uikit.surface.ScrollToTopEffect(listState, refreshTrigger)
    val isMiuix = currentUiKitStyle() == UiKitStyle.Miuix

    LaunchedEffect(isActive, listState, apps.size, hasMoreApps, manualRefreshing, showLoading) {
        if (!isActive || manualRefreshing || showLoading) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (!hasMoreApps || apps.isEmpty()) return@collect
                if (lastVisibleIndex >= apps.lastIndex - APP_LIST_PREFETCH_DISTANCE) {
                    viewModel.loadMoreApps()
                }
            }
    }

    val body: @Composable (PaddingValues, Modifier) -> Unit = { listPadding, scrollModifier ->
        val overlayTopPadding = listPadding.calculateTopPadding()
        val pullToRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = manualRefreshing,
            onRefresh = {
                manualRefreshStartedAt = SystemClock.elapsedRealtime()
                manualRefreshing = true
                viewModel.refreshData(force = true)
            },
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = overlayTopPadding + LoadingIndicatorTokens.OverlayTopSpacing),
                    isRefreshing = manualRefreshing,
                    state = pullToRefreshState,
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (showLoading && !manualRefreshing) {
                Box(modifier = Modifier.fillMaxSize()) {
                    PolygonMorphLoadingIndicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = overlayTopPadding + LoadingIndicatorTokens.OverlayTopSpacing),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(scrollModifier),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(if (isMiuix) 12.dp else 0.dp),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = overlayTopPadding,
                        bottom = listPadding.calculateBottomPadding(),
                    ),
                ) {
                    items(apps) { app ->
                        AppConfigItem(
                            app = app,
                            isDataActive = keepDataActive,
                            onBlockedChange = { blocked -> viewModel.setBlocked(app.packageName, blocked) },
                        )
                        if (!isMiuix) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentUiKitStyle()) {
            UiKitStyle.Miuix -> AppConfigScreenMiuix(
                onBack = onBack,
                onOpenSettings = { showSettingsMenu = true },
                scrollChromeState = scrollChromeState,
                searchState = searchState,
                listState = listState,
                body = body,
            )

            UiKitStyle.Expressive -> AppConfigScreenMaterial(
                onBack = onBack,
                onOpenSettings = { showSettingsMenu = true },
                scrollChromeState = scrollChromeState,
                searchState = searchState,
                listState = listState,
                body = body,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )

        io.github.magisk317.uikit.surface.AppBottomSheet(
            show = showSettingsMenu,
            onDismissRequest = { showSettingsMenu = false },
            title = stringResource(R.string.app_config_settings),
        ) {
            io.github.magisk317.uikit.preference.Item(
                title = stringResource(R.string.action_sort_by_label),
                summary = "",
                trailingContent = {
                    io.github.magisk317.uikit.preference.AppRadioButton(
                        selected = currentSortOption == AppConfigViewModel.SortOption.LABEL,
                        onClick = null,
                    )
                },
                onClick = {
                    viewModel.setSortOption(AppConfigViewModel.SortOption.LABEL)
                    showSettingsMenu = false
                },
            )
            io.github.magisk317.uikit.preference.Item(
                title = stringResource(R.string.action_sort_by_selection),
                summary = "",
                trailingContent = {
                    io.github.magisk317.uikit.preference.AppRadioButton(
                        selected = currentSortOption == AppConfigViewModel.SortOption.SELECTION,
                        onClick = null,
                    )
                },
                onClick = {
                    viewModel.setSortOption(AppConfigViewModel.SortOption.SELECTION)
                    showSettingsMenu = false
                },
            )
            io.github.magisk317.uikit.preference.Item(
                title = stringResource(R.string.action_sort_by_usage),
                summary = "",
                trailingContent = {
                    io.github.magisk317.uikit.preference.AppRadioButton(
                        selected = currentSortOption == AppConfigViewModel.SortOption.USAGE,
                        onClick = null,
                    )
                },
                onClick = {
                    viewModel.setSortOption(AppConfigViewModel.SortOption.USAGE)
                    showSettingsMenu = false
                },
            )
            io.github.magisk317.uikit.preference.ActionSwitchItem(
                title = stringResource(R.string.action_hide_system_apps),
                summary = "",
                checked = hideSystemApps,
                onClick = {
                    viewModel.setHideSystemApps(!hideSystemApps)
                    showSettingsMenu = false
                },
                onCheckedChange = {
                    viewModel.setHideSystemApps(it)
                },
            )
        }
    }


    if (showUsagePermissionDialog) {
        io.github.magisk317.uikit.surface.AppAlertDialog(
            onDismissRequest = { showUsagePermissionDialog = false },
            title = { Text(stringResource(R.string.action_sort_by_usage)) },
            text = { Text(stringResource(R.string.usage_permission_prompt)) },
            confirmButton = {
                io.github.magisk317.uikit.surface.AppPrimaryButton(
                    text = stringResource(R.string.confirm),
                    onClick = {
                        showUsagePermissionDialog = false
                        try {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        } catch (_: Exception) {
                        }
                    },
                )
            },
            dismissButton = {
                io.github.magisk317.uikit.surface.AppSecondaryButton(
                    text = stringResource(R.string.cancel),
                    onClick = {
                        showUsagePermissionDialog = false
                    },
                )
            },
        )
    }
}
@Composable
fun AppConfigItem(
    app: AppInfo,
    isDataActive: Boolean = true,
    onBlockedChange: (Boolean) -> Unit,
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val bgColor = if (app.blocked) {
        val base = MaterialTheme.colorScheme.errorContainer
        if (isDark) base.copy(alpha = 0.25f) else base.copy(alpha = 0.4f)
    } else {
        Color.Transparent
    }

    WorkspaceListItem(
        containerColor = bgColor,
        leadingContent = {
            if (isDataActive) {
                AppIconImage(
                    packageName = app.packageName,
                    contentDescription = null,
                )
            } else {
                Spacer(modifier = Modifier.size(40.dp))
            }
        },
        trailingContent = {
            io.github.magisk317.uikit.preference.AppSwitch(
                checked = app.blocked,
                onCheckedChange = onBlockedChange,
            )
        },
    ) {
        Text(
            text = app.label ?: app.packageName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = app.packageName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
