package com.github.magisk317.smscode.xp

import android.content.Context
import android.net.Uri
import com.github.magisk317.smscode.common.utils.PrefsReader
import com.github.magisk317.smscode.common.utils.RuntimeLogStore
import com.github.magisk317.smscode.data.db.DBProvider
import com.github.magisk317.smscode.data.log.RuntimeLogProvider
import com.github.magisk317.smscode.runtime.BuildConfig as RuntimeBuildConfig
import com.github.magisk317.smscode.runtime.RuntimeCodeRecordRestoreFacade
import com.github.magisk317.smscode.runtime.RuntimeNotificationFacade
import com.github.magisk317.smscode.runtime.RuntimePrefsFacade
import com.github.magisk317.smscode.runtime.RuntimeStorageFacade
import com.github.magisk317.smscode.runtime.bridge.HookContentProviderAccess
import com.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge
import com.github.magisk317.smscode.xp.helper.ModuleConflictArbiter
import com.github.tianma8023.xposed.smscode.BuildConfig
import io.github.magisk317.xposed.logging.DefaultLogSanitizer
import io.github.magisk317.smscode.xposed.runtime.CoreHookPolicy
import io.github.magisk317.smscode.xposed.runtime.CoreHookPolicyHolder
import io.github.magisk317.smscode.xposed.runtime.CoreLogSink
import io.github.magisk317.smscode.xposed.runtime.CoreLogSinkHolder
import io.github.magisk317.smscode.xposed.runtime.CoreRuntime
import io.github.magisk317.smscode.xposed.runtime.CoreRuntimeAccess
import io.github.magisk317.xposed.logging.XposedLogClient

object XposedRuntimeInstaller {

    @Volatile
    private var logSinkInstalled = false

    @Volatile
    private var moduleContext: Context? = null

    fun installEntryRuntime() {
        CoreRuntime.install(object : CoreRuntimeAccess {
            override val logTag: String = BuildConfig.LOG_TAG
            override val logLevel: Int = RuntimeBuildConfig.LOG_LEVEL
            override val logToXposed: Boolean = RuntimeBuildConfig.LOG_TO_XPOSED
            override val debug: Boolean = BuildConfig.DEBUG
            override val applicationId: String = BuildConfig.APPLICATION_ID
            override val actionNamespace: String = "com.github.magisk317.smscode"
        })
        CoreHookPolicyHolder.install(object : CoreHookPolicy {
            override fun shouldSuppressSystemHooks(context: Context?, source: String): Boolean {
                return ModuleConflictArbiter.shouldSuppressByRelay(context, source)
            }
        })
        XposedLogClient.configure(
            authority = RuntimeLogProvider.authority(BuildConfig.APPLICATION_ID),
            source = "SmsCode",
        )
        installHookBridge()
        installLogSink()
    }

    /**
     * Wires the extracted :hook module's [HookRuntimeBridge] to the concrete
     * runtime Facades. Must run in every hooked process before any hook fires,
     * otherwise hook code hits `HookRuntimeBridge.*Access` "not installed" errors.
     */
    private fun installHookBridge() {
        HookRuntimeBridge.install(
            prefs = RuntimePrefsFacade,
            notification = RuntimeNotificationFacade,
            storage = RuntimeStorageFacade,
            codeRecord = RuntimeCodeRecordRestoreFacade,
            contentProvider = object : HookContentProviderAccess {
                override fun smsMsgContentUri(context: Context): Uri =
                    DBProvider.smsMsgContentUri(context)

                override fun appInfoContentUri(context: Context): Uri =
                    DBProvider.appInfoContentUri(context)

                override fun authority(context: Context): String =
                    DBProvider.authority(context)
            },
        )
        HookRuntimeBridge.hookProcessInit = { context -> ensureHookProcessLogging(context) }
    }

    fun ensureHookProcessLogging(moduleContext: Context) {
        this.moduleContext = moduleContext.applicationContext ?: moduleContext
        com.github.magisk317.smscode.common.utils.PrefsReader.setHookContext(this.moduleContext!!)
        val verbose = com.github.magisk317.smscode.common.utils.PrefsReader.isVerboseLogMode(moduleContext)
        RuntimeLogStore.initialize(this.moduleContext ?: moduleContext, enableDetailedLogs = verbose)
        syncLogSanitizerConfig()
        installLogSink()
    }

    private fun installLogSink() {
        if (logSinkInstalled) return
        synchronized(this) {
            if (logSinkInstalled) return
            XposedLogClient.attachContext(moduleContext ?: return@synchronized)
            android.util.Log.w("XSmsCode", "installLogSink: installing CoreLogSink, moduleContext=${moduleContext != null}")
            syncLogSanitizerConfig()
            CoreLogSinkHolder.install(object : CoreLogSink {
                override fun append(
                    priority: Int,
                    tag: String,
                    message: String,
                    force: Boolean,
                    route: String?,
                    sensitive: Boolean,
                ) {
                    val safeMessage = if (sensitive) {
                        DefaultLogSanitizer.sanitizeIfEnabled(message)
                    } else {
                        message
                    }
                    XposedLogClient.append(priority, tag, safeMessage, force, route, sensitive)
                }
            })
            logSinkInstalled = true
        }
    }

    /**
     * Apply [LogSanitizerConfig] once from prefs. Prefer calling again when prefs
     * change; do not re-read prefs on every log line (hot path + noisy).
     */
    private fun syncLogSanitizerConfig() {
        val ctx = moduleContext ?: return
        val sensitiveDebugEnabled = runCatching {
            com.github.magisk317.smscode.common.utils.PrefsReader.isSensitiveDebugLogMode(ctx)
        }.getOrDefault(false)
        io.github.magisk317.xposed.logging.LogSanitizerConfig.setEnabled(!sensitiveDebugEnabled)
    }
}
