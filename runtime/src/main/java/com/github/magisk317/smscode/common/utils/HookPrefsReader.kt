package com.github.magisk317.smscode.common.utils

import android.content.Context
import android.content.SharedPreferences
import com.github.magisk317.smscode.runtime.BuildConfig
import com.github.magisk317.smscode.common.constant.CodeNotificationOwner
import com.github.magisk317.smscode.common.constant.PrefConst
import com.magisk317.mobile.entitlement.MobileEntitlementGate
import com.magisk317.mobile.entitlement.MobileEntitlementVerificationPolicy
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.runtime.bridge.HookPrefsAccess
import io.github.magisk317.smscode.runtime.contract.prefs.PrefRead
import io.github.magisk317.smscode.runtime.contract.prefs.PrefReadResult
import io.github.magisk317.smscode.runtime.contract.prefs.PrefSources
import io.github.magisk317.smscode.runtime.contract.prefs.PrefsSource
import io.github.magisk317.smscode.runtime.common.prefs.PrefsResolver
import io.github.magisk317.xposed.preferences.PreferenceRead
import io.github.magisk317.xposed.preferences.PreferenceSource
import io.github.magisk317.xposed.preferences.SharedPreferencesSource
import java.util.Collections

/**
 * Hook-process preference reader.
 *
 * The hook process has no local preference source. Every value comes from the
 * libxposed remote-preferences Provider, with the method default used only when
 * that Provider is unavailable.
 */
object HookPrefsReader : HookPrefsAccess {
    private const val MISSING_LONG_VALUE = "-9223372036854775808"
    private const val SOURCE_REMOTE_PROVIDER = "provider"

    /**
     * A non-zero cache would be unsafe here: the provider can disappear without a callback when the
     * module process is frozen or the remote binding is removed. The shared resolver retains stale
     * hits for unavailable sources, so this parent must re-resolve every read instead of serving an
     * old hook configuration after source failure.
     */
    private const val PREFS_CACHE_TTL_MS = 0L
    private val prefsResolver = PrefsResolver(
        cacheTtlMs = PREFS_CACHE_TTL_MS,
        clock = { android.os.SystemClock.elapsedRealtime() },
        missFallsThrough = false,
    )

    @Volatile
    private var remotePrefsProvider: (() -> SharedPreferences?)? = null
    @Volatile
    private var remoteProviderLogged = false
    private val remoteTraceLoggedKeys = Collections.synchronizedSet(mutableSetOf<String>())
    private data class BooleanReadTrace(val value: Boolean, val source: String)
    private data class StringReadTrace(val value: String, val source: String)
    private val remotePrefsSource: PrefsSource = KitPreferenceSourceAdapter(
        SharedPreferencesSource(
            sourceName = SOURCE_REMOTE_PROVIDER,
            provider = ::getRemotePrefs,
            onError = ::logPrefsSourceError,
        ),
    )

    private val mobileEntitlementPolicy
        get() = MobileEntitlementVerificationPolicy(
            signingPublicJwk = BuildConfig.MOBILE_ENTITLEMENT_SIGNING_PUBLIC_JWK,
            issuer = BuildConfig.MOBILE_ENTITLEMENT_API_ORIGIN,
            appId = "xposed-sms-code",
            enforced = true,
        )

    @JvmStatic
    fun setRemotePrefsProvider(provider: (() -> SharedPreferences?)?) {
        remotePrefsProvider = provider
        remoteProviderLogged = false
        prefsResolver.invalidate()
    }


    /**
     * Drops the resolver state so the next read hits the backing store. Public reads also invalidate
     * immediately before resolving because remote binding loss has no callback in the hook process.
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
                XLog.w("HookPrefsReader: remote prefs provider failed", t)
            }
            null
        }
    }

    private fun prefSources(): List<PrefsSource> = listOf(remotePrefsSource)

    private fun logRemoteTraceOnce(key: String, state: String) {
        if (remoteTraceLoggedKeys.add(key)) {
            XLog.w("Diag remote prefs: key=%s state=%s", key, state)
        }
    }

    private fun logPrefsSourceError(message: String, error: Throwable) {
        XLog.w("HookPrefsReader: %s", message, error)
    }

    private fun resolveBooleanPref(key: String, defaultValue: Boolean): PrefReadResult<Boolean> {
        prefsResolver.invalidate()
        return prefsResolver.resolveBoolean(key, defaultValue, prefSources())
    }

    private fun resolveStringPref(key: String, defaultValue: String): PrefReadResult<String> {
        prefsResolver.invalidate()
        return prefsResolver.resolveString(key, defaultValue, prefSources())
    }

    private fun resolveIntPref(key: String, defaultValue: Int): PrefReadResult<Int> {
        prefsResolver.invalidate()
        return prefsResolver.resolveInt(key, defaultValue, prefSources())
    }

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

    override fun isEnabled(context: Context): Boolean {
        val defaultValue = true
        return getBooleanViaProvider(PrefConst.KEY_ENABLE, defaultValue)
    }

    override fun mobileAutomationAllowed(context: Context): Boolean {
        val state = MobileEntitlementGate.read(getRemotePrefs())
        return MobileEntitlementGate.isAllowed(state, mobileEntitlementPolicy)
    }

    override fun isVerboseLogMode(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(PrefConst.KEY_VERBOSE_LOG_MODE, defaultValue)
    }

    override fun isSensitiveDebugLogMode(context: Context): Boolean {
        return getBooleanViaProvider(PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE, false)
    }

    @JvmStatic
    fun isAnalyticsEnabled(context: Context): Boolean {
        return getBooleanViaProvider(PrefConst.KEY_ENABLE_ANALYTICS, true)
    }

    override fun autoInputCodeEnabled(context: Context): Boolean {
        val defaultValue = true
        return getBooleanViaProvider(PrefConst.KEY_ENABLE_AUTO_INPUT_CODE, defaultValue)
    }

    @JvmStatic
    fun autoEnterCodeEnabled(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(PrefConst.KEY_ENABLE_AUTO_ENTER_CODE, defaultValue)
    }

    override fun getAutoInputCodeDelay(context: Context): Long {
        val value = getStringViaProvider(
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY,
            MISSING_LONG_VALUE,
        )
        value.toLongOrNull()?.takeUnless { it == Long.MIN_VALUE }?.let { return it.coerceAtLeast(0L) }

        return getStringViaProvider(
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY_LEGACY,
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT,
        ).toLongOrNull()?.coerceAtLeast(0L)?.times(1000L)
            ?: PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT.toLong()
    }

    override fun getAutoInputCodeIntervalMs(context: Context): Long {
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

    override fun shouldShowToast(context: Context): Boolean {
        val defaultValue = true
        return getBooleanViaProvider(PrefConst.KEY_SHOW_TOAST, defaultValue)
    }

    @JvmStatic
    fun getSMSCodeKeywords(context: Context): String? = getStringViaProvider(
        PrefConst.KEY_SMSCODE_KEYWORDS,
        PrefConst.SMSCODE_KEYWORDS_DEFAULT,
    )

    override fun markAsReadEnabled(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(PrefConst.KEY_MARK_AS_READ, defaultValue)
    }

    override fun deleteSmsEnabled(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(PrefConst.KEY_DELETE_SMS, defaultValue)
    }

    override fun copyToClipboardEnabled(context: Context): Boolean {
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

    override fun recordCodeSmsEnabled(context: Context): Boolean {
        return getBooleanViaProvider(PrefConst.KEY_ENABLE_CODE_RECORDS_CODE, true)
    }

    @JvmStatic
    fun recordPlainSmsEnabled(context: Context): Boolean {
        return getBooleanViaProvider(PrefConst.KEY_ENABLE_CODE_RECORDS_PLAIN_SMS, true)
    }

    override fun recordAppNotifyEnabled(context: Context): Boolean {
        return getBooleanViaProvider(PrefConst.KEY_ENABLE_CODE_RECORDS_APP_NOTIFY, true)
    }

    override fun recordCallNotifyEnabled(context: Context): Boolean {
        return getBooleanViaProvider(PrefConst.KEY_ENABLE_CODE_RECORDS_CALL_NOTIFY, true)
    }

    override fun blockSmsEnabled(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(PrefConst.KEY_BLOCK_SMS, defaultValue)
    }

    override fun killMeEnabled(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(PrefConst.KEY_KILL_ME, defaultValue)
    }

    override fun showCodeNotification(context: Context): Boolean {
        val defaultValue = true
        return getBooleanViaProvider(PrefConst.KEY_SHOW_CODE_NOTIFICATION, defaultValue)
    }

    @JvmStatic
    fun getCodeNotificationOwner(context: Context): String {
        val value = getStringViaProvider(PrefConst.KEY_CODE_NOTIFICATION_OWNER, CodeNotificationOwner.DEFAULT)
        return CodeNotificationOwner.normalize(value)
    }

    override fun autoCancelCodeNotification(context: Context): Boolean {
        val defaultValue = false
        return getBooleanViaProvider(PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION, defaultValue)
    }

    override fun getNotificationRetentionTime(context: Context): Int {
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

    override fun deduplicateSms(context: Context): Boolean {
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

    override fun getHistoryLimit(context: Context): Int {
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

    override fun getHistoryLimit(context: Context, msgType: Int, isCodeSms: Boolean): Int {
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

    override fun getIpcToken(context: Context): String {
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

    private class KitPreferenceSourceAdapter(
        private val delegate: PreferenceSource,
    ) : PrefsSource {
        override val sourceName: String
            get() = delegate.sourceName

        override fun readBoolean(key: String, defaultValue: Boolean): PrefRead<Boolean> =
            delegate.readBoolean(key, defaultValue).toCoreResult()

        override fun readString(key: String, defaultValue: String): PrefRead<String> =
            delegate.readString(key, defaultValue).toCoreResult()

        override fun readInt(key: String, defaultValue: Int): PrefRead<Int> =
            delegate.readInt(key, defaultValue).toCoreResult()

        private fun <T> PreferenceRead<T>.toCoreResult(): PrefRead<T> = when (this) {
            is PreferenceRead.Hit -> PrefRead.Hit(value, source)
            PreferenceRead.Missing -> PrefRead.Miss
            PreferenceRead.Unavailable -> PrefRead.Unavailable
        }
    }
}
