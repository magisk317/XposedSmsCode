package io.github.magisk317.smscode.xp.hook.code.action.impl

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.os.Bundle
import io.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge
import io.github.magisk317.smscode.db.entity.SmsMsg
import io.github.magisk317.smscode.runtime.verification.RecordSmsActionHelper
import io.github.magisk317.smscode.runtime.verification.RecordSmsInsertResultHelper
import io.github.magisk317.smscode.xp.hook.code.action.CallableAction
import io.github.magisk317.smscode.xp.hook.code.VerificationSmsMsg
import io.github.magisk317.smscode.xp.hook.code.toVerificationMessage
import io.github.magisk317.smscode.xp.hook.code.withProviderIpcToken

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
            enabled = enabled ?: recordEnabledForMessageType(mSmsMsg),
            deduplicateEnabled = deduplicateEnabled ?: HookRuntimeBridge.prefsAccess.deduplicateSms(mPluginContext),
            // The app-owned provider serializes fingerprint upserts. Hook
            // processes no longer need a world-writable file lock or direct DB access.
            withFileLock = { _, _, block -> block() },
            shouldSkipByDedup = { _, _ -> false },
            primaryInserter = { smsMsg -> insertPrimary(smsMsg.raw) },
            fallbackExporter = { smsMsg -> HookRuntimeBridge.codeRecordAccess.exportToFile(mPluginContext, smsMsg.raw) },
        ).run()
    }

    private fun insertPrimary(smsMsg: SmsMsg): RecordSmsActionHelper.InsertResult {
        return RecordSmsInsertResultHelper.capture {
            val smsMsgUri = HookRuntimeBridge.contentProviderAccess.smsMsgContentUri(mPluginContext)
                .withProviderIpcToken(mPluginContext)
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
                put("deduplicate", deduplicateEnabled ?: HookRuntimeBridge.prefsAccess.deduplicateSms(mPluginContext))
            }

            val insertedUri = resolver.insert(smsMsgUri, values)
                ?: return RecordSmsInsertResultHelper.failure("provider_insert_rejected")
            if (insertedUri.getBooleanQueryParameter("duplicate", false)) {
                return RecordSmsInsertResultHelper.duplicate(
                    detail = "record_uri=$insertedUri,simSlot=${smsMsg.simSlot},subId=${smsMsg.subId}",
                )
            }

            val projections = arrayOf("_id")
            val order = "date ASC"
            val selection = "msg_type = ? AND sms_code IS NOT NULL AND sms_code != ''"
            val selectionArgs = arrayOf(smsMsg.msgType.toString())
            val cursor: Cursor? = resolver.query(smsMsgUri, projections, selection, selectionArgs, order)
            if (cursor == null) {
                return RecordSmsInsertResultHelper.success(
                    detail = "record_uri=$insertedUri,simSlot=${smsMsg.simSlot},subId=${smsMsg.subId},retention_query_null",
                )
            }

            val count = cursor.count
            val limit = HookRuntimeBridge.prefsAccess.getHistoryLimit(
                context = mPluginContext,
                msgType = smsMsg.msgType,
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

                resolver.applyBatch(HookRuntimeBridge.contentProviderAccess.authority(mPluginContext), operations)
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

    private fun recordEnabledForMessageType(smsMsg: SmsMsg): Boolean {
        return when (smsMsg.msgType) {
            SmsMsg.MSG_TYPE_APP_NOTIFY -> HookRuntimeBridge.prefsAccess.recordAppNotifyEnabled(mPluginContext)
            SmsMsg.MSG_TYPE_CALL_NOTIFY -> HookRuntimeBridge.prefsAccess.recordCallNotifyEnabled(mPluginContext)
            else -> HookRuntimeBridge.prefsAccess.recordCodeSmsEnabled(mPluginContext)
        }
    }

}
