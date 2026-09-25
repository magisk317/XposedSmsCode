package com.github.magisk317.smscode.ui.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import com.github.magisk317.smscode.runtime.BuildConfig as RuntimeBuildConfig
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.github.magisk317.smscode.common.constant.PrefConst
import io.github.magisk317.smscode.runtime.common.diagnostics.ActivationDiagnosticsStore
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import com.github.magisk317.smscode.common.utils.XscPreferenceHooks
import com.github.magisk317.smscode.common.utils.HookPreferenceMirror
import io.github.magisk317.smscode.xposed.utils.ModuleActivationStore
import io.github.magisk317.smscode.xposed.utils.ModuleUtils
import com.github.magisk317.smscode.common.utils.RuntimeDiagnosticsBridge
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogStore
import com.github.magisk317.smscode.xp.helper.ModuleConflictArbiter
import io.github.magisk317.smscode.xposed.runtime.CoreHookPolicy
import io.github.magisk317.smscode.xposed.runtime.CoreHookPolicyHolder
import io.github.magisk317.smscode.xposed.runtime.CoreLogSink
import io.github.magisk317.smscode.xposed.runtime.CoreLogSinkHolder
import io.github.magisk317.smscode.xposed.runtime.CoreRuntime
import io.github.magisk317.smscode.runtime.common.utils.StorageUtils
import io.github.magisk317.smscode.xposed.runtime.CoreRuntimeAccess
import io.github.magisk317.smscode.xposed.utils.XLog
import io.github.magisk317.xposed.logging.DefaultLogSanitizer
import com.github.magisk317.smscode.di.appModule
import com.magisk317.mobile.entitlement.MobileEntitlementBridge
import com.magisk317.mobile.entitlement.MobileEntitlementConfig
import com.magisk317.mobile.entitlement.MobileEntitlementCoordinator
import com.magisk317.mobile.entitlement.MobileEntitlementPublishedState
import com.magisk317.mobile.entitlement.MobileEntitlementRuntime
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
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.xposed.logging.AnonymousInstallationId

@SuppressLint("LogNotTimber")
class SmsCodeApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startedActivityCount: Int = 0
    private var entitlementForegroundPrimed = false

    override fun onCreate() {
        super.onCreate()
        AppPreferencesDataStore.install(XscPreferenceHooks)
        configureMobileEntitlement()
        android.util.Log.w("smscode", "SmsCodeApplication.onCreate() START")
        val installationId = AnonymousInstallationId.getOrCreate(this, TELEMETRY_PREFS_NAME)
        runBlocking {
            AppPreferencesDataStore.setString(
                this@SmsCodeApplication,
                AnonymousInstallationId.PREFERENCE_KEY,
                installationId,
            )
        }
        MagiskOtel.configureForInstallation(
            this,
            MagiskOtel.Config(
                enabled = BuildConfig.DEBUG || runBlocking {
                    AppPreferencesDataStore.getBoolean(
                        this@SmsCodeApplication,
                        PrefConst.KEY_ENABLE_ANALYTICS,
                        true,
                    )
                },
                serviceName = "xposedsmscode",
                serviceVersion = BuildConfig.VERSION_NAME,
                projectId = "83955172",
                projectName = "XposedSmsCode",
                environment = if (BuildConfig.DEBUG) "debug" else "release",
                // Skip dominates this service (~81% of every span): an intercept check that found
                // nothing to do. Dropping it keeps ok / error / fallback readable.
                suppressedResultValues = setOf("skip"),
            ),
            TELEMETRY_PREFS_NAME,
        )
        MagiskOtel.event(
            name = "app.boot",
            attributes = mapOf(
                "result" to "ok",
                "process" to "main",
            ),
        )
        ensureIpcToken()
        RuntimeDiagnosticsBridge.ensureInstalled()
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
        
        org.koin.core.context.GlobalContext.get().getAll<io.github.magisk317.uikit.shell.AppInitializer>().forEach {
            it.init(this)
        }

        initXposedServiceActivationMonitor()
        importPendingCodeRecords()
        syncPreferences()
        registerLicenseActivityKiller()
        MobileEntitlementCoordinator.initialize(this, applicationScope)
    }

    private fun configureMobileEntitlement() {
        MobileEntitlementRuntime.configure(
            MobileEntitlementConfig(
                apiOrigin = BuildConfig.MOBILE_ENTITLEMENT_API_ORIGIN,
                signingPublicJwk = BuildConfig.MOBILE_ENTITLEMENT_SIGNING_PUBLIC_JWK,
                appId = "xposed-sms-code",
                channel = BuildConfig.MOBILE_ENTITLEMENT_CHANNEL,
                enforced = BuildConfig.MOBILE_ENTITLEMENT_ENFORCED,
            ),
            bridge = object : MobileEntitlementBridge {
                override fun publish(context: Context, state: MobileEntitlementPublishedState): Boolean = runBlocking {
                    AppPreferencesDataStore.batchEdit(context) {
                        setBoolean(
                            PrefConst.KEY_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED,
                            state.automationAllowed,
                        )
                        setString(
                            PrefConst.KEY_MOBILE_ENTITLEMENT_TOKEN,
                            state.entitlementToken.orEmpty(),
                        )
                    }
                    HookPreferenceMirror.publish(context)
                }

                override fun log(message: String, vararg args: Any?) {
                    XLog.i(message, *args)
                }
            },
        )
    }

    private companion object {
        const val TELEMETRY_PREFS_NAME = "smscode_telemetry_prefs"
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
            RuntimeDiagnosticsBridge.ensureInstalled()
            RuntimeLogStore.setEnabled(verboseLog)
            val sensitiveDebugMode = AppPreferencesDataStore.getBoolean(
                this@SmsCodeApplication,
                PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE,
                false,
            )
            io.github.magisk317.xposed.logging.LogSanitizerConfig
                .syncFromVerboseMode(sensitiveDebugMode)
            StorageUtils.ensureExternalAppDataPermissions(this@SmsCodeApplication)
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
                throwableText: String?,
            ) {
                // sensitive=true means payload may contain secrets; honor shared switch
                // Opening pref_sensitive_debug_log_mode disables sanitization and lets
                // plaintext through for debugging.
                val safeMessage = if (sensitive) {
                    DefaultLogSanitizer.sanitizeIfEnabled(message)
                } else {
                    message
                }
                RuntimeDiagnosticsBridge.ensureInstalled()
                RuntimeLogStore.append(priority, tag, safeMessage, force, route, throwableText)
            }
        })
        CoreHookPolicyHolder.install(object : CoreHookPolicy {
            override fun shouldSuppressSystemHooks(context: Context?, source: String): Boolean {
                return ModuleConflictArbiter.shouldSuppressByRelay(context, source)
            }
        })
        AppShellRuntimeBridge.install(this)
    }

    internal fun handleXposedServiceBound(
        remotePrefsProvider: (() -> SharedPreferences?)?,
        frameworkName: String?,
        frameworkVersion: String?,
    ) {
        XLog.i(
            "handleXposedServiceBound() called: framework=$frameworkName version=$frameworkVersion provider=${remotePrefsProvider != null}",
        )
        AppPreferencesDataStore.setRemotePrefsProvider(remotePrefsProvider)
        ModuleUtils.setRuntimeActivated(true)
        ModuleActivationStore.markActivated(this)
        ActivationDiagnosticsStore.recordServiceBind(
            context = this,
            frameworkName = frameworkName ?: "unknown",
            frameworkVersion = frameworkVersion ?: "unknown",
            verboseLogging = readVerboseLogMode(),
        )
        XLog.i(
            "Xposed service connected: framework=%s version=%s",
            frameworkName ?: "unknown",
            frameworkVersion ?: "unknown",
        )
        MagiskOtel.event(
            name = "app.lifecycle",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "app",
                "stage" to "xposed_service_bind",
                "reason" to "connected",
                "source" to (frameworkName ?: "unknown"),
            ),
            statusOk = true,
        )
    }

    internal fun handleXposedServiceDied() {
        AppPreferencesDataStore.setRemotePrefsProvider(null)
        ModuleUtils.setRuntimeActivated(false)
        ActivationDiagnosticsStore.recordServiceDied(
            context = this,
            verboseLogging = readVerboseLogMode(),
        )
        XLog.w("Xposed service disconnected")
        MagiskOtel.event(
            name = "app.lifecycle",
            attributes = mapOf(
                "result" to "error",
                "duration_ms" to "0",
                "process" to "app",
                "stage" to "xposed_service_died",
                "reason" to "disconnected",
            ),
            statusOk = false,
        )
    }

    internal fun logXposedServiceBridgeFailure(throwable: Throwable) {
        XLog.w("Failed to register Xposed service listener: %s", throwable.message ?: "unknown")
        MagiskOtel.event(
            name = "app.lifecycle",
            attributes = mapOf(
                "result" to "error",
                "duration_ms" to "0",
                "process" to "app",
                "stage" to "xposed_service_bridge",
                "reason" to throwable.javaClass.simpleName,
            ),
            statusOk = false,
        )
    }

    private fun readVerboseLogMode(): Boolean = runBlocking(Dispatchers.IO) {
        AppPreferencesDataStore.getBoolean(this@SmsCodeApplication, PrefConst.KEY_VERBOSE_LOG_MODE, false)
    }

    private fun registerLicenseActivityKiller() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {
                if (startedActivityCount == 0) {
                    if (entitlementForegroundPrimed) {
                        applicationScope.launch {
                            runCatching { MobileEntitlementCoordinator.refresh(this@SmsCodeApplication) }
                        }
                    } else {
                        // initialize() covers the first process start.
                        entitlementForegroundPrimed = true
                    }
                }
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
            AppIpcTokenStore.ensurePublished(
                existingToken = {
                    AppPreferencesDataStore.getString(this@SmsCodeApplication, PrefConst.KEY_IPC_TOKEN, "")
                },
                generateToken = { UUID.randomUUID().toString() },
                persistToken = { generated ->
                    AppPreferencesDataStore.setString(
                        this@SmsCodeApplication,
                        PrefConst.KEY_IPC_TOKEN,
                        generated,
                    )
                    Timber.i("Generated new IPC Security Token via DataStore")
                },
            )
            HookPreferenceMirror.publish(this@SmsCodeApplication)
        }
    }

}
