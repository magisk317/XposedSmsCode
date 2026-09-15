package com.github.magisk317.smscode.data.log

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.github.magisk317.smscode.common.utils.ProviderCallerGuard
import com.github.magisk317.smscode.common.utils.RuntimeDiagnosticsBridge
import io.github.magisk317.smscode.runtime.common.diagnostics.RuntimeLogStore
import io.github.magisk317.smscode.runtime.common.utils.StorageUtils
import io.github.magisk317.xposed.logging.BaseXposedLogProvider
import io.github.magisk317.xposed.logging.LogProviderQuotaConfig
import io.github.magisk317.xposed.logging.LogProviderQuotaPolicy
import io.github.magisk317.xposed.logging.XposedLogEvent
import java.io.File
import java.util.Locale

class RuntimeLogProvider : BaseXposedLogProvider() {

    override val authority: String
        get() = "${context?.packageName ?: "unknown"}.$AUTHORITY_SUFFIX"

    override fun isCallerAllowed(context: Context): Boolean {
        return ProviderCallerGuard.isCallerAllowed(context)
    }

    override val ingressPolicy: LogProviderQuotaPolicy = LogProviderQuotaPolicy(
        LogProviderQuotaConfig(
            maxEventsPerWindow = 600,
            windowMs = 60_000L,
            maxBytesPerDay = RuntimeLogIngressPolicy.MAX_PERSISTED_LOG_BYTES,
            maxEventsPerDay = 100_000L,
            maxTrackedUids = 64,
        ),
    )

    override fun appendLog(event: XposedLogEvent) {
        val ctx = context?.applicationContext ?: return
        if (!RuntimeLogIngressPolicy.ensurePersistentQuota(StorageUtils.getLogDir(ctx))) return
        RuntimeDiagnosticsBridge.ensureInstalled()
        RuntimeLogStore.initialize(ctx, enableDetailedLogs = true)
        val normalizedLevel = RuntimeLogIngressPolicy.normalizeLevel(event.level)
        RuntimeLogStore.append(
            priority = priorityFor(normalizedLevel),
            tag = RuntimeLogIngressPolicy.truncateUtf8(event.tag, RuntimeLogIngressPolicy.MAX_TAG_BYTES),
            message = RuntimeLogIngressPolicy.truncateUtf8(
                event.message,
                RuntimeLogIngressPolicy.MAX_MESSAGE_BYTES,
            ),
            force = event.force || normalizedLevel in FORCE_LEVELS,
            route = RuntimeLogIngressPolicy.truncateUtf8(
                event.route ?: event.source,
                RuntimeLogIngressPolicy.MAX_ROUTE_BYTES,
            ),
            throwable = RuntimeLogIngressPolicy.truncateUtf8(
                event.throwable,
                RuntimeLogIngressPolicy.MAX_THROWABLE_BYTES,
            ),
        )
    }

    companion object {
        private const val AUTHORITY_SUFFIX = "runtime-log.provider"
        private val FORCE_LEVELS = setOf("W", "E")

        fun authority(packageName: String): String = "$packageName.$AUTHORITY_SUFFIX"

        fun uri(packageName: String): Uri = "content://${authority(packageName)}/entry".toUri()

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

internal object RuntimeLogIngressPolicy {
    const val MAX_TAG_BYTES = 128
    const val MAX_ROUTE_BYTES = 128
    const val MAX_MESSAGE_BYTES = 16 * 1024
    const val MAX_THROWABLE_BYTES = 64 * 1024
    const val MAX_PERSISTED_LOG_BYTES = 32L * 1024L * 1024L
    private const val PERSISTED_LOG_HEADROOM_BYTES = 64L * 1024L

    private val ALLOWED_LEVELS = setOf("V", "D", "I", "W", "E")

    fun normalizeLevel(level: String): String =
        level.uppercase(Locale.ROOT).takeIf(ALLOWED_LEVELS::contains) ?: "I"

    fun truncateUtf8(value: String, maxBytes: Int): String {
        if (value.toByteArray(Charsets.UTF_8).size <= maxBytes) return value
        val result = StringBuilder()
        var index = 0
        var bytes = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            val encodedBytes = String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8).size
            if (bytes + encodedBytes > maxBytes) break
            result.appendCodePoint(codePoint)
            bytes += encodedBytes
            index += Character.charCount(codePoint)
        }
        return result.toString()
    }

    fun ensurePersistentQuota(logDir: File?): Boolean {
        val runtimeLogs = logDir
            ?.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith("runtime") && it.extension == "jsonl" }
            .sortedWith(compareBy(File::lastModified, File::getName))
        var totalBytes = runtimeLogs.sumOf(File::length)
        if (totalBytes < MAX_PERSISTED_LOG_BYTES) return true
        for (file in runtimeLogs) {
            val fileBytes = file.length()
            if (file.delete()) totalBytes -= fileBytes
            if (totalBytes < MAX_PERSISTED_LOG_BYTES - PERSISTED_LOG_HEADROOM_BYTES) return true
        }
        return totalBytes < MAX_PERSISTED_LOG_BYTES
    }
}
