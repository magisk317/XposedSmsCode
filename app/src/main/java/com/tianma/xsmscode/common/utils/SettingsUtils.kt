package com.tianma.xsmscode.common.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import com.github.tianma8023.xposed.smscode.BuildConfig

/**
 * Utility for android.provider.Settings
 */
object SettingsUtils {

    private fun getSecureString(context: Context, key: String): String? {
        return Settings.Secure.getString(context.contentResolver, key)
    }

    /**
     * Get system default SMS app package
     * @return package name of default sms app
     */
    @JvmStatic
    fun getDefaultSmsAppPackage(context: Context): String? {
        val key = "sms_default_application"
        return getSecureString(context, key)
    }

    /**
     * Request ignore battery optimization
     */
    @RequiresApi(Build.VERSION_CODES.M)
    @SuppressLint("BatteryLife")
    @JvmStatic
    fun requestIgnoreBatteryOptimization(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = Uri.parse("package:" + BuildConfig.APPLICATION_ID)
        context.startActivity(intent)
    }

    /**
     * Go to ignore battery optimization settings.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    @JvmStatic
    fun gotoIgnoreBatteryOptimizationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        context.startActivity(intent)
    }
}
