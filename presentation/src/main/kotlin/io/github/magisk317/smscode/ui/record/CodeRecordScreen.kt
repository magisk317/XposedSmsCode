@file:Suppress("LocalContextGetResourceValueCall")

package io.github.magisk317.smscode.ui.record

import android.content.ClipData
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import io.github.magisk317.smscode.common.utils.HookPreferenceMirror
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.smscode.presentation.R
import io.github.magisk317.uikit.R as UiKitR
import io.github.magisk317.smscode.common.constant.PrefConst
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import io.github.magisk317.smscode.runtime.contract.record.SmsMsgRecordData
import io.github.magisk317.smscode.runtime.bridge.UiPrefsAccess
import org.koin.compose.koinInject
import io.github.magisk317.xposed.contract.util.CodeRecordSimilarityUtils
import io.github.magisk317.uikit.surface.AppPullToRefresh
import io.github.magisk317.uikit.foundation.LoadingIndicatorTokens
import io.github.magisk317.uikit.foundation.LocalSnackbarHostState
import io.github.magisk317.uikit.common.AppSnackbarDuration
import io.github.magisk317.uikit.common.AppSnackbarResult
import io.github.magisk317.uikit.common.showLatestSnackbar
import io.github.magisk317.uikit.foundation.PolygonMorphLoadingIndicator
import io.github.magisk317.uikit.foundation.SessionLoadingRegistry
import io.github.magisk317.uikit.foundation.rememberMinDurationLoading
import io.github.magisk317.uikit.preference.AppArrowItem
import io.github.magisk317.smscode.ui.home.PageRefreshAction
import io.github.magisk317.smscode.ui.home.PageRefreshTriggerConsumer
import io.github.magisk317.smscode.ui.home.RetentionDialog
import io.github.magisk317.smscode.ui.home.SwitchItem
import io.github.magisk317.uikit.preference.TextInputDialog
import io.github.magisk317.uikit.surface.WorkspaceEmptyState
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.surface.AppIcon
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.*

private const val RECORD_ENABLE_KEY = PrefConst.KEY_ENABLE_CODE_RECORDS_CODE
private const val RECORD_HISTORY_LIMIT_KEY = PrefConst.KEY_HISTORY_LIMIT_CODE
private const val CODE_RECORD_DEDUP_WINDOW_MS = CodeRecordSimilarityUtils.DEFAULT_WINDOW_MS

internal class RecordSwipeGestureCoordinator(
    private val onActiveChanged: (Boolean) -> Unit,
) {
    private val activeRows = mutableSetOf<Any>()

    fun update(rowKey: Any, active: Boolean) {
        val wasActive = activeRows.isNotEmpty()
        if (active) {
            activeRows.add(rowKey)
        } else {
            activeRows.remove(rowKey)
        }
        val isActive = activeRows.isNotEmpty()
        if (wasActive != isActive) onActiveChanged(isActive)
    }

    fun releaseAll() {
        if (activeRows.isEmpty()) return
        activeRows.clear()
        onActiveChanged(false)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Suppress("CyclomaticComplexMethod")
@Composable
fun CodeRecordScreen(
    onBack: (() -> Unit)? = null,
    refreshTrigger: Int = 0,
    isActive: Boolean = true,
    keepDataActive: Boolean = isActive,
    onPageDataReady: (cacheHit: Boolean) -> Unit = {},
    onRecordSwipeGestureActiveChanged: (Boolean) -> Unit = {},
    viewModel: CodeRecordViewModel = koinViewModel(),
    scrollChromeState: io.github.magisk317.uikit.scroll.ScrollChromeState? = null,
) {
    val currentOnPageDataReady by rememberUpdatedState(onPageDataReady)
    val currentOnRecordSwipeGestureActiveChanged by rememberUpdatedState(
        onRecordSwipeGestureActiveChanged,
    )
    val swipeGestureCoordinator = remember {
        RecordSwipeGestureCoordinator { active ->
            currentOnRecordSwipeGestureActiveChanged(active)
        }
    }
    DisposableEffect(viewModel, keepDataActive) {
        viewModel.setActive(keepDataActive)
        onDispose {
            if (keepDataActive) viewModel.setActive(false)
        }
    }
    DisposableEffect(swipeGestureCoordinator, isActive) {
        if (!isActive) swipeGestureCoordinator.releaseAll()
        onDispose {
            if (isActive) swipeGestureCoordinator.releaseAll()
        }
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val smsList = uiState.smsList
    val isLoading = uiState.isLoading
    val shouldShowInitialLoading = remember { SessionLoadingRegistry.shouldShowInitial("records") }
    var initialLoadingStarted by remember { mutableStateOf(false) }
    var manualRefreshing by remember { mutableStateOf(false) }
    var manualRefreshStartedAt by remember { mutableLongStateOf(0L) }
    val showLoading = rememberMinDurationLoading(
        actualLoading = isActive && isLoading && shouldShowInitialLoading,
        minDurationMillis = LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS,
    )
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    val prefs = koinInject<UiPrefsAccess>()
    val simSlot1Remark by produceState(initialValue = "", context, keepDataActive) {
        if (!keepDataActive) return@produceState
        val fallback = prefs.getSimSlotRemark(context, 0)
        value = fallback
        AppPreferencesDataStore.getStringFlow(
            context,
            PrefConst.KEY_SIM_SLOT1_REMARK,
            fallback,
        ).collect { value = it }
    }
    val simSlot2Remark by produceState(initialValue = "", context, keepDataActive) {
        if (!keepDataActive) return@produceState
        val fallback = prefs.getSimSlotRemark(context, 1)
        value = fallback
        AppPreferencesDataStore.getStringFlow(
            context,
            PrefConst.KEY_SIM_SLOT2_REMARK,
            fallback,
        ).collect { value = it }
    }
    val simSlotRemarkResolver: (Int) -> String = remember(simSlot1Remark, simSlot2Remark) {
        { slot ->
            when (slot) {
                0 -> simSlot1Remark
                1 -> simSlot2Remark
                else -> ""
            }
        }
    }

    LaunchedEffect(isActive, isLoading, shouldShowInitialLoading, initialLoadingStarted) {
        if (!isActive || !shouldShowInitialLoading) return@LaunchedEffect
        if (isLoading) {
            initialLoadingStarted = true
        } else if (initialLoadingStarted) {
            SessionLoadingRegistry.markShown("records")
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
        if (refreshTriggerConsumer.consume(isActive, refreshTrigger) == PageRefreshAction.FORCE_REFRESH) {
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

    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        val cacheHit = viewModel.hasRecordSnapshot.value
        if (!cacheHit) {
            viewModel.hasRecordSnapshot.first { it }
        }
        currentOnPageDataReady(cacheHit)
    }

    // Selection State
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    var historyLimitCode by remember { mutableStateOf("0") }
    var showHistoryLimitDialog by remember { mutableStateOf(false) }
    var showHistoryLimitInput by remember { mutableStateOf(false) }

    LaunchedEffect(keepDataActive) {
        if (!keepDataActive) return@LaunchedEffect
        historyLimitCode = AppPreferencesDataStore.getString(context, PrefConst.KEY_HISTORY_LIMIT_CODE, "0")
    }

    // Detail Dialog State
    var detailSmsMsg by remember { mutableStateOf<SmsMsgRecordData?>(null) }

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
    val deleteAndUndo = remember(viewModel, scope, context, resources, snackbarHostState) {
        { target: SmsMsgRecordData ->
            viewModel.removeSmsMsg(listOf(target))
            // The Job is only the undo snackbar's lifetime; callers need the side effect, so it
            // is bound instead of leaving the lambda with a Job to hand back.
            val undoSnackbar = scope.launch {
                val result = snackbarHostState.showLatestSnackbar(
                    message = resources.getQuantityString(
                        R.plurals.some_items_removed,
                        1,
                        1,
                    ),
                    actionLabel = context.getString(R.string.revoke),
                    duration = AppSnackbarDuration.Long,
                )
                if (result == AppSnackbarResult.ActionPerformed) {
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
                message = resources.getQuantityString(
                    R.plurals.some_items_removed,
                    deleteList.size,
                    deleteList.size,
                ),
                actionLabel = context.getString(R.string.revoke),
                duration = AppSnackbarDuration.Long,
            )
            if (result == AppSnackbarResult.ActionPerformed) {
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

    // Chrome state handed to the style-specific top bar variants.
    val chromeTitle = if (isSelectionMode) {
        resources.getQuantityString(
            R.plurals.selected_count,
            selectedIds.size,
            selectedIds.size,
        )
    } else {
        context.getString(UiKitR.string.pref_code_records_title)
    }
    val onSelectAllVisible: () -> Unit = {
        val visibleIds = smsList
            .filter { it.msgType == SmsMsgRecordData.MSG_TYPE_SMS && !it.smsCode.isNullOrBlank() }
            .mapNotNull { it.id }
            .toSet()
        if (visibleIds.isNotEmpty()) {
            val allVisibleSelected = visibleIds.all { selectedIds.contains(it) }
            selectedIds = if (allVisibleSelected) {
                selectedIds - visibleIds
            } else {
                selectedIds + visibleIds
            }
        }
    }
    val onExitSelectionMode: () -> Unit = {
        isSelectionMode = false
        selectedIds = emptySet()
    }

    // Owned by the entry so the chrome variants can drive quick return-to-top
    // (double-tap strip + ScrollToTopFAB) against the very list the body renders.
    val recordListState = rememberLazyListState()

    val body: @Composable (PaddingValues, Modifier) -> Unit = { listPadding, scrollModifier ->
        val topPadding = listPadding.calculateTopPadding()
        val codeSmsList = deduplicateCodeRecords(
            smsList.filter { it.msgType == SmsMsgRecordData.MSG_TYPE_SMS && !it.smsCode.isNullOrBlank() },
        )
        val activeSmsList = codeSmsList
        val activeTitle = context.getString(R.string.records_column_code_title)
        val activeEmptyHint = context.getString(R.string.records_column_code_empty)

        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            AppPullToRefresh(
                isRefreshing = manualRefreshing,
                onRefresh = {
                    manualRefreshStartedAt = SystemClock.elapsedRealtime()
                    manualRefreshing = true
                    viewModel.refreshData()
                },
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(top = topPadding),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
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
                                        .padding(top = topPadding + LoadingIndicatorTokens.OverlayTopSpacing),
                                )
                            }
                        } else if (list.isEmpty() && !loading) {
                            WorkspaceEmptyState(
                                title = stringResource(R.string.list_empty_prompt),
                                summary = stringResource(R.string.record_empty_summary),
                                modifier = Modifier.fillMaxSize(),
                                icon = {
                                    AppIcon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = appColor(AppColorRole.OnSurfaceVariant),
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
                                listScrollModifier = scrollModifier,
                                showHeader = false,
                                listContentPadding = listPadding,
                                listState = recordListState,
                                simSlotRemarkResolver = simSlotRemarkResolver,
                                scrollChromeState = scrollChromeState,
                                isActive = isActive,
                                scrollToTopSignal = refreshTrigger,
                                onRowSwipeGestureActiveChanged = swipeGestureCoordinator::update,
                            )
                        }
                    }
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
                        title = stringResource(id = UiKitR.string.pref_enable_code_records_title),
                        summary = "",
                        key = RECORD_ENABLE_KEY,
                        defaultValue = true,
                    )

                    AppArrowItem(
                        title = stringResource(
                            id = UiKitR.string.pref_history_limit_title_with_target,
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
                    titleId = UiKitR.string.pref_history_limit_title,
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
                    selectAllOnOpen = true,
                    onDismiss = { showHistoryLimitInput = false },
                    showClearButton = true,
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
                    title = { AppText(stringResource(R.string.record_export_dialog_title)) },
                    text = {
                        AppText(text = stringResource(R.string.record_export_current_tab_option, currentTabName))
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
                            onClick = {
                                showExportDialog = false
                            },
                        )
                    },
                )
            }
        }
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            when (currentUiKitStyle()) {
                UiKitStyle.Miuix -> CodeRecordScreenMiuix(
                    title = chromeTitle,
                    isSelectionMode = isSelectionMode,
                    onBack = onBack,
                    onExitSelectionMode = onExitSelectionMode,
                    onSelectAllVisible = onSelectAllVisible,
                    onDeleteSelected = { deleteSelected() },
                    onOpenSettings = { showSettingsSheet = true },
                    onOpenExport = { showExportDialog = true },
                    scrollChromeState = scrollChromeState,
                    listState = recordListState,
                    body = body,
                )

                UiKitStyle.Expressive -> CodeRecordScreenMaterial(
                    title = chromeTitle,
                    isSelectionMode = isSelectionMode,
                    onBack = onBack,
                    onExitSelectionMode = onExitSelectionMode,
                    onSelectAllVisible = onSelectAllVisible,
                    onDeleteSelected = { deleteSelected() },
                    onOpenSettings = { showSettingsSheet = true },
                    onOpenExport = { showExportDialog = true },
                    scrollChromeState = scrollChromeState,
                    listState = recordListState,
                    body = body,
                )
            }

            val detailSms = detailSmsMsg
            if (detailSms != null) {
                RecordDetailOverlay(
                    sms = detailSms,
                    onDismiss = { detailSmsMsg = null },
                    onCopy = { label, value, toast ->
                        copyWithFeedback(label, value, toast, toast)
                    },
                    onDelete = {
                        deleteAndUndo(detailSms)
                    },
                    simSlotRemarkResolver = simSlotRemarkResolver,
                )
            }
        }
    }
}
private fun deduplicateCodeRecords(records: List<SmsMsgRecordData>): List<SmsMsgRecordData> {
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

