package io.github.magisk317.smscode.xp.hook.mms

import android.content.Context
import android.content.Intent
import io.github.magisk317.smscode.hook.BuildConfig
import io.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge
import io.github.magisk317.smscode.xp.helper.ModuleConflictArbiter
import io.github.magisk317.smscode.xp.helper.RelayConflictNoticeHelper
import io.github.magisk317.smscode.xp.hook.code.CodeWorker
import io.github.magisk317.smscode.xp.hook.code.SmsBlockEvaluator
import io.github.magisk317.smscode.xposed.hook.telephony.BlockEvaluation
import io.github.magisk317.smscode.xposed.hook.telephony.MmsEntryPointHook
import io.github.magisk317.smscode.xposed.hook.telephony.MmsEntryPointHookHost

/**
 * XSC wiring for the shared MMS entry-point hook.
 *
 * XSC has no outbound forward pipeline, so it records no blacklist hits and does
 * not delete blacklisted SMS from the inbox. It only shows the conflict notice
 * when another module already owns the SMS.
 */
private object XscMmsEntryPointHost : MmsEntryPointHookHost {
    override val applicationId: String = BuildConfig.APPLICATION_ID

    override fun shouldSuppressByRelay(context: Context, tag: String): Boolean =
        ModuleConflictArbiter.shouldSuppressByRelay(context, tag)

    override fun isVerboseLogMode(pluginContext: Context): Boolean =
        HookRuntimeBridge.prefsAccess.isVerboseLogMode(pluginContext)

    override fun onPluginContextReady(
        pluginContext: Context,
        phoneContext: Context,
        source: String,
    ) {
        HookRuntimeBridge.hookProcessInit?.invoke(pluginContext)
    }

    override fun onConflictNotice(pluginContext: Context, hostContext: Context, eventId: String) {
        RelayConflictNoticeHelper.notifyConflictOnSms(pluginContext, hostContext, eventId)
    }

    override fun evaluateBlock(
        pluginContext: Context,
        intent: Intent,
        eventId: String,
        source: String,
    ): BlockEvaluation? {
        val result = SmsBlockEvaluator.evaluate(
            pluginContext = pluginContext,
            intent = intent,
            eventId = eventId,
            source = source,
        ) ?: return null
        return BlockEvaluation(
            smsMsg = result.smsMsg,
            blockReason = result.blockReason,
            blacklistDeleteOnly = result.blacklistDeleteOnly,
        )
    }

    override fun runParseWorker(
        pluginContext: Context,
        phoneContext: Context,
        intent: Intent,
        eventId: String,
    ) {
        CodeWorker(pluginContext, phoneContext, intent, eventId).parse()
    }
}

/**
 * XSC MMS entry point.
 *
 * Delegates to the shared [MmsEntryPointHook]; this wrapper only exists because the
 * hook list needs a no-arg [io.github.magisk317.xposed.BaseHook] instance.
 */
class MmsMessagesHook : io.github.magisk317.xposed.BaseHook() {
    private val delegate = MmsEntryPointHook(XscMmsEntryPointHost)

    override fun hookOnLoadPackage(): Boolean = delegate.hookOnLoadPackage()

    override fun onLoadPackage(param: io.github.magisk317.xposed.LoadParam) {
        delegate.onLoadPackage(param)
    }

    override fun onHotReloading() {
        delegate.onHotReloading()
    }
}
