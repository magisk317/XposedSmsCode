package com.github.magisk317.smscode.runtime

import android.content.Context
import com.github.magisk317.smscode.data.db.AppDatabase
import com.github.magisk317.smscode.data.db.DBManager
import com.github.magisk317.smscode.runtime.bridge.HookStorageAccess

object RuntimeStorageFacade : HookStorageAccess {
    fun appDatabase(context: Context): AppDatabase = AppDatabase.getInstance(context)

    override fun dbManager(context: Context): DBManager = DBManager.get(context)
}
