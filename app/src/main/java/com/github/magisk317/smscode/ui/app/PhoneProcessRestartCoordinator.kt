package com.github.magisk317.smscode.ui.app

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.telephony.TelephonyManager
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

object PhoneProcessRestartCoordinator {

    fun requestAfterInstallOrUpdate(context: Context, scope: CoroutineScope) {
        val appContext = context.applicationContext
        scope.launch(Dispatchers.IO) {
            restartAfterInstallOrUpdate(appContext)
        }
    }

    fun restartAfterInstallOrUpdate(context: Context) {
        synchronized(restartLock) {
            val appContext = context.applicationContext
            val installToken = buildInstallToken(appContext) ?: return
            val prefs = appContext.getSharedPreferences(INSTALL_GUARD_PREFS, Context.MODE_PRIVATE)
            val lastHandledToken = prefs.getString(KEY_LAST_HANDLED_INSTALL_TOKEN, null)
            if (lastHandledToken == installToken) {
                return
            }

            val now = System.currentTimeMillis()
            val lastAttemptAt = prefs.getLong(KEY_LAST_RESTART_ATTEMPT_AT, 0L)
            if (now - lastAttemptAt < RESTART_ATTEMPT_COOLDOWN_MS) {
                return
            }
            prefs.edit().putLong(KEY_LAST_RESTART_ATTEMPT_AT, now).apply()

            if (isPhoneCallActive(appContext)) {
                Timber.i("Skip phone process restart because call state is active.")
                return
            }

            if (!canUseRoot()) {
                Timber.w("Target process restart deferred after install/update: root unavailable.")
                return
            }

            val restartResult = restartTargetProcessesViaRoot()
            if (restartResult.shouldMarkInstallHandled) {
                prefs.edit().putString(KEY_LAST_HANDLED_INSTALL_TOKEN, installToken).apply()
            }
        }
    }

    private fun buildInstallToken(context: Context): String? {
        val packageInfo = runCatching { getSelfPackageInfo(context) }.getOrNull() ?: return null
        val apkFile = runCatching { File(context.applicationInfo.sourceDir) }.getOrNull() ?: return null
        val apkSize = runCatching { apkFile.length() }.getOrDefault(0L)
        val apkModified = runCatching { apkFile.lastModified() }.getOrDefault(0L)
        return listOf(
            packageInfo.firstInstallTime,
            packageInfo.lastUpdateTime,
            apkSize,
            apkModified,
        ).joinToString(separator = ":")
    }

    private fun getSelfPackageInfo(context: Context): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
    }

    private fun canUseRoot(): Boolean {
        val result = runSuCommand("id -u")
        return result.exitCode == 0 && result.output.trim() == "0"
    }

    private fun isPhoneCallActive(context: Context): Boolean {
        val telephonyInCall = runCatching {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            @Suppress("DEPRECATION")
            val state = telephonyManager?.callState ?: TelephonyManager.CALL_STATE_IDLE
            state == TelephonyManager.CALL_STATE_OFFHOOK || state == TelephonyManager.CALL_STATE_RINGING
        }.getOrDefault(false)
        if (telephonyInCall) {
            return true
        }

        // Fallback without runtime permission dependency.
        return runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val mode = audioManager?.mode ?: AudioManager.MODE_NORMAL
            mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
        }.getOrDefault(false)
    }

    private fun restartTargetProcessesViaRoot(): RestartAttemptResult {
        val targets = TARGET_PROCESSES.joinToString(separator = " ") { shellQuote(it) }
        val command =
            "FOUND=0; KILLED=0; FAILED=0; " +
                "for NAME in $targets; do " +
                "PIDS=\$(pidof \"${'$'}NAME\" 2>/dev/null); " +
                "if [ -n \"${'$'}PIDS\" ]; then " +
                "FOUND=\$((FOUND + 1)); " +
                "if kill -9 ${'$'}PIDS >/dev/null 2>&1; then " +
                "KILLED=\$((KILLED + 1)); " +
                "else FAILED=\$((FAILED + 1)); fi; " +
                "fi; " +
                "done; " +
                "echo found=${'$'}FOUND killed=${'$'}KILLED failed=${'$'}FAILED; " +
                "if [ \"${'$'}FAILED\" -gt 0 ]; then exit 1; fi; " +
                "exit 0"
        val result = runSuCommand(command)
        val restartResult = RestartAttemptResult(
            exitCode = result.exitCode,
            summary = parseRestartCommandSummary(result.output),
            rawOutput = result.output,
        )
        if (restartResult.shouldMarkInstallHandled) {
            Timber.i(
                "Target process restart completed after install/update change: found=%d killed=%d",
                restartResult.summary?.found ?: 0,
                restartResult.summary?.killed ?: 0,
            )
        } else {
            Timber.w(
                "Target process restart still pending after install/update change: exit=%d output=%s",
                result.exitCode,
                result.output.trim(),
            )
        }
        return restartResult
    }

    internal fun parseRestartCommandSummary(output: String): RestartCommandSummary? {
        val match = RESTART_SUMMARY_REGEX.find(output) ?: return null
        return RestartCommandSummary(
            found = match.groupValues[1].toInt(),
            killed = match.groupValues[2].toInt(),
            failed = match.groupValues[3].toInt(),
        )
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\\''") + "'"
    }

    private fun runSuCommand(command: String): SuCommandResult {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val completed = process.waitFor(SU_COMMAND_TIMEOUT_SEC, TimeUnit.SECONDS)
            if (!completed) {
                process.destroy()
                if (process.isAlive) {
                    process.destroyForcibly()
                }
                return SuCommandResult(exitCode = -2, output = "")
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.exitValue()
            SuCommandResult(exitCode = exitCode, output = output)
        } catch (_: Throwable) {
            SuCommandResult(exitCode = -1, output = "")
        }
    }

    private data class SuCommandResult(
        val exitCode: Int,
        val output: String,
    )

    internal data class RestartCommandSummary(
        val found: Int,
        val killed: Int,
        val failed: Int,
    )

    internal data class RestartAttemptResult(
        val exitCode: Int,
        val summary: RestartCommandSummary?,
        val rawOutput: String,
    ) {
        val shouldMarkInstallHandled: Boolean
            get() = exitCode == 0 && summary != null && summary.failed == 0
    }

    private const val INSTALL_GUARD_PREFS = "install_guard_prefs"
    private const val KEY_LAST_HANDLED_INSTALL_TOKEN = "last_handled_install_token"
    private const val KEY_LAST_RESTART_ATTEMPT_AT = "last_restart_attempt_at"
    private const val RESTART_ATTEMPT_COOLDOWN_MS = 60_000L
    private const val SU_COMMAND_TIMEOUT_SEC = 10L
    private val RESTART_SUMMARY_REGEX = Regex("""found=(\d+)\s+killed=(\d+)\s+failed=(\d+)""")
    private val restartLock = Any()
    private val TARGET_PROCESSES = listOf(
        "com.android.phone",
        "com.xiaomi.phone",
        "com.android.providers.telephony",
        "com.android.mms",
        "com.android.mms:mms_service",
        "com.google.android.apps.messaging",
    )
}
