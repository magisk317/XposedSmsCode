package com.github.magisk317.smscode.common.utils

import android.content.Context
import com.github.magisk317.smscode.runtime.BuildConfig
import io.github.magisk317.smscode.runtime.common.diagnostics.ActivationDiagnosticsSnapshot
import io.github.magisk317.smscode.runtime.common.diagnostics.ActivationStatusInputs
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeDiagnosticsConfig
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeDiagnosticsEnvironment

internal object RuntimeDiagnosticsBridge {
    private const val KEY_RUNTIME_LOG_RETENTION_DAYS = "pref_runtime_log_retention_days"
    private const val RUNTIME_LOG_RETENTION_DAYS_DEFAULT = 2
    private const val RUNTIME_LOG_RETENTION_DAYS_MIN = 1

    @Volatile
    private var installed = false

    fun ensureInstalled() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            RuntimeDiagnosticsEnvironment.install(
                RuntimeDiagnosticsConfig(
                    applicationId = BuildConfig.APPLICATION_ID,
                    logTag = BuildConfig.LOG_TAG,
                    exportFilePrefix = "smscode_logs_",
                    stagingDirPrefix = ".tmp_smscode_logs_",
                    logRetentionDaysProvider = ::readConfiguredLogRetentionDays,
                    runtimeConnectedProvider = ModuleUtils::isRuntimeActivated,
                    activationStatusResolver = ::resolveActivationStatus,
                    routeResolver = { io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogStore.ROUTE_APP },
                ),
            )
            installed = true
        }
    }

    private fun readConfiguredLogRetentionDays(context: Context): Int {
        val prefs = runCatching { context.getSharedPreferences("xposed_prefs", Context.MODE_PRIVATE) }.getOrNull()
            ?: return RUNTIME_LOG_RETENTION_DAYS_DEFAULT
        val raw = prefs.all[KEY_RUNTIME_LOG_RETENTION_DAYS]
        val value = when (raw) {
            is Int -> raw
            is Long -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> RUNTIME_LOG_RETENTION_DAYS_DEFAULT
        } ?: RUNTIME_LOG_RETENTION_DAYS_DEFAULT
        return value.coerceAtLeast(RUNTIME_LOG_RETENTION_DAYS_MIN)
    }

    private fun resolveActivationStatus(
        context: Context,
        snapshot: ActivationDiagnosticsSnapshot,
        inputs: ActivationStatusInputs,
    ): Boolean {
        return inputs.runtimeConnected || inputs.hasHookHeartbeat
    }
}
