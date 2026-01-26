package com.tianma.xsmscode.feature.migrate.db

import android.content.Context
import com.tianma.xsmscode.feature.migrate.ITransition
import com.tianma.xsmscode.ui.record.CodeRecordRestoreManager

class DBTransition(private val mContext: Context) : ITransition {

    override fun shouldTransit(): Boolean {
        val recordFiles = CodeRecordRestoreManager.getRecordFiles()
        return recordFiles != null && recordFiles.isNotEmpty()
    }

    override fun doTransition(): Boolean {
        return CodeRecordRestoreManager.importToDatabase(mContext)
    }
}
