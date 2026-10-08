package io.github.magisk317.smscode.runtime

import android.content.Context
import io.github.magisk317.smscode.data.db.AppDatabase
import io.github.magisk317.smscode.data.db.DBManager
import io.github.magisk317.smscode.runtime.bridge.HookStorageAccess
import io.github.magisk317.smscode.runtime.bridge.UiStorageAccess
import io.github.magisk317.smscode.data.repository.RoomSmsCodeRuleRepository

object RuntimeStorageFacade : HookStorageAccess, UiStorageAccess {
    override fun appDatabase(context: Context): AppDatabase = AppDatabase.getInstance(context)

    override fun dbManager(context: Context): DBManager = DBManager.get(context)

    override fun smsCodeRuleRepository(context: Context): RoomSmsCodeRuleRepository =
        RoomSmsCodeRuleRepository(dbManager(context))
}
