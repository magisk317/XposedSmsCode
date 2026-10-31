package io.github.magisk317.smscode.xp.hook.telephony

import io.github.magisk317.smscode.hook.BuildConfig
import io.github.magisk317.smscode.xp.hook.helper.XscHookHostCapabilities
import io.github.magisk317.smscode.xposed.hook.telephony.SmsHookCapabilities
import io.github.magisk317.smscode.xposed.hook.telephony.BaseSmsProviderHook
import io.github.magisk317.smscode.xposed.hook.telephony.SmsProviderHookHost

/**
 * Host wiring for the shared telephony provider hook.
 *
 * Installation, package filtering and write diagnostics live in
 * `BaseSmsProviderHook`; prefs/heartbeat/process-init defaults are backed by the
 * unified [HookHostCapabilities] adapter, so this file carries no host-specific
 * members.
 */
private object XscSmsProviderHookHost : SmsProviderHookHost {
    override val applicationId: String = BuildConfig.APPLICATION_ID

    override val capabilities: SmsHookCapabilities = XscHookHostCapabilities
}

class SmsProviderHook : BaseSmsProviderHook(XscSmsProviderHookHost)
