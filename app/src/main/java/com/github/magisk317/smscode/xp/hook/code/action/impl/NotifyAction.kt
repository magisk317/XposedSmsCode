package com.github.magisk317.smscode.xp.hook.code.action.impl

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import com.github.magisk317.smscode.common.constant.CodeNotificationOwner
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.common.constant.NotificationConst
import com.github.magisk317.smscode.runtime.RuntimeNotificationFacade as NotificationUtils
import com.github.magisk317.smscode.runtime.RuntimePrefsFacade as PrefsReader
import com.github.magisk317.smscode.xp.hook.code.CodeNotificationBroadcastContract
import io.github.magisk317.smscode.verification.CodeNotificationDeliveryHelper
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import io.github.magisk317.smscode.verification.NotifyActionHelper
import com.github.magisk317.smscode.xp.hook.code.AutoCancelReceiver
import com.github.magisk317.smscode.xp.hook.code.CopyCodeReceiver
import com.github.magisk317.smscode.xp.hook.code.action.CallableAction
import com.github.magisk317.smscode.xp.hook.code.VerificationSmsMsg
import com.github.magisk317.smscode.xp.hook.code.toVerificationMessage

/**
 * 显示验证码通知
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
        return NotifyActionHelper<VerificationSmsMsg, Bundle?>(
            pluginContext = mPluginContext,
            smsMsg = mSmsMsg.toVerificationMessage(),
            enabled = enabled ?: PrefsReader.showCodeNotification(mPluginContext),
            ownerReader = PrefsReader::getCodeNotificationOwner,
            appOwnedValue = CodeNotificationOwner.APP,
            phoneOwnedValue = CodeNotificationOwner.PHONE,
            autoCancelEnabledProvider = { context ->
                autoCancelEnabled ?: PrefsReader.autoCancelCodeNotification(context)
            },
            retentionTimeMsProvider = { context ->
                retentionTimeMs ?: (PrefsReader.getNotificationRetentionTime(context) * 1000L)
            },
            tokenProvider = { context -> PrefsReader.getIpcToken(context).takeIf(String::isNotBlank) },
            appOwnedChannelInitializer = ::ensureNotificationChannel,
            phoneOwnedChannelInitializer = { ensureNotificationChannel(mPhoneContext) },
            appOwnedDiagnostics = { context ->
                NotificationUtils.inspectDelivery(context, NotificationConst.CHANNEL_ID_SMSCODE_NOTIFICATION).toShared()
            },
            appOwnedNotifier = { request -> showAppOwnedNotification(request) },
            phoneOwnedNotifier = { request -> showPhoneOwnedNotification(request) },
        ).run()
    }

    private fun showAppOwnedNotification(
        request: NotifyActionHelper.AppOwnedNotificationRequest<VerificationSmsMsg>,
    ): Bundle? {
        return CodeNotificationDeliveryHelper.requestAppOwnedNotificationOrFallback(
            context = mPhoneContext,
            request = request,
            intentFactory = CodeNotificationBroadcastContract::createIntent,
            fallbackNotifier = ::showPhoneOwnedNotification,
        )
    }

    private fun showPhoneOwnedNotification(
        request: NotifyActionHelper.PhoneOwnedNotificationRequest<VerificationSmsMsg>,
    ): Bundle? {
        CodeNotificationDeliveryHelper.showPhoneOwnedNotification(
            phoneContext = mPhoneContext,
            pluginContext = mPluginContext,
            request = request,
            visualConfig = CodeNotificationDeliveryHelper.VisualConfig(
                channelId = NotificationConst.CHANNEL_ID_SMSCODE_NOTIFICATION,
                groupKey = NotificationConst.GROUP_KEY_SMSCODE_NOTIFICATION,
                smallIconResId = R.drawable.ic_app_icon,
                largeIconResId = R.drawable.ic_app_icon,
                accentColorResId = R.color.ic_launcher_background,
            ),
            fallbackTitle = mPluginContext.getString(R.string.app_name),
            contentTextProvider = { code ->
                mPluginContext.getString(R.string.code_notification_content, code)
            },
            copyCodeIntentFactory = CopyCodeReceiver::createIntent,
            autoCancelIntentFactory = AutoCancelReceiver::createIntent,
        )
        return null
    }

    private fun ensureNotificationChannel(context: Context) {
        NotificationUtils.createNotificationChannel(
            context,
            NotificationConst.CHANNEL_ID_SMSCODE_NOTIFICATION,
            mPluginContext.getString(R.string.channel_name_smscode_notification),
            NotificationManager.IMPORTANCE_HIGH,
        )
    }

    private fun NotificationUtils.DeliveryDiagnostics.toShared(): NotifyActionHelper.DeliveryDiagnostics {
        return NotifyActionHelper.DeliveryDiagnostics(
            canPost = canPost,
            summary = summary(),
        )
    }

}
