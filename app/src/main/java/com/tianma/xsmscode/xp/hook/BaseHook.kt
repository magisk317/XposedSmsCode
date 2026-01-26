package com.tianma.xsmscode.xp.hook

import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

open class BaseHook : IHook {

    @Throws(Throwable::class)
    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
    }

    open fun hookInitZygote(): Boolean {
        return false
    }

    @Throws(Throwable::class)
    override fun onLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
    }

    open fun hookOnLoadPackage(): Boolean {
        return true
    }
}
