package com.tianma.xsmscode.xp.hook.code.helper

import com.tianma.xsmscode.common.utils.XLog

object InputHelper {

    @JvmStatic
    fun sendText(context: android.content.Context, text: String?, autoEnter: Boolean = false) {
        if (text == null) return
        val intent = android.content.Intent(
            com.tianma.xsmscode.xp.hook.system.SystemInputInjectorHook.ACTION_AUTO_INPUT,
        )
        intent.putExtra("code", text)
        intent.putExtra("autoEnter", autoEnter)
        // Broadcast without explicit package to avoid dropping delivery when
        // system_server receiver isn't bound to package.
        context.sendBroadcast(intent)
        XLog.i("Sent Broadcast ACTION_AUTO_INPUT with code: $text, autoEnter: $autoEnter")
    }
}
