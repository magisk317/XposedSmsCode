package com.github.magisk317.smscode.common.utils

import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import io.github.magisk317.smscode.runtime.contract.logging.XLog as ContractXLog

/**
 * Thin delegate to the unified XLog in :contract.
 * Preserves the `com.github.magisk317.smscode.common.utils.XLog` import path
 * that existing callers use.
 */
object XLog {
    @JvmStatic fun v(message: String, vararg args: Any?) = ContractXLog.v(message, *args)
    @JvmStatic fun d(message: String, vararg args: Any?) = ContractXLog.d(message, *args)
    @JvmStatic fun i(message: String, vararg args: Any?) = ContractXLog.i(message, *args)
    @JvmStatic fun w(message: String, vararg args: Any?) = ContractXLog.w(message, *args)
    @JvmStatic fun e(message: String, vararg args: Any?) = ContractXLog.e(message, *args)
    @JvmStatic fun setLogLevel(logLevel: Int) = ContractXLog.setLogLevel(logLevel)
    @JvmStatic fun getLogLevel(): Int = ContractXLog.getLogLevel()
}
