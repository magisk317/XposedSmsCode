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
import io.github.magisk317.smscode.xposed.utils.XLog

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
            appOwnedChannelInitializer = {},
            phoneOwnedChannelInitializer = { ensureNotificationChannel(mPhoneContext) },
            appOwnedDiagnostics = {
                NotifyActionHelper.DeliveryDiagnostics(
                    canPost = true,
                    summary = "deferred_to_receiver",
                )
            },
            appOwnedNotifier = { request -> showAppOwnedNotification(request) },
            phoneOwnedNotifier = { request -> showPhoneOwnedNotification(request) },
        ).run()
    }

    private fun showAppOwnedNotification(
        request: NotifyActionHelper.AppOwnedNotificationRequest<VerificationSmsMsg>,
    ): Bundle? {
        if (AppOwnedNotificationDeliveryGuard.shouldFallbackToPhoneOwned(
                context = mPhoneContext,
                targetPackage = mPluginContext.packageName,
            )
        ) {
            XLog.w(
                "App-owned code notification target is background/cached on Xiaomi-family device, fallback to phone-owned",
            )
            return showPhoneOwnedNotification(request.toPhoneOwnedRequest())
        }
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
                channelId = resolvePhoneOwnedChannelId(),
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

    private fun NotifyActionHelper.AppOwnedNotificationRequest<VerificationSmsMsg>.toPhoneOwnedRequest():
        NotifyActionHelper.PhoneOwnedNotificationRequest<VerificationSmsMsg> {
        return NotifyActionHelper.PhoneOwnedNotificationRequest(
            smsMsg = smsMsg,
            notificationId = notificationId,
            autoCancelEnabled = autoCancelEnabled,
            retentionTimeMs = retentionTimeMs,
        )
    }

    private fun ensureNotificationChannel(context: Context) {
        val channelId = if (context.packageName == PACKAGE_MMS) {
            resolvePhoneOwnedChannelId()
        } else {
            NotificationConst.CHANNEL_ID_SMSCODE_NOTIFICATION
        }
        if (channelId != NotificationConst.CHANNEL_ID_SMSCODE_NOTIFICATION) {
            XLog.i(
                "Reuse host SMS notification channel for phone-owned code notification: %s",
                channelId,
            )
            return
        }
        NotificationUtils.createNotificationChannel(
            context,
            NotificationConst.CHANNEL_ID_SMSCODE_NOTIFICATION,
            mPluginContext.getString(R.string.channel_name_smscode_notification),
            NotificationManager.IMPORTANCE_HIGH,
        )
    }

    private fun resolvePhoneOwnedChannelId(): String {
        if (mPhoneContext.packageName != PACKAGE_MMS) {
            return NotificationConst.CHANNEL_ID_SMSCODE_NOTIFICATION
        }
        val manager = mPhoneContext.getSystemService(NotificationManager::class.java) ?: return MMS_DEFAULT_CHANNEL_ID
        val channels = manager.notificationChannels
        val channel = channels.firstOrNull { channel ->
            channel.id.startsWith(MMS_MESSAGE_CHANNEL_PREFIX) &&
                channel.importance != NotificationManager.IMPORTANCE_NONE
        } ?: channels.firstOrNull { channel ->
            channel.group == MMS_MESSAGE_CHANNEL_GROUP &&
                channel.importance != NotificationManager.IMPORTANCE_NONE
        } ?: channels.firstOrNull { channel ->
            channel.id == MMS_DEFAULT_CHANNEL_ID &&
                channel.importance != NotificationManager.IMPORTANCE_NONE
        }
        return channel?.id ?: MMS_DEFAULT_CHANNEL_ID
    }

    private fun NotificationUtils.DeliveryDiagnostics.toShared(): NotifyActionHelper.DeliveryDiagnostics {
        return NotifyActionHelper.DeliveryDiagnostics(
            canPost = canPost,
            summary = summary(),
        )
    }

    private companion object {
        const val PACKAGE_MMS = "com.android.mms"
        const val MMS_DEFAULT_CHANNEL_ID = "Mms_Default"
        const val MMS_MESSAGE_CHANNEL_GROUP = "Channel_Msg_Group"
        const val MMS_MESSAGE_CHANNEL_PREFIX = "Channel_Msg_Default"
    }
}
