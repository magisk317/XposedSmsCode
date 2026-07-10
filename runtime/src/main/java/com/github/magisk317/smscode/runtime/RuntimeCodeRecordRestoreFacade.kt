package com.github.magisk317.smscode.runtime

import android.annotation.SuppressLint
import android.content.Context
import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.common.utils.XLog
import com.github.magisk317.smscode.data.db.DBManager
import com.github.magisk317.smscode.data.db.entity.SmsMsg
import io.github.magisk317.smscode.runtime.common.utils.JsonUtils
import io.github.magisk317.smscode.runtime.common.utils.StorageUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object RuntimeCodeRecordRestoreFacade : com.github.magisk317.smscode.runtime.bridge.HookCodeRecordAccess {
    private const val RECORD_FILE_PREFIX = "CodeRecord_"

    @SuppressLint("SetWorldWritable", "SetWorldReadable")
    override fun exportToFile(context: Context, smsMsg: SmsMsg): Boolean {
        return runCatching {
            val filename = RECORD_FILE_PREFIX + smsMsg.date
            val recordFile = File(StorageUtils.getFilesDir(context), filename)
            OutputStreamWriter(FileOutputStream(recordFile), StandardCharsets.UTF_8).use { writer ->
                JsonUtils.toJson(smsMsg, writer, true)
            }
            StorageUtils.setFileWorldWritable(recordFile, 0)
            true
        }.onFailure { XLog.e("Export code record to file failed", it) }
            .getOrDefault(false)
    }

    fun importToDatabase(context: Context): Boolean {
        return runCatching {
            val recordFiles = getRecordFiles(context)
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
        }.onFailure { XLog.e("Import code records to database failed.", it) }
            .getOrDefault(false)
    }

    fun getRecordFiles(context: Context): Array<File>? {
        val filesDir = StorageUtils.getFilesDir(context)
        return filesDir.listFiles { _, name -> name.startsWith(RECORD_FILE_PREFIX) }
    }

    private fun loadFromFile(recordFile: File): SmsMsg? {
        return runCatching {
            InputStreamReader(FileInputStream(recordFile), StandardCharsets.UTF_8).use { reader ->
                JsonUtils.entityFromJson(reader, SmsMsg::class.java, true)
            }
        }.onFailure { XLog.e("Load code record from file failed", it) }
            .getOrNull()
    }
}
