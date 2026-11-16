package io.github.magisk317.smscode.data.repository

import io.github.magisk317.smscode.data.db.DBManager
import io.github.magisk317.smscode.db.entity.SmsCodeRule
import io.github.magisk317.smscode.rule.model.SmsCodeMatchedRuleSource
import io.github.magisk317.smscode.rule.model.SmsCodeRuleSpec
import io.github.magisk317.smscode.rule.repository.SmsCodeRuleRecord
import io.github.magisk317.smscode.rule.repository.SmsCodeRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * XSC rule storage on top of the Room DAO, speaking the shared
 * [SmsCodeRuleRepository] contract in [SmsCodeRuleRecord]s so neither app has to
 * know about Room. This adapter is the one place that translates, which keeps the
 * screens free of record/entity plumbing.
 */
class RoomSmsCodeRuleRepository(
    private val dbManager: DBManager,
) : SmsCodeRuleRepository {

    override fun observeRules(): Flow<List<SmsCodeRuleRecord>> =
        dbManager.queryAllSmsCodeRulesFlow().map { rules -> rules.map { it.toRecord() } }

    override suspend fun getAll(): List<SmsCodeRuleRecord> =
        dbManager.queryAllSmsCodeRulesSuspend().map { it.toRecord() }

    override suspend fun getById(id: Long): SmsCodeRuleRecord? =
        dbManager.querySmsCodeRuleByIdSuspend(id)?.toRecord()

    override suspend fun upsert(record: SmsCodeRuleRecord): Long {
        val entity = record.toEntity()
        if (entity.id == null || entity.id == 0L) {
            dbManager.addSmsCodeRuleSuspend(entity)
        } else {
            dbManager.updateSmsCodeRuleSuspend(entity)
        }
        return entity.id ?: record.id
    }

    override suspend fun delete(record: SmsCodeRuleRecord) {
        dbManager.removeSmsCodeRuleSuspend(record.toEntity())
    }

    override suspend fun insertAll(records: List<SmsCodeRuleRecord>) {
        dbManager.addSmsCodeRulesSuspend(records.map { it.toEntity() })
    }

    override suspend fun clearAll() {
        dbManager.queryAllSmsCodeRulesSuspend().forEach { dbManager.removeSmsCodeRuleSuspend(it) }
    }
}

/**
 * The stored rule carries company, keyword and regex only; the remaining spec fields
 * are matcher configuration this storage does not persist.
 */
internal fun SmsCodeRule.toRecord(): SmsCodeRuleRecord = SmsCodeRuleRecord(
    id = id ?: 0L,
    spec = SmsCodeRuleSpec(
        company = company,
        codeKeyword = codeKeyword,
        codeRegex = codeRegex,
        source = SmsCodeMatchedRuleSource.CUSTOM,
    ),
)

internal fun SmsCodeRuleRecord.toEntity(): SmsCodeRule = SmsCodeRule(
    company = spec.company,
    codeKeyword = spec.codeKeyword,
    codeRegex = spec.codeRegex,
    id = id.takeIf { it != 0L },
)
