package com.github.magisk317.smscode.xp.hook.code.action.impl

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.github.magisk317.smscode.common.constant.NotificationConst
import com.github.magisk317.smscode.hook.R
import com.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge
import com.github.magisk317.smscode.xp.hook.code.CodeNotificationBroadcastContract
import io.github.magisk317.smscode.runtime.verification.CodeNotificationDeliveryHelper
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import io.github.magisk317.smscode.runtime.verification.NotifyActionHelper
import io.github.magisk317.smscode.runtime.verification.PhoneOwnedNotificationDispatcher
import com.github.magisk317.smscode.xp.hook.code.CopyCodeReceiver
import com.github.magisk317.smscode.xp.hook.code.action.CallableAction
import com.github.magisk317.smscode.xp.hook.code.VerificationSmsMsg
import com.github.magisk317.smscode.xp.hook.code.toVerificationMessage
import io.github.magisk317.smscode.xposed.utils.XLog

/**
 * 显示验证码通知
 *
 * 两条投递路径：
 * - **app-owned**：广播请模块 App 进程代发，只在 App 存活（通常在前台）时可用，体验更好；
 * - **phone-owned**：以 phone 应用身份直接投递，不依赖模块 App 进程存活。
 *
 * 模块 App 被系统冻结后 app-owned 会以 `reason=no_receiver` 失败，因此 **phone-owned 才是
 * 保证送达的那条路**。该路径的实现已下沉到共享库的 [PhoneOwnedNotificationDispatcher]
 * （渠道解析与轮换、按投递包自检、投递后反查系统是否接受），本类只负责选择路径并提供通知本体，
 * 因为图标与文案是 hook 模块自己的资源。
 */
class NotifyAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
    private val enabled: Boolean? = null,
    private val autoCancelEnabled: Boolean? = null,
    private val retentionTimeMs: Long? = null,
) : CallableAction(pluginContext, phoneContext, smsMsg) {

    private val phoneOwned = PhoneOwnedNotificationDispatcher(
        phoneContext = mPhoneContext,
        bridge = runCatching { HookRuntimeBridge.notificationAccess }.getOrNull(),
        modulePackageName = modulePackageName(),
        primaryChannelId = NotificationConst.CHANNEL_ID_SMSCODE_NOTIFICATION,
        fallbackChannelId = NotificationConst.CHANNEL_ID_SMSCODE_NOTIFICATION_FALLBACK,
        channelName = mPluginContext.getString(R.string.hook_smscode_channel),
    )

    override fun action(): Bundle? {
        XLog.i("NotifyAction.action() called: enabled=%s smsCode=%s", enabled, mSmsMsg.smsCode)
        // Resolve the phone-owned channel (creating or rotating as needed) before the diagnostics
        // check runs, so the check inspects the channel that will really be posted into.
        val phoneChannelId = phoneOwned.resolveChannelId().channelId
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
            diagnostics = { phoneOwned.deliveryDiagnostics(phoneChannelId) },
            notifier = { request -> notifyCode(request, phoneChannelId) },
        ).run()
    }


    /**
     * Tries the phone-owned path first, then falls through to the app-owned one.
     *
     * On ColorOS/OEM skins the system's notification assistant silently suppresses notifications
     * from non-system apps (the module app uid). Notifications posted by the phone process
     * (com.android.phone, uid=1001) as a privileged system app bypass these restrictions, so
     * phone-owned is tried first. App-owned becomes the fallback for non-OEM environments or
     * when the phone-owned channel is broken.
     */
    private fun notifyCode(
        request: NotifyActionHelper.AppOwnedNotificationRequest<VerificationSmsMsg>,
        phoneChannelId: String,
    ): Bundle {
        // 1. Phone-owned first — privileged system app, bypasses OEM notification suppression.
        val outcome = phoneOwned.post(
            notificationId = request.notificationId,
            channelId = phoneChannelId,
            build = { channelId -> buildPhoneOwnedNotification(channelId, request) },
        )
        if (outcome.delivered) {
            return resultBundle(delivered = true, reason = outcome.id)
        }
        XLog.w(
            "Phone-owned notification not accepted (reason=%s), falling back to app-owned",
            outcome.id,
        )

        // 2. App-owned fallback — relies on module app process being alive.
        if (PhoneOwnedNotificationDispatcher.isPackageAllowedToPost(mPhoneContext, modulePackageName())) {
            val appResult = CodeNotificationDeliveryHelper.requestAppOwnedNotification(
                context = mPhoneContext,
                smsMsg = request.smsMsg,
                notificationId = request.notificationId,
                autoCancelEnabled = request.autoCancelEnabled,
                retentionTimeMs = request.retentionTimeMs,
                token = request.token,
                intentFactory = CodeNotificationBroadcastContract::createIntent,
            )
            if (appResult.success) return resultBundle(delivered = true, reason = APP_OWNED_REASON)
            XLog.w(
                "App-owned code notification also failed (reason=%s)",
                appResult.reason,
            )
        } else {
            XLog.w(
                "Skipping app-owned fallback: POST_NOTIFICATIONS is not granted to %s",
                modulePackageName(),
            )
        }
        return resultBundle(delivered = false, reason = "both_paths_failed")
    }

    /**
     * Builds the notification body. Posting and verification belong to the dispatcher; only the
     * content belongs here, because the icon and the strings are hook-module resources.
     */
    private fun buildPhoneOwnedNotification(
        channelId: String,
        request: NotifyActionHelper.AppOwnedNotificationRequest<VerificationSmsMsg>,
    ): Notification {
        val copyIntent = CopyCodeReceiver.createIntent(
            mPhoneContext,
            request.smsMsg.smsCode,
            request.notificationId,
        )
        val pendingIntent = PendingIntent.getBroadcast(
            mPhoneContext,
            request.notificationId,
            copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val smallIcon = runCatching {
            androidx.core.graphics.drawable.IconCompat.createWithResource(
                mPluginContext,
                R.drawable.ic_hook_app_icon,
            )
        }.getOrNull()

        val builder = NotificationCompat.Builder(mPhoneContext, channelId)
            .setContentTitle(mPluginContext.getString(R.string.hook_app_name))
            .setContentText("SMS code: ${request.smsMsg.smsCode}")
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, false)
            .setAutoCancel(true)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        if (smallIcon != null) {
            builder.setSmallIcon(smallIcon)
        } else {
            builder.setSmallIcon(android.R.drawable.stat_notify_chat)
        }
        // No setGroup(): there is no group summary anywhere in this module, and a grouped child
        // without a summary is a known way for the system to collapse the notification into an
        // empty group header - i.e. exactly the reported "nothing in the status bar".
        if (request.autoCancelEnabled && request.retentionTimeMs > 0L) {
            builder.setTimeoutAfter(request.retentionTimeMs)
        }
        return builder.build()
    }

    /** Reports the real outcome instead of the null the old implementation returned unconditionally. */
    private fun resultBundle(delivered: Boolean, reason: String): Bundle = Bundle().apply {
        putBoolean("success", delivered)
        putString("reason", reason)
    }

    private fun modulePackageName(): String =
        runCatching { mPluginContext.packageName }.getOrDefault("unknown")

    private companion object {
        const val APP_OWNED_REASON = "app_owned"
    }
}
