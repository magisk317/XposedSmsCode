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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RecordDetailOverlay(
    sms: SmsMsgRecordData,
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

internal fun formatDetailTime(
    formatter: SimpleDateFormat,
    timestamp: Long,
): String {
    return if (timestamp > 0L) {
        formatter.format(Date(timestamp))
    } else {
        "-"
    }
}
