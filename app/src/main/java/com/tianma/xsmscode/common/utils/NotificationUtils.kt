package com.tianma.xsmscode.common.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationUtils {

    @JvmStatic
    fun createNotificationChannel(context: Context, channelId: String,
                                  channelName: String, importance: Int) {
        val channel = NotificationChannel(channelId, channelName, importance)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        manager?.createNotificationChannel(channel)
    }
}
