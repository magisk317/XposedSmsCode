package com.github.magisk317.smscode.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.magisk317.smscode.verification.CodeNotificationReceiverHandler

class CodeNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        CodeNotificationReceiverHandler.handleBroadcast(
            receiver = this,
            context = context,
            intent = intent,
            config = CodeNotificationReceiverConfig.create(
                context = context.applicationContext ?: context,
                source = "CodeNotificationReceiver",
                sentFromUidProvider = ::getSentFromUidCompat,
            ),
        )
    }

    private fun getSentFromUidCompat(): Int {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            getSentFromUid()
        } else {
            -1
        }
    }
}
