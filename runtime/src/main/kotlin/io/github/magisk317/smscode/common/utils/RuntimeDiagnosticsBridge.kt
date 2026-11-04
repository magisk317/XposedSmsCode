package io.github.magisk317.smscode.common.utils

import io.github.magisk317.smscode.common.constant.PrefConst
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import android.content.Context
import io.github.magisk317.smscode.runtime.BuildConfig
import io.github.magisk317.smscode.runtime.common.diagnostics.LogRetentionConfigs
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
                routeResolver = { io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogStore.ROUTE_APP },
            )
        },
    )

    fun ensureInstalled() = installer.ensureInstalled()

    private fun readConfiguredLogRetentionDays(context: Context): Int =
        LogRetentionConfigs.readConfiguredLogRetentionDays(
            context = context,
            applicationId = BuildConfig.APPLICATION_ID,
            key = PrefConst.KEY_RUNTIME_LOG_RETENTION_DAYS,
            dataStoreDefaultDays = MagiskLogPolicy.DEFAULT_LOG_RETENTION_DAYS,
        )
}
