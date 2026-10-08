package io.github.magisk317.smscode.common.utils

import android.content.Context
import android.net.Uri
import android.os.Bundle
import io.github.magisk317.smscode.common.constant.PrefConst
import io.github.magisk317.smscode.runtime.contract.ipc.IpcTokenMatcher
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * IPC-token authorization channel for the module ContentProvider.
 *
 * Hook code runs inside the hooked app process and therefore presents that app's
 * uid; the package allowlist in [ProviderCallerGuard] cannot express "this process
 * executes module hook code". The provider additionally accepts a per-install IPC
 * token issued by the module app (the same secret the kill-self and notification
 * broadcasts already use). Hook code obtains it through the bridge prefs access
 * (HookPrefsReader.getIpcToken) and attaches it to every provider request.
 *
 * Channels:
 *  - uri query parameter [PARAM_IPC_TOKEN] for insert/query/update/delete
 *  - Bundle extra [EXTRA_IPC_TOKEN] for ContentProvider.call
 */
object ProviderIpcTokenGate {
    const val PARAM_IPC_TOKEN = "ipc_token"
    const val EXTRA_IPC_TOKEN = "ipc_token"

    fun presentedToken(uri: Uri?): String? =
        uri?.getQueryParameter(PARAM_IPC_TOKEN)?.takeIf { it.isNotBlank() }

    fun presentedToken(extras: Bundle?): String? =
        extras?.getString(EXTRA_IPC_TOKEN)?.takeIf { it.isNotBlank() }

    /**
     * Pure decision for the provider gate: allow when the uid/package allowlist
     * already accepts the caller, or when the presented token matches the expected
     * one (constant-time comparison, blank tokens never match, oversized tokens are
     * rejected by [IpcTokenMatcher]).
     */
    fun evaluate(callerAllowed: Boolean, expectedToken: String?, presentedToken: String?): Boolean =
        callerAllowed || IpcTokenMatcher.matches(expectedToken, presentedToken)

    /**
     * Resolves the expected token from the module app preferences - the same source
     * KillSelfControlReceiver reads - or null when unreadable/absent.
     */
    internal fun readExpectedToken(context: Context): String? = runCatching {
        runBlocking(Dispatchers.IO) {
            AppPreferencesDataStore.getString(
                context.applicationContext ?: context,
                PrefConst.KEY_IPC_TOKEN,
                "",
            )
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
