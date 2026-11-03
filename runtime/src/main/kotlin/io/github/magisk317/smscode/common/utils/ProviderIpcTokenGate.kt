package io.github.magisk317.smscode.common.utils


import android.content.Context
import android.net.Uri
import android.os.Bundle
import io.github.magisk317.smscode.common.constant.PrefConst
import io.github.magisk317.xposed.runtime.ipc.IpcTokenMatcher

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
    const val PARAM_IPC_TOKEN = ProviderIpcTokenCodec.PARAM_IPC_TOKEN
    const val EXTRA_IPC_TOKEN = ProviderIpcTokenCodec.EXTRA_IPC_TOKEN

    fun attachToken(uri: Uri, token: String): Uri = ProviderIpcTokenCodec.attachToken(uri, token)

    fun presentedToken(uri: Uri?): String? = ProviderIpcTokenCodec.presentedToken(uri)

    fun presentedToken(extras: Bundle?): String? = ProviderIpcTokenCodec.presentedToken(extras)

    fun evaluate(callerAllowed: Boolean, expectedToken: String?, presentedToken: String?): Boolean =
        ProviderIpcTokenCodec.evaluate(callerAllowed, expectedToken, presentedToken)

    /**
     * Resolves the expected token from the module app preferences - the same source
     * KillSelfControlReceiver reads - or null when unreadable/absent.
     */
    internal fun readExpectedToken(context: Context): String? =
        ProviderIpcTokenCodec.readExpectedToken(context, PrefConst.KEY_IPC_TOKEN)
}
