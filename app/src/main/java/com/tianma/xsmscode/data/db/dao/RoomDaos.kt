package com.tianma.xsmscode.data.db.dao

import androidx.room.*
import com.tianma.xsmscode.data.db.entity.SmsCodeRule
import com.tianma.xsmscode.data.db.entity.SmsMsg
import com.tianma.xsmscode.data.db.entity.AppInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsCodeRuleDao {
    @Query("SELECT * FROM sms_code_rule")
    fun getAll(): List<SmsCodeRule>


    @Query("SELECT * FROM sms_code_rule")
    fun getAllFlow(): Flow<List<SmsCodeRule>>

    @Query("SELECT * FROM sms_code_rule WHERE company = :company AND code_keyword = :codeKeyword AND code_regex = :codeRegex")
    fun queryRules(company: String?, codeKeyword: String, codeRegex: String): List<SmsCodeRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(rule: SmsCodeRule): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(rules: List<SmsCodeRule>)

    @Update
    fun update(rule: SmsCodeRule)

    @Delete
    fun delete(rule: SmsCodeRule)

    @Delete
    fun deleteAll(rules: List<SmsCodeRule>)

    @Query("DELETE FROM sms_code_rule")
    fun clearAll()

    @Query("SELECT count(*) FROM sms_code_rule")
    fun count(): Long
}

@Dao
interface SmsMsgDao {
    @Query("SELECT * FROM sms_msg ORDER BY date DESC")
    fun getAll(): List<SmsMsg>


    @Query("SELECT * FROM sms_msg ORDER BY date DESC")
    fun getAllFlow(): Flow<List<SmsMsg>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(msg: SmsMsg): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(msgs: List<SmsMsg>)

    @Query("DELETE FROM sms_msg")
    fun clearAll()

    @Query("SELECT count(*) FROM sms_msg")
    fun count(): Long

    @Delete
    fun delete(msg: SmsMsg)

    @Delete
    fun deleteInTx(msgs: List<SmsMsg>)
}

@Dao
interface AppInfoDao {
    @Query("SELECT * FROM app_info")
    fun getAll(): List<AppInfo>

    @Query("SELECT * FROM app_info")
    fun getAllFlow(): Flow<List<AppInfo>>

    @Query("SELECT * FROM app_info WHERE blocked = 1")
    fun getBlockedApps(): List<AppInfo>

    @Query("SELECT * FROM app_info WHERE package_name = :packageName")
    fun getByPackageName(packageName: String): AppInfo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(appInfo: AppInfo)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(appInfos: List<AppInfo>)

    @Update
    fun update(appInfo: AppInfo)

    @Delete
    fun delete(appInfo: AppInfo)

    @Delete
    fun deleteInTx(appInfos: List<AppInfo>)

    @Query("DELETE FROM app_info")
    fun clearAll()
}
