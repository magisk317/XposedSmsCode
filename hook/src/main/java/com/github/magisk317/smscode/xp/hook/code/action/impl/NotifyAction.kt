package com.github.magisk317.smscode.xp.hook.code.action.impl

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.common.constant.NotificationConst
import com.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge
import com.github.magisk317.smscode.xp.hook.code.CodeNotificationBroadcastContract
import io.github.magisk317.smscode.verification.CodeNotificationDeliveryHelper
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import io.github.magisk317.smscode.verification.NotifyActionHelper
import com.github.magisk317.smscode.xp.hook.code.AutoCancelReceiver
import com.github.magisk317.smscode.xp.hook.code.CopyCodeReceiver
import com.github.magisk317.smscode.xp.hook.code.action.CallableAction
import com.github.magisk317.smscode.xp.hook.code.VerificationSmsMsg
import com.github.magisk317.smscode.xp.hook.code.toVerificationMessage
import io.github.magisk317.smscode.xposed.utils.XLog

/**
 * 显示验证码通知（app-owned，从 SmsCode app 进程发出）
 */
class NotifyAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
    private val enabled: Boolean? = null,
    private val autoCancelEnabled: Boolean? = null,
    private val retentionTimeMs: Long? = null,
) : CallableAction(pluginContext, phoneContext, smsMsg) {

    override fun action(): Bundle? {
        XLog.i("NotifyAction.action() called: enabled=%s smsCode=%s", enabled, mSmsMsg.smsCode)
        return NotifyActionHelper<VerificationSmsMsg, Bundle?>(
            pluginContext = mPluginContext,
            smsMsg = mSmsMsg.toVerificationMessage(),
            enabled = enabled ?: HookRuntimeBridge.prefsAccess.showCodeNotification(mPluginContext),
            autoCancelEnabledProvider = { context ->
                autoCancelEnabled ?: HookRuntimeBridge.prefsAccess.autoCancelCodeNotification(context)
            },
            retentionTimeMsProvider = { context ->
                retentionTimeMs ?: (HookRuntimeBridge.prefsAccess.getNotificationRetentionTime(context) * 1000L)
            },
            tokenProvider = { context -> HookRuntimeBridge.prefsAccess.getIpcToken(context).takeIf(String::isNotBlank) },
            channelInitializer = { context -> ensureNotificationChannel(context) },
            diagnostics = {
                NotifyActionHelper.DeliveryDiagnostics(
                    canPost = true,
                    summary = "deferred_to_receiver",
                )
            },
            notifier = { request -> showAppOwnedNotification(request) },
        ).run()
    }

    private fun showAppOwnedNotification(
        request: NotifyActionHelper.AppOwnedNotificationRequest<VerificationSmsMsg>,
    ): Bundle? {
        CodeNotificationDeliveryHelper.requestAppOwnedNotification(
            context = mPhoneContext,
            smsMsg = request.smsMsg,
            notificationId = request.notificationId,
            autoCancelEnabled = request.autoCancelEnabled,
            retentionTimeMs = request.retentionTimeMs,
            token = request.token,
            intentFactory = CodeNotificationBroadcastContract::createIntent,
        )
        return null
    }

    private fun ensureNotificationChannel(context: Context) {
        // Channel creation in the hook process is best-effort. The actual notification
        // is posted from the SmsCode app process via broadcast, where the receiver
        // creates the channel with the correct UID. We catch any SecurityException
        // because uid 10148 (com.android.mms) cannot manage channels for
        // com.github.tianma8023.xposed.smscode (uid 10353).
        runCatching {
            HookRuntimeBridge.notificationAccess.createNotificationChannel(
                mPhoneContext,
                NotificationConst.CHANNEL_ID_SMSCODE_NOTIFICATION,
                mPluginContext.getString(R.string.channel_name_smscode_notification),
                NotificationManager.IMPORTANCE_HIGH,
            )
        }
    }
}
