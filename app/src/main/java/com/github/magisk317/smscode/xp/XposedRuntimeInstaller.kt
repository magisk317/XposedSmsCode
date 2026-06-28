package com.github.magisk317.smscode.xp

import android.content.Context
import com.github.magisk317.smscode.common.utils.PrefsReader
import com.github.magisk317.smscode.common.utils.RuntimeLogStore
import com.github.magisk317.smscode.data.log.RuntimeLogProvider
import com.github.magisk317.smscode.runtime.BuildConfig as RuntimeBuildConfig
import com.github.magisk317.smscode.xp.helper.ModuleConflictArbiter
import com.github.tianma8023.xposed.smscode.BuildConfig
import io.github.magisk317.smscode.runtime.contract.logging.DefaultLogSanitizer
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
        installLogSink()
    }

    fun ensureHookProcessLogging(moduleContext: Context) {
        this.moduleContext = moduleContext.applicationContext ?: moduleContext
        PrefsReader.setHookContext(this.moduleContext!!)
        RuntimeLogStore.initialize(this.moduleContext ?: moduleContext, enableDetailedLogs = PrefsReader.isVerboseLogMode(moduleContext))
        installLogSink()
    }

    private fun installLogSink() {
        if (logSinkInstalled) return
        synchronized(this) {
            if (logSinkInstalled) return
            XposedLogClient.attachContext(moduleContext ?: return@synchronized)
            android.util.Log.w("XSmsCode", "installLogSink: installing CoreLogSink, moduleContext=${moduleContext != null}")
            CoreLogSinkHolder.install(object : CoreLogSink {
                override fun append(
                    priority: Int,
                    tag: String,
                    message: String,
                    force: Boolean,
                    route: String?,
                    sensitive: Boolean,
                ) {
                    val sensitiveDebugEnabled = moduleContext?.let {
                        PrefsReader.isSensitiveDebugLogMode(it)
                    } ?: false
                    val safeMessage = if (sensitive && !sensitiveDebugEnabled) {
                        DefaultLogSanitizer.sanitize(message)
                    } else {
                        message
                    }
                    XposedLogClient.append(priority, tag, safeMessage, force, route, sensitive)
                }
            })
            logSinkInstalled = true
        }
    }
}
