package com.tianma.xsmscode.common.utils

import com.tianma.xsmscode.common.constant.PrefConst
import de.robv.android.xposed.XSharedPreferences

object XSPUtils {

    /**
     * 总开关是否打开
     */
    @JvmStatic
    fun isEnabled(preferences: XSharedPreferences): Boolean {
        return preferences.getBoolean(PrefConst.KEY_ENABLE, true)
    }

    /**
     * 日志模式是否是verbose log模式
     */
    @JvmStatic
    fun isVerboseLogMode(preferences: XSharedPreferences): Boolean {
        return preferences.getBoolean(PrefConst.KEY_VERBOSE_LOG_MODE, false)
    }

    /**
     * 自动输入总开关是否打开
     */
    @JvmStatic
    fun autoInputCodeEnabled(preferences: XSharedPreferences): Boolean {
        return preferences.getBoolean(PrefConst.KEY_ENABLE_AUTO_INPUT_CODE, true)
    }

    /**
     * 自动输入延迟(单位s)
     */
    @JvmStatic
    fun getAutoInputCodeDelay(preferences: XSharedPreferences): Long {
        val value = preferences.getString(PrefConst.KEY_AUTO_INPUT_CODE_DELAY, PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT)
        return try {
            value?.toLong() ?: PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT.toLong()
        } catch (e: Exception) {
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT.toLong()
        }
    }

    /**
     * 是否应该在复制验证码到系统剪切板之后显示Toast
     */
    @JvmStatic
    fun shouldShowToast(preferences: XSharedPreferences): Boolean {
        return preferences.getBoolean(PrefConst.KEY_SHOW_TOAST, true)
    }

    /**
     * 获取短信验证码关键字
     */
    @JvmStatic
    fun getSMSCodeKeywords(preferences: XSharedPreferences): String? {
        return preferences.getString(PrefConst.KEY_SMSCODE_KEYWORDS, PrefConst.SMSCODE_KEYWORDS_DEFAULT)
    }

    /**
     * 标记为已读是否打开
     */
    @JvmStatic
    fun markAsReadEnabled(preferences: XSharedPreferences): Boolean {
        return preferences.getBoolean(PrefConst.KEY_MARK_AS_READ, false)
    }

    /**
     * 是否删除验证码短信
     */
    @JvmStatic
    fun deleteSmsEnabled(preferences: XSharedPreferences): Boolean {
        return preferences.getBoolean(PrefConst.KEY_DELETE_SMS, false)
    }

    /**
     * 是否复制到剪切板
     */
    @JvmStatic
    fun copyToClipboardEnabled(preferences: XSharedPreferences): Boolean {
        return preferences.getBoolean(PrefConst.KEY_COPY_TO_CLIPBOARD, false)
    }

    /**
     * 是否记录短信验证码
     */
    @JvmStatic
    fun recordSmsCodeEnabled(preferences: XSharedPreferences): Boolean {
        return preferences.getBoolean(PrefConst.KEY_ENABLE_CODE_RECORDS, true)
    }

    /**
     * 是否拦截短信通知
     */
    @JvmStatic
    fun blockSmsEnabled(preferences: XSharedPreferences): Boolean {
        return preferences.getBoolean(PrefConst.KEY_BLOCK_SMS, false)
    }

    /**
     * 验证码提取成功后是否杀掉模块进程
     */
    @JvmStatic
    fun killMeEnabled(preferences: XSharedPreferences): Boolean {
        return preferences.getBoolean(PrefConst.KEY_KILL_ME, false)
    }

    /**
     * 是否显示验证码通知
     */
    @JvmStatic
    fun showCodeNotification(preferences: XSharedPreferences): Boolean {
        return preferences.getBoolean(PrefConst.KEY_SHOW_CODE_NOTIFICATION, true)
    }

    /**
     * 是否自动清除验证码通知
     */
    @JvmStatic
    fun autoCancelCodeNotification(preferences: XSharedPreferences): Boolean {
        return preferences.getBoolean(PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION, false)
    }

    /**
     * 获取验证码通知保留时间
     */
    @JvmStatic
    fun getNotificationRetentionTime(preferences: XSharedPreferences): Int {
        val value = preferences.getString(PrefConst.KEY_NOTIFICATION_RETENTION_TIME, PrefConst.NOTIFICATION_RETENTION_TIME_DEFAULT)
        return try {
            value?.toInt() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 是否过滤掉重复短信
     */
    @JvmStatic
    fun deduplicateSms(preferences: XSharedPreferences): Boolean {
        return preferences.getBoolean(PrefConst.KEY_DEDUPLICATE_SMS, false)
    }
}
