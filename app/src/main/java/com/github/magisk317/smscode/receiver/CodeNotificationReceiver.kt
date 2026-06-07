package com.github.magisk317.smscode.receiver

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.github.magisk317.smscode.common.constant.NotificationConst
import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.common.utils.AppPreferencesDataStore
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.xp.hook.code.AutoCancelReceiver
import com.github.magisk317.smscode.xp.hook.code.CodeNotificationBroadcastContract
import com.github.magisk317.smscode.xp.hook.code.CopyCodeReceiver
import com.github.magisk317.smscode.runtime.RuntimeNotificationFacade as NotificationUtils
import io.github.magisk317.smscode.verification.CodeNotificationDeliveryHelper
import io.github.magisk317.smscode.verification.CodeNotificationPayload
import io.github.magisk317.smscode.xposed.utils.XLog
import kotlinx.coroutines.runBlocking

class CodeNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CodeNotificationBroadcastContract.ACTION_SHOW_CODE_NOTIFICATION) {
            return
        }

        val payload = CodeNotificationPayload.readPayload(intent)
        val smsCode = payload.smsCode.orEmpty()
        if (smsCode.isBlank()) {
            XLog.w("CodeNotificationReceiver ignored blank smsCode")
            finishOrderedResult(CodeNotificationPayload.RESULT_DATA_BLANK_CODE)
            return
        }

        val expectedToken = runBlocking {
            AppPreferencesDataStore.getString(context, PrefConst.KEY_IPC_TOKEN, "")
        }
        val receivedToken = payload.token
        val sentFromUid = resolveSentFromUidCompat()
        val tokenMatched = expectedToken.isNotBlank() && receivedToken == expectedToken
        val allowSystemBypass = expectedToken.isBlank() && CodeNotificationPayload.shouldAllowSmsHookTokenBypass(sentFromUid)
        if (!tokenMatched && !allowSystemBypass) {
            XLog.w(
                "CodeNotificationReceiver rejected token. expectedEmpty=%s receivedEmpty=%s sentFromUid=%d",
                expectedToken.isBlank(),
                receivedToken.isNullOrBlank(),
                sentFromUid ?: -1,
            )
            finishOrderedResult(CodeNotificationPayload.RESULT_DATA_REJECTED_TOKEN)
            return
        }

        val appContext = context.applicationContext
        val notificationId = payload.notificationId
        val autoCancelEnabled = payload.autoCancelEnabled
        val retentionTimeMs = payload.retentionTimeMs
        val title = CodeNotificationPayload.resolveTitle(
            company = payload.company,
            sender = payload.sender,
            fallbackTitle = appContext.getString(R.string.app_name),
        )
        val content = appContext.getString(R.string.code_notification_content, smsCode)

        NotificationUtils.createNotificationChannel(
            appContext,
            NotificationConst.CHANNEL_ID_SMSCODE_NOTIFICATION,
            appContext.getString(R.string.channel_name_smscode_notification),
            NotificationManager.IMPORTANCE_HIGH,
        )

        val copyIntent = CopyCodeReceiver.createIntent(appContext, smsCode, notificationId)
        val contentIntent = PendingIntent.getBroadcast(
            appContext,
            notificationId,
            copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or CodeNotificationPayload.pendingIntentImmutableFlag(),
        )

        val notification = CodeNotificationDeliveryHelper.buildCodeNotification(
            context = appContext,
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
                appContext.getString(R.string.code_notification_content, code)
            },
            autoCancelEnabled = autoCancelEnabled,
            retentionTimeMs = retentionTimeMs,
        )

        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        if (manager == null) {
            XLog.w("CodeNotificationReceiver missing NotificationManager")
            finishOrderedResult(CodeNotificationPayload.RESULT_DATA_MISSING_NOTIFICATION_MANAGER)
            return
        }

        val diagnostics = NotificationUtils.inspectDelivery(
            appContext,
            NotificationConst.CHANNEL_ID_SMSCODE_NOTIFICATION,
        )
        XLog.i("CodeNotificationReceiver delivery diagnostics: %s", diagnostics.summary())
        if (!diagnostics.canPost) {
            XLog.w(
                "CodeNotificationReceiver posting while app-owned notifications are unavailable: %s",
                diagnostics.summary(),
            )
        }

        runCatching {
            showNotification(manager, notificationId, notification)
        }.onFailure { throwable ->
            XLog.w(
                "CodeNotificationReceiver notify failed: id=%d err=%s",
                notificationId,
                throwable.message ?: throwable.javaClass.simpleName,
            )
            finishOrderedResult(CodeNotificationPayload.RESULT_DATA_NOTIFY_FAILED)
            return
        }
        XLog.i("CodeNotificationReceiver posted app-owned notification id=%d", notificationId)
        finishOrderedResult(CodeNotificationPayload.RESULT_DATA_POSTED, success = true)

        if (autoCancelEnabled && retentionTimeMs > 0L) {
            scheduleAutoCancelSafely(appContext, notificationId, retentionTimeMs)
        }
    }

    @SuppressLint("NotificationPermission")
    private fun showNotification(
        manager: NotificationManager,
        notificationId: Int,
        notification: android.app.Notification,
    ) {
        manager.notify(notificationId, notification)
    }

    private fun scheduleAutoCancelSafely(
        context: Context,
        notificationId: Int,
        retentionTimeMs: Long,
    ) {
        runCatching {
            scheduleAutoCancel(context, notificationId, retentionTimeMs)
        }.onFailure { throwable ->
            XLog.w(
                "CodeNotificationReceiver auto-cancel scheduling failed: id=%d retentionMs=%d err=%s",
                notificationId,
                retentionTimeMs,
                throwable.message ?: throwable.javaClass.simpleName,
            )
        }
    }

    private fun scheduleAutoCancel(
        context: Context,
        notificationId: Int,
        retentionTimeMs: Long,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager? ?: return
        val intent = AutoCancelReceiver.createIntent(context, notificationId)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or CodeNotificationPayload.pendingIntentImmutableFlag(),
        )
        val triggerAt = System.currentTimeMillis() + retentionTimeMs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun resolveSentFromUidCompat(): Int? {
        if (Build.VERSION.SDK_INT < API_LEVEL_34) return null
        return runCatching { getSentFromUid() }.getOrNull()
    }

    private fun finishOrderedResult(reason: String, success: Boolean = false) {
        if (!isOrderedBroadcast) return
        setResult(
            if (success) {
                CodeNotificationPayload.RESULT_CODE_APP_NOTIFICATION_POSTED
            } else {
                CodeNotificationPayload.RESULT_CODE_APP_NOTIFICATION_FAILED
            },
            reason,
            null,
        )
    }

    private companion object {
        private const val API_LEVEL_34 = 34
    }
}
