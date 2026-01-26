package com.tianma.xsmscode.common.utils

import android.content.Context
import android.content.SharedPreferences
import com.tianma.xsmscode.common.constant.PrefConst

/**
 * Common Shared preferences utils.
 */
object PreferencesUtils {

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PrefConst.PREF_NAME, Context.MODE_PRIVATE)
    }

    @JvmStatic
    fun contains(context: Context, key: String): Boolean {
        return getPreferences(context).contains(key)
    }

    @JvmStatic
    fun getString(context: Context, key: String, defValue: String?): String? {
        return getPreferences(context).getString(key, defValue)
    }

    @JvmStatic
    fun putString(context: Context, key: String, value: String?) {
        getPreferences(context).edit().putString(key, value).apply()
    }

    @JvmStatic
    fun getBoolean(context: Context, key: String, defValue: Boolean): Boolean {
        return getPreferences(context).getBoolean(key, defValue)
    }

    @JvmStatic
    fun putBoolean(context: Context, key: String, value: Boolean) {
        getPreferences(context).edit().putBoolean(key, value).apply()
    }

    @JvmStatic
    fun getInt(context: Context, key: String, defValue: Int): Int {
        return getPreferences(context).getInt(key, defValue)
    }

    @JvmStatic
    fun putInt(context: Context, key: String, value: Int) {
        getPreferences(context).edit().putInt(key, value).apply()
    }

    @JvmStatic
    fun getFloat(context: Context, key: String, defValue: Float): Float {
        return getPreferences(context).getFloat(key, defValue)
    }

    @JvmStatic
    fun putFloat(context: Context, key: String, value: Float) {
        getPreferences(context).edit().putFloat(key, value).apply()
    }

    @JvmStatic
    fun getLong(context: Context, key: String, defValue: Long): Long {
        return getPreferences(context).getLong(key, defValue)
    }

    @JvmStatic
    fun putLong(context: Context, key: String, value: Long) {
        getPreferences(context).edit().putLong(key, value).apply()
    }
}
