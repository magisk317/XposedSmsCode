package com.tianma.xsmscode.common.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tianma.xsmscode.common.constant.PrefConst
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

object AppPreferencesDataStore {
    private val backupCompatTipShownKey = booleanPreferencesKey(PrefConst.KEY_BACKUP_COMPAT_TIP_SHOWN)
    private const val DATASTORE_FILE_NAME = "app_preferences.preferences_pb"
    private const val SHARED_PREFS_FILE_NAME = "xposed_prefs"

    @Volatile
    private var INSTANCE: DataStore<Preferences>? = null

    private fun getInstance(context: Context): DataStore<Preferences> = INSTANCE ?: synchronized(this) {
        val instance = PreferenceDataStoreFactory.create {
            File(context.dataDir, "datastore/$DATASTORE_FILE_NAME")
        }
        INSTANCE = instance
        instance
    }

    private fun getDataStoreFile(context: Context): File = File(context.dataDir, "datastore/$DATASTORE_FILE_NAME")

    private fun getSharedPrefsFile(context: Context): File =
        File(context.dataDir, "shared_prefs/$SHARED_PREFS_FILE_NAME.xml")

    private fun getSharedPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(SHARED_PREFS_FILE_NAME, Context.MODE_PRIVATE)

    private fun ensureDataStoreReadable(context: Context) {
        val file = getDataStoreFile(context)
        StorageUtils.setFileWorldReadable(file, 3)
    }

    private fun ensureSharedPrefsReadable(context: Context) {
        val file = getSharedPrefsFile(context)
        StorageUtils.setFileWorldReadable(file, 3)
    }

    fun ensureReadable(context: Context) {
        ensureDataStoreReadable(context)
        ensureSharedPrefsReadable(context)
    }

    suspend fun isBackupCompatTipShown(context: Context): Boolean = getInstance(context).data
        .map { prefs: Preferences -> prefs[backupCompatTipShownKey] ?: false }
        .first()

    suspend fun setBackupCompatTipShown(context: Context, shown: Boolean) {
        getInstance(context).edit { prefs ->
            prefs[backupCompatTipShownKey] = shown
        }
        getSharedPrefs(context).edit().putBoolean(PrefConst.KEY_BACKUP_COMPAT_TIP_SHOWN, shown).apply()
        ensureDataStoreReadable(context)
        ensureSharedPrefsReadable(context)
    }

    suspend fun getBoolean(context: Context, key: String, defaultValue: Boolean): Boolean {
        val prefKey = booleanPreferencesKey(key)
        return getInstance(context).data
            .map { prefs: Preferences -> prefs[prefKey] ?: defaultValue }
            .first()
    }

    suspend fun setBoolean(context: Context, key: String, value: Boolean) {
        val prefKey = booleanPreferencesKey(key)
        getInstance(context).edit { prefs ->
            prefs[prefKey] = value
        }
        getSharedPrefs(context).edit().putBoolean(key, value).apply()
        ensureDataStoreReadable(context)
        ensureSharedPrefsReadable(context)
    }

    suspend fun getString(context: Context, key: String, defaultValue: String): String {
        val prefKey = stringPreferencesKey(key)
        return getInstance(context).data
            .map { prefs: Preferences -> prefs[prefKey] ?: defaultValue }
            .first()
    }

    suspend fun setString(context: Context, key: String, value: String) {
        val prefKey = stringPreferencesKey(key)
        getInstance(context).edit { prefs ->
            prefs[prefKey] = value
        }
        getSharedPrefs(context).edit().putString(key, value).apply()
        ensureDataStoreReadable(context)
        ensureSharedPrefsReadable(context)
    }

    suspend fun getInt(context: Context, key: String, defaultValue: Int): Int {
        val prefKey = intPreferencesKey(key)
        return getInstance(context).data
            .map { prefs: Preferences -> prefs[prefKey] ?: defaultValue }
            .first()
    }

    suspend fun setInt(context: Context, key: String, value: Int) {
        val prefKey = intPreferencesKey(key)
        getInstance(context).edit { prefs ->
            prefs[prefKey] = value
        }
        getSharedPrefs(context).edit().putInt(key, value).apply()
        ensureDataStoreReadable(context)
        ensureSharedPrefsReadable(context)
    }

    suspend fun getBooleanCompat(context: Context, key: String, defaultValue: Boolean): Boolean {
        val sharedPrefs = getSharedPrefs(context)
        return if (sharedPrefs.contains(key)) {
            sharedPrefs.getBoolean(key, defaultValue)
        } else {
            getBoolean(context, key, defaultValue)
        }
    }

    suspend fun getStringCompat(context: Context, key: String, defaultValue: String): String {
        val sharedPrefs = getSharedPrefs(context)
        return if (sharedPrefs.contains(key)) {
            sharedPrefs.getString(key, defaultValue) ?: defaultValue
        } else {
            getString(context, key, defaultValue)
        }
    }

    suspend fun getIntCompat(context: Context, key: String, defaultValue: Int): Int {
        val sharedPrefs = getSharedPrefs(context)
        return if (sharedPrefs.contains(key)) {
            sharedPrefs.getInt(key, defaultValue)
        } else {
            getInt(context, key, defaultValue)
        }
    }

    suspend fun syncToSharedPrefs(context: Context) {
        val editor = getSharedPrefs(context).edit()
        editor.putBoolean(PrefConst.KEY_ENABLE, getBoolean(context, PrefConst.KEY_ENABLE, true))
        editor.putBoolean(PrefConst.KEY_VERBOSE_LOG_MODE, getBoolean(context, PrefConst.KEY_VERBOSE_LOG_MODE, false))
        editor.putBoolean(
            PrefConst.KEY_ENABLE_AUTO_INPUT_CODE,
            getBoolean(context, PrefConst.KEY_ENABLE_AUTO_INPUT_CODE, true),
        )
        editor.putString(
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY,
            getString(context, PrefConst.KEY_AUTO_INPUT_CODE_DELAY, PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT),
        )
        editor.putBoolean(PrefConst.KEY_SHOW_TOAST, getBoolean(context, PrefConst.KEY_SHOW_TOAST, true))
        editor.putString(
            PrefConst.KEY_SMSCODE_KEYWORDS,
            getString(context, PrefConst.KEY_SMSCODE_KEYWORDS, PrefConst.SMSCODE_KEYWORDS_DEFAULT),
        )
        editor.putBoolean(PrefConst.KEY_MARK_AS_READ, getBoolean(context, PrefConst.KEY_MARK_AS_READ, false))
        editor.putBoolean(PrefConst.KEY_DELETE_SMS, getBoolean(context, PrefConst.KEY_DELETE_SMS, false))
        editor.putBoolean(PrefConst.KEY_COPY_TO_CLIPBOARD, getBoolean(context, PrefConst.KEY_COPY_TO_CLIPBOARD, true))
        editor.putBoolean(
            PrefConst.KEY_ENABLE_CODE_RECORDS,
            getBoolean(context, PrefConst.KEY_ENABLE_CODE_RECORDS, true),
        )
        editor.putBoolean(PrefConst.KEY_BLOCK_SMS, getBoolean(context, PrefConst.KEY_BLOCK_SMS, false))
        editor.putBoolean(PrefConst.KEY_KILL_ME, getBoolean(context, PrefConst.KEY_KILL_ME, false))
        editor.putBoolean(
            PrefConst.KEY_SHOW_CODE_NOTIFICATION,
            getBoolean(context, PrefConst.KEY_SHOW_CODE_NOTIFICATION, true),
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
        editor.putString(PrefConst.KEY_HISTORY_LIMIT, getString(context, PrefConst.KEY_HISTORY_LIMIT, "0"))
        editor.putBoolean(
            PrefConst.KEY_AUTO_UPDATE_ON_START,
            getBoolean(context, PrefConst.KEY_AUTO_UPDATE_ON_START, true),
        )
        editor.putBoolean(
            PrefConst.KEY_AUTO_UPDATE_WIFI_ONLY,
            getBoolean(context, PrefConst.KEY_AUTO_UPDATE_WIFI_ONLY, false),
        )
        editor.putBoolean(
            PrefConst.KEY_ENABLE_AUTO_ENTER_CODE,
            getBoolean(context, PrefConst.KEY_ENABLE_AUTO_ENTER_CODE, false),
        )
        editor.apply()
        ensureSharedPrefsReadable(context)
    }

    fun getBooleanFlow(context: Context, key: String, defaultValue: Boolean): Flow<Boolean> {
        val prefKey = booleanPreferencesKey(key)
        return getInstance(context).data
            .map { prefs: Preferences -> prefs[prefKey] ?: defaultValue }
    }

    fun getStringFlow(context: Context, key: String, defaultValue: String): Flow<String> {
        val prefKey = stringPreferencesKey(key)
        return getInstance(context).data
            .map { prefs: Preferences -> prefs[prefKey] ?: defaultValue }
    }

    fun getIntFlow(context: Context, key: String, defaultValue: Int): Flow<Int> {
        val prefKey = intPreferencesKey(key)
        return getInstance(context).data
            .map { prefs: Preferences -> prefs[prefKey] ?: defaultValue }
    }
}
