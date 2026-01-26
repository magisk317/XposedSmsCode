package com.tianma.xsmscode.xp.hook.permission

import android.os.UserHandle
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
 * Since Android 12 & 12L(API 31~32)<br/>
 * Hook com.android.server.pm.permission.PermissionManagerService
 */
class PermissionManagerServiceHook31(classLoader: ClassLoader) : BaseSubHook(classLoader) {

    @RequiresApi(31)
    override fun startHook() {
        try {
            hookGrantPermissions()
        } catch (e: Throwable) {
            XLog.e("Failed to hook PermissionManagerService", e)
        }
    }

    private fun hookGrantPermissions() {
        XLog.d("Hooking grantPermissions() for Android 31+")
        val method = findTargetMethod()
        if (method == null) {
            XLog.e("Cannot find the method to grant relevant permission")
            return
        }
        XposedBridge.hookMethod(method, object : MethodHookWrapper() {
            @Throws(Throwable::class)
            override fun after(param: MethodHookParam) {
                afterGrantPermissionsSinceAndroid12(param)
            }
        })
    }

    private fun findTargetMethod(): Method? {
        val pmsClass = XposedHelpers.findClass(CLASS_PERMISSION_MANAGER_SERVICE, mClassLoader)
        val androidPackageClass = XposedHelpers.findClass(CLASS_ANDROID_PACKAGE, mClassLoader)
        val callbackClass = XposedHelpers.findClassIfExists(CLASS_PERMISSION_CALLBACK, mClassLoader)

        // 精确匹配
        var method = XposedHelpers.findMethodExactIfExists(
            pmsClass, "restorePermissionState",
            /* AndroidPackage pkg          */ androidPackageClass,
            /* boolean replace             */ Boolean::class.javaPrimitiveType,
            /* String packageOfInterest    */ String::class.java,
            /* PermissionCallback callback */ callbackClass,
            /* int filterUserId            */ Int::class.javaPrimitiveType
        )

        if (method == null) { // method restorePermissionState() not found
            // 参数类型精确匹配
            val methods = XposedHelpers.findMethodsByExactParameters(
                pmsClass, Void.TYPE,
                /* AndroidPackage pkg          */ androidPackageClass,
                /* boolean replace             */ Boolean::class.javaPrimitiveType,
                /* String packageOfInterest    */ String::class.java,
                /* PermissionCallback callback */ callbackClass,
                /* int filterUserId            */ Int::class.javaPrimitiveType
            )
            if (methods != null && methods.isNotEmpty()) {
                method = methods[0]
            }
        }
        return method
    }

    @Suppress("UNCHECKED_CAST")
    private fun afterGrantPermissionsSinceAndroid12(param: XC_MethodHook.MethodHookParam) {
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

                // Manifest.xml 中声明的permission列表
                val requestedPermissions = XposedHelpers.callMethod(pkg, "getRequestedPermissions") as List<String>

                // com.android.server.pm.permission.DevicePermissionState 对象
                val mState = XposedHelpers.getObjectField(permissionManagerService, "mState")

                // UserHandle.USER_ALL
                val filterUserId = param.args[4] as Int
                val userAll = XposedHelpers.getStaticIntField(UserHandle::class.java, "USER_ALL")
                val userIds = if (filterUserId == userAll) {
                    XposedHelpers.callMethod(permissionManagerService, "getAllUserIds") as IntArray
                } else {
                    intArrayOf(filterUserId)
                }

                val permissionsToGrant = PACKAGE_PERMISSIONS[packageName] ?: continue

                for (userId in userIds) {
                    // com.android.server.pm.permission.UserPermissionState 对象
                    val userState = XposedHelpers.callMethod(mState, "getOrCreateUserState", userId)
                    val appId = XposedHelpers.callMethod(ps, "getAppId") as Int
                    //  com.android.server.pm.permission.UidPermissionState 对象
                    val uidState = XposedHelpers.callMethod(userState, "getOrCreateUidState", appId)

                    // com.android.server.pm.permission.PermissionRegistry 对象
                    val mRegistry = XposedHelpers.getObjectField(permissionManagerService, "mRegistry")

                    for (permissionToGrant in permissionsToGrant) {
                        if (!requestedPermissions.contains(permissionToGrant)) {
                            val granted = XposedHelpers.callMethod(uidState, "isPermissionGranted", permissionToGrant) as Boolean
                            if (!granted) {
                                // permission not grant before
                                val bpToGrant = XposedHelpers.callMethod(mRegistry, "getPermission", permissionToGrant)
                                val result = XposedHelpers.callMethod(uidState, "grantPermission", bpToGrant) as Boolean
                                XLog.d("Add $permissionToGrant; result = $result")
                            } else {
                                // permission has been granted already
                                XLog.d("Already have $permissionToGrant permission")
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val CLASS_PERMISSION_MANAGER_SERVICE = "com.android.server.pm.permission.PermissionManagerService"
        private const val CLASS_ANDROID_PACKAGE = "com.android.server.pm.parsing.pkg.AndroidPackage"
        private const val CLASS_PERMISSION_CALLBACK = "com.android.server.pm.permission.PermissionManagerService.PermissionCallback"
    }
}
