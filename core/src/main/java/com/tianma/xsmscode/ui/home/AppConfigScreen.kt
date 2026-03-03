package com.tianma.xsmscode.ui.home

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tianma.xsmscode.core.R
import com.tianma.xsmscode.data.db.entity.AppInfo
import com.tianma.xsmscode.ui.common.AppIconImage
import com.tianma.xsmscode.ui.common.LoadingIndicatorTokens
import com.tianma.xsmscode.ui.common.PolygonMorphLoadingIndicator
import com.tianma.xsmscode.ui.common.SessionLoadingRegistry
import com.tianma.xsmscode.ui.common.rememberMinDurationLoading
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.viewmodel.koinViewModel

private val BlockedColumnWidth = 84.dp
private val ForwardingColumnWidth = 108.dp
private const val MINI_SWITCH_SCALE = 0.75f
private const val APP_LIST_PREFETCH_DISTANCE = 12

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppConfigScreen(
    hazeState: HazeState,
    hazeStyle: HazeStyle,
    onBack: (() -> Unit)? = null,
    onAppClick: ((AppInfo) -> Unit)? = null,
    refreshTrigger: Int = 0,
    viewModel: AppConfigViewModel = koinViewModel(),
) {
    val apps by viewModel.appsFlow.collectAsStateWithLifecycle()
    val isLoading by viewModel.loadingFlow.collectAsStateWithLifecycle()
    val hasMoreApps by viewModel.hasMoreAppsFlow.collectAsStateWithLifecycle()
    val hideSystemApps by viewModel.hideSystemAppsFlow.collectAsStateWithLifecycle()
    val currentSortOption by viewModel.sortOptionFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current
    val shouldShowInitialLoading = remember { SessionLoadingRegistry.shouldShowInitial("app_config") }
    val snackbarHostState = remember { SnackbarHostState() }

    var initialLoadingStarted by remember { mutableStateOf(false) }
    var manualRefreshing by remember { mutableStateOf(false) }
    var manualRefreshStartedAt by remember { mutableLongStateOf(0L) }
    var showUsagePermissionDialog by remember { mutableStateOf(false) }
    val searchQuery by viewModel.filterFlow.collectAsStateWithLifecycle()
    var showSettingsMenu by remember { mutableStateOf(false) }
    var fixedTopHeightPx by remember { mutableIntStateOf(0) }

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
    val defaultTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 156.dp
    val fixedTopHeight = if (fixedTopHeightPx > 0) with(density) { fixedTopHeightPx.toDp() } else defaultTopPadding
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
                        .padding(top = fixedTopHeight + LoadingIndicatorTokens.OverlayTopSpacing),
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
                            .padding(top = fixedTopHeight + LoadingIndicatorTokens.OverlayTopSpacing),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(state = hazeState)
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    state = listState,
                    contentPadding = PaddingValues(top = fixedTopHeight, bottom = bottomPadding),
                ) {
                    items(apps) { app ->
                        AppConfigItem(
                            app = app,
                            onClick = { onAppClick?.invoke(app) },
                            onBlockedChange = {
                                viewModel.setBlocked(app, it)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.pref_sync_toast),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            onForwardingChange = {
                                viewModel.setForwarding(app, it)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.pref_sync_toast),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .onSizeChanged { fixedTopHeightPx = it.height }
                .hazeEffect(hazeState, hazeStyle) {
                    forceInvalidateOnPreDraw = true
                },
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_config_settings),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                scrollBehavior = scrollBehavior,
                windowInsets = WindowInsets.statusBars,
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showSettingsMenu = true }) {
                            Icon(Icons.Default.Tune, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showSettingsMenu,
                            onDismissRequest = { showSettingsMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_sort_by_label)) },
                                trailingIcon = {
                                    RadioButton(
                                        selected = currentSortOption == AppConfigViewModel.SortOption.LABEL,
                                        onClick = null,
                                    )
                                },
                                onClick = {
                                    viewModel.setSortOption(AppConfigViewModel.SortOption.LABEL)
                                    showSettingsMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_sort_by_selection)) },
                                trailingIcon = {
                                    RadioButton(
                                        selected = currentSortOption == AppConfigViewModel.SortOption.SELECTION,
                                        onClick = null,
                                    )
                                },
                                onClick = {
                                    viewModel.setSortOption(AppConfigViewModel.SortOption.SELECTION)
                                    showSettingsMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_sort_by_usage)) },
                                trailingIcon = {
                                    RadioButton(
                                        selected = currentSortOption == AppConfigViewModel.SortOption.USAGE,
                                        onClick = null,
                                    )
                                },
                                onClick = {
                                    viewModel.setSortOption(AppConfigViewModel.SortOption.USAGE)
                                    showSettingsMenu = false
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_hide_system_apps)) },
                                trailingIcon = {
                                    Checkbox(checked = hideSystemApps, onCheckedChange = null)
                                },
                                onClick = {
                                    viewModel.setHideSystemApps(!hideSystemApps)
                                    showSettingsMenu = false
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        viewModel.doFilter(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.action_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                viewModel.doFilter("")
                            }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                    },
                )
            }
            AppConfigHeader()
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }

    if (showUsagePermissionDialog) {
        AlertDialog(
            onDismissRequest = { showUsagePermissionDialog = false },
            title = { Text(stringResource(R.string.action_sort_by_usage)) },
            text = { Text(stringResource(R.string.usage_permission_prompt)) },
            confirmButton = {
                Button(onClick = {
                    showUsagePermissionDialog = false
                    try {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    } catch (_: Exception) {
                    }
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUsagePermissionDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
fun AppConfigHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.label_app_info),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.label_blocked),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(BlockedColumnWidth),
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Text(
            text = stringResource(R.string.label_forwarding),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(ForwardingColumnWidth),
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
fun AppConfigItem(
    app: AppInfo,
    onClick: () -> Unit,
    onBlockedChange: (Boolean) -> Unit,
    onForwardingChange: (Boolean) -> Unit,
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val bgColor = when {
        app.blocked && app.forwarding -> {
            val base = MaterialTheme.colorScheme.secondaryContainer
            if (isDark) base.copy(alpha = 0.25f) else base.copy(alpha = 0.4f)
        }

        app.blocked -> {
            val base = MaterialTheme.colorScheme.errorContainer
            if (isDark) base.copy(alpha = 0.25f) else base.copy(alpha = 0.4f)
        }

        app.forwarding -> {
            val base = MaterialTheme.colorScheme.primaryContainer
            if (isDark) base.copy(alpha = 0.25f) else base.copy(alpha = 0.4f)
        }

        else -> Color.Transparent
    }

    Box(modifier = Modifier.background(bgColor)) {
        ListItem(
            modifier = Modifier.clickable(onClick = onClick),
            headlineContent = {
                Text(
                    app.label ?: app.packageName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                )
            },
            supportingContent = {
                Text(
                    app.packageName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                )
            },
            leadingContent = {
                AppIconImage(
                    packageName = app.packageName,
                    contentDescription = null,
                )
            },
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    MiniSwitch(
                        checked = app.blocked,
                        onCheckedChange = onBlockedChange,
                        modifier = Modifier.width(BlockedColumnWidth),
                    )
                    MiniSwitch(
                        checked = app.forwarding,
                        onCheckedChange = onForwardingChange,
                        modifier = Modifier.width(ForwardingColumnWidth),
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun MiniSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier
            .height(24.dp)
            .scale(MINI_SWITCH_SCALE),
        thumbContent = null,
    )
}
