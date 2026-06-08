package com.github.magisk317.smscode.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.magisk317.smscode.ui.app.PhoneProcessRestartCoordinator
import kotlin.concurrent.thread

class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val pendingResult = goAsync()
        thread(name = "smscode-package-replaced") {
            try {
                PhoneProcessRestartCoordinator.restartAfterInstallOrUpdate(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
