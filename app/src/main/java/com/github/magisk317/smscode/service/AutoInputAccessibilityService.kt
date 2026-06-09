package com.github.magisk317.smscode.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import io.github.magisk317.smscode.verification.AutoInputAccessibilityNodeHelper
import io.github.magisk317.smscode.verification.AutoInputAccessibilityNodeHelper.Result as AutoInputResult
import io.github.magisk317.smscode.verification.AutoInputAccessibilityRequestHandler
import io.github.magisk317.smscode.xposed.hook.system.SystemInputInjectorHook
import io.github.magisk317.smscode.xposed.utils.XLog

class AutoInputAccessibilityService : AccessibilityService() {

    private var receiverRegistered = false

    override fun onCreate() {
        super.onCreate()
        XLog.d("Accessibility lifecycle create: %s", accessibilityStateSnapshot())
    }

    private val autoInputReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AutoInputAccessibilityRequestHandler.handle(
                serviceContext = this@AutoInputAccessibilityService,
                receiver = this,
                intent = intent,
                expectedAction = SystemInputInjectorHook.resolveActionAutoInput(),
                resultAction = SystemInputInjectorHook.resolveActionAutoInputResult(),
                packageName = packageName,
                performAutoInput = { request -> handleAutoInput(request.code, request.autoEnter) },
            )
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerAutoInputReceiver()
        XLog.d(
            "Accessibility lifecycle connected: %s %s",
            accessibilityStateSnapshot(),
            accessibilityServiceInfoSummary(),
        )
        XLog.d("Accessibility auto input service connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        val shouldRebind = super.onUnbind(intent)
        XLog.d(
            "Accessibility lifecycle unbind: action=%s shouldRebind=%s %s %s",
            intent?.action.orEmpty().ifBlank { "<none>" },
            shouldRebind,
            accessibilityStateSnapshot(),
            accessibilityServiceInfoSummary(),
        )
        return shouldRebind
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        XLog.d(
            "Accessibility lifecycle rebind: action=%s %s",
            intent?.action.orEmpty().ifBlank { "<none>" },
            accessibilityStateSnapshot(),
        )
    }

    override fun onDestroy() {
        XLog.d(
            "Accessibility lifecycle destroy start: %s %s",
            accessibilityStateSnapshot(),
            accessibilityServiceInfoSummary(),
        )
        unregisterAutoInputReceiver()
        XLog.d("Accessibility auto input service destroyed")
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        XLog.d("Accessibility lifecycle interrupt: %s", accessibilityStateSnapshot())
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        XLog.d(
            "Accessibility lifecycle task removed: action=%s %s",
            rootIntent?.action.orEmpty().ifBlank { "<none>" },
            accessibilityStateSnapshot(),
        )
        super.onTaskRemoved(rootIntent)
    }

    private fun registerAutoInputReceiver() {
        if (receiverRegistered) {
            XLog.d("Accessibility receiver register skipped: already registered")
            return
        }
        val filter = IntentFilter(SystemInputInjectorHook.resolveActionAutoInput()).apply {
            priority = RECEIVER_PRIORITY_ACCESSIBILITY
        }
        try {
            ContextCompat.registerReceiver(
                this,
                autoInputReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED,
            )
            receiverRegistered = true
            XLog.d("Accessibility receiver registered: priority=%d", RECEIVER_PRIORITY_ACCESSIBILITY)
        } catch (exception: SecurityException) {
            logReceiverRegisterFailure(exception)
            throw exception
        } catch (exception: IllegalArgumentException) {
            logReceiverRegisterFailure(exception)
            throw exception
        }
    }

    private fun logReceiverRegisterFailure(throwable: Throwable) {
        XLog.d(
            "Accessibility receiver register failed: %s",
            throwable.message ?: throwable.javaClass.simpleName,
        )
    }

    private fun unregisterAutoInputReceiver() {
        if (!receiverRegistered) {
            XLog.d("Accessibility receiver unregister skipped: not registered")
            return
        }
        runCatching { unregisterReceiver(autoInputReceiver) }
            .onSuccess { XLog.d("Accessibility receiver unregistered") }
            .onFailure { throwable ->
                XLog.d(
                    "Accessibility receiver unregister failed: %s",
                    throwable.message ?: throwable.javaClass.simpleName,
                )
            }
        receiverRegistered = false
    }

    private fun accessibilityStateSnapshot(): String {
        val component = android.content.ComponentName(packageName, javaClass.name).flattenToString()
        val enabledSetting = runCatching {
            Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, -1)
        }.getOrDefault(-2)
        val enabledServices = runCatching {
            Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
        }.getOrDefault("")
        val serviceEntries = enabledServices
            .split(':')
            .filter { it.isNotBlank() }
        val serviceListed = serviceEntries.any { it.equals(component, ignoreCase = true) }
        return "accessibilityEnabled=$enabledSetting serviceListed=$serviceListed " +
            "enabledServiceCount=${serviceEntries.size} receiverRegistered=$receiverRegistered"
    }

    private fun accessibilityServiceInfoSummary(): String {
        val info = runCatching { serviceInfo }.getOrNull() ?: return "serviceInfo=<null>"
        val capabilities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            info.capabilities
        } else {
            -1
        }
        return "serviceInfo[eventTypes=${info.eventTypes}, feedbackType=${info.feedbackType}, " +
            "flags=${info.flags}, capabilities=$capabilities]"
    }

    private fun handleAutoInput(
        code: String,
        autoEnter: Boolean,
    ): AutoInputResult {
        return AutoInputAccessibilityNodeHelper.performAutoInput(rootInActiveWindow, code, autoEnter)
    }

    private companion object {
        private const val RECEIVER_PRIORITY_ACCESSIBILITY = -500
    }
}
