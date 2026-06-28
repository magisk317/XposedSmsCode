package com.github.magisk317.smscode.data.log

import android.content.Context
import android.net.Uri
import com.github.magisk317.smscode.common.utils.RuntimeLogStore
import io.github.magisk317.xposed.logging.BaseXposedLogProvider
import io.github.magisk317.xposed.logging.XposedLogEvent

class RuntimeLogProvider : BaseXposedLogProvider() {

    override val authority: String
        get() = "${context?.packageName ?: "unknown"}.$AUTHORITY_SUFFIX"

    override fun appendLog(event: XposedLogEvent) {
        val ctx = context?.applicationContext ?: return
        RuntimeLogStore.initialize(ctx, enableDetailedLogs = true)
        RuntimeLogStore.append(
            priority = priorityFor(event.level),
            tag = event.tag,
            message = event.message,
            force = event.force || event.level in FORCE_LEVELS,
            route = event.route ?: event.source,
        )
    }

    companion object {
        private const val AUTHORITY_SUFFIX = "runtime-log.provider"
        private val FORCE_LEVELS = setOf("W", "E")

        fun authority(packageName: String): String = "$packageName.$AUTHORITY_SUFFIX"

        fun uri(packageName: String): Uri = Uri.parse("content://${authority(packageName)}/entry")

        fun uri(context: Context): Uri = uri(context.packageName)

        @Deprecated("Hook-side logging should use XposedLogClient instead")
        fun append(
            context: Context,
            authorityPackageName: String,
            priority: Int,
            tag: String,
            message: String,
            force: Boolean,
            route: String?,
        ): Boolean {
            io.github.magisk317.xposed.logging.XposedLogClient.append(
                priority = priority,
                tag = tag,
                message = message,
                force = force,
                route = route,
                sensitive = false
            )
            return true
        }

        @Deprecated("Hook-side logging should use XposedLogClient instead")
        fun append(
            context: Context,
            priority: Int,
            tag: String,
            message: String,
            force: Boolean,
            route: String?,
        ): Boolean {
            io.github.magisk317.xposed.logging.XposedLogClient.append(
                priority = priority,
                tag = tag,
                message = message,
                force = force,
                route = route,
                sensitive = false
            )
            return true
        }

        private fun priorityFor(level: String): Int = when (level) {
            "E" -> android.util.Log.ERROR
            "W" -> android.util.Log.WARN
            "I" -> android.util.Log.INFO
            "D" -> android.util.Log.DEBUG
            else -> android.util.Log.VERBOSE
        }
    }
}
