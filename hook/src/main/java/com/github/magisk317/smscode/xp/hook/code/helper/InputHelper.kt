package com.github.magisk317.smscode.xp.hook.code.helper

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import io.github.magisk317.smscode.verification.AutoInputBroadcastHelper
import io.github.magisk317.smscode.xposed.utils.XLog

object InputHelper {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JvmStatic
    fun sendText(
        context: android.content.Context,
        text: String?,
        autoEnter: Boolean = false,
        inputIntervalMs: Long = 0L,
        attemptId: Long? = null,
    ) {
        AutoInputBroadcastHelper.sendText(
            context = context,
            text = text,
            autoEnter = autoEnter,
            inputIntervalMs = inputIntervalMs,
            attemptId = attemptId,
            actionResolver = {
                io.github.magisk317.smscode.xposed.hook.system.SystemInputInjectorHook.resolveActionAutoInput()
            },
        )
    }

    @JvmStatic
    fun sendToast(
        context: android.content.Context,
        text: String?,
        duration: Int = Toast.LENGTH_LONG,
    ) {
        if (text.isNullOrEmpty()) return
        mainHandler.post {
            Toast.makeText(context, text, duration).show()
        }
        XLog.i(
            "Show toast locally with textLength: %d, duration: %d",
            text.length,
            duration,
        )
    }
}
