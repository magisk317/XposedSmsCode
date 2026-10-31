package io.github.magisk317.smscode.common.utils

import io.github.magisk317.smscode.common.constant.PrefConst
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import android.content.Context
import io.github.magisk317.smscode.runtime.BuildConfig
import io.github.magisk317.xposed.runtime.state.RuntimeDiagnosticsSnapshot
import io.github.magisk317.xposed.runtime.state.RuntimeStatusInputs
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeDiagnosticsConfig
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeDiagnosticsInstaller
import io.github.magisk317.xposed.logging.MagiskLogPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

object RuntimeDiagnosticsBridge {
    private const val PRODUCT = "smscode"

    private val installer = RuntimeDiagnosticsInstaller(
        configProvider = {
            RuntimeDiagnosticsConfig(
                applicationId = BuildConfig.APPLICATION_ID,
                logTag = BuildConfig.LOG_TAG,
                // One token names the bundle, the staging directory and the export directory.
                product = PRODUCT,
                distributionChannel = BuildConfig.MOBILE_ENTITLEMENT_CHANNEL,
                commitHash = BuildConfig.COMMIT_HASH,
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
            return MagiskLogPolicy.DEFAULT_LOG_RETENTION_DAYS
        }
        return runBlocking(Dispatchers.IO) {
            AppPreferencesDataStore.getInt(
                context,
                PrefConst.KEY_RUNTIME_LOG_RETENTION_DAYS,
                MagiskLogPolicy.DEFAULT_LOG_RETENTION_DAYS,
            )
        }.let(MagiskLogPolicy::retentionDays)
    }

    private fun resolveActivationStatus(
        context: Context,
        snapshot: RuntimeDiagnosticsSnapshot,
        inputs: RuntimeStatusInputs,
    ): Boolean {
        return inputs.runtimeConnected || inputs.hasHookHeartbeat
    }
}
