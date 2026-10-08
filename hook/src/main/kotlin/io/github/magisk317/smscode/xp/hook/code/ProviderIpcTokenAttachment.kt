package io.github.magisk317.smscode.xp.hook.code

import android.content.Context
import android.net.Uri
import io.github.magisk317.smscode.common.utils.ProviderIpcTokenGate
import io.github.magisk317.smscode.runtime.bridge.HookRuntimeBridge

/**
 * Attaches the module IPC token to a provider request uri so the DBProvider can
 * authenticate this hook process (the caller uid is the hooked app, not the
 * module). When the token is unavailable the original uri is returned; the
 * provider then rejects the write and the record falls back to the file exporter.
 */
internal fun Uri.withProviderIpcToken(context: Context): Uri {
    val token = HookRuntimeBridge.prefsAccess.getIpcToken(context)
    if (token.isBlank()) return this
    return buildUpon().appendQueryParameter(ProviderIpcTokenGate.PARAM_IPC_TOKEN, token).build()
}
