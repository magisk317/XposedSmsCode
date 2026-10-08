package io.github.magisk317.smscode.xp.hook.telephony

import android.content.Context
import io.github.magisk317.smscode.hook.BuildConfig
import io.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge
import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import io.github.magisk317.smscode.xposed.hook.telephony.BaseSmsProviderHook
import io.github.magisk317.smscode.xposed.hook.telephony.SmsProviderHookHost
import io.github.magisk317.smscode.xposed.hook.telephony.SmsProviderHookInstaller

/**
 * Host wiring for the shared telephony provider hook.
 *
 * Installation, package filtering and write diagnostics live in
 * `BaseSmsProviderHook`; this only bridges XSC's runtime bridge and prefs.
 */
private object XscSmsProviderHookHost : SmsProviderHookHost {
    override val applicationId: String = BuildConfig.APPLICATION_ID

    override fun recordHeartbeat(
        pluginContext: Context,
        phoneContext: Context,
        processName: String,
        source: String,
    ) {
        HookRuntimeBridge.contentProviderAccess.recordHookHeartbeat(
            context = pluginContext,
            packageName = SmsProviderHookInstaller.TARGET_PACKAGE,
            processName = processName,
            source = source,
            verboseLogging = HookRuntimeBridge.prefsAccess.isVerboseLogMode(pluginContext),
            route = LogRoute.SMS_HOOK.id,
        )
    }

    override fun isVerboseLogMode(pluginContext: Context): Boolean =
        HookRuntimeBridge.prefsAccess.isVerboseLogMode(pluginContext)

    override fun onPluginContextReady(pluginContext: Context, phoneContext: Context) {
        HookRuntimeBridge.hookProcessInit?.invoke(pluginContext)
    }
}

class SmsProviderHook : BaseSmsProviderHook(XscSmsProviderHookHost)
