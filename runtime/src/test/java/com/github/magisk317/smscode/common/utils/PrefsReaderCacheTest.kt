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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Verifies the in-process resolved-pref cache in [PrefsReader]: a hit reuses the last
 * value without touching the (remote) prefs provider, the value refreshes once the TTL
 * lapses, an explicit invalidate forces a reload, unavailable peers do not poison the
 * cache with defaults, and concurrent misses single-flight the provider.
 */
class PrefsReaderCacheTest {

    private var clock = 0L
    private lateinit var providerCalls: AtomicInteger
    private lateinit var activePrefs: AtomicReference<SharedPreferences?>

    @BeforeEach
    fun setUp() {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } answers { clock }

        providerCalls = AtomicInteger(0)
        val prefs = mockk<SharedPreferences>()
        every { prefs.contains(PrefConst.KEY_SMSCODE_KEYWORDS) } returns true
        every { prefs.getString(PrefConst.KEY_SMSCODE_KEYWORDS, any()) } returns "keyword-value"
        every { prefs.contains(PrefConst.KEY_ENABLE) } returns true
        every { prefs.getBoolean(PrefConst.KEY_ENABLE, any()) } returns true
        every { prefs.contains(PrefConst.KEY_MARK_AS_READ) } returns true
        every { prefs.getBoolean(PrefConst.KEY_MARK_AS_READ, any()) } returns true

        activePrefs = AtomicReference(prefs)

        // Each provider invocation models one Binder round-trip to the module process.
        // Mutate [activePrefs] to simulate freeze/unfreeze without clearing the cache
        // (unlike setRemotePrefsProvider, which intentionally invalidates).
        PrefsReader.setRemotePrefsProvider {
            providerCalls.incrementAndGet()
            activePrefs.get() ?: error("binder frozen")
        }
        PrefsReader.invalidateCache()
    }

    @AfterEach
    fun tearDown() {
        PrefsReader.setRemotePrefsProvider(null)
        PrefsReader.invalidateCache()
        unmockkStatic(SystemClock::class)
    }

    @Test
    fun `repeated reads within TTL hit the provider only once`() {
        val context = mockk<android.content.Context>(relaxed = true)

        val first = PrefsReader.getSMSCodeKeywords(context)
        repeat(20) { PrefsReader.getSMSCodeKeywords(context) }

        assertEquals("keyword-value", first)
        assertEquals(1, providerCalls.get())
    }

    @Test
    fun `read after TTL expiry refreshes from the provider`() {
        val context = mockk<android.content.Context>(relaxed = true)

        PrefsReader.getSMSCodeKeywords(context)
        assertEquals(1, providerCalls.get())

        clock += 4_000L
        PrefsReader.getSMSCodeKeywords(context)
        assertEquals(1, providerCalls.get())

        clock += 2_000L
        PrefsReader.getSMSCodeKeywords(context)
        assertEquals(2, providerCalls.get())
    }

    @Test
    fun `invalidateCache forces the next read to reload`() {
        val context = mockk<android.content.Context>(relaxed = true)

        PrefsReader.getSMSCodeKeywords(context)
        assertEquals(1, providerCalls.get())

        PrefsReader.invalidateCache()

        PrefsReader.getSMSCodeKeywords(context)
        assertEquals(2, providerCalls.get())
    }

    @Test
    fun `unavailable provider does not cache default as a hit`() {
        val context = mockk<android.content.Context>(relaxed = true)

        // Cold start + frozen peer: no prior value, return default, do NOT store it.
        activePrefs.set(null)
        PrefsReader.invalidateCache()
        providerCalls.set(0)

        assertEquals(false, PrefsReader.markAsReadEnabled(context))
        val afterFirst = providerCalls.get()
        assertEquals(1, afterFirst)

        // Still inside what would be a TTL window if we had cached the default:
        // correct behaviour re-attempts the provider instead of serving a poisoned false.
        clock += 1_000L
        assertEquals(false, PrefsReader.markAsReadEnabled(context))
        assertEquals(afterFirst + 1, providerCalls.get())
    }

    @Test
    fun `unavailable after hit keeps stale value`() {
        val context = mockk<android.content.Context>(relaxed = true)

        // Warm: enable=true confirmed and cached.
        assertEquals(true, PrefsReader.isEnabled(context))
        assertEquals(1, providerCalls.get())

        // Expire TTL, freeze peer — do not clear cache via setRemotePrefsProvider.
        clock += 6_000L
        activePrefs.set(null)

        // Stale true is served; default is NOT used. One probe is expected on expiry.
        assertEquals(true, PrefsReader.isEnabled(context))
        val afterStaleServe = providerCalls.get()
        assertEquals(2, afterStaleServe)

        // TTL was refreshed on the stale value: next read inside the window is a hit.
        clock += 1_000L
        assertEquals(true, PrefsReader.isEnabled(context))
        assertEquals(afterStaleServe, providerCalls.get())
    }

    @Test
    fun `confirmed miss caches the default for the TTL window`() {
        val context = mockk<android.content.Context>(relaxed = true)
        val prefs = mockk<SharedPreferences>()
        every { prefs.contains(PrefConst.KEY_MARK_AS_READ) } returns false
        activePrefs.set(prefs)
        PrefsReader.invalidateCache()
        providerCalls.set(0)

        assertEquals(false, PrefsReader.markAsReadEnabled(context))
        assertEquals(1, providerCalls.get())
        assertEquals(false, PrefsReader.markAsReadEnabled(context))
        assertEquals(1, providerCalls.get())
    }

    @Test
    fun `concurrent miss loads the provider only once`() {
        val context = mockk<android.content.Context>(relaxed = true)
        val prefs = mockk<SharedPreferences>()
        every { prefs.contains(PrefConst.KEY_SMSCODE_KEYWORDS) } returns true
        every { prefs.getString(PrefConst.KEY_SMSCODE_KEYWORDS, any()) } answers {
            Thread.sleep(30)
            "keyword-value"
        }
        activePrefs.set(prefs)
        PrefsReader.invalidateCache()
        providerCalls.set(0)

        val threads = 12
        val barrier = CyclicBarrier(threads)
        val executor = Executors.newFixedThreadPool(threads)
        val tasks = (1..threads).map {
            Callable {
                barrier.await()
                PrefsReader.getSMSCodeKeywords(context)
            }
        }
        val results = executor.invokeAll(tasks).map { it.get() }
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)

        assertEquals(List(threads) { "keyword-value" }, results)
        assertEquals(1, providerCalls.get())
    }
}
