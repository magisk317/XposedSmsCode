package com.github.magisk317.smscode.xp.hook.telephony

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Binder
import com.github.magisk317.smscode.hook.BuildConfig
import com.github.magisk317.smscode.common.utils.ActivationDiagnosticsStore
import com.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge
import io.github.magisk317.smscode.xposed.utils.XLog
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.HookEnv
import io.github.magisk317.xposed.HookHelpers
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.xposed.MethodHook
import io.github.magisk317.xposed.MethodHookParam
import io.github.magisk317.smscode.runtime.contract.logging.LogRoute

/**
 * Log SMS provider writes to identify who inserts/updates SMS rows.
 */
class SmsProviderHook : BaseHook() {

    override fun hookOnLoadPackage(): Boolean = true

    override fun onLoadPackage(param: LoadParam) {
        XLog.withRoute(LogRoute.SMS_HOOK) {
            onLoadPackageRouted(param)
        }
    }

    private fun onLoadPackageRouted(param: LoadParam) {
        if (param.packageName != TELEPHONY_PROVIDER_PACKAGE) return
        hookProviderMethods(param.classLoader)
    }

    private fun hookProviderMethods(classLoader: ClassLoader) {
        val providerClass = runCatching { HookHelpers.findClass(TELEPHONY_PROVIDER_CLASS, classLoader) }.getOrNull() ?: run {
            XLog.w("SmsProviderHook: class not found: %s", TELEPHONY_PROVIDER_CLASS)
            return
        }

        hookMethod(providerClass, "insert")
        hookMethod(providerClass, "bulkInsert")
        hookMethod(providerClass, "update")
    }

    private fun hookMethod(clazz: Class<*>, methodName: String) {
        val methods = clazz.declaredMethods.filter { it.name == methodName }
        if (methods.isEmpty()) return
        methods.forEach { method ->
            HookEnv.api.hookMethod(
                method,
                object : MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        XLog.withRoute(LogRoute.SMS_HOOK) {
                            val uri = param.args.getOrNull(0) as? Uri ?: return@withRoute
                            if (!isSmsUri(uri)) return@withRoute
                            val provider = param.thisObject as? ContentProvider
                            val context = provider?.context
                            val pluginContext = runCatching {
                                context?.createPackageContext(
                                    BuildConfig.APPLICATION_ID,
                                    Context.CONTEXT_IGNORE_SECURITY,
                                )
                            }.getOrNull()
                            pluginContext?.let({ ctx -> HookRuntimeBridge.hookProcessInit?.invoke(ctx) })
                            if (pluginContext != null && context != null) {
                                ActivationDiagnosticsStore.recordHookHeartbeat(
                                    context = pluginContext,
                                    packageName = TELEPHONY_PROVIDER_PACKAGE,
                                    processName = context.applicationInfo?.processName ?: TELEPHONY_PROVIDER_PACKAGE,
                                    source = "sms_provider_$methodName",
                                    verboseLogging = HookRuntimeBridge.prefsAccess.isVerboseLogMode(pluginContext),
                                )
                            }
                            val verboseDiag = pluginContext != null && HookRuntimeBridge.prefsAccess.isVerboseLogMode(pluginContext)
                            if (!verboseDiag) return@withRoute
                            val callingUid = Binder.getCallingUid()
                            val callingPid = Binder.getCallingPid()
                            val packages = runCatching {
                                context?.packageManager?.getPackagesForUid(callingUid)?.toList()
                            }.getOrNull().orEmpty()
                            val valuesSummary = when (methodName) {
                                "insert", "update" -> {
                                    val values = param.args.getOrNull(1) as? ContentValues
                                    values?.keySet()?.joinToString(",") ?: "<none>"
                                }
                                "bulkInsert" -> {
                                    val values = param.args.getOrNull(1) as? Array<*>
                                    "count=${values?.size ?: 0}"
                                }
                                else -> "<none>"
                            }
                            XLog.w(
                                "Diag sms provider %s: class=%s process=%s uri=%s uid=%d pid=%d pkgs=%s values=%s",
                                methodName,
                                provider?.javaClass?.name ?: TELEPHONY_PROVIDER_CLASS,
                                context?.applicationInfo?.processName ?: TELEPHONY_PROVIDER_PACKAGE,
                                uri,
                                callingUid,
                                callingPid,
                                if (packages.isEmpty()) "<none>" else packages.joinToString(","),
                                valuesSummary,
                            )
                        }
                    }
                },
            )
        }
    }

    private fun isSmsUri(uri: Uri): Boolean {
        val authority = uri.authority.orEmpty()
        if (authority == "sms" || authority == "mms-sms" || authority == "com.android.providers.telephony") {
            return true
        }
        val path = uri.toString()
        return path.contains("content://sms") || path.contains("content://mms-sms")
    }

    companion object {
        private const val TELEPHONY_PROVIDER_PACKAGE = "com.android.providers.telephony"
        private const val TELEPHONY_PROVIDER_CLASS = "com.android.providers.telephony.TelephonyProvider"
    }
}
