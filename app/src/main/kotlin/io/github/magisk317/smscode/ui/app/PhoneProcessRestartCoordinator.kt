package io.github.magisk317.smscode.ui.app

import android.content.Context
import io.github.magisk317.smscode.runtime.common.process.PhoneProcessRestartCoordinator as SharedCoordinator
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber

internal object PhoneProcessRestartCoordinator {
    /** Google Messages keeps its own MMS connection, so it rides along on every restart. */
    private val TARGET_PROCESSES = SharedCoordinator.DEFAULT_TARGET_PROCESSES + "com.google.android.apps.messaging"

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
