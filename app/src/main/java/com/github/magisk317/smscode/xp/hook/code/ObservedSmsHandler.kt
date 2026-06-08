package com.github.magisk317.smscode.xp.hook.code

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.telephony.SubscriptionManager
import android.util.Log
import com.github.magisk317.smscode.runtime.RuntimePrefsFacade as PrefsReader
import io.github.magisk317.smscode.runtime.common.utils.SharedRuntimeGate
import io.github.magisk317.smscode.runtime.contract.logging.LogRoute
import com.github.magisk317.smscode.common.utils.SmsCodeUtils
import com.github.magisk317.smscode.data.db.DBProvider
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import com.github.magisk317.smscode.runtime.RuntimeStorageFacade
import io.github.magisk317.smscode.xposed.utils.XLog
import io.github.magisk317.smscode.verification.ObservedInboxScanRecord
import io.github.magisk317.smscode.verification.ObservedSmsHandler as SharedObservedSmsHandler
import io.github.magisk317.smscode.verification.SmsCodePostParseCoordinator
import io.github.magisk317.smscode.verification.SmsInboxObserverDecision
import com.github.magisk317.smscode.xp.helper.ModuleConflictArbiter
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
    private val moduleEnabledReader: (Context) -> Boolean = PrefsReader::isEnabled,
    private val conflictSuppressor: (Context, String) -> Boolean = { context, source ->
        ModuleConflictArbiter.shouldSuppressByRelay(context, source)
    },
    private val sharedGateClaimer: (Context, String, String, Long, Int) -> SharedRuntimeGate.ClaimResult =
        { context, fileName, key, windowMs, maxEntries ->
            SharedRuntimeGate.claimWithinWindow(
                context = context,
                fileName = fileName,
                key = key,
                windowMs = windowMs,
                maxEntries = maxEntries,
            )
    },
    private val roleStateLogger: (String) -> Unit = {},
    private val duplicateChecker: ((SmsCodePostParseCoordinator.Settings, String, String, Long) -> Boolean)? = null,
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
    ): Boolean {
        if (!settings.deduplicateSmsEnabled) {
            return false
        }
        val timestamp = if (date > 0) date else currentTimeMillis()
        return runCatching {
            RuntimeStorageFacade.dbManager(pluginContext).querySmsMsgByFingerprint(sender, body, timestamp) != null
        }.getOrDefault(false)
    }

    private fun backfillExistingSimRouting(record: ObservedInboxScanRecord): Boolean {
        val backfillSimSlot = resolveBackfillSimSlot(record.simSlot, record.subId)
        if (backfillSimSlot < 0 && record.subId <= 0) {
            XLog.d("Diag SMS routing backfill skip: sms_id=%d reason=no_routing", record.smsId)
            return false
        }
        return runCatching {
            val timestamp = if (record.date > 0) record.date else currentTimeMillis()
            val existing = queryExistingSmsRouting(record, timestamp) ?: run {
                XLog.d(
                    "Diag SMS routing backfill skip: sms_id=%d reason=no_existing timestamp=%d simSlot=%d subId=%d",
                    record.smsId,
                    timestamp,
                    backfillSimSlot,
                    record.subId,
                )
                return@runCatching false
            }
            val shouldUpdateSimSlot = existing.simSlot < 0 && backfillSimSlot >= 0
            val shouldUpdateSubId = existing.subId <= 0 && record.subId > 0
            if (!shouldUpdateSimSlot && !shouldUpdateSubId) {
                XLog.d(
                    "Diag SMS routing backfill skip: sms_id=%d reason=existing_has_routing oldSlot=%d oldSubId=%d",
                    record.smsId,
                    existing.simSlot,
                    existing.subId,
                )
                return@runCatching false
            }
            val newSimSlot = if (shouldUpdateSimSlot) backfillSimSlot else existing.simSlot
            val newSubId = if (shouldUpdateSubId) record.subId else existing.subId
            val rows = updateExistingSmsRouting(
                recordId = existing.id,
                simSlot = newSimSlot,
                subId = newSubId,
            )
            XLog.log(
                Log.INFO,
                LogRoute.SMS_HOOK,
                false,
                false,
                "Diag SMS routing backfill: sms_id=%d record_id=%d rows=%d oldSlot=%d oldSubId=%d newSlot=%d newSubId=%d",
                record.smsId,
                existing.id,
                rows,
                existing.simSlot,
                existing.subId,
                newSimSlot,
                newSubId,
            )
            XLog.d(
                "Diag SMS routing backfill match: sms_id=%d record_id=%d mode=%s deltaMs=%d rawSlot=%d rawSubId=%d",
                record.smsId,
                existing.id,
                existing.matchMode,
                existing.timestampDeltaMs,
                record.simSlot,
                record.subId,
            )
            rows > 0
        }.onFailure {
            XLog.w(
                "Diag SMS routing backfill failed: sms_id=%d err=%s",
                record.smsId,
                it.message ?: it.javaClass.simpleName,
            )
        }.getOrDefault(false)
    }

    private fun queryExistingSmsRouting(
        record: ObservedInboxScanRecord,
        timestamp: Long,
    ): ExistingSmsRouting? {
        val uri = DBProvider.smsMsgContentUri(pluginContext)
        val projection = arrayOf("_id", "sender", "body", "date", "sim_slot", "sub_id")
        val selection = "msg_type = ?"
        val selectionArgs = arrayOf(SmsMsg.MSG_TYPE_SMS.toString())
        val sortOrder = "date DESC LIMIT $ROUTING_BACKFILL_QUERY_LIMIT"
        val cursor = pluginContext.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
            ?: return null
        cursor.use {
            var bestExactMatch: ExistingSmsRouting? = null
            val timeFallbackMatches = mutableListOf<ExistingSmsRouting>()
            while (it.moveToNext()) {
                val existingSender = it.getStringOrEmpty("sender")
                val existingBody = it.getStringOrEmpty("body")
                val existingDate = it.getLongByColumn("date")
                val delta = kotlin.math.abs(existingDate - timestamp)
                if (delta > ROUTING_BACKFILL_WINDOW_MS) {
                    continue
                }
                val candidate = ExistingSmsRouting(
                    id = it.getLongByColumn("_id"),
                    simSlot = it.getIntByColumn("sim_slot", -1),
                    subId = it.getIntByColumn("sub_id", 0),
                    timestampDeltaMs = delta,
                    matchMode = if (existingSender == record.sender && existingBody == record.body) {
                        ROUTING_MATCH_EXACT
                    } else {
                        ROUTING_MATCH_TIME_FALLBACK
                    },
                )
                if (candidate.matchMode == ROUTING_MATCH_EXACT) {
                    val currentBest = bestExactMatch
                    if (currentBest == null || candidate.timestampDeltaMs < currentBest.timestampDeltaMs) {
                        bestExactMatch = candidate
                    }
                } else if (candidate.simSlot < 0 || candidate.subId <= 0) {
                    timeFallbackMatches += candidate
                }
            }
            bestExactMatch?.let { return it }
            if (timeFallbackMatches.size == 1) {
                return timeFallbackMatches.single()
            }
            if (timeFallbackMatches.size > 1) {
                XLog.d(
                    "Diag SMS routing backfill skip: sms_id=%d reason=ambiguous_time_fallback candidates=%d",
                    record.smsId,
                    timeFallbackMatches.size,
                )
            }
            return null
        }
    }

    private fun updateExistingSmsRouting(recordId: Long, simSlot: Int, subId: Int): Int {
        val uri = Uri.withAppendedPath(DBProvider.smsMsgContentUri(pluginContext), recordId.toString())
        val values = ContentValues().apply {
            put("sim_slot", simSlot)
            put("sub_id", subId)
        }
        return pluginContext.contentResolver.update(uri, values, null, null)
    }

    private fun Cursor.getStringOrEmpty(column: String): String {
        val index = getColumnIndex(column)
        if (index < 0 || isNull(index)) return ""
        return getString(index).orEmpty()
    }

    private fun Cursor.getLongByColumn(column: String): Long {
        return getLong(getColumnIndexOrThrow(column))
    }

    private fun Cursor.getIntByColumn(column: String, defaultValue: Int): Int {
        val index = getColumnIndex(column)
        if (index < 0 || isNull(index)) return defaultValue
        return getInt(index)
    }

    private fun resolveBackfillSimSlot(simSlot: Int, subId: Int): Int {
        if (simSlot >= 0) return simSlot
        if (subId <= 0) return -1
        val platformSlot = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { SubscriptionManager.getSlotIndex(subId) }.getOrDefault(-1)
        } else {
            -1
        }
        if (platformSlot >= 0) return platformSlot
        return when (subId) {
            1 -> 0
            2 -> 1
            else -> -1
        }
    }

    private fun SharedRuntimeGate.ClaimResult.toShared(): SharedObservedSmsHandler.ClaimResult {
        return SharedObservedSmsHandler.ClaimResult(
            claimed = claimed,
            ageMs = ageMs,
        )
    }

    private companion object {
        private const val ROUTING_BACKFILL_WINDOW_MS = 30 * 60 * 1000L
        private const val ROUTING_BACKFILL_QUERY_LIMIT = 64
        private const val ROUTING_MATCH_EXACT = "exact"
        private const val ROUTING_MATCH_TIME_FALLBACK = "time_fallback"

        private fun resolveCompanyAndPackage(
            phoneContext: Context,
            body: String,
        ): Pair<String, String?> {
            val companyCandidates = SmsCodeUtils.parseCompanyCandidates(body)
                .map { it.trim().trim('【', '】', '[', ']') }
                .filter { it.isNotBlank() }
            var company = SmsCodeUtils.parseCompany(body)
                .trim()
                .trim('【', '】', '[', ']')
            var resolvedPackage: String? = null
            for (candidate in companyCandidates) {
                val pkg = SmsCodeUtils.findPackageNameByLabel(phoneContext, candidate)
                if (!pkg.isNullOrBlank()) {
                    company = candidate
                    resolvedPackage = pkg
                    break
                }
            }
            if (resolvedPackage.isNullOrBlank()) {
                resolvedPackage = SmsCodeUtils.findPackageNameByLabel(phoneContext, company)
            }
            return company to resolvedPackage
        }
    }
}
