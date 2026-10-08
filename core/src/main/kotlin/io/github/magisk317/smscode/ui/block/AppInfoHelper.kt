package io.github.magisk317.smscode.ui.block

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import io.github.magisk317.smscode.db.entity.AppInfo

object AppInfoHelper {
    fun getAppInfo(pm: PackageManager, packageInfo: PackageInfo): AppInfo {
        val info = io.github.magisk317.uikit.shell.AppInfoHelper.getAppInfo(pm, packageInfo)
        return AppInfo(info.packageName, info.label)
    }

    fun getAppInfo(pm: PackageManager, applicationInfo: ApplicationInfo): AppInfo {
        val info = io.github.magisk317.uikit.shell.AppInfoHelper.getAppInfo(pm, applicationInfo)
        return AppInfo(info.packageName, info.label)
    }
}
