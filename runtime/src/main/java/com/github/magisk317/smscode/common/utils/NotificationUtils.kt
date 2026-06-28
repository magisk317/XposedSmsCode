package com.github.magisk317.smscode.common.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object NotificationUtils {

    data class DeliveryDiagnostics(
        val notificationsEnabled: Boolean,
        val postNotificationsGranted: Boolean,
        val channelImportance: Int?,
    ) {
        val canPost: Boolean
            get() = notificationsEnabled &&
                postNotificationsGranted &&
                channelImportance != NotificationManager.IMPORTANCE_NONE

        fun summary(): String {
            return "enabled=$notificationsEnabled permission=$postNotificationsGranted channel=${importanceLabel(channelImportance)}"
        }
    }

    @JvmStatic
    fun createNotificationChannel(context: Context, channelId: String, channelName: String, importance: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
            ?: return
        // Android does not restore a deleted channel via createNotificationChannel().
        // If the channel was previously deleted (getNotificationChannel returns null but
        // the deleted record persists internally), we must explicitly delete-then-recreate.
        // Note: getNotificationChannel() may throw SecurityException when called from a
        // different process (e.g., com.android.mms trying to read channels for SmsCode app).
        // We catch this and fall back to direct creation.
        val existing = runCatching { manager.getNotificationChannel(channelId) }.getOrNull()
        if (existing == null) {
            // Channel either never existed, was deleted, or we can't read it —
            // force delete to clear the internal "deleted" marker, then create fresh.
            runCatching { manager.deleteNotificationChannel(channelId) }
        }
        val channel = NotificationChannel(channelId, channelName, importance)
        manager.createNotificationChannel(channel)
    }

    @JvmStatic
    fun inspectDelivery(context: Context, channelId: String): DeliveryDiagnostics {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        val channelImportance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.getNotificationChannel(channelId)?.importance
        } else {
            null
        }
        return DeliveryDiagnostics(
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            postNotificationsGranted = hasPostNotificationsPermission(context),
            channelImportance = channelImportance,
        )
    }

    @JvmStatic
    fun hasPostNotificationsPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun importanceLabel(importance: Int?): String {
        return when (importance) {
            null -> "missing"
            NotificationManager.IMPORTANCE_NONE -> "none"
            NotificationManager.IMPORTANCE_MIN -> "min"
            NotificationManager.IMPORTANCE_LOW -> "low"
            NotificationManager.IMPORTANCE_DEFAULT -> "default"
            NotificationManager.IMPORTANCE_HIGH -> "high"
            NotificationManager.IMPORTANCE_MAX -> "max"
            else -> importance.toString()
        }
    }
}
