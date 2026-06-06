package com.github.magisk317.smscode.ui.app

import com.github.magisk317.smscode.common.utils.AppPreferencesDataStore
import com.github.magisk317.smscode.common.utils.HookPreferenceMirror
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal object FlavorXposedServiceBridge {
    fun initialize(application: SmsCodeApplication, applicationScope: CoroutineScope) {
        runCatching<Unit> {
            XposedServiceHelper.registerListener(
                object : XposedServiceHelper.OnServiceListener {
                    override fun onServiceBind(service: XposedService) {
                        application.handleXposedServiceBound(
                            remotePrefsProvider = { service.getRemotePreferences("xposed_prefs") },
                            frameworkName = service.frameworkName,
                            frameworkVersion = service.frameworkVersion,
                        )
                        applicationScope.launch {
                            HookPreferenceMirror.publish(application)
                        }
                    }

                    override fun onServiceDied(service: XposedService) {
                        application.handleXposedServiceDied()
                    }
                },
            )
        }.onFailure {
            application.logXposedServiceBridgeFailure(it)
        }
    }
}
