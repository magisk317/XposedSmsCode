package com.github.magisk317.smscode.common.utils

import android.content.Context
import com.github.magisk317.smscode.runtime.BuildConfig
import io.github.magisk317.smscode.runtime.contract.diagnostics.ActivationDiagnosticsSnapshot
import io.github.magisk317.smscode.runtime.contract.diagnostics.ActivationStatusInputs
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeDiagnosticsConfig
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeDiagnosticsInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

object RuntimeDiagnosticsBridge {
    private const val KEY_RUNTIME_LOG_RETENTION_DAYS = "pref_runtime_log_retention_days"
    private const val RUNTIME_LOG_RETENTION_DAYS_DEFAULT = 2
    private const val RUNTIME_LOG_RETENTION_DAYS_MIN = 1

    private val installer = RuntimeDiagnosticsInstaller(
        configProvider = {
            RuntimeDiagnosticsConfig(
                applicationId = BuildConfig.APPLICATION_ID,
                logTag = BuildConfig.LOG_TAG,
                exportFilePrefix = "smscode_logs_",
                stagingDirPrefix = ".tmp_smscode_logs_",
                logRetentionDaysProvider = ::readConfiguredLogRetentionDays,
                runtimeConnectedProvider = ModuleUtils::isRuntimeActivated,
                activationStatusResolver = ::resolveActivationStatus,
                routeResolver = { io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogStore.ROUTE_APP },
            )
        },
    )

    fun ensureInstalled() = installer.ensureInstalled()

    private fun readConfiguredLogRetentionDays(context: Context): Int {
        // RuntimeLogStore may invoke this callback with a target-process context when the
        // provider route is unavailable. Never interpret that process's private DataStore as
        // module configuration.
        if (context.packageName != BuildConfig.APPLICATION_ID) {
            return RUNTIME_LOG_RETENTION_DAYS_DEFAULT
        }
        return runBlocking(Dispatchers.IO) {
            AppPreferencesDataStore.getInt(context, KEY_RUNTIME_LOG_RETENTION_DAYS, RUNTIME_LOG_RETENTION_DAYS_DEFAULT)
        }.coerceAtLeast(RUNTIME_LOG_RETENTION_DAYS_MIN)
    }

    private fun resolveActivationStatus(
        context: Context,
        snapshot: ActivationDiagnosticsSnapshot,
        inputs: ActivationStatusInputs,
    ): Boolean {
        return inputs.runtimeConnected || inputs.hasHookHeartbeat
    }
}
