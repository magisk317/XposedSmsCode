package com.github.magisk317.smscode.common.constant

import io.github.magisk317.smscode.rule.constant.SmsCodeConst

/**
 * Preference相关的常量
 */
object PrefConst {

    /** Xposed RemotePreferences group shared by the app and hook processes. */
    const val REMOTE_PREFS_GROUP = "smscode_runtime_preferences"

    // General
    const val KEY_ENABLE = "pref_enable"
    const val KEY_HIDE_LAUNCHER_ICON = "pref_hide_launcher_icon"
    const val KEY_SHOW_LAUNCHER_ICON = "pref_show_launcher_icon"
    const val KEY_CHOOSE_THEME = "pref_choose_theme"
    const val KEY_UI_KIT_STYLE = "pref_ui_kit_style"
    const val KEY_COMPOSE_SETTINGS = "pref_compose_settings"
    const val KEY_SETTINGS_ACCORDION_MODE = "pref_settings_accordion_mode"

    // SMS Code
    const val KEY_SHOW_TOAST = "pref_show_toast"
    const val KEY_COPY_TO_CLIPBOARD = "pref_copy_to_clipboard"
    const val KEY_ENABLE_AUTO_INPUT_CODE = "pref_enable_auto_input_code"
    const val KEY_ENABLE_AUTO_ENTER_CODE = "pref_enable_auto_enter_code"
    const val KEY_AUTO_INPUT_CODE_DELAY = "pref_auto_input_code_delay_ms"
    const val KEY_AUTO_INPUT_CODE_DELAY_LEGACY = "pref_auto_input_code_delay"
    const val KEY_AUTO_INPUT_CODE_DELAY_DEFAULT = "0"
    const val KEY_AUTO_INPUT_CODE_INTERVAL = "pref_auto_input_code_interval"
    const val KEY_AUTO_INPUT_CODE_INTERVAL_DEFAULT = "80"
    const val KEY_APP_BLOCK_ENTRY = "pref_app_block_entry"
    const val KEY_BLOCK_SMS = "pref_block_sms"
    const val KEY_DEDUPLICATE_SMS = "pref_deduplicate_sms"
    const val KEY_ENABLE_SMS_BLACKLIST = "pref_enable_sms_blacklist"
    const val KEY_SMS_BLACKLIST_NUMBERS = "pref_sms_blacklist_numbers"
    const val KEY_SMS_BLACKLIST_PREFIXES = "pref_sms_blacklist_prefixes"
    const val KEY_SMS_BLACKLIST_REGEX = "pref_sms_blacklist_regex"
    const val KEY_SMS_BLACKLIST_CONTENT = "pref_sms_blacklist_content"
    const val KEY_SMS_BLACKLIST_ACTION_DELETE = "pref_sms_blacklist_action_delete"
    const val KEY_SMS_BLACKLIST_ACTION_BLOCK = "pref_sms_blacklist_action_block"

    // Code Notification
    const val KEY_SHOW_CODE_NOTIFICATION = "pref_show_code_notification"
    const val KEY_CODE_NOTIFICATION_OWNER = "pref_code_notification_owner"
    const val KEY_AUTO_CANCEL_CODE_NOTIFICATION = "pref_auto_cancel_code_notification"
    const val KEY_NOTIFICATION_RETENTION_TIME = "pref_notification_retention_time"
    const val NOTIFICATION_RETENTION_TIME_DEFAULT = "5"

    // Code Record
    const val MAX_SMS_RECORDS_COUNT_DEFAULT = 20
    const val KEY_ENTRY_CODE_RECORDS = "pref_entry_code_records"
    const val KEY_ENABLE_CODE_RECORDS_CODE = "pref_enable_code_records_code"
    const val KEY_ENABLE_CODE_RECORDS_PLAIN_SMS = "pref_enable_code_records_plain_sms"
    const val KEY_ENABLE_CODE_RECORDS_APP_NOTIFY = "pref_enable_code_records_app_notify"
    const val KEY_ENABLE_CODE_RECORDS_CALL_NOTIFY = "pref_enable_code_records_call_notify"
    const val KEY_HISTORY_LIMIT_CODE = "pref_history_limit_code"
    const val KEY_HISTORY_LIMIT_PLAIN_SMS = "pref_history_limit_plain_sms"
    const val KEY_HISTORY_LIMIT_APP_NOTIFY = "pref_history_limit_app_notify"
    const val KEY_HISTORY_LIMIT_CALL_NOTIFY = "pref_history_limit_call_notify"

    // Code Rules
    const val KEY_SMSCODE_KEYWORDS = "pref_smscode_keywords"
    val SMSCODE_KEYWORDS_DEFAULT = SmsCodeConst.VERIFICATION_KEYWORDS_REGEX
    const val KEY_SMSCODE_TEST = "pref_smscode_test"
    const val KEY_CODE_RULES = "pref_code_rules"
    const val KEY_SMS_CODE_RULE_SOURCE_URL = "pref_sms_code_rule_source_url"

    // Experimental
    const val KEY_MARK_AS_READ = "pref_mark_as_read"
    const val KEY_DELETE_SMS = "pref_delete_sms"
    const val KEY_KILL_ME = "pref_kill_me"

    // Others
    const val KEY_VERBOSE_LOG_MODE = "pref_verbose_log_mode"
    const val KEY_RUNTIME_LOG_RETENTION_DAYS = "pref_runtime_log_retention_days"
    const val RUNTIME_LOG_RETENTION_DAYS_DEFAULT = 2
    const val RUNTIME_LOG_RETENTION_DAYS_MIN = 1
    const val KEY_SENSITIVE_DEBUG_LOG_MODE = "pref_sensitive_debug_log_mode"
    const val KEY_ENABLE_ANALYTICS = "pref_enable_analytics"
    const val KEY_AUTO_UPDATE_ON_START = "pref_auto_update_on_start"
    const val KEY_AUTO_UPDATE_WIFI_ONLY = "pref_auto_update_wifi_only"
    const val KEY_GITHUB_IGNORED_VERSION = "pref_github_ignored_version"

    // About
    const val KEY_ABOUT = "pref_about"
    const val KEY_VERSION = "pref_version"
    const val KEY_SOURCE_CODE = "pref_source_code"
    const val KEY_DONATE_BY_ALIPAY = "pref_donate_by_alipay"
    const val KEY_PRIVACY_POLICY = "pref_privacy_policy"
    const val KEY_PRIVACY_POLICY_ACCEPTED = "pref_privacy_policy_accepted"
    const val KEY_BACKUP_COMPAT_TIP_SHOWN = "pref_backup_compat_tip_shown"
    const val KEY_ABOUT_COMPOSE = "pref_about_compose"
    const val KEY_IPC_TOKEN = "ipc_token"
    const val KEY_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED = "mobile_entitlement_automation_allowed"
    const val KEY_MOBILE_ENTITLEMENT_TOKEN = "entitlement_token"
    const val DEFAULT_MOBILE_ENTITLEMENT_AUTOMATION_ALLOWED = false
    const val KEY_SIM_SLOT1_REMARK = "pref_sim_slot1_remark"
    const val KEY_SIM_SLOT2_REMARK = "pref_sim_slot2_remark"
}
