package com.github.magisk317.smscode.xp.hook.code.action.impl

import android.app.ActivityManager
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppOwnedNotificationDeliveryGuardTest {

    @Test
    fun isXiaomiFamily_matchesXiaomiBrands() {
        assertTrue(AppOwnedNotificationDeliveryGuard.isXiaomiFamily("Xiaomi", null))
        assertTrue(AppOwnedNotificationDeliveryGuard.isXiaomiFamily(null, "Redmi"))
        assertTrue(AppOwnedNotificationDeliveryGuard.isXiaomiFamily(null, "POCO"))
    }

    @Test
    fun isXiaomiFamily_ignoresOtherBrands() {
        assertFalse(AppOwnedNotificationDeliveryGuard.isXiaomiFamily("Google", "Pixel"))
        assertFalse(AppOwnedNotificationDeliveryGuard.isXiaomiFamily(null, null))
    }

    @Test
    fun isBackgroundOrCached_onlyMatchesProcessesBelowVisible() {
        assertFalse(
            AppOwnedNotificationDeliveryGuard.isBackgroundOrCached(
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
            ),
        )
        assertFalse(
            AppOwnedNotificationDeliveryGuard.isBackgroundOrCached(
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE,
            ),
        )
        assertTrue(
            AppOwnedNotificationDeliveryGuard.isBackgroundOrCached(
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED,
            ),
        )
    }
}
