package com.github.magisk317.smscode.common.constant

/**
 * Notification Constants
 */
object NotificationConst {

    const val CHANNEL_ID_FOREGROUND_SERVICE = "foreground_service"

    const val CHANNEL_ID_SMSCODE_NOTIFICATION = "smscode_notification"

    /**
     * Rotated channel id for the phone-owned fallback, used when the user switched the primary
     * channel off. Android does not let an app raise the importance of an existing channel, so a
     * fresh id is the only way to recover from `IMPORTANCE_NONE` without user action.
     */
    const val CHANNEL_ID_SMSCODE_NOTIFICATION_FALLBACK = "smscode_notification_fallback"
    const val CHANNEL_ID_RELAY_CONFLICT = "relay_conflict"
    const val GROUP_KEY_SMSCODE_NOTIFICATION = "group_key_smscode_notification"
    const val NOTIFICATION_ID_RELAY_CONFLICT = 0x72636f6e
}
