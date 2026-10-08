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
 * XSC rule storage on top of the Room DAO, speaking the Room entity the screens
 * already use.
 *
 * The shared contract in core deals in [SmsCodeRuleRecord] so neither app has to know
 * about Room. This adapter is the one place that translates, which keeps the screens
 * free of record/entity plumbing.
 */
class RoomSmsCodeRuleRepository(
    private val dbManager: DBManager,
) : SmsCodeRuleRepository {

    fun observeRuleEntities(): Flow<List<SmsCodeRule>> =
        dbManager.queryAllSmsCodeRulesFlow()

    suspend fun getAllEntities(): List<SmsCodeRule> = dbManager.queryAllSmsCodeRulesSuspend()

    suspend fun getEntityById(id: Long): SmsCodeRule? = dbManager.querySmsCodeRuleByIdSuspend(id)

    suspend fun upsertEntity(rule: SmsCodeRule) {
        if (rule.id == null || rule.id == 0L) {
            dbManager.addSmsCodeRuleSuspend(rule)
        } else {
            dbManager.updateSmsCodeRuleSuspend(rule)
        }
    }

    suspend fun deleteEntity(rule: SmsCodeRule) {
        dbManager.removeSmsCodeRuleSuspend(rule)
    }

    suspend fun insertAllEntities(rules: List<SmsCodeRule>) {
        dbManager.addSmsCodeRulesSuspend(rules)
    }

    suspend fun clearAllEntities() {
        getAllEntities().forEach { dbManager.removeSmsCodeRuleSuspend(it) }
    }

    // --- the shared contract, expressed in records ---------------------------

    override fun observeRules(): Flow<List<SmsCodeRuleRecord>> =
        observeRuleEntities().map { rules -> rules.map { it.toRecord() } }

    override suspend fun getAll(): List<SmsCodeRuleRecord> = getAllEntities().map { it.toRecord() }

    override suspend fun getById(id: Long): SmsCodeRuleRecord? = getEntityById(id)?.toRecord()

    override suspend fun upsert(record: SmsCodeRuleRecord): Long {
        val entity = record.toEntity()
        upsertEntity(entity)
        return entity.id ?: record.id
    }

    override suspend fun delete(record: SmsCodeRuleRecord) {
        deleteEntity(record.toEntity())
    }

    override suspend fun insertAll(records: List<SmsCodeRuleRecord>) {
        insertAllEntities(records.map { it.toEntity() })
    }

    override suspend fun clearAll() {
        clearAllEntities()
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
