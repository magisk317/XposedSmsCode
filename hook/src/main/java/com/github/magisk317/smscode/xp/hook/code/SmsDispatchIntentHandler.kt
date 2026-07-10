package com.github.magisk317.smscode.xp.hook.code

import android.content.Context
import android.content.Intent
import com.github.magisk317.smscode.runtime.RuntimePrefsFacade as PrefsReader
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.xp.helper.ModuleConflictArbiter
import com.github.magisk317.smscode.xp.helper.RelayConflictNoticeHelper
import io.github.magisk317.smscode.verification.DispatchGateDecision
import io.github.magisk317.smscode.verification.SmsDispatchIntentProcessor as SharedSmsDispatchIntentProcessor
import io.github.magisk317.smscode.verification.SmsDispatchIntentHandler as SharedSmsDispatchIntentHandler

internal class SmsDispatchIntentHandler(
    private val runtimeResolver: (String) -> SmsHookRuntimeContext?,
    private val moduleEnabledReader: (Context) -> Boolean = PrefsReader::isEnabled,
    private val conflictSuppressor: (Context, String) -> Boolean = { context, source ->
        ModuleConflictArbiter.shouldSuppressByRelay(context, source)
    },
    private val dispatchProcessor: (Context, Context, Intent, String) -> SmsDispatchIntentProcessor.Outcome =
        { pluginContext, phoneContext, intent, eventId ->
            SmsDispatchIntentProcessor(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
            ).handle(intent, eventId)
        },
    private val conflictNotifier: (Context, Context, String, String) -> Unit =
        { pluginContext, phoneContext, eventId, _ ->
            RelayConflictNoticeHelper.notifyConflictOnSms(pluginContext, phoneContext, eventId)
        },
    private val suppressionLogger: (String) -> Unit = {},
    private val blacklistDeleteScheduler: (Context, Context, SmsMsg) -> Unit = { _, _, _ -> },
    private val inboundBlocker: (Any, Any, String, String) -> Unit = { _, _, _, _ -> },
    private val gateEvaluator: (Boolean, Boolean) -> DispatchGateDecision = ::defaultGateDecision,
) {
    enum class StopReason {
        RUNTIME_UNAVAILABLE,
        MODULE_DISABLED,
        CONFLICT_SUPPRESSED,
        SMS_BLOCKED,
    }

    data class Outcome(
        val stopReason: StopReason? = null,
        val inboundBlocked: Boolean = false,
    ) {
        val shouldStopDispatch: Boolean = stopReason != null
    }

    private val delegate = SharedSmsDispatchIntentHandler<VerificationSmsMsg>(
        runtimeResolver = runtimeResolver,
        moduleEnabledReader = moduleEnabledReader,
        conflictSuppressor = conflictSuppressor,
        dispatchProcessor = { pluginContext, phoneContext, intent, eventId ->
            val outcome = dispatchProcessor(pluginContext, phoneContext, intent, eventId)
            SharedSmsDispatchIntentProcessor.Outcome<VerificationSmsMsg>(
                smsMsg = outcome.smsMsg?.toVerificationMessage(),
                blacklistResult = outcome.blacklistResult,
                parseResult = outcome.parseResult,
                decision = outcome.decision,
            )
        },
        conflictNotifier = conflictNotifier,
        suppressionLogger = suppressionLogger,
        blacklistDeleteScheduler = { pluginContext, phoneContext, smsMsg ->
            blacklistDeleteScheduler(pluginContext, phoneContext, smsMsg.raw)
        },
        inboundBlocker = inboundBlocker,
        gateEvaluator = gateEvaluator,
    )

    fun handle(
        intent: Intent,
        eventId: String,
        inboundSmsHandler: Any?,
        receiver: Any?,
    ): Outcome {
        val outcome = delegate.handle(
            intent = intent,
            eventId = eventId,
            inboundSmsHandler = inboundSmsHandler,
            receiver = receiver,
        )
        return Outcome(
            stopReason = outcome.stopReason?.toLocal(),
            inboundBlocked = outcome.inboundBlocked,
        )
    }
}

private fun defaultGateDecision(
    moduleEnabled: Boolean,
    suppressedByRelay: Boolean,
): DispatchGateDecision {
    if (!moduleEnabled) {
        return DispatchGateDecision.moduleDisabled()
    }
    if (suppressedByRelay) {
        return DispatchGateDecision.conflictSuppressed()
    }
    return DispatchGateDecision.allow()
}

private fun SharedSmsDispatchIntentHandler.StopReason.toLocal(): SmsDispatchIntentHandler.StopReason {
    return when (this) {
        SharedSmsDispatchIntentHandler.StopReason.RUNTIME_UNAVAILABLE -> SmsDispatchIntentHandler.StopReason.RUNTIME_UNAVAILABLE
        SharedSmsDispatchIntentHandler.StopReason.MODULE_DISABLED -> SmsDispatchIntentHandler.StopReason.MODULE_DISABLED
        SharedSmsDispatchIntentHandler.StopReason.CONFLICT_SUPPRESSED -> SmsDispatchIntentHandler.StopReason.CONFLICT_SUPPRESSED
        SharedSmsDispatchIntentHandler.StopReason.SMS_BLOCKED -> SmsDispatchIntentHandler.StopReason.SMS_BLOCKED
    }
}
