package com.github.magisk317.smscode.xp.hook.code.action.impl

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import io.github.magisk317.smscode.verification.CodeNotificationActionPayload
import io.github.magisk317.smscode.xposed.utils.XLog
import com.github.magisk317.smscode.xp.hook.code.action.CallableAction

class CancelNotifyAction(pluginContext: Context, phoneContext: Context, smsMsg: SmsMsg) :
    CallableAction(pluginContext, phoneContext, smsMsg) {

    private var mNotificationId = NOTIFICATION_NONE

    fun setNotificationId(notificationId: Int) {
        mNotificationId = notificationId
    }

    override fun action(): Bundle? {
        cancelNotification()
        return null
    }

    private fun cancelNotification() {
        if (CodeNotificationActionPayload.hasNotificationId(mNotificationId, NOTIFICATION_NONE)) {
            val manager = mPhoneContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
            manager?.let {
                it.cancel(mNotificationId)
                XLog.d("Notification auto cancelled")
            }
        }
    }

    companion object {
        private const val NOTIFICATION_NONE = CodeNotificationActionPayload.AUTO_CANCEL_NOTIFICATION_ID_MISSING
    }
}
