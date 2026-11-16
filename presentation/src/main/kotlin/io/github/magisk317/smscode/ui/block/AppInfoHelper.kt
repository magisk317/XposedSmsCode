package io.github.magisk317.smscode.ui.block

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import io.github.magisk317.smscode.runtime.bridge.UiAppConfig

object AppInfoHelper {
    fun getAppInfo(pm: PackageManager, packageInfo: PackageInfo): UiAppConfig {
        val info = io.github.magisk317.uikit.shell.AppInfoHelper.getAppInfo(pm, packageInfo)
        return UiAppConfig(packageName = info.packageName, label = info.label)
    }

    fun getAppInfo(pm: PackageManager, applicationInfo: ApplicationInfo): UiAppConfig {
        val info = io.github.magisk317.uikit.shell.AppInfoHelper.getAppInfo(pm, applicationInfo)
        return UiAppConfig(packageName = info.packageName, label = info.label)
    }
}
