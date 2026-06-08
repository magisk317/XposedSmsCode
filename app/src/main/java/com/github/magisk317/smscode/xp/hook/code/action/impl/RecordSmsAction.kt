package com.github.magisk317.smscode.xp.hook.code.action.impl

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.os.Bundle
import com.github.magisk317.smscode.runtime.RuntimePrefsFacade as PrefsReader
import io.github.magisk317.smscode.runtime.common.utils.SharedRuntimeGate
import com.github.magisk317.smscode.data.db.DBProvider
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.runtime.RuntimeCodeRecordRestoreFacade
import com.github.magisk317.smscode.runtime.RuntimeStorageFacade
import io.github.magisk317.smscode.domain.utils.CodeRecordSimilarityUtils
import io.github.magisk317.smscode.verification.RecordSmsDedupHelper
import io.github.magisk317.smscode.verification.RecordSmsActionHelper
import io.github.magisk317.smscode.verification.RecordSmsInsertResultHelper
import com.github.magisk317.smscode.xp.hook.code.action.CallableAction
import com.github.magisk317.smscode.xp.hook.code.VerificationSmsMsg
import com.github.magisk317.smscode.xp.hook.code.toVerificationMessage

/**
 * 记录验证码短信
 */
class RecordSmsAction(
    pluginContext: Context,
    phoneContext: Context,
    smsMsg: SmsMsg,
    private val eventId: String = "",
    private val enabled: Boolean? = null,
    private val deduplicateEnabled: Boolean? = null,
) :
    CallableAction(pluginContext, phoneContext, smsMsg) {

    override fun action(): Bundle? {
        return RecordSmsActionHelper<VerificationSmsMsg>(
            pluginContext = mPluginContext,
            smsMsg = mSmsMsg.toVerificationMessage(),
            eventId = eventId,
            enabled = enabled ?: PrefsReader.recordCodeSmsEnabled(mPluginContext),
            deduplicateEnabled = deduplicateEnabled ?: PrefsReader.deduplicateSms(mPluginContext),
            withFileLock = { context, fileName, block ->
                SharedRuntimeGate.withFileLock(context, fileName) { block() }
            },
            shouldSkipByDedup = { smsMsg, eventLabel -> shouldSkipByDedup(smsMsg.raw, eventLabel) },
            primaryInserter = { smsMsg -> insertPrimary(smsMsg.raw) },
            fallbackExporter = { smsMsg -> exportFallback(smsMsg.raw) },
        ).run()
    }

    private fun insertPrimary(smsMsg: SmsMsg): RecordSmsActionHelper.InsertResult {
        return RecordSmsInsertResultHelper.capture {
            val smsMsgUri = DBProvider.smsMsgContentUri(mPluginContext)
            val resolver = mPluginContext.contentResolver
            val processedTime = smsMsg.processedTime.takeIf { it > 0L } ?: System.currentTimeMillis()

            val values = ContentValues().apply {
                put("body", smsMsg.body)
                put("company", smsMsg.company)
                put("date", smsMsg.date)
                put("processed_time", processedTime)
                put("sender", smsMsg.sender)
                put("sms_code", smsMsg.smsCode)
                put("package_name", smsMsg.packageName)
                put("sim_slot", smsMsg.simSlot)
                put("sub_id", smsMsg.subId)
                put("msg_type", smsMsg.msgType)
                put("forward_status", smsMsg.forwardStatus)
                put("forward_target", smsMsg.forwardTarget)
                put("forward_message", smsMsg.forwardMessage)
                put("forward_time", smsMsg.forwardTime)
            }

            val insertedUri = resolver.insert(smsMsgUri, values)

            val projections = arrayOf("_id")
            val order = "date ASC"
            val selection = "msg_type = ? AND sms_code IS NOT NULL AND sms_code != ''"
            val selectionArgs = arrayOf(SmsMsg.MSG_TYPE_SMS.toString())
            val cursor: Cursor? = resolver.query(smsMsgUri, projections, selection, selectionArgs, order)
            if (cursor == null) {
                return RecordSmsInsertResultHelper.success(
                    detail = "record_uri=$insertedUri,simSlot=${smsMsg.simSlot},subId=${smsMsg.subId},retention_query_null",
                )
            }

            val count = cursor.count
            val limit = PrefsReader.getHistoryLimit(
                context = mPluginContext,
                msgType = SmsMsg.MSG_TYPE_SMS,
                isCodeSms = true,
            )
            if (limit > 0 && count > limit) {
                // 删除最早的记录，直至剩余数目为 limit
                val operations = ArrayList<ContentProviderOperation>()
                val deleteSelection = "_id = ?"
                for (i in 0 until count - limit) {
                    if (cursor.moveToNext()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"))
                        val operation = ContentProviderOperation.newDelete(smsMsgUri)
                            .withSelection(deleteSelection, arrayOf(id.toString()))
                            .build()
                        operations.add(operation)
                    }
                }

                resolver.applyBatch(DBProvider.authority(mPluginContext), operations)
                cursor.close()
                return RecordSmsInsertResultHelper.success(
                    detail = "record_uri=$insertedUri,simSlot=${smsMsg.simSlot},subId=${smsMsg.subId}," +
                        "retention_removed=${count - limit},limit=$limit",
                )
            }
            cursor.close()
            RecordSmsInsertResultHelper.success(
                detail = "record_uri=$insertedUri,simSlot=${smsMsg.simSlot},subId=${smsMsg.subId}",
            )
        }
    }

    private fun exportFallback(smsMsg: SmsMsg): Boolean {
        return RuntimeCodeRecordRestoreFacade.exportToFile(mPluginContext, smsMsg)
    }

    private fun shouldSkipByDedup(smsMsg: SmsMsg, eventLabel: String): Boolean {
        val db = RuntimeStorageFacade.dbManager(mPluginContext)
        return RecordSmsDedupHelper.shouldSkipByWindow(
            smsMsg = smsMsg.toVerificationMessage(),
            eventLabel = eventLabel,
            hasFingerprintDuplicate = { sender, body, from, to ->
                runCatching {
                    db.querySmsMsgByFingerprintInRange(sender, body, from, to) != null
                }.getOrDefault(false)
            },
            hasCodeDuplicateInWindow = { code, from, to ->
                runCatching {
                    db.querySmsMsgByCodeInRange(code, from, to)
                        .sortedByDescending { existing ->
                            CodeRecordSimilarityUtils.crossSourceMatchScore(
                                existingCode = existing.smsCode,
                                existingBody = existing.body,
                                existingCompany = existing.company,
                                existingSender = existing.sender,
                                incomingCode = smsMsg.smsCode,
                                incomingBody = smsMsg.body,
                                incomingCompany = smsMsg.company,
                                incomingSender = smsMsg.sender,
                            )
                        }
                        .any { existing ->
                            CodeRecordSimilarityUtils.crossSourceMatchScore(
                                existingCode = existing.smsCode,
                                existingBody = existing.body,
                                existingCompany = existing.company,
                                existingSender = existing.sender,
                                incomingCode = smsMsg.smsCode,
                                incomingBody = smsMsg.body,
                                incomingCompany = smsMsg.company,
                                incomingSender = smsMsg.sender,
                            ) > 0
                        }
                }.getOrDefault(false)
            },
            hasCodeDuplicateByPackage = { code, pkg, from, to ->
                runCatching {
                    db.querySmsMsgByCodeAndPackageInRange(code, pkg, from, to) != null
                }.getOrDefault(false)
            },
            hasCodeDuplicateByCompany = { code, company, from, to ->
                runCatching {
                    db.querySmsMsgByCodeAndCompanyInRange(code, company, from, to) != null
                }.getOrDefault(false)
            },
        )
    }
}
