package com.github.magisk317.smscode.entitlement

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.magisk317.mobile.entitlement.MobileEntitlementCoordinator
import com.magisk317.mobile.entitlement.MobileEntitlementStatus
import com.github.magisk317.smscode.common.constant.Const
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.common.utils.XLog
import com.github.magisk317.smscode.ui.theme.AppTheme
import io.github.magisk317.uikit.surface.AppCard
import io.github.magisk317.uikit.surface.AppCircularProgressIndicator
import io.github.magisk317.uikit.surface.AppIcon
import io.github.magisk317.uikit.surface.AppIconButton
import io.github.magisk317.uikit.surface.AppPrimaryButton
import io.github.magisk317.uikit.surface.AppScaffold
import io.github.magisk317.uikit.surface.AppSecondaryButton
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

    fun openTelegram(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure { message = it.message ?: it.javaClass.simpleName }
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
                openTelegram(challenge.botUrl)
            }.onFailure {
                XLog.e("Mobile entitlement Telegram activation failed", it)
                message = it.message ?: it.javaClass.simpleName
            }
            busyAction = null
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
                    Text(
                        text = stringResource(
                            R.string.mobile_entitlement_status,
                            stringResource(mobileEntitlementStatusStringRes(evaluation?.status)),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
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
                        Text(
                            text = stringResource(
                                R.string.mobile_entitlement_issued_at,
                                formatEpoch(issuedAt),
                            ),
                        )
                    }
                    evaluation?.claims?.deviceId?.takeIf { it.isNotBlank() }?.let { deviceId ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.mobile_entitlement_device_id, deviceId),
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            AppIconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                        as? android.content.ClipboardManager
                                    cm?.setPrimaryClip(
                                        android.content.ClipData.newPlainText("device_id", deviceId),
                                    )
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
                        Text(
                            text = stringResource(R.string.mobile_entitlement_activation_token_label),
                            style = MaterialTheme.typography.titleMedium,
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
                            Text(
                                text = stringResource(
                                    R.string.mobile_entitlement_activation_token_confirm,
                                ),
                            )
                        }
                        Text(
                            text = stringResource(
                                R.string.mobile_entitlement_activation_token_get_hint,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            val currentEvaluation = evaluation
            val showActivationActions = currentEvaluation == null ||
                currentEvaluation.status != MobileEntitlementStatus.ACTIVE ||
                currentEvaluation.renewDue
            if (showActivationActions) {
                AppSecondaryButton(
                    onClick = ::openTelegramBot,
                    enabled = busyAction == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busyAction == ActivationAction.TELEGRAM) {
                        AppCircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(text = stringResource(R.string.mobile_entitlement_activate_telegram))
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
                Text(text = stringResource(R.string.mobile_entitlement_refresh))
            }
            message?.let {
                Text(
                    text = stringResource(R.string.mobile_entitlement_error, it),
                    color = MaterialTheme.colorScheme.error,
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
