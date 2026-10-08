@file:Suppress("LocalContextGetResourceValueCall")

package io.github.magisk317.smscode.ui.record

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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import io.github.magisk317.smscode.common.utils.HookPreferenceMirror
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.magisk317.smscode.core.R
import io.github.magisk317.smscode.common.constant.PrefConst
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import io.github.magisk317.smscode.db.entity.SmsMsg
import io.github.magisk317.smscode.runtime.bridge.UiPrefsAccess
import org.koin.compose.koinInject
import io.github.magisk317.smscode.runtime.contract.sim.SimSlotLabelFormatter
import io.github.magisk317.smscode.rule.utils.CodeRecordSimilarityUtils
import io.github.magisk317.uikit.surface.AppIconImage
import io.github.magisk317.uikit.surface.AppSurface
import io.github.magisk317.uikit.surface.AppHorizontalDivider
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
import io.github.magisk317.uikit.preference.AppCheckbox
import io.github.magisk317.uikit.preference.TextInputDialog
import io.github.magisk317.uikit.surface.WorkspaceEmptyState
import io.github.magisk317.uikit.surface.WorkspaceListItem
import io.github.magisk317.uikit.surface.swipeRevealSurface
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.surface.AppIcon
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor
import io.github.magisk317.uikit.theme.AppShapeRole
import io.github.magisk317.uikit.theme.appShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val deleteAndUndo = remember(viewModel, scope, context, resources, snackbarHostState) {
        { target: SmsMsg ->
            viewModel.removeSmsMsg(listOf(target))
            scope.launch {
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
        context.getString(R.string.pref_code_records_title)
    }
    val onSelectAllVisible: () -> Unit = {
        val visibleIds = smsList
            .filter { it.msgType == SmsMsg.MSG_TYPE_SMS && !it.smsCode.isNullOrBlank() }
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
            smsList.filter { it.msgType == SmsMsg.MSG_TYPE_SMS && !it.smsCode.isNullOrBlank() },
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
                        title = stringResource(id = R.string.pref_enable_code_records_title),
                        summary = "",
                        key = RECORD_ENABLE_KEY,
                        defaultValue = true,
                    )

                    AppArrowItem(
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
    sms: SmsMsg,
    onDismiss: () -> Unit,
    onCopy: (label: String, value: String, toast: String) -> Unit,
    onDelete: () -> Unit,
    simSlotRemarkResolver: (Int) -> String,
) {
    val context = LocalContext.current
    val detailDateFormatter = remember { SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault()) }
    val sender = sms.sender ?: sms.company ?: context.getString(R.string.unknown)
    val originalTime = formatDetailTime(detailDateFormatter, sms.date)
    val processedTime = formatDetailTime(detailDateFormatter, sms.processedTime)
    val receiverSimLabel = remember(sms.simSlot, simSlotRemarkResolver) {
        SimSlotLabelFormatter.format(sms.simSlot, simSlotRemarkResolver)
    }
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
            .background(appColor(AppColorRole.Scrim).copy(alpha = 0.28f))
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
                    AppText(
                        text = stringResource(R.string.detail_click_copy_hint),
                        role = AppTextRole.Footnote,
                        color = appColor(AppColorRole.OnSurfaceVariant),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AppText(
                            text = "${stringResource(R.string.detail_sender)}:",
                            role = AppTextRole.Body,
                        )
                        AppText(
                            text = sender,
                            role = AppTextRole.Body,
                            color = appColor(AppColorRole.Primary),
                            modifier = Modifier.clickable {
                                val message = context.getString(
                                    R.string.prompt_field_copied,
                                    context.getString(R.string.detail_sender),
                                )
                                onCopy("sms_sender", sender, message)
                            },
                        )
                    }
                    if (receiverSimLabel.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            AppText(
                                text = "${stringResource(R.string.detail_receiver_sim)}:",
                                role = AppTextRole.Body,
                            )
                            AppText(
                                text = receiverSimLabel,
                                role = AppTextRole.Body,
                                color = appColor(AppColorRole.Primary),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AppText(
                            text = "${stringResource(R.string.detail_original_time)}:",
                            role = AppTextRole.Body,
                        )
                        AppText(
                            text = originalTime,
                            role = AppTextRole.Body,
                            color = appColor(AppColorRole.Primary),
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
                        AppText(
                            text = "${stringResource(R.string.detail_processed_time)}:",
                            role = AppTextRole.Body,
                        )
                        AppText(
                            text = processedTime,
                            role = AppTextRole.Body,
                            color = appColor(AppColorRole.Primary),
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
                    AppText(
                        text = "${stringResource(R.string.detail_content)}:",
                        role = AppTextRole.Body,
                    )
                    AppText(
                        text = content,
                        role = AppTextRole.Body,
                        color = appColor(AppColorRole.Primary),
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
                    AppHorizontalDivider()
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
    showHeader: Boolean = true,
    listContentPadding: PaddingValues = PaddingValues(0.dp),
    listScrollModifier: Modifier = Modifier,
    listState: LazyListState,
    simSlotRemarkResolver: (Int) -> String,
    scrollChromeState: io.github.magisk317.uikit.scroll.ScrollChromeState? = null,
    isActive: Boolean = true,
    scrollToTopSignal: Int = 0,
    onRowSwipeGestureActiveChanged: (rowKey: Any, active: Boolean) -> Unit = { _, _ -> },
) {
    io.github.magisk317.uikit.scroll.ReportLazyListScrollToChrome(listState, scrollChromeState)
    io.github.magisk317.uikit.surface.ScrollToTopEffect(listState, scrollToTopSignal)
    val isMiuix = currentUiKitStyle() == UiKitStyle.Miuix
    AppSurface(
        modifier = modifier,
        shape = appShape(AppShapeRole.Large),
        tonalElevation = 2.dp,
        color = Color.Transparent,
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
                    AppText(
                        text = title,
                        role = AppTextRole.Subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AppText(
                        text = list.size.toString(),
                        role = AppTextRole.Footnote,
                        color = appColor(AppColorRole.OnSurfaceVariant),
                    )
                }
                AppHorizontalDivider()
            }
            if (list.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        text = emptyHint,
                        role = AppTextRole.Body,
                        color = appColor(AppColorRole.OnSurfaceVariant),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(listScrollModifier),
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
                                simSlotRemarkResolver = simSlotRemarkResolver,
                                isActive = isActive,
                            )
                        } else {
                            // Track whether this page has completed its first frame. During the
                            // first frame, render a lightweight card without SwipeToDismissBox to
                            // avoid the per-item state + effect + pointerInput cost on the initial
                            // composition pass. The swipe gesture becomes available on the next frame.
                            var swipeReady by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) {
                                withFrameNanos { }
                                swipeReady = true
                            }
                            if (!swipeReady) {
                                CodeRecordItem(
                                    smsMsg = smsMsg,
                                    isSelectionMode = false,
                                    isSelected = false,
                                    onClick = { onCopyCode(smsMsg) },
                                    onLongClick = { onActivateSelection(smsMsg.id ?: 0) },
                                    onDetailClick = { onShowDetail(smsMsg) },
                                    modifier = Modifier.animateItem(),
                                    simSlotRemarkResolver = simSlotRemarkResolver,
                                    isActive = false,
                                )
                            } else {
                            val dismissState = rememberSwipeToDismissBoxState()
                            val rowGestureKey: Any = smsMsg.id ?: System.identityHashCode(smsMsg)
                            LaunchedEffect(dismissState.currentValue, isActive) {
                                if (isActive && dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                                    onDelete(smsMsg)
                                }
                            }
                            SwipeToDismissBox(
                                modifier = Modifier.pointerInput(rowGestureKey, isActive) {
                                    if (!isActive) return@pointerInput
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        onRowSwipeGestureActiveChanged(rowGestureKey, true)
                                        try {
                                            do {
                                                val event = awaitPointerEvent(PointerEventPass.Final)
                                            } while (event.changes.any { it.pressed })
                                        } finally {
                                            onRowSwipeGestureActiveChanged(rowGestureKey, false)
                                        }
                                    }
                                },
                                state = dismissState,
                                enableDismissFromStartToEnd = true,
                                enableDismissFromEndToStart = true,
                                backgroundContent = {
                                    // Full-row reveal carrying the row card's own shape: the red
                                    // silhouette is the same rounded rectangle as the card, slowly
                                    // uncovered behind it, so the card's corners always sit on
                                    // continuous reveal colour (see ui-kit swipeRevealSurface).
                                    val revealAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                                        Alignment.CenterStart
                                    } else {
                                        Alignment.CenterEnd
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .then(
                                                swipeRevealSurface(
                                                    color = appColor(AppColorRole.ErrorContainer),
                                                ),
                                            )
                                            .padding(horizontal = 24.dp),
                                        contentAlignment = revealAlignment,
                                    ) {
                                        AppIcon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.remove),
                                            tint = appColor(AppColorRole.OnErrorContainer),
                                        )
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
                                        simSlotRemarkResolver = simSlotRemarkResolver,
                                        isActive = isActive,
                                    )
                                },
                            )
                            } // swipeReady else
                        }
                        if (!isMiuix) {
                            AppHorizontalDivider()
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
    simSlotRemarkResolver: (Int) -> String,
    isActive: Boolean = true,
) {
    val dateFormatter = remember { SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault()) }
    val context = LocalContext.current
    val isMiuix = currentUiKitStyle() == UiKitStyle.Miuix
    val itemBackground = when {
        isSelected -> appColor(AppColorRole.PrimaryContainer)
        // Material rows are plain boxes with no surface of their own; an opaque fill
        // keeps the full-row swipe reveal from showing through the row, both at rest
        // and mid-swipe. Miuix rows are MiuixCards that already fall back to an opaque
        // surfaceContainer when the container color is transparent.
        !isMiuix -> appColor(AppColorRole.SurfaceContainerLow)
        else -> Color.Transparent
    }

    val fallbackLabel = (smsMsg.company ?: smsMsg.sender ?: stringResource(R.string.unknown))
        .trim()
        .trim('【', '】', '[', ']')
    var appLabel by remember(smsMsg.packageName) { mutableStateOf<String?>(null) }
    LaunchedEffect(smsMsg.packageName, isActive, appLabel == null) {
        if (!isActive || appLabel != null) return@LaunchedEffect
        val pkg = smsMsg.packageName
        appLabel = if (pkg.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val pm = context.packageManager
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString()
                }.getOrNull()
            }
        }
    }
    val displayLabel = appLabel ?: fallbackLabel
    val hasCode = !smsMsg.smsCode.isNullOrBlank()
    val codeOrSender = smsMsg.smsCode?.takeIf { it.isNotBlank() }
        ?: smsMsg.sender?.takeIf { it.isNotBlank() }
        ?: fallbackLabel
    val receiverSimLabel = remember(smsMsg.simSlot, simSlotRemarkResolver) {
        SimSlotLabelFormatter.format(smsMsg.simSlot, simSlotRemarkResolver)
    }

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
                    modifier = Modifier.width(64.dp),
                ) {
                    if (isActive) {
                        AppIconImage(
                            packageName = smsMsg.packageName,
                            contentDescription = stringResource(R.string.sms_icon_description),
                        )
                    } else {
                        Spacer(modifier = Modifier.size(40.dp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    AppText(
                        text = displayLabel,
                        role = AppTextRole.Footnote,
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
            AppText(
                text = codeOrSender,
                role = AppTextRole.Title,
                color = appColor(AppColorRole.Primary),
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
            AppText(
                text = dateFormatter.format(Date(smsMsg.date)),
                role = AppTextRole.BodySmall,
                color = appColor(AppColorRole.OnSurfaceVariant),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        if (receiverSimLabel.isNotBlank()) {
            AppText(
                text = stringResource(R.string.detail_receiver_sim_with_value, receiverSimLabel),
                role = AppTextRole.BodySmall,
                color = appColor(AppColorRole.OnSurfaceVariant),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        val body = smsMsg.body
        if (!body.isNullOrEmpty()) {
            AppText(
                text = body,
                role = AppTextRole.Body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onDetailClick() },
            )
        }
    }
}
