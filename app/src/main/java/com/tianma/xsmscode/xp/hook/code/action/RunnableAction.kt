package com.tianma.xsmscode.xp.hook.code.action

import android.content.Context
import com.tianma.xsmscode.data.db.entity.SmsMsg
import de.robv.android.xposed.XSharedPreferences

/**
 * Runnable + Action + Callable
 */
abstract class RunnableAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
    xsp: XSharedPreferences
) : CallableAction(pluginContext, phoneContext, smsMsg, xsp), Runnable {

    override fun run() {
        call()
    }
}
