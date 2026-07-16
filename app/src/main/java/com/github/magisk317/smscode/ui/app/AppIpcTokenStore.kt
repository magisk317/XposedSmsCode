package com.github.magisk317.smscode.ui.app

import io.github.magisk317.smscode.runtime.contract.ipc.IpcTokenMatcher
import java.util.concurrent.atomic.AtomicReference

object AppIpcTokenStore {
    private val token = AtomicReference("")

    fun current(): String = token.get()

    fun matches(received: String): Boolean {
        return IpcTokenMatcher.matches(current(), received)
    }

    internal suspend fun ensurePublished(
        existingToken: suspend () -> String,
        generateToken: () -> String,
        persistToken: suspend (String) -> Unit,
    ): String {
        val existing = existingToken()
        val resolved = if (existing.isBlank()) {
            generateToken().also { generated ->
                require(generated.isNotBlank()) { "Generated IPC token must not be blank" }
                persistToken(generated)
            }
        } else {
            existing
        }
        token.set(resolved)
        return resolved
    }
}
