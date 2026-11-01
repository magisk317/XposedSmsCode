package io.github.magisk317.smscode.ui.app

import io.github.magisk317.smscode.common.constant.PrefConst
import io.github.magisk317.smscode.common.utils.HookPreferenceMirror
import io.github.libxposed.service.XposedService
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.xposed.runtime.state.FrameworkHookTargetStatus
import io.github.magisk317.xposed.runtime.state.RuntimeStateStore
import io.github.magisk317.xposed.service.ServiceState
import io.github.magisk317.xposed.service.XposedServiceBridge as KitServiceBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The kit owns the libxposed listener and its bind/retract state; this object only maps each
 * transition onto the effects the app has always performed on them.
 */
internal object XposedServiceBridge {
    fun initialize(application: SmsCodeApplication, applicationScope: CoroutineScope) {
        XLog.i(message = "XposedServiceBridge.initialize() called")
        val registered = KitServiceBridge.register(
            onError = { operation, error ->
                XLog.e(message = "Xposed service bridge failed during %s", operation, error)
                application.logXposedServiceBridgeFailure(error)
            },
        )
        if (registered) {
            XLog.i(message = "XposedServiceHelper.registerListener() succeeded")
        }
        applicationScope.launch {
            KitServiceBridge.state.collect { state ->
                when (state) {
                    is ServiceState.Bound -> onBound(application, applicationScope, state)
                    ServiceState.Retracted -> onRetracted(application)
                    ServiceState.Waiting -> Unit
                }
            }
        }
    }

    private fun onBound(
        application: SmsCodeApplication,
        applicationScope: CoroutineScope,
        bound: ServiceState.Bound,
    ) {
        MagiskOtel.event(
            name = "hook.service",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "app",
                "stage" to "bound",
            ),
            statusOk = true,
        )
        XLog.i(
            message = "XposedServiceBridge.onServiceBind() called: framework=%s version=%s",
            bound.service.frameworkName,
            bound.service.frameworkVersion,
        )
        recordFrameworkHookTargets(application, bound.service)
        application.handleXposedServiceBound(
            remotePrefsProvider = { bound.service.getRemotePreferences(PrefConst.REMOTE_PREFS_GROUP) },
            frameworkName = bound.service.frameworkName,
            frameworkVersion = bound.service.frameworkVersion,
        )
        PhoneProcessRestartCoordinator.requestAfterXposedServiceBind(
            application,
            applicationScope,
        )
        applicationScope.launch {
            HookPreferenceMirror.publish(application)
        }
    }

    /**
     * Records the processes the framework reports as running the module code.
     *
     * The framework is the only side that knows which processes carry the module, so this keeps
     * the activation status independent from the hook having to reach the app through a content
     * provider the system may refuse to start.
     */
    private fun recordFrameworkHookTargets(application: SmsCodeApplication, service: XposedService) {
        val targets = runCatching { service.runningTargets }.getOrElse { error ->
            // The framework could not answer; keep what it told us last time.
            XLog.w(message = "failed to read the framework hook targets: ${error.message}")
            return
        }
        val packageManager = application.packageManager
        RuntimeStateStore.recordFrameworkHookTargets(
            context = application,
            targets = targets.map { target ->
                FrameworkHookTargetStatus(
                    packageName = packageManager.getPackagesForUid(target.uid)?.firstOrNull().orEmpty(),
                    processName = target.processName,
                    pid = target.pid,
                    uid = target.uid,
                    state = target.state.name,
                )
            },
            source = "framework_targets",
        )
    }

    private fun onRetracted(application: SmsCodeApplication) {
        MagiskOtel.event(
            name = "hook.service",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "app",
                "stage" to "died",
            ),
            statusOk = true,
        )
        XLog.w(message = "XposedServiceBridge.onServiceDied() called")
        application.handleXposedServiceDied()
    }
}
