package com.github.magisk317.smscode.xp.hook.code

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.magisk317.smscode.verification.CodeNotificationActionHandler
import io.github.magisk317.smscode.verification.CodeNotificationActionPayload
import io.github.magisk317.xposed.logging.MagiskOtel

/**
 * Fallback auto-cancel when the module process is not alive.
 */
class AutoCancelReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        MagiskOtel.event(
            name = "notify.cancel",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "hook",
                "stage" to "receiver",
                "reason" to "auto_cancel",
            ),
            statusOk = true,
        )
        CodeNotificationActionHandler.handleAutoCancelReceiverIntent(context, intent)
    }

    companion object {
        const val EXTRA_NOTIFICATION_ID = CodeNotificationActionPayload.EXTRA_NOTIFICATION_ID

        fun createIntent(context: Context, notificationId: Int): Intent =
            CodeNotificationActionPayload.createAutoCancelIntent(
                context = context,
                receiverClass = AutoCancelReceiver::class.java,
                notificationId = notificationId,
            )
    }
}
