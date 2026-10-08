package io.github.magisk317.smscode.xp.hook.code

import android.content.Context
import android.content.Intent
import io.github.magisk317.smscode.common.utils.HookPrefsReader
import io.github.magisk317.smscode.db.entity.SmsMsg
import io.github.magisk317.smscode.xp.helper.ModuleConflictArbiter
import io.github.magisk317.smscode.xp.helper.RelayConflictNoticeHelper
import io.github.magisk317.smscode.verification.DispatchGateDecision
import io.github.magisk317.smscode.runtime.verification.SmsDispatchIntentProcessor as SharedSmsDispatchIntentProcessor
import io.github.magisk317.smscode.runtime.verification.SmsDispatchIntentHandler as SharedSmsDispatchIntentHandler
import io.github.magisk317.smscode.runtime.verification.defaultGateDecision

/**
 * Host adapter over the shared [SharedSmsDispatchIntentHandler]; stop reasons and
 * outcomes reuse the shared types directly.
 */
internal class SmsDispatchIntentHandler(
    private val runtimeResolver: (String) -> SmsHookRuntimeContext?,
    private val moduleEnabledReader: (Context) -> Boolean = HookPrefsReader::isEnabled,
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
    ): SharedSmsDispatchIntentHandler.Outcome {
        return delegate.handle(
            intent = intent,
            eventId = eventId,
            inboundSmsHandler = inboundSmsHandler,
            receiver = receiver,
        )
    }
}
