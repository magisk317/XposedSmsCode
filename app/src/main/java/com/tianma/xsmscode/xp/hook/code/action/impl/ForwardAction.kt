package com.tianma.xsmscode.xp.hook.code.action.impl

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import com.tianma.xsmscode.common.utils.PrefsReader
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.data.db.DBProvider
import com.tianma.xsmscode.data.db.entity.SmsMsg
import com.tianma.xsmscode.xp.hook.code.action.CallableAction

/**
 * Capture SMS code, record it to local DB, and forward to external SmsCode App via Broadcast (IPC).
 */
class ForwardAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
    private val mSmsIntent: Intent? = null,
    private val eventId: String = "",
) :
    CallableAction(pluginContext, phoneContext, smsMsg) {

    override fun action(): Bundle? {
        try {
            val isCodeSms = !mSmsMsg.smsCode.isNullOrBlank()
            XLog.w(
                "Diag ForwardAction: event_id=%s delegate to app process, isCode=%s",
                eventId.ifBlank { "<none>" },
                isCodeSms,
            )
            // Send IPC Broadcast to the integrated SmsCode App Module
            val intent = Intent(ACTION_FORWARD_SMS)
            // ForwardReceiver is now merged into the same APK; target the host app package.
            intent.setPackage(mPluginContext.packageName)
            if (eventId.isNotBlank()) {
                intent.putExtra(EVENT_ID_EXTRA, eventId)
            }
            
            intent.putExtra("sender", mSmsMsg.sender)
            intent.putExtra("body", mSmsMsg.body)
            intent.putExtra("date", mSmsMsg.date)
            intent.putExtra("company", mSmsMsg.company)
            intent.putExtra("smsCode", mSmsMsg.smsCode)
            intent.putExtra("packageName", mSmsMsg.packageName)
            intent.putExtra("msgType", "sms")
            intent.putExtra("forward_source", "sms_hook")
            copySimExtras(intent)

            // Securing IPC with Token: Only the receiver matching our token can process this msg.
            // We use PrefsReader to retrieve token via cross-process Provider.
            val token = PrefsReader.getIpcToken(mPluginContext)
            if (token.isBlank()) {
                if (!shouldAllowSmsTokenBypass()) {
                    XLog.e("IPC token is empty, skip forwarding broadcast for security. event_id=%s", eventId.ifBlank { "<none>" })
                    persistForwardResult(
                        success = false,
                        target = "SmsCode Engine",
                        message = "IPC token missing",
                    )
                    return null
                }
                XLog.w(
                    "IPC token empty, continue forwarding with receiver-side bypass. event_id=%s source=sms_hook uid=%d sdk=%d",
                    eventId.ifBlank { "<none>" },
                    Process.myUid(),
                    Build.VERSION.SDK_INT,
                )
            } else {
                intent.putExtra("ipc_token", token)
            }

            mPluginContext.sendBroadcast(intent)
            
            XLog.i(
                "Successfully broadcasted SMS info to SmsCode Engine (event_id=%s tokenPresent=%s length=%d): %s",
                eventId.ifBlank { "<none>" },
                token.isNotBlank(),
                token.length,
                mSmsMsg.smsCode,
            )
        } catch (t: Throwable) {
            XLog.e("Failed to broadcast SMS info to SmsCode Engine", t)
            persistForwardResult(
                success = false,
                target = "SmsCode Engine",
                message = "IPC Broadcast Failed: ${t.message}"
            )
        }

        return null
    }

    private fun copySimExtras(target: Intent) {
        val simSlot = readIntExtra(
            "slot",
            "simId",
            "sim_id",
            "simSlot",
            "sim_slot",
            "android.telephony.extra.SLOT_INDEX",
        )
        val subId = readIntExtra(
            "subscription",
            "subscription_id",
            "sub_id",
            "android.telephony.extra.SUBSCRIPTION_INDEX",
            "android.telephony.extra.SUBSCRIPTION_ID",
        )
        if (simSlot != null) target.putExtra("sim_slot", simSlot)
        if (subId != null) target.putExtra("sub_id", subId)
        XLog.d(
            "ForwardAction SIM extras copied: sim_slot=%s sub_id=%s",
            simSlot?.toString() ?: "N/A",
            subId?.toString() ?: "N/A",
        )
    }

    private fun readIntExtra(vararg keys: String): Int? {
        val sourceIntent = mSmsIntent ?: return null
        for (key in keys) {
            if (!sourceIntent.hasExtra(key)) continue
            val intValue = sourceIntent.getIntExtra(key, Int.MIN_VALUE)
            if (intValue != Int.MIN_VALUE) return intValue
            val longValue = sourceIntent.getLongExtra(key, Long.MIN_VALUE)
            if (longValue != Long.MIN_VALUE) return longValue.toInt()
            sourceIntent.getStringExtra(key)?.toIntOrNull()?.let { return it }
        }
        return null
    }

    private fun shouldAllowSmsTokenBypass(): Boolean {
        val uid = Process.myUid()
        return uid == Process.SYSTEM_UID || uid == Process.PHONE_UID
    }

    private fun persistForwardResult(success: Boolean, target: String?, message: String) {
        val resolver = mPluginContext.contentResolver
        val smsMsgUri = DBProvider.SMS_MSG_CONTENT_URI
        val now = System.currentTimeMillis()
        val status = if (success) SmsMsg.FORWARD_STATUS_SUCCESS else SmsMsg.FORWARD_STATUS_FAILED
        val trimmedMessage = message.take(MAX_MESSAGE_LEN)
        val values = ContentValues().apply {
            put("forward_status", status)
            put("forward_target", target)
            put("forward_message", trimmedMessage)
            put("forward_time", now)
        }

        try {
            var matchedId: Long? = null
            val projection = arrayOf("_id", "sender", "body", "date")
            resolver.query(smsMsgUri, projection, null, null, "date DESC")?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow("_id")
                val senderIdx = cursor.getColumnIndexOrThrow("sender")
                val bodyIdx = cursor.getColumnIndexOrThrow("body")
                val dateIdx = cursor.getColumnIndexOrThrow("date")
                while (cursor.moveToNext()) {
                    val sender = cursor.getString(senderIdx)
                    val body = cursor.getString(bodyIdx)
                    val date = cursor.getLong(dateIdx)
                    if (sender == mSmsMsg.sender && body == mSmsMsg.body && date == mSmsMsg.date) {
                        matchedId = cursor.getLong(idIdx)
                        break
                    }
                }
            }

            if (matchedId != null) {
                val itemUri = ContentUris.withAppendedId(smsMsgUri, matchedId)
                resolver.update(itemUri, values, null, null)
            } else {
                values.put("sender", mSmsMsg.sender)
                values.put("body", mSmsMsg.body)
                values.put("date", mSmsMsg.date)
                values.put("company", mSmsMsg.company)
                values.put("sms_code", mSmsMsg.smsCode)
                values.put("package_name", mSmsMsg.packageName)
                resolver.insert(smsMsgUri, values)
            }
        } catch (t: Throwable) {
            XLog.w("Persist forward result failed: %s", t.message ?: "unknown")
        }
    }

    companion object {
        const val ACTION_FORWARD_SMS = "com.tianma.xsmscode.ACTION_FORWARD_SMS"
        private const val EVENT_ID_EXTRA = "event_id"
        private const val MAX_MESSAGE_LEN = 300
    }
}
