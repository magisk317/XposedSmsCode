package com.tianma.xsmscode.xp.hook

import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

interface IHook {

    @Throws(Throwable::class)
    fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam)

    @Throws(Throwable::class)
    fun onLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam)
}
