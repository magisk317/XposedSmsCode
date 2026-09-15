package com.github.magisk317.smscode.ui.app

import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.common.utils.AppPreferencesDataStore
import com.github.magisk317.smscode.common.utils.HookPreferenceMirror
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import io.github.magisk317.smscode.xposed.utils.XLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import io.github.magisk317.xposed.logging.MagiskOtel

internal object XposedServiceBridge {
    fun initialize(application: SmsCodeApplication, applicationScope: CoroutineScope) {
        XLog.i("XposedServiceBridge.initialize() called")
        runCatching<Unit> {
            XposedServiceHelper.registerListener(
                object : XposedServiceHelper.OnServiceListener {
                    override fun onServiceBind(service: XposedService) {
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
                            "XposedServiceBridge.onServiceBind() called: framework=%s version=%s",
                            service.frameworkName,
                            service.frameworkVersion,
                        )
                        application.handleXposedServiceBound(
                            remotePrefsProvider = { service.getRemotePreferences(PrefConst.REMOTE_PREFS_GROUP) },
                            frameworkName = service.frameworkName,
                            frameworkVersion = service.frameworkVersion,
                        )
                        PhoneProcessRestartCoordinator.requestAfterXposedServiceBind(
                            application,
                            applicationScope,
                        )
                        applicationScope.launch {
                            HookPreferenceMirror.publish(application)
                        }
                    }

                    override fun onServiceDied(service: XposedService) {
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
                        XLog.w("XposedServiceBridge.onServiceDied() called")
                        application.handleXposedServiceDied()
                    }
                },
            )
            XLog.i("XposedServiceHelper.registerListener() succeeded")
        }.onFailure {
            XLog.e("XposedServiceHelper.registerListener() failed", it)
            application.logXposedServiceBridgeFailure(it)
        }
    }
}
