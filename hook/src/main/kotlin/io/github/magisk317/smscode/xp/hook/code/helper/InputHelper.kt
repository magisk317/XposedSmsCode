package io.github.magisk317.smscode.xp.hook.code.helper

import android.content.Context
import io.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge
import io.github.magisk317.smscode.xposed.helper.LocalInputActions

/** Thin delegate; implementation lives in core [LocalInputActions]. */
object InputHelper {

    @JvmStatic
    fun sendText(
        context: Context,
        text: String?,
        autoEnter: Boolean = false,
        inputIntervalMs: Long = 0L,
        attemptId: Long? = null,
    ) {
        LocalInputActions.sendText(
            context = context,
            text = text,
            autoEnter = autoEnter,
            inputIntervalMs = inputIntervalMs,
            attemptId = attemptId,
            tokenProvider = {
                HookRuntimeBridge.prefsAccess.getIpcToken(context).takeIf(String::isNotBlank) ?: ""
            },
        )
    }

    @JvmStatic
    fun sendToast(
        context: Context,
        text: String?,
        duration: Int = android.widget.Toast.LENGTH_LONG,
    ) {
        LocalInputActions.sendToast(context, text, duration)
    }
}
