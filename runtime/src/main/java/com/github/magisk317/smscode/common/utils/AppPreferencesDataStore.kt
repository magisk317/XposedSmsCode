package com.github.magisk317.smscode.common.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.github.magisk317.smscode.common.constant.CodeNotificationOwner
import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.common.utils.XLog
import io.github.magisk317.smscode.runtime.contract.prefs.PreferenceChange
import io.github.magisk317.smscode.runtime.contract.prefs.PreferenceChangeSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

object AppPreferencesDataStore {
    private val backupCompatTipShownKey = booleanPreferencesKey(PrefConst.KEY_BACKUP_COMPAT_TIP_SHOWN)
    private const val DATASTORE_FILE_NAME = "app_preferences.preferences_pb"

    @Volatile
    private var INSTANCE: DataStore<Preferences>? = null

    private fun getInstance(context: Context): DataStore<Preferences> {
        INSTANCE?.let { return it }
        return synchronized(this) {
            INSTANCE ?: PreferenceDataStoreFactory.create {
                // In Xposed/createPackageContext scenarios applicationContext may be null.
                val safeContext = context.applicationContext ?: context
                File(safeContext.dataDir, "datastore/$DATASTORE_FILE_NAME")
            }.also { INSTANCE = it }
        }
    }

    @Volatile
    private var remotePrefsProvider: (() -> SharedPreferences?)? = null
    @Volatile
    private var remoteProviderLogged = false

    fun setRemotePrefsProvider(provider: (() -> SharedPreferences?)?) {
        remotePrefsProvider = provider
        remoteProviderLogged = false
    }

    private fun getRemotePrefs(): SharedPreferences? {
        val provider = remotePrefsProvider ?: return null
        return runCatching { provider.invoke() }.getOrElse { t ->
            if (!remoteProviderLogged) {
                remoteProviderLogged = true
                XLog.w("RemotePrefs provider failed: %s", t.message ?: t.javaClass.simpleName)
            }
            null
        }
    }

    private suspend fun populateRemoteEditor(context: Context, editor: SharedPreferences.Editor) {
        editor.putString(
            io.github.magisk317.xposed.logging.AnonymousInstallationId.PREFERENCE_KEY,
            getString(
                context,
                io.github.magisk317.xposed.logging.AnonymousInstallationId.PREFERENCE_KEY,
                "",
            ),
        )
        editor.putBoolean(PrefConst.KEY_ENABLE, getBoolean(context, PrefConst.KEY_ENABLE, true))
        editor.putBoolean(
            PrefConst.KEY_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED,
            getBoolean(
                context,
                PrefConst.KEY_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED,
                PrefConst.DEFAULT_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED,
            ),
        )
        editor.putString(
            PrefConst.KEY_MOBILE_ENTITLEMENT_TOKEN,
            getString(context, PrefConst.KEY_MOBILE_ENTITLEMENT_TOKEN, ""),
        )
        editor.putBoolean(
            PrefConst.KEY_SETTINGS_ACCORDION_MODE,
            getBoolean(context, PrefConst.KEY_SETTINGS_ACCORDION_MODE, true),
        )
        editor.putBoolean(PrefConst.KEY_VERBOSE_LOG_MODE, getBoolean(context, PrefConst.KEY_VERBOSE_LOG_MODE, false))
        editor.putInt(
            PrefConst.KEY_RUNTIME_LOG_RETENTION_DAYS,
            getInt(
                context,
                PrefConst.KEY_RUNTIME_LOG_RETENTION_DAYS,
                PrefConst.RUNTIME_LOG_RETENTION_DAYS_DEFAULT,
            ).coerceAtLeast(PrefConst.RUNTIME_LOG_RETENTION_DAYS_MIN),
        )
        editor.putBoolean(
            PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE,
            getBoolean(context, PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE, false),
        )
        editor.putBoolean(
            PrefConst.KEY_ENABLE_AUTO_INPUT_CODE,
            getBoolean(context, PrefConst.KEY_ENABLE_AUTO_INPUT_CODE, true),
        )
        editor.putString(
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY,
            getString(context, PrefConst.KEY_AUTO_INPUT_CODE_DELAY, PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT),
        )
        editor.putString(
            PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL,
            getString(
                context,
                PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL,
                PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL_DEFAULT,
            ),
        )
        editor.putBoolean(PrefConst.KEY_SHOW_TOAST, getBoolean(context, PrefConst.KEY_SHOW_TOAST, true))
        editor.putString(
            PrefConst.KEY_SMSCODE_KEYWORDS,
            getString(context, PrefConst.KEY_SMSCODE_KEYWORDS, PrefConst.SMSCODE_KEYWORDS_DEFAULT),
        )
        editor.putBoolean(PrefConst.KEY_MARK_AS_READ, getBoolean(context, PrefConst.KEY_MARK_AS_READ, false))
        editor.putBoolean(PrefConst.KEY_DELETE_SMS, getBoolean(context, PrefConst.KEY_DELETE_SMS, false))
        editor.putBoolean(PrefConst.KEY_COPY_TO_CLIPBOARD, getBoolean(context, PrefConst.KEY_COPY_TO_CLIPBOARD, false))
        editor.putBoolean(
            PrefConst.KEY_ENABLE_CODE_RECORDS_CODE,
            getBoolean(context, PrefConst.KEY_ENABLE_CODE_RECORDS_CODE, true),
        )
        editor.putBoolean(
            PrefConst.KEY_ENABLE_CODE_RECORDS_PLAIN_SMS,
            getBoolean(context, PrefConst.KEY_ENABLE_CODE_RECORDS_PLAIN_SMS, true),
        )
        editor.putBoolean(
            PrefConst.KEY_ENABLE_CODE_RECORDS_APP_NOTIFY,
            getBoolean(context, PrefConst.KEY_ENABLE_CODE_RECORDS_APP_NOTIFY, true),
        )
        editor.putBoolean(
            PrefConst.KEY_ENABLE_CODE_RECORDS_CALL_NOTIFY,
            getBoolean(context, PrefConst.KEY_ENABLE_CODE_RECORDS_CALL_NOTIFY, true),
        )
        editor.putBoolean(PrefConst.KEY_BLOCK_SMS, getBoolean(context, PrefConst.KEY_BLOCK_SMS, false))
        editor.putBoolean(PrefConst.KEY_KILL_ME, getBoolean(context, PrefConst.KEY_KILL_ME, false))
        editor.putBoolean(
            PrefConst.KEY_SHOW_CODE_NOTIFICATION,
            getBoolean(context, PrefConst.KEY_SHOW_CODE_NOTIFICATION, true),
        )
        editor.putString(
            PrefConst.KEY_CODE_NOTIFICATION_OWNER,
            CodeNotificationOwner.normalize(
                getString(context, PrefConst.KEY_CODE_NOTIFICATION_OWNER, CodeNotificationOwner.DEFAULT),
            ),
        )
        editor.putBoolean(
            PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION,
            getBoolean(context, PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION, false),
        )
        editor.putString(
            PrefConst.KEY_NOTIFICATION_RETENTION_TIME,
            getString(
                context,
                PrefConst.KEY_NOTIFICATION_RETENTION_TIME,
                PrefConst.NOTIFICATION_RETENTION_TIME_DEFAULT,
            ),
        )
        editor.putBoolean(PrefConst.KEY_DEDUPLICATE_SMS, getBoolean(context, PrefConst.KEY_DEDUPLICATE_SMS, true))
        editor.putBoolean(
            PrefConst.KEY_ENABLE_SMS_BLACKLIST,
            getBoolean(context, PrefConst.KEY_ENABLE_SMS_BLACKLIST, false),
        )
        editor.putString(
            PrefConst.KEY_SMS_BLACKLIST_NUMBERS,
            getString(context, PrefConst.KEY_SMS_BLACKLIST_NUMBERS, ""),
        )
        editor.putString(
            PrefConst.KEY_SMS_BLACKLIST_PREFIXES,
            getString(context, PrefConst.KEY_SMS_BLACKLIST_PREFIXES, ""),
        )
        editor.putString(
            PrefConst.KEY_SMS_BLACKLIST_REGEX,
            getString(context, PrefConst.KEY_SMS_BLACKLIST_REGEX, ""),
        )
        editor.putString(
            PrefConst.KEY_SMS_BLACKLIST_CONTENT,
            getString(context, PrefConst.KEY_SMS_BLACKLIST_CONTENT, ""),
        )
        editor.putBoolean(
            PrefConst.KEY_SMS_BLACKLIST_ACTION_DELETE,
            getBoolean(context, PrefConst.KEY_SMS_BLACKLIST_ACTION_DELETE, true),
        )
        editor.putBoolean(
            PrefConst.KEY_SMS_BLACKLIST_ACTION_BLOCK,
            getBoolean(context, PrefConst.KEY_SMS_BLACKLIST_ACTION_BLOCK, false),
        )
        editor.putString(
            PrefConst.KEY_HISTORY_LIMIT_CODE,
            getString(context, PrefConst.KEY_HISTORY_LIMIT_CODE, "0"),
        )
        editor.putString(
            PrefConst.KEY_HISTORY_LIMIT_PLAIN_SMS,
            getString(context, PrefConst.KEY_HISTORY_LIMIT_PLAIN_SMS, "0"),
        )
        editor.putString(
            PrefConst.KEY_HISTORY_LIMIT_APP_NOTIFY,
            getString(context, PrefConst.KEY_HISTORY_LIMIT_APP_NOTIFY, "0"),
        )
        editor.putString(
            PrefConst.KEY_HISTORY_LIMIT_CALL_NOTIFY,
            getString(
                context,
                PrefConst.KEY_HISTORY_LIMIT_CALL_NOTIFY,
                "20",
            ),
        )
        editor.putBoolean(
            PrefConst.KEY_AUTO_UPDATE_ON_START,
            getBoolean(context, PrefConst.KEY_AUTO_UPDATE_ON_START, true),
        )
        editor.putBoolean(
            PrefConst.KEY_AUTO_UPDATE_WIFI_ONLY,
            getBoolean(context, PrefConst.KEY_AUTO_UPDATE_WIFI_ONLY, false),
        )
        editor.putInt(
            PrefConst.KEY_CHOOSE_THEME,
            getInt(context, PrefConst.KEY_CHOOSE_THEME, 0),
        )
        editor.putInt(
            PrefConst.KEY_UI_KIT_STYLE,
            getInt(context, PrefConst.KEY_UI_KIT_STYLE, 0),
        )
        editor.putBoolean(
            PrefConst.KEY_ENABLE_AUTO_ENTER_CODE,
            getBoolean(context, PrefConst.KEY_ENABLE_AUTO_ENTER_CODE, false),
        )
        editor.putString(
            PrefConst.KEY_IPC_TOKEN,
            getString(context, PrefConst.KEY_IPC_TOKEN, ""),
        )
        editor.putString(
            PrefConst.KEY_SIM_SLOT1_REMARK,
            getString(context, PrefConst.KEY_SIM_SLOT1_REMARK, ""),
        )
        editor.putString(
            PrefConst.KEY_SIM_SLOT2_REMARK,
            getString(context, PrefConst.KEY_SIM_SLOT2_REMARK, ""),
        )
    }

    suspend fun isBackupCompatTipShown(context: Context): Boolean = getInstance(context).data
        .map { prefs: Preferences -> prefs[backupCompatTipShownKey] ?: false }
        .first()

    suspend fun setBackupCompatTipShown(context: Context, shown: Boolean) {
        getInstance(context).edit { prefs ->
            prefs[backupCompatTipShownKey] = shown
        }
    }

    suspend fun getBoolean(context: Context, key: String, defaultValue: Boolean): Boolean {
        val prefKey = booleanPreferencesKey(key)
        return getInstance(context).data
            .map { prefs: Preferences -> safeRead(prefs, prefKey, defaultValue) }
            .first()
    }

    suspend fun setBoolean(context: Context, key: String, value: Boolean) {
        val prefKey = booleanPreferencesKey(key)
        getInstance(context).edit { prefs ->
            prefs[prefKey] = value
        }
    }

    suspend fun getString(context: Context, key: String, defaultValue: String): String {
        val prefKey = stringPreferencesKey(key)
        return getInstance(context).data
            .map { prefs: Preferences -> safeRead(prefs, prefKey, defaultValue) }
            .first()
    }

    suspend fun setString(context: Context, key: String, value: String) {
        val prefKey = stringPreferencesKey(key)
        getInstance(context).edit { prefs ->
            prefs[prefKey] = value
        }
    }

    suspend fun getInt(context: Context, key: String, defaultValue: Int): Int {
        val prefKey = intPreferencesKey(key)
        return getInstance(context).data
            .map { prefs: Preferences -> safeRead(prefs, prefKey, defaultValue) }
            .first()
    }

    suspend fun setInt(context: Context, key: String, value: Int) {
        val prefKey = intPreferencesKey(key)
        getInstance(context).edit { prefs ->
            prefs[prefKey] = value
        }
    }

    suspend fun getFloat(context: Context, key: String, defaultValue: Float): Float {
        val prefKey = floatPreferencesKey(key)
        return getInstance(context).data
            .map { prefs: Preferences -> safeRead(prefs, prefKey, defaultValue) }
            .first()
    }

    suspend fun setFloat(context: Context, key: String, value: Float) {
        val prefKey = floatPreferencesKey(key)
        getInstance(context).edit { prefs ->
            prefs[prefKey] = value
        }
    }

    /** Applies one shared change set in a single DataStore transaction. */
    suspend fun persistChanges(context: Context, changes: PreferenceChangeSet): Boolean {
        if (changes.isEmpty) return true
        getInstance(context).edit { prefs ->
            applyPreferenceChanges(prefs, changes)
        }
        return true
    }

    internal fun applyPreferenceChanges(
        prefs: MutablePreferences,
        changes: PreferenceChangeSet,
    ) {
        changes.changes.forEach { change ->
            when (change) {
                is PreferenceChange.PutBoolean -> {
                    prefs[booleanPreferencesKey(change.key)] = change.value
                }
                is PreferenceChange.PutString -> prefs[stringPreferencesKey(change.key)] = change.value
                is PreferenceChange.PutInt -> prefs[intPreferencesKey(change.key)] = change.value
                is PreferenceChange.PutFloat -> prefs[floatPreferencesKey(change.key)] = change.value
                is PreferenceChange.Remove -> {
                    prefs.remove(booleanPreferencesKey(change.key))
                    prefs.remove(stringPreferencesKey(change.key))
                    prefs.remove(intPreferencesKey(change.key))
                    prefs.remove(floatPreferencesKey(change.key))
                }
            }
        }
    }

    /** Execute multiple writes atomically in a single DataStore transaction. */
    suspend fun batchEdit(
        context: Context,
        block: suspend BatchEditScope.() -> Unit,
    ) {
        val scope = BatchEditScope()
        scope.block()
        getInstance(context).edit { prefs ->
            scope.operations.forEach { operation ->
                when (operation) {
                    is BatchOp.SetBool -> {
                        prefs[booleanPreferencesKey(operation.key)] =
                            operation.value
                    }
                    is BatchOp.SetStr -> prefs[stringPreferencesKey(operation.key)] = operation.value
                    is BatchOp.SetInt -> prefs[intPreferencesKey(operation.key)] = operation.value
                    is BatchOp.SetFlt -> prefs[floatPreferencesKey(operation.key)] = operation.value
                }
            }
        }
    }

    class BatchEditScope {
        internal val operations = mutableListOf<BatchOp>()

        suspend fun setBoolean(key: String, value: Boolean) {
            operations += BatchOp.SetBool(key, value)
        }

        suspend fun setString(key: String, value: String) {
            operations += BatchOp.SetStr(key, value)
        }

        suspend fun setInt(key: String, value: Int) {
            operations += BatchOp.SetInt(key, value)
        }

        suspend fun setFloat(key: String, value: Float) {
            operations += BatchOp.SetFlt(key, value)
        }
    }

    internal sealed interface BatchOp {
        data class SetBool(val key: String, val value: Boolean) : BatchOp
        data class SetStr(val key: String, val value: String) : BatchOp
        data class SetInt(val key: String, val value: Int) : BatchOp
        data class SetFlt(val key: String, val value: Float) : BatchOp
    }

    @SuppressLint("ApplySharedPref")
    @Suppress("TooGenericExceptionCaught")
    suspend fun syncToRemotePrefs(context: Context): Boolean? {
        if (remotePrefsProvider == null) return null
        val prefs = getRemotePrefs() ?: return false
        return try {
            val editor = prefs.edit()
            populateRemoteEditor(context, editor)
            editor.commit().also { committed ->
                if (!committed) XLog.w("RemotePrefs sync commit failed")
            }
        } catch (e: Exception) {
            XLog.w("RemotePrefs sync failed: %s", e.message ?: e.javaClass.simpleName)
            false
        }
    }

    suspend fun snapshotForBackup(context: Context): Map<String, String?> =
        getInstance(context).data.first().asMap()
            .asSequence()
            .filterNot { (key, _) ->
                key.name.startsWith("internal_") ||
                    key.name == PrefConst.KEY_MOBILE_ENTITLEMENT_TOKEN ||
                    key.name == PrefConst.KEY_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED
            }
            .associate { (key, value) -> key.name to value.toString() }

    fun getBooleanFlow(context: Context, key: String, defaultValue: Boolean): Flow<Boolean> {
        val prefKey = booleanPreferencesKey(key)
        return getInstance(context).data
            .map { prefs: Preferences ->
                safeRead(prefs, prefKey, defaultValue)
            }
    }

    fun getStringFlow(context: Context, key: String, defaultValue: String): Flow<String> {
        val prefKey = stringPreferencesKey(key)
        return getInstance(context).data
            .map { prefs: Preferences -> safeRead(prefs, prefKey, defaultValue) }
    }

    fun getIntFlow(context: Context, key: String, defaultValue: Int): Flow<Int> {
        val prefKey = intPreferencesKey(key)
        return getInstance(context).data
            .map { prefs: Preferences -> safeRead(prefs, prefKey, defaultValue) }
    }

    fun getFloatFlow(context: Context, key: String, defaultValue: Float): Flow<Float> {
        val prefKey = floatPreferencesKey(key)
        return getInstance(context).data
            .map { prefs: Preferences -> safeRead(prefs, prefKey, defaultValue) }
    }

    private fun <T> safeRead(
        prefs: Preferences,
        key: Preferences.Key<T>,
        defaultValue: T,
    ): T {
        return runCatching {
            prefs[key] ?: defaultValue
        }.getOrElse {
            XLog.w(
                "DataStore type mismatch key=%s err=%s",
                key.name,
                it.message ?: it.javaClass.simpleName,
            )
            defaultValue
        }
    }
}
