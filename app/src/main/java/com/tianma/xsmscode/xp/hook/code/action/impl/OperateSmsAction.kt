package com.tianma.xsmscode.xp.hook.code.action.impl

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Telephony
import androidx.annotation.IntDef
import androidx.core.content.ContextCompat
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.common.utils.XSPUtils
import com.tianma.xsmscode.data.db.entity.SmsMsg
import com.tianma.xsmscode.xp.hook.code.action.CallableAction
import de.robv.android.xposed.XSharedPreferences

/**
 * 将验证码短信删除或者标记为已读
 */
class OperateSmsAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
    xsp: XSharedPreferences
) : CallableAction(pluginContext, phoneContext, smsMsg, xsp) {

    @IntDef(OP_DELETE, OP_MARK_AS_READ)
    @Retention(AnnotationRetention.SOURCE)
    private annotation class SmsOp

    override fun action(): Bundle? {
        val sender = mSmsMsg.sender
        val body = mSmsMsg.body
        if (XSPUtils.deleteSmsEnabled(xsp)) {
            deleteSms(sender, body)
        } else if (XSPUtils.markAsReadEnabled(xsp)) {
            markSmsAsRead(sender, body)
        }
        return null
    }

    private fun markSmsAsRead(sender: String?, body: String?) {
        XLog.d("Marking SMS as read...")
        val result = operateSms(sender, body, OP_MARK_AS_READ)
        if (result) {
            XLog.i("Mark SMS as read succeed")
        } else {
            XLog.i("Mark SMS as read failed")
        }
    }

    private fun deleteSms(sender: String?, body: String?) {
        XLog.d("Deleting SMS...")
        val result = operateSms(sender, body, OP_DELETE)
        if (result) {
            XLog.i("Delete SMS succeed")
        } else {
            XLog.i("Delete SMS failed")
        }
    }

    private fun operateSms(sender: String?, body: String?, @SmsOp smsOp: Int): Boolean {
        var cursor: android.database.Cursor? = null
        try {
            if (ContextCompat.checkSelfPermission(mPluginContext, Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                XLog.e("Don't have permission to read/write sms")
                return false
            }
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.READ,
                Telephony.Sms.DATE
            )
            // 查看最近5条短信
            val sortOrder = Telephony.Sms.DATE + " desc limit 5"
            val uri = Telephony.Sms.CONTENT_URI
            val resolver = mPluginContext.contentResolver
            cursor = resolver.query(uri, projection, null, null, sortOrder)
            if (cursor == null) {
                XLog.d("Cursor is null")
                return false
            }
            while (cursor.moveToNext()) {
                val curAddress = cursor.getString(cursor.getColumnIndexOrThrow("address"))
                val curRead = cursor.getInt(cursor.getColumnIndexOrThrow("read"))
                val curBody = cursor.getString(cursor.getColumnIndexOrThrow("body"))
                if (curAddress == sender && curRead == 0 && curBody != null && curBody.startsWith(body ?: "")) {
                    val smsMessageId = cursor.getString(cursor.getColumnIndexOrThrow("_id"))
                    val where = Telephony.Sms._ID + " = ?"
                    val selectionArgs = arrayOf(smsMessageId)
                    if (smsOp == OP_DELETE) {
                        val rows = resolver.delete(uri, where, selectionArgs)
                        if (rows > 0) {
                            return true
                        }
                    } else if (smsOp == OP_MARK_AS_READ) {
                        val values = ContentValues()
                        values.put(Telephony.Sms.READ, true)
                        val rows = resolver.update(uri, values, where, selectionArgs)
                        if (rows > 0) {
                            return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            XLog.e("Operate SMS failed: ", e)
        } finally {
            cursor?.close()
        }
        return false
    }

    companion object {
        private const val OP_DELETE = 0
        private const val OP_MARK_AS_READ = 1
    }
}
