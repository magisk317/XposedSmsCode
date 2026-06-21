package com.github.magisk317.smscode.xp

import android.os.Bundle
import android.util.Log
import com.github.magisk317.smscode.runtime.BuildConfig as RuntimeBuildConfig
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.github.magisk317.smscode.common.utils.PrefsReader
import com.github.magisk317.smscode.xp.hook.code.SmsHandlerHook
import com.github.magisk317.smscode.xp.hook.mms.MmsMessagesHook
import com.github.magisk317.smscode.xp.hook.telephony.SmsProviderHook
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
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
import java.lang.ref.WeakReference
import java.util.ArrayList
import java.util.concurrent.ConcurrentHashMap

class LibXposedEntry : XposedModule {
    private companion object {
        private const val TAG = "XSmsCode"
        private const val MIN_LIBXPOSED_API_VERSION = 102
        private const val REMOTE_PREFS_GROUP = "xposed_prefs"
        private const val STATE_PROCESS_NAME = "processName"
        private const val STATE_LOADED_PACKAGES = "loadedPackages"
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
    private val loadedPackages = ConcurrentHashMap<String, ClassLoader>()

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        val api = apiVersion
        if (api < MIN_LIBXPOSED_API_VERSION) {
            Log.w(TAG, "skipped: apiVersion=$api < $MIN_LIBXPOSED_API_VERSION")
            moduleActive = false
            return
        }

        installModuleRuntime(param, LibXposedHookApi(this))
        moduleActive = true
        installInitZygoteHooks()
        XLog.i("$TAG: onModuleLoaded api=$apiVersion process=$processName framework=$frameworkName($frameworkVersionCode)")
    }

    private fun installModuleRuntime(param: ModuleLoadedParam, hookApi: LibXposedHookApi) {
        installCoreRuntime()
        HookEnv.init(hookApi)
        val remotePrefsProvider = { runCatching { getRemotePreferences(REMOTE_PREFS_GROUP) }.getOrNull() }
        PrefsReader.setRemotePrefsProvider(remotePrefsProvider)
        CorePrefsBridge.installRemote(remotePrefsProvider)
        processName = if (param.isSystemServer) "android" else param.processName
        try {
            XLog.setLogLevel(RuntimeBuildConfig.LOG_LEVEL)
        } catch (t: Throwable) {
            XLog.e("", t)
        }
    }

    private fun installInitZygoteHooks() {
        for (hook in hookList) {
            if (hook.hookInitZygote()) {
                hook.initZygote(ZygoteParam())
            }
        }
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        if (!moduleActive) return
        loadedPackages["android"] = param.classLoader
        val loadParam = LoadParam("android", processName, param.classLoader)
        dispatchLoad(loadParam)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!moduleActive) return
        loadedPackages[param.packageName] = param.classLoader
        val loadParam = LoadParam(param.packageName, processName, param.classLoader)
        dispatchLoad(loadParam)
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean {
        if (!moduleActive) return false
        return runCatching {
            param.setSavedInstanceState(createHotReloadState())
            cleanupForHotReload()
            true
        }.getOrElse { t ->
            Log.e(TAG, "LibXposedEntry hot reload rejected: ${t.message}", t)
            false
        }
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        val hookApi = LibXposedHookApi(this)
        installModuleRuntime(param, hookApi)
        moduleActive = true
        hookApi.beginHotReload(param.oldHookHandles)
        val removed = try {
            installInitZygoteHooks()
            restoreHotReloadState(param.savedInstanceState)
            resolveCurrentProcessTargets(param).forEach { (pkg, cl) ->
                loadedPackages.putIfAbsent(pkg, cl)
            }
            loadedPackages.forEach { (pkg, cl) ->
                dispatchLoad(LoadParam(pkg, processName, cl))
            }
            hookApi.finishHotReload()
        } catch (t: Throwable) {
            hookApi.finishHotReload()
            Log.e(TAG, "LibXposedEntry hot reload failed", t)
            throw t
        }
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

    private fun createHotReloadState(): Bundle {
        return Bundle().apply {
            putString(STATE_PROCESS_NAME, processName)
            putStringArrayList(STATE_LOADED_PACKAGES, ArrayList(loadedPackages.keys.sorted()))
        }
    }

    private fun cleanupForHotReload() {
        for (hook in hookList) {
            runCatching { hook.onHotReloading() }
                .onFailure { t ->
                    Log.e(TAG, "Hot reload cleanup failed: ${hook.javaClass.name}", t)
                }
        }
    }

    private fun restoreHotReloadState(savedState: Any?) {
        val state = savedState as? Bundle ?: return
        processName = state.getString(STATE_PROCESS_NAME) ?: processName
        val packages = state.getStringArrayList(STATE_LOADED_PACKAGES) ?: return
        loadedPackages.clear()
        packages.forEach { pkg ->
            val classLoader = resolveLoadedPackageClassLoader(pkg)
            if (classLoader == null) {
                Log.w(TAG, "Hot reload skipped package without classloader: $pkg")
            } else {
                loadedPackages[pkg] = classLoader
            }
        }
    }

    private fun resolveCurrentProcessTargets(param: ModuleLoadedParam): Map<String, ClassLoader> {
        val process = if (param.isSystemServer) "android" else param.processName
        return when (process) {
            "android", "system", "system_server" -> mapOf("android" to resolveSystemServerClassLoader())
            "com.android.phone", "com.xiaomi.phone" -> {
                val classLoader = resolveLoadedPackageClassLoader(process) ?: resolveContextClassLoader()
                mapOf(process to classLoader)
            }
            "com.android.mms", "com.android.mms:mms_service" -> {
                val classLoader = resolveLoadedPackageClassLoader("com.android.mms") ?: resolveContextClassLoader()
                mapOf("com.android.mms" to classLoader)
            }
            "com.android.providers.telephony" -> {
                val classLoader = resolveLoadedPackageClassLoader(process) ?: resolveContextClassLoader()
                mapOf(process to classLoader)
            }
            else -> emptyMap()
        }
    }

    private fun resolveLoadedPackageClassLoader(packageName: String): ClassLoader? {
        if (packageName == "android" || packageName == "system") {
            return resolveSystemServerClassLoader()
        }
        return runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val activityThread = activityThreadClass.getDeclaredMethod("currentActivityThread").invoke(null) ?: return null
            listOf("mPackages", "mResourcePackages").firstNotNullOfOrNull { fieldName ->
                val field = activityThreadClass.getDeclaredField(fieldName).apply { isAccessible = true }
                val packages = field.get(activityThread) as? Map<*, *> ?: return@firstNotNullOfOrNull null
                val loadedApkRef = packages[packageName] ?: return@firstNotNullOfOrNull null
                val loadedApk = (loadedApkRef as? WeakReference<*>)?.get() ?: loadedApkRef
                loadedApk.javaClass
                    .getDeclaredMethod("getClassLoader")
                    .apply { isAccessible = true }
                    .invoke(loadedApk) as? ClassLoader
            }
        }.getOrElse { t ->
            Log.w(TAG, "Hot reload classloader resolve failed for $packageName: ${t.message}", t)
            null
        }
    }

    private fun resolveSystemServerClassLoader(): ClassLoader {
        return resolveContextClassLoader()
    }

    private fun resolveContextClassLoader(): ClassLoader {
        return Thread.currentThread().contextClassLoader ?: ClassLoader.getSystemClassLoader()
    }
}
