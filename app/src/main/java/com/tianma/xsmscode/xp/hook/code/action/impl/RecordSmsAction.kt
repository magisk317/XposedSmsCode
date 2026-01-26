package com.tianma.xsmscode.xp.hook.code.action.impl

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.tianma.xsmscode.common.constant.PrefConst
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.common.utils.XSPUtils
import com.tianma.xsmscode.data.db.DBProvider
import com.tianma.xsmscode.data.db.entity.SmsMsg
import com.tianma.xsmscode.ui.record.CodeRecordRestoreManager
import com.tianma.xsmscode.xp.hook.code.action.CallableAction
import de.robv.android.xposed.XSharedPreferences

/**
 * 记录验证码短信
 */
class RecordSmsAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
    xsp: XSharedPreferences
) : CallableAction(pluginContext, phoneContext, smsMsg, xsp) {

    override fun action(): Bundle? {
        if (XSPUtils.recordSmsCodeEnabled(xsp)) {
            recordSmsMsg(mSmsMsg)
        }
        return null
    }

    private fun recordSmsMsg(smsMsg: SmsMsg) {
        try {
            val smsMsgUri = DBProvider.SMS_MSG_CONTENT_URI
            val resolver = mPluginContext.contentResolver

            val values = ContentValues().apply {
                put("body", smsMsg.body)
                put("company", smsMsg.company)
                put("date", smsMsg.date)
                put("sender", smsMsg.sender)
                put("sms_code", smsMsg.smsCode)
            }

            resolver.insert(smsMsgUri, values)
            XLog.d("Add code record succeed by content provider")

            val projections = arrayOf("_id")
            val order = "date ASC"
            val cursor: Cursor? = resolver.query(smsMsgUri, projections, null, null, order)
            if (cursor == null) {
                return
            }

            val count = cursor.count
            val maxRecordCount = PrefConst.MAX_SMS_RECORDS_COUNT_DEFAULT
            if (count > maxRecordCount) {
                // 删除最早的记录，直至剩余数目为 PrefConst.MAX_SMS_RECORDS_COUNT_DEFAULT
                val operations = ArrayList<ContentProviderOperation>()
                val selection = "_id = ?"
                for (i in 0 until count - maxRecordCount) {
                    if (cursor.moveToNext()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"))
                        val operation = ContentProviderOperation.newDelete(smsMsgUri)
                            .withSelection(selection, arrayOf(id.toString()))
                            .build()
                        operations.add(operation)
                    }
                }

                resolver.applyBatch(DBProvider.AUTHORITY, operations)
                XLog.d("Remove outdated code records succeed by content provider")
            }
            cursor.close()
        } catch (e1: Exception) {
            // ContentProvider dead.
            // Write file to do data transition
            if (CodeRecordRestoreManager.exportToFile(smsMsg)) {
                XLog.d("Export code record to file succeed")
            }
        }
    }
}
