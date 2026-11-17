@file:Suppress("LocalContextGetResourceValueCall")

package io.github.magisk317.smscode.ui.record

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.magisk317.smscode.presentation.R
import io.github.magisk317.smscode.runtime.contract.record.SmsMsgRecordData
import io.github.magisk317.xposed.contract.sim.SimSlotLabelFormatter
import io.github.magisk317.uikit.surface.AppIconImage
import io.github.magisk317.uikit.surface.AppSurface
import io.github.magisk317.uikit.surface.AppHorizontalDivider
import io.github.magisk317.uikit.preference.AppCheckbox
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun RecordSplitColumn(
    title: String,
    emptyHint: String,
    list: List<SmsMsgRecordData>,
    isSelectionMode: Boolean,
    selectedIds: Set<Long>,
    onToggleSelection: (Long) -> Unit,
    onActivateSelection: (Long) -> Unit,
    onCopyCode: (SmsMsgRecordData) -> Unit,
    onShowDetail: (SmsMsgRecordData) -> Unit,
    onDelete: (SmsMsgRecordData) -> Unit,
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
    smsMsg: SmsMsgRecordData,
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
