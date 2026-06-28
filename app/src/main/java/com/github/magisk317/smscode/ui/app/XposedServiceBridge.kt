package com.github.magisk317.smscode.ui.app

import com.github.magisk317.smscode.common.utils.AppPreferencesDataStore
import com.github.magisk317.smscode.common.utils.HookPreferenceMirror
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal object XposedServiceBridge {
    fun initialize(application: SmsCodeApplication, applicationScope: CoroutineScope) {
        android.util.Log.i("XSmsCode", "XposedServiceBridge.initialize() called")
        runCatching<Unit> {
            XposedServiceHelper.registerListener(
                object : XposedServiceHelper.OnServiceListener {
                    override fun onServiceBind(service: XposedService) {
                        android.util.Log.i(
                            "XSmsCode",
                            "XposedServiceBridge.onServiceBind() called: framework=${service.frameworkName} version=${service.frameworkVersion}",
                        )
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
                        android.util.Log.w("XSmsCode", "XposedServiceBridge.onServiceDied() called")
                        application.handleXposedServiceDied()
                    }
                },
            )
            android.util.Log.i("XSmsCode", "XposedServiceHelper.registerListener() succeeded")
        }.onFailure {
            android.util.Log.e("XSmsCode", "XposedServiceHelper.registerListener() failed", it)
            application.logXposedServiceBridgeFailure(it)
        }
    }
}
