package io.github.magisk317.smscode.receiver

import android.content.Context
import android.content.Intent
import io.github.magisk317.smscode.runtime.verification.AsyncActionBroadcastReceiver

class AutoInputResultReceiver : AsyncActionBroadcastReceiver() {
    override val action: String
        get() = AutoInputResultHandler.action

    override fun handle(context: Context, intent: Intent, onComplete: () -> Unit) {
        AutoInputResultHandler.handle(context, intent, onComplete)
    }
}
