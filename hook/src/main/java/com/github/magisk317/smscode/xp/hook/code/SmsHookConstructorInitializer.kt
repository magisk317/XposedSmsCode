package com.github.magisk317.smscode.xp.hook.code

import android.content.Context
import com.github.magisk317.smscode.runtime.RuntimePrefsFacade as PrefsReader
import com.github.magisk317.smscode.xp.helper.ModuleConflictArbiter
import com.github.magisk317.smscode.xp.helper.RelayConflictNoticeHelper
import io.github.magisk317.smscode.verification.SmsHookConstructorInitializer as SharedSmsHookConstructorInitializer
import io.github.magisk317.smscode.xposed.utils.ModuleActivationStore

internal class SmsHookConstructorInitializer(
    private val runtimeInitializer: (Context) -> SmsHookRuntimeContext?,
    private val conflictNoticeChannelInitializer: (Context, Context) -> Unit =
        RelayConflictNoticeHelper::initNotificationChannel,
    private val conflictSuppressor: (Context, String) -> Boolean = { context, source ->
        ModuleConflictArbiter.shouldSuppressByRelay(context, source)
    },
    private val showNotificationReader: (Context) -> Boolean = PrefsReader::showCodeNotification,
    private val notificationChannelInitializer: (SmsHookRuntimeContext) -> Unit = {},
    private val copyCodeRegistrar: (SmsHookRuntimeContext) -> Unit = {},
    private val activationMarker: (Context) -> Unit = ModuleActivationStore::markActivated,
    private val heartbeatRecorder: (String) -> Unit = {},
    private val suppressionLogger: (String) -> Unit = {},
    private val inboxObserverRegistrar: (SmsHookRuntimeContext) -> Unit = {},
) {
    enum class StopReason {
        RUNTIME_UNAVAILABLE,
    }

    data class Outcome(
        val stopReason: StopReason? = null,
        val suppressedByRelay: Boolean = false,
    ) {
        val initialized: Boolean = stopReason == null
    }

    private val delegate = SharedSmsHookConstructorInitializer(
        runtimeInitializer = runtimeInitializer,
        conflictNoticeChannelInitializer = conflictNoticeChannelInitializer,
        conflictSuppressor = conflictSuppressor,
        showNotificationReader = showNotificationReader,
        notificationChannelInitializer = notificationChannelInitializer,
        copyCodeRegistrar = copyCodeRegistrar,
        activationMarker = activationMarker,
        heartbeatRecorder = heartbeatRecorder,
        suppressionLogger = suppressionLogger,
        inboxObserverRegistrar = inboxObserverRegistrar,
    )

    fun handle(phoneContext: Context): Outcome {
        val outcome = delegate.handle(phoneContext)
        return Outcome(
            stopReason = outcome.stopReason?.toLocal(),
            suppressedByRelay = outcome.suppressedByRelay,
        )
    }
}

private fun SharedSmsHookConstructorInitializer.StopReason.toLocal(): SmsHookConstructorInitializer.StopReason {
    return when (this) {
        SharedSmsHookConstructorInitializer.StopReason.RUNTIME_UNAVAILABLE ->
            SmsHookConstructorInitializer.StopReason.RUNTIME_UNAVAILABLE
    }
}
