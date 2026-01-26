package com.tianma.xsmscode.xp.hook.code.action.impl

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.data.db.entity.SmsMsg
import com.tianma.xsmscode.xp.hook.code.action.CallableAction
import de.robv.android.xposed.XSharedPreferences

class CancelNotifyAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
    xsp: XSharedPreferences
) : CallableAction(pluginContext, phoneContext, smsMsg, xsp) {

    private var mNotificationId = NOTIFICATION_NONE

    fun setNotificationId(notificationId: Int) {
        mNotificationId = notificationId
    }

    override fun action(): Bundle? {
        cancelNotification()
        return null
    }

    private fun cancelNotification() {
        if (mNotificationId != NOTIFICATION_NONE) {
            val manager = mPhoneContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
            manager?.let {
                it.cancel(mNotificationId)
                XLog.d("Notification auto cancelled")
            }
        }
    }

    companion object {
        private const val NOTIFICATION_NONE = -0xff
    }
}
