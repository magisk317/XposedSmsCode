package com.github.magisk317.smscode.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
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
            if (intent.action != SystemInputInjectorHook.resolveActionAutoInput()) return

            val code = intent.getStringExtra("code").orEmpty()
            val autoEnter = intent.getBooleanExtra("autoEnter", false)
            val inputIntervalMs = intent.getLongExtra("inputIntervalMs", 0L).coerceAtLeast(0L)
            val attemptId = intent.getLongExtra("attemptId", -1L).takeIf { it >= 0L }
            XLog.w(
                "Accessibility auto input request received: attemptId=%d code_len=%d autoEnter=%s inputIntervalMs=%d ordered=%s",
                attemptId ?: -1L,
                code.length,
                autoEnter,
                inputIntervalMs,
                isOrderedBroadcast,
            )

            if (code.isBlank()) {
                XLog.w("Accessibility auto input ignored: empty code")
                return
            }

            val result = handleAutoInput(code, autoEnter)
            XLog.w(
                "Accessibility auto input result: attemptId=%d success=%s strategy=%s reason=%s windowPkg=%s",
                attemptId ?: -1L,
                result.success,
                result.strategy,
                result.reason,
                result.windowPackage.ifBlank { "<none>" },
            )
            publishOrderedAccessibilityResult(result)
            if (result.success) {
                publishTerminalAutoInputResult(attemptId, result)
            }
            if (result.success && isOrderedBroadcast) {
                abortBroadcast()
            }
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
        val root = rootInActiveWindow ?: return AutoInputResult(false, "none", "no_active_window")
        val windowPackage = root.packageName?.toString().orEmpty()
        XLog.w(
            "Accessibility active window: pkg=%s class=%s",
            windowPackage.ifBlank { "<unknown>" },
            root.className?.toString().orEmpty().ifBlank { "<unknown>" },
        )

        val focusedNode = findFocusedEditableNode(root)
        if (focusedNode != null) {
            val focusedResult = setNodeText(focusedNode, code, autoEnter)
            if (focusedResult.success) {
                return focusedResult.copy(strategy = "focused_node", windowPackage = windowPackage)
            }
            XLog.w("Accessibility focused-node input failed: reason=%s", focusedResult.reason)
        }

        val editableNodes = collectEditableNodes(root)
        if (editableNodes.isEmpty()) {
            return AutoInputResult(false, "none", "no_editable_node", windowPackage)
        }

        val groupedResult = fillEditableGroup(editableNodes, code, autoEnter)
        if (groupedResult.success) {
            return groupedResult.copy(windowPackage = windowPackage)
        }

        val singleResult = setNodeText(editableNodes.first(), code, autoEnter)
        if (singleResult.success) {
            return singleResult.copy(strategy = "best_editable_node", windowPackage = windowPackage)
        }

        return groupedResult.copy(windowPackage = windowPackage)
    }

    private fun findFocusedEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val inputFocus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (inputFocus != null && canSetText(inputFocus)) {
            return inputFocus
        }
        val accessibilityFocus = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        if (accessibilityFocus != null && canSetText(accessibilityFocus)) {
            return accessibilityFocus
        }
        return null
    }

    private fun collectEditableNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val result = ArrayList<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (canSetText(node)) {
                result += node
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }
        return result
            .distinctBy { buildNodeIdentity(it) }
            .sortedWith(
                compareByDescending<AccessibilityNodeInfo> { it.isFocused }
                    .thenBy { nodeTop(it) }
                    .thenBy { nodeLeft(it) },
            )
    }

    private fun fillEditableGroup(
        nodes: List<AccessibilityNodeInfo>,
        code: String,
        autoEnter: Boolean,
    ): AutoInputResult {
        if (code.length !in 4..8) {
            return AutoInputResult(false, "group_nodes", "group_strategy_not_applicable")
        }
        val charNodes = nodes.filter { nodeTextLength(it) <= 1 }
        if (charNodes.size < code.length) {
            return AutoInputResult(false, "group_nodes", "not_enough_editable_nodes")
        }

        for ((index, char) in code.withIndex()) {
            val result = setNodeText(
                node = charNodes[index],
                text = char.toString(),
                autoEnter = autoEnter && index == code.lastIndex,
            )
            if (!result.success) {
                XLog.w("Accessibility grouped input failed at index=%d reason=%s", index, result.reason)
                return AutoInputResult(false, "group_nodes", "group_set_text_failed")
            }
        }
        return AutoInputResult(true, "group_nodes", "ok")
    }

    private fun setNodeText(
        node: AccessibilityNodeInfo,
        text: String,
        autoEnter: Boolean,
    ): AutoInputResult {
        return runCatching {
            if (!node.isFocused) {
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                node.refresh()
            }
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val setTextSucceeded = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            if (!setTextSucceeded) {
                return AutoInputResult(false, "set_text", "action_set_text_failed")
            }
            if (autoEnter) {
                tryImeEnter(node)
            }
            AutoInputResult(true, "set_text", "ok")
        }.getOrElse { throwable ->
            XLog.w("Accessibility set text failed: %s", throwable.message ?: throwable.javaClass.simpleName)
            AutoInputResult(false, "set_text", "set_text_exception")
        }
    }

    private fun tryImeEnter(node: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            XLog.w("Accessibility auto enter skipped: android version too low")
            return
        }
        val imeEnterActionId = runCatching {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
        }.getOrNull() ?: return
        if (node.actionList.none { it.id == imeEnterActionId }) {
            XLog.w("Accessibility auto enter skipped: ime action unsupported")
            return
        }
        val result = node.performAction(imeEnterActionId)
        XLog.w("Accessibility auto enter invoked: success=%s", result)
    }

    private fun canSetText(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser) return false
        if (!node.isEnabled) return false
        if (node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }) return true
        if (node.isEditable) return true
        val className = node.className?.toString().orEmpty()
        return className.contains("EditText", ignoreCase = true)
    }

    private fun buildNodeIdentity(node: AccessibilityNodeInfo): String {
        val bounds = Rect().also(node::getBoundsInScreen)
        return listOf(
            node.viewIdResourceName.orEmpty(),
            node.className?.toString().orEmpty(),
            node.packageName?.toString().orEmpty(),
            bounds.left.toString(),
            bounds.top.toString(),
            bounds.right.toString(),
            bounds.bottom.toString(),
        ).joinToString("|")
    }

    private fun nodeTop(node: AccessibilityNodeInfo): Int = Rect().also(node::getBoundsInScreen).top

    private fun nodeLeft(node: AccessibilityNodeInfo): Int = Rect().also(node::getBoundsInScreen).left

    private fun nodeTextLength(node: AccessibilityNodeInfo): Int {
        val hintLength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            node.hintText?.length
        } else {
            null
        }
        return node.text?.length ?: hintLength ?: 0
    }

    private fun publishOrderedAccessibilityResult(result: AutoInputResult) {
        if (!autoInputReceiver.isOrderedBroadcast) return
        val extras = runCatching { autoInputReceiver.getResultExtras(true) }.getOrElse { Bundle() }
        extras.putBoolean(SystemInputInjectorHook.EXTRA_ACCESSIBILITY_HANDLED, true)
        extras.putBoolean(SystemInputInjectorHook.EXTRA_ACCESSIBILITY_SUCCESS, result.success)
        extras.putString(SystemInputInjectorHook.EXTRA_ACCESSIBILITY_REASON, result.reason)
        extras.putString(SystemInputInjectorHook.EXTRA_ACCESSIBILITY_STRATEGY, result.strategy)
        extras.putString(SystemInputInjectorHook.EXTRA_ACCESSIBILITY_WINDOW_PACKAGE, result.windowPackage)
        autoInputReceiver.setResultExtras(extras)
    }

    private fun publishTerminalAutoInputResult(
        attemptId: Long?,
        result: AutoInputResult,
    ) {
        val resolvedAttemptId = attemptId ?: return
        val intent = Intent(SystemInputInjectorHook.resolveActionAutoInputResult()).apply {
            setPackage(packageName)
            putExtra("attemptId", resolvedAttemptId)
            putExtra("success", result.success)
            if (!result.success) {
                putExtra("reason", result.reason)
            }
        }
        runCatching { sendBroadcast(intent) }
            .onFailure { error ->
                XLog.w(
                    "Accessibility auto input result broadcast failed: %s",
                    error.message ?: error.javaClass.simpleName,
                )
            }
    }

    private data class AutoInputResult(
        val success: Boolean,
        val strategy: String,
        val reason: String,
        val windowPackage: String = "",
    )

    private companion object {
        private const val RECEIVER_PRIORITY_ACCESSIBILITY = -500
    }
}
