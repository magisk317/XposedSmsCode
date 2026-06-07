@file:Suppress("LocalContextGetResourceValueCall")

package com.github.magisk317.smscode.ui.record

import android.content.ClipData
import android.os.SystemClock
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.github.magisk317.smscode.common.utils.HookPreferenceMirror
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.common.utils.AppPreferencesDataStore
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import io.github.magisk317.smscode.rule.utils.CodeRecordSimilarityUtils
import com.github.magisk317.smscode.ui.common.AppIconImage
import com.github.magisk317.smscode.ui.common.LoadingIndicatorTokens
import io.github.magisk317.uikit.foundation.LocalSnackbarHostState
import io.github.magisk317.uikit.common.DismissibleSnackbarHost
import io.github.magisk317.uikit.common.showLatestSnackbar
import com.github.magisk317.smscode.ui.common.PolygonMorphLoadingIndicator
import com.github.magisk317.smscode.ui.common.SessionLoadingRegistry
import com.github.magisk317.smscode.ui.common.rememberMinDurationLoading
import com.github.magisk317.smscode.ui.home.Item
import com.github.magisk317.smscode.ui.home.RetentionDialog
import com.github.magisk317.smscode.ui.home.SectionHeader
import com.github.magisk317.smscode.ui.home.SwitchItem
import com.github.magisk317.smscode.ui.home.TextInputDialog
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeSource
import io.github.magisk317.uikit.preference.AppCheckbox
import io.github.magisk317.uikit.surface.AppTopBar
import io.github.magisk317.uikit.surface.WorkspaceEmptyState
import io.github.magisk317.uikit.surface.WorkspaceListItem
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.*

private const val RECORD_ENABLE_KEY = PrefConst.KEY_ENABLE_CODE_RECORDS_CODE
private const val RECORD_HISTORY_LIMIT_KEY = PrefConst.KEY_HISTORY_LIMIT_CODE
private const val CODE_RECORD_DEDUP_WINDOW_MS = CodeRecordSimilarityUtils.DEFAULT_WINDOW_MS

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Suppress("CyclomaticComplexMethod")
@Composable
fun CodeRecordScreen(
    hazeState: HazeState,
    hazeStyle: HazeBlurStyle,
    onBack: (() -> Unit)? = null,
    refreshTrigger: Int = 0,
    viewModel: CodeRecordViewModel = koinViewModel(),
) {
    when (currentUiKitStyle()) {
        UiKitStyle.Miuix -> CodeRecordScreenMiuix(
            hazeState = hazeState,
            hazeStyle = hazeStyle,
            onBack = onBack,
            refreshTrigger = refreshTrigger,
            viewModel = viewModel,
        )

        UiKitStyle.Expressive -> CodeRecordScreenMaterial(
            hazeState = hazeState,
            hazeStyle = hazeStyle,
            onBack = onBack,
            refreshTrigger = refreshTrigger,
            viewModel = viewModel,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Suppress("CyclomaticComplexMethod")
@Composable
internal fun CodeRecordScreenShared(
    hazeState: HazeState,
    hazeStyle: HazeBlurStyle,
    onBack: (() -> Unit)? = null,
    refreshTrigger: Int = 0,
    viewModel: CodeRecordViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val smsList = uiState.smsList
    val isLoading = uiState.isLoading
    val shouldShowInitialLoading = remember { SessionLoadingRegistry.shouldShowInitial("records") }
    var initialLoadingStarted by remember { mutableStateOf(false) }
    var manualRefreshing by remember { mutableStateOf(false) }
    var manualRefreshStartedAt by remember { mutableLongStateOf(0L) }
    val showLoading = rememberMinDurationLoading(
        actualLoading = isLoading && shouldShowInitialLoading,
        minDurationMillis = LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS,
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(isLoading, shouldShowInitialLoading, initialLoadingStarted) {
        if (!shouldShowInitialLoading) return@LaunchedEffect
        if (isLoading) {
            initialLoadingStarted = true
        } else if (initialLoadingStarted) {
            SessionLoadingRegistry.markShown("records")
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
            viewModel.refreshData()
        }
    }

    val clipboard = LocalClipboard.current

    fun copyWithFeedback(label: String, text: String, toastText: String, snackbarText: String) {
        scope.launch {
            clipboard.setClipEntry(ClipData.newPlainText(label, text).toClipEntry())
            snackbarHostState.showSnackbar(snackbarText.ifBlank { toastText })
        }
    }

    // Initial Load
    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    // Selection State
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var fixedTopHeightPx by remember { mutableIntStateOf(0) }

    var historyLimitCode by remember { mutableStateOf("0") }
    var showHistoryLimitDialog by remember { mutableStateOf(false) }
    var showHistoryLimitInput by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        historyLimitCode = AppPreferencesDataStore.getString(context, PrefConst.KEY_HISTORY_LIMIT_CODE, "0")
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
                val result = snackbarHostState.showLatestSnackbar(
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
            val result = snackbarHostState.showLatestSnackbar(
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
                viewModel.exportRecords(context = context, uri = uri)
            }
        }

    if (showSettingsSheet) {
        val currentTabName = stringResource(R.string.record_settings_target_code)
        val currentHistoryLimit = historyLimitCode
        io.github.magisk317.uikit.surface.AppBottomSheet(
            show = true,
            onDismissRequest = { showSettingsSheet = false },
            title = stringResource(
                id = R.string.record_settings_title_with_target,
                currentTabName,
            ),
        ) {
            SwitchItem(
                title = stringResource(id = R.string.pref_enable_code_records_title),
                summary = "",
                key = RECORD_ENABLE_KEY,
                defaultValue = true,
            )

            Item(
                title = stringResource(
                    id = R.string.pref_history_limit_title_with_target,
                    currentTabName,
                ),
                summary = run {
                    val entries = stringArrayResource(id = R.array.history_limit_entry_list)
                    val values = stringArrayResource(id = R.array.history_limit_value_list)
                    val index = values.indexOf(currentHistoryLimit)
                    if (index >= 0) {
                        entries[index]
                    } else {
                        "$currentHistoryLimit $currentTabName"
                    }
                },
            ) { showHistoryLimitDialog = true }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showHistoryLimitDialog) {
        val currentHistoryLimit = historyLimitCode
        RetentionDialog(
            selectedValue = currentHistoryLimit,
            onDismiss = { showHistoryLimitDialog = false },
            titleId = R.string.pref_history_limit_title,
            entriesId = R.array.history_limit_entry_list,
            valuesId = R.array.history_limit_value_list,
        ) { value ->
            if (value == "-1") {
                showHistoryLimitInput = true
            } else {
                historyLimitCode = value
                scope.launch {
                    AppPreferencesDataStore.setString(context, RECORD_HISTORY_LIMIT_KEY, value)
                    HookPreferenceMirror.publish(context)
                }
            }
            showHistoryLimitDialog = false
        }
    }

    if (showHistoryLimitInput) {
        val currentHistoryLimit = historyLimitCode
        TextInputDialog(
            title = stringResource(id = R.string.history_limit_custom_entry),
            initialValue = if (currentHistoryLimit == "0" || currentHistoryLimit == "-1") {
                ""
            } else {
                currentHistoryLimit
            },
            onDismiss = { showHistoryLimitInput = false },
        ) { value ->
            if (value.all { it.isDigit() } && value.isNotEmpty()) {
                historyLimitCode = value
                scope.launch {
                    AppPreferencesDataStore.setString(context, RECORD_HISTORY_LIMIT_KEY, value)
                    HookPreferenceMirror.publish(context)
                }
            }
            showHistoryLimitInput = false
        }
    }

    if (showExportDialog) {
        val currentTabName = stringResource(R.string.record_settings_target_code)
        io.github.magisk317.uikit.surface.AppAlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(stringResource(R.string.record_export_dialog_title)) },
            text = {
                Text(text = stringResource(R.string.record_export_current_tab_option, currentTabName))
            },
            confirmButton = {
                io.github.magisk317.uikit.surface.AppPrimaryButton(
                    text = stringResource(R.string.confirm),
                    onClick = {
                        val suffix = "code"
                        val filename = "Records_${suffix}_${SimpleDateFormat(
                            "yyyyMMdd_HHmm",
                            Locale.getDefault(),
                        ).format(Date())}.json"
                        showExportDialog = false
                        exportLauncher.launch(filename)
                    },
                )
            },
            dismissButton = {
                io.github.magisk317.uikit.surface.AppSecondaryButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showExportDialog = false },
                )
            },
        )
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val pullToRefreshState = rememberPullToRefreshState()
    val density = LocalDensity.current

    val codeSmsList = deduplicateCodeRecords(
        smsList.filter { it.msgType == SmsMsg.MSG_TYPE_SMS && !it.smsCode.isNullOrBlank() },
    )
    val activeSmsList = codeSmsList
    val activeTitle = context.getString(R.string.records_column_code_title)
    val activeEmptyHint = context.getString(R.string.records_column_code_empty)

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
        val defaultTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 120.dp
        val fixedTopHeight = if (fixedTopHeightPx > 0) with(density) { fixedTopHeightPx.toDp() } else defaultTopPadding
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 80.dp

        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = manualRefreshing,
            onRefresh = {
                manualRefreshStartedAt = SystemClock.elapsedRealtime()
                manualRefreshing = true
                viewModel.refreshData()
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
            modifier = Modifier
                .fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState),
            ) {
                AnimatedContent(
                    targetState = Pair(showLoading, activeSmsList),
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                    },
                    label = "CodeRecordState",
                ) { (loading, list) ->
                    if (loading && !manualRefreshing && list.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            PolygonMorphLoadingIndicator(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = fixedTopHeight + LoadingIndicatorTokens.OverlayTopSpacing),
                            )
                        }
                    } else if (list.isEmpty() && !loading) {
                        WorkspaceEmptyState(
                            title = stringResource(R.string.list_empty_prompt),
                            summary = stringResource(R.string.record_empty_summary),
                            modifier = Modifier.fillMaxSize(),
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    } else {
                        RecordSplitColumn(
                            title = activeTitle,
                            emptyHint = activeEmptyHint,
                            list = list,
                            isSelectionMode = isSelectionMode,
                            selectedIds = selectedIds,
                            onToggleSelection = { toggleSelection(it) },
                            onActivateSelection = {
                                isSelectionMode = true
                                toggleSelection(it)
                            },
                            onCopyCode = { smsMsg ->
                                val code = smsMsg.smsCode
                                if (!code.isNullOrEmpty()) {
                                    val message = context.getString(R.string.prompt_sms_code_copied, code)
                                    copyWithFeedback("sms_code", code, message, message)
                                }
                            },
                            onShowDetail = { detailSmsMsg = it },
                            onDelete = { deleteAndUndo(it) },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            scrollBehavior = scrollBehavior,
                            showHeader = false,
                            listContentPadding = PaddingValues(top = fixedTopHeight, bottom = bottomPadding),
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
                .hazeEffect(hazeState) {
                    blurEffect { style = hazeStyle }
                    forceInvalidateOnPreDraw = true
                },
        ) {
            AppTopBar(
                title = if (isSelectionMode) {
                    context.getString(R.string.selected_count, selectedIds.size)
                } else {
                    context.getString(R.string.pref_code_records_title)
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
                            val visibleIds = smsList
                                .filter { it.msgType == SmsMsg.MSG_TYPE_SMS && !it.smsCode.isNullOrBlank() }
                                .mapNotNull { it.id }
                                .toSet()
                            if (visibleIds.isEmpty()) return@IconButton
                            val allVisibleSelected = visibleIds.all { selectedIds.contains(it) }
                            selectedIds = if (allVisibleSelected) {
                                selectedIds - visibleIds
                            } else {
                                selectedIds + visibleIds
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
                            showExportDialog = true
                        }) {
                            Icon(
                                painterResource(R.drawable.ic_export),
                                contentDescription = stringResource(R.string.action_export_rules),
                            )
                        }
                    }
                },
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                scrollBehavior = scrollBehavior,
                windowInsets = WindowInsets.statusBars,
            )
        }

        io.github.magisk317.uikit.common.DismissibleSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )

        val sms = detailSmsMsg
        if (sms != null) {
            RecordDetailOverlay(
                hazeState = hazeState,
                hazeStyle = hazeStyle,
                sms = sms,
                onDismiss = { detailSmsMsg = null },
                onCopy = { label, value, toast ->
                    copyWithFeedback(label, value, toast, toast)
                },
                onDelete = {
                    deleteAndUndo(sms)
                },
            )
        }
        }
    }
}

private fun deduplicateCodeRecords(records: List<SmsMsg>): List<SmsMsg> {
    return CodeRecordSimilarityUtils.deduplicateRecords(
        records = records,
        projection = { record ->
            CodeRecordSimilarityUtils.Projection(
                code = record.smsCode,
                body = record.body,
                company = record.company,
                sender = record.sender,
                packageName = record.packageName,
                date = record.date,
            )
        },
        systemPackages = setOf("com.android.mms"),
        windowMs = CODE_RECORD_DEDUP_WINDOW_MS,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecordDetailOverlay(
    hazeState: HazeState,
    hazeStyle: HazeBlurStyle,
    sms: SmsMsg,
    onDismiss: () -> Unit,
    onCopy: (label: String, value: String, toast: String) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val detailDateFormatter = remember { SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault()) }
    val sender = sms.sender ?: sms.company ?: context.getString(R.string.unknown)
    val originalTime = formatDetailTime(detailDateFormatter, sms.date)
    val processedTime = formatDetailTime(detailDateFormatter, sms.processedTime)
    val content = sms.body.orEmpty()
    val dismissInteraction = remember { MutableInteractionSource() }
    val detailTitleRes = R.string.message_details
    val copyTextRes = R.string.copy_sms
    val copyToastRes = R.string.prompt_sms_copied
    val deleteTextRes = R.string.delete_sms_action
    val copyLabel = "sms_body"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .hazeEffect(hazeState) {
                    blurEffect { style = hazeStyle }
                    forceInvalidateOnPreDraw = true
            }
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f))
            .clickable(
                interactionSource = dismissInteraction,
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
        ) {
            io.github.magisk317.uikit.surface.AppDialogSurface(
                title = stringResource(detailTitleRes),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.detail_click_copy_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "${stringResource(R.string.detail_sender)}:",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = sender,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                val message = context.getString(
                                    R.string.prompt_field_copied,
                                    context.getString(R.string.detail_sender),
                                )
                                onCopy("sms_sender", sender, message)
                            },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "${stringResource(R.string.detail_original_time)}:",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = originalTime,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = if (sms.date > 0L) {
                                Modifier.clickable {
                                    val message = context.getString(
                                        R.string.prompt_field_copied,
                                        context.getString(R.string.detail_original_time),
                                    )
                                    onCopy("sms_original_time", originalTime, message)
                                }
                            } else {
                                Modifier
                            },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "${stringResource(R.string.detail_processed_time)}:",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = processedTime,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = if (sms.processedTime > 0L) {
                                Modifier.clickable {
                                    val message = context.getString(
                                        R.string.prompt_field_copied,
                                        context.getString(R.string.detail_processed_time),
                                    )
                                    onCopy("sms_processed_time", processedTime, message)
                                }
                            } else {
                                Modifier
                            },
                        )
                    }
                    Text(
                        text = "${stringResource(R.string.detail_content)}:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            if (content.isNotEmpty()) {
                                val message = context.getString(
                                    R.string.prompt_field_copied,
                                    context.getString(R.string.detail_content),
                                )
                                onCopy("sms_body", content, message)
                            }
                        },
                    )
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        io.github.magisk317.uikit.surface.AppSecondaryButton(
                            text = stringResource(copyTextRes),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (content.isNotEmpty()) {
                                    val message = context.getString(copyToastRes)
                                    onCopy(copyLabel, content, message)
                                }
                                onDismiss()
                            },
                        )
                        io.github.magisk317.uikit.surface.AppPrimaryButton(
                            text = stringResource(deleteTextRes),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onDelete()
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun formatDetailTime(
    formatter: SimpleDateFormat,
    timestamp: Long,
): String {
    return if (timestamp > 0L) {
        formatter.format(Date(timestamp))
    } else {
        "-"
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RecordSplitColumn(
    title: String,
    emptyHint: String,
    list: List<SmsMsg>,
    isSelectionMode: Boolean,
    selectedIds: Set<Long>,
    onToggleSelection: (Long) -> Unit,
    onActivateSelection: (Long) -> Unit,
    onCopyCode: (SmsMsg) -> Unit,
    onShowDetail: (SmsMsg) -> Unit,
    onDelete: (SmsMsg) -> Unit,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior,
    showHeader: Boolean = true,
    listContentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val listState = rememberLazyListState()
    val isMiuix = currentUiKitStyle() == UiKitStyle.Miuix
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
        color = Color.Transparent,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showHeader) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = list.size.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
            if (list.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = emptyHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(if (isMiuix) 12.dp else 0.dp),
                    contentPadding = listContentPadding,
                ) {
                    items(list, key = { it.id ?: 0 }) { smsMsg ->
                        val isSelected = selectedIds.contains(smsMsg.id)
                        if (isSelectionMode) {
                            CodeRecordItem(
                                smsMsg = smsMsg,
                                isSelectionMode = true,
                                isSelected = isSelected,
                                onClick = { onToggleSelection(smsMsg.id ?: 0) },
                                onLongClick = {},
                                onDetailClick = { onShowDetail(smsMsg) },
                                modifier = Modifier.animateItem(),
                            )
                        } else {
                            val dismissState = rememberSwipeToDismissBoxState()
                            LaunchedEffect(dismissState.currentValue) {
                                if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                                    onDelete(smsMsg)
                                }
                            }
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = true,
                                enableDismissFromEndToStart = true,
                                backgroundContent = {
                                    val dismissDirection = dismissState.dismissDirection
                                    val isDismissing = dismissDirection != SwipeToDismissBoxValue.Settled
                                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                        val revealWidth = with(LocalDensity.current) {
                                            val offset = runCatching { dismissState.requireOffset() }.getOrDefault(0f)
                                            (if (offset < 0f) -offset else offset).toDp()
                                        }.coerceAtMost(maxWidth)
                                        if (isDismissing && revealWidth > 0.dp) {
                                            val revealAlignment = if (dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                                                Alignment.CenterStart
                                            } else {
                                                Alignment.CenterEnd
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .align(revealAlignment)
                                                    .fillMaxHeight()
                                                    .width(revealWidth)
                                                    .background(MaterialTheme.colorScheme.errorContainer)
                                                    .padding(horizontal = 24.dp),
                                                contentAlignment = revealAlignment,
                                            ) {
                                                if (revealWidth >= 56.dp) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = stringResource(R.string.remove),
                                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                },
                                content = {
                                    CodeRecordItem(
                                        smsMsg = smsMsg,
                                        isSelectionMode = false,
                                        isSelected = false,
                                        onClick = { onCopyCode(smsMsg) },
                                        onLongClick = { onActivateSelection(smsMsg.id ?: 0) },
                                        onDetailClick = { onShowDetail(smsMsg) },
                                        modifier = Modifier.animateItem(),
                                    )
                                },
                            )
                        }
                        if (!isMiuix) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
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
    val context = LocalContext.current
    val itemBackground = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }

    val fallbackLabel = (smsMsg.company ?: smsMsg.sender ?: stringResource(R.string.unknown))
        .trim()
        .trim('【', '】', '[', ']')
    val appLabel = remember(smsMsg.packageName) {
        val pkg = smsMsg.packageName
        if (pkg.isNullOrBlank()) {
            null
        } else {
            runCatching {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(appInfo).toString()
            }.getOrNull()
        }
    }
    val displayLabel = appLabel ?: fallbackLabel
    val iconLabel = if (smsMsg.packageName.isNullOrBlank()) {
        fallbackLabel.replace(Regex("[【】\\[\\]]"), "").trim()
    } else {
        null
    }
    val hasCode = !smsMsg.smsCode.isNullOrBlank()
    val codeOrSender = smsMsg.smsCode?.takeIf { it.isNotBlank() }
        ?: smsMsg.sender?.takeIf { it.isNotBlank() }
        ?: fallbackLabel

    WorkspaceListItem(
        modifier = modifier.fillMaxWidth(),
        containerColor = itemBackground,
        onClick = onClick,
        onLongClick = if (isSelectionMode) null else onLongClick,
        leadingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSelectionMode) {
                    AppCheckbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(92.dp),
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
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(),
                    )
                }
            }
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = codeOrSender,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                maxLines = 1,
                overflow = if (hasCode) TextOverflow.Ellipsis else TextOverflow.Clip,
                modifier = if (hasCode) {
                    Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                        .basicMarquee()
                } else {
                    Modifier
                        .width(120.dp)
                        .basicMarquee()
                },
            )
            if (!hasCode) {
                Spacer(modifier = Modifier.weight(1f))
            }
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
