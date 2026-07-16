package com.github.magisk317.smscode.common.utils

import android.content.Context
import io.github.magisk317.xposed.logging.CallerGuard
import io.github.magisk317.xposed.logging.PackageCallerGuard

object ProviderCallerGuard {
    private val TRUSTED_HOOK_PACKAGES = setOf(
        "android",
        "system",
        "com.android.phone",
        "com.xiaomi.phone",
        "com.android.providers.telephony",
        "com.android.mms",
        "com.google.android.apps.messaging",
    )

    private val delegate = PackageCallerGuard(TRUSTED_HOOK_PACKAGES)

    fun isCallerAllowed(context: Context): Boolean = delegate.isCallerAllowed(context)

    fun isPrivilegedUid(uid: Int, appUid: Int?): Boolean =
        PackageCallerGuard.isPrivilegedUid(uid, appUid)

    fun isTrustedSystemPackage(packageName: String, flags: Int): Boolean =
        delegate.isPackageAllowed(packageName, flags)

    fun isSystemPackageFlags(flags: Int): Boolean = CallerGuard.isSystemPackageFlags(flags)

    internal val trustedHookPackages: Set<String>
        get() = TRUSTED_HOOK_PACKAGES
}
