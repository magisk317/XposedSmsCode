package io.github.magisk317.smscode.runtime

import android.content.Context
import io.github.magisk317.smscode.common.constant.PrefConst
import io.github.magisk317.smscode.common.utils.XLog
import io.github.magisk317.smscode.data.db.DBManager
import io.github.magisk317.smscode.db.entity.SmsMsg
import io.github.magisk317.smscode.runtime.common.record.CodeRecordFileStore
import java.io.File
import kotlinx.coroutines.runBlocking

/**
 * Thin host facade; export/import, telemetry, and file handling live in the
 * shared [CodeRecordFileStore] (smscode-core runtime) so both hosts stay
 * behaviorally aligned. Only the storage insert/trim closure stays host-side.
 */
object RuntimeCodeRecordRestoreFacade : io.github.magisk317.smscode.runtime.bridge.HookCodeRecordAccess {
    private val store = CodeRecordFileStore(
        recordSerializer = SmsMsg.serializer(),
        fileNameFor = { record -> "CodeRecord_" + record.date },
    )

    override fun exportToFile(context: Context, smsMsg: SmsMsg): Boolean {
        return store.exportToFile(context, smsMsg)
    }

    fun importToDatabase(context: Context): Boolean {
        return runBlocking {
            store.importToDatabase(context, insertRecords = { list ->
                val dbManager = DBManager.get(context)
                dbManager.addSmsMsgList(list)
                trimAfterImport(dbManager)
            })
        }
    }

    private fun trimAfterImport(dbManager: DBManager) {
        val allMsgList = dbManager.queryAllSmsMsg()
        if (allMsgList.size > PrefConst.MAX_SMS_RECORDS_COUNT_DEFAULT) {
            val outdatedMsgList = allMsgList.subList(PrefConst.MAX_SMS_RECORDS_COUNT_DEFAULT, allMsgList.size)
            dbManager.removeSmsMsgList(outdatedMsgList)
            XLog.d("Remove outdated code records succeed")
        }
    }

    fun getRecordFiles(context: Context): Array<File>? {
        return store.getRecordFiles(context)
    }
}
