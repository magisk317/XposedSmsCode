package com.github.magisk317.smscode.xp.helper

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import com.github.magisk317.smscode.hook.BuildConfig
import com.github.magisk317.smscode.hook.R
import com.github.magisk317.smscode.common.constant.NotificationConst
import com.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge
import com.github.magisk317.smscode.xp.hook.code.helper.InputHelper
import io.github.magisk317.smscode.verification.ConflictNotificationHelper
import io.github.magisk317.smscode.verification.RecentEventIdTracker
import io.github.magisk317.smscode.xposed.utils.XLog

object RelayConflictNoticeHelper {
    private const val MAX_TRACKED_EVENT_IDS = 64
    private val notifiedEventIds = RecentEventIdTracker(MAX_TRACKED_EVENT_IDS)

    fun initNotificationChannel(pluginContext: Context, phoneContext: Context) {
        HookRuntimeBridge.notificationAccess.createNotificationChannel(
            phoneContext,
            NotificationConst.CHANNEL_ID_RELAY_CONFLICT,
            pluginContext.getString(R.string.hook_conflict_channel),
            NotificationManager.IMPORTANCE_HIGH,
        )
    }

    fun notifyConflictOnSms(pluginContext: Context, phoneContext: Context, eventId: String) {
        if (!markNotified(eventId)) {
            XLog.w("Relay conflict notice deduped: event_id=%s", eventId)
            return
        }
        showConflictNotification(pluginContext, phoneContext)
        showConflictToast(pluginContext, phoneContext)
        XLog.w(
            "Relay conflict notice sent: event_id=%s package=%s bypass=%s",
            eventId,
            ModuleConflictArbiter.TARGET_RELAY_PACKAGE,
            BuildConfig.ALLOW_CONFLICT_BYPASS,
        )
    }

    private fun markNotified(eventId: String): Boolean = notifiedEventIds.mark(eventId)

    private fun showConflictNotification(pluginContext: Context, phoneContext: Context) {
        val content = pluginContext.getString(
            R.string.hook_conflict_content,
            pluginContext.getString(R.string.hook_conflict_other_app),
            ModuleConflictArbiter.TARGET_RELAY_PACKAGE,
            pluginContext.getString(R.string.hook_app_name),
        )
        ConflictNotificationHelper.showConflictNotification(
            ConflictNotificationHelper.Request(
                phoneContext = phoneContext,
                pluginContext = pluginContext,
                applicationId = BuildConfig.APPLICATION_ID,
                visualConfig = ConflictNotificationHelper.VisualConfig(
                    channelId = NotificationConst.CHANNEL_ID_RELAY_CONFLICT,
                    notificationId = NotificationConst.NOTIFICATION_ID_RELAY_CONFLICT,
                    smallIconResId = R.drawable.ic_hook_app_icon,
                    largeIconResId = R.drawable.ic_hook_app_icon,
                    accentColorResId = R.color.hook_notification_accent,
                ),
                title = pluginContext.getString(R.string.hook_conflict_title),
                content = content,
                activityPendingIntentImmutableMinSdk = Build.VERSION_CODES.S,
            ),
        )
    }

    private fun showConflictToast(pluginContext: Context, phoneContext: Context) {
        InputHelper.sendToast(
            phoneContext,
            pluginContext.getString(R.string.hook_conflict_toast),
            Toast.LENGTH_SHORT,
        )
    }

}
