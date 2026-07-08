package com.github.magisk317.smscode.common.constant

enum class PrefValueType {
    BOOLEAN,
    INT,
    FLOAT,
    STRING,
}

object PrefRestoreTypeRegistry {
    val BOOLEAN_KEYS: Set<String> = setOf(
        PrefConst.KEY_ENABLE,
        PrefConst.KEY_HIDE_LAUNCHER_ICON,
        PrefConst.KEY_SHOW_LAUNCHER_ICON,
        PrefConst.KEY_SETTINGS_ACCORDION_MODE,
        PrefConst.KEY_SHOW_TOAST,
        PrefConst.KEY_COPY_TO_CLIPBOARD,
        PrefConst.KEY_ENABLE_AUTO_INPUT_CODE,
        PrefConst.KEY_ENABLE_AUTO_ENTER_CODE,
        PrefConst.KEY_BLOCK_SMS,
        PrefConst.KEY_DEDUPLICATE_SMS,
        PrefConst.KEY_ENABLE_SMS_BLACKLIST,
        PrefConst.KEY_SMS_BLACKLIST_ACTION_DELETE,
        PrefConst.KEY_SMS_BLACKLIST_ACTION_BLOCK,
        PrefConst.KEY_SHOW_CODE_NOTIFICATION,
        PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION,
        PrefConst.KEY_ENABLE_CODE_RECORDS_CODE,
        PrefConst.KEY_ENABLE_CODE_RECORDS_PLAIN_SMS,
        PrefConst.KEY_ENABLE_CODE_RECORDS_APP_NOTIFY,
        PrefConst.KEY_ENABLE_CODE_RECORDS_CALL_NOTIFY,
        PrefConst.KEY_MARK_AS_READ,
        PrefConst.KEY_DELETE_SMS,
        PrefConst.KEY_KILL_ME,
        PrefConst.KEY_VERBOSE_LOG_MODE,
        PrefConst.KEY_AUTO_UPDATE_ON_START,
        PrefConst.KEY_AUTO_UPDATE_WIFI_ONLY,
        PrefConst.KEY_PRIVACY_POLICY_ACCEPTED,
        PrefConst.KEY_BACKUP_COMPAT_TIP_SHOWN,
    )

    val INT_KEYS: Set<String> = setOf(
        PrefConst.KEY_CHOOSE_THEME,
        PrefConst.KEY_UI_KIT_STYLE,
        PrefConst.KEY_RUNTIME_LOG_RETENTION_DAYS,
        "local_version_code",
    )

    val FLOAT_KEYS: Set<String> = emptySet()

    fun typeOf(key: String): PrefValueType {
        return when {
            BOOLEAN_KEYS.contains(key) -> PrefValueType.BOOLEAN
            INT_KEYS.contains(key) -> PrefValueType.INT
            FLOAT_KEYS.contains(key) -> PrefValueType.FLOAT
            else -> PrefValueType.STRING
        }
    }
}
