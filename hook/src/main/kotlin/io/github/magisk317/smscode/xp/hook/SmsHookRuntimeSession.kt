package io.github.magisk317.smscode.xp.hook

import android.content.Context
import io.github.magisk317.smscode.runtime.verification.VerificationRuntimeContext

internal data class SmsHookRuntimeContext(
    override val phoneContext: Context,
    override val pluginContext: Context,
) : VerificationRuntimeContext

internal class SmsHookRuntimeSession(
    private val applicationId: String,
    private val fallbackPackageName: String? = null,
    private val pluginContextResolver: (Context, String) -> Context? = { phoneContext, applicationId ->
        SmsHookBridgeHelper.resolvePluginContext(
            phoneContext = phoneContext,
            currentPluginContext = null,
            applicationId = applicationId,
        )
    },
    private val heartbeatRecorder: (Context, Context, String, String) -> Unit = { pluginContext, phoneContext, packageName, source ->
        SmsHookBridgeHelper.recordSmsHookHeartbeat(
            pluginContext = pluginContext,
            phoneContext = phoneContext,
            packageName = packageName,
            source = source,
        )
    },
) {
    @Volatile
    private var phoneContext: Context? = null

    @Volatile
    private var pluginContext: Context? = null

    fun initialize(context: Context): SmsHookRuntimeContext? = synchronized(this) {
        if (phoneContext == null) {
            phoneContext = context
        }
        currentOrResolveLocked()
    }

    fun currentOrResolve(): SmsHookRuntimeContext? = synchronized(this) {
        currentOrResolveLocked()
    }

    fun recordHeartbeat(source: String): SmsHookRuntimeContext? {
        val runtime = currentOrResolve() ?: return null
        val packageName = runtime.phoneContext.packageName.ifBlank { fallbackPackageName.orEmpty() }
        heartbeatRecorder(runtime.pluginContext, runtime.phoneContext, packageName, source)
        return runtime
    }

    private fun currentOrResolveLocked(): SmsHookRuntimeContext? {
        val resolvedPhoneContext = phoneContext ?: return null
        val resolvedPluginContext = pluginContext
            ?: pluginContextResolver(resolvedPhoneContext, applicationId)?.also { pluginContext = it }
            ?: return null
        return SmsHookRuntimeContext(
            phoneContext = resolvedPhoneContext,
            pluginContext = resolvedPluginContext,
        )
    }
}
