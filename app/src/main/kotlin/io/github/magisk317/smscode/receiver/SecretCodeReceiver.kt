package io.github.magisk317.smscode.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.magisk317.smscode.runtime.common.appentry.SecretCodeEntry
import io.github.magisk317.smscode.ui.home.MainActivity

class SecretCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SecretCodeEntry.onReceive(context, intent, MainActivity::class.java)
    }
}
