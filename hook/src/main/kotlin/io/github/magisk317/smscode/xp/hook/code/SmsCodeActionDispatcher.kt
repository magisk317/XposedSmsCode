package io.github.magisk317.smscode.xp.hook.code

import android.content.Context
import android.os.Handler
import io.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge
import io.github.magisk317.smscode.db.entity.SmsMsg
import io.github.magisk317.smscode.xp.hook.code.action.impl.AutoInputAction
import io.github.magisk317.smscode.xp.hook.code.action.impl.CopyToClipboardAction
import io.github.magisk317.smscode.xp.hook.code.action.impl.NotifyAction
import io.github.magisk317.smscode.xp.hook.code.action.impl.OperateSmsAction
import io.github.magisk317.smscode.xp.hook.code.action.impl.RecordSmsAction
import io.github.magisk317.smscode.xp.hook.code.action.impl.ToastAction
import io.github.magisk317.smscode.runtime.verification.AutoInputDispatchGuard
import io.github.magisk317.smscode.runtime.verification.HostSmsCodeActionWiring
import io.github.magisk317.smscode.runtime.verification.NotificationDispatchGuard
import io.github.magisk317.smscode.runtime.verification.SmsCodePostParseCoordinator
import io.github.magisk317.smscode.runtime.verification.SmsCodeActionDispatcher as SharedSmsCodeActionDispatcher
import io.github.magisk317.smscode.xposed.utils.XLog
import java.util.concurrent.ScheduledExecutorService

object SmsCodeActionDispatcher {

    private val wiring: HostSmsCodeActionWiring<VerificationSmsMsg> = HostSmsCodeActionWiring<VerificationSmsMsg>(
        mobileAutomationAllowed = ::mobileAutomationAllowed,
        claimAutoInputDispatch = { context, msg, delayMs -> claimAutoInputDispatch(context, msg.raw, delayMs) },
        claimNotificationDispatch = { context, msg -> claimNotificationDispatch(context, msg.raw) },
        newCopyAction = { plugin, phone, msg, enabled ->
            CopyToClipboardAction(
                pluginContext = plugin,
                phoneContext = phone,
                smsMsg = msg.raw,
                enabled = enabled,
            )
        },
        newToastAction = { plugin, phone, msg, enabled ->
            ToastAction(
                pluginContext = plugin,
                phoneContext = phone,
                smsMsg = msg.raw,
                enabled = enabled,
            )
        },
        newAutoInputAction = { plugin, phone, msg, deduplicateEnabled, dispatchDelayMs, attemptId ->
            AutoInputAction(
                pluginContext = plugin,
                phoneContext = phone,
                smsMsg = msg.raw,
                deduplicateEnabled = deduplicateEnabled,
                dispatchDelayMs = dispatchDelayMs,
                attemptId = attemptId,
            )
        },
        newRecordAction = { plugin, phone, msg, eventId, deduplicateEnabled ->
            {
                RecordSmsAction(
                    pluginContext = plugin,
                    phoneContext = phone,
                    smsMsg = msg.raw,
                    eventId = eventId,
                    enabled = true,
                    deduplicateEnabled = deduplicateEnabled,
                ).call()
            }
        },
        newNotifyAction = { plugin, phone, msg, autoCancelEnabled, retentionTimeMs ->
            {
                NotifyAction(
                    pluginContext = plugin,
                    phoneContext = phone,
                    smsMsg = msg.raw,
                    enabled = true,
                    autoCancelEnabled = autoCancelEnabled,
                    retentionTimeMs = retentionTimeMs,
                ).call()
            }
        },
        newOperateSmsAction = { plugin, phone, msg ->
            {
                OperateSmsAction(plugin, phone, msg.raw).call()
            }
        },
    )

    fun dispatchParsedSmsActions(
        uiHandler: Handler,
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        eventId: String,
        plan: SmsCodePostParseCoordinator.ParsedSmsPlan,
        attemptId: Long? = null,
    ) {
        SharedSmsCodeActionDispatcher.dispatchParsedSmsActions(
            uiHandler = uiHandler,
            executor = executor,
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg.toVerificationMessage(),
            eventId = eventId,
            plan = plan,
            uiDispatcher = { handler, plugin, phone, message, uiPlan ->
                wiring.dispatchUiActions(handler, plugin, phone, message, uiPlan)
            },
            autoInputScheduler = { scheduledExecutor, plugin, phone, message, delayMs, deduplicateEnabled, _ ->
                wiring.scheduleAutoInput(scheduledExecutor, plugin, phone, message, delayMs, deduplicateEnabled, attemptId)
            },
            notificationScheduler = { scheduledExecutor, plugin, phone, message, notificationPlan ->
                wiring.scheduleNotification(
                    scheduledExecutor,
                    plugin,
                    phone,
                    message,
                    notificationPlan,
                    plan.deduplicateSmsEnabled,
                )
            },
            recordScheduler = { scheduledExecutor, plugin, phone, message, recordEventId, deduplicateEnabled ->
                wiring.scheduleRecord(scheduledExecutor, plugin, phone, message, recordEventId, deduplicateEnabled)
            },
            operateSmsScheduler = { scheduledExecutor, plugin, phone, message, delays ->
                wiring.scheduleOperateSmsActions(scheduledExecutor, plugin, phone, message, delays)
            },
        )
    }

    fun dispatchObservedSmsActions(
        executor: ScheduledExecutorService?,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        eventId: String,
        plan: SmsCodePostParseCoordinator.ObservedSmsPlan,
        autoInputRunner: (Context, Context, SmsMsg, Boolean, Long?) -> Unit = { plugin, phone, msg, deduplicateEnabled, attemptId ->
            wiring.runAutoInputNow(plugin, phone, msg.toVerificationMessage(), deduplicateEnabled, attemptId)
        },
        autoInputScheduler: (ScheduledExecutorService, Context, Context, SmsMsg, Long, Boolean, Long?) -> Unit =
            { executor, plugin, phone, msg, delayMs, deduplicateEnabled, attemptId ->
                wiring.scheduleAutoInput(executor, plugin, phone, msg.toVerificationMessage(), delayMs, deduplicateEnabled, attemptId)
            },
        recordRunner: (Context, Context, SmsMsg, String, Boolean) -> Unit = { plugin, phone, msg, eventId, deduplicateEnabled ->
            wiring.runRecordNow(plugin, phone, msg.toVerificationMessage(), eventId, deduplicateEnabled)
        },
    ) {
        SharedSmsCodeActionDispatcher.dispatchObservedSmsActions(
            executor = executor,
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg.toVerificationMessage(),
            eventId = eventId,
            plan = plan,
            autoInputRunner = { plugin, phone, message, deduplicateEnabled, attemptId ->
                autoInputRunner(plugin, phone, message.raw, deduplicateEnabled, attemptId)
            },
            autoInputScheduler = { scheduledExecutor, plugin, phone, message, delayMs, deduplicateEnabled, attemptId ->
                autoInputScheduler(scheduledExecutor, plugin, phone, message.raw, delayMs, deduplicateEnabled, attemptId)
            },
            recordRunner = { plugin, phone, message, recordEventId, deduplicateEnabled ->
                recordRunner(plugin, phone, message.raw, recordEventId, deduplicateEnabled)
            },
        )
    }

    private fun claimAutoInputDispatch(
        pluginContext: Context,
        smsMsg: SmsMsg,
        delayMs: Long,
    ): Boolean {
        if (!mobileAutomationAllowed(pluginContext)) {
            XLog.i("Mobile entitlement gate skipped auto-input dispatch")
            return false
        }
        return AutoInputDispatchGuard.claim(
            pluginContext = pluginContext,
            smsMsg = smsMsg.toVerificationMessage(),
            delayMs = delayMs,
        ) { context, fileName, keys, windowMs, maxEntries ->
            HookRuntimeBridge.contentProviderAccess.claimRuntimeGate(
                context = context,
                fileName = fileName,
                keys = keys,
                windowMs = windowMs,
                maxEntries = maxEntries,
            ).toAutoInputClaim()
        }
    }

    private fun claimNotificationDispatch(
        pluginContext: Context,
        smsMsg: SmsMsg,
    ): Boolean {
        return NotificationDispatchGuard.claim(
            pluginContext = pluginContext,
            smsMsg = smsMsg.toVerificationMessage(),
        ) { context, fileName, keys, windowMs, maxEntries ->
            HookRuntimeBridge.contentProviderAccess.claimRuntimeGate(
                context = context,
                fileName = fileName,
                keys = keys,
                windowMs = windowMs,
                maxEntries = maxEntries,
            ).let { result ->
                NotificationDispatchGuard.ClaimResult(
                    claimed = result.claimed,
                    ageMs = result.ageMs,
                    key = result.blockedKey,
                )
            }
        }
    }

    private fun mobileAutomationAllowed(context: Context): Boolean = runCatching {
        HookRuntimeBridge.prefsAccess.mobileAutomationAllowed(context)
    }.getOrDefault(false)

    private fun io.github.magisk317.smscode.runtime.bridge.HookRuntimeGateClaimResult.toAutoInputClaim(): AutoInputDispatchGuard.ClaimResult {
        return AutoInputDispatchGuard.ClaimResult(
            claimed = claimed,
            ageMs = ageMs,
            key = blockedKey,
        )
    }
}
