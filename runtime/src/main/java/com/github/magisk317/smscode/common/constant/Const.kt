package com.github.magisk317.smscode.common.constant

import com.github.magisk317.smscode.runtime.BuildConfig

/**
 * Constant about 3rd app
 */
object Const {

    const val TELEGRAM_GROUP_URL = "https://t.me/+NR2QaQ4dlEgxYmNl"

    /* Xposed SmsCode begin */
    const val HOME_ACTIVITY_ALIAS = BuildConfig.APPLICATION_ID + ".HomeActivityAlias"
    const val EXTRA_ACTION = "extra_action"
    const val ACTION_OPEN_RECORDS = "smscode_records"
    const val ACTION_OPEN_RULES = "smscode_rules"
    const val ACTION_OPEN_SETTINGS = "smscode_settings"

    const val PROJECT_SOURCE_CODE_URL = "https://gitlab.com/magisk3171/XposedSmsCode"
    const val PROJECT_GITHUB_LATEST_RELEASE_URL =
        "https://github.com/magisk317/XposedSmsCode/releases/latest"
    const val PROJECT_DOC_BASE_URL = "https://magisk317.github.io/SmsCode"
    const val PRIVACY_POLICY_URL =
        "https://gitlab.com/magisk3171/XposedSmsCode/-/blob/beta/docs/PRIVACY.md"
    const val DOC_SMS_CODE_RULE_HELP = "sms_code_rule_help"
    /* Xposed SmsCode end */

    const val LSPOSED_MANAGER_PACKAGE_NAME = "org.lsposed.manager"

    /* Rule Edit Types */
    const val EDIT_TYPE_CREATE = 0
    const val EDIT_TYPE_EDIT = 1
    const val KEY_RULE_EDIT_TYPE = "key_rule_edit_type"
    const val KEY_CODE_RULE = "key_code_rule"
    const val KEY_RULE_ID = "key_rule_id"
    const val EXTRA_IMPORT_URI = "extra_import_uri"

    /* UI Dimensions (dp) */
    const val PADDING_SMALL = 8
    const val PADDING_MEDIUM = 16
    const val PADDING_LARGE = 24
    const val BOTTOM_SPACE_HEIGHT = 80

    /* UI Measurements */
    const val TOP_BAR_HEIGHT = 64
    const val SPACING_EXTRA_SMALL = 4
    const val SPACING_SMALL = 8
    const val SPACING_MEDIUM = 16
    const val FLOW_STOP_TIMEOUT_MS = 5000L
}
