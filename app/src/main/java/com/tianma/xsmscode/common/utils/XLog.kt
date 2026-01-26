package com.tianma.xsmscode.common.utils

import android.util.Log
import com.github.tianma8023.xposed.smscode.BuildConfig

object XLog {

    private val LOG_TAG = BuildConfig.LOG_TAG
    @Volatile
    private var sLogLevel = BuildConfig.LOG_LEVEL
    private const val LOG_TO_XPOSED = BuildConfig.LOG_TO_XPOSED

    private fun log(priority: Int, message: String, vararg args: Any?) {
        if (priority < sLogLevel) return

        var formattedMessage = if (args.isNotEmpty()) {
            try {
                String.format(message, *args)
            } catch (e: Exception) {
                message
            }
        } else {
            message
        }

        if (args.isNotEmpty() && args[args.size - 1] is Throwable) {
            val throwable = args[args.size - 1] as Throwable
            val stacktraceStr = Log.getStackTraceString(throwable)
            formattedMessage += "\n$stacktraceStr"
        }

        // Write to the default log tag
        Log.println(priority, LOG_TAG, formattedMessage)

        // Duplicate to the Xposed log if enabled
        if (LOG_TO_XPOSED) {
            // only log to LSPosed
            Log.println(priority, "LSPosed-Bridge", "$LOG_TAG: $formattedMessage")
        }
    }

    @JvmStatic
    fun v(message: String, vararg args: Any?) {
        log(Log.VERBOSE, message, *args)
    }

    @JvmStatic
    fun d(message: String, vararg args: Any?) {
        log(Log.DEBUG, message, *args)
    }

    @JvmStatic
    fun i(message: String, vararg args: Any?) {
        log(Log.INFO, message, *args)
    }

    @JvmStatic
    fun w(message: String, vararg args: Any?) {
        log(Log.WARN, message, *args)
    }

    @JvmStatic
    fun e(message: String, vararg args: Any?) {
        log(Log.ERROR, message, *args)
    }

    @JvmStatic
    fun setLogLevel(logLevel: Int) {
        sLogLevel = logLevel
    }
}
