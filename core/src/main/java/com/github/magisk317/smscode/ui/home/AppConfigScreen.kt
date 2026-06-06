@file:Suppress("LocalContextGetResourceValueCall")

package com.github.magisk317.smscode.ui.home

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.data.db.entity.AppInfo
import com.github.magisk317.smscode.ui.common.AppIconImage
import com.github.magisk317.smscode.ui.common.LoadingIndicatorTokens
import com.github.magisk317.smscode.ui.common.PolygonMorphLoadingIndicator
import com.github.magisk317.smscode.ui.common.SessionLoadingRegistry
import com.github.magisk317.smscode.ui.common.rememberMinDurationLoading
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeSource
import io.github.magisk317.uikit.surface.OverlayHeaderScaffold
import io.github.magisk317.uikit.surface.WorkspaceTopBarSearchOverlay
import io.github.magisk317.uikit.surface.WorkspaceListItem
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.viewmodel.koinViewModel

private const val APP_LIST_PREFETCH_DISTANCE = 12

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppConfigScreen(
    hazeState: HazeState,
    hazeStyle: HazeBlurStyle,
    onBack: (() -> Unit)? = null,
    refreshTrigger: Int = 0,
    viewModel: AppConfigViewModel = koinViewModel(),
) {
    when (currentUiKitStyle()) {
        UiKitStyle.Miuix -> AppConfigScreenMiuix(
            hazeState = hazeState,
            hazeStyle = hazeStyle,
            onBack = onBack,
            refreshTrigger = refreshTrigger,
            viewModel = viewModel,
        )

        UiKitStyle.Expressive -> AppConfigScreenMaterial(
            hazeState = hazeState,
            hazeStyle = hazeStyle,
            onBack = onBack,
            refreshTrigger = refreshTrigger,
            viewModel = viewModel,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppConfigScreenShared(
    hazeState: HazeState,
    hazeStyle: HazeBlurStyle,
    onBack: (() -> Unit)? = null,
    refreshTrigger: Int = 0,
    viewModel: AppConfigViewModel = koinViewModel(),
) {
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
    val searchQuery by viewModel.filterFlow.collectAsStateWithLifecycle()
    var showSettingsMenu by remember { mutableStateOf(false) }

    val showLoading = rememberMinDurationLoading(
        actualLoading = isLoading && shouldShowInitialLoading,
        minDurationMillis = LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS,
    )

    LaunchedEffect(isLoading, shouldShowInitialLoading, initialLoadingStarted) {
        if (!shouldShowInitialLoading) return@LaunchedEffect
        if (isLoading) {
            initialLoadingStarted = true
        } else if (initialLoadingStarted) {
            SessionLoadingRegistry.markShown("app_config")
        }
    }

    LaunchedEffect(isLoading, manualRefreshing) {
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

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) {
            manualRefreshStartedAt = SystemClock.elapsedRealtime()
            manualRefreshing = true
            viewModel.refreshData(force = true)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshData()
    }

    LaunchedEffect(Unit) {
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
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val pullToRefreshState = rememberPullToRefreshState()
    val isMiuix = io.github.magisk317.uikit.theme.currentUiKitStyle() ==
        io.github.magisk317.uikit.theme.UiKitStyle.Miuix
    val defaultTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 156.dp
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp

    LaunchedEffect(listState, apps.size, hasMoreApps, manualRefreshing, showLoading) {
        if (manualRefreshing || showLoading) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (!hasMoreApps || apps.isEmpty()) return@collect
                if (lastVisibleIndex >= apps.lastIndex - APP_LIST_PREFETCH_DISTANCE) {
                    viewModel.loadMoreApps()
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        OverlayHeaderScaffold(
            fallbackTopPadding = defaultTopPadding,
            bottomPadding = bottomPadding,
            overlayModifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .hazeEffect(hazeState) {
                    blurEffect { style = hazeStyle }
                    forceInvalidateOnPreDraw = true
                },
            overlay = {
                WorkspaceTopBarSearchOverlay(
                    title = stringResource(R.string.app_config_settings),
                    searchQuery = searchQuery,
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
                        IconButton(onClick = { showSettingsMenu = true }) {
                            Icon(Icons.Default.Tune, contentDescription = null)
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    onSearchChange = { viewModel.doFilter(it) },
                )
            },
            content = { overlayPadding ->
                val overlayTopPadding = overlayPadding.calculateTopPadding()
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
                                .hazeSource(state = hazeState)
                                .nestedScroll(scrollBehavior.nestedScrollConnection),
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(if (isMiuix) 12.dp else 0.dp),
                            contentPadding = PaddingValues(
                                top = overlayTopPadding,
                                bottom = overlayPadding.calculateBottomPadding(),
                            ),
                        ) {
                            items(apps) { app ->
                                AppConfigItem(
                                    app = app,
                                    onBlockedChange = { blocked -> viewModel.setBlocked(app.packageName, blocked) },
                                )
                                if (!isMiuix) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
        )

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
                    onClick = { showUsagePermissionDialog = false },
                )
            },
        )
    }
}

@Composable
fun AppConfigItem(
    app: AppInfo,
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
            AppIconImage(
                packageName = app.packageName,
                contentDescription = null,
            )
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
