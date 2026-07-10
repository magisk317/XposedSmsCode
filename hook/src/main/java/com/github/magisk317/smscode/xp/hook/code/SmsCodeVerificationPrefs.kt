package com.github.magisk317.smscode.xp.hook.code

import android.content.Context
import com.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge
import io.github.magisk317.smscode.verification.VerificationPrefs

class SmsCodeVerificationPrefs(
    private val context: Context,
) : VerificationPrefs {
    override fun showNotification(): Boolean = HookRuntimeBridge.prefsAccess.showCodeNotification(context)

    override fun autoCancelNotification(): Boolean = HookRuntimeBridge.prefsAccess.autoCancelCodeNotification(context)

    override fun notificationRetentionMs(): Long = HookRuntimeBridge.prefsAccess.getNotificationRetentionTime(context) * 1000L

    override fun autoInputEnabled(): Boolean = HookRuntimeBridge.prefsAccess.autoInputCodeEnabled(context)

    override fun autoInputDelayMs(): Long = HookRuntimeBridge.prefsAccess.getAutoInputCodeDelay(context) * 1000L

    override fun copyToClipboardEnabled(): Boolean = HookRuntimeBridge.prefsAccess.copyToClipboardEnabled(context)

    override fun showToast(): Boolean = HookRuntimeBridge.prefsAccess.shouldShowToast(context)

    override fun recordSmsEnabled(): Boolean = HookRuntimeBridge.prefsAccess.recordCodeSmsEnabled(context)

    override fun blockSmsEnabled(): Boolean = HookRuntimeBridge.prefsAccess.blockSmsEnabled(context)

    override fun markAsReadEnabled(): Boolean = HookRuntimeBridge.prefsAccess.markAsReadEnabled(context)

    override fun deleteSmsEnabled(): Boolean = HookRuntimeBridge.prefsAccess.deleteSmsEnabled(context)

    override fun deduplicateSmsEnabled(): Boolean = HookRuntimeBridge.prefsAccess.deduplicateSms(context)
}
