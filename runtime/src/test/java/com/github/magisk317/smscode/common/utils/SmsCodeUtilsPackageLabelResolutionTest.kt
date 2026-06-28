package com.github.magisk317.smscode.common.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SmsCodeUtilsPackageLabelResolutionTest {

    @Test
    fun findPackageNameByLabel_matchesWhenAppLabelContainsCompanyName() {
        val context = mockk<Context>(relaxed = true)
        val packageManager = mockk<PackageManager>(relaxed = true)
        val appInfo = ApplicationInfo().apply {
            packageName = "com.bank.app"
        }

        every { context.packageManager } returns packageManager
        every { packageManager.getInstalledApplications(PackageManager.MATCH_ALL) } returns listOf(appInfo)
        every { packageManager.getApplicationLabel(appInfo) } returns "Bank Security"

        val resolved = SmsCodeUtils.findPackageNameByLabel(context, "Bank")

        assertEquals("com.bank.app", resolved)
    }
}
