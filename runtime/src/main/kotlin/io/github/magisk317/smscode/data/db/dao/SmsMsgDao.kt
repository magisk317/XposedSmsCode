package io.github.magisk317.smscode.data.db.dao

import androidx.room.*
import io.github.magisk317.smscode.data.db.entity.SmsMsg
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsMsgDao {
    @Query("SELECT * FROM sms_msg ORDER BY date DESC")
    suspend fun getAll(): List<SmsMsg>

    @Query("SELECT * FROM sms_msg WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SmsMsg?

    @Query("SELECT * FROM sms_msg WHERE sender IS :sender AND body IS :body AND date = :date AND msg_type = :msgType LIMIT 1")
    suspend fun getByFingerprint(sender: String?, body: String?, date: Long, msgType: Int): SmsMsg?

    @Query("SELECT * FROM sms_msg WHERE msg_type = :msgType AND session_key = :sessionKey LIMIT 1")
    fun getBySessionKey(msgType: Int, sessionKey: String): SmsMsg?

    @Query(
        "SELECT * FROM sms_msg " +
            "WHERE sender IS :sender AND body IS :body " +
            "AND msg_type = :msgType AND date BETWEEN :dateFrom AND :dateTo LIMIT 1",
    )
    fun getByFingerprintInRange(
        sender: String?,
        body: String?,
        msgType: Int,
        dateFrom: Long,
        dateTo: Long,
    ): SmsMsg?

    @Query(
        "SELECT * FROM sms_msg " +
            "WHERE sms_code IS :smsCode AND company IS :company " +
            "AND msg_type = :msgType AND date BETWEEN :dateFrom AND :dateTo LIMIT 1",
    )
    fun getByCodeAndCompanyInRange(
        smsCode: String?,
        company: String?,
        msgType: Int,
        dateFrom: Long,
        dateTo: Long,
    ): SmsMsg?

    @Query(
        "SELECT * FROM sms_msg " +
            "WHERE sms_code IS :smsCode AND package_name IS :packageName " +
            "AND msg_type = :msgType AND date BETWEEN :dateFrom AND :dateTo LIMIT 1",
    )
    fun getByCodeAndPackageInRange(
        smsCode: String?,
        packageName: String?,
        msgType: Int,
        dateFrom: Long,
        dateTo: Long,
    ): SmsMsg?

    @Query(
        "SELECT * FROM sms_msg " +
            "WHERE sms_code IS :smsCode " +
            "AND msg_type = :msgType AND date BETWEEN :dateFrom AND :dateTo",
    )
    suspend fun getByCodeInRange(
        smsCode: String?,
        msgType: Int,
        dateFrom: Long,
        dateTo: Long,
    ): List<SmsMsg>

    @Query(
        "SELECT * FROM sms_msg " +
            "WHERE sim_slot = :simSlot AND msg_type = :msgType " +
            "AND date BETWEEN :dateFrom AND :dateTo LIMIT 1",
    )
    suspend fun getBySimSlotInRange(
        simSlot: Int,
        msgType: Int,
        dateFrom: Long,
        dateTo: Long,
    ): SmsMsg?

    @Query("SELECT * FROM sms_msg ORDER BY date DESC")
    fun getAllFlow(): Flow<List<SmsMsg>>

    @Query(
        "SELECT notify_channel_id FROM sms_msg " +
            "WHERE msg_type = :msgType " +
            "AND package_name = :packageName " +
            "AND notify_channel_id != '' " +
            "GROUP BY notify_channel_id " +
            "ORDER BY MAX(date) DESC " +
            "LIMIT :limit",
    )
    suspend fun queryRecentNotifyChannelIds(
        packageName: String,
        msgType: Int = SmsMsg.MSG_TYPE_APP_NOTIFY,
        limit: Int = 20,
    ): List<String>

    @Query(
        "SELECT notify_channel_id FROM sms_msg " +
            "WHERE msg_type = :msgType " +
            "AND package_name = :packageName " +
            "AND notify_channel_id != '' " +
            "GROUP BY notify_channel_id " +
            "ORDER BY MAX(date) DESC " +
            "LIMIT :limit",
    )
    fun observeRecentNotifyChannelIds(
        packageName: String,
        msgType: Int = SmsMsg.MSG_TYPE_APP_NOTIFY,
        limit: Int = 20,
    ): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(msg: SmsMsg): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(msg: SmsMsg): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(msgs: List<SmsMsg>)

    @Update
    suspend fun update(msg: SmsMsg)

    @Query("DELETE FROM sms_msg")
    suspend fun clearAll()

    @Query("SELECT count(*) FROM sms_msg")
    suspend fun count(): Long

    @Query("SELECT count(*) FROM sms_msg")
    fun countFlow(): Flow<Long>

    @Query("SELECT count(*) FROM sms_msg WHERE date >= :fromMs")
    suspend fun countFrom(fromMs: Long): Long

    @Query(
        "SELECT count(*) FROM sms_msg " +
            "WHERE msg_type = :msgType " +
            "AND sms_code IS NOT NULL AND sms_code != '' " +
            "AND date >= :fromMs",
    )
    suspend fun countCodeSmsFrom(fromMs: Long, msgType: Int = SmsMsg.MSG_TYPE_SMS): Long

    @Delete
    suspend fun delete(msg: SmsMsg)

    @Delete
    suspend fun deleteInTx(msgs: List<SmsMsg>)
}
