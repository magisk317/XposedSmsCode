package io.github.magisk317.relay.platform.ipc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.common.utils.AppPreferencesDataStore
import com.github.magisk317.smscode.receiver.CodeNotificationReceiverConfig
import com.github.magisk317.smscode.xp.hook.code.CodeNotificationBroadcastContract
import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import io.github.magisk317.smscode.verification.CodeNotificationPayload
import io.github.magisk317.smscode.verification.CodeNotificationReceiverHandler
import io.github.magisk317.smscode.xposed.hook.notification.NotificationHookConst
import io.github.magisk317.smscode.xposed.utils.XLog
import kotlinx.coroutines.runBlocking

class ForwardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        XLog.withRoute(LogRoute.NMS_HOOK) {
            handleForward(context, intent)
        }
    }

    private fun handleForward(context: Context, intent: Intent) {
        val action = intent.action.orEmpty()
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()
        val sourcePackage = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        val msgType = intent.getStringExtra(EXTRA_MSG_TYPE).orEmpty()
        XLog.i(
            "NmsForwardReceiver received: action=%s event=%s pkg=%s type=%s",
            action.ifBlank { "<empty>" },
            eventId.ifBlank { "<empty>" },
            sourcePackage.ifBlank { "<empty>" },
            msgType.ifBlank { "<empty>" },
        )

        if (action != NotificationHookConst.ACTION_FORWARD_SMS) {
            XLog.w(
                "NmsForwardReceiver ignored unexpected action: action=%s expected=%s event=%s",
                action.ifBlank { "<empty>" },
                NotificationHookConst.ACTION_FORWARD_SMS,
                eventId.ifBlank { "<empty>" },
            )
            return
        }

        val appContext = context.applicationContext ?: context
        if (!showCodeNotification(appContext)) {
            XLog.w(
                "NmsForwardReceiver skipped: notification preference disabled event=%s pkg=%s type=%s",
                eventId.ifBlank { "<empty>" },
                sourcePackage.ifBlank { "<empty>" },
                msgType.ifBlank { "<empty>" },
            )
            val resultSet = CodeNotificationPayload.finishOrderedResult(this, RESULT_DATA_NOTIFICATION_DISABLED)
            XLog.i(
                "NmsForwardReceiver finished skip: event=%s reason=%s orderedResult=%s",
                eventId.ifBlank { "<empty>" },
                RESULT_DATA_NOTIFICATION_DISABLED,
                resultSet,
            )
            return
        }

        val smsCode = readSmsCode(intent)
        if (smsCode.isBlank()) {
            XLog.w(
                "NmsForwardReceiver skipped: missing smsCode event=%s pkg=%s type=%s",
                eventId.ifBlank { "<empty>" },
                sourcePackage.ifBlank { "<empty>" },
                msgType.ifBlank { "<empty>" },
            )
            val resultSet = CodeNotificationPayload.finishOrderedResult(this, CodeNotificationPayload.RESULT_DATA_BLANK_CODE)
            XLog.i(
                "NmsForwardReceiver finished skip: event=%s reason=%s orderedResult=%s",
                eventId.ifBlank { "<empty>" },
                CodeNotificationPayload.RESULT_DATA_BLANK_CODE,
                resultSet,
            )
            return
        }

        val autoCancelEnabled = autoCancelCodeNotification(appContext)
        val retentionTimeMs = if (autoCancelEnabled) {
            getNotificationRetentionTime(appContext) * 1000L
        } else {
            0L
        }
        val translatedIntent = createCodeNotificationIntent(
            sourceIntent = intent,
            smsCode = smsCode,
            autoCancelEnabled = autoCancelEnabled,
            retentionTimeMs = retentionTimeMs,
        )

        XLog.i(
            "NmsForwardReceiver delegate: event=%s pkg=%s type=%s codeLen=%d autoCancel=%s retentionMs=%d tokenPresent=%s",
            eventId.ifBlank { "<empty>" },
            sourcePackage.ifBlank { "<empty>" },
            msgType.ifBlank { "<empty>" },
            smsCode.length,
            autoCancelEnabled,
            retentionTimeMs,
            !intent.getStringExtra(NotificationHookConst.EXTRA_IPC_TOKEN).isNullOrBlank(),
        )
        val ordered = isOrderedBroadcast
        CodeNotificationReceiverHandler.handleBroadcast(
            receiver = this,
            context = appContext,
            intent = translatedIntent,
            config = CodeNotificationReceiverConfig.create(
                context = appContext,
                source = "NmsForwardReceiver",
                sentFromUidProvider = ::getSentFromUidCompat,
            ),
        )
        XLog.i(
            "NmsForwardReceiver handled: event=%s pkg=%s ordered=%s resultCode=%d resultData=%s",
            eventId.ifBlank { "<empty>" },
            sourcePackage.ifBlank { "<empty>" },
            ordered,
            if (ordered) resultCode else 0,
            if (ordered) resultData ?: "<null>" else "<not-ordered>",
        )
    }

    private fun createCodeNotificationIntent(
        sourceIntent: Intent,
        smsCode: String,
        autoCancelEnabled: Boolean,
        retentionTimeMs: Long,
    ): Intent {
        return CodeNotificationPayload.fillIntent(
            Intent(CodeNotificationBroadcastContract.ACTION_SHOW_CODE_NOTIFICATION),
            CodeNotificationPayload.Payload(
                sender = sourceIntent.getStringExtra(CodeNotificationPayload.EXTRA_SENDER),
                company = sourceIntent.getStringExtra(CodeNotificationPayload.EXTRA_COMPANY),
                smsCode = smsCode,
                notificationId = resolveNotificationId(sourceIntent),
                autoCancelEnabled = autoCancelEnabled,
                retentionTimeMs = retentionTimeMs,
                token = sourceIntent.getStringExtra(NotificationHookConst.EXTRA_IPC_TOKEN),
            ),
        )
    }

    private fun readSmsCode(intent: Intent): String {
        return intent.getStringExtra(EXTRA_SMS_CODE_LEGACY)
            ?: intent.getStringExtra(CodeNotificationPayload.EXTRA_SMS_CODE)
            ?: ""
    }

    private fun resolveNotificationId(intent: Intent): Int {
        if (intent.hasExtra(CodeNotificationPayload.EXTRA_NOTIFICATION_ID)) {
            return intent.getIntExtra(CodeNotificationPayload.EXTRA_NOTIFICATION_ID, 0)
        }
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID).orEmpty()
        val sourcePackage = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        val seed = eventId.ifBlank {
            "${sourcePackage.ifBlank { "nms" }}_${System.currentTimeMillis()}"
        }
        return seed.hashCode()
    }

    private fun showCodeNotification(context: Context): Boolean {
        return runBlocking {
            AppPreferencesDataStore.getBoolean(
                context,
                PrefConst.KEY_SHOW_CODE_NOTIFICATION,
                true,
            )
        }
    }

    private fun autoCancelCodeNotification(context: Context): Boolean {
        return runBlocking {
            AppPreferencesDataStore.getBoolean(
                context,
                PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION,
                false,
            )
        }
    }

    private fun getNotificationRetentionTime(context: Context): Long {
        return runBlocking {
            AppPreferencesDataStore.getString(
                context,
                PrefConst.KEY_NOTIFICATION_RETENTION_TIME,
                PrefConst.NOTIFICATION_RETENTION_TIME_DEFAULT,
            )
        }.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
    }

    private fun getSentFromUidCompat(): Int {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            getSentFromUid()
        } else {
            -1
        }
    }

    private companion object {
        private const val EXTRA_EVENT_ID = "event_id"
        private const val EXTRA_PACKAGE_NAME = "packageName"
        private const val EXTRA_MSG_TYPE = "msgType"
        private const val EXTRA_SMS_CODE_LEGACY = "smsCode"
        private const val RESULT_DATA_NOTIFICATION_DISABLED = "notification_disabled"
    }
}
