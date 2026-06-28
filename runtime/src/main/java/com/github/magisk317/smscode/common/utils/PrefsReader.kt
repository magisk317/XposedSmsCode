package com.github.magisk317.smscode.common.utils

import android.content.Context
import android.content.SharedPreferences
import com.github.magisk317.smscode.common.constant.CodeNotificationOwner
import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.data.db.entity.SmsMsg

import com.github.magisk317.smscode.common.utils.XLog
import java.util.Collections

object PrefsReader {
    private const val PREFS_NAME = "xposed_prefs"
    @Volatile
    private var remotePrefsProvider: (() -> SharedPreferences?)? = null
    @Volatile
    private var hookContext: Context? = null
    @Volatile
    private var remoteProviderLogged = false
    private val remoteTraceLoggedKeys = Collections.synchronizedSet(mutableSetOf<String>())
    private data class BooleanReadTrace(val value: Boolean, val source: String)
    private data class StringReadTrace(val value: String, val source: String)
    @JvmStatic
    fun setRemotePrefsProvider(provider: (() -> SharedPreferences?)?) {
        remotePrefsProvider = provider
        remoteProviderLogged = false
    }

    @JvmStatic
    fun setHookContext(context: Context) {
        hookContext = context.applicationContext ?: context
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

    private fun getAnyPrefs(): SharedPreferences? {
        return getRemotePrefs() ?: getLocalPrefs()
    }


    private fun logRemoteTraceOnce(key: String, state: String) {
        if (remoteTraceLoggedKeys.add(key)) {
            XLog.w("Diag remote prefs: key=%s state=%s", key, state)
        }
    }

    private fun getBooleanViaRemote(key: String, defaultValue: Boolean): Boolean? {
        val prefs = getAnyPrefs() ?: return null
        return try {
            if (!prefs.contains(key)) return null
            prefs.getBoolean(key, defaultValue)
        } catch (t: Throwable) {
            XLog.w("PrefsReader: prefs boolean '%s' failed", key, t)
            null
        }
    }

    private fun getStringViaRemote(key: String, defaultValue: String): String? {
        val prefs = getAnyPrefs() ?: return null
        return try {
            if (!prefs.contains(key)) return null
            prefs.getString(key, defaultValue) ?: defaultValue
        } catch (t: Throwable) {
            XLog.w("PrefsReader: prefs string '%s' failed", key, t)
            null
        }
    }

    private fun getIntViaRemote(key: String, defaultValue: Int): Int? {
        val prefs = getAnyPrefs() ?: return null
        return try {
            if (!prefs.contains(key)) return null
            when (val any = prefs.all[key]) {
                is Int -> any
                is Long -> any.toInt()
                is String -> any.toIntOrNull() ?: defaultValue
                else -> prefs.getInt(key, defaultValue)
            }
        } catch (t: Throwable) {
            XLog.w("PrefsReader: prefs int '%s' failed", key, t)
            null
        }
    }

    private fun getBooleanViaProvider(key: String, defaultValue: Boolean): Boolean {
        return getBooleanViaRemote(key, defaultValue) ?: defaultValue
    }

    private fun readBooleanWithTrace(key: String, defaultValue: Boolean): BooleanReadTrace {
        val prefs = getAnyPrefs()
        val state = when {
            prefs == null -> "unavailable"
            prefs.contains(key) -> "hit"
            else -> "miss"
        }
        logRemoteTraceOnce("bool:$key", state)
        if (prefs != null) {
            try {
                if (prefs.contains(key)) {
                    return BooleanReadTrace(
                        value = prefs.getBoolean(key, defaultValue),
                        source = "prefs",
                    )
                }
            } catch (t: Throwable) {
                XLog.w("PrefsReader: prefs boolean '%s' failed", key, t)
            }
        }
        return BooleanReadTrace(
            value = defaultValue,
            source = "default",
        )
    }

    private fun getStringViaProvider(key: String, defaultValue: String): String {
        return getStringViaRemote(key, defaultValue) ?: defaultValue
    }

    private fun readStringWithTrace(key: String, defaultValue: String): StringReadTrace {
        val remotePrefs = getAnyPrefs()
        val remoteState = when {
            remotePrefs == null -> "unavailable"
            remotePrefs.contains(key) -> "hit"
            else -> "miss"
        }
        logRemoteTraceOnce("string:$key", remoteState)
        if (remotePrefs != null) {
            try {
                if (remotePrefs.contains(key)) {
                    return StringReadTrace(
                        value = remotePrefs.getString(key, defaultValue) ?: defaultValue,
                        source = "remote",
                    )
                }
            } catch (t: Throwable) {
                XLog.w("PrefsReader: remote prefs string '%s' failed", key, t)
            }
        }
        return StringReadTrace(
            value = defaultValue,
            source = "default",
        )
    }

    private fun getIntViaProvider(key: String, defaultValue: Int): Int {
        return getIntViaRemote(key, defaultValue) ?: defaultValue
    }

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
        val result = getBooleanViaProvider(PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE, false)
        android.util.Log.w("XSmsCode", "isSensitiveDebugLogMode: result=$result remotePrefsProvider=${remotePrefsProvider != null}")
        return result
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
