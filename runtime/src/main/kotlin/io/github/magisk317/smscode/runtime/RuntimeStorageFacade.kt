package io.github.magisk317.smscode.runtime

import android.content.Context
import io.github.magisk317.smscode.data.db.DBManager
import io.github.magisk317.smscode.runtime.bridge.HookStorageAccess
import io.github.magisk317.smscode.runtime.bridge.UiAppConfigRepository
import io.github.magisk317.smscode.runtime.bridge.UiStorageAccess
import io.github.magisk317.smscode.data.repository.RoomAppConfigRepository
import io.github.magisk317.smscode.data.repository.RoomSmsCodeRuleRepository
import io.github.magisk317.smscode.rule.repository.SmsCodeRuleRepository
import kotlinx.coroutines.flow.Flow

object RuntimeStorageFacade : HookStorageAccess, UiStorageAccess {
    override fun dbManager(context: Context): DBManager = DBManager.get(context)

    override fun smsCodeRuleRepository(context: Context): SmsCodeRuleRepository =
        RoomSmsCodeRuleRepository(dbManager(context))

    override fun appConfigRepository(context: Context): UiAppConfigRepository =
        RoomAppConfigRepository(dbManager(context))

    override fun smsRecordCountFlow(context: Context): Flow<Long> =
        dbManager(context).queryAllSmsMsgCountFlow()
}
