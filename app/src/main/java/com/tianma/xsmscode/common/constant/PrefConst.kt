package com.tianma.xsmscode.common.constant

import com.github.tianma8023.xposed.smscode.BuildConfig

/**
 * Preference相关的常量
 */
object PrefConst {

    const val PREF_NAME = BuildConfig.APPLICATION_ID + "_preferences"

    // General
    const val KEY_ENABLE = "pref_enable"
    const val KEY_HIDE_LAUNCHER_ICON = "pref_hide_launcher_icon"
    const val KEY_CHOOSE_THEME = "pref_choose_theme"

    // SMS Code
    const val KEY_SHOW_TOAST = "pref_show_toast"
    const val KEY_COPY_TO_CLIPBOARD = "pref_copy_to_clipboard"
    const val KEY_ENABLE_AUTO_INPUT_CODE = "pref_enable_auto_input_code"
    const val KEY_AUTO_INPUT_CODE_DELAY = "pref_auto_input_code_delay"
    const val KEY_AUTO_INPUT_CODE_DELAY_DEFAULT = "0"
    const val KEY_APP_BLOCK_ENTRY = "pref_app_block_entry"
    const val KEY_BLOCK_SMS = "pref_block_sms"
    const val KEY_DEDUPLICATE_SMS = "pref_deduplicate_sms"


    // Code Notification
    const val KEY_SHOW_CODE_NOTIFICATION = "pref_show_code_notification"
    const val KEY_AUTO_CANCEL_CODE_NOTIFICATION = "pref_auto_cancel_code_notification"
    const val KEY_NOTIFICATION_RETENTION_TIME = "pref_notification_retention_time"
    const val NOTIFICATION_RETENTION_TIME_DEFAULT = "5"


    // Code Record
    const val KEY_ENABLE_CODE_RECORDS = "pref_enable_code_records"
    const val MAX_SMS_RECORDS_COUNT_DEFAULT = 20
    const val KEY_ENTRY_CODE_RECORDS = "pref_entry_code_records"

    // Code Rules
    const val KEY_SMSCODE_KEYWORDS = "pref_smscode_keywords"
    val SMSCODE_KEYWORDS_DEFAULT = SmsCodeConst.VERIFICATION_KEYWORDS_REGEX
    const val KEY_SMSCODE_TEST = "pref_smscode_test"
    const val KEY_CODE_RULES = "pref_code_rules"

    // Experimental
    const val KEY_MARK_AS_READ = "pref_mark_as_read"
    const val KEY_DELETE_SMS = "pref_delete_sms"
    const val KEY_KILL_ME = "pref_kill_me"

    // Others
    const val KEY_VERBOSE_LOG_MODE = "pref_verbose_log_mode"

    // About
    const val KEY_ABOUT = "pref_about"
    const val KEY_VERSION = "pref_version"
    const val KEY_JOIN_QQ_GROUP = "pref_join_qq_group"
    const val KEY_SOURCE_CODE = "pref_source_code"
    const val KEY_DONATE_BY_ALIPAY = "pref_donate_by_alipay"
    const val KEY_PRIVACY_POLICY = "pref_privacy_policy"
    const val KEY_PRIVACY_POLICY_ACCEPTED = "pref_privacy_policy_accepted"
}
