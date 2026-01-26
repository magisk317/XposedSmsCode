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
 * Android 4.4 ~ Android 8.1 (API 19 - 27)<br/>
 * Hook com.android.server.pm.PackageManagerService
 */
class PackageManagerServiceHook(classLoader: ClassLoader) : BaseSubHook(classLoader) {

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override fun startHook() {
        try {
            hookGrantPermissionsLPw()
        } catch (e: Throwable) {
            XLog.e("Failed to hook PackageManagerService", e)
        }
    }

    private fun hookGrantPermissionsLPw() {
        val pmsClass = XposedWrapper.findClass(CLASS_PACKAGE_MANAGER_SERVICE, mClassLoader)
        val method: Method = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Android 5.0 +
            XLog.d("Hooking grantPermissionsLPw() for Android 21+")
            XposedHelpers.findMethodExact(
                pmsClass, "grantPermissionsLPw",
                /* PackageParser.Package pkg */ CLASS_PACKAGE_PARSER_PACKAGE,
                /* boolean replace           */ Boolean::class.javaPrimitiveType,
                /* String packageOfInterest  */ String::class.java
            )
        } else {
            // Android 4.4 +
            XLog.d("Hooking grantPermissionsLPw() for Android 19+")
            XposedHelpers.findMethodExact(
                pmsClass, "grantPermissionsLPw",
                /* PackageParser.Package pkg */ CLASS_PACKAGE_PARSER_PACKAGE,
                /* boolean replace           */ Boolean::class.javaPrimitiveType
            )
        }

        XposedBridge.hookMethod(method, object : MethodHookWrapper() {
            override fun after(param: MethodHookParam) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    grantPermissionsLPwSinceM(param)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    grantPermissionsLPwSinceK(param)
                }
            }
        })
    }

    companion object {
        private const val CLASS_PACKAGE_MANAGER_SERVICE = "com.android.server.pm.PackageManagerService"
        private const val CLASS_PACKAGE_PARSER_PACKAGE = "android.content.pm.PackageParser.Package"

        @Suppress("UNCHECKED_CAST")
        private fun grantPermissionsLPwSinceM(param: XC_MethodHook.MethodHookParam) {
            // API 23 (Android 6.0)
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

        @Suppress("UNCHECKED_CAST")
        private fun grantPermissionsLPwSinceK(param: XC_MethodHook.MethodHookParam) {
            // API 19 (Android 4.4)
            val pkg = param.args[0]
            val packageNameInPkg = XposedHelpers.getObjectField(pkg, "packageName") as String

            for (packageName in PACKAGE_PERMISSIONS.keys) {
                if (packageName == packageNameInPkg) {
                    XLog.d("PackageName: %s", packageName)
                    val extra = XposedHelpers.getObjectField(pkg, "mExtras")
                    val grantedPermissions = XposedHelpers.getObjectField(extra, "grantedPermissions") as MutableSet<String>
                    val settings = XposedHelpers.getObjectField(param.thisObject, "mSettings")
                    val permissions = XposedHelpers.getObjectField(settings, "mPermissions")

                    val permissionsToGrant = PACKAGE_PERMISSIONS[packageName] ?: continue
                    for (permissionToGrant in permissionsToGrant) {
                        if (!grantedPermissions.contains(permissionToGrant)) {
                            val bpToGrant = XposedHelpers.callMethod(permissions, "get", permissionToGrant)
                            grantedPermissions.add(permissionToGrant)

                            val gpGids = XposedHelpers.getObjectField(extra, "gids") as IntArray
                            val bpGids = XposedHelpers.getObjectField(bpToGrant, "gids") as IntArray
                            XposedHelpers.callStaticMethod(param.thisObject.javaClass, "appendInts", gpGids, bpGids)

                            XLog.d("Add $bpToGrant")
                        } else {
                            XLog.d("Already have $permissionToGrant permission")
                        }
                    }
                }
            }
        }
    }
}
