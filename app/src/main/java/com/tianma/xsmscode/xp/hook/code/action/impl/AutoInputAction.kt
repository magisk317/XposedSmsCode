package com.tianma.xsmscode.xp.hook.code.action.impl

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.data.db.DBProvider
import com.tianma.xsmscode.data.db.entity.AppInfo
import com.tianma.xsmscode.data.db.entity.SmsMsg
import com.tianma.xsmscode.feature.store.EntityStoreManager
import com.tianma.xsmscode.feature.store.EntityType
import com.tianma.xsmscode.xp.hook.code.action.CallableAction
import com.tianma.xsmscode.xp.hook.code.helper.InputHelper
import java.util.*

/**
 * 自动输入验证码
 */
class AutoInputAction(pluginContext: Context, phoneContext: Context, smsMsg: SmsMsg) :
    CallableAction(pluginContext, phoneContext, smsMsg) {

    override fun action(): Bundle? {
        prepareAutoInputCode(mSmsMsg.smsCode)
        return null
    }

    private fun prepareAutoInputCode(code: String?) {
        if (!autoInputBlockedHere()) {
            autoInputCode(code)
        }
    }

    // auto-input
    @Suppress("TooGenericExceptionCaught")
    private fun autoInputCode(code: String?) {
        try {
            val autoEnter = mPluginContext.getSharedPreferences("xposed_prefs", Context.MODE_PRIVATE)
                .getBoolean(com.tianma.xsmscode.common.constant.PrefConst.KEY_ENABLE_AUTO_ENTER_CODE, false)
            InputHelper.sendText(mPhoneContext, code, autoEnter)
            XLog.d("Auto input code succeed, autoEnter: $autoEnter")
        } catch (throwable: Throwable) {
            XLog.e("Error occurs when auto input code", throwable)
        }
    }

    // 是否屏蔽自动输入
    @Suppress("TooGenericExceptionCaught")
    private fun autoInputBlockedHere(): Boolean {
        var result = false
        try {
            val blockedAppList = mutableListOf<String>()
            try {
                val appInfoUri = DBProvider.APP_INFO_URI
                val resolver = mPluginContext.contentResolver

                val packageColumn = "package_name"
                val blockedColumn = "blocked"

                val projection = arrayOf(packageColumn)
                val selection = "$blockedColumn = ?"
                val selectionArgs = arrayOf("1")
                val cursor = resolver.query(appInfoUri, projection, selection, selectionArgs, null)
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        blockedAppList.add(cursor.getString(cursor.getColumnIndexOrThrow(packageColumn)))
                    }
                    cursor.close()
                }
                XLog.d("Get blocked apps by content provider")
            } catch (ignored: Exception) {
                val appInfoList = EntityStoreManager.loadEntitiesFromFile(
                    mPluginContext,
                    EntityType.BLOCKED_APP,
                    AppInfo::class.java,
                )
                for (appInfo in appInfoList) {
                    blockedAppList.add(appInfo.packageName)
                }
                XLog.d("Get blocked apps from file")
            }

            if (blockedAppList.isEmpty()) {
                return false
            }

            val runningTasks = getRunningTasks(mPhoneContext)
            var topPkgPrimary: String? = null
            if (runningTasks != null && runningTasks.isNotEmpty()) {
                topPkgPrimary = runningTasks[0].topActivity?.packageName
                XLog.d("topPackagePrimary: %s", topPkgPrimary)
            }

            if (topPkgPrimary != null && blockedAppList.contains(topPkgPrimary)) {
                return true
            }

            // RunningAppProcess 判断当前的进程不是很准确，所以用作次要参考
            val appProcesses = getRunningAppProcesses(mPhoneContext) ?: return false

            val topPkgSecondary = appProcesses[0].pkgList
            val topProcessSecondary = appProcesses[0].processName
            XLog.d("topProcessSecondary: %s, topPackages: %s", topProcessSecondary, Arrays.toString(topPkgSecondary))

            if (blockedAppList.contains(topProcessSecondary)) {
                result = true
            } else {
                for (topPackage in topPkgSecondary) {
                    if (blockedAppList.contains(topPackage)) {
                        result = true
                        break
                    }
                }
            }
        } catch (t: Throwable) {
            XLog.e("", t)
        }
        return result
    }

    private fun getRunningAppProcesses(context: Context): List<ActivityManager.RunningAppProcessInfo>? {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        return am?.runningAppProcesses
    }

    @Suppress("DEPRECATION")
    private fun getRunningTasks(context: Context): List<ActivityManager.RunningTaskInfo>? {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        return am?.getRunningTasks(10)
    }
}
