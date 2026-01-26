package com.tianma.xsmscode.common.utils

/**
 * 当前Xposed模块相关工具类
 */
object ModuleUtils {

    /**
     * 返回模块版本 <br/>
     * 注意：该方法被本模块Hook住，返回的值是 BuildConfig.MODULE_VERSION，如果没被Hook则返回-1
     */
    @JvmStatic
    fun getModuleVersion(): Int {
        XLog.d("getModuleVersion()")
        return -1
    }

    /**
     * 当前模块是否在XposedInstaller中被启用
     */
    @JvmStatic
    fun isModuleEnabled(): Boolean {
        return getModuleVersion() > 0
    }
}
