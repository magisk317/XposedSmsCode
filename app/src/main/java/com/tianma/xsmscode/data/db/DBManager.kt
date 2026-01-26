package com.tianma.xsmscode.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.tianma.xsmscode.data.db.dao.AppInfoDao
import com.tianma.xsmscode.data.db.dao.SmsCodeRuleDao
import com.tianma.xsmscode.data.db.dao.SmsMsgDao
import com.tianma.xsmscode.data.db.entity.AppInfo
import com.tianma.xsmscode.data.db.entity.SmsCodeRule
import com.tianma.xsmscode.data.db.entity.SmsMsg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.concurrent.Callable

/**
 * Database Manager for Room (Migrated from GreenDao)
 */
class DBManager private constructor(context: Context) {
    private val mDatabase: AppDatabase = AppDatabase.getInstance(context)
    private val mSmsCodeRuleDao: SmsCodeRuleDao = mDatabase.smsCodeRuleDao()
    private val mSmsMsgDao: SmsMsgDao = mDatabase.smsMsgDao()
    private val mAppInfoDao: AppInfoDao = mDatabase.appInfoDao()

    @Deprecated("Use Room DAOs directly if possible. This returns a raw SQLiteDatabase for legacy ContentProvider support.")
    fun getSQLiteDatabase(): SQLiteDatabase {
        // Warning: Room uses SupportSQLiteDatabase, but DBProvider expects android.database.sqlite.SQLiteDatabase
        // We will try to open the database file directly for raw SQL if needed,
        // or refactor DBProvider to use SupportSQLiteDatabase if possible.
        // For now, let's keep it compatible by opening the file.
        val path = mDatabase.openHelper.writableDatabase.path ?: throw IllegalStateException("Database path is null")
        return SQLiteDatabase.openOrCreateDatabase(path, null)
    }

    suspend fun updateSmsCodeRuleSuspend(smsCodeRule: SmsCodeRule) {
        withContext(Dispatchers.IO) {
            mSmsCodeRuleDao.update(smsCodeRule)
        }
    }

    suspend fun isExistsSuspend(codeRule: SmsCodeRule): Boolean {
        return withContext(Dispatchers.IO) {
            isExists(codeRule)
        }
    }

    suspend fun addSmsCodeRuleSuspend(smsCodeRule: SmsCodeRule): Long {
        return withContext(Dispatchers.IO) {
            mSmsCodeRuleDao.insert(smsCodeRule)
        }
    }

    fun addSmsCodeRule(smsCodeRule: SmsCodeRule): Long {
        return mSmsCodeRuleDao.insert(smsCodeRule)
    }


    fun addSmsCodeRules(smsCodeRules: List<SmsCodeRule>) {
        mSmsCodeRuleDao.insertAll(smsCodeRules)
    }


    suspend fun addSmsCodeRulesSuspend(smsCodeRules: List<SmsCodeRule>): List<SmsCodeRule> {
        return withContext(Dispatchers.IO) {
            mSmsCodeRuleDao.insertAll(smsCodeRules)
            smsCodeRules
        }
    }

    fun updateSmsCodeRule(smsCodeRule: SmsCodeRule) {
        mSmsCodeRuleDao.update(smsCodeRule)
    }


    fun queryAllSmsCodeRules(): List<SmsCodeRule> {
        return mSmsCodeRuleDao.getAll()
    }


    suspend fun queryAllSmsCodeRulesSuspend(): List<SmsCodeRule> {
        return withContext(Dispatchers.IO) { mSmsCodeRuleDao.getAll() }
    }

    // New Coroutines support
    fun queryAllSmsCodeRulesFlow(): Flow<List<SmsCodeRule>> {
        return mSmsCodeRuleDao.getAllFlow()
    }

    fun querySmsCodeRules(criteria: SmsCodeRule): List<SmsCodeRule> {
        return mSmsCodeRuleDao.queryRules(criteria.company, criteria.codeKeyword, criteria.codeRegex)
    }


    fun isExists(codeRule: SmsCodeRule): Boolean {
        return querySmsCodeRules(codeRule).isNotEmpty()
    }


    fun removeSmsCodeRule(smsCodeRule: SmsCodeRule) {
        mSmsCodeRuleDao.delete(smsCodeRule)
    }


    suspend fun removeSmsCodeRuleSuspend(smsCodeRule: SmsCodeRule): SmsCodeRule {
        return withContext(Dispatchers.IO) {
            mSmsCodeRuleDao.delete(smsCodeRule)
            smsCodeRule
        }
    }

    fun removeAllSmsCodeRules() {
        mSmsCodeRuleDao.clearAll()
    }


    suspend fun removeAllSmsCodeRulesSuspend() {
        withContext(Dispatchers.IO) {
             mSmsCodeRuleDao.clearAll()
        }
    }

    fun addSmsMsg(smsMsg: SmsMsg) {
        mSmsMsgDao.insert(smsMsg)
    }


    fun addSmsMsgList(smsMsgList: List<SmsMsg>) {
        mSmsMsgDao.insertAll(smsMsgList)
    }


    fun queryAllSmsMsg(): List<SmsMsg> {
        return mSmsMsgDao.getAll()
    }


    fun queryAllSmsMsgFlow(): Flow<List<SmsMsg>> {
        return mSmsMsgDao.getAllFlow()
    }

    fun removeSmsMsgList(smsMsgList: List<SmsMsg>) {
        mSmsMsgDao.deleteInTx(smsMsgList)
    }


    suspend fun removeSmsMsgListSuspend(smsMsgList: List<SmsMsg>) {
        withContext(Dispatchers.IO) {
            mSmsMsgDao.deleteInTx(smsMsgList)
        }
    }

    fun queryAllBlockedApps(): List<AppInfo> {
        return mAppInfoDao.getAll()
    }


    suspend fun queryAllBlockedAppsSuspend(): List<AppInfo> {
        return withContext(Dispatchers.IO) { mAppInfoDao.getAll() }
    }
    

    suspend fun removeBlockedAppsSuspend(appList: List<AppInfo>): List<AppInfo> {
        return withContext(Dispatchers.IO) {
            mAppInfoDao.deleteInTx(appList)
            appList
        }
    }


    suspend fun addBlockedAppsSuspend(appList: List<AppInfo>): List<AppInfo> {
        return withContext(Dispatchers.IO) {
            mAppInfoDao.insertAll(appList)
            appList
        }
    }

    // Legacy generic methods for AppBlockViewModel compatibility
    fun <T> deleteAll(entityClass: Class<T>) {
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

    @Suppress("UNCHECKED_CAST")
    fun <T> insertOrReplaceInTx(entityClass: Class<T>, entities: List<T>) {
        if (entityClass == AppInfo::class.java) {
            mAppInfoDao.insertAll(entities as List<AppInfo>)
        } else if (entityClass == SmsCodeRule::class.java) {
            mSmsCodeRuleDao.insertAll(entities as List<SmsCodeRule>)
        } else if (entityClass == SmsMsg::class.java) {
            mSmsMsgDao.insertAll(entities as List<SmsMsg>)
        }
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
        fun get(context: Context): DBManager {
            return sInstance ?: synchronized(DBManager::class.java) {
                sInstance ?: DBManager(context).also { sInstance = it }
            }
        }
    }
}
