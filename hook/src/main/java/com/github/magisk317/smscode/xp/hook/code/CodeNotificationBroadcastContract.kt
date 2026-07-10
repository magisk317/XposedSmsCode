package com.github.magisk317.smscode.xp.hook.code

import android.content.Intent
import com.github.magisk317.smscode.runtime.bridge.HookBroadcastContract
import com.github.magisk317.smscode.hook.BuildConfig
import io.github.magisk317.smscode.verification.CodeNotificationPayload

object CodeNotificationBroadcastContract {
    private val sharedContract = CodeNotificationPayload.BroadcastContract(
        applicationId = BuildConfig.APPLICATION_ID,
        receiverClassName = HookBroadcastContract.CODE_NOTIFICATION_RECEIVER_CLASS,
    )

    val ACTION_SHOW_CODE_NOTIFICATION: String =
        sharedContract.actionShowCodeNotification

    const val EXTRA_SENDER = CodeNotificationPayload.EXTRA_SENDER
    const val EXTRA_COMPANY = CodeNotificationPayload.EXTRA_COMPANY
    const val EXTRA_SMS_CODE = CodeNotificationPayload.EXTRA_SMS_CODE
    const val EXTRA_NOTIFICATION_ID = CodeNotificationPayload.EXTRA_NOTIFICATION_ID
    const val EXTRA_AUTO_CANCEL_ENABLED = CodeNotificationPayload.EXTRA_AUTO_CANCEL_ENABLED
    const val EXTRA_RETENTION_TIME_MS = CodeNotificationPayload.EXTRA_RETENTION_TIME_MS
    const val EXTRA_IPC_TOKEN = CodeNotificationPayload.EXTRA_IPC_TOKEN

    fun createIntent(
        sender: String?,
        company: String?,
        smsCode: String?,
        notificationId: Int,
        autoCancelEnabled: Boolean,
        retentionTimeMs: Long,
        token: String?,
    ): Intent =
        sharedContract.createIntent(
            sender = sender,
            company = company,
            smsCode = smsCode,
            notificationId = notificationId,
            autoCancelEnabled = autoCancelEnabled,
            retentionTimeMs = retentionTimeMs,
            token = token,
        )
}
