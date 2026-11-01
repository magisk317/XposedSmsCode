package io.github.magisk317.smscode.ui.app

import io.github.magisk317.smscode.common.constant.PrefConst
import io.github.magisk317.smscode.common.utils.HookPreferenceMirror
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.xposed.service.FrameworkHookTargets
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
        FrameworkHookTargets.record(application, bound.service)
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
