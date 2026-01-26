package com.tianma.xsmscode.common.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.annotation.IntDef
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.common.constant.Const
import com.tianma.xsmscode.xp.hook.permission.PermissionGranterHook
import com.tianma.xsmscode.xp.hook.code.SmsHandlerHook

/**
 * 包相关工具类
 */
object PackageUtils {

    /**
     * not installed
     */
    private const val PACKAGE_NOT_INSTALLED = 0

    /**
     * installed & disabled
     */
    private const val PACKAGE_DISABLED = 1

    /**
     * installed & enabled
     */
    private const val PACKAGE_ENABLED = 2

    @IntDef(PACKAGE_NOT_INSTALLED, PACKAGE_DISABLED, PACKAGE_ENABLED)
    @Retention(AnnotationRetention.SOURCE)
    annotation class PackageState

    private fun checkPackageState(context: Context, packageName: String): Int {
        return if (isPackageEnabled(context, packageName)) {
            PACKAGE_ENABLED
        } else {
            if (isPackageInstalled(context, packageName)) {
                PACKAGE_DISABLED
            } else {
                PACKAGE_NOT_INSTALLED
            }
        }
    }

    /**
     * 指定的包名对应的App是否已安装
     */
    @JvmStatic
    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        return try {
            val packageInfo = pm.getPackageInfo(packageName, 0)
            packageInfo != null
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 对应包名的应用是否已启用
     */
    @JvmStatic
    fun isPackageEnabled(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            appInfo.enabled
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun checkAlipayExists(context: Context): Boolean {
        val packageState = checkPackageState(context, Const.ALIPAY_PACKAGE_NAME)
        return when (packageState) {
            PACKAGE_ENABLED -> true
            PACKAGE_DISABLED -> {
                Toast.makeText(context, R.string.alipay_enable_prompt, Toast.LENGTH_SHORT).show()
                false
            }
            PACKAGE_NOT_INSTALLED -> {
                Toast.makeText(context, R.string.alipay_install_prompt, Toast.LENGTH_SHORT).show()
                false
            }
            else -> false
        }
    }

    /**
     * 打开支付宝
     */
    @JvmStatic
    fun startAlipayActivity(context: Context) {
        if (checkAlipayExists(context)) {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(Const.ALIPAY_PACKAGE_NAME)
            context.startActivity(intent)
        }
    }

    /**
     * 打开支付宝捐赠页
     */
    @JvmStatic
    fun startAlipayDonatePage(context: Context) {
        if (checkAlipayExists(context)) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(Const.ALIPAY_QRCODE_URI_PREFIX + Const.ALIPAY_QRCODE_URL)
            context.startActivity(intent)
        }
    }


    /**
     * Join QQ group
     */
    @JvmStatic
    fun joinQQGroup(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Const.QQ_GROUP_URL))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, R.string.prompt_join_qq_group_failed, Toast.LENGTH_SHORT).show()
        }
    }


    @JvmStatic
    fun showAppDetailsInCoolApk(context: Context) {
        val packageState = checkPackageState(context, Const.COOL_MARKET_PACKAGE_NAME)
        when (packageState) {
            PACKAGE_ENABLED -> {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.data = Uri.parse("market://details?id=" + BuildConfig.APPLICATION_ID)
                intent.setPackage(Const.COOL_MARKET_PACKAGE_NAME)
                context.startActivity(intent)
            }
            PACKAGE_DISABLED -> {
                Toast.makeText(context, R.string.coolapk_enable_prompt, Toast.LENGTH_SHORT).show()
            }
            PACKAGE_NOT_INSTALLED -> {
                Toast.makeText(context, R.string.coolapk_install_prompt, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
