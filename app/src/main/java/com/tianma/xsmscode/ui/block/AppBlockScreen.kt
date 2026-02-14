package com.tianma.xsmscode.ui.block

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.data.db.entity.AppInfo
import com.tianma.xsmscode.ui.common.AppIconImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import org.koin.compose.viewmodel.koinViewModel
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBlockScreen(
    hazeState: HazeState,
    hazeStyle: HazeStyle,
    onBack: (() -> Unit)? = null,
    viewModel: AppBlockViewModel = koinViewModel(),
) {
    val apps by viewModel.appsFlow.collectAsStateWithLifecycle()
    val isLoading by viewModel.loadingFlow.collectAsStateWithLifecycle()
    val hideSystemApps by viewModel.hideSystemAppsFlow.collectAsStateWithLifecycle()
    val hasChanges by viewModel.hasChangesFlow.collectAsStateWithLifecycle()
    val currentSortOption by viewModel.sortOptionFlow.collectAsStateWithLifecycle()
    val isAscending by viewModel.isAscendingFlow.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val isCompact = LocalConfiguration.current.screenWidthDp < 600

    // Initial Load
    LaunchedEffect(Unit) {
        viewModel.refreshData()
    }

    // Usage Permission Dialog State
    var showUsagePermissionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AppBlockViewModel.AppBlockEvent.SaveSuccess -> {
                    Toast.makeText(context, context.getString(R.string.pref_sync_toast), Toast.LENGTH_SHORT).show()
                    onBack?.invoke()
                }

                is AppBlockViewModel.AppBlockEvent.SaveFailed -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.save_failed))
                }

                is AppBlockViewModel.AppBlockEvent.Error -> {
                    snackbarHostState.showSnackbar(event.throwable.message ?: context.getString(R.string.save_failed))
                }

                is AppBlockViewModel.AppBlockEvent.ShowUsageStatsPermission -> {
                    showUsagePermissionDialog = true
                }
            }
        }
    }

    // Search State
    var searchQuery by remember { mutableStateOf("") }

    // Settings Menu State
    var showSettingsMenu by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val showTopDivider by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp + 72.dp // TopBar(64) + SearchBox(72)
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 0.dp), // Content starts at top
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(state = hazeState)
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    state = listState,
                    contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        AppInfoItem(
                            appInfo = app,
                            onCheckedChange = { checked -> viewModel.setBlocked(app, checked) },
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

        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .hazeEffect(hazeState, hazeStyle) {
                    forceInvalidateOnPreDraw = true
                },
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_block_settings),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                scrollBehavior = scrollBehavior,
                windowInsets = WindowInsets.statusBars,
                modifier = Modifier,
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showSettingsMenu = true }) {
                            Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.action_sort))
                        }
                        DropdownMenu(
                            expanded = showSettingsMenu,
                            onDismissRequest = { showSettingsMenu = false },
                        ) {
                            Text(
                                text = stringResource(R.string.action_sort_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_sort_by_label)) },
                                trailingIcon = {
                                    RadioButton(
                                        selected = currentSortOption == AppBlockViewModel.SortOption.LABEL,
                                        onClick = null,
                                    )
                                },
                                onClick = {
                                    viewModel.setSortOption(AppBlockViewModel.SortOption.LABEL)
                                    showSettingsMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_sort_by_pkg)) },
                                trailingIcon = {
                                    RadioButton(
                                        selected = currentSortOption == AppBlockViewModel.SortOption.PACKAGE,
                                        onClick = null,
                                    )
                                },
                                onClick = {
                                    viewModel.setSortOption(AppBlockViewModel.SortOption.PACKAGE)
                                    showSettingsMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_sort_by_usage)) },
                                trailingIcon = {
                                    RadioButton(
                                        selected = currentSortOption == AppBlockViewModel.SortOption.USAGE,
                                        onClick = null,
                                    )
                                },
                                onClick = {
                                    viewModel.setSortOption(AppBlockViewModel.SortOption.USAGE)
                                    showSettingsMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_sort_reverse)) },
                                trailingIcon = {
                                    Checkbox(
                                        checked = !isAscending,
                                        onCheckedChange = null,
                                    )
                                },
                                onClick = {
                                    viewModel.setAscending(!isAscending)
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_hide_system_apps)) },
                                trailingIcon = {
                                    Checkbox(
                                        checked = hideSystemApps,
                                        onCheckedChange = null,
                                    )
                                },
                                onClick = {
                                    viewModel.setHideSystemApps(!hideSystemApps)
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
                        searchQuery = it
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
                                searchQuery = ""
                                viewModel.doFilter("")
                            }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                    },
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )

        if (hasChanges) {
            FloatingActionButton(
                onClick = { viewModel.saveData() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                            if (isCompact) 80.dp else 16.dp,
                        end = 16.dp,
                    ),
            ) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.action_accomplish))
            }
        }
    }

    if (showUsagePermissionDialog) {
        AlertDialog(
            onDismissRequest = { showUsagePermissionDialog = false },
            title = { Text(stringResource(R.string.action_sort_by_usage)) },
            text = { Text(stringResource(R.string.usage_permission_prompt)) },
            confirmButton = {
                TextButton(onClick = {
                    showUsagePermissionDialog = false
                    try {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS),
                        )
                    } catch (ignored: Exception) {
                        // Fallback or toast
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
fun AppInfoItem(
    appInfo: AppInfo,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    ListItem(
        headlineContent = { Text(appInfo.label ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(appInfo.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            AppIconImage(
                packageName = appInfo.packageName,
                contentDescription = stringResource(R.string.app_icon_description, appInfo.label ?: ""),
            )
        },
        trailingContent = {
            Checkbox(
                checked = appInfo.blocked,
                onCheckedChange = null,
                modifier = Modifier.semantics {
                    contentDescription = context.getString(
                        if (appInfo.blocked) R.string.action_unblock_app else R.string.action_block_app,
                        appInfo.label ?: "",
                    )
                },
            )
        },
        modifier = modifier.toggleable(
            value = appInfo.blocked,
            role = Role.Checkbox,
            onValueChange = onCheckedChange,
        ),
    )
}
