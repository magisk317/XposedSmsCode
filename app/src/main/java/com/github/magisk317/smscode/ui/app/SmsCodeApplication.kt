package com.github.magisk317.smscode.ui.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.telephony.TelephonyManager
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
import com.github.magisk317.smscode.ui.record.CodeRecordRestoreManager
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import timber.log.Timber

class SmsCodeApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startedActivityCount: Int = 0

    override fun onCreate() {
        super.onCreate()
        ensureIpcToken()
        RuntimeLogStore.initialize(this, enableDetailedLogs = false)
        installCoreRuntime()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        startKoin {
            androidLogger()
            androidContext(this@SmsCodeApplication)
            modules(appModule)
        }
        initXposedServiceActivationMonitor()
        importPendingCodeRecords()
        syncPreferences()
        handlePhoneProcessRestartIfNeeded()
        registerLicenseActivityKiller()
    }

    private fun importPendingCodeRecords() {
        applicationScope.launch {
            CodeRecordRestoreManager.importToDatabase(this@SmsCodeApplication)
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
            override val logLevel: Int = BuildConfig.LOG_LEVEL
            override val logToXposed: Boolean = BuildConfig.LOG_TO_XPOSED
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
        applicationScope.launch {
            val token = AppPreferencesDataStore.getString(this@SmsCodeApplication, PrefConst.KEY_IPC_TOKEN, "")
            if (token.isEmpty()) {
                val newToken = UUID.randomUUID().toString()
                AppPreferencesDataStore.setString(this@SmsCodeApplication, PrefConst.KEY_IPC_TOKEN, newToken)
                Timber.i("Generated new IPC Security Token via DataStore")
            }
            HookPreferenceMirror.publish(this@SmsCodeApplication)
        }
    }

    private fun handlePhoneProcessRestartIfNeeded() {
        applicationScope.launch {
            val installToken = buildInstallToken() ?: return@launch
            val prefs = getSharedPreferences(INSTALL_GUARD_PREFS, MODE_PRIVATE)
            val lastHandledToken = prefs.getString(KEY_LAST_HANDLED_INSTALL_TOKEN, null)
            if (lastHandledToken == installToken) {
                return@launch
            }

            val now = System.currentTimeMillis()
            val lastAttemptAt = prefs.getLong(KEY_LAST_RESTART_ATTEMPT_AT, 0L)
            if (now - lastAttemptAt < RESTART_ATTEMPT_COOLDOWN_MS) {
                return@launch
            }
            prefs.edit().putLong(KEY_LAST_RESTART_ATTEMPT_AT, now).apply()

            if (isPhoneCallActive()) {
                return@launch
            }

            val hasRootAccess = canUseRoot()
            if (hasRootAccess) {
                restartPhoneProcessViaRoot()
            }

            // Mark token handled even when root is unavailable to avoid repeated noisy attempts.
            prefs.edit().putString(KEY_LAST_HANDLED_INSTALL_TOKEN, installToken).apply()
        }
    }

    private fun buildInstallToken(): String? {
        val packageInfo = runCatching { getSelfPackageInfo() }.getOrNull() ?: return null
        val apkFile = runCatching { File(applicationInfo.sourceDir) }.getOrNull() ?: return null
        val apkSize = runCatching { apkFile.length() }.getOrDefault(0L)
        val apkModified = runCatching { apkFile.lastModified() }.getOrDefault(0L)
        return listOf(
            packageInfo.firstInstallTime,
            packageInfo.lastUpdateTime,
            apkSize,
            apkModified,
        ).joinToString(separator = ":")
    }

    private fun getSelfPackageInfo(): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
    }

    private fun canUseRoot(): Boolean {
        val result = runSuCommand("id -u")
        return result.exitCode == 0 && result.output.trim() == "0"
    }

    private fun isPhoneCallActive(): Boolean {
        val telephonyInCall = runCatching {
            val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            @Suppress("DEPRECATION")
            val state = telephonyManager?.callState ?: TelephonyManager.CALL_STATE_IDLE
            state == TelephonyManager.CALL_STATE_OFFHOOK || state == TelephonyManager.CALL_STATE_RINGING
        }.getOrDefault(false)
        if (telephonyInCall) {
            return true
        }

        // Fallback without runtime permission dependency.
        return runCatching {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val mode = audioManager?.mode ?: AudioManager.MODE_NORMAL
            mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
        }.getOrDefault(false)
    }

    private fun restartPhoneProcessViaRoot() {
        val packages = PHONE_PROCESS_PACKAGES.joinToString(separator = " ")
        val command =
            "for PKG in $packages; do " +
                "PIDS=\$(pidof \"${'$'}PKG\" 2>/dev/null); " +
                "if [ -n \"${'$'}PIDS\" ]; then kill -9 ${'$'}PIDS; FOUND=1; fi; " +
                "pkill -f \"${'$'}PKG\" >/dev/null 2>&1 && FOUND=1; " +
                "done; " +
                "if [ \"${'$'}FOUND\" = 1 ]; then exit 0; fi; " +
                "exit 1"
        val result = runSuCommand(command)
        if (result.exitCode == 0) {
            Timber.i("Phone process restart requested after install/update change.")
        }
    }

    private fun runSuCommand(command: String): SuCommandResult {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val completed = process.waitFor(SU_COMMAND_TIMEOUT_SEC, TimeUnit.SECONDS)
            if (!completed) {
                process.destroy()
                if (process.isAlive) {
                    process.destroyForcibly()
                }
                return SuCommandResult(exitCode = -2, output = "")
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.exitValue()
            SuCommandResult(exitCode = exitCode, output = output)
        } catch (_: Throwable) {
            SuCommandResult(exitCode = -1, output = "")
        }
    }

    private data class SuCommandResult(
        val exitCode: Int,
        val output: String,
    )

    companion object {
        private const val INSTALL_GUARD_PREFS = "install_guard_prefs"
        private const val KEY_LAST_HANDLED_INSTALL_TOKEN = "last_handled_install_token"
        private const val KEY_LAST_RESTART_ATTEMPT_AT = "last_restart_attempt_at"
        private const val RESTART_ATTEMPT_COOLDOWN_MS = 60_000L
        private const val SU_COMMAND_TIMEOUT_SEC = 10L
        private val PHONE_PROCESS_PACKAGES = listOf(
            "com.android.phone",
            "com.xiaomi.phone",
        )
    }
}
