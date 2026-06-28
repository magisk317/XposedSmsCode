package com.github.magisk317.smscode.ui.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import com.github.magisk317.smscode.runtime.BuildConfig as RuntimeBuildConfig
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.common.utils.ActivationDiagnosticsStore
import com.github.magisk317.smscode.common.utils.AppPreferencesDataStore
import com.github.magisk317.smscode.common.utils.HookPreferenceMirror
import com.github.magisk317.smscode.runtime.RuntimePrefsFacade as PrefsReader
import io.github.magisk317.smscode.xposed.utils.ModuleActivationStore
import io.github.magisk317.smscode.xposed.utils.ModuleUtils
import com.github.magisk317.smscode.common.utils.RuntimeLogStore
import com.github.magisk317.smscode.xp.helper.ModuleConflictArbiter
import io.github.magisk317.smscode.xposed.runtime.CoreHookPolicy
import io.github.magisk317.smscode.xposed.runtime.CoreHookPolicyHolder
import io.github.magisk317.smscode.xposed.runtime.CoreLogSink
import io.github.magisk317.smscode.xposed.runtime.CoreLogSinkHolder
import io.github.magisk317.smscode.xposed.runtime.CoreRuntime
import io.github.magisk317.smscode.xposed.runtime.CoreRuntimeAccess
import io.github.magisk317.smscode.xposed.utils.XLog
import io.github.magisk317.smscode.runtime.contract.logging.DefaultLogSanitizer
import com.github.magisk317.smscode.di.appModule
import com.github.magisk317.smscode.runtime.RuntimeCodeRecordRestoreFacade
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import timber.log.Timber

class SmsCodeApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startedActivityCount: Int = 0

    override fun onCreate() {
        super.onCreate()
        android.util.Log.w("XSmsCode", "SmsCodeApplication.onCreate() START")
        ensureIpcToken()
        RuntimeLogStore.initialize(this, enableDetailedLogs = false)
        installCoreRuntime()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        startKoin {
            androidLogger()
            androidContext(this@SmsCodeApplication)
            modules(appModule, com.github.magisk317.smscode.di.billingModule)
        }
        
        org.koin.core.context.GlobalContext.get().getAll<com.github.magisk317.smscode.app.AppInitializer>().forEach {
            it.init(this)
        }

        initXposedServiceActivationMonitor()
        importPendingCodeRecords()
        syncPreferences()
        PhoneProcessRestartCoordinator.requestAfterInstallOrUpdate(this, applicationScope)
        registerLicenseActivityKiller()
    }

    private fun importPendingCodeRecords() {
        applicationScope.launch {
            RuntimeCodeRecordRestoreFacade.importToDatabase(this@SmsCodeApplication)
        }
    }

    private fun syncPreferences() {
        applicationScope.launch {
            HookPreferenceMirror.publish(this@SmsCodeApplication)
            val verboseLog = AppPreferencesDataStore.getBoolean(
                this@SmsCodeApplication,
                PrefConst.KEY_VERBOSE_LOG_MODE,
                false,
            )
            RuntimeLogStore.setEnabled(verboseLog)
        }
    }

    private fun initXposedServiceActivationMonitor() {
        XposedServiceBridge.initialize(this, applicationScope)
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
        CoreLogSinkHolder.install(object : CoreLogSink {
            override fun append(
                priority: Int,
                tag: String,
                message: String,
                force: Boolean,
                route: String?,
                sensitive: Boolean,
            ) {
                val safeMessage = if (sensitive) DefaultLogSanitizer.sanitize(message) else message
                RuntimeLogStore.append(priority, tag, safeMessage, force, route)
            }
        })
        CoreHookPolicyHolder.install(object : CoreHookPolicy {
            override fun shouldSuppressSystemHooks(context: Context?, source: String): Boolean {
                return ModuleConflictArbiter.shouldSuppressByRelay(context, source)
            }
        })
    }

    internal fun handleXposedServiceBound(
        remotePrefsProvider: (() -> SharedPreferences?)?,
        frameworkName: String?,
        frameworkVersion: String?,
    ) {
        android.util.Log.i(
            "XSmsCode",
            "handleXposedServiceBound() called: framework=$frameworkName version=$frameworkVersion provider=${remotePrefsProvider != null}",
        )
        AppPreferencesDataStore.setRemotePrefsProvider(remotePrefsProvider)
        ModuleUtils.setRuntimeActivated(true)
        ModuleActivationStore.markActivated(this)
        ActivationDiagnosticsStore.recordServiceBind(
            context = this,
            frameworkName = frameworkName ?: "unknown",
            frameworkVersion = frameworkVersion ?: "unknown",
            verboseLogging = PrefsReader.isVerboseLogMode(this),
        )
        XLog.i(
            "Xposed service connected: framework=%s version=%s",
            frameworkName ?: "unknown",
            frameworkVersion ?: "unknown",
        )
    }

    internal fun handleXposedServiceDied() {
        AppPreferencesDataStore.setRemotePrefsProvider(null)
        ModuleUtils.setRuntimeActivated(false)
        ActivationDiagnosticsStore.recordServiceDied(
            context = this,
            verboseLogging = PrefsReader.isVerboseLogMode(this),
        )
        XLog.w("Xposed service disconnected")
    }

    internal fun logXposedServiceBridgeFailure(throwable: Throwable) {
        XLog.w("Failed to register Xposed service listener: %s", throwable.message ?: "unknown")
    }

    private fun registerLicenseActivityKiller() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {
                startedActivityCount += 1
            }
            override fun onActivityResumed(activity: Activity) {
                if (activity.javaClass.name == "com.pairip.licensecheck.LicenseActivity") {
                    runCatching {
                        Timber.w("Detected com.pairip.licensecheck.LicenseActivity. Finishing it to prevent gray screen.")
                        activity.finish()
                    }
                        .onFailure { Timber.e(it, "Failed to finish LicenseActivity") }
                }
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {
                startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun ensureIpcToken() {
        runBlocking(Dispatchers.IO) {
            val token = AppPreferencesDataStore.getString(this@SmsCodeApplication, PrefConst.KEY_IPC_TOKEN, "")
            if (token.isEmpty()) {
                val newToken = UUID.randomUUID().toString()
                AppPreferencesDataStore.setString(this@SmsCodeApplication, PrefConst.KEY_IPC_TOKEN, newToken)
                Timber.i("Generated new IPC Security Token via DataStore")
            }
            HookPreferenceMirror.publish(this@SmsCodeApplication)
        }
    }

}
