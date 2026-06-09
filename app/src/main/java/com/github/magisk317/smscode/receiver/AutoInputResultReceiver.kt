package com.github.magisk317.smscode.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AutoInputResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AutoInputResultHandler.action) return
        val pendingResult = goAsync()
        AutoInputResultHandler.handle(context.applicationContext ?: context, intent) {
            pendingResult.finish()
        }
    }
}
