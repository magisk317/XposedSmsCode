package com.github.magisk317.smscode.xp

import com.github.magisk317.smscode.runtime.BuildConfig as RuntimeBuildConfig
import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.common.utils.HookPrefsReader
import com.github.magisk317.smscode.xp.hook.code.SmsHandlerHook
import com.github.magisk317.smscode.xp.hook.me.ModuleUtilsHook
import com.github.magisk317.smscode.xp.hook.mms.MmsMessagesHook
import com.github.magisk317.smscode.xp.hook.telephony.SmsProviderHook
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.magisk317.smscode.xposed.hook.permission.PermissionGranterHook
import io.github.magisk317.smscode.xposed.hook.system.SystemInputInjectorHook
import io.github.magisk317.xposed.HookEnv
import io.github.magisk317.xposed.LibXposedHookApi
import io.github.magisk317.smscode.xposed.utils.XLog
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.BaseLibXposedEntry
import io.github.magisk317.xposed.logging.MagiskOtel

class LibXposedEntry : BaseLibXposedEntry {

    @Suppress("unused", "UnusedParameter")
    constructor(xposed: XposedInterface, loadedParam: ModuleLoadedParam) : super(xposed, loadedParam)
    constructor() : super()

    override val logTag: String = TAG

    override val hooks: List<BaseHook> = listOf(
        SmsHandlerHook(),
        MmsMessagesHook(),
        ModuleUtilsHook(),
        PermissionGranterHook(),
        SystemInputInjectorHook(),
        SmsProviderHook(),
    )

    override fun installModuleRuntime(module: XposedModule, hookApi: LibXposedHookApi) {
        installCoreRuntime()
        HookEnv.init(hookApi)
        val remotePrefsProvider = { runCatching { getRemotePreferences(REMOTE_PREFS_GROUP) }.getOrNull() }
        HookPrefsReader.setRemotePrefsProvider(remotePrefsProvider)
        CorePrefsBridge.installRemote(remotePrefsProvider)
        // Diagnostic: test remote prefs reading
        runCatching {
            val prefs = remotePrefsProvider()
            val sensitiveLog = prefs?.getBoolean("pref_sensitive_debug_log_mode", false)
            val enabled = prefs?.getBoolean("pref_enable", false)
            XLog.w(
                "Hook remotePrefs: prefs=%s sensitiveLog=%s enabled=%s keys=%s",
                prefs != null,
                sensitiveLog,
                enabled,
                prefs?.all?.keys?.size,
            )
        }.onFailure {
            XLog.e("Hook remotePrefs failed", it)
        }
        try {
            XLog.setLogLevel(RuntimeBuildConfig.LOG_LEVEL)
        } catch (t: Throwable) {
            XLog.e("", t)
        }
        MagiskOtel.event(
            name = "hook.load",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "hook",
                "stage" to "module_runtime",
                "reason" to "installed",
                "source" to "xposedsmscode",
            ),
            statusOk = true,
        )
    }

    private fun installCoreRuntime() {
        XposedRuntimeInstaller.installEntryRuntime()
    }

    private companion object {
        private const val TAG = "smscode"
        private const val REMOTE_PREFS_GROUP = PrefConst.REMOTE_PREFS_GROUP
    }
}
