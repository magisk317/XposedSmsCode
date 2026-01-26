package com.tianma.xsmscode.xp.hook.code.action

import android.content.Context
import android.os.Bundle
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.data.db.entity.SmsMsg
import de.robv.android.xposed.XSharedPreferences
import java.util.concurrent.Callable

/**
 * Action + Callable
 */
abstract class CallableAction(
    @JvmField protected val mPluginContext: Context,
    @JvmField protected val mPhoneContext: Context,
    @JvmField protected val mSmsMsg: SmsMsg,
    @JvmField protected val xsp: XSharedPreferences
) : Action<Bundle?>, Callable<Bundle?> {

    override fun call(): Bundle? {
        return try {
            action()
        } catch (t: Throwable) {
            XLog.e("Error in CallableAction#call()", t)
            null
        }
    }
}
