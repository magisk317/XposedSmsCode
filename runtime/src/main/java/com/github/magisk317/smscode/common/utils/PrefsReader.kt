package com.github.magisk317.smscode.common.utils

import android.content.Context
import android.content.SharedPreferences
import com.github.magisk317.smscode.common.constant.CodeNotificationOwner
import com.github.magisk317.smscode.common.constant.PrefConst
import java.util.Collections
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import io.github.magisk317.smscode.runtime.common.prefs.PrefsResolver
import io.github.magisk317.smscode.runtime.common.prefs.SharedPrefsSource
import io.github.magisk317.smscode.runtime.contract.prefs.PrefReadResult
import io.github.magisk317.smscode.runtime.contract.prefs.PrefSources
import io.github.magisk317.smscode.runtime.contract.prefs.PrefsSource

object PrefsReader {
    private const val PREFS_NAME = "xposed_prefs"
    private const val SOURCE_REMOTE_PROVIDER = "provider"
    private const val SOURCE_LOCAL_HOOK_PREFS = "local_hook_prefs"

    /**
     * TTL for the in-process resolved-pref cache.
     *
     * In a hooked system process (e.g. com.android.phone) every public getter here
     * ultimately reads a remote SharedPreferences owned by the module app process over
     * Binder. On the per-SMS parse path several of these fire (keywords, toggles, ...),
     * and when the module process is frozen (NoActive) each read blocks for tens of
     * seconds. Caching the resolved value for a short window collapses the repeated IPC
     * to at most one read per key per window while staying fresh enough that a settings
     * change becomes visible a few seconds later — there is no live pref listener in the
     * hook process (mirrors XposedRuntimeInstaller.SANITIZER_SYNC_TTL_MS). Kept shorter
     * than the rule cache TTL because toggles are cheap to re-read and users expect
     * config changes to apply promptly.
     */
    private const val PREFS_CACHE_TTL_MS = 5_000L
    private val prefsResolver = PrefsResolver(
        cacheTtlMs = PREFS_CACHE_TTL_MS,
        clock = { android.os.SystemClock.elapsedRealtime() },
        missFallsThrough = false,
    )

    @Volatile
    private var remotePrefsProvider: (() -> SharedPreferences?)? = null
    @Volatile
    private var hookContext: Context? = null
    @Volatile
    private var remoteProviderLogged = false
    private val remoteTraceLoggedKeys = Collections.synchronizedSet(mutableSetOf<String>())
    private data class BooleanReadTrace(val value: Boolean, val source: String)
    private data class StringReadTrace(val value: String, val source: String)
    private val remotePrefsSource = SharedPrefsSource(
        sourceName = SOURCE_REMOTE_PROVIDER,
        provider = ::getRemotePrefs,
        onError = ::logPrefsSourceError,
    )
    private val localPrefsSource = SharedPrefsSource(
        sourceName = SOURCE_LOCAL_HOOK_PREFS,
        provider = ::getLocalPrefs,
        onError = ::logPrefsSourceError,
    )

    @JvmStatic
    fun setRemotePrefsProvider(provider: (() -> SharedPreferences?)?) {
        remotePrefsProvider = provider
        remoteProviderLogged = false
        prefsResolver.invalidate()
    }

    @JvmStatic
    fun setHookContext(context: Context) {
        hookContext = context.applicationContext ?: context
    }

    /**
     * Drops all cached pref values so the next read hits the backing store. Wire this to
     * a settings-change signal in the hook process to apply config immediately instead of
     * waiting out [PREFS_CACHE_TTL_MS]; otherwise the TTL alone bounds staleness.
     */
    @JvmStatic
    fun invalidateCache() {
        prefsResolver.invalidate()
    }

    private fun getRemotePrefs(): SharedPreferences? {
        val provider = remotePrefsProvider ?: return null
        return runCatching { provider.invoke() }.getOrElse { t ->
            if (!remoteProviderLogged) {
                remoteProviderLogged = true
                XLog.w("PrefsReader: remote prefs provider failed", t)
            }
            null
        }
    }

    private fun getLocalPrefs(): SharedPreferences? {
        val ctx = hookContext ?: return null
        return runCatching { ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }.getOrNull()
    }

    private fun prefSources(): List<PrefsSource> = listOf(remotePrefsSource, localPrefsSource)

    private fun logRemoteTraceOnce(key: String, state: String) {
        if (remoteTraceLoggedKeys.add(key)) {
            XLog.w("Diag remote prefs: key=%s state=%s", key, state)
        }
    }

    private fun logPrefsSourceError(message: String, error: Throwable) {
        XLog.w("PrefsReader: %s", message, error)
    }

    private fun resolveBooleanPref(key: String, defaultValue: Boolean): PrefReadResult<Boolean> =
        prefsResolver.resolveBoolean(key, defaultValue, prefSources())

    private fun resolveStringPref(key: String, defaultValue: String): PrefReadResult<String> =
        prefsResolver.resolveString(key, defaultValue, prefSources())

    private fun resolveIntPref(key: String, defaultValue: Int): PrefReadResult<Int> =
        prefsResolver.resolveInt(key, defaultValue, prefSources())

    private fun traceState(result: PrefReadResult<*>): String =
        if (result.source == PrefSources.SOURCE_DEFAULT) "default" else "hit"

    private fun getBooleanViaProvider(key: String, defaultValue: Boolean): Boolean {
        return resolveBooleanPref(key, defaultValue).value
    }

    private fun readBooleanWithTrace(key: String, defaultValue: Boolean): BooleanReadTrace {
        val result = resolveBooleanPref(key, defaultValue)
        logRemoteTraceOnce("bool:$key", traceState(result))
        return BooleanReadTrace(
            value = result.value,
            source = result.source,
        )
    }

    private fun getStringViaProvider(key: String, defaultValue: String): String {
        return resolveStringPref(key, defaultValue).value
    }

    private fun readStringWithTrace(key: String, defaultValue: String): StringReadTrace {
        val result = resolveStringPref(key, defaultValue)
        logRemoteTraceOnce("string:$key", traceState(result))
        return StringReadTrace(
            value = result.value,
            source = result.source,
        )
    }

    private fun getIntViaProvider(key: String, defaultValue: Int): Int {
        return resolveIntPref(key, defaultValue).value
    }

    @JvmStatic
    fun getBooleanPreference(key: String, defaultValue: Boolean): Boolean =
        getBooleanViaProvider(key, defaultValue)

    @JvmStatic
    fun getStringPreference(key: String, defaultValue: String): String =
        getStringViaProvider(key, defaultValue)

    @JvmStatic
    fun getIntPreference(key: String, defaultValue: Int): Int =
        getIntViaProvider(key, defaultValue)

    @JvmStatic
    fun isEnabled(context: Context): Boolean {
        val defaultValue = true
        return getBooleanViaProvider(PrefConst.KEY_ENABLE, defaultValue)
    }

    @JvmStatic
    fun isVerboseLogMode(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(PrefConst.KEY_VERBOSE_LOG_MODE, defaultValue)
    }

    @JvmStatic
    fun isSensitiveDebugLogSupported(): Boolean = true

    @JvmStatic
    fun isSensitiveDebugLogMode(context: Context): Boolean {
        if (!isSensitiveDebugLogSupported()) return false
        return getBooleanViaProvider(PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE, false)
    }

    @JvmStatic
    fun isAnalyticsEnabled(context: Context): Boolean {
        return getBooleanViaProvider(PrefConst.KEY_ENABLE_ANALYTICS, true)
    }

    @JvmStatic
    fun autoInputCodeEnabled(context: Context): Boolean {
        val defaultValue = true
        return getBooleanViaProvider(PrefConst.KEY_ENABLE_AUTO_INPUT_CODE, defaultValue)
    }

    @JvmStatic
    fun autoEnterCodeEnabled(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(PrefConst.KEY_ENABLE_AUTO_ENTER_CODE, defaultValue)
    }

    @JvmStatic
    fun getAutoInputCodeDelay(context: Context): Long {
        val value = getStringViaProvider(
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY,
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT,
        )
        return try {
            value.toLong()
        } catch (ignored: Exception) {
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT.toLong()
        }
    }

    @JvmStatic
    fun getAutoInputCodeIntervalMs(context: Context): Long {
        val value = getStringViaProvider(
            PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL,
            PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL_DEFAULT,
        )
        return try {
            value.toLong().coerceAtLeast(0L)
        } catch (ignored: Exception) {
            PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL_DEFAULT.toLong()
        }
    }

    @JvmStatic
    fun shouldShowToast(context: Context): Boolean {
        val defaultValue = true
        return getBooleanViaProvider(PrefConst.KEY_SHOW_TOAST, defaultValue)
    }

    @JvmStatic
    fun getSMSCodeKeywords(context: Context): String? = getStringViaProvider(
        PrefConst.KEY_SMSCODE_KEYWORDS,
        PrefConst.SMSCODE_KEYWORDS_DEFAULT,
    )

    @JvmStatic
    fun markAsReadEnabled(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(PrefConst.KEY_MARK_AS_READ, defaultValue)
    }

    @JvmStatic
    fun deleteSmsEnabled(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(PrefConst.KEY_DELETE_SMS, defaultValue)
    }

    @JvmStatic
    fun copyToClipboardEnabled(context: Context): Boolean {
        val defaultValue = false
        val trace = readBooleanWithTrace(PrefConst.KEY_COPY_TO_CLIPBOARD, defaultValue)
        XLog.w(
            "Diag pref copy_to_clipboard: value=%s source=%s default=%s",
            trace.value,
            trace.source,
            defaultValue,
        )
        return trace.value
    }

    @JvmStatic
    fun recordSmsCodeEnabled(context: Context): Boolean {
        return recordCodeSmsEnabled(context)
    }

    @JvmStatic
    fun recordCodeSmsEnabled(context: Context): Boolean {
        return getBooleanViaProvider(PrefConst.KEY_ENABLE_CODE_RECORDS_CODE, true)
    }

    @JvmStatic
    fun recordPlainSmsEnabled(context: Context): Boolean {
        return getBooleanViaProvider(PrefConst.KEY_ENABLE_CODE_RECORDS_PLAIN_SMS, true)
    }

    @JvmStatic
    fun recordAppNotifyEnabled(context: Context): Boolean {
        return getBooleanViaProvider(PrefConst.KEY_ENABLE_CODE_RECORDS_APP_NOTIFY, true)
    }

    @JvmStatic
    fun recordCallNotifyEnabled(context: Context): Boolean {
        return getBooleanViaProvider(PrefConst.KEY_ENABLE_CODE_RECORDS_CALL_NOTIFY, true)
    }

    @JvmStatic
    fun blockSmsEnabled(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(PrefConst.KEY_BLOCK_SMS, defaultValue)
    }

    @JvmStatic
    fun killMeEnabled(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(PrefConst.KEY_KILL_ME, defaultValue)
    }

    @JvmStatic
    fun showCodeNotification(context: Context): Boolean {
        val defaultValue = true
        return getBooleanViaProvider(PrefConst.KEY_SHOW_CODE_NOTIFICATION, defaultValue)
    }

    @JvmStatic
    fun getCodeNotificationOwner(context: Context): String {
        val value = getStringViaProvider(PrefConst.KEY_CODE_NOTIFICATION_OWNER, CodeNotificationOwner.DEFAULT)
        return CodeNotificationOwner.normalize(value)
    }

    @JvmStatic
    fun autoCancelCodeNotification(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION, defaultValue)
    }

    @JvmStatic
    fun getNotificationRetentionTime(context: Context): Int {
        val value = getStringViaProvider(
            PrefConst.KEY_NOTIFICATION_RETENTION_TIME,
            PrefConst.NOTIFICATION_RETENTION_TIME_DEFAULT,
        )
        return try {
            value.toInt()
        } catch (ignored: Exception) {
            0
        }
    }

    @JvmStatic
    fun deduplicateSms(context: Context): Boolean {
        val defaultValue = true
        return getBooleanViaProvider(PrefConst.KEY_DEDUPLICATE_SMS, defaultValue)
    }

    @JvmStatic
    fun smsBlacklistEnabled(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(PrefConst.KEY_ENABLE_SMS_BLACKLIST, defaultValue)
    }

    @JvmStatic
    fun smsBlacklistNumbers(context: Context): String =
        getStringViaProvider(PrefConst.KEY_SMS_BLACKLIST_NUMBERS, "")

    @JvmStatic
    fun smsBlacklistPrefixes(context: Context): String =
        getStringViaProvider(PrefConst.KEY_SMS_BLACKLIST_PREFIXES, "")

    @JvmStatic
    fun smsBlacklistRegex(context: Context): String =
        getStringViaProvider(PrefConst.KEY_SMS_BLACKLIST_REGEX, "")

    @JvmStatic
    fun smsBlacklistContent(context: Context): String =
        getStringViaProvider(PrefConst.KEY_SMS_BLACKLIST_CONTENT, "")

    @JvmStatic
    fun smsBlacklistActionDelete(context: Context): Boolean {
        val defaultValue = true
        return getBooleanViaProvider(PrefConst.KEY_SMS_BLACKLIST_ACTION_DELETE, defaultValue)
    }

    @JvmStatic
    fun smsBlacklistActionBlock(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(PrefConst.KEY_SMS_BLACKLIST_ACTION_BLOCK, defaultValue)
    }

    @JvmStatic
    fun getHistoryLimit(context: Context): Int {
        return getCodeHistoryLimit(context)
    }

    @JvmStatic
    fun getCodeHistoryLimit(context: Context): Int {
        return getHistoryLimitByKey(
            context = context,
            key = PrefConst.KEY_HISTORY_LIMIT_CODE,
        )
    }

    @JvmStatic
    fun getPlainSmsHistoryLimit(context: Context): Int {
        return getHistoryLimitByKey(
            context = context,
            key = PrefConst.KEY_HISTORY_LIMIT_PLAIN_SMS,
        )
    }

    @JvmStatic
    fun getAppNotifyHistoryLimit(context: Context): Int {
        return getHistoryLimitByKey(
            context = context,
            key = PrefConst.KEY_HISTORY_LIMIT_APP_NOTIFY,
        )
    }

    @JvmStatic
    fun getCallNotifyHistoryLimit(context: Context): Int {
        return getHistoryLimitByKey(
            context = context,
            key = PrefConst.KEY_HISTORY_LIMIT_CALL_NOTIFY,
        )
    }

    @JvmStatic
    fun getHistoryLimit(context: Context, msgType: Int, isCodeSms: Boolean): Int {
        return when (msgType) {
            SmsMsg.MSG_TYPE_APP_NOTIFY -> getAppNotifyHistoryLimit(context)
            SmsMsg.MSG_TYPE_CALL_NOTIFY -> getCallNotifyHistoryLimit(context)
            SmsMsg.MSG_TYPE_SMS -> if (isCodeSms) getCodeHistoryLimit(context) else getPlainSmsHistoryLimit(context)
            else -> getCodeHistoryLimit(context)
        }
    }

    private fun getHistoryLimitByKey(context: Context, key: String): Int {
        val value = getStringViaProvider(key, "0")
        return try {
            value.toInt()
        } catch (ignored: Exception) {
            0
        }
    }

    @JvmStatic
    fun getIpcToken(context: Context): String {
        val trace = readStringWithTrace(PrefConst.KEY_IPC_TOKEN, "")
        if (trace.value.isBlank() || trace.source != "provider") {
            XLog.w(
                "Diag pref ipc_token: blank=%s source=%s",
                trace.value.isBlank(),
                trace.source,
            )
        }
        return trace.value
    }

    @JvmStatic
    fun getSimSlotRemark(context: Context, simSlot: Int): String {
        val key = when (simSlot) {
            0 -> PrefConst.KEY_SIM_SLOT1_REMARK
            1 -> PrefConst.KEY_SIM_SLOT2_REMARK
            else -> return ""
        }
        return getStringViaProvider(key, "").trim()
    }
}
