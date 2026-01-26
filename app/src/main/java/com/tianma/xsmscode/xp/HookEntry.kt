package com.tianma.xsmscode.xp

import android.util.Log
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.tianma.xsmscode.common.constant.PrefConst
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.common.utils.XSPUtils
import com.tianma.xsmscode.xp.hook.BaseHook
import com.tianma.xsmscode.xp.hook.me.ModuleUtilsHook
import com.tianma.xsmscode.xp.hook.permission.PermissionGranterHook
import com.tianma.xsmscode.xp.hook.code.SmsHandlerHook
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.callbacks.XC_LoadPackage

class HookEntry : IXposedHookLoadPackage, IXposedHookZygoteInit {

    private val mHookList: List<BaseHook> = listOf(
        SmsHandlerHook(), // InBoundsSmsHandler Hook
        ModuleUtilsHook(), // ModuleUtils Hook
        PermissionGranterHook() // PackageManagerService Hook
    )

    @Throws(Throwable::class)
    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        for (hook in mHookList) {
            if (hook.hookInitZygote()) {
                hook.initZygote(startupParam)
            }
        }

        try {
            val xsp = XSharedPreferences(BuildConfig.APPLICATION_ID, PrefConst.PREF_NAME)
            if (XSPUtils.isVerboseLogMode(xsp)) {
                XLog.setLogLevel(Log.VERBOSE)
            } else {
                XLog.setLogLevel(BuildConfig.LOG_LEVEL)
            }
        } catch (t: Throwable) {
            XLog.e("", t)
        }
    }

    @Throws(Throwable::class)
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (hook in mHookList) {
            if (hook.hookOnLoadPackage()) {
                hook.onLoadPackage(lpparam)
            }
        }
    }
}
