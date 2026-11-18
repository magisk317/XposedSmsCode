package io.github.magisk317.smscode.service

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.magisk317.smscode.common.utils.SmsCodeUtils
import io.github.magisk317.smscode.rule.model.SmsCodeParseSource
import io.github.magisk317.smscode.rule.model.SmsCodeParseSourceKind
import io.github.magisk317.smscode.runtime.AppPrefsFacade
import io.github.magisk317.smscode.runtime.verification.CodeNotificationReadCoordinator
import io.github.magisk317.xposed.logging.XLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * App-side backup of the mark-as-read pipeline. The hook-side DB write
 * ([io.github.magisk317.smscode.runtime.verification.OperateSmsActionHelper]) stays primary;
 * when it succeeds the SMS app clears its own notification and this listener naturally sees
 * nothing actionable. When the source app keeps unread state outside the telephony provider
 * (vendor ROMs) it still posts a notification, and this service clicks the app's own
 * mark-as-read action so the notification disappears with the conversation left intact.
 *
 * Requires the user to grant notification access; the settings screen links to the grant page
 * when the toggle is enabled. Every decision is emitted as the `sms.notify_read` OTel event by
 * the shared coordinator, feeding the vendor adaptation backlog (smscode-core docs/vendor-sms-apps.md).
 */
class CodeMarkReadNotificationService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        scope.launch {
            runCatching { process(sbn, notification) }.onFailure { error ->
                XLog.w(
                    message = "Notification mark-as-read processing failed: package=%s",
                    sbn.packageName,
                    error,
                )
            }
        }
    }

    private suspend fun process(sbn: StatusBarNotification, notification: Notification) {
        val context: Context = applicationContext
        if (!AppPrefsFacade.markAsReadEnabled(context)) {
            return
        }
        val packageName = sbn.packageName
        val extras = notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)
        val decision = buildCoordinator(context).maybeMarkAsRead(
            packageName = packageName,
            title = title,
            text = text,
            notification = notification,
            tag = sbn.tag,
        )
        if (decision == CodeNotificationReadCoordinator.Decision.NO_READ_ACTION) {
            // Vendor SMS apps (MIUI and friends) post code notifications without a read action.
            // The DB write already marked telephony read/seen; cancel the leftover notification
            // so the bar stays clean while the message stays in the inbox.
            runCatching { cancelNotification(sbn.key) }.onFailure { error ->
                XLog.w(message = "Notification cancel failed: package=%s", packageName, error)
            }
        }
    }

    private fun buildCoordinator(context: Context): CodeNotificationReadCoordinator =
        CodeNotificationReadCoordinator(
            codeParser = { input ->
                SmsCodeUtils.parseSmsCodeIfExists(
                    context,
                    input,
                    source = SmsCodeParseSource(
                        packageName = packageName,
                        sourceKind = SmsCodeParseSourceKind.APP_NOTIFICATION,
                    ),
                ).takeIf { it.isNotBlank() }
            },
            actionSender = { action -> action.actionIntent?.send() },
        )
}
