package com.tianma.xsmscode.xp.hook.permission

import android.os.Build
import com.tianma.xsmscode.xp.hook.BaseHook
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Hook com.android.server.pm.PackageManagerService to grant permissions.
 */
class PermissionGranterHook : BaseHook() {

    override fun onLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (ANDROID_PACKAGE == lpparam.packageName && ANDROID_PACKAGE == lpparam.processName) {
            val classLoader = lpparam.classLoader

            val sdkInt = Build.VERSION.SDK_INT
            when {
                sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                    PermissionManagerServiceHook34(classLoader).startHook()
                }
                sdkInt >= Build.VERSION_CODES.TIRAMISU -> { // Android 13+
                    PermissionManagerServiceHook33(classLoader).startHook()
                }
                sdkInt >= Build.VERSION_CODES.S -> { // Android 12~12L
                    PermissionManagerServiceHook31(classLoader).startHook()
                }
                sdkInt >= Build.VERSION_CODES.R -> { // Android 11
                    PermissionManagerServiceHook30(classLoader).startHook()
                }
                sdkInt >= Build.VERSION_CODES.P -> { // Android 9.0~10
                    PermissionManagerServiceHook(classLoader).startHook()
                }
                else -> { // Android 5.0 ~ 8.1
                    PackageManagerServiceHook(classLoader).startHook()
                }
            }
        }
    }

    companion object {
        const val ANDROID_PACKAGE = "android"
    }
}
