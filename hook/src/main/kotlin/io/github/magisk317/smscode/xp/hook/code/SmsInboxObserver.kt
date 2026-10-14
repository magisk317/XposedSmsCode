package io.github.magisk317.smscode.xp.hook.code

import android.content.Context
import io.github.magisk317.smscode.xp.hook.helper.XscHookHostCapabilities
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.smscode.xposed.hook.telephony.SmsInboxObserver as SharedSmsInboxObserver
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

/**
 * XSC wiring for the shared inbox observer.
 *
 * Scanning, routing repair and telemetry live in core. XSC supplies its host
 * capabilities ([XscHookHostCapabilities] over the runtime bridge) and what to
 * do with a scanned record.
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
            XLog.w(message = "Diag observer sms role: event_id=%s", eventId)
        },
    )

    private val delegate = SharedSmsInboxObserver(
        pluginContext = pluginContext,
        phoneContext = phoneContext,
        recordHandler = { record -> observedSmsHandler.handle(record) },
        capabilities = XscHookHostCapabilities,
    )

    fun register() {
        delegate.setRoutingRepair { record -> observedSmsHandler.repairRouting(record) }
        delegate.register()
    }

    fun unregister() {
        delegate.unregister()
    }
}
