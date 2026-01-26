package com.tianma.xsmscode.ui.block

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.tianma.xsmscode.data.db.entity.AppInfo

object AppInfoHelper {
    fun getAppInfo(pm: PackageManager, packageInfo: PackageInfo): AppInfo {
        val appInfo = packageInfo.applicationInfo
        val label = if (appInfo != null) pm.getApplicationLabel(appInfo).toString() else packageInfo.packageName
        val packageName = packageInfo.packageName
        return AppInfo(label, packageName)
    }

    fun getAppInfo(pm: PackageManager, applicationInfo: ApplicationInfo): AppInfo {
        val label = pm.getApplicationLabel(applicationInfo).toString()
        val packageName = applicationInfo.packageName
        return AppInfo(label, packageName)
    }
}
