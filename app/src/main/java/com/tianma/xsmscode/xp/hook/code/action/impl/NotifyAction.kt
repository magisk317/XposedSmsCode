package com.tianma.xsmscode.xp.hook.code.action.impl

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.common.constant.NotificationConst
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.common.utils.XSPUtils
import com.tianma.xsmscode.data.db.entity.SmsMsg
import com.tianma.xsmscode.xp.hook.code.CopyCodeReceiver
import com.tianma.xsmscode.xp.hook.code.action.CallableAction
import de.robv.android.xposed.XSharedPreferences

/**
 * 显示验证码通知
 */
class NotifyAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
    xsp: XSharedPreferences
) : CallableAction(pluginContext, phoneContext, smsMsg, xsp) {

    override fun action(): Bundle? {
        if (XSPUtils.showCodeNotification(xsp)) {
            return showCodeNotification(mSmsMsg)
        }
        return null
    }

    @SuppressLint("UnspecifiedImmutableFlag", "NotificationPermission")
    private fun showCodeNotification(smsMsg: SmsMsg): Bundle? {
        val manager = mPhoneContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager? ?: return null

        val company = smsMsg.company
        val smsCode = smsMsg.smsCode
        val title = if (TextUtils.isEmpty(company)) smsMsg.sender else company
        val content = mPluginContext.getString(R.string.code_notification_content, smsCode)

        val notificationId = smsMsg.hashCode()

        val copyCodeIntent = CopyCodeReceiver.createIntent(smsCode)
        val contentIntent = PendingIntent.getBroadcast(
            mPhoneContext, 0, copyCodeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE or 0x01000000 // PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT
        )

        val notification = NotificationCompat.Builder(mPluginContext, NotificationConst.CHANNEL_ID_SMSCODE_NOTIFICATION)
            .setSmallIcon(R.drawable.ic_app_icon)
            .setLargeIcon(BitmapFactory.decodeResource(mPluginContext.resources, R.drawable.ic_app_icon))
            .setWhen(System.currentTimeMillis())
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setColor(ContextCompat.getColor(mPluginContext, R.color.ic_launcher_background))
            .setGroup(NotificationConst.GROUP_KEY_SMSCODE_NOTIFICATION)
            .build()

        manager.notify(notificationId, notification)
        XLog.d("Show notification succeed")

        if (XSPUtils.autoCancelCodeNotification(xsp)) {
            val retentionTime = XSPUtils.getNotificationRetentionTime(xsp) * 1000L
            val bundle = Bundle()
            bundle.putLong(NOTIFY_RETENTION_TIME, retentionTime)
            bundle.putInt(NOTIFY_ID, notificationId)
            return bundle
        }
        return null
    }

    companion object {
        const val NOTIFY_RETENTION_TIME = "notify_retention_time"
        const val NOTIFY_ID = "notify_id"
    }
}
