package io.github.magisk317.relay.platform.ipc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.runtime.RuntimePrefsFacade as PrefsReader
import com.github.magisk317.smscode.xp.hook.code.SmsCodeActionDispatcher
import com.github.magisk317.smscode.xp.hook.code.SmsCodeVerificationPrefs
import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import io.github.magisk317.smscode.verification.CodeNotificationPayload
import io.github.magisk317.smscode.verification.SmsCodePostParseCoordinator
import io.github.magisk317.smscode.xposed.hook.notification.NotificationHookConst
import io.github.magisk317.smscode.xposed.utils.XLog
import java.util.concurrent.Executors

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
        if (!PrefsReader.isEnabled(appContext)) {
            XLog.w(
                "NmsForwardReceiver skipped: module disabled event=%s pkg=%s type=%s",
                eventId.ifBlank { "<empty>" },
                sourcePackage.ifBlank { "<empty>" },
                msgType.ifBlank { "<empty>" },
            )
            val resultSet = CodeNotificationPayload.finishOrderedResult(this, RESULT_DATA_MODULE_DISABLED)
            XLog.i(
                "NmsForwardReceiver finished skip: event=%s reason=%s orderedResult=%s",
                eventId.ifBlank { "<empty>" },
                RESULT_DATA_MODULE_DISABLED,
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

        val smsMsg = createNotificationSmsMsg(intent, smsCode, msgType)
        val plan = createNotificationCodePlan(appContext, smsMsg.msgType)

        XLog.i(
            "NmsForwardReceiver dispatch local actions: event=%s pkg=%s type=%s codeLen=%d notify=%s copy=%s record=%s autoInputDelay=%s",
            eventId.ifBlank { "<empty>" },
            sourcePackage.ifBlank { "<empty>" },
            msgType.ifBlank { "<empty>" },
            smsCode.length,
            plan.notificationPlan != null,
            plan.uiPlan.copyToClipboardEnabled,
            plan.shouldRecord,
            plan.autoInputDelayMs?.toString() ?: "<disabled>",
        )
        val ordered = isOrderedBroadcast
        val dispatched = dispatchLocalNotificationCodeActions(
            appContext = appContext,
            smsMsg = smsMsg,
            eventId = eventId,
            plan = plan,
        )
        val resultSet = CodeNotificationPayload.finishOrderedResult(
            receiver = this,
            reason = if (dispatched) RESULT_DATA_ACTIONS_DISPATCHED else RESULT_DATA_ACTIONS_FAILED,
            success = dispatched,
        )
        XLog.i(
            "NmsForwardReceiver handled: event=%s pkg=%s ordered=%s resultCode=%d resultData=%s orderedResult=%s",
            eventId.ifBlank { "<empty>" },
            sourcePackage.ifBlank { "<empty>" },
            ordered,
            if (ordered) resultCode else 0,
            if (ordered) resultData ?: "<null>" else "<not-ordered>",
            resultSet,
        )
    }

    private fun createNotificationSmsMsg(
        intent: Intent,
        smsCode: String,
        msgType: String,
    ): SmsMsg {
        val timestamp = intent.getLongExtra(EXTRA_DATE, 0L).takeIf { it > 0L } ?: System.currentTimeMillis()
        return SmsMsg(
            sender = intent.getStringExtra(EXTRA_SENDER),
            body = intent.getStringExtra(EXTRA_BODY),
            date = timestamp,
            processedTime = System.currentTimeMillis(),
            company = intent.getStringExtra(EXTRA_COMPANY),
            smsCode = smsCode,
            packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME),
            notifyChannelId = intent.getStringExtra(EXTRA_NOTIFY_CHANNEL_ID).orEmpty(),
            msgType = resolveRecordMessageType(msgType),
        )
    }

    private fun createNotificationCodePlan(
        context: Context,
        msgType: Int,
    ): SmsCodePostParseCoordinator.ParsedSmsPlan {
        val settings = SmsCodePostParseCoordinator.loadSettings(SmsCodeVerificationPrefs(context)).copy(
            recordSmsEnabled = recordEnabledForMessageType(context, msgType),
            blockSmsEnabled = false,
            markAsReadEnabled = false,
            deleteSmsEnabled = false,
        )
        return SmsCodePostParseCoordinator.createParsedSmsPlan(settings).copy(
            blockSms = false,
            operateSmsDelays = emptyList(),
        )
    }

    private fun dispatchLocalNotificationCodeActions(
        appContext: Context,
        smsMsg: SmsMsg,
        eventId: String,
        plan: SmsCodePostParseCoordinator.ParsedSmsPlan,
    ): Boolean {
        val executor = Executors.newSingleThreadScheduledExecutor()
        return try {
            SmsCodeActionDispatcher.dispatchParsedSmsActions(
                uiHandler = Handler(Looper.getMainLooper()),
                executor = executor,
                pluginContext = appContext,
                phoneContext = appContext,
                smsMsg = smsMsg,
                eventId = eventId,
                plan = plan,
            )
            true
        } catch (error: RuntimeException) {
            XLog.e("NmsForwardReceiver local action dispatch failed", error)
            false
        } finally {
            executor.shutdown()
        }
    }

    private fun readSmsCode(intent: Intent): String {
        return intent.getStringExtra(EXTRA_SMS_CODE_LEGACY)
            ?: intent.getStringExtra(CodeNotificationPayload.EXTRA_SMS_CODE)
            ?: ""
    }

    private fun resolveRecordMessageType(msgType: String): Int {
        return when (msgType) {
            MSG_TYPE_APP_NOTIFY -> SmsMsg.MSG_TYPE_APP_NOTIFY
            MSG_TYPE_CALL_NOTIFY -> SmsMsg.MSG_TYPE_CALL_NOTIFY
            else -> SmsMsg.MSG_TYPE_SMS
        }
    }

    private fun recordEnabledForMessageType(context: Context, msgType: Int): Boolean {
        return when (msgType) {
            SmsMsg.MSG_TYPE_APP_NOTIFY -> PrefsReader.recordAppNotifyEnabled(context)
            SmsMsg.MSG_TYPE_CALL_NOTIFY -> PrefsReader.recordCallNotifyEnabled(context)
            else -> PrefsReader.recordCodeSmsEnabled(context)
        }
    }

    private companion object {
        private const val EXTRA_EVENT_ID = "event_id"
        private const val EXTRA_SENDER = "sender"
        private const val EXTRA_BODY = "body"
        private const val EXTRA_DATE = "date"
        private const val EXTRA_COMPANY = "company"
        private const val EXTRA_PACKAGE_NAME = "packageName"
        private const val EXTRA_NOTIFY_CHANNEL_ID = "notify_channel_id"
        private const val EXTRA_MSG_TYPE = "msgType"
        private const val EXTRA_SMS_CODE_LEGACY = "smsCode"
        private const val MSG_TYPE_APP_NOTIFY = "app_notify"
        private const val MSG_TYPE_CALL_NOTIFY = "call_notify"
        private const val RESULT_DATA_MODULE_DISABLED = "module_disabled"
        private const val RESULT_DATA_ACTIONS_DISPATCHED = "actions_dispatched"
        private const val RESULT_DATA_ACTIONS_FAILED = "actions_failed"
    }
}
