package com.tianma.xsmscode.ui.record

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.rememberDismissState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.common.constant.PrefConst
import com.tianma.xsmscode.common.utils.AppPreferencesDataStore
import com.tianma.xsmscode.data.db.entity.SmsMsg
import com.tianma.xsmscode.ui.common.AppIconImage
import com.tianma.xsmscode.ui.home.Item
import com.tianma.xsmscode.ui.home.RetentionDialog
import com.tianma.xsmscode.ui.home.SectionHeader
import com.tianma.xsmscode.ui.home.SwitchItem
import com.tianma.xsmscode.ui.home.TextInputDialog
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun CodeRecordScreen(
    hazeState: HazeState,
    hazeStyle: HazeStyle,
    onBack: (() -> Unit)? = null,
    viewModel: CodeRecordViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val smsList = uiState.smsList
    val isLoading = uiState.isLoading
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    // Initial Load
    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    // Selection State
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    var historyLimit by remember { mutableStateOf("0") }
    var showHistoryLimitDialog by remember { mutableStateOf(false) }
    var showHistoryLimitInput by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        historyLimit = AppPreferencesDataStore.getString(context, PrefConst.KEY_HISTORY_LIMIT, "0")
    }

    // Detail Dialog State
    var detailSmsMsg by remember { mutableStateOf<SmsMsg?>(null) }

    // Logic to toggle selection mode
    fun toggleSelection(id: Long) {
        val newSelection = selectedIds.toMutableSet()
        if (newSelection.contains(id)) {
            newSelection.remove(id)
        } else {
            newSelection.add(id)
        }
        selectedIds = newSelection
        if (newSelection.isEmpty()) {
            isSelectionMode = false
        }
    }

    // Back Handler
    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedIds = emptySet()
    }

    // Move deleteAndUndo outside items block and remember it
    val deleteAndUndo = remember(viewModel, scope, context, snackbarHostState) {
        { target: SmsMsg ->
            viewModel.removeSmsMsg(listOf(target))
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = context.getString(R.string.some_items_removed, 1),
                    actionLabel = context.getString(R.string.revoke),
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.restoreSmsMsgList(listOf(target))
                }
            }
        }
    }

    fun deleteSelected() {
        val deleteList = smsList.filter { sms -> sms.id != null && selectedIds.contains(sms.id) }
        if (deleteList.isEmpty()) return

        viewModel.removeSmsMsg(deleteList)
        isSelectionMode = false
        selectedIds = emptySet()

        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.some_items_removed, deleteList.size),
                actionLabel = context.getString(R.string.revoke),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restoreSmsMsgList(deleteList)
            }
        }
    }

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                viewModel.exportRecords(context, uri)
            }
        }

    if (detailSmsMsg != null) {
        AlertDialog(
            onDismissRequest = { detailSmsMsg = null },
            title = { Text(stringResource(R.string.message_details)) },
            text = {
                SelectionContainer {
                    Text(detailSmsMsg?.body ?: "")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val code = detailSmsMsg?.smsCode
                        if (!code.isNullOrEmpty()) {
                            clipboardManager.setText(AnnotatedString(code))
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.prompt_sms_code_copied, code))
                            }
                        }
                        detailSmsMsg = null
                    },
                ) {
                    Text(stringResource(R.string.copy_smscode))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val body = detailSmsMsg?.body
                        if (!body.isNullOrEmpty()) {
                            clipboardManager.setText(AnnotatedString(body))
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.prompt_sms_copied))
                            }
                        }
                        detailSmsMsg = null
                    },
                ) {
                    Text(stringResource(R.string.copy_sms))
                }
            },
        )
    }

    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SectionHeader(text = stringResource(id = R.string.pref_code_records_title))
                SwitchItem(
                    title = stringResource(id = R.string.pref_enable_code_records_title),
                    summary = "",
                    key = PrefConst.KEY_ENABLE_CODE_RECORDS,
                    defaultValue = true,
                )

                Item(
                    title = stringResource(id = R.string.pref_history_limit_title),
                    summary = run {
                        val entries = stringArrayResource(id = R.array.history_limit_entry_list)
                        val values = stringArrayResource(id = R.array.history_limit_value_list)
                        val index = values.indexOf(historyLimit)
                        if (index >= 0) entries[index] else "$historyLimit ${stringResource(R.string.smscode_records)}"
                    },
                ) { showHistoryLimitDialog = true }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showHistoryLimitDialog) {
        RetentionDialog(
            selectedValue = historyLimit,
            onDismiss = { showHistoryLimitDialog = false },
            titleId = R.string.pref_history_limit_title,
            entriesId = R.array.history_limit_entry_list,
            valuesId = R.array.history_limit_value_list,
        ) { value ->
            if (value == "-1") {
                showHistoryLimitInput = true
            } else {
                historyLimit = value
                scope.launch {
                    AppPreferencesDataStore.setString(context, PrefConst.KEY_HISTORY_LIMIT, value)
                    AppPreferencesDataStore.syncToSharedPrefs(context)
                }
            }
            showHistoryLimitDialog = false
        }
    }

    if (showHistoryLimitInput) {
        TextInputDialog(
            title = stringResource(id = R.string.history_limit_custom_entry),
            initialValue = if (historyLimit == "0" || historyLimit == "-1") "" else historyLimit,
            onDismiss = { showHistoryLimitInput = false },
        ) { value ->
            if (value.all { it.isDigit() } && value.isNotEmpty()) {
                historyLimit = value
                scope.launch {
                    AppPreferencesDataStore.setString(context, PrefConst.KEY_HISTORY_LIMIT, value)
                    AppPreferencesDataStore.syncToSharedPrefs(context)
                }
            }
            showHistoryLimitInput = false
        }
    }

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
        val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 0.dp),
        ) {
            AnimatedContent(
                targetState = Pair(isLoading, smsList),
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "CodeRecordState",
            ) { (loading, list) ->
                if (loading && list.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (list.isEmpty() && !loading) {
                    // Empty View
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.list_empty_prompt),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .hazeSource(state = hazeState)
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        state = listState,
                        contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
                    ) {
                        items(list, key = { it.id ?: 0 }) { smsMsg ->
                            val isSelected = selectedIds.contains(smsMsg.id)

                            if (isSelectionMode) {
                                CodeRecordItem(
                                    smsMsg = smsMsg,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = isSelected,
                                    onClick = {
                                        toggleSelection(smsMsg.id ?: 0)
                                    },
                                    onLongClick = {},
                                    onDetailClick = { detailSmsMsg = smsMsg },
                                    modifier = Modifier.animateItem(),
                                )
                            } else {
                                val dismissState = rememberDismissState(confirmStateChange = { value ->
                                    if (value == DismissValue.DismissedToEnd ||
                                        value == DismissValue.DismissedToStart
                                    ) {
                                        deleteAndUndo(smsMsg)
                                    }
                                    true
                                })
                                SwipeToDismiss(
                                    state = dismissState,
                                    directions = setOf(DismissDirection.StartToEnd, DismissDirection.EndToStart),
                                    background = {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.errorContainer)
                                                .padding(horizontal = 24.dp),
                                            contentAlignment = if (dismissState.dismissDirection ==
                                                DismissDirection.StartToEnd
                                            ) {
                                                Alignment.CenterStart
                                            } else {
                                                Alignment.CenterEnd
                                            },
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.remove),
                                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                            )
                                        }
                                    },
                                    dismissContent = {
                                        CodeRecordItem(
                                            smsMsg = smsMsg,
                                            isSelectionMode = false,
                                            isSelected = isSelected,
                                            onClick = {
                                                val code = smsMsg.smsCode
                                                if (!code.isNullOrEmpty()) {
                                                    clipboardManager.setText(AnnotatedString(code))
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar(
                                                            context.getString(R.string.prompt_sms_code_copied, code),
                                                        )
                                                    }
                                                }
                                            },
                                            onLongClick = {
                                                isSelectionMode = true
                                                toggleSelection(smsMsg.id ?: 0)
                                            },
                                            onDetailClick = { detailSmsMsg = smsMsg },
                                            modifier = Modifier.animateItem(),
                                        )
                                    },
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter),
        ) {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text(stringResource(R.string.selected_count, selectedIds.size))
                    } else {
                        Text(stringResource(R.string.smscode_records))
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedIds = emptySet()
                        }) {
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
                        IconButton(onClick = {
                            val allIds = smsList.mapNotNull { it.id }.toSet()
                            if (selectedIds.size == allIds.size) {
                                selectedIds = emptySet()
                            } else {
                                selectedIds = allIds
                            }
                        }) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.action_select_all))
                        }
                        IconButton(onClick = { deleteSelected() }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    } else {
                        IconButton(onClick = { showSettingsSheet = true }) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = stringResource(R.string.pref_code_records_title),
                            )
                        }
                        IconButton(onClick = {
                            val filename = "SmsCodeRecords_${SimpleDateFormat(
                                "yyyyMMdd_HHmm",
                                Locale.getDefault(),
                            ).format(Date())}.json"
                            exportLauncher.launch(filename)
                        }) {
                            Icon(
                                painterResource(R.drawable.ic_export),
                                contentDescription = stringResource(R.string.action_export_rules),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                scrollBehavior = scrollBehavior,
                windowInsets = WindowInsets.statusBars,
                modifier = Modifier
                    .hazeEffect(hazeState, hazeStyle) {
                        forceInvalidateOnPreDraw = true
                    },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CodeRecordItem(
    smsMsg: SmsMsg,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDetailClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormatter = remember { SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault()) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            )
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier.padding(end = 16.dp),
            )
        }

        // Left Side: Icon + App Name
        val displayLabel = (smsMsg.company ?: smsMsg.sender ?: stringResource(R.string.unknown)).trim().trim('【', '】', '[', ']')
        val iconLabel = displayLabel.replace(Regex("[【】\\[\\]]"), "").trim()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(end = 16.dp),
        ) {
            AppIconImage(
                packageName = smsMsg.packageName,
                label = iconLabel,
                contentDescription = stringResource(R.string.sms_icon_description),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = displayLabel,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(),
            )
        }

        // Right Side
        Column(modifier = Modifier.weight(1f)) {
            // Top Row: Code + Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = smsMsg.smsCode ?: "",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
                Text(
                    text = dateFormatter.format(Date(smsMsg.date)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            val body = smsMsg.body
            if (!body.isNullOrEmpty()) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onDetailClick() },
                )
            }
        }
    }
}
