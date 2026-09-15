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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.Alignment
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.magisk317.mobile.entitlement.MobileEntitlementCoordinator
import com.magisk317.mobile.entitlement.MobileEntitlementStatus
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.common.utils.XLog
import com.github.magisk317.smscode.ui.theme.AppTheme
import io.github.magisk317.uikit.theme.UiKitStyle
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
            AppTheme(themeMode = 0, uiKitStyle = UiKitStyle.Expressive.value) {
                MobileEntitlementScreen(
                    activity = this@MobileEntitlementActivity,
                    onBack = ::finish,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    var activationTokenInput by remember { mutableStateOf("") }

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
        val trimmed = activationTokenInput.trim()
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
                activationTokenInput = ""
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mobile_entitlement_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
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
                        Text(stringResource(R.string.mobile_entitlement_issued_at, formatEpoch(issuedAt)))
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
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("device_id", deviceId))
                                    android.widget.Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.mobile_entitlement_copied),
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                },
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.mobile_entitlement_copy),
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
            val isActivated = isMobileEntitlementActivated(evaluation?.status)

            if (!isActivated) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.mobile_entitlement_activation_token_label),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        OutlinedTextField(
                            value = activationTokenInput,
                            onValueChange = { activationTokenInput = it.trim().uppercase() },
                            label = { Text(stringResource(R.string.mobile_entitlement_activation_token_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = busyAction == null,
                        )
                        Button(
                            onClick = ::activateWithToken,
                            enabled = busyAction == null && activationTokenInput.trim().length == 32,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (busyAction == ActivationAction.TOKEN) {
                                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                            }
                            Text(stringResource(R.string.mobile_entitlement_activation_token_confirm))
                        }
                        Text(
                            text = stringResource(R.string.mobile_entitlement_activation_token_get_hint),
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
                OutlinedButton(
                    onClick = ::openTelegramBot,
                    enabled = busyAction == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busyAction == ActivationAction.TELEGRAM) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                    }
                    Text(stringResource(R.string.mobile_entitlement_activate_telegram))
                }
            }
            OutlinedButton(
                onClick = { refreshStatus(force = true) },
                enabled = busyAction == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.mobile_entitlement_refresh))
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
