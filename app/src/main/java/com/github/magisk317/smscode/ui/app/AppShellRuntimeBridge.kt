package com.github.magisk317.smscode.ui.app

import android.content.Context
import android.net.Uri
import com.github.magisk317.smscode.common.utils.PrefsReader
import com.github.magisk317.smscode.data.db.DBProvider
import com.github.magisk317.smscode.runtime.RuntimeCodeRecordRestoreFacade
import com.github.magisk317.smscode.runtime.RuntimeNotificationFacade
import com.github.magisk317.smscode.runtime.RuntimePrefsFacade
import com.github.magisk317.smscode.runtime.RuntimeStorageFacade
import com.github.magisk317.smscode.runtime.bridge.HookContentProviderAccess
import com.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge

internal object AppShellRuntimeBridge {
    fun install(context: Context) {
        PrefsReader.setHookContext(context.applicationContext ?: context)
        PrefsReader.invalidateCache()
        HookRuntimeBridge.install(
            prefs = RuntimePrefsFacade,
            notification = RuntimeNotificationFacade,
            storage = RuntimeStorageFacade,
            codeRecord = RuntimeCodeRecordRestoreFacade,
            contentProvider = object : HookContentProviderAccess {
                override fun smsMsgContentUri(context: Context): Uri = DBProvider.smsMsgContentUri(context)

                override fun appInfoContentUri(context: Context): Uri = DBProvider.appInfoContentUri(context)

                override fun autoInputEventContentUri(context: Context): Uri =
                    DBProvider.autoInputEventContentUri(context)

                override fun authority(context: Context): String = DBProvider.authority(context)
            },
        )
        HookRuntimeBridge.hookProcessInit = null
    }
}
