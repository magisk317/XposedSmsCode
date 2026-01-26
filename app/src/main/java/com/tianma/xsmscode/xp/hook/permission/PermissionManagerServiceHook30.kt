package com.tianma.xsmscode.xp.hook.permission

import androidx.annotation.RequiresApi
import com.tianma.xsmscode.common.constant.PermConst.PACKAGE_PERMISSIONS
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.xp.helper.MethodHookWrapper
import com.tianma.xsmscode.xp.hook.BaseSubHook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method

/**
 * Since Android 11(API 30)<br/>
 * Hook com.android.server.pm.permission.PermissionManagerService
 */
class PermissionManagerServiceHook30(classLoader: ClassLoader) : BaseSubHook(classLoader) {

    @RequiresApi(30)
    override fun startHook() {
        try {
            hookGrantPermissions()
        } catch (e: Throwable) {
            XLog.e("Failed to hook PermissionManagerService", e)
        }
    }

    private fun hookGrantPermissions() {
        XLog.d("Hooking grantPermissions() for Android 30+")
        val method = findTargetMethod()
        if (method == null) {
            XLog.e("Cannot find the method to grant relevant permission")
            return
        }
        XposedBridge.hookMethod(method, object : MethodHookWrapper() {
            @Throws(Throwable::class)
            override fun after(param: MethodHookParam) {
                afterGrantPermissionsSinceAndroid11(param)
            }
        })
    }

    private fun findTargetMethod(): Method? {
        val pmsClass = XposedHelpers.findClass(CLASS_PERMISSION_MANAGER_SERVICE, mClassLoader)
        val androidPackageClass = XposedHelpers.findClass(CLASS_ANDROID_PACKAGE, mClassLoader)
        val callbackClass = XposedHelpers.findClassIfExists(CLASS_PERMISSION_CALLBACK, mClassLoader)

        var method = XposedHelpers.findMethodExactIfExists(
            pmsClass, "restorePermissionState",
            /* AndroidPackage pkg   */ androidPackageClass,
            /* boolean replace             */ Boolean::class.javaPrimitiveType,
            /* String packageOfInterest    */ String::class.java,
            /* PermissionCallback callback */ callbackClass
        )

        if (method == null) { // method restorePermissionState() not found
            val methods = XposedHelpers.findMethodsByExactParameters(
                pmsClass, Void.TYPE,
                /* AndroidPackage pkg   */ androidPackageClass,
                /* boolean replace             */ Boolean::class.javaPrimitiveType,
                /* String packageOfInterest    */ String::class.java,
                /* PermissionCallback callback */ callbackClass
            )
            if (methods != null && methods.isNotEmpty()) {
                method = methods[0]
            }
        }
        return method
    }

    @Suppress("UNCHECKED_CAST")
    private fun afterGrantPermissionsSinceAndroid11(param: XC_MethodHook.MethodHookParam) {
        // com.android.server.pm.parsing.pkg.AndroidPackage 对象
        val pkg = param.args[0]
        val packageNameInPkg = XposedHelpers.callMethod(pkg, "getPackageName") as String

        for (packageName in PACKAGE_PERMISSIONS.keys) {
            if (packageName == packageNameInPkg) {
                XLog.d("PackageName: %s", packageName)

                // PermissionManagerService 对象
                val permissionManagerService = param.thisObject
                // PackageManagerInternal 对象 mPackageManagerInt
                val mPackageManagerInt = XposedHelpers.getObjectField(permissionManagerService, "mPackageManagerInt")

                // PackageSetting 对象 ps
                val ps = XposedHelpers.callMethod(mPackageManagerInt, "getPackageSetting", packageName)

                // com.android.server.pm.permission.PermissionsState 对象
                val permissionsState = XposedHelpers.callMethod(ps, "getPermissionsState")

                // Manifest.xml 中声明的permission列表
                val requestedPermissions = XposedHelpers.callMethod(pkg, "getRequestedPermissions") as List<String>

                // com.android.server.pm.permission.PermissionSettings mSettings 对象
                val settings = XposedHelpers.getObjectField(permissionManagerService, "mSettings")
                // ArrayMap<String, com.android.server.pm.permission.BasePermission> mPermissions 对象
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
        private const val CLASS_ANDROID_PACKAGE = "com.android.server.pm.parsing.pkg.AndroidPackage"
        private const val CLASS_PERMISSION_CALLBACK = "com.android.server.pm.permission.PermissionManagerServiceInternal.PermissionCallback"
    }
}
