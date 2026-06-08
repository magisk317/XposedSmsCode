package com.github.magisk317.smscode.xp.hook.code

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import com.github.magisk317.smscode.common.utils.SmsCodeUtils
import com.github.magisk317.smscode.runtime.RuntimePrefsFacade as PrefsReader
import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import io.github.magisk317.smscode.runtime.common.utils.StringUtils
import io.github.magisk317.smscode.verification.ObservedInboxScanner
import io.github.magisk317.smscode.verification.SmsInboxSeenTracker
import io.github.magisk317.smscode.verification.SmsRoleStateResolver
import io.github.magisk317.smscode.xposed.utils.XLog
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

internal class SmsInboxObserver(
    private val pluginContext: Context,
    private val phoneContext: Context,
) {
    private val smsRoleStateResolver = SmsRoleStateResolver()
    private val smsInboxScanner = createInboxScanner()
    private val observedSmsHandler = ObservedSmsHandler(
        pluginContext = pluginContext,
        phoneContext = phoneContext,
        actionExecutor = queryExecutor,
        roleStateLogger = ::logSmsRoleState,
    )
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            onChange(selfChange, null)
        }

        override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
            queryExecutor.execute { scanRecentInbox(uri?.toString().orEmpty()) }
        }
    }

    fun register() {
        runCatching {
            phoneContext.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
            XLog.i("SmsInboxObserver registered")
            queryExecutor.execute { repairRecentRouting() }
        }.onFailure {
            XLog.w("SmsInboxObserver register failed: %s", it.message ?: it.javaClass.simpleName)
        }
    }

    private fun repairRecentRouting() {
        var scanned = 0
        var updated = 0
        createInboxScanner().scanRouting(
            triggerUri = ROUTING_REPAIR_TRIGGER_URI,
            recentSmsWindowMs = ROUTING_REPAIR_WINDOW_MS,
        ).forEach { record ->
            scanned += 1
            if (observedSmsHandler.repairRouting(record)) {
                updated += 1
            }
        }
        XLog.log(
            Log.INFO,
            LogRoute.SMS_HOOK,
            false,
            false,
            "Diag SMS routing repair finished: scanned=%d updated=%d",
            scanned,
            updated,
        )
    }

    private fun scanRecentInbox(triggerUri: String) {
        smsInboxScanner.scan(
            triggerUri = triggerUri,
            recentSmsWindowMs = RECENT_SMS_WINDOW_MS,
        ).forEach { record ->
            val sensitiveDebugLog = PrefsReader.isSensitiveDebugLogMode(pluginContext)
            XLog.i(
                "Diag SMS provider observed: sms_id=%d trigger_uri=%s sender_hash=%s date=%d read=%s simSlot=%d subId=%d code=%s body=%s",
                record.smsId,
                record.triggerUri,
                senderHash(record.sender),
                record.date,
                record.read,
                record.simSlot,
                record.subId,
                if (sensitiveDebugLog) StringUtils.escape(record.code) else StringUtils.summarizeCode(record.code),
                if (sensitiveDebugLog) StringUtils.escape(record.body) else StringUtils.summarizeBody(record.body),
            )
            logSmsRoleStateForSms(record.smsId, record.triggerUri)
            observedSmsHandler.handle(record)
        }
    }

    private fun logSmsRoleState(eventId: String) {
        val roleState = smsRoleStateResolver.resolve(phoneContext)
        XLog.d(
            "Diag observer sms role: event_id=%s defaultSms=%s roleHolders=%s",
            eventId,
            roleState.defaultSms ?: "<none>",
            if (roleState.roleHolders.isEmpty()) "<none>" else roleState.roleHolders.joinToString(","),
        )
    }

    private fun logSmsRoleStateForSms(smsId: Long, triggerUri: String) {
        val roleState = smsRoleStateResolver.resolve(phoneContext)
        XLog.d(
            "Diag observer sms role: sms_id=%d trigger_uri=%s defaultSms=%s roleHolders=%s",
            smsId,
            triggerUri.ifBlank { Telephony.Sms.CONTENT_URI.toString() },
            roleState.defaultSms ?: "<none>",
            if (roleState.roleHolders.isEmpty()) "<none>" else roleState.roleHolders.joinToString(","),
        )
    }

    private fun senderHash(sender: String): String {
        if (sender.isBlank()) return "none"
        return Integer.toHexString(sender.hashCode())
    }

    private fun createInboxScanner(): ObservedInboxScanner {
        return ObservedInboxScanner(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsIdTracker = SmsInboxSeenTracker(MAX_TRACKED_SMS_IDS),
            smsCodeParser = SmsCodeUtils::parseSmsCodeIfExists,
        )
    }

    companion object {
        private const val RECENT_SMS_WINDOW_MS = 10 * 60 * 1000L
        private const val ROUTING_REPAIR_WINDOW_MS = 24 * 60 * 60 * 1000L
        private const val ROUTING_REPAIR_TRIGGER_URI = "content://sms"
        private const val MAX_TRACKED_SMS_IDS = 128
        private val queryExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    }
}
