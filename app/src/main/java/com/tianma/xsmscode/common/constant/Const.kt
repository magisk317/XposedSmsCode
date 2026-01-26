package com.tianma.xsmscode.common.constant

import com.github.tianma8023.xposed.smscode.BuildConfig

/**
 * Constant about 3rd app
 */
object Const {

    /* Alipay begin */
    const val ALIPAY_PACKAGE_NAME = "com.eg.android.AlipayGphone"
    const val ALIPAY_QRCODE_URI_PREFIX = "alipayqr://platformapi/startapp?saId=10000007&qrcode="
    // 收款码 URL
    const val ALIPAY_QRCODE_URL = "HTTPS://QR.ALIPAY.COM/FKX074142EKXD0OIMV8B60"
    // 红包口令
    const val ALIPAY_POCKET_TOKEN = "J:/wkSIPXL689C 或📸復 zhi📸此消息打开🔍吱.f`u宝🔎，得幸福宏饱，天天等着你  s:/r HU6311 $801"
    /* Alipay end */

    /* QQ begin */
    const val QQ_GROUP_URL = "https://qm.qq.com/q/4mMpX3vk4U"
    /* QQ end */

    /* Xposed SmsCode begin */
    const val HOME_ACTIVITY_ALIAS = BuildConfig.APPLICATION_ID + ".HomeActivityAlias"

    const val PROJECT_SOURCE_CODE_URL = "https://github.com/magisk317/XposedSmsCode"
    const val PROJECT_GITHUB_LATEST_RELEASE_URL = PROJECT_SOURCE_CODE_URL + "/releases/latest"
    const val PROJECT_DOC_BASE_URL = "https://magisk317.github.io/SmsCode"
    const val DOC_SMS_CODE_RULE_HELP = "sms_code_rule_help"
    /* Xposed SmsCode end */



    /* CoolApk */
    const val COOL_MARKET_PACKAGE_NAME = "com.coolapk.market"
    /* CoolApk end */
}
