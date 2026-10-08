package io.github.magisk317.smscode.xp.hook.code

import android.content.Context
import io.github.magisk317.smscode.common.utils.HookPrefsReader
import io.github.magisk317.smscode.xp.helper.ModuleConflictArbiter
import io.github.magisk317.smscode.xp.helper.RelayConflictNoticeHelper
import io.github.magisk317.smscode.runtime.verification.SmsHookConstructorInitializer

/**
 * XSC wiring for the shared constructor initializer.
 *
 * The flow lives in core; only the host-specific pieces are injected here.
 */
internal fun createXscConstructorInitializer(
    runtimeInitializer: (Context) -> SmsHookRuntimeContext?,
    notificationChannelInitializer: (SmsHookRuntimeContext) -> Unit,
    copyCodeRegistrar: (SmsHookRuntimeContext) -> Unit,
    heartbeatRecorder: (String) -> Unit,
    suppressionLogger: (String) -> Unit,
    inboxObserverRegistrar: (SmsHookRuntimeContext) -> Unit,
): SmsHookConstructorInitializer<SmsHookRuntimeContext> = SmsHookConstructorInitializer(
    runtimeInitializer = runtimeInitializer,
    conflictNoticeChannelInitializer = RelayConflictNoticeHelper::initNotificationChannel,
    conflictSuppressor = { context, source ->
        ModuleConflictArbiter.shouldSuppressByRelay(context, source)
    },
    showNotificationReader = HookPrefsReader::showCodeNotification,
    notificationChannelInitializer = notificationChannelInitializer,
    copyCodeRegistrar = copyCodeRegistrar,
    // The heartbeat below is what tells the app a hook process is live; writing the
    // activation file from the hook process would be overwritten by the app anyway.
    activationMarker = {},
    heartbeatRecorder = heartbeatRecorder,
    suppressionLogger = suppressionLogger,
    inboxObserverRegistrar = inboxObserverRegistrar,
)
