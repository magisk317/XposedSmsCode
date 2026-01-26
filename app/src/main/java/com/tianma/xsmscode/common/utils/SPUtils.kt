package com.tianma.xsmscode.common.utils

import android.content.Context
import com.tianma.xsmscode.common.constant.PrefConst

object SPUtils {

    // 本地的版本号
    private const val LOCAL_VERSION_CODE = "local_version_code"
    private const val LOCAL_VERSION_CODE_DEFAULT = 16

    /**
     * 获取本地记录的版本号
     */
    @JvmStatic
    fun getLocalVersionCode(context: Context): Int {
        // 如果不存在,则默认返回16,即v1.4.5版本
        return PreferencesUtils.getInt(context, LOCAL_VERSION_CODE, LOCAL_VERSION_CODE_DEFAULT)
    }

    /**
     * 设置当前版本号
     */
    @JvmStatic
    fun setLocalVersionCode(context: Context, versionCode: Int) {
        PreferencesUtils.putInt(context, LOCAL_VERSION_CODE, versionCode)
    }

    /**
     * 获取短信验证码关键字
     */
    @JvmStatic
    fun getSMSCodeKeywords(context: Context): String? {
        return PreferencesUtils.getString(context, PrefConst.KEY_SMSCODE_KEYWORDS, PrefConst.SMSCODE_KEYWORDS_DEFAULT)
    }

    /**
     * 是否同意隐私协议
     */
    @JvmStatic
    fun isPrivacyPolicyAccepted(context: Context): Boolean {
        return PreferencesUtils.getBoolean(context, PrefConst.KEY_PRIVACY_POLICY_ACCEPTED, false)
    }

    /**
     * 设置是否同意隐私协议
     */
    @JvmStatic
    fun setPrivacyPolicyAccepted(context: Context, accepted: Boolean) {
        PreferencesUtils.putBoolean(context, PrefConst.KEY_PRIVACY_POLICY_ACCEPTED, accepted)
    }

    /**
     * 获取当前主题模式
     * 0: Follow System, 1: Light, 2: Dark
     */
    @JvmStatic
    fun getThemeMode(context: Context): Int {
        return PreferencesUtils.getInt(context, PrefConst.KEY_CHOOSE_THEME, 0)
    }

    /**
     * 设置当前主题模式
     */
    @JvmStatic
    fun setThemeMode(context: Context, mode: Int) {
        PreferencesUtils.putInt(context, PrefConst.KEY_CHOOSE_THEME, mode)
    }
}
