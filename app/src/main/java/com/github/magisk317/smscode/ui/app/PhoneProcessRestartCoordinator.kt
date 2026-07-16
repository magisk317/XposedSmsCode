package com.github.magisk317.smscode.ui.app

import android.content.Context
import io.github.magisk317.smscode.runtime.common.process.PhoneProcessRestartCoordinator as SharedCoordinator
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber

internal object PhoneProcessRestartCoordinator {
    private val TARGET_PROCESSES = listOf(
        "com.android.phone",
        "com.xiaomi.phone",
        "com.android.providers.telephony",
        "com.android.mms",
        "com.android.mms:mms_service",
        "com.google.android.apps.messaging",
    )

    private val delegate = SharedCoordinator(
        targetProcesses = TARGET_PROCESSES,
        logger = object : SharedCoordinator.Logger {
            override fun info(message: String) = Timber.i(message)
            override fun warn(message: String) = Timber.w(message)
        },
    )

    fun requestAfterXposedServiceBind(context: Context, scope: CoroutineScope) {
        delegate.requestAfterInstallOrUpdate(context, scope)
    }

}
