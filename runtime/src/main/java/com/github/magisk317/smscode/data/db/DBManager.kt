package com.github.magisk317.smscode.data.db

import android.content.Context
import com.github.magisk317.smscode.data.db.dao.AppInfoDao
import com.github.magisk317.smscode.data.db.dao.AutoInputEventDao
import com.github.magisk317.smscode.data.db.dao.SmsCodeRuleDao
import com.github.magisk317.smscode.data.db.dao.SmsMsgDao
import com.github.magisk317.smscode.data.db.entity.AppInfo
import com.github.magisk317.smscode.data.db.entity.AutoInputEvent
import com.github.magisk317.smscode.data.db.entity.SmsCodeRule
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking

/**
 * Database Manager for Room (Migrated from GreenDao)
 */
class DBManager private constructor(context: Context) {
    private val mDatabase: AppDatabase = AppDatabase.getInstance(context)
    private val mSmsCodeRuleDao: SmsCodeRuleDao = mDatabase.smsCodeRuleDao()
    private val mSmsMsgDao: SmsMsgDao = mDatabase.smsMsgDao()
    private val mAppInfoDao: AppInfoDao = mDatabase.appInfoDao()
    private val mAutoInputEventDao: AutoInputEventDao = mDatabase.autoInputEventDao()


    suspend fun updateSmsCodeRuleSuspend(smsCodeRule: SmsCodeRule) {
        withContext(Dispatchers.IO) {
            mSmsCodeRuleDao.update(smsCodeRule)
        }
    }

    suspend fun isExistsSuspend(codeRule: SmsCodeRule): Boolean = withContext(Dispatchers.IO) {
        isExists(codeRule)
    }

    suspend fun addSmsCodeRuleSuspend(smsCodeRule: SmsCodeRule): Long = withContext(Dispatchers.IO) {
        mSmsCodeRuleDao.insert(smsCodeRule)
    }

    fun addSmsCodeRule(smsCodeRule: SmsCodeRule): Long = runBlocking { mSmsCodeRuleDao.insert(smsCodeRule) }

    fun addSmsCodeRules(smsCodeRules: List<SmsCodeRule>) = runBlocking {
        mSmsCodeRuleDao.insertAll(smsCodeRules)
    }

    suspend fun addSmsCodeRulesSuspend(smsCodeRules: List<SmsCodeRule>): List<SmsCodeRule> =
        withContext(Dispatchers.IO) {
            mSmsCodeRuleDao.insertAll(smsCodeRules)
            smsCodeRules
        }

    fun updateSmsCodeRule(smsCodeRule: SmsCodeRule) = runBlocking {
        mSmsCodeRuleDao.update(smsCodeRule)
    }

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
    }

    suspend fun removeSmsCodeRuleSuspend(smsCodeRule: SmsCodeRule): SmsCodeRule = withContext(Dispatchers.IO) {
        mSmsCodeRuleDao.delete(smsCodeRule)
        smsCodeRule
    }

    fun removeAllSmsCodeRules() = runBlocking {
        mSmsCodeRuleDao.clearAll()
    }

    suspend fun removeAllSmsCodeRulesSuspend() {
        withContext(Dispatchers.IO) {
            mSmsCodeRuleDao.clearAll()
        }
    }

    fun addSmsMsg(smsMsg: SmsMsg): Long = runBlocking { mSmsMsgDao.insert(smsMsg) }

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
}
