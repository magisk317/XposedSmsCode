package com.github.magisk317.smscode.xp.hook.code.action.impl

import android.app.ActivityManager
import android.content.Context
import android.os.Build

internal object AppOwnedNotificationDeliveryGuard {
    fun shouldFallbackToPhoneOwned(
        context: Context,
        targetPackage: String,
    ): Boolean {
        if (!isXiaomiFamily(Build.MANUFACTURER, Build.BRAND)) return false
        val importance = resolveProcessImportance(context, targetPackage) ?: return false
        return isBackgroundOrCached(importance)
    }

    internal fun isXiaomiFamily(manufacturer: String?, brand: String?): Boolean {
        return listOf(manufacturer, brand)
            .filterNotNull()
            .map { it.lowercase() }
            .any { value ->
                value.contains("xiaomi") ||
                    value.contains("redmi") ||
                    value.contains("poco")
            }
    }

    internal fun isBackgroundOrCached(importance: Int): Boolean {
        return importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
    }

    private fun resolveProcessImportance(context: Context, targetPackage: String): Int? {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return null
        val processes = runCatching { manager.runningAppProcesses.orEmpty() }
            .getOrElse { return null }
        return processes.asSequence()
            .filter { process ->
                process.processName == targetPackage ||
                    process.pkgList.orEmpty().contains(targetPackage)
            }
            .map { process -> process.importance }
            .minOrNull()
    }
}
