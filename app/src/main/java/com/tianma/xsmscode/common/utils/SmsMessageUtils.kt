package com.tianma.xsmscode.common.utils

import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage

object SmsMessageUtils {

    private const val SMS_CHARACTER_LIMIT = 160

    @JvmStatic
    fun fromIntent(intent: Intent): Array<SmsMessage> {
        return Telephony.Sms.Intents.getMessagesFromIntent(intent)
    }

    @JvmStatic
    fun getMessageBody(messageParts: Array<SmsMessage>): String {
        return if (messageParts.size == 1) {
            messageParts[0].displayMessageBody
        } else {
            val sb = StringBuilder(SMS_CHARACTER_LIMIT * messageParts.size)
            for (messagePart in messageParts) {
                sb.append(messagePart.displayMessageBody)
            }
            sb.toString()
        }
    }
}
