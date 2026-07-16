package com.github.magisk317.smscode.ui.app

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppIpcTokenStoreTest {
    @Test
    fun existingTokenIsPublishedWithoutPersistence() = runBlocking {
        var persisted: String? = null

        val resolved = AppIpcTokenStore.ensurePublished(
            existingToken = { "existing-token" },
            generateToken = { "unused-generated-token" },
            persistToken = { persisted = it },
        )

        assertEquals("existing-token", resolved)
        assertEquals("existing-token", AppIpcTokenStore.current())
        assertEquals(null, persisted)
        assertTrue(AppIpcTokenStore.matches("existing-token"))
        assertFalse(AppIpcTokenStore.matches("forged-token"))
    }

    @Test
    fun missingTokenIsPersistedBeforePublication() = runBlocking {
        val events = mutableListOf<String>()

        val resolved = AppIpcTokenStore.ensurePublished(
            existingToken = { "" },
            generateToken = { "generated-token" },
            persistToken = { generated -> events += "persist:$generated" },
        )
        events += "published:${AppIpcTokenStore.current()}"

        assertEquals("generated-token", resolved)
        assertEquals(
            listOf("persist:generated-token", "published:generated-token"),
            events,
        )
        assertTrue(AppIpcTokenStore.matches("generated-token"))
        assertFalse(AppIpcTokenStore.matches(""))
    }
}
