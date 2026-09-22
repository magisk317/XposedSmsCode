package com.github.magisk317.smscode.runtime

import android.content.Context
import com.github.magisk317.smscode.runtime.bridge.HookNotificationAccess
import com.github.magisk317.smscode.runtime.bridge.UiNotificationAccess
import io.github.magisk317.smscode.runtime.common.notification.AndroidNotificationPlatformBridge
import io.github.magisk317.smscode.runtime.contract.notification.NotificationDeliveryDiagnostics

object RuntimeNotificationFacade : HookNotificationAccess, UiNotificationAccess {
    private val platform = AndroidNotificationPlatformBridge(recoverDeletedChannels = true)

    override fun createNotificationChannel(
        context: Context,
        channelId: String,
        channelName: String,
        importance: Int,
    ) {
        platform.createNotificationChannel(context, channelId, channelName, importance)
    }

    override fun inspectDelivery(context: Context, channelId: String): NotificationDeliveryDiagnostics =
        platform.inspectDelivery(context, channelId)

    override fun hasPostNotificationsPermission(context: Context): Boolean {
        return platform.hasPostNotificationsPermission(context)
    }
}
