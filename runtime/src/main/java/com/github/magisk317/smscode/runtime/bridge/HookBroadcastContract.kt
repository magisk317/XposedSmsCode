package com.github.magisk317.smscode.runtime.bridge

/**
 * Broadcast action/extra constants shared between hook code and app receivers.
 * Extracted here so the :hook module doesn't need to depend on :app.
 */
object HookBroadcastContract {
    const val ACTION_KILL_SELF = "com.github.magisk317.smscode.ACTION_KILL_SELF"
    const val EXTRA_DELAY_MS = "delay_ms"
    const val EXTRA_IPC_TOKEN = "ipc_token"

    /** Fully-qualified receiver class name for kill-self broadcast. */
    const val KILL_SELF_RECEIVER_CLASS = "com.github.magisk317.smscode.receiver.KillSelfControlReceiver"

    /** Fully-qualified receiver class name for code notification actions. */
    const val CODE_NOTIFICATION_RECEIVER_CLASS = "com.github.magisk317.smscode.receiver.CodeNotificationReceiver"
}
