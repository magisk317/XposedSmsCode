package com.github.magisk317.smscode.runtime

import android.content.Context
import android.net.Uri
import com.github.magisk317.smscode.common.utils.XLog
import com.github.magisk317.smscode.data.db.DBManager
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.runtime.bridge.UiCodeRecordAccess
import io.github.magisk317.smscode.runtime.common.utils.JsonUtils
import kotlinx.coroutines.flow.Flow
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object RuntimeCodeRecordFacade : UiCodeRecordAccess {
    override fun recordsFlow(context: Context): Flow<List<SmsMsg>> {
        return DBManager.get(context).queryAllSmsMsgFlow()
    }

    override fun queryRecords(context: Context): List<SmsMsg> {
        return DBManager.get(context).queryAllSmsMsg()
    }

    override suspend fun removeRecords(context: Context, records: List<SmsMsg>) {
        DBManager.get(context).removeSmsMsgListSuspend(records)
    }

    override suspend fun restoreRecords(context: Context, records: List<SmsMsg>) {
        DBManager.get(context).insertSmsMsgListSuspend(records)
    }

    override fun exportCodeRecords(context: Context, uri: Uri, records: List<SmsMsg>): Boolean {
        val codeRecords = records.filter {
            it.msgType == SmsMsg.MSG_TYPE_SMS && !it.smsCode.isNullOrBlank()
        }
        return runCatching {
            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: return@runCatching false
            outputStream.use { os ->
                OutputStreamWriter(os, StandardCharsets.UTF_8).use { writer ->
                    JsonUtils.toJson(codeRecords, writer, true)
                }
            }
            true
        }.onFailure { XLog.e("Export code records failed", it) }
            .getOrDefault(false)
    }
}
