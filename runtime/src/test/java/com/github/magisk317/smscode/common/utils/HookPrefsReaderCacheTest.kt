package com.github.magisk317.smscode.common.utils

import android.content.SharedPreferences
import android.os.SystemClock
import com.github.magisk317.smscode.common.constant.PrefConst
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Verifies that hook preferences are read only from the remote provider and never retain a stale
 * value when the provider becomes unavailable. The parent cannot observe a remote binding failure
 * without probing it, so these reads intentionally do not use the shared resolver's stale-value
 * cache.
 */
class HookPrefsReaderCacheTest {

    @BeforeEach
    fun setUpClock() {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 1_000L
    }

    private lateinit var providerCalls: AtomicInteger
    private lateinit var activePrefs: AtomicReference<SharedPreferences?>

    @BeforeEach
    fun setUp() {
        providerCalls = AtomicInteger(0)
        val prefs = mockk<SharedPreferences>()
        every { prefs.contains(PrefConst.KEY_SMSCODE_KEYWORDS) } returns true
        every { prefs.getString(PrefConst.KEY_SMSCODE_KEYWORDS, any()) } returns "keyword-value"
        every { prefs.contains(PrefConst.KEY_ENABLE) } returns true
        every { prefs.getBoolean(PrefConst.KEY_ENABLE, any()) } returns true
        every { prefs.contains(PrefConst.KEY_MARK_AS_READ) } returns true
        every { prefs.getBoolean(PrefConst.KEY_MARK_AS_READ, any()) } returns true
        every { prefs.all } returns mapOf(
            PrefConst.KEY_SMSCODE_KEYWORDS to "keyword-value",
            PrefConst.KEY_ENABLE to true,
            PrefConst.KEY_MARK_AS_READ to true,
        )

        activePrefs = AtomicReference(prefs)
        HookPrefsReader.setRemotePrefsProvider {
            providerCalls.incrementAndGet()
            activePrefs.get()
        }
        HookPrefsReader.invalidateCache()
    }

    @AfterEach
    fun tearDown() {
        HookPrefsReader.setRemotePrefsProvider(null)
        HookPrefsReader.invalidateCache()
        unmockkStatic(SystemClock::class)
    }

    @Test
    fun `each read re-resolves the remote provider`() {
        val context = mockk<android.content.Context>(relaxed = true)

        repeat(21) { HookPrefsReader.getSMSCodeKeywords(context) }

        assertEquals(21, providerCalls.get())
    }

    @Test
    fun `unavailable provider after a hit returns the default instead of stale value`() {
        val context = mockk<android.content.Context>(relaxed = true)

        assertTrue(HookPrefsReader.markAsReadEnabled(context))
        activePrefs.set(null)

        assertFalse(HookPrefsReader.markAsReadEnabled(context))
        assertEquals(2, providerCalls.get())
    }

    @Test
    fun `cold unavailable provider is retried instead of caching the default`() {
        val context = mockk<android.content.Context>(relaxed = true)
        activePrefs.set(null)

        assertFalse(HookPrefsReader.markAsReadEnabled(context))
        assertFalse(HookPrefsReader.markAsReadEnabled(context))

        assertEquals(2, providerCalls.get())
    }

    @Test
    fun `explicit false remains distinct from a missing key`() {
        val context = mockk<android.content.Context>(relaxed = true)
        val explicitFalse = mockk<SharedPreferences>()
        every { explicitFalse.contains(PrefConst.KEY_MARK_AS_READ) } returns true
        every { explicitFalse.getBoolean(PrefConst.KEY_MARK_AS_READ, any()) } returns false
        activePrefs.set(explicitFalse)

        assertFalse(HookPrefsReader.markAsReadEnabled(context))

        val missing = mockk<SharedPreferences>()
        every { missing.contains(PrefConst.KEY_MARK_AS_READ) } returns false
        activePrefs.set(missing)
        assertFalse(HookPrefsReader.markAsReadEnabled(context))
        assertEquals(2, providerCalls.get())
    }

    @Test
    fun `invalidateCache still forces a reload`() {
        val context = mockk<android.content.Context>(relaxed = true)

        HookPrefsReader.getSMSCodeKeywords(context)
        HookPrefsReader.invalidateCache()
        HookPrefsReader.getSMSCodeKeywords(context)

        assertEquals(2, providerCalls.get())
    }
}
