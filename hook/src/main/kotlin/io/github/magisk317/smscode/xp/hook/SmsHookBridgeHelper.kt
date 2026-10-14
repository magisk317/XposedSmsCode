package io.github.magisk317.smscode.xp.hook

import android.content.Context
import io.github.magisk317.smscode.common.utils.HookPrefsReader
import io.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge
import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import io.github.magisk317.xposed.logging.XLog

internal object SmsHookBridgeHelper {
    fun resolvePluginContext(
        phoneContext: Context?,
        currentPluginContext: Context?,
        applicationId: String,
    ): Context? {
        if (currentPluginContext != null) return currentPluginContext
        return try {
            val pluginContext = phoneContext?.createPackageContext(
                applicationId,
                Context.CONTEXT_IGNORE_SECURITY,
            )
            pluginContext?.let({ ctx -> HookRuntimeBridge.hookProcessInit?.invoke(ctx) })
            HookPrefsReader.installSnapshot(phoneContext)
            pluginContext
        } catch (e: Exception) {
            XLog.e(message = "Create plugin context failed: %s", e)
            null
        }
    }

    fun recordSmsHookHeartbeat(
        pluginContext: Context,
        phoneContext: Context,
        packageName: String,
        source: String,
    ) {
        HookRuntimeBridge.contentProviderAccess.recordHookHeartbeat(
            context = pluginContext,
            packageName = packageName,
            processName = phoneContext.applicationInfo?.processName ?: packageName,
            source = source,
            verboseLogging = HookRuntimeBridge.prefsAccess.isVerboseLogMode(pluginContext),
            route = LogRoute.SMS_HOOK.id,
        )
    }
}
