package com.github.magisk317.smscode.xp

import android.util.Log
import com.github.magisk317.smscode.runtime.BuildConfig as RuntimeBuildConfig
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.github.magisk317.smscode.common.utils.PrefsReader
import com.github.magisk317.smscode.xp.hook.code.SmsHandlerHook
import com.github.magisk317.smscode.xp.hook.mms.MmsMessagesHook
import com.github.magisk317.smscode.xp.hook.telephony.SmsProviderHook
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import io.github.magisk317.smscode.xposed.hook.BaseHook
import io.github.magisk317.smscode.xposed.hook.permission.PermissionGranterHook
import io.github.magisk317.smscode.xposed.hook.system.SystemInputInjectorHook
import io.github.magisk317.smscode.xposed.hookapi.HookEnv
import io.github.magisk317.smscode.xposed.hookapi.LibXposedHookApi
import io.github.magisk317.smscode.xposed.hookapi.LoadParam
import io.github.magisk317.smscode.xposed.hookapi.ZygoteParam
import io.github.magisk317.smscode.xposed.runtime.CoreRuntime
import io.github.magisk317.smscode.xposed.runtime.CoreRuntimeAccess
import io.github.magisk317.smscode.xposed.utils.XLog

class LibXposedEntry : XposedModule {
    private companion object {
        private const val TAG = "XSmsCode"
        private const val MIN_LIBXPOSED_API_VERSION = 102
        private const val REMOTE_PREFS_GROUP = "xposed_prefs"
    }

    @Suppress("unused", "UnusedParameter")
    constructor(xposed: XposedInterface, loadedParam: ModuleLoadedParam) : super()

    constructor() : super()

    private val hookList: List<BaseHook> = listOf(
        SmsHandlerHook(),
        MmsMessagesHook(),
        PermissionGranterHook(),
        SystemInputInjectorHook(),
        SmsProviderHook(),
    )

    private var processName: String = "unknown"
    private var moduleActive: Boolean = false
    private val loadedPackages = java.util.concurrent.ConcurrentHashMap<String, ClassLoader>()

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        val api = apiVersion
        if (api < MIN_LIBXPOSED_API_VERSION) {
            Log.w(TAG, "skipped: apiVersion=$api < $MIN_LIBXPOSED_API_VERSION")
            moduleActive = false
            return
        }

        installCoreRuntime()
        HookEnv.init(LibXposedHookApi(this))
        moduleActive = true
        val remotePrefsProvider = { runCatching { getRemotePreferences(REMOTE_PREFS_GROUP) }.getOrNull() }
        PrefsReader.setRemotePrefsProvider(remotePrefsProvider)
        CorePrefsBridge.installRemote(remotePrefsProvider)
        processName = if (param.isSystemServer) "android" else param.processName

        for (hook in hookList) {
            if (hook.hookInitZygote()) {
                hook.initZygote(ZygoteParam())
            }
        }

        try {
            XLog.setLogLevel(RuntimeBuildConfig.LOG_LEVEL)
        } catch (t: Throwable) {
            XLog.e("", t)
        }

        XLog.i("$TAG: onModuleLoaded api=$apiVersion process=$processName framework=$frameworkName($frameworkVersionCode)")
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        if (!moduleActive) return
        val loadParam = LoadParam("android", processName, param.classLoader)
        dispatchLoad(loadParam)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!moduleActive) return
        loadedPackages[param.packageName] = param.classLoader
        val loadParam = LoadParam(param.packageName, processName, param.classLoader)
        dispatchLoad(loadParam)
    }

    override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean {
        if (!moduleActive) return false
        param.setSavedInstanceState(Pair(processName, HashMap(loadedPackages)))
        return true
    }

    @Suppress("UNCHECKED_CAST")
    override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {
        installCoreRuntime()
        val hookApi = HookEnv.api as? LibXposedHookApi ?: return
        hookApi.beginHotReload(param.oldHookHandles)
        
        val state = param.savedInstanceState as? Pair<String, Map<String, ClassLoader>>
        if (state != null) {
            processName = state.first
            state.second.forEach { (pkg, cl) ->
                dispatchLoad(LoadParam(pkg, processName, cl))
            }
        }
        
        val removed = hookApi.finishHotReload()
        Log.i(TAG, "onHotReloaded: replaced hooks, removed $removed stale hooks")
    }

    private fun dispatchLoad(loadParam: LoadParam) {
        installCoreRuntime()
        XLog.d("LibXposedEntry: Loaded package: ${loadParam.packageName} process: ${loadParam.processName}")
        if ("android" == loadParam.packageName || "system" == loadParam.packageName) {
            XLog.w(
                "LibXposedEntry: Android/system package loaded: pkg=%s process=%s",
                loadParam.packageName,
                loadParam.processName,
            )
        }
        for (hook in hookList) {
            if (!hook.hookOnLoadPackage()) continue
            runCatching {
                hook.onLoadPackage(loadParam)
            }.onFailure { throwable ->
                XLog.e(
                    "LibXposedEntry: %s failed for pkg=%s process=%s",
                    hook.javaClass.simpleName,
                    loadParam.packageName,
                    loadParam.processName,
                    throwable,
                )
            }
        }
    }

    private fun installCoreRuntime() {
        CoreRuntime.install(object : CoreRuntimeAccess {
            override val logTag: String = BuildConfig.LOG_TAG
            override val logLevel: Int = RuntimeBuildConfig.LOG_LEVEL
            override val logToXposed: Boolean = RuntimeBuildConfig.LOG_TO_XPOSED
            override val debug: Boolean = BuildConfig.DEBUG
            override val applicationId: String = BuildConfig.APPLICATION_ID
            override val actionNamespace: String = "com.github.magisk317.smscode"
        })
    }
}
