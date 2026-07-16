package com.github.magisk317.smscode.common.utils

import android.content.pm.ApplicationInfo
import android.os.Process
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ProviderCallerGuardTest {
    @Test
    fun isPrivilegedUid_allowsSystemAndSelf() {
        assertTrue(ProviderCallerGuard.isPrivilegedUid(Process.SYSTEM_UID, appUid = 12345))
        assertTrue(ProviderCallerGuard.isPrivilegedUid(12345, appUid = 12345))
    }

    @Test
    fun isPrivilegedUid_rejectsOrdinaryCaller() {
        assertFalse(ProviderCallerGuard.isPrivilegedUid(20000, appUid = 12345))
        assertFalse(ProviderCallerGuard.isPrivilegedUid(20000, appUid = null))
    }

    @Test
    fun explicitPackagePolicy_requiresTrustedNameAndSystemInstall() {
        ProviderCallerGuard.trustedHookPackages.forEach { packageName ->
            assertTrue(
                ProviderCallerGuard.isTrustedSystemPackage(
                    packageName,
                    ApplicationInfo.FLAG_SYSTEM,
                ),
            )
            assertTrue(
                ProviderCallerGuard.isTrustedSystemPackage(
                    packageName,
                    ApplicationInfo.FLAG_UPDATED_SYSTEM_APP,
                ),
            )
            assertFalse(ProviderCallerGuard.isTrustedSystemPackage(packageName, flags = 0))
        }
        assertFalse(
            ProviderCallerGuard.isTrustedSystemPackage(
                "com.android.settings",
                ApplicationInfo.FLAG_SYSTEM,
            ),
        )
        assertFalse(
            ProviderCallerGuard.isTrustedSystemPackage(
                "com.example.attacker",
                ApplicationInfo.FLAG_SYSTEM,
            ),
        )
    }

    @Test
    fun explicitPackagePolicy_staysAlignedWithXposedScope() {
        val scopeFile = sequenceOf(
            File("app/src/main/resources/META-INF/xposed/scope.list"),
            File("../app/src/main/resources/META-INF/xposed/scope.list"),
        ).first(File::isFile)
        val scopePackages = scopeFile.readLines().filter(String::isNotBlank).toSet()

        assertTrue(ProviderCallerGuard.trustedHookPackages.containsAll(scopePackages))
    }
}
