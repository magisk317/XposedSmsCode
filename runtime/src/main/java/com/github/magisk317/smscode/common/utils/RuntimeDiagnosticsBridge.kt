package com.github.magisk317.smscode.common.utils

import android.content.Context
import com.github.magisk317.smscode.runtime.BuildConfig
import io.github.magisk317.smscode.runtime.common.diagnostics.ActivationDiagnosticsSnapshot
import io.github.magisk317.smscode.runtime.common.diagnostics.ActivationStatusInputs
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeDiagnosticsConfig
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeDiagnosticsInstaller
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeDiagnosticsPreferences

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
        return RuntimeDiagnosticsPreferences.readInt(
            context = context,
            preferencesName = "xposed_prefs",
            key = KEY_RUNTIME_LOG_RETENTION_DAYS,
            defaultValue = RUNTIME_LOG_RETENTION_DAYS_DEFAULT,
            minimumValue = RUNTIME_LOG_RETENTION_DAYS_MIN,
        )
    }

    private fun resolveActivationStatus(
        context: Context,
        snapshot: ActivationDiagnosticsSnapshot,
        inputs: ActivationStatusInputs,
    ): Boolean {
        return inputs.runtimeConnected || inputs.hasHookHeartbeat
    }
}
