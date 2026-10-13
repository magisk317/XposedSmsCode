package io.github.magisk317.smscode.entitlement

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.magisk317.mobile.entitlement.MobileEntitlementChallenge
import com.magisk317.mobile.entitlement.MobileEntitlementCoordinator
import com.magisk317.mobile.entitlement.MobileEntitlementStatus
import io.github.magisk317.smscode.core.R
import io.github.magisk317.smscode.common.constant.Const
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.smscode.ui.theme.AppTheme
import io.github.magisk317.uikit.entitlement.MobileEntitlementBusyAction
import io.github.magisk317.uikit.entitlement.MobileEntitlementContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MobileEntitlementActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        XLog.w(message = "Mobile entitlement activity created")
        setContent {
            // Follow the stored appearance preferences (theme mode + UI kit style) like the
            // main activity: passing explicit values here pinned the page to dark Expressive
            // even when the app ran light or Miuix.
            AppTheme {
                MobileEntitlementRoot(onBack = ::finish)
            }
        }
    }
}

@Composable
private fun MobileEntitlementRoot(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var evaluation by remember {
        mutableStateOf(MobileEntitlementCoordinator.readCachedEvaluation())
    }
    var busyAction by remember { mutableStateOf<MobileEntitlementBusyAction?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var qqChallenge by remember { mutableStateOf<MobileEntitlementChallenge?>(null) }
    val qqEntryMissingText = stringResource(io.github.magisk317.uikit.R.string.mobile_entitlement_qq_entry_missing)

    fun refreshStatus(force: Boolean = false) {
        scope.launch {
            busyAction = MobileEntitlementBusyAction.REFRESH
            message = null
            XLog.w(message = "Mobile entitlement refresh started")
            runCatching {
                withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.refresh(context, force = force)
                }
            }.onSuccess {
                evaluation = it
                XLog.w(
                    message = "Mobile entitlement refresh finished status=%s allowed=%s renewDue=%s",
                    it.status,
                    it.automationAllowed,
                    it.renewDue,
                )
            }.onFailure {
                XLog.e(message = "Mobile entitlement refresh failed", it)
                message = it.message ?: it.javaClass.simpleName
            }
            busyAction = null
        }
    }

    fun openTelegram(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure { error ->
            // ACTION_VIEW 失败（如设备上 Telegram 未安装或链接无法解析）时，自动把跳转
            // 链接复制到剪贴板：用户可手动发送给机器人，或粘贴到官网激活页兑换令牌。
            XLog.w(message = "Mobile entitlement Telegram jump failed, link copied: %s", error.message)
            copyPlainText(context, "bot_url", url)
            message = error.message ?: error.javaClass.simpleName
        }
    }

    fun openTelegramBot() {
        scope.launch {
            busyAction = MobileEntitlementBusyAction.TELEGRAM
            message = null
            XLog.w(message = "Mobile entitlement Telegram activation started")
            runCatching {
                val challenge = withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.createTelegramChallenge(context)
                }
                XLog.w(message = "Mobile entitlement Telegram challenge received id=%s", challenge.id.take(8))
                val url = challenge.botUrl
                if (url.isNullOrBlank()) {
                    message = qqEntryMissingText
                } else {
                    openTelegram(url)
                }
            }.onFailure {
                XLog.e(message = "Mobile entitlement Telegram activation failed", it)
                message = it.message ?: it.javaClass.simpleName
            }
            busyAction = null
        }
    }

    fun startQQActivation() {
        scope.launch {
            busyAction = MobileEntitlementBusyAction.CODE
            message = null
            XLog.w(message = "Mobile entitlement QQ activation started")
            runCatching {
                qqChallenge = withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.createQQChallenge(context)
                }
            }.onFailure {
                XLog.e(message = "Mobile entitlement QQ activation failed", it)
                qqChallenge = null
                message = it.message ?: it.javaClass.simpleName
            }
            busyAction = null
        }
    }

    fun activateWithToken(token: String) {
        scope.launch {
            busyAction = MobileEntitlementBusyAction.TOKEN
            message = null
            XLog.w(message = "Mobile entitlement token activation started")
            runCatching {
                withContext(Dispatchers.IO) {
                    MobileEntitlementCoordinator.activateByToken(context, token)
                }
            }.onSuccess {
                evaluation = it
                qqChallenge = null
                XLog.w(
                    message = "Mobile entitlement token activation success status=%s allowed=%s",
                    it.status,
                    it.automationAllowed,
                )
            }.onFailure {
                XLog.e(message = "Mobile entitlement token activation failed", it)
                message = it.message ?: it.javaClass.simpleName
            }
            busyAction = null
        }
    }

    MobileEntitlementContent(
        statusLabel = stringResource(mobileEntitlementStatusStringRes(evaluation?.status)),
        activated = isMobileEntitlementActivated(evaluation?.status),
        automationAllowed = evaluation?.automationAllowed == true,
        renewDue = evaluation?.renewDue == true,
        issuedAtSeconds = evaluation?.claims?.issuedAt ?: 0L,
        deviceId = evaluation?.claims?.deviceId?.takeIf { it.isNotBlank() },
        activationCode = qqChallenge?.activationCode,
        busy = busyAction,
        message = message,
        channelUrl = Const.QQ_CHANNEL_URL,
        onBack = onBack,
        onRefresh = { refreshStatus(force = true) },
        onGetActivationCode = { startQQActivation() },
        onOpenTelegram = { openTelegramBot() },
        onActivateToken = { activateWithToken(it) },
        onCodeSheetDismissed = { qqChallenge = null },
    )
}

private fun copyPlainText(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText(label, text))
}

internal fun isMobileEntitlementActivated(status: MobileEntitlementStatus?): Boolean =
    status == MobileEntitlementStatus.ACTIVE || status == MobileEntitlementStatus.GRACE

@androidx.annotation.StringRes
internal fun mobileEntitlementStatusStringRes(status: MobileEntitlementStatus?): Int = when {
    status == null -> R.string.mobile_entitlement_not_loaded
    isMobileEntitlementActivated(status) -> R.string.module_status_active
    else -> R.string.module_status_inactive
}
