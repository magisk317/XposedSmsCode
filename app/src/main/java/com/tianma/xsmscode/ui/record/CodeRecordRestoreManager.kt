package com.tianma.xsmscode.ui.record

import android.annotation.SuppressLint
import android.content.Context
import com.tianma.xsmscode.common.constant.PrefConst
import com.tianma.xsmscode.common.utils.JsonUtils
import com.tianma.xsmscode.common.utils.StorageUtils
import com.tianma.xsmscode.common.utils.XLog
import com.tianma.xsmscode.data.db.DBManager
import com.tianma.xsmscode.data.db.entity.SmsMsg
import java.io.*
import java.nio.charset.StandardCharsets

object CodeRecordRestoreManager {
    private const val RECORD_FILE_PREFIX = "CodeRecord_"

    @SuppressLint("SetWorldWritable", "SetWorldReadable")
    @JvmStatic
    fun exportToFile(smsMsg: SmsMsg): Boolean {
        var osw: OutputStreamWriter? = null
        return try {
            val filename = RECORD_FILE_PREFIX + smsMsg.date
            val recordFile = File(StorageUtils.getFilesDir(), filename)
            osw = OutputStreamWriter(FileOutputStream(recordFile), StandardCharsets.UTF_8)
            JsonUtils.toJson(smsMsg, osw, true)
            StorageUtils.setFileWorldWritable(recordFile, 0)
            true
        } catch (e: Exception) {
            XLog.e("Export code record to file failed", e)
            false
        } finally {
            try {
                osw?.close()
            } catch (ioException: IOException) {
                // ignore
            }
        }
    }

    @JvmStatic
    fun importToDatabase(context: Context): Boolean {
        return try {
            val recordFiles = getRecordFiles()
            val smsMsgList = mutableListOf<SmsMsg>()
            recordFiles?.forEach { recordFile ->
                val smsMsg = loadFromFile(recordFile)
                if (smsMsg != null) {
                    smsMsgList.add(smsMsg)
                    recordFile.delete()
                }
            }

            if (smsMsgList.isNotEmpty()) {
                val dbManager = DBManager.get(context)
                dbManager.addSmsMsgList(smsMsgList)
                XLog.d("Import code records to database succeed")

                val allMsgList = dbManager.queryAllSmsMsg()
                if (allMsgList.size > PrefConst.MAX_SMS_RECORDS_COUNT_DEFAULT) {
                    val outdatedMsgList = allMsgList.subList(PrefConst.MAX_SMS_RECORDS_COUNT_DEFAULT, allMsgList.size)
                    dbManager.removeSmsMsgList(outdatedMsgList)
                    XLog.d("Remove outdated code records succeed")
                }
            }
            true
        } catch (t: Throwable) {
            XLog.e("Import code records to database failed.", t)
            false
        }
    }

    @JvmStatic
    fun getRecordFiles(): Array<File>? {
        val filesDir = StorageUtils.getFilesDir()
        return filesDir.listFiles { _, name -> name.startsWith(RECORD_FILE_PREFIX) }
    }

    private fun loadFromFile(recordFile: File): SmsMsg? {
        var isr: InputStreamReader? = null
        return try {
            isr = InputStreamReader(FileInputStream(recordFile), StandardCharsets.UTF_8)
            JsonUtils.entityFromJson(isr, SmsMsg::class.java, true)
        } catch (e: FileNotFoundException) {
            XLog.e("", e)
            null
        } finally {
            try {
                isr?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}
