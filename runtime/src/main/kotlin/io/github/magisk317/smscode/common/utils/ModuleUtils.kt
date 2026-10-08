package io.github.magisk317.smscode.common.utils

import android.content.Context

object ModuleUtils {
    @JvmStatic
    fun isRuntimeActivated(): Boolean =
        io.github.magisk317.smscode.xposed.utils.ModuleUtils.isRuntimeActivated()

    @JvmStatic
    fun isModuleActivated(context: Context): Boolean =
        io.github.magisk317.smscode.xposed.utils.ModuleUtils.isModuleActivated(context)
}
