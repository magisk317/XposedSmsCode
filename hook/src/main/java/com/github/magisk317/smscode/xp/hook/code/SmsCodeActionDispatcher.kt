package com.github.magisk317.smscode.xp.hook.code

import android.content.Context
import android.os.Handler
import com.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.xp.hook.code.action.impl.AutoInputAction
import com.github.magisk317.smscode.xp.hook.code.action.impl.CopyToClipboardAction
import com.github.magisk317.smscode.xp.hook.code.action.impl.NotifyAction
import com.github.magisk317.smscode.xp.hook.code.action.impl.OperateSmsAction
import com.github.magisk317.smscode.xp.hook.code.action.impl.RecordSmsAction
import com.github.magisk317.smscode.xp.hook.code.action.impl.ToastAction
import io.github.magisk317.smscode.runtime.verification.AutoInputDispatchGuard
import io.github.magisk317.smscode.runtime.verification.NotificationDispatchGuard
import io.github.magisk317.smscode.runtime.verification.SmsCodeActionScheduler
import io.github.magisk317.smscode.runtime.verification.SmsCodeActionDispatcher as SharedSmsCodeActionDispatcher
import io.github.magisk317.smscode.runtime.verification.SmsCodePostParseCoordinator
import io.github.magisk317.smscode.xposed.utils.XLog
import java.util.concurrent.Callable
import java.util.concurrent.ScheduledExecutorService

object SmsCodeActionDispatcher {
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
                dispatchUiActions(handler, plugin, phone, message.raw, uiPlan)
            },
            autoInputScheduler = { scheduledExecutor, plugin, phone, message, delayMs, deduplicateEnabled, _ ->
                scheduleAutoInput(scheduledExecutor, plugin, phone, message.raw, delayMs, deduplicateEnabled, attemptId)
            },
            notificationScheduler = { scheduledExecutor, plugin, phone, message, notificationPlan ->
                scheduleNotification(
                    scheduledExecutor,
                    plugin,
                    phone,
                    message.raw,
                    notificationPlan,
                    plan.deduplicateSmsEnabled,
                )
            },
            recordScheduler = { scheduledExecutor, plugin, phone, message, recordEventId, deduplicateEnabled ->
                scheduleRecord(scheduledExecutor, plugin, phone, message.raw, recordEventId, deduplicateEnabled)
            },
            operateSmsScheduler = { scheduledExecutor, plugin, phone, message, delays ->
                scheduleOperateSmsActions(scheduledExecutor, plugin, phone, message.raw, delays)
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
        autoInputRunner: (Context, Context, SmsMsg, Boolean, Long?) -> Unit = ::runAutoInputNow,
        autoInputScheduler: (ScheduledExecutorService, Context, Context, SmsMsg, Long, Boolean, Long?) -> Unit = ::scheduleAutoInput,
        recordRunner: (Context, Context, SmsMsg, String, Boolean) -> Unit = ::runRecordNow,
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

    private fun dispatchUiActions(
        uiHandler: Handler,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        uiPlan: SmsCodePostParseCoordinator.UiPlan,
    ) {
        if (!mobileAutomationAllowed(pluginContext)) {
            XLog.i("Mobile entitlement gate skipped UI actions")
            return
        }
        uiHandler.post(
            CopyToClipboardAction(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsMsg = smsMsg,
                enabled = uiPlan.copyToClipboardEnabled,
            ),
        )
        uiHandler.post(
            ToastAction(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsMsg = smsMsg,
                enabled = uiPlan.showToast,
            ),
        )
    }

    private fun runAutoInputNow(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        deduplicateEnabled: Boolean,
        attemptId: Long? = null,
    ) {
        SmsCodeActionScheduler.runAutoInputNowIfClaimed(
            pluginContext = pluginContext,
            smsMsg = smsMsg,
            claimDelayMs = 0L,
            claimAutoInputDispatch = ::claimAutoInputDispatch,
        ) {
            AutoInputAction(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsMsg = smsMsg,
                deduplicateEnabled = deduplicateEnabled,
                dispatchDelayMs = 0L,
                attemptId = attemptId,
            )
        }
    }

    private fun scheduleAutoInput(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        delayMs: Long,
        deduplicateEnabled: Boolean,
        attemptId: Long? = null,
    ) {
        SmsCodeActionScheduler.scheduleAutoInputIfClaimed(
            executor = executor,
            pluginContext = pluginContext,
            smsMsg = smsMsg,
            delayMs = delayMs,
            claimAutoInputDispatch = ::claimAutoInputDispatch,
        ) {
            AutoInputAction(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsMsg = smsMsg,
                deduplicateEnabled = deduplicateEnabled,
                dispatchDelayMs = delayMs,
                attemptId = attemptId,
            )
        }
    }

    private fun runRecordNow(
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        eventId: String,
        deduplicateEnabled: Boolean,
    ) {
        RecordSmsAction(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            smsMsg = smsMsg,
            eventId = eventId,
            enabled = true,
            deduplicateEnabled = deduplicateEnabled,
        ).call()
    }

    private fun scheduleRecord(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        eventId: String,
        deduplicateEnabled: Boolean,
    ) {
        SmsCodeActionScheduler.scheduleNow(executor) {
            RecordSmsAction(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsMsg = smsMsg,
                eventId = eventId,
                enabled = true,
                deduplicateEnabled = deduplicateEnabled,
            )
        }
    }

    private fun scheduleNotification(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        plan: SmsCodePostParseCoordinator.NotificationPlan,
        deduplicateEnabled: Boolean,
    ) {
        if (!mobileAutomationAllowed(pluginContext)) {
            XLog.i("Mobile entitlement gate skipped code notification")
            return
        }
        if (deduplicateEnabled && !claimNotificationDispatch(pluginContext, smsMsg)) return
        XLog.i("scheduleNotification() running inline: smsCode=%s", smsMsg.smsCode)
        runCatching {
            NotifyAction(
                pluginContext = pluginContext,
                phoneContext = phoneContext,
                smsMsg = smsMsg,
                enabled = true,
                autoCancelEnabled = plan.autoCancelDelayMs != null,
                retentionTimeMs = plan.autoCancelDelayMs ?: 0L,
            ).call()
        }.onFailure { error ->
            XLog.e("scheduleNotification() failed: %s", error)
        }
    }

    private fun scheduleOperateSmsActions(
        executor: ScheduledExecutorService,
        pluginContext: Context,
        phoneContext: Context,
        smsMsg: SmsMsg,
        delays: List<Long>,
    ) {
        SmsCodeActionScheduler.scheduleEachDelay(
            executor = executor,
            delays = delays,
        ) {
            Callable {
                if (mobileAutomationAllowed(pluginContext)) {
                    OperateSmsAction(pluginContext, phoneContext, smsMsg).call()
                } else {
                    XLog.i("Mobile entitlement gate skipped SMS operations")
                }
            }
        }
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

    private fun com.github.magisk317.smscode.runtime.bridge.HookRuntimeGateClaimResult.toAutoInputClaim(): AutoInputDispatchGuard.ClaimResult {
        return AutoInputDispatchGuard.ClaimResult(
            claimed = claimed,
            ageMs = ageMs,
            key = blockedKey,
        )
    }
}
