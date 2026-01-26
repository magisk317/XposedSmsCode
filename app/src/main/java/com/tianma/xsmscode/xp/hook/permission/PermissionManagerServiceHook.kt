package com.tianma.xsmscode.xp.hook.permission

import android.os.Build
import androidx.annotation.RequiresApi
import com.tianma.xsmscode.common.constant.PermConst.PACKAGE_PERMISSIONS
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.xp.helper.MethodHookWrapper
import com.tianma.xsmscode.xp.helper.XposedWrapper
import com.tianma.xsmscode.xp.hook.BaseSubHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method

/**
 * Since Android P(API 28)<br/>
 * Hook com.android.server.pm.permission.PermissionManagerService
 */
class PermissionManagerServiceHook(classLoader: ClassLoader) : BaseSubHook(classLoader) {

    @RequiresApi(Build.VERSION_CODES.P)
    override fun startHook() {
        try {
            hookGrantPermissions()
        } catch (e: Throwable) {
            XLog.e("Failed to hook PermissionManagerService", e)
        }
    }

    private fun hookGrantPermissions() {
        XLog.d("Hooking grantPermissions() for Android 28+")
        val method = findTargetMethod()
        XposedBridge.hookMethod(method, object : MethodHookWrapper() {
            @Throws(Throwable::class)
            override fun after(param: MethodHookParam) {
                afterGrantPermissionsSinceP(param)
            }
        })
    }

    private fun findTargetMethod(): Method? {
        val pmsClass = XposedHelpers.findClass(CLASS_PERMISSION_MANAGER_SERVICE, mClassLoader)
        val packageClass = XposedHelpers.findClass(CLASS_PACKAGE_PARSER_PACKAGE, mClassLoader)
        var callbackClass = XposedHelpers.findClassIfExists(CLASS_PERMISSION_CALLBACK, mClassLoader)
        if (callbackClass == null) {
            // Android Q PermissionCallback 不一样
            callbackClass = XposedWrapper.findClass(CLASS_PERMISSION_CALLBACK_Q, mClassLoader)
        }

        var method = XposedHelpers.findMethodExactIfExists(
            pmsClass, "grantPermissions",
            /* PackageParser.Package pkg   */ packageClass,
            /* boolean replace             */ Boolean::class.javaPrimitiveType,
            /* String packageOfInterest    */ String::class.java,
            /* PermissionCallback callback */ callbackClass
        )

        if (method == null) { // method grantPermissions() not found
            // Android Q
            method = XposedHelpers.findMethodExactIfExists(
                pmsClass, "restorePermissionState",
                /* PackageParser.Package pkg   */ packageClass,
                /* boolean replace             */ Boolean::class.javaPrimitiveType,
                /* String packageOfInterest    */ String::class.java,
                /* PermissionCallback callback */ callbackClass
            )
            if (method == null) { // method restorePermissionState() not found
                val methods = XposedHelpers.findMethodsByExactParameters(
                    pmsClass, Void.TYPE,
                    /* PackageParser.Package pkg   */ packageClass,
                    /* boolean replace             */ Boolean::class.javaPrimitiveType,
                    /* String packageOfInterest    */ String::class.java,
                    /* PermissionCallback callback */ callbackClass
                )
                if (methods != null && methods.isNotEmpty()) {
                    method = methods[0]
                }
            }
        }
        return method
    }

    @Suppress("UNCHECKED_CAST")
    private fun afterGrantPermissionsSinceP(param: XC_MethodHook.MethodHookParam) {
        // android.content.pm.PackageParser.Package 对象
        val pkg = param.args[0]
        val packageNameInPkg = XposedHelpers.getObjectField(pkg, "packageName") as String

        for (packageName in PACKAGE_PERMISSIONS.keys) {
            if (packageName == packageNameInPkg) {
                XLog.d("PackageName: %s", packageName)
                val extras = XposedHelpers.getObjectField(pkg, "mExtras")
                val permissionsState = XposedHelpers.callMethod(extras, "getPermissionsState")
                val requestedPermissions = XposedHelpers.getObjectField(pkg, "requestedPermissions") as List<String>
                val settings = XposedHelpers.getObjectField(param.thisObject, "mSettings")
                val permissions = XposedHelpers.getObjectField(settings, "mPermissions")

                val permissionsToGrant = PACKAGE_PERMISSIONS[packageName] ?: continue
                for (permissionToGrant in permissionsToGrant) {
                    if (!requestedPermissions.contains(permissionToGrant)) {
                        val granted = XposedHelpers.callMethod(permissionsState, "hasInstallPermission", permissionToGrant) as Boolean
                        if (!granted) {
                            val bpToGrant = XposedHelpers.callMethod(permissions, "get", permissionToGrant)
                            val result = XposedHelpers.callMethod(permissionsState, "grantInstallPermission", bpToGrant) as Int
                            XLog.d("Add $bpToGrant; result = $result")
                        } else {
                            XLog.d("Already have $permissionToGrant permission")
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val CLASS_PERMISSION_MANAGER_SERVICE = "com.android.server.pm.permission.PermissionManagerService"
        private const val CLASS_PERMISSION_CALLBACK = "com.android.server.pm.permission.PermissionManagerInternal.PermissionCallback"
        private const val CLASS_PACKAGE_PARSER_PACKAGE = "android.content.pm.PackageParser.Package"
        private const val CLASS_PERMISSION_CALLBACK_Q = "com.android.server.pm.permission.PermissionManagerServiceInternal.PermissionCallback"
    }
}
