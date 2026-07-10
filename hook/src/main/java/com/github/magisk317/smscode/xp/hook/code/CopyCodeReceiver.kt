package com.github.magisk317.smscode.xp.hook.code

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.hook.BuildConfig
import io.github.magisk317.smscode.runtime.common.utils.ClipboardUtils
import io.github.magisk317.smscode.verification.CodeNotificationActionHandler
import io.github.magisk317.smscode.verification.CodeNotificationActionPayload

/**
 * Receiver for copy code when notification clicked
 */
class CopyCodeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
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
        val text = context.getString(R.string.prompt_sms_code_copied, smsCode)
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val ACTION_COPY_CODE = "${BuildConfig.APPLICATION_ID}.ACTION_COPY_CODE"

        private val instance: CopyCodeReceiver by lazy { CopyCodeReceiver() }

        @JvmStatic
        fun createIntent(context: Context, smsCode: String?, notificationId: Int): Intent =
            CodeNotificationActionPayload.createCopyCodeIntent(
                context = context,
                receiverClass = CopyCodeReceiver::class.java,
                action = ACTION_COPY_CODE,
                smsCode = smsCode,
                notificationId = notificationId,
            )

        @JvmStatic
        fun registerMe(context: Context) {
            val filter = IntentFilter()
            filter.addAction(ACTION_COPY_CODE)
            ContextCompat.registerReceiver(context, instance, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
    }
}
