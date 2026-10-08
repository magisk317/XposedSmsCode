package io.github.magisk317.smscode.data.db

import android.content.Context
import androidx.room.withTransaction
import io.github.magisk317.smscode.db.dao.AppInfoDao
import io.github.magisk317.smscode.db.dao.AutoInputEventDao
import io.github.magisk317.smscode.db.dao.SmsCodeRuleDao
import io.github.magisk317.smscode.db.dao.SmsMsgDao
import io.github.magisk317.smscode.db.entity.AppInfo
import io.github.magisk317.smscode.db.entity.AutoInputEvent
import io.github.magisk317.smscode.db.entity.SmsCodeRule
import io.github.magisk317.smscode.db.entity.SmsMsg
import io.github.magisk317.smscode.rule.utils.CodeRecordSimilarityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking

/**
 * Database Manager for Room (Migrated from GreenDao)
 */
class DBManager private constructor(context: Context) {
    private val appContext: Context = context.applicationContext ?: context
    private val mDatabase: AppDatabase = AppDatabase.getInstance(context)
    private val mSmsCodeRuleDao: SmsCodeRuleDao = mDatabase.smsCodeRuleDao()
    private val mSmsMsgDao: SmsMsgDao = mDatabase.smsMsgDao()
    private val mAppInfoDao: AppInfoDao = mDatabase.appInfoDao()
    private val mAutoInputEventDao: AutoInputEventDao = mDatabase.autoInputEventDao()

    private fun notifyRulesCacheChanged() {
        DBProvider.notifyRulesCacheChanged(appContext)
    }

    suspend fun updateSmsCodeRuleSuspend(smsCodeRule: SmsCodeRule) {
        withContext(Dispatchers.IO) {
            mSmsCodeRuleDao.update(smsCodeRule)
        }
        notifyRulesCacheChanged()
    }

    suspend fun isExistsSuspend(codeRule: SmsCodeRule): Boolean = withContext(Dispatchers.IO) {
        isExists(codeRule)
    }

    suspend fun addSmsCodeRuleSuspend(smsCodeRule: SmsCodeRule): Long = withContext(Dispatchers.IO) {
        mSmsCodeRuleDao.insert(smsCodeRule)
    }.also { notifyRulesCacheChanged() }

    fun addSmsCodeRule(smsCodeRule: SmsCodeRule): Long =
        runBlocking { mSmsCodeRuleDao.insert(smsCodeRule) }.also { notifyRulesCacheChanged() }

    fun addSmsCodeRules(smsCodeRules: List<SmsCodeRule>) = runBlocking {
        mSmsCodeRuleDao.insertAll(smsCodeRules)
    }.also { notifyRulesCacheChanged() }

    suspend fun addSmsCodeRulesSuspend(smsCodeRules: List<SmsCodeRule>): List<SmsCodeRule> =
        withContext(Dispatchers.IO) {
            mSmsCodeRuleDao.insertAll(smsCodeRules)
            smsCodeRules
        }.also { notifyRulesCacheChanged() }

    fun updateSmsCodeRule(smsCodeRule: SmsCodeRule) = runBlocking {
        mSmsCodeRuleDao.update(smsCodeRule)
    }.also { notifyRulesCacheChanged() }

    fun queryAllSmsCodeRules(): List<SmsCodeRule> = runBlocking { mSmsCodeRuleDao.getAll() }

    fun querySmsCodeRuleById(id: Long): SmsCodeRule? = runBlocking { mSmsCodeRuleDao.getById(id) }

    suspend fun queryAllSmsCodeRulesSuspend(): List<SmsCodeRule> = withContext(Dispatchers.IO) {
        mSmsCodeRuleDao.getAll()
    }

    suspend fun querySmsCodeRuleByIdSuspend(id: Long): SmsCodeRule? = withContext(Dispatchers.IO) {
        mSmsCodeRuleDao.getById(id)
    }

    // New Coroutines support
    fun queryAllSmsCodeRulesFlow(): Flow<List<SmsCodeRule>> = mSmsCodeRuleDao.getAllFlow()

    fun querySmsCodeRules(criteria: SmsCodeRule): List<SmsCodeRule> =
        runBlocking { mSmsCodeRuleDao.queryRules(criteria.company, criteria.codeKeyword, criteria.codeRegex) }

    fun isExists(codeRule: SmsCodeRule): Boolean = querySmsCodeRules(codeRule).isNotEmpty()

    fun removeSmsCodeRule(smsCodeRule: SmsCodeRule) = runBlocking {
        mSmsCodeRuleDao.delete(smsCodeRule)
    }.also { notifyRulesCacheChanged() }

    suspend fun removeSmsCodeRuleSuspend(smsCodeRule: SmsCodeRule): SmsCodeRule = withContext(Dispatchers.IO) {
        mSmsCodeRuleDao.delete(smsCodeRule)
        smsCodeRule
    }.also { notifyRulesCacheChanged() }

    fun removeAllSmsCodeRules() = runBlocking {
        mSmsCodeRuleDao.clearAll()
    }.also { notifyRulesCacheChanged() }

    suspend fun removeAllSmsCodeRulesSuspend() {
        withContext(Dispatchers.IO) {
            mSmsCodeRuleDao.clearAll()
        }
        notifyRulesCacheChanged()
    }

    fun addSmsMsg(smsMsg: SmsMsg): Long = runBlocking { mSmsMsgDao.insert(smsMsg) }

    /**
     * Atomically inserts a record or returns the row that already owns the
     * canonical fingerprint (sender, body, date, message type).
     *
     * ContentProvider methods may run concurrently on several Binder threads.
     * Keeping the lookup and insert in one Room write transaction prevents two
     * hook processes from racing a check-then-insert sequence. The IGNORE
     * insert also preserves the original row instead of REPLACE deleting it.
     */
    fun insertSmsMsgOrGetExisting(
        smsMsg: SmsMsg,
        deduplicate: Boolean = true,
    ): SmsMsgInsertResult = runBlocking {
        mDatabase.withTransaction {
            val existing = mSmsMsgDao.getByFingerprint(
                sender = smsMsg.sender,
                body = smsMsg.body,
                date = smsMsg.date,
                msgType = smsMsg.msgType,
            )
            val existingId = existing?.id
            if (existingId != null) {
                return@withTransaction SmsMsgInsertResult(id = existingId, duplicate = true)
            }

            if (deduplicate) {
                val timestamp = smsMsg.date.takeIf { it > 0L } ?: System.currentTimeMillis()
                val from = (timestamp - RECORD_DEDUP_WINDOW_MS).coerceAtLeast(0L)
                val to = timestamp + RECORD_DEDUP_WINDOW_MS
                val code = smsMsg.smsCode
                if (!code.isNullOrBlank()) {
                    val codeDuplicate = mSmsMsgDao.getByCodeInRange(
                        smsCode = code,
                        msgType = smsMsg.msgType,
                        dateFrom = from,
                        dateTo = to,
                    ).firstOrNull { existingRecord ->
                        CodeRecordSimilarityUtils.crossSourceMatchScore(
                            existingCode = existingRecord.smsCode,
                            existingBody = existingRecord.body,
                            existingCompany = existingRecord.company,
                            existingSender = existingRecord.sender,
                            incomingCode = smsMsg.smsCode,
                            incomingBody = smsMsg.body,
                            incomingCompany = smsMsg.company,
                            incomingSender = smsMsg.sender,
                        ) > 0
                    }
                    if (codeDuplicate != null) {
                        return@withTransaction SmsMsgInsertResult(
                            id = codeDuplicate.id ?: error("Code duplicate has no id"),
                            duplicate = true,
                        )
                    }

                    smsMsg.packageName?.takeIf { it.isNotBlank() }?.let { packageName ->
                        mSmsMsgDao.getByCodeAndPackageInRange(
                            smsCode = code,
                            packageName = packageName,
                            msgType = smsMsg.msgType,
                            dateFrom = from,
                            dateTo = to,
                        )?.id?.let { id ->
                            return@withTransaction SmsMsgInsertResult(id = id, duplicate = true)
                        }
                    }
                    smsMsg.company?.takeIf { it.isNotBlank() }?.let { company ->
                        mSmsMsgDao.getByCodeAndCompanyInRange(
                            smsCode = code,
                            company = company,
                            msgType = smsMsg.msgType,
                            dateFrom = from,
                            dateTo = to,
                        )?.id?.let { id ->
                            return@withTransaction SmsMsgInsertResult(id = id, duplicate = true)
                        }
                    }
                }

                if (!smsMsg.sender.isNullOrBlank() && !smsMsg.body.isNullOrBlank()) {
                    mSmsMsgDao.getByFingerprintInRange(
                        sender = smsMsg.sender,
                        body = smsMsg.body,
                        msgType = smsMsg.msgType,
                        dateFrom = from,
                        dateTo = to,
                    )?.id?.let { id ->
                        return@withTransaction SmsMsgInsertResult(id = id, duplicate = true)
                    }
                }
            }

            val insertedId = mSmsMsgDao.insertIfAbsent(smsMsg)
            if (insertedId > 0L) {
                return@withTransaction SmsMsgInsertResult(id = insertedId, duplicate = false)
            }

            // A unique-index conflict can still win between the lookup and
            // INSERT. Resolve and return that row while still in the same
            // transaction so callers receive a stable canonical URI.
            val racedId = mSmsMsgDao.getByFingerprint(
                sender = smsMsg.sender,
                body = smsMsg.body,
                date = smsMsg.date,
                msgType = smsMsg.msgType,
            )?.id ?: error("SMS fingerprint conflict without an existing row")
            SmsMsgInsertResult(id = racedId, duplicate = true)
        }
    }

    fun addSmsMsgList(smsMsgList: List<SmsMsg>) = runBlocking {
        mSmsMsgDao.insertAll(smsMsgList)
    }

    fun queryAllSmsMsg(): List<SmsMsg> = runBlocking { mSmsMsgDao.getAll() }

    fun querySmsMsgById(id: Long): SmsMsg? = runBlocking { mSmsMsgDao.getById(id) }

    fun querySmsMsgByFingerprint(
        sender: String?,
        body: String?,
        date: Long,
        msgType: Int = SmsMsg.MSG_TYPE_SMS,
    ): SmsMsg? = runBlocking { mSmsMsgDao.getByFingerprint(sender, body, date, msgType) }

    fun querySmsMsgByFingerprintInRange(
        sender: String?,
        body: String?,
        dateFrom: Long,
        dateTo: Long,
        msgType: Int = SmsMsg.MSG_TYPE_SMS,
    ): SmsMsg? = runBlocking { mSmsMsgDao.getByFingerprintInRange(sender, body, msgType, dateFrom, dateTo) }

    fun querySmsMsgByCodeAndCompanyInRange(
        smsCode: String?,
        company: String?,
        dateFrom: Long,
        dateTo: Long,
        msgType: Int = SmsMsg.MSG_TYPE_SMS,
    ): SmsMsg? = runBlocking { mSmsMsgDao.getByCodeAndCompanyInRange(smsCode, company, msgType, dateFrom, dateTo) }

    fun querySmsMsgByCodeAndPackageInRange(
        smsCode: String?,
        packageName: String?,
        dateFrom: Long,
        dateTo: Long,
        msgType: Int = SmsMsg.MSG_TYPE_SMS,
    ): SmsMsg? = runBlocking { mSmsMsgDao.getByCodeAndPackageInRange(smsCode, packageName, msgType, dateFrom, dateTo) }

    fun querySmsMsgByCodeInRange(
        smsCode: String?,
        dateFrom: Long,
        dateTo: Long,
        msgType: Int = SmsMsg.MSG_TYPE_SMS,
    ): List<SmsMsg> = runBlocking { mSmsMsgDao.getByCodeInRange(smsCode, msgType, dateFrom, dateTo) }

    fun updateSmsMsg(smsMsg: SmsMsg): Int = runBlocking {
        val id = smsMsg.id ?: return@runBlocking 0
        if (mSmsMsgDao.getById(id) == null) {
            return@runBlocking 0
        }
        mSmsMsgDao.update(smsMsg)
        1
    }

    fun insertAutoInputAttempt(
        id: Long? = null,
        recordId: Long?,
        packageName: String?,
        codeLength: Int,
        attemptAt: Long = System.currentTimeMillis(),
    ): Long = runBlocking {
        mAutoInputEventDao.insert(
            AutoInputEvent(
                id = id?.takeIf { it > 0L } ?: 0L,
                recordId = recordId,
                packageName = packageName,
                codeLength = codeLength,
                attemptAt = attemptAt,
            ),
        )
    }

    fun updateAutoInputResult(
        attemptId: Long,
        success: Boolean,
        reason: String?,
    ): Int = runBlocking {
        mAutoInputEventDao.updateResult(attemptId, success, reason)
    }

    fun upsertAutoInputResult(
        attemptId: Long,
        success: Boolean,
        reason: String?,
    ): Long = runBlocking {
        mAutoInputEventDao.upsertResult(attemptId, 0, success, reason)
    }

    fun queryAllSmsMsgFlow(): Flow<List<SmsMsg>> = mSmsMsgDao.getAllFlow()

    fun queryAllSmsMsgCountFlow(): Flow<Long> = mSmsMsgDao.countFlow()

    fun removeSmsMsgList(smsMsgList: List<SmsMsg>) = runBlocking {
        mSmsMsgDao.deleteInTx(smsMsgList)
    }

    fun removeSmsMsgById(id: Long): Int = runBlocking {
        val item = mSmsMsgDao.getById(id) ?: return@runBlocking 0
        mSmsMsgDao.delete(item)
        1
    }

    suspend fun removeSmsMsgListSuspend(smsMsgList: List<SmsMsg>) {
        withContext(Dispatchers.IO) {
            mSmsMsgDao.deleteInTx(smsMsgList)
        }
    }

    suspend fun insertSmsMsgListSuspend(smsMsgList: List<SmsMsg>) {
        withContext(Dispatchers.IO) {
            mSmsMsgDao.insertAll(smsMsgList)
        }
    }

    fun queryAllAppInfos(): List<AppInfo> = runBlocking { mAppInfoDao.getAll() }

    fun queryAppInfoByPackageName(packageName: String): AppInfo? = runBlocking { mAppInfoDao.getByPackageName(packageName) }

    fun upsertAppInfo(appInfo: AppInfo): Int = runBlocking {
        mAppInfoDao.insert(appInfo)
        1
    }

    fun removeAppInfosByPackage(packageNames: List<String>): Int {
        if (packageNames.isEmpty()) {
            return 0
        }
        return runBlocking { mAppInfoDao.deleteByPackageNames(packageNames) }
    }

    suspend fun queryAllAppInfosSuspend(): List<AppInfo> = withContext(Dispatchers.IO) { mAppInfoDao.getAll() }

    suspend fun removeAppInfosSuspend(appList: List<AppInfo>): List<AppInfo> = withContext(Dispatchers.IO) {
        mAppInfoDao.deleteInTx(appList)
        appList
    }

    suspend fun addAppInfosSuspend(appList: List<AppInfo>): List<AppInfo> = withContext(Dispatchers.IO) {
        mAppInfoDao.insertAll(appList)
        appList
    }

    // Legacy generic methods for AppBlockViewModel compatibility
    fun <T> deleteAll(entityClass: Class<T>) = runBlocking {
        if (entityClass == AppInfo::class.java) {
            mAppInfoDao.clearAll()
        } else if (entityClass == SmsCodeRule::class.java) {
            mSmsCodeRuleDao.clearAll()
        } else if (entityClass == SmsMsg::class.java) {
            mSmsMsgDao.clearAll()
        }
    }

    suspend fun <T> deleteAllSuspend(entityClass: Class<T>) {
        withContext(Dispatchers.IO) {
            deleteAll(entityClass)
        }
    }

    fun <T> insertOrReplaceInTx(entityClass: Class<T>, entities: List<T>) = runBlocking {
        if (entityClass == AppInfo::class.java) {
            mAppInfoDao.insertAll(castEntities(entities, AppInfo::class.java))
        } else if (entityClass == SmsCodeRule::class.java) {
            mSmsCodeRuleDao.insertAll(castEntities(entities, SmsCodeRule::class.java))
        } else if (entityClass == SmsMsg::class.java) {
            mSmsMsgDao.insertAll(castEntities(entities, SmsMsg::class.java))
        }
    }

    private fun <T : Any> castEntities(entities: List<*>, clazz: Class<T>): List<T> {
        if (entities.any { !clazz.isInstance(it) }) {
            throw IllegalArgumentException("Entity list contains unexpected type for ${clazz.name}")
        }
        return entities.map { clazz.cast(it)!! }
    }

    suspend fun <T> insertOrReplaceInTxSuspend(entityClass: Class<T>, entities: List<T>) {
        withContext(Dispatchers.IO) {
            insertOrReplaceInTx(entityClass, entities)
        }
    }

    companion object {
        private const val RECORD_DEDUP_WINDOW_MS = 20_000L

        @Volatile
        private var sInstance: DBManager? = null

        @JvmStatic
        fun get(context: Context): DBManager = sInstance ?: synchronized(DBManager::class.java) {
            sInstance ?: DBManager(context).also { sInstance = it }
        }

        @JvmStatic
        fun resetInstance() {
            synchronized(DBManager::class.java) {
                sInstance = null
            }
        }
    }

    data class SmsMsgInsertResult(
        val id: Long,
        val duplicate: Boolean,
    )

}
