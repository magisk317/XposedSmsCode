package com.github.magisk317.smscode.common.constant

import android.Manifest
import android.os.Build
import com.github.magisk317.smscode.runtime.BuildConfig
import java.util.ArrayList
import java.util.HashMap

/**
 * Permission Constants
 */
object PermConst {

    @JvmField
    val PACKAGE_PERMISSIONS: Map<String, List<String>> = HashMap()

    init {
        val smsCodePermissions: MutableList<String> = ArrayList()

        // Backup import or export
        smsCodePermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        smsCodePermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        smsCodePermissions.add(Manifest.permission.READ_CONTACTS)

        val smsCodePackage = BuildConfig.APPLICATION_ID
        (PACKAGE_PERMISSIONS as MutableMap)[smsCodePackage] = smsCodePermissions

        val phonePermissions: MutableList<String> = ArrayList()
        // permission for InputManager#injectInputEvent();
        phonePermissions.add("android.permission.INJECT_EVENTS")

        // permission for kill background process - ActivityManagerService#killBackgroundProcesses()
        phonePermissions.add(Manifest.permission.KILL_BACKGROUND_PROCESSES)

        // READ_SMS for Mark SMS as read & Delete extracted verification SMS
        phonePermissions.add(Manifest.permission.READ_SMS)
        // api version < android M
        phonePermissions.add("android.permission.WRITE_SMS")

        // Permission for grant AppOps permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Android P
            phonePermissions.add("android.permission.MANAGE_APP_OPS_MODES")
        } else {
            // android 4.4 ~ 8.1
            phonePermissions.add("android.permission.UPDATE_APP_OPS_STATS")
        }

        PACKAGE_PERMISSIONS["com.android.phone"] = phonePermissions
        PACKAGE_PERMISSIONS["com.xiaomi.phone"] = phonePermissions
    }
}
