package io.github.magisk317.smscode.common.utils

import android.content.Context
import io.github.magisk317.xposed.logging.CallerGuard
import io.github.magisk317.xposed.logging.PackageCallerGuard

object ProviderCallerGuard {
    // Platform-structural entries only (uid level + platform-signed system packages).
    // Hooked third-party SMS apps (e.g. Google Messages installed as a normal app)
    // authenticate through the per-install IPC token instead; see ProviderIpcTokenGate.
    private val TRUSTED_HOOK_PACKAGES = setOf(
        "android",
        "system",
        "com.android.phone",
        "com.android.providers.telephony",
        "com.android.mms",
    )

    /**
     * Hooked SMS apps that authenticate to the provider with the module IPC token
     * rather than the system-package allowlist. They stay in the Xposed scope; every
     * hooked package must be reachable through exactly one of the two sets.
     */
    internal val TOKEN_ONLY_HOOK_PACKAGES = setOf(
        "com.xiaomi.phone",
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
