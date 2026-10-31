package io.github.magisk317.smscode.ui.app

import android.content.Context
import android.net.Uri
import io.github.magisk317.smscode.data.db.DBProvider
import io.github.magisk317.smscode.runtime.RuntimeCodeRecordRestoreFacade
import io.github.magisk317.smscode.runtime.RuntimeNotificationFacade
import io.github.magisk317.smscode.common.utils.HookPrefsReader
import io.github.magisk317.smscode.common.utils.ProviderIpcTokenGate
import io.github.magisk317.smscode.data.db.entity.SmsMsg
import io.github.magisk317.smscode.verification.SmsDuplicateCheckResult
import io.github.magisk317.smscode.verification.resolveSmsDuplicateCheck
import io.github.magisk317.smscode.runtime.RuntimeStorageFacade
import io.github.magisk317.smscode.runtime.bridge.HookContentProviderAccess
import io.github.magisk317.smscode.runtime.bridge.HookRuntimeGateClaimResult
import io.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge

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

                override fun querySmsMsgByFingerprint(
                    context: Context,
                    sender: String?,
                    body: String,
                    date: Long,
                ): SmsDuplicateCheckResult {
                    val token = HookPrefsReader.getIpcToken(context)
                    return resolveSmsDuplicateCheck(token) {
                        val uri = DBProvider.smsMsgContentUri(context)
                            .buildUpon()
                            .appendQueryParameter(ProviderIpcTokenGate.PARAM_IPC_TOKEN, token)
                            .build()
                        context.contentResolver.query(
                            uri,
                            arrayOf("_id"),
                            "msg_type = ? AND sender = ? AND body = ? AND date = ?",
                            arrayOf(SmsMsg.MSG_TYPE_SMS.toString(), sender.orEmpty(), body, date.toString()),
                            null,
                        )?.use { it.moveToFirst() }
                    }
                }

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
