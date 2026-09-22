package com.github.magisk317.smscode.common.utils

import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import android.content.Context
import com.github.magisk317.smscode.common.constant.PrefConst

object AppPreferences {
    private const val UI_KIT_STYLE_MATERIAL = 0

    /**
     * 是否同意隐私协议
     */
    suspend fun isPrivacyPolicyAccepted(context: Context): Boolean =
        AppPreferencesDataStore.getBoolean(context, PrefConst.KEY_PRIVACY_POLICY_ACCEPTED, false)

    /**
     * 设置是否同意隐私协议
     */
    suspend fun setPrivacyPolicyAccepted(context: Context, accepted: Boolean) {
        AppPreferencesDataStore.setBoolean(context, PrefConst.KEY_PRIVACY_POLICY_ACCEPTED, accepted)
    }

    /**
     * 获取当前主题模式
     * 0: Follow System, 1: Light, 2: Dark
     */
    suspend fun getThemeMode(context: Context): Int =
        AppPreferencesDataStore.getInt(context, PrefConst.KEY_CHOOSE_THEME, 0)

    /**
     * 设置当前主题模式
     */
    suspend fun setThemeMode(context: Context, mode: Int) {
        AppPreferencesDataStore.setInt(context, PrefConst.KEY_CHOOSE_THEME, mode)
    }

    suspend fun getUiKitStyle(context: Context): Int =
        AppPreferencesDataStore.getInt(context, PrefConst.KEY_UI_KIT_STYLE, UI_KIT_STYLE_MATERIAL)

    suspend fun setUiKitStyle(context: Context, style: Int) {
        AppPreferencesDataStore.setInt(context, PrefConst.KEY_UI_KIT_STYLE, style)
    }
}
