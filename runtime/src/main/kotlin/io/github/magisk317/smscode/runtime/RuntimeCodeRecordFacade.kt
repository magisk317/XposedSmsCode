package io.github.magisk317.smscode.runtime

import android.content.Context
import android.net.Uri
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.smscode.data.db.DBManager
import io.github.magisk317.smscode.data.db.entity.SmsMsg
import io.github.magisk317.smscode.runtime.bridge.UiCodeRecordAccess
import io.github.magisk317.smscode.runtime.contract.record.SmsMsgRecordData
import io.github.magisk317.smscode.runtime.common.utils.JsonUtils
import io.github.magisk317.xposed.logging.MagiskOtel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object RuntimeCodeRecordFacade : UiCodeRecordAccess {
    private const val NANOS_PER_MILLI = 1_000_000L

    override fun recordsFlow(context: Context): Flow<List<SmsMsgRecordData>> {
        return DBManager.get(context).queryAllSmsMsgFlow().map { list -> list.map { it.toRecordFileData() } }
    }

    override fun queryRecords(context: Context): List<SmsMsgRecordData> {
        return DBManager.get(context).queryAllSmsMsg().map { it.toRecordFileData() }
    }

    override suspend fun removeRecords(context: Context, records: List<SmsMsgRecordData>) {
        val startedAt = System.nanoTime()
        runCatching {
            DBManager.get(context).removeSmsMsgListSuspend(records.map { it.toSmsMsg() })
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

    override suspend fun restoreRecords(context: Context, records: List<SmsMsgRecordData>) {
        val startedAt = System.nanoTime()
        runCatching {
            DBManager.get(context).insertSmsMsgListSuspend(records.map { it.toSmsMsg() })
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

    override fun exportCodeRecords(context: Context, uri: Uri, records: List<SmsMsgRecordData>): Boolean {
        val startedAt = System.nanoTime()
        val codeRecords = records.filter {
            it.msgType == SmsMsgRecordData.MSG_TYPE_SMS && !it.smsCode.isNullOrBlank()
        }
        val ok = runCatching {
            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: return@runCatching false
            outputStream.use { os ->
                OutputStreamWriter(os, StandardCharsets.UTF_8).use { writer ->
                    JsonUtils.toJson(codeRecords, writer)
                }
            }
            true
        }.onFailure { XLog.e(message = "Export code records failed", it) }
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
        return ((System.nanoTime() - startedAt) / NANOS_PER_MILLI).coerceAtLeast(0L)
    }

    private fun SmsMsg.toRecordFileData(): SmsMsgRecordData = SmsMsgRecordData(
        id = id,
        sender = sender,
        body = body,
        date = date,
        processedTime = processedTime,
        company = company,
        smsCode = smsCode,
        packageName = packageName,
        notifyChannelId = notifyChannelId,
        simSlot = simSlot,
        subId = subId,
        contactName = contactName,
        phoneArea = phoneArea,
        msgType = msgType,
        callType = callType,
        sessionKey = sessionKey,
    )

    private fun SmsMsgRecordData.toSmsMsg(): SmsMsg = SmsMsg(
        id = id,
        sender = sender,
        body = body,
        date = date,
        processedTime = processedTime,
        company = company,
        smsCode = smsCode,
        packageName = packageName,
        notifyChannelId = notifyChannelId,
        simSlot = simSlot,
        subId = subId,
        contactName = contactName,
        phoneArea = phoneArea,
        msgType = msgType,
        callType = callType,
        sessionKey = sessionKey,
    )
}
