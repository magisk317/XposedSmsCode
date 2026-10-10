package io.github.magisk317.smscode.entitlement

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import io.github.magisk317.uikit.common.AppSnackbarHost
import io.github.magisk317.uikit.common.AppSnackbarHostState
import com.magisk317.mobile.entitlement.MobileEntitlementChallenge
import com.magisk317.mobile.entitlement.MobileEntitlementCoordinator
import com.magisk317.mobile.entitlement.MobileEntitlementStatus
import io.github.magisk317.smscode.common.constant.Const
import io.github.magisk317.smscode.core.R
import io.github.magisk317.smscode.common.utils.XLog
import io.github.magisk317.smscode.ui.theme.AppTheme
import io.github.magisk317.uikit.surface.AppCard
import io.github.magisk317.uikit.surface.AppCircularProgressIndicator
import io.github.magisk317.uikit.surface.AppIcon
import io.github.magisk317.uikit.surface.AppIconButton
import io.github.magisk317.uikit.surface.AppOutlinedIconButton
import io.github.magisk317.uikit.surface.AppPrimaryButton
import io.github.magisk317.uikit.surface.AppScaffold
import io.github.magisk317.uikit.surface.AppSecondaryButton
import io.github.magisk317.uikit.surface.AppTextButton
import io.github.magisk317.uikit.surface.AppTextField
import io.github.magisk317.uikit.surface.AppTopBar
import io.github.magisk317.uikit.surface.SectionColumn
import io.github.magisk317.uikit.surface.rememberSaveableTextFieldState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.magisk317.uikit.text.AppText
import io.github.magisk317.uikit.text.AppTextRole
import io.github.magisk317.uikit.theme.AppColorRole
import io.github.magisk317.uikit.theme.appColor

class MobileEntitlementActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        XLog.w("Mobile entitlement activity created")
        setContent {
            // Follow the stored appearance preferences (theme mode + UI kit style) like the
            // main activity: passing explicit values here pinned the page to dark Expressive
            // even when the app ran light or Miuix.
            AppTheme {
                MobileEntitlementScreen(
                    activity = this@MobileEntitlementActivity,
                    onBack = ::finish,
                )
            }
        }
    }
}

@Composable
private fun MobileEntitlementScreen(
    activity: Activity,
    onBack: () -> Unit,
) {
    val context: Context = activity
    val scope = rememberCoroutineScope()
    var evaluation by remember {
        mutableStateOf(MobileEntitlementCoordinator.readCachedEvaluation())
    }
    var busyAction by remember { mutableStateOf<ActivationAction?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingBotUrl by remember { mutableStateOf<String?>(null) }
    var qqChallenge by remember { mutableStateOf<MobileEntitlementChallenge?>(null) }
    val snackbarHostState = remember { AppSnackbarHostState() }
    val tokenState = rememberSaveableTextFieldState()
    val tokenInputTransformation = InputTransformation {
        val original = toString()
        val normalized = original.trim().uppercase()
        if (normalized != original) {
            replace(0, length, normalized)
            selection = TextRange(normalized.length)
        }
    }

    fun refreshStatus(force: Boolean = false) {
        scope.launch {
            busyAction = ActivationAction.REFRESH
            message = null
            XLog.w("Mobile entitlement refresh started")
            runCatching {
                withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.refresh(context, force = force)
                }
            }.onSuccess {
                evaluation = it
                XLog.w(
                    "Mobile entitlement refresh finished status=%s allowed=%s renewDue=%s",
                    it.status,
                    it.automationAllowed,
                    it.renewDue,
                )
            }.onFailure {
                XLog.e("Mobile entitlement refresh failed", it)
                message = it.message ?: it.javaClass.simpleName
            }
            busyAction = null
        }
    }

    fun copyWithToast(label: String, value: String) {
        copyPlainText(context, label, value)
        android.widget.Toast
            .makeText(
                context,
                context.getString(R.string.mobile_entitlement_copied),
                android.widget.Toast.LENGTH_SHORT,
            )
            .show()
    }

    fun openTelegram(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure { error ->
            // ACTION_VIEW 失败（如设备上 Telegram 未安装或链接无法解析）时，自动把跳转
            // 链接复制到剪贴板：用户可手动发送给机器人，或粘贴到官网激活页兑换令牌。
            XLog.w("Mobile entitlement Telegram jump failed, link copied: %s", error.message)
            copyPlainText(context, "bot_url", url)
            message = error.message ?: error.javaClass.simpleName
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.mobile_entitlement_jump_failed_copied),
                )
            }
        }
    }

    fun openTelegramBot() {
        scope.launch {
            busyAction = ActivationAction.TELEGRAM
            message = null
            XLog.w("Mobile entitlement Telegram activation started")
            runCatching {
                val challenge = withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.createTelegramChallenge(context)
                }
                XLog.w("Mobile entitlement Telegram challenge received id=%s", challenge.id.take(8))
                pendingBotUrl = challenge.botUrl
                val url = challenge.botUrl
                if (url.isNullOrBlank()) {
                    message = context.getString(R.string.mobile_entitlement_qq_entry_missing)
                } else {
                    openTelegram(url)
                }
            }.onFailure {
                XLog.e("Mobile entitlement Telegram activation failed", it)
                message = it.message ?: it.javaClass.simpleName
            }
            busyAction = null
        }
    }

    fun startQQActivation() {
        scope.launch {
            busyAction = ActivationAction.QQ
            message = null
            XLog.w("Mobile entitlement QQ activation started")
            runCatching {
                qqChallenge = withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.createQQChallenge(context)
                }
            }.onFailure {
                XLog.e("Mobile entitlement QQ activation failed", it)
                qqChallenge = null
                message = it.message ?: it.javaClass.simpleName
            }
            busyAction = null
        }
    }

    fun copyBotLink() {
        val url = pendingBotUrl ?: return
        copyPlainText(context, "bot_url", url)
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.mobile_entitlement_link_copied),
            )
        }
    }

    fun activateWithToken() {
        val trimmed = tokenState.text.toString().trim().uppercase()
        if (trimmed.length != 32) {
            message = context.getString(R.string.mobile_entitlement_activation_token_invalid)
            return
        }
        scope.launch {
            busyAction = ActivationAction.TOKEN
            message = null
            XLog.w("Mobile entitlement token activation started")
            runCatching {
                withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.activateByToken(context, trimmed)
                }
            }.onSuccess {
                evaluation = it
                tokenState.edit { replace(0, length, "") }
                XLog.w(
                    "Mobile entitlement token activation success status=%s allowed=%s",
                    it.status,
                    it.automationAllowed,
                )
            }.onFailure {
                XLog.e("Mobile entitlement token activation failed", it)
                message = it.message ?: it.javaClass.simpleName
            }
            busyAction = null
        }
    }

    LaunchedEffect(Unit) {
        refreshStatus()
    }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.mobile_entitlement_title),
                navigationIcon = {
                    AppIconButton(onClick = onBack) {
                        AppIcon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        SectionColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            contentPadding = PaddingValues(horizontal = Const.PADDING_SMALL.dp),
            verticalArrangement = Arrangement.spacedBy(Const.SPACING_SMALL.dp),
        ) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppText(
                        text = stringResource(
                            R.string.mobile_entitlement_status,
                            stringResource(mobileEntitlementStatusStringRes(evaluation?.status)),
                        ),
                        role = AppTextRole.Subtitle,
                    )
                    AppText(
                        text = stringResource(
                            R.string.mobile_entitlement_automation,
                            if (evaluation?.automationAllowed == true) {
                                stringResource(R.string.mobile_entitlement_allowed)
                            } else {
                                stringResource(R.string.mobile_entitlement_paused)
                            },
                        ),
                    )
                    evaluation?.claims?.issuedAt?.takeIf { it > 0 }?.let { issuedAt ->
                        AppText(
                            text = stringResource(
                                R.string.mobile_entitlement_issued_at,
                                formatEpoch(issuedAt),
                            ),
                        )
                    }
                    // TODO(entitlement): restore once entitlement-android ships
                    // MobileEntitlementEvaluation.lastConfirmedAt (0.2.1 lacks the field;
                    // 0e64feec landed the UI ahead of the library). The
                    // mobile_entitlement_last_confirmed_at string resource is kept for
                    // that purpose.
                    evaluation?.claims?.deviceId?.takeIf { it.isNotBlank() }?.let { deviceId ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppText(
                                text = stringResource(R.string.mobile_entitlement_device_id, deviceId),
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            AppIconButton(
                                onClick = {
                                    copyPlainText(context, "device_id", deviceId)
                                    android.widget.Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.mobile_entitlement_copied),
                                            android.widget.Toast.LENGTH_SHORT,
                                        )
                                        .show()
                                },
                            ) {
                                AppIcon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = stringResource(
                                        R.string.mobile_entitlement_copy,
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            val isActivated = isMobileEntitlementActivated(evaluation?.status)

            if (!isActivated) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppText(
                            text = stringResource(R.string.mobile_entitlement_activation_token_label),
                            role = AppTextRole.Subtitle,
                        )
                        AppTextField(
                            state = tokenState,
                            label = stringResource(
                                R.string.mobile_entitlement_activation_token_hint,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = busyAction == null,
                            inputTransformation = tokenInputTransformation,
                        )
                        AppPrimaryButton(
                            onClick = ::activateWithToken,
                            enabled = busyAction == null &&
                                tokenState.text.toString().trim().length == 32,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (busyAction == ActivationAction.TOKEN) {
                                AppCircularProgressIndicator(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                            AppText(
                                text = stringResource(
                                    R.string.mobile_entitlement_activation_token_confirm,
                                ),
                            )
                        }
                        AppText(
                            text = stringResource(
                                R.string.mobile_entitlement_activation_token_get_hint,
                            ),
                            role = AppTextRole.BodySmall,
                            color = appColor(AppColorRole.OnSurfaceVariant),
                        )
                    }
                }
            }

            qqChallenge?.let { challenge ->
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppText(
                                text = stringResource(R.string.mobile_entitlement_qq_title),
                                role = AppTextRole.Subtitle,
                            )
                            AppTextButton(
                                text = stringResource(R.string.mobile_entitlement_qq_dismiss),
                                onClick = { qqChallenge = null },
                            )
                        }
                        challenge.activationCode?.let { code ->
                            EntitlementCopyRow(
                                label = stringResource(R.string.mobile_entitlement_qq_code_label),
                                value = code,
                                monospace = true,
                                onCopy = ::copyWithToast,
                            )
                        }
                        AppSecondaryButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(Const.QQ_CHANNEL_URL)),
                                    )
                                }.onFailure { error ->
                                    XLog.w(
                                        "Mobile entitlement QQ channel jump failed: %s",
                                        error.message,
                                    )
                                    message = error.message ?: error.javaClass.simpleName
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            AppText(stringResource(R.string.mobile_entitlement_qq_join_channel))
                        }
                        AppText(
                            text = stringResource(R.string.mobile_entitlement_qq_instructions),
                            role = AppTextRole.BodySmall,
                            color = appColor(AppColorRole.OnSurfaceVariant),
                        )
                    }
                }
            }

            val currentEvaluation = evaluation
            val showActivationActions = currentEvaluation == null ||
                currentEvaluation.status != MobileEntitlementStatus.ACTIVE ||
                currentEvaluation.renewDue
            if (showActivationActions) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Const.SPACING_SMALL.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppSecondaryButton(
                        onClick = ::openTelegramBot,
                        enabled = busyAction == null,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (busyAction == ActivationAction.TELEGRAM) {
                            AppCircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        AppText(text = stringResource(R.string.mobile_entitlement_activate_telegram))
                    }
                    AppOutlinedIconButton(
                        onClick = ::copyBotLink,
                        enabled = busyAction == null && pendingBotUrl != null,
                        modifier = Modifier.size(40.dp),
                    ) {
                        AppIcon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(
                                R.string.mobile_entitlement_copy_link,
                            ),
                        )
                    }
                }
                AppSecondaryButton(
                    onClick = ::startQQActivation,
                    enabled = busyAction == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busyAction == ActivationAction.QQ) {
                        AppCircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    AppText(text = stringResource(R.string.mobile_entitlement_activate_qq))
                }
            }
            AppSecondaryButton(
                onClick = { refreshStatus(force = true) },
                enabled = busyAction == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busyAction == ActivationAction.REFRESH) {
                    AppCircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    AppIcon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                AppText(text = stringResource(R.string.mobile_entitlement_refresh))
            }
            message?.let {
                AppText(
                    text = stringResource(R.string.mobile_entitlement_error, it),
                    color = appColor(AppColorRole.Error),
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private enum class ActivationAction {
    REFRESH,
    TOKEN,
    TELEGRAM,
    QQ,
}

@Composable
private fun EntitlementCopyRow(
    label: String,
    value: String,
    onCopy: (String, String) -> Unit,
    monospace: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            AppText(
                text = label,
                role = AppTextRole.BodySmall,
                color = appColor(AppColorRole.OnSurfaceVariant),
            )
            AppText(
                text = value,
                role = AppTextRole.Subtitle,
                fontFamily = if (monospace) FontFamily.Monospace else null,
            )
        }
        AppIconButton(onClick = { onCopy(label, value) }) {
            AppIcon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.mobile_entitlement_copy),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

private fun copyPlainText(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
    cm?.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
}

private fun formatEpoch(epochSeconds: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochSecond(epochSeconds))

internal fun isMobileEntitlementActivated(status: MobileEntitlementStatus?): Boolean =
    status == MobileEntitlementStatus.ACTIVE || status == MobileEntitlementStatus.GRACE

@androidx.annotation.StringRes
internal fun mobileEntitlementStatusStringRes(status: MobileEntitlementStatus?): Int = when {
    status == null -> R.string.mobile_entitlement_not_loaded
    isMobileEntitlementActivated(status) -> R.string.module_status_active
    else -> R.string.module_status_inactive
}
