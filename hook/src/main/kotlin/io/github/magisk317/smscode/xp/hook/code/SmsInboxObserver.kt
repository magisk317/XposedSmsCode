package io.github.magisk317.smscode.xp.hook.code

import android.content.Context
import io.github.magisk317.smscode.common.utils.HookPrefsReader
import io.github.magisk317.smscode.common.utils.SmsCodeUtils
import io.github.magisk317.smscode.runtime.common.utils.StringUtils
import io.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge
import io.github.magisk317.smscode.xposed.hook.telephony.SmsInboxObserver as SharedSmsInboxObserver
import io.github.magisk317.smscode.xposed.utils.XLog
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

/**
 * XSC wiring for the shared inbox observer.
 *
 * Scanning, routing repair and telemetry live in core. XSC supplies how a code is
 * parsed, how sensitive text is redacted, and what to do with a scanned record.
 */
internal class SmsInboxObserver(
    private val pluginContext: Context,
    private val phoneContext: Context,
) {
    private val queryExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val observedSmsHandler = ObservedSmsHandler(
        pluginContext = pluginContext,
        phoneContext = phoneContext,
        actionExecutor = queryExecutor,
        roleStateLogger = { eventId ->
            XLog.w("Diag observer sms role: event_id=%s", eventId)
        },
    )

    private val delegate = SharedSmsInboxObserver(
        pluginContext = pluginContext,
        phoneContext = phoneContext,
        recordHandler = { record -> observedSmsHandler.handle(record) },
        isSensitiveDebugLog = { context ->
            runCatching { HookRuntimeBridge.prefsAccess.isSensitiveDebugLogMode(context) }.getOrDefault(false)
        },
        escapeCode = { value -> StringUtils.escape(value).orEmpty() },
        summarizeCode = StringUtils::summarizeCode,
        escapeBody = { value -> StringUtils.escape(value).orEmpty() },
        summarizeBody = StringUtils::summarizeBody,
        parseSmsCode = { context, content -> SmsCodeUtils.parseSmsCodeIfExists(context, content) },
    )

    fun register() {
        delegate.setRoutingRepair { record -> observedSmsHandler.repairRouting(record) }
        delegate.register()
    }

    fun unregister() {
        delegate.unregister()
    }
}
