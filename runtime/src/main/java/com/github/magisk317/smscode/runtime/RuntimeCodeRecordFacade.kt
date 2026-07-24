package com.github.magisk317.smscode.runtime

import android.content.Context
import android.net.Uri
import com.github.magisk317.smscode.common.utils.XLog
import com.github.magisk317.smscode.data.db.DBManager
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.runtime.bridge.UiCodeRecordAccess
import io.github.magisk317.smscode.runtime.common.utils.JsonUtils
import io.github.magisk317.xposed.logging.MagiskOtel
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
        val startedAt = System.nanoTime()
        runCatching {
            DBManager.get(context).removeSmsMsgListSuspend(records)
            emitRecord(
                result = "ok",
                reason = "remove",
                durationMs = elapsedMs(startedAt),
                extra = mapOf("record_count" to records.size.toString()),
            )
        }.onFailure {
            emitRecord(
                result = "error",
                reason = "remove_failed",
                durationMs = elapsedMs(startedAt),
                statusOk = false,
                extra = mapOf(
                    "record_count" to records.size.toString(),
                    "error_class" to it.javaClass.simpleName,
                ),
            )
            throw it
        }
    }

    override suspend fun restoreRecords(context: Context, records: List<SmsMsg>) {
        val startedAt = System.nanoTime()
        runCatching {
            DBManager.get(context).insertSmsMsgListSuspend(records)
            emitRecord(
                result = "ok",
                reason = "restore",
                durationMs = elapsedMs(startedAt),
                extra = mapOf("record_count" to records.size.toString()),
            )
        }.onFailure {
            emitRecord(
                result = "error",
                reason = "restore_failed",
                durationMs = elapsedMs(startedAt),
                statusOk = false,
                extra = mapOf(
                    "record_count" to records.size.toString(),
                    "error_class" to it.javaClass.simpleName,
                ),
            )
            throw it
        }
    }

    override fun exportCodeRecords(context: Context, uri: Uri, records: List<SmsMsg>): Boolean {
        val startedAt = System.nanoTime()
        val codeRecords = records.filter {
            it.msgType == SmsMsg.MSG_TYPE_SMS && !it.smsCode.isNullOrBlank()
        }
        val ok = runCatching {
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
        emitRecord(
            result = if (ok) "ok" else "error",
            reason = if (ok) "export" else "export_failed",
            durationMs = elapsedMs(startedAt),
            statusOk = ok,
            extra = mapOf(
                "record_count" to records.size.toString(),
                "exported_count" to codeRecords.size.toString(),
            ),
        )
        return ok
    }

    private fun emitRecord(
        result: String,
        reason: String,
        durationMs: Long,
        statusOk: Boolean = true,
        extra: Map<String, String> = emptyMap(),
    ) {
        val attrs = mutableMapOf(
            "result" to result,
            "duration_ms" to durationMs.toString(),
            "process" to "app",
            "stage" to "code_record",
            "reason" to reason,
        )
        attrs.putAll(extra)
        MagiskOtel.event(name = "sms.record", attributes = attrs, statusOk = statusOk)
    }

    private fun elapsedMs(startedAt: Long): Long {
        return ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
    }
}
