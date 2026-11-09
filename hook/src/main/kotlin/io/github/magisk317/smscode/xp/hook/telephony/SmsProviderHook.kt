package io.github.magisk317.smscode.xp.hook.telephony

import android.content.Context
import io.github.magisk317.smscode.common.utils.SmsBlacklistUtils
import io.github.magisk317.smscode.hook.BuildConfig
import io.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge
import io.github.magisk317.smscode.xp.hook.helper.XscHookHostCapabilities
import io.github.magisk317.smscode.xp.helper.ModuleConflictArbiter
import io.github.magisk317.smscode.xposed.hook.telephony.BaseSmsProviderHook
import io.github.magisk317.smscode.xposed.hook.telephony.SmsHookCapabilities
import io.github.magisk317.smscode.xposed.hook.telephony.SmsProviderBlacklistEngine
import io.github.magisk317.smscode.xposed.hook.telephony.SmsProviderDraft
import io.github.magisk317.smscode.xposed.hook.telephony.SmsProviderHookHost
import io.github.magisk317.smscode.xposed.hook.telephony.SmsProviderHookInstaller
import io.github.magisk317.smscode.xposed.hook.telephony.SmsProviderWriteDecision

private object XscSmsProviderHookHost : SmsProviderHookHost {
    override val applicationId: String = BuildConfig.APPLICATION_ID

    override val capabilities: SmsHookCapabilities = XscHookHostCapabilities

    override fun onInboundSmsProviderWrite(
        pluginContext: Context,
        phoneContext: Context,
        drafts: List<SmsProviderDraft>,
        call: SmsProviderHookInstaller.WriteCall,
    ): SmsProviderWriteDecision = SmsProviderBlacklistEngine.evaluate(
        match = { sender, body -> SmsBlacklistUtils.match(pluginContext, sender, body) },
        enabled = runCatching { HookRuntimeBridge.prefsAccess.isEnabled(pluginContext) }.getOrDefault(false),
        entitlementAllowed = runCatching {
            HookRuntimeBridge.prefsAccess.mobileAutomationAllowed(pluginContext)
        }.getOrDefault(false),
        conflictSuppressed = runCatching {
            ModuleConflictArbiter.shouldSuppressByRelay(phoneContext, "XscSmsProviderHookHost")
        }.getOrDefault(false),
        drafts = drafts,
    ).write
}

class SmsProviderHook : BaseSmsProviderHook(XscSmsProviderHookHost)
