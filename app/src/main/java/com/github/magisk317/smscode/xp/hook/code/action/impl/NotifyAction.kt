package com.github.magisk317.smscode.xp.hook.code.action.impl

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.Bundle
import com.github.magisk317.smscode.common.constant.CodeNotificationOwner
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.common.constant.NotificationConst
import com.github.magisk317.smscode.runtime.RuntimeNotificationFacade as NotificationUtils
import com.github.magisk317.smscode.runtime.RuntimePrefsFacade as PrefsReader
import com.github.magisk317.smscode.xp.hook.code.CodeNotificationBroadcastContract
import io.github.magisk317.smscode.verification.CodeNotificationDeliveryHelper
import io.github.magisk317.smscode.verification.CodeNotificationPayload
import io.github.magisk317.smscode.xposed.utils.XLog
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
        val deliveryResult = CodeNotificationDeliveryHelper.requestAppOwnedNotification(
            context = mPhoneContext,
            smsMsg = request.smsMsg,
            notificationId = request.notificationId,
            autoCancelEnabled = request.autoCancelEnabled,
            retentionTimeMs = request.retentionTimeMs,
            token = request.token,
            intentFactory = CodeNotificationBroadcastContract::createIntent,
        )
        if (!deliveryResult.success) {
            XLog.w(
                "App-owned code notification request failed, fallback to phone-owned: reason=%s",
                deliveryResult.reason,
            )
            return showPhoneOwnedNotification(
                NotifyActionHelper.PhoneOwnedNotificationRequest(
                    smsMsg = request.smsMsg,
                    notificationId = request.notificationId,
                    autoCancelEnabled = request.autoCancelEnabled,
                    retentionTimeMs = request.retentionTimeMs,
                ),
            )
        }
        return null
    }

    @SuppressLint("UnspecifiedImmutableFlag", "NotificationPermission")
    private fun showPhoneOwnedNotification(
        request: NotifyActionHelper.PhoneOwnedNotificationRequest<VerificationSmsMsg>,
    ): Bundle? {
        val manager = mPhoneContext.getSystemService(
            Context.NOTIFICATION_SERVICE,
        ) as NotificationManager? ?: return null

        val smsCode = request.smsMsg.raw.smsCode
        val title = CodeNotificationPayload.resolveTitle(
            company = request.smsMsg.raw.company,
            sender = request.smsMsg.raw.sender,
            fallbackTitle = mPluginContext.getString(R.string.app_name),
        )

        val copyCodeIntent = CopyCodeReceiver.createIntent(mPluginContext, smsCode, request.notificationId)
        val contentIntent = PendingIntent.getBroadcast(
            mPhoneContext,
            request.notificationId,
            copyCodeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or CodeNotificationPayload.pendingIntentImmutableFlag(),
        )
        val notification = CodeNotificationDeliveryHelper.buildCodeNotification(
            context = mPluginContext,
            visualConfig = CodeNotificationDeliveryHelper.VisualConfig(
                channelId = NotificationConst.CHANNEL_ID_SMSCODE_NOTIFICATION,
                groupKey = NotificationConst.GROUP_KEY_SMSCODE_NOTIFICATION,
                smallIconResId = R.drawable.ic_app_icon,
                largeIconResId = R.drawable.ic_app_icon,
                accentColorResId = R.color.ic_launcher_background,
            ),
            title = title,
            smsCode = smsCode,
            contentIntent = contentIntent,
            contentTextProvider = { code ->
                mPluginContext.getString(R.string.code_notification_content, code)
            },
            autoCancelEnabled = request.autoCancelEnabled,
            retentionTimeMs = request.retentionTimeMs,
        )
        if (request.autoCancelEnabled) {
            if (request.retentionTimeMs > 0L) {
                scheduleAutoCancel(request.notificationId, request.retentionTimeMs)
            } else {
                XLog.i("Auto cancel skipped: retentionTimeMs=%d", request.retentionTimeMs)
            }
        } else {
            XLog.i("Auto cancel disabled")
        }

        manager.notify(request.notificationId, notification)
        XLog.i("Posted phone-owned code notification id=%d autoCancel=%s", request.notificationId, request.autoCancelEnabled)
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

    private fun scheduleAutoCancel(notificationId: Int, retentionTimeMs: Long) {
        val alarmManager = mPluginContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager? ?: return
        val appUid = mPluginContext.applicationInfo?.uid ?: -1
        if (android.os.Process.myUid() != appUid) {
            XLog.i("Skip alarm auto cancel: uid=%d, appUid=%d", android.os.Process.myUid(), appUid)
            return
        }
        val intent = AutoCancelReceiver.createIntent(mPluginContext, notificationId)
        val pendingIntent = PendingIntent.getBroadcast(
            mPluginContext,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or CodeNotificationPayload.pendingIntentImmutableFlag(),
        )
        val triggerAt = System.currentTimeMillis() + retentionTimeMs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
        XLog.i("Schedule auto cancel alarm, id=%d, delayMs=%d", notificationId, retentionTimeMs)
    }

}
