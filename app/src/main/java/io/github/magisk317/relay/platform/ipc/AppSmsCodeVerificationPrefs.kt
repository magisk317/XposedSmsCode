package io.github.magisk317.relay.platform.ipc

import android.content.Context
import com.github.magisk317.smscode.runtime.RuntimePrefsFacade
import io.github.magisk317.smscode.verification.VerificationPrefs

internal class AppSmsCodeVerificationPrefs(
    private val context: Context,
) : VerificationPrefs {
    override fun showNotification(): Boolean = RuntimePrefsFacade.showCodeNotification(context)

    override fun autoCancelNotification(): Boolean = RuntimePrefsFacade.autoCancelCodeNotification(context)

    override fun notificationRetentionMs(): Long = RuntimePrefsFacade.getNotificationRetentionTime(context) * 1000L

    override fun autoInputEnabled(): Boolean = RuntimePrefsFacade.autoInputCodeEnabled(context)

    override fun autoInputDelayMs(): Long = RuntimePrefsFacade.getAutoInputCodeDelay(context) * 1000L

    override fun copyToClipboardEnabled(): Boolean = RuntimePrefsFacade.copyToClipboardEnabled(context)

    override fun showToast(): Boolean = RuntimePrefsFacade.shouldShowToast(context)

    override fun recordSmsEnabled(): Boolean = RuntimePrefsFacade.recordCodeSmsEnabled(context)

    override fun blockSmsEnabled(): Boolean = RuntimePrefsFacade.blockSmsEnabled(context)

    override fun markAsReadEnabled(): Boolean = RuntimePrefsFacade.markAsReadEnabled(context)

    override fun deleteSmsEnabled(): Boolean = RuntimePrefsFacade.deleteSmsEnabled(context)

    override fun deduplicateSmsEnabled(): Boolean = RuntimePrefsFacade.deduplicateSms(context)
}
