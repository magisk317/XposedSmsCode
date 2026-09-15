package com.github.magisk317.smscode.ui.app

import android.content.Context
import android.net.Uri
import com.github.magisk317.smscode.data.db.DBProvider
import com.github.magisk317.smscode.runtime.RuntimeCodeRecordRestoreFacade
import com.github.magisk317.smscode.runtime.RuntimeNotificationFacade
import com.github.magisk317.smscode.common.utils.HookPrefsReader
import com.github.magisk317.smscode.runtime.RuntimeStorageFacade
import com.github.magisk317.smscode.runtime.bridge.HookContentProviderAccess
import com.github.magisk317.smscode.runtime.bridge.HookRuntimeGateClaimResult
import com.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge

internal object AppShellRuntimeBridge {
    fun install(context: Context) {
        HookRuntimeBridge.install(
            prefs = HookPrefsReader,
            notification = RuntimeNotificationFacade,
            storage = RuntimeStorageFacade,
            codeRecord = RuntimeCodeRecordRestoreFacade,
            contentProvider = object : HookContentProviderAccess {
                override fun smsMsgContentUri(context: Context): Uri = DBProvider.smsMsgContentUri(context)

                override fun appInfoContentUri(context: Context): Uri = DBProvider.appInfoContentUri(context)

                override fun autoInputEventContentUri(context: Context): Uri =
                    DBProvider.autoInputEventContentUri(context)

                override fun authority(context: Context): String = DBProvider.authority(context)

                override fun claimRuntimeGate(
                    context: Context,
                    fileName: String,
                    keys: Collection<String>,
                    windowMs: Long,
                    maxEntries: Int,
                ): HookRuntimeGateClaimResult = DBProvider.claimRuntimeGate(
                    context = context,
                    fileName = fileName,
                    keys = keys,
                    windowMs = windowMs,
                    maxEntries = maxEntries,
                )

                override fun recordHookHeartbeat(
                    context: Context,
                    packageName: String,
                    processName: String,
                    source: String,
                    verboseLogging: Boolean,
                    route: String,
                ): Boolean = DBProvider.recordHookHeartbeat(
                    context = context,
                    packageName = packageName,
                    processName = processName,
                    source = source,
                    verboseLogging = verboseLogging,
                    route = route,
                )
            },
        )
        HookRuntimeBridge.hookProcessInit = null
    }
}
