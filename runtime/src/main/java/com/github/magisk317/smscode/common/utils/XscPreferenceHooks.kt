package com.github.magisk317.smscode.common.utils

import android.content.Context
import android.content.SharedPreferences
import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.common.constant.CodeNotificationOwner
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesHooks

/**
 * XposedSmsCode's extension points for the shared preference store.
 *
 * The mirror list below is what the hook/child processes read through a plain
 * SharedPreferences; it is the only part of the store that is repository
 * specific, so it lives here instead of in the shared implementation.
 */
object XscPreferenceHooks : AppPreferencesHooks {
    override suspend fun publishRemoteEntries(context: Context, editor: SharedPreferences.Editor) {
        editor.putString(
            io.github.magisk317.xposed.logging.AnonymousInstallationId.PREFERENCE_KEY,
            AppPreferencesDataStore.getString(
                context,
                io.github.magisk317.xposed.logging.AnonymousInstallationId.PREFERENCE_KEY,
                "",
            ),
        )
        editor.putBoolean(PrefConst.KEY_ENABLE, AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_ENABLE, true))
        editor.putBoolean(
            PrefConst.KEY_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED,
            AppPreferencesDataStore.getBoolean(
                context,
                PrefConst.KEY_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED,
                PrefConst.DEFAULT_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED,
            ),
        )
        editor.putString(
            PrefConst.KEY_MOBILE_ENTITLEMENT_TOKEN,
            AppPreferencesDataStore.getString(context, PrefConst.KEY_MOBILE_ENTITLEMENT_TOKEN, ""),
        )
        editor.putBoolean(
            PrefConst.KEY_SETTINGS_ACCORDION_MODE,
            AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_SETTINGS_ACCORDION_MODE, true),
        )
        editor.putBoolean(PrefConst.KEY_VERBOSE_LOG_MODE, AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_VERBOSE_LOG_MODE, false))
        editor.putInt(
            PrefConst.KEY_RUNTIME_LOG_RETENTION_DAYS,
            AppPreferencesDataStore.getInt(
                context,
                PrefConst.KEY_RUNTIME_LOG_RETENTION_DAYS,
                PrefConst.RUNTIME_LOG_RETENTION_DAYS_DEFAULT,
            ).coerceAtLeast(PrefConst.RUNTIME_LOG_RETENTION_DAYS_MIN),
        )
        editor.putBoolean(
            PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE,
            AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE, false),
        )
        editor.putBoolean(
            PrefConst.KEY_ENABLE_AUTO_INPUT_CODE,
            AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_ENABLE_AUTO_INPUT_CODE, true),
        )
        editor.putString(
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY,
            AppPreferencesDataStore.getString(context, PrefConst.KEY_AUTO_INPUT_CODE_DELAY, PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT),
        )
        editor.putString(
            PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL,
            AppPreferencesDataStore.getString(
                context,
                PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL,
                PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL_DEFAULT,
            ),
        )
        editor.putBoolean(PrefConst.KEY_SHOW_TOAST, AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_SHOW_TOAST, true))
        editor.putString(
            PrefConst.KEY_SMSCODE_KEYWORDS,
            AppPreferencesDataStore.getString(context, PrefConst.KEY_SMSCODE_KEYWORDS, PrefConst.SMSCODE_KEYWORDS_DEFAULT),
        )
        editor.putBoolean(PrefConst.KEY_MARK_AS_READ, AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_MARK_AS_READ, false))
        editor.putBoolean(PrefConst.KEY_DELETE_SMS, AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_DELETE_SMS, false))
        editor.putBoolean(PrefConst.KEY_COPY_TO_CLIPBOARD, AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_COPY_TO_CLIPBOARD, false))
        editor.putBoolean(
            PrefConst.KEY_ENABLE_CODE_RECORDS_CODE,
            AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_ENABLE_CODE_RECORDS_CODE, true),
        )
        editor.putBoolean(
            PrefConst.KEY_ENABLE_CODE_RECORDS_PLAIN_SMS,
            AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_ENABLE_CODE_RECORDS_PLAIN_SMS, true),
        )
        editor.putBoolean(
            PrefConst.KEY_ENABLE_CODE_RECORDS_APP_NOTIFY,
            AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_ENABLE_CODE_RECORDS_APP_NOTIFY, true),
        )
        editor.putBoolean(
            PrefConst.KEY_ENABLE_CODE_RECORDS_CALL_NOTIFY,
            AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_ENABLE_CODE_RECORDS_CALL_NOTIFY, true),
        )
        editor.putBoolean(PrefConst.KEY_BLOCK_SMS, AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_BLOCK_SMS, false))
        editor.putBoolean(PrefConst.KEY_KILL_ME, AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_KILL_ME, false))
        editor.putBoolean(
            PrefConst.KEY_SHOW_CODE_NOTIFICATION,
            AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_SHOW_CODE_NOTIFICATION, true),
        )
        editor.putString(
            PrefConst.KEY_CODE_NOTIFICATION_OWNER,
            CodeNotificationOwner.normalize(
                AppPreferencesDataStore.getString(context, PrefConst.KEY_CODE_NOTIFICATION_OWNER, CodeNotificationOwner.DEFAULT),
            ),
        )
        editor.putBoolean(
            PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION,
            AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION, false),
        )
        editor.putString(
            PrefConst.KEY_NOTIFICATION_RETENTION_TIME,
            AppPreferencesDataStore.getString(
                context,
                PrefConst.KEY_NOTIFICATION_RETENTION_TIME,
                PrefConst.NOTIFICATION_RETENTION_TIME_DEFAULT,
            ),
        )
        editor.putBoolean(PrefConst.KEY_DEDUPLICATE_SMS, AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_DEDUPLICATE_SMS, true))
        editor.putBoolean(
            PrefConst.KEY_ENABLE_SMS_BLACKLIST,
            AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_ENABLE_SMS_BLACKLIST, false),
        )
        editor.putString(
            PrefConst.KEY_SMS_BLACKLIST_NUMBERS,
            AppPreferencesDataStore.getString(context, PrefConst.KEY_SMS_BLACKLIST_NUMBERS, ""),
        )
        editor.putString(
            PrefConst.KEY_SMS_BLACKLIST_PREFIXES,
            AppPreferencesDataStore.getString(context, PrefConst.KEY_SMS_BLACKLIST_PREFIXES, ""),
        )
        editor.putString(
            PrefConst.KEY_SMS_BLACKLIST_REGEX,
            AppPreferencesDataStore.getString(context, PrefConst.KEY_SMS_BLACKLIST_REGEX, ""),
        )
        editor.putString(
            PrefConst.KEY_SMS_BLACKLIST_CONTENT,
            AppPreferencesDataStore.getString(context, PrefConst.KEY_SMS_BLACKLIST_CONTENT, ""),
        )
        editor.putBoolean(
            PrefConst.KEY_SMS_BLACKLIST_ACTION_DELETE,
            AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_SMS_BLACKLIST_ACTION_DELETE, true),
        )
        editor.putBoolean(
            PrefConst.KEY_SMS_BLACKLIST_ACTION_BLOCK,
            AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_SMS_BLACKLIST_ACTION_BLOCK, false),
        )
        editor.putString(
            PrefConst.KEY_HISTORY_LIMIT_CODE,
            AppPreferencesDataStore.getString(context, PrefConst.KEY_HISTORY_LIMIT_CODE, "0"),
        )
        editor.putString(
            PrefConst.KEY_HISTORY_LIMIT_PLAIN_SMS,
            AppPreferencesDataStore.getString(context, PrefConst.KEY_HISTORY_LIMIT_PLAIN_SMS, "0"),
        )
        editor.putString(
            PrefConst.KEY_HISTORY_LIMIT_APP_NOTIFY,
            AppPreferencesDataStore.getString(context, PrefConst.KEY_HISTORY_LIMIT_APP_NOTIFY, "0"),
        )
        editor.putString(
            PrefConst.KEY_HISTORY_LIMIT_CALL_NOTIFY,
            AppPreferencesDataStore.getString(
                context,
                PrefConst.KEY_HISTORY_LIMIT_CALL_NOTIFY,
                "20",
            ),
        )
        editor.putBoolean(
            PrefConst.KEY_AUTO_UPDATE_ON_START,
            AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_AUTO_UPDATE_ON_START, true),
        )
        editor.putBoolean(
            PrefConst.KEY_AUTO_UPDATE_WIFI_ONLY,
            AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_AUTO_UPDATE_WIFI_ONLY, false),
        )
        editor.putInt(
            PrefConst.KEY_CHOOSE_THEME,
            AppPreferencesDataStore.getInt(context, PrefConst.KEY_CHOOSE_THEME, 0),
        )
        editor.putInt(
            PrefConst.KEY_UI_KIT_STYLE,
            AppPreferencesDataStore.getInt(context, PrefConst.KEY_UI_KIT_STYLE, 0),
        )
        editor.putBoolean(
            PrefConst.KEY_ENABLE_AUTO_ENTER_CODE,
            AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_ENABLE_AUTO_ENTER_CODE, false),
        )
        editor.putString(
            PrefConst.KEY_IPC_TOKEN,
            AppPreferencesDataStore.getString(context, PrefConst.KEY_IPC_TOKEN, ""),
        )
        editor.putString(
            PrefConst.KEY_SIM_SLOT1_REMARK,
            AppPreferencesDataStore.getString(context, PrefConst.KEY_SIM_SLOT1_REMARK, ""),
        )
        editor.putString(
            PrefConst.KEY_SIM_SLOT2_REMARK,
            AppPreferencesDataStore.getString(context, PrefConst.KEY_SIM_SLOT2_REMARK, ""),
        )
    }

}
