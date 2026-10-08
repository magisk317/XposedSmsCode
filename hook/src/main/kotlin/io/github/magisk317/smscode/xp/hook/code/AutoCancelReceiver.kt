package io.github.magisk317.smscode.xp.hook.code

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge
import io.github.magisk317.smscode.runtime.verification.CodeNotificationActionHandler
import io.github.magisk317.smscode.runtime.verification.CodeNotificationActionPayload
import io.github.magisk317.xposed.logging.MagiskOtel

/**
 * Fallback auto-cancel when the module process is not alive.
 */
class AutoCancelReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!runCatching { HookRuntimeBridge.prefsAccess.mobileAutomationAllowed(context) }.getOrDefault(false)) {
            MagiskOtel.event(
                name = "notify.cancel",
                attributes = mapOf("result" to "skip", "process" to "hook", "reason" to "mobile_entitlement"),
                statusOk = true,
            )
            return
        }
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
