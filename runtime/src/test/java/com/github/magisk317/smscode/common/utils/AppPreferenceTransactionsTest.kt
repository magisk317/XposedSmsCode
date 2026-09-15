package com.github.magisk317.smscode.common.utils

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.magisk317.smscode.runtime.contract.prefs.AtomicPreferencePersistence
import io.github.magisk317.smscode.runtime.contract.prefs.PreferenceCommitResult
import io.github.magisk317.smscode.runtime.contract.prefs.PreferenceSpec
import io.github.magisk317.smscode.runtime.contract.prefs.preferenceChangeSet
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppPreferenceTransactionsTest {
    @Test
    fun `DataStore adapter maps puts and removes in one staged change set`() {
        val enabled = PreferenceSpec.boolean("enabled", true)
        val label = PreferenceSpec.string("label", "default")
        val stale = PreferenceSpec.int("stale", 0)
        val prefs = mutablePreferencesOf(intPreferencesKey(stale.key) to 7)

        AppPreferencesDataStore.applyPreferenceChanges(
            prefs,
            preferenceChangeSet {
                set(enabled, false)
                set(label, "updated")
                remove(stale)
            },
        )

        assertFalse(prefs[booleanPreferencesKey(enabled.key)] ?: true)
        assertEquals("updated", prefs[stringPreferencesKey(label.key)])
        assertNull(prefs[intPreferencesKey(stale.key)])
    }

    @Test
    fun `successful persistence publishes after persistence`() = runBlocking {
        val events = mutableListOf<String>()
        val coordinator = createHookPreferenceCommitCoordinator(
            persistence = AtomicPreferencePersistence {
                events += "persist"
                true
            },
            publish = { events += "publish" },
        )

        val result = coordinator.commit {
            set(HookPreferenceSpecs.autoInputEnabled, false)
        }

        assertTrue(result is PreferenceCommitResult.Persisted)
        assertEquals(listOf("persist", "publish"), events)
    }

    @Test
    fun `rejected persistence never invalidates or publishes`() = runBlocking {
        val events = mutableListOf<String>()
        val coordinator = createHookPreferenceCommitCoordinator(
            persistence = AtomicPreferencePersistence {
                events += "persist"
                false
            },
            publish = { events += "publish" },
        )

        val result = coordinator.commit {
            set(HookPreferenceSpecs.autoInputEnabled, false)
        }

        assertTrue(result is PreferenceCommitResult.NotPersisted)
        assertEquals(listOf("persist"), events)
    }
}
