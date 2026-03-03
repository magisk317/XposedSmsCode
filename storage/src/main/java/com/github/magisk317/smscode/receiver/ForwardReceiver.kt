package com.github.magisk317.smscode.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import com.github.magisk317.smscode.forwarder.entity.MsgInfo
import com.github.magisk317.smscode.forwarder.utils.SendUtils
import com.github.magisk317.smscode.forwarder.utils.SourceMetadataResolver
import com.tianma.xsmscode.common.constant.PrefConst
import com.tianma.xsmscode.common.utils.ForwardFlowLog
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.data.db.DBManager
import com.tianma.xsmscode.data.db.entity.SmsMsg
import android.telephony.SubscriptionManager
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class ForwardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ordered = isOrderedBroadcast
        val pendingResult = goAsync()
        val eventId = intent.getStringExtra("event_id").orEmpty()
        val traceId = buildTraceId(intent, eventId)
        val task = Runnable {
            var killAppAfterFinish = false
            var resultMarked = false
            fun markResult(code: Int, reason: String) {
                resultMarked = true
                setOrderedResult(pendingResult, ordered, code, reason, eventId)
            }
            runCatching {
                val receiveStartMessage = buildString {
                    append("ForwardReceiver onReceive action=")
                    append(intent.action)
                    append(" event=")
                    append(eventId.ifBlank { "<none>" })
                    append(" ordered=")
                    append(ordered)
                }
                ForwardFlowLog.i(
                    traceId,
                    receiveStartMessage,
                )
                // 1. Action validation
                if (
                    intent.action != PrefConst.ACTION_FORWARD_SMS &&
                    intent.action != PrefConst.ACTION_KILL_ME
                ) {
                    XLog.e("Rejecting broadcast with invalid action: %s", intent.action)
                    ForwardFlowLog.w(traceId, "Reject invalid action=${intent.action}")
                    markResult(RESULT_REJECT_ACTION, "invalid_action")
                    return@runCatching
                }
                if (intent.action == PrefConst.ACTION_KILL_ME) {
                    val receivedToken = intent.getStringExtra("ipc_token")
                    val expectedToken = runBlocking {
                        com.tianma.xsmscode.common.utils.AppPreferencesDataStore.getString(
                            context,
                            com.tianma.xsmscode.common.constant.PrefConst.KEY_IPC_TOKEN,
                            "",
                        )
                    }
                    if (expectedToken.isEmpty() || receivedToken != expectedToken) {
                        ForwardFlowLog.w(
                            traceId,
                            "Reject kill action token mismatch expectedEmpty=${expectedToken.isEmpty()} receivedEmpty=${receivedToken.isNullOrBlank()}",
                        )
                        markResult(RESULT_REJECT_TOKEN, "token_mismatch")
                        return@runCatching
                    }
                    ForwardFlowLog.w(traceId, "Kill action accepted, app process will terminate")
                    markResult(RESULT_OK, "kill_requested")
                    killAppAfterFinish = true
                    return@runCatching
                }

                val sender = intent.getStringExtra("sender")
                val body = intent.getStringExtra("body")
                val date = intent.getLongExtra("date", 0L)
                val company = intent.getStringExtra("company")
                val smsCode = intent.getStringExtra("smsCode")
                val packageName = intent.getStringExtra("packageName")
                val receivedToken = intent.getStringExtra("ipc_token")
                val msgTypeStr = intent.getStringExtra("msgType") ?: "sms"
                val forwardSource = intent.getStringExtra("forward_source") ?: "unknown"
                val sentFromUid = resolveSentFromUidCompat()
                val sentFromPkg = resolveSentFromPackageCompat()
                val subId = readIntExtra(
                    intent,
                    "sub_id",
                    "subscription",
                    "subscription_id",
                    "android.telephony.extra.SUBSCRIPTION_INDEX",
                    "android.telephony.extra.SUBSCRIPTION_ID",
                )
                val rawSlot = readIntExtra(
                    intent,
                    "sim_slot",
                    "slot",
                    "simId",
                    "sim_id",
                    "simSlot",
                    "android.telephony.extra.SLOT_INDEX",
                )

                // 2. Verifying IPC Token: prevent third-party apps from spoofing broadcasts.
                // We retrieve local token from DataStore (which is synced to xposed_prefs).
                val expectedToken = runBlocking {
                    com.tianma.xsmscode.common.utils.AppPreferencesDataStore.getString(
                        context,
                        com.tianma.xsmscode.common.constant.PrefConst.KEY_IPC_TOKEN,
                        "",
                    )
                }
                val tokenMatched = expectedToken.isNotEmpty() && receivedToken == expectedToken
                val allowSystemBypass = shouldAllowSystemTokenBypass(
                    msgType = msgTypeStr,
                    forwardSource = forwardSource,
                    sentFromUid = sentFromUid,
                )

                if (!tokenMatched && !allowSystemBypass) {
                    XLog.e("IPC Token mismatch! Security breach attempt or uninitialized token. Rejecting broadcast.")
                    val rejectTokenMessage = buildString {
                        append("Reject token mismatch event=")
                        append(eventId.ifBlank { "<none>" })
                        append(" pkg=")
                        append(packageName.orEmpty())
                        append(" expectedEmpty=")
                        append(expectedToken.isEmpty())
                        append(" receivedEmpty=")
                        append(receivedToken.isNullOrBlank())
                        append(" source=")
                        append(forwardSource)
                        append(" sentFromUid=")
                        append(sentFromUid ?: -1)
                        append(" sentFromPkg=")
                        append(sentFromPkg ?: "<none>")
                    }
                    ForwardFlowLog.w(
                        traceId,
                        rejectTokenMessage,
                    )
                    markResult(RESULT_REJECT_TOKEN, "token_mismatch")
                    return@runCatching
                }
                if (!tokenMatched && allowSystemBypass) {
                    val bypassMessage = buildString {
                        append("Token bypass accepted source=")
                        append(forwardSource)
                        append(" msgType=")
                        append(msgTypeStr)
                        append(" sentFromUid=")
                        append(sentFromUid ?: -1)
                        append(" sentFromPkg=")
                        append(sentFromPkg ?: "<none>")
                    }
                    XLog.w("IPC token bypass accepted. %s", bypassMessage)
                    ForwardFlowLog.w(traceId, bypassMessage)
                }
                if (
                    msgTypeStr == "app_notify" &&
                    !shouldForwardAppNotify(context, packageName, traceId, forwardSource)
                ) {
                    markResult(RESULT_REJECT_APP_GATE, "app_gate_drop")
                    return@runCatching
                }
                if (msgTypeStr == "app_notify" && shouldDropDuplicateAppNotify(packageName, sender, body)) {
                    XLog.i(
                        "Drop duplicate app_notify: pkg=%s sender=%s source=%s",
                        packageName.orEmpty(),
                        sender.orEmpty(),
                        forwardSource,
                    )
                    ForwardFlowLog.i(
                        traceId,
                        buildString {
                            append("Drop duplicate app_notify pkg=")
                            append(packageName.orEmpty())
                            append(" sender=")
                            append(sender.orEmpty())
                            append(" source=")
                            append(forwardSource)
                        },
                    )
                    markResult(RESULT_DROP_DUPLICATE, "duplicate_drop")
                    return@runCatching
                }

                XLog.i("IPC verified and received message from: %s", sender ?: "")
                val verifiedMessage = buildString {
                    append("IPC verified event=")
                    append(eventId.ifBlank { "<none>" })
                    append(" type=")
                    append(msgTypeStr)
                    append(" pkg=")
                    append(packageName.orEmpty())
                    append(" sender=")
                    append(sender.orEmpty())
                    append(" bodyLen=")
                    append(body?.length ?: 0)
                    append(" source=")
                    append(forwardSource)
                    append(" sentFromUid=")
                    append(sentFromUid ?: -1)
                    append(" sentFromPkg=")
                    append(sentFromPkg ?: "<none>")
                }
                ForwardFlowLog.i(
                    traceId,
                    verifiedMessage,
                )
                val normalizedSubId = subId ?: 0
                val resolvedSimSlot = resolveSimSlot(rawSlot, normalizedSubId)
                val contactName = SourceMetadataResolver.resolveContactName(context, sender ?: "")
                val phoneArea = SourceMetadataResolver.resolvePhoneArea(sender ?: "")
                XLog.i(
                    "Resolved metadata: sim_slot=%d sub_id=%d contact=%s area=%s",
                    resolvedSimSlot,
                    normalizedSubId,
                    contactName.ifBlank { "<empty>" },
                    phoneArea.ifBlank { "<empty>" },
                )
                ForwardFlowLog.d(
                    traceId,
                    buildString {
                        append("Resolved metadata simSlot=")
                        append(resolvedSimSlot)
                        append(" subId=")
                        append(normalizedSubId)
                        append(" contact=")
                        append(contactName.ifBlank { "<empty>" })
                        append(" area=")
                        append(phoneArea.ifBlank { "<empty>" })
                    },
                )

                val smsMsgType = if (msgTypeStr == "app_notify") {
                    SmsMsg.MSG_TYPE_APP_NOTIFY
                } else {
                    SmsMsg.MSG_TYPE_SMS
                }
                val msgInfo = MsgInfo(
                    type = msgTypeStr,
                    from = sender ?: "",
                    content = body ?: "",
                    date = java.util.Date(date),
                    simInfo = company ?: "",
                    simSlot = resolvedSimSlot,
                    subId = normalizedSubId,
                    packageName = packageName ?: "",
                    contactName = contactName,
                    phoneArea = phoneArea,
                )
                var recordId: Long? = null

                if (msgTypeStr == "app_notify") {
                    val canRecordAppNotify = com.tianma.xsmscode.common.utils.PrefsReader.recordAppNotifyEnabled(context)
                    runCatching {
                        if (canRecordAppNotify) {
                            recordId = insertRecord(
                                context = context,
                                sender = msgInfo.from,
                                body = msgInfo.content,
                                date = msgInfo.date.time,
                                company = msgInfo.simInfo,
                                smsCode = smsCode,
                                packageName = msgInfo.packageName,
                                msgType = smsMsgType,
                                isCodeSms = false,
                            )
                        }
                    }.onFailure { error ->
                        XLog.e("Failed to record app notification to DB", error)
                        ForwardFlowLog.e(traceId, "Record app_notify failed", error)
                    }
                } else {
                    val isCodeSms = !smsCode.isNullOrBlank()
                    val canRecordSms = if (isCodeSms) {
                        com.tianma.xsmscode.common.utils.PrefsReader.recordCodeSmsEnabled(context)
                    } else {
                        com.tianma.xsmscode.common.utils.PrefsReader.recordPlainSmsEnabled(context)
                    }
                    recordId = findRecordIdByFingerprint(
                        context = context,
                        sender = msgInfo.from,
                        body = msgInfo.content,
                        date = msgInfo.date.time,
                        msgType = smsMsgType,
                    )
                    if (recordId == null && canRecordSms) {
                        runCatching {
                            recordId = insertRecord(
                                context = context,
                                sender = msgInfo.from,
                                body = msgInfo.content,
                                date = msgInfo.date.time,
                                company = msgInfo.simInfo,
                                smsCode = smsCode,
                                packageName = msgInfo.packageName,
                                msgType = smsMsgType,
                                isCodeSms = isCodeSms,
                            )
                            ForwardFlowLog.i(
                                traceId,
                                "Inserted missing sms record for forwarding date=${msgInfo.date.time}",
                            )
                        }.onFailure { error ->
                            XLog.e("Failed to insert missing sms record", error)
                            ForwardFlowLog.e(traceId, "Insert missing sms record failed", error)
                        }
                    }
                }
                if (recordId == null) {
                    recordId = findRecordIdByFingerprint(
                        context = context,
                        sender = msgInfo.from,
                        body = msgInfo.content,
                        date = msgInfo.date.time,
                        msgType = smsMsgType,
                    )
                }
                val readyDispatchMessage = buildString {
                    append("Ready to dispatch event=")
                    append(eventId.ifBlank { "<none>" })
                    append(" msgType=")
                    append(msgTypeStr)
                    append(" recordId=")
                    append(recordId ?: -1)
                    append(" isCodeSms=")
                    append(!smsCode.isNullOrBlank())
                    append(" source=")
                    append(forwardSource)
                    append(" final_decision=forward")
                }
                ForwardFlowLog.i(
                    traceId,
                    readyDispatchMessage,
                )

                // Dispatch to the multi-channel forwarding engine.
                // isCodeSms: true = verification code SMS, false = regular SMS.
                // SendUtils will use this to filter per-sender receiveCode/receiveNonCode setting.
                val isCodeSms = !smsCode.isNullOrBlank()
                runCatching {
                    SendUtils.sendMsg(context, msgInfo, isCodeSms, recordId, traceId)
                }.onSuccess {
                    markResult(RESULT_OK, "forward_dispatched")
                }.onFailure { error ->
                    ForwardFlowLog.e(
                        traceId,
                        buildString {
                            append("Dispatch failed event=")
                            append(eventId.ifBlank { "<none>" })
                            append(" source=")
                            append(forwardSource)
                            append(" pkg=")
                            append(packageName.orEmpty())
                        },
                        error,
                    )
                    markResult(RESULT_DISPATCH_FAILED, "dispatch_failed")
                }
            }.onFailure { error ->
                XLog.e("ForwardReceiver unexpected error", error)
                ForwardFlowLog.e(
                    traceId,
                    buildString {
                        append("ForwardReceiver unexpected error action=")
                        append(intent.action)
                        append(" event=")
                        append(eventId.ifBlank { "<none>" })
                    },
                    error,
                )
                if (!resultMarked) {
                    markResult(RESULT_DISPATCH_FAILED, "receiver_exception")
                }
            }
            if (!resultMarked) {
                markResult(RESULT_DISPATCH_FAILED, "receiver_no_result")
            }
            ForwardFlowLog.d(traceId, "ForwardReceiver finished")
            pendingResult.finish()
            if (killAppAfterFinish) {
                terminateSelfProcess(traceId)
            }
        }
        runCatching {
            FORWARD_EXECUTOR.execute(task)
        }.onFailure { error ->
            XLog.e("ForwardReceiver failed to schedule task", error)
            ForwardFlowLog.e(
                traceId,
                buildString {
                    append("ForwardReceiver schedule failed action=")
                    append(intent.action)
                    append(" event=")
                    append(eventId.ifBlank { "<none>" })
                },
                error,
            )
            setOrderedResult(pendingResult, ordered, RESULT_DISPATCH_FAILED, "executor_rejected", eventId)
            pendingResult.finish()
        }
    }

    companion object {
        private const val TAG = "ForwardReceiver"
        private const val APP_NOTIFY_DEDUP_WINDOW_MS = 2500L
        private const val APP_NOTIFY_DEDUP_MAX_ENTRIES = 256
        private const val API_LEVEL_34 = 34
        private const val RESULT_OK = 0
        private const val RESULT_REJECT_ACTION = -101
        private const val RESULT_REJECT_TOKEN = -102
        private const val RESULT_REJECT_APP_GATE = -103
        private const val RESULT_DROP_DUPLICATE = -104
        private const val RESULT_DISPATCH_FAILED = -105
        private const val FORWARD_WORKER_COUNT = 2
        private val workerIndex = AtomicInteger(1)
        private val FORWARD_EXECUTOR: ExecutorService = Executors.newFixedThreadPool(FORWARD_WORKER_COUNT) { runnable ->
            Thread(runnable, "ForwardReceiverWorker-${workerIndex.getAndIncrement()}")
        }
        private val recentAppNotify = ConcurrentHashMap<String, Long>()
    }

    private fun shouldAllowSystemTokenBypass(
        msgType: String,
        forwardSource: String,
        sentFromUid: Int?,
    ): Boolean {
        // Keep strict token verification by default.
        // Controlled bypass is only for system-origin paths when token lookup is temporarily unavailable.
        return when {
            msgType == "app_notify" && forwardSource == "nms_hook" -> {
                if (sentFromUid == Process.SYSTEM_UID) {
                    true
                } else if (Build.VERSION.SDK_INT < API_LEVEL_34 && sentFromUid == null) {
                    XLog.w("IPC token bypass accepted for nms_hook without sender uid (API<34)")
                    true
                } else {
                    false
                }
            }
            msgType == "sms" && forwardSource == "sms_hook" -> {
                if (sentFromUid == Process.SYSTEM_UID || sentFromUid == Process.PHONE_UID) {
                    true
                } else if (Build.VERSION.SDK_INT < API_LEVEL_34 && sentFromUid == null) {
                    XLog.w("IPC token bypass accepted for sms_hook without sender uid (API<34)")
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }

    private fun resolveSentFromUidCompat(): Int? {
        if (Build.VERSION.SDK_INT < API_LEVEL_34) return null
        return runCatching { getSentFromUid() }.getOrNull()
    }

    private fun resolveSentFromPackageCompat(): String? {
        if (Build.VERSION.SDK_INT < API_LEVEL_34) return null
        return runCatching { getSentFromPackage() }.getOrNull()
    }

    private fun resolveSimSlot(rawSlot: Int?, subId: Int): Int {
        if (subId > 0) {
            val slotFromSubId = runCatching { SubscriptionManager.getSlotIndex(subId) }.getOrDefault(-1)
            if (slotFromSubId >= 0) return slotFromSubId
        }
        val slot = rawSlot ?: return -1
        return when {
            slot in 0..1 -> slot
            slot == 2 -> 1
            else -> -1
        }
    }

    private fun readIntExtra(intent: Intent, vararg keys: String): Int? {
        for (key in keys) {
            if (!intent.hasExtra(key)) continue
            val intValue = intent.getIntExtra(key, Int.MIN_VALUE)
            if (intValue != Int.MIN_VALUE) return intValue
            val longValue = intent.getLongExtra(key, Long.MIN_VALUE)
            if (longValue != Long.MIN_VALUE) return longValue.toInt()
            intent.getStringExtra(key)?.toIntOrNull()?.let { return it }
        }
        return null
    }

    private fun shouldDropDuplicateAppNotify(
        packageName: String?,
        sender: String?,
        body: String?,
    ): Boolean {
        val key = buildString {
            append(packageName.orEmpty().trim())
            append('|')
            append(sender.orEmpty().trim())
            append('|')
            append(body.orEmpty().trim())
        }
        if (key == "||") return false

        val now = System.currentTimeMillis()
        val previous = recentAppNotify[key]
        if (previous != null && now - previous < APP_NOTIFY_DEDUP_WINDOW_MS) {
            return true
        }
        recentAppNotify[key] = now

        if (recentAppNotify.size > APP_NOTIFY_DEDUP_MAX_ENTRIES) {
            val cutoff = now - APP_NOTIFY_DEDUP_WINDOW_MS * 2
            recentAppNotify.entries.removeIf { it.value < cutoff }
        }
        return false
    }

    private fun shouldForwardAppNotify(
        context: Context,
        packageName: String?,
        traceId: String,
        forwardSource: String,
    ): Boolean {
        val pkg = packageName.orEmpty().trim()
        if (pkg.isEmpty()) {
            ForwardFlowLog.w(
                traceId,
                "App notify gate pkg=<empty> source=$forwardSource final_decision=drop reason=empty_package",
            )
            XLog.w("App notify gate: empty package source=%s final_decision=drop", forwardSource)
            return false
        }
        val appInfo = runCatching { DBManager.get(context).queryAppInfoByPackageName(pkg) }.getOrElse { error ->
            ForwardFlowLog.e(
                traceId,
                "App notify gate query failed pkg=$pkg source=$forwardSource final_decision=drop",
                error,
            )
            XLog.e("App notify gate query failed: pkg=$pkg source=$forwardSource", error)
            return false
        }
        val enabled = appInfo?.forwarding == true
        val state = when {
            appInfo == null -> "missing"
            enabled -> "enabled"
            else -> "disabled"
        }
        val finalDecision = if (enabled) "forward" else "drop"
        ForwardFlowLog.i(
            traceId,
            "App notify gate pkg=$pkg source=$forwardSource state=$state final_decision=$finalDecision",
        )
        XLog.d(
            "App notify gate: pkg=%s source=%s state=%s final_decision=%s",
            pkg,
            forwardSource,
            state,
            finalDecision,
        )
        return enabled
    }

    private fun findRecordIdByFingerprint(
        context: Context,
        sender: String,
        body: String,
        date: Long,
        msgType: Int,
    ): Long? {
        val resolver = context.contentResolver
        val smsMsgUri = com.tianma.xsmscode.data.db.DBProvider.SMS_MSG_CONTENT_URI
        val projection = arrayOf("_id", "sender", "body", "date", "msg_type")
        return runCatching {
            resolver.query(smsMsgUri, projection, null, null, "date DESC")?.use { cursor ->
                val idIdx = cursor.getColumnIndex("_id")
                val senderIdx = cursor.getColumnIndex("sender")
                val bodyIdx = cursor.getColumnIndex("body")
                val dateIdx = cursor.getColumnIndex("date")
                val msgTypeIdx = cursor.getColumnIndex("msg_type")
                while (cursor.moveToNext()) {
                    val senderValue = if (senderIdx >= 0) cursor.getString(senderIdx) else null
                    val bodyValue = if (bodyIdx >= 0) cursor.getString(bodyIdx) else null
                    val dateValue = if (dateIdx >= 0) cursor.getLong(dateIdx) else -1L
                    val msgTypeValue = if (msgTypeIdx >= 0) cursor.getInt(msgTypeIdx) else SmsMsg.MSG_TYPE_SMS
                    if (senderValue == sender && bodyValue == body && dateValue == date && msgTypeValue == msgType) {
                        return if (idIdx >= 0) cursor.getLong(idIdx) else null
                    }
                }
                null
            }
        }.getOrElse { error ->
            XLog.w("findRecordIdByFingerprint failed: %s", error.message ?: error.javaClass.simpleName)
            null
        }
    }

    private fun insertRecord(
        context: Context,
        sender: String,
        body: String,
        date: Long,
        company: String,
        smsCode: String?,
        packageName: String,
        msgType: Int,
        isCodeSms: Boolean,
    ): Long? {
        val smsMsgUri = com.tianma.xsmscode.data.db.DBProvider.SMS_MSG_CONTENT_URI
        val resolver = context.contentResolver
        trimOldRecordsIfNeeded(context, resolver, msgType, isCodeSms)
        val values = android.content.ContentValues().apply {
            put("body", body)
            put("company", company)
            put("date", date)
            put("sender", sender)
            put("sms_code", smsCode)
            put("package_name", packageName)
            put("msg_type", msgType)
        }
        return resolver.insert(smsMsgUri, values)?.lastPathSegment?.toLongOrNull()
    }

    private fun trimOldRecordsIfNeeded(
        context: Context,
        resolver: android.content.ContentResolver,
        msgType: Int,
        isCodeSms: Boolean,
    ) {
        val smsMsgUri = com.tianma.xsmscode.data.db.DBProvider.SMS_MSG_CONTENT_URI
        val (selection, selectionArgs) = recordSelectionForType(msgType, isCodeSms)
        val cursor = resolver.query(smsMsgUri, arrayOf("_id"), selection, selectionArgs, "date ASC") ?: return
        cursor.use {
            val count = it.count
            val limit = runBlocking {
                com.tianma.xsmscode.common.utils.PrefsReader.getHistoryLimit(
                    context = context,
                    msgType = msgType,
                    isCodeSms = isCodeSms,
                )
            }
            if (limit <= 0 || count < limit) return
            val selection = "_id = ?"
            val operations = ArrayList<android.content.ContentProviderOperation>()
            for (i in 0 until (count - limit + 1)) {
                if (!it.moveToNext()) break
                val id = it.getLong(0)
                val operation = android.content.ContentProviderOperation.newDelete(smsMsgUri)
                    .withSelection(selection, arrayOf(id.toString()))
                    .build()
                operations.add(operation)
            }
            if (operations.isNotEmpty()) {
                resolver.applyBatch(com.tianma.xsmscode.data.db.DBProvider.AUTHORITY, operations)
            }
        }
    }

    private fun recordSelectionForType(msgType: Int, isCodeSms: Boolean): Pair<String, Array<String>> {
        return when (msgType) {
            SmsMsg.MSG_TYPE_APP_NOTIFY -> {
                "msg_type = ?" to arrayOf(SmsMsg.MSG_TYPE_APP_NOTIFY.toString())
            }

            SmsMsg.MSG_TYPE_SMS -> {
                if (isCodeSms) {
                    "msg_type = ? AND sms_code IS NOT NULL AND sms_code != ''" to
                        arrayOf(SmsMsg.MSG_TYPE_SMS.toString())
                } else {
                    "msg_type = ? AND (sms_code IS NULL OR sms_code = '')" to
                        arrayOf(SmsMsg.MSG_TYPE_SMS.toString())
                }
            }

            else -> {
                "msg_type = ?" to arrayOf(msgType.toString())
            }
        }
    }

    private fun setOrderedResult(
        pendingResult: PendingResult,
        ordered: Boolean,
        code: Int,
        reason: String,
        eventId: String,
    ) {
        if (!ordered) return
        runCatching {
            pendingResult.setResultCode(code)
            pendingResult.setResultData("reason=$reason;event_id=${eventId.ifBlank { "<none>" }}")
        }
    }

    private fun buildTraceId(intent: Intent, eventId: String): String {
        if (eventId.isNotBlank()) return eventId
        val pkg = intent.getStringExtra("packageName") ?: "unknown"
        val now = System.currentTimeMillis().toString(36)
        val suffix = kotlin.math.abs((pkg + now).hashCode()).toString(36)
        return "${now}_$suffix"
    }

    private fun terminateSelfProcess(traceId: String) {
        val pid = Process.myPid()
        ForwardFlowLog.w(traceId, "Terminating app process pid=$pid by kill action")
        XLog.w("ForwardReceiver kill action: terminate process pid=%d", pid)
        runCatching { Process.killProcess(pid) }
            .onFailure { error ->
                XLog.e("ForwardReceiver kill action failed", error)
            }
    }
}
