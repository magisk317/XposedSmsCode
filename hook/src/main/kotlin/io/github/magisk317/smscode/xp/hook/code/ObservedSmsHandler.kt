package io.github.magisk317.smscode.xp.hook.code

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.telephony.SubscriptionManager
import android.util.Log
import io.github.magisk317.smscode.common.utils.HookPrefsReader
import io.github.magisk317.xposed.runtime.contract.logging.LogRoute
import io.github.magisk317.smscode.common.utils.SmsCodeUtils
import io.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge
import io.github.magisk317.smscode.runtime.verification.SmsRoutingBackfill
import io.github.magisk317.smscode.runtime.verification.SmsRoutingCandidate
import io.github.magisk317.smscode.runtime.verification.SmsRoutingStore
import io.github.magisk317.smscode.data.db.entity.SmsMsg
import io.github.magisk317.smscode.rule.utils.SmsCodeParsedMetadataResolver
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.smscode.verification.ObservedInboxScanRecord
import io.github.magisk317.smscode.runtime.verification.ObservedSmsHandler as SharedObservedSmsHandler
import io.github.magisk317.smscode.verification.SmsDuplicateCheckResult
import io.github.magisk317.smscode.runtime.verification.SmsCodePostParseCoordinator
import io.github.magisk317.smscode.verification.SmsInboxObserverDecision
import io.github.magisk317.smscode.xp.helper.ModuleConflictArbiter
import java.util.concurrent.ScheduledExecutorService

internal class ObservedSmsHandler(
    private val pluginContext: Context,
    private val phoneContext: Context,
    private val actionExecutor: ScheduledExecutorService? = null,
    private val settingsLoader: (Context) -> SmsCodePostParseCoordinator.Settings = { context ->
        SmsCodePostParseCoordinator.loadSettings(SmsCodeVerificationPrefs(context))
    },
    private val planFactory: (SmsCodePostParseCoordinator.Settings) -> SmsCodePostParseCoordinator.ObservedSmsPlan =
        SmsCodePostParseCoordinator::createObservedSmsPlan,
        private val moduleEnabledReader: (Context) -> Boolean = HookPrefsReader::isEnabled,
    private val conflictSuppressor: (Context, String) -> Boolean = { context, source ->
        ModuleConflictArbiter.shouldSuppressByRelay(context, source)
    },
    private val sharedGateClaimer: (Context, String, String, Long, Int) -> io.github.magisk317.smscode.runtime.bridge.HookRuntimeGateClaimResult =
        { context, fileName, key, windowMs, maxEntries ->
            HookRuntimeBridge.contentProviderAccess.claimRuntimeGate(
                context = context,
                fileName = fileName,
                keys = listOf(key),
                windowMs = windowMs,
                maxEntries = maxEntries,
            )
    },
    private val roleStateLogger: (String) -> Unit = {},
    private val duplicateChecker: ((SmsCodePostParseCoordinator.Settings, String, String, Long) -> SmsDuplicateCheckResult)? = null,
    private val smsEnricher: (Context, ObservedInboxScanRecord) -> VerificationSmsMsg = { context, record ->
        val (company, packageName) = resolveCompanyAndPackage(context, record.body)
        SmsMsg(
            sender = record.sender,
            body = record.body,
            date = if (record.date > 0) record.date else System.currentTimeMillis(),
            company = company,
            smsCode = record.code,
            packageName = packageName,
            msgType = SmsMsg.MSG_TYPE_SMS,
            simSlot = record.simSlot,
            subId = record.subId,
        ).toVerificationMessage()
    },
    private val dispatcher: (
        Context,
        Context,
        VerificationSmsMsg,
        String,
        SmsCodePostParseCoordinator.ObservedSmsPlan,
    ) -> Unit = { resolvedPluginContext, resolvedPhoneContext, smsMsg, eventId, plan ->
        SmsCodeActionDispatcher.dispatchObservedSmsActions(
            executor = actionExecutor,
            pluginContext = resolvedPluginContext,
            phoneContext = resolvedPhoneContext,
            smsMsg = smsMsg.raw,
            eventId = eventId,
            plan = plan,
        )
    },
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    data class Outcome(
        val eventId: String,
        val decision: SmsInboxObserverDecision.Decision,
        val dispatched: Boolean,
    )

    private data class ExistingSmsRouting(
        val id: Long,
        val simSlot: Int,
        val subId: Int,
        val timestampDeltaMs: Long,
        val matchMode: String,
    )

    private val delegate = SharedObservedSmsHandler(
        pluginContext = pluginContext,
        phoneContext = phoneContext,
        settingsLoader = settingsLoader,
        planFactory = planFactory,
        moduleEnabledReader = moduleEnabledReader,
        conflictSuppressor = conflictSuppressor,
        sharedGateClaimer = { context, fileName, key, windowMs, maxEntries ->
            sharedGateClaimer(context, fileName, key, windowMs, maxEntries).toShared()
        },
        roleStateLogger = roleStateLogger,
        duplicateChecker = { settings, sender, body, date ->
            (duplicateChecker ?: ::defaultDuplicateCheck)(settings, sender, body, date)
        },
        smsEnricher = smsEnricher,
        dispatcher = dispatcher,
        currentTimeMillis = currentTimeMillis,
    )

    fun handle(record: ObservedInboxScanRecord): Outcome {
        backfillExistingSimRouting(record)
        val outcome = delegate.handle(record)
        return Outcome(
            eventId = outcome.eventId,
            decision = outcome.decision,
            dispatched = outcome.dispatched,
        )
    }

    fun repairRouting(record: ObservedInboxScanRecord): Boolean {
        return backfillExistingSimRouting(record)
    }

    private fun defaultDuplicateCheck(
        settings: SmsCodePostParseCoordinator.Settings,
        sender: String,
        body: String,
        date: Long,
    ): SmsDuplicateCheckResult {
        if (!settings.deduplicateSmsEnabled) {
            return SmsDuplicateCheckResult.NOT_DUPLICATE
        }
        val timestamp = if (date > 0) date else currentTimeMillis()
        return HookRuntimeBridge.contentProviderAccess.querySmsMsgByFingerprint(
            context = pluginContext,
            sender = sender,
            body = body,
            date = timestamp,
        )
    }

    private fun backfillExistingSimRouting(record: ObservedInboxScanRecord): Boolean {
        return runCatching {
            val timestamp = if (record.date > 0) record.date else currentTimeMillis()
            SmsRoutingBackfill.backfill(
                smsId = record.smsId,
                sender = record.sender,
                body = record.body,
                recordSimSlot = record.simSlot,
                recordSubId = record.subId,
                timestampMs = timestamp,
                store = providerRoutingStore,
            )
        }.onFailure {
            XLog.w(
                message = "Diag SMS routing backfill failed: sms_id=%d err=%s",
                record.smsId,
                it.message ?: it.javaClass.simpleName,
            )
        }.getOrDefault(false)
    }

    /** Provider-backed transport: recent sms_msg rows + targeted routing patch. */
    private val providerRoutingStore = object : SmsRoutingStore {
        override fun findRecentSms(limit: Int): List<SmsRoutingCandidate> {
            val uri = HookRuntimeBridge.contentProviderAccess.smsMsgContentUri(pluginContext)
                .withProviderIpcToken(pluginContext)
            val projection = arrayOf("_id", "sender", "body", "date", "sim_slot", "sub_id")
            val selection = "msg_type = ?"
            val selectionArgs = arrayOf(SmsMsg.MSG_TYPE_SMS.toString())
            val sortOrder = "date DESC LIMIT $limit"
            val cursor = pluginContext.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
                ?: return emptyList()
            return cursor.use {
                val rows = mutableListOf<SmsRoutingCandidate>()
                while (it.moveToNext()) {
                    rows += SmsRoutingCandidate(
                        id = it.getLongByColumn("_id"),
                        sender = it.getStringOrEmpty("sender"),
                        body = it.getStringOrEmpty("body"),
                        date = it.getLongByColumn("date"),
                        simSlot = it.getIntByColumn("sim_slot", -1),
                        subId = it.getIntByColumn("sub_id", 0),
                    )
                }
                rows
            }
        }

        override fun updateRouting(recordId: Long, simSlot: Int, subId: Int): Int {
            val uri = Uri.withAppendedPath(
                HookRuntimeBridge.contentProviderAccess.smsMsgContentUri(pluginContext),
                recordId.toString(),
            ).withProviderIpcToken(pluginContext)
            val values = ContentValues().apply {
                put("sim_slot", simSlot)
                put("sub_id", subId)
            }
            return pluginContext.contentResolver.update(uri, values, null, null)
        }

        private fun android.database.Cursor.getStringOrEmpty(column: String): String {
            val index = getColumnIndex(column)
            if (index < 0 || isNull(index)) return ""
            return getString(index).orEmpty()
        }

        private fun android.database.Cursor.getLongByColumn(column: String): Long =
            getLong(getColumnIndexOrThrow(column))

        private fun android.database.Cursor.getIntByColumn(column: String, defaultValue: Int): Int {
            val index = getColumnIndex(column)
            if (index < 0 || isNull(index)) return defaultValue
            return getInt(index)
        }
    }

    private fun io.github.magisk317.smscode.runtime.bridge.HookRuntimeGateClaimResult.toShared(): SharedObservedSmsHandler.ClaimResult {
        return SharedObservedSmsHandler.ClaimResult(
            claimed = claimed,
            ageMs = ageMs,
        )
    }

    private companion object {
        private fun resolveCompanyAndPackage(
            phoneContext: Context,
            body: String,
        ): Pair<String, String?> {
            val metadata = SmsCodeParsedMetadataResolver.resolve(
                body = body,
                parseCompanyCandidates = SmsCodeUtils::parseCompanyCandidates,
                parseCompany = SmsCodeUtils::parseCompany,
                findPackageNameByLabel = { label ->
                    SmsCodeUtils.findPackageNameByLabel(phoneContext, label)
                },
            )
            return metadata.company to metadata.packageName
        }
    }
}
