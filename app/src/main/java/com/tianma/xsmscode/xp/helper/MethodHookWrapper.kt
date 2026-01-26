package com.tianma.xsmscode.xp.helper

import com.tianma.xsmscode.common.utils.XLog
import de.robv.android.xposed.XC_MethodHook

abstract class MethodHookWrapper : XC_MethodHook() {
    @Throws(Throwable::class)
    override fun beforeHookedMethod(param: MethodHookParam) {
        try {
            before(param)
        } catch (t: Throwable) {
            XLog.d("Error in hook %s", param.method.name, t)
        }
    }

    @Throws(Throwable::class)
    protected open fun before(param: MethodHookParam) {
    }

    @Throws(Throwable::class)
    override fun afterHookedMethod(param: MethodHookParam) {
        try {
            after(param)
        } catch (t: Throwable) {
            XLog.e("Error in hook %s", param.method.name, t)
        }
    }

    @Throws(Throwable::class)
    protected open fun after(param: MethodHookParam) {
    }
}
