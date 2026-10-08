package io.github.magisk317.smscode.xp.hook.code

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import io.github.magisk317.smscode.hook.BuildConfig
import io.github.magisk317.smscode.hook.R
import io.github.magisk317.smscode.runtime.common.utils.ClipboardUtils
import io.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge
import io.github.magisk317.smscode.runtime.verification.CodeNotificationActionHandler
import io.github.magisk317.smscode.runtime.verification.CodeNotificationActionPayload
import io.github.magisk317.smscode.xposed.hook.telephony.CopyCodeReceiverRegistrar
import io.github.magisk317.smscode.xposed.utils.XLog
import io.github.magisk317.xposed.logging.MagiskOtel

/**
 * Receiver for copy code when notification clicked
 */
class CopyCodeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!runCatching { HookRuntimeBridge.prefsAccess.mobileAutomationAllowed(context) }.getOrDefault(false)) {
            MagiskOtel.event(
                name = "sms.copy",
                attributes = mapOf("result" to "skip", "process" to "hook", "reason" to "mobile_entitlement"),
                statusOk = true,
            )
            return
        }
        MagiskOtel.event(
            name = "sms.copy",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "hook",
                "stage" to "receiver",
                "reason" to "copy_click",
            ),
            statusOk = true,
        )
        CodeNotificationActionHandler.handleCopyCodeReceiverIntent(
            context = context,
            intent = intent,
            expectedAction = ACTION_COPY_CODE,
            copyCode = { smsCode ->
                ClipboardUtils.copyToClipboard(context, smsCode)
                showToast(context, smsCode)
            },
        )
    }

    private fun showToast(context: Context, smsCode: String) {
        val text = context.getString(R.string.hook_sms_code_copied, smsCode)
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val ACTION_COPY_CODE = "${BuildConfig.APPLICATION_ID}.ACTION_COPY_CODE"

        private val instance: CopyCodeReceiver by lazy { CopyCodeReceiver() }

        private val registrar = CopyCodeReceiverRegistrar(instance, ACTION_COPY_CODE)

        @JvmStatic
        fun createIntent(context: Context, smsCode: String?, notificationId: Int): Intent =
            CodeNotificationActionPayload.createCopyCodeIntent(
                context = context,
                receiverClass = CopyCodeReceiver::class.java,
                action = ACTION_COPY_CODE,
                smsCode = smsCode,
                notificationId = notificationId,
            )

        /**
         * Registers the receiver unless the user turned code notifications off.
         *
         * The pref is checked here rather than at the call site so the gate travels with
         * the registration, matching the other host.
         */
        @JvmStatic
        fun registerMe(context: Context) {
            registrar.registerMe(context) {
                runCatching { HookRuntimeBridge.prefsAccess.showCodeNotification(context) }
                    .getOrDefault(false)
            }
        }

        @JvmStatic
        fun unregisterMe(context: Context) {
            registrar.unregisterMe(context)
        }
    }
}
