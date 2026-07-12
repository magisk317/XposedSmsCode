package com.github.magisk317.smscode.common.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class TtlValueCacheTest {

    private fun cacheWith(ttl: Long, now: AtomicLong): TtlValueCache<String> =
        TtlValueCache(ttlMillis = ttl, clock = { now.get() })

    @Test
    fun `hit within ttl does not reload`() {
        val now = AtomicLong(0)
        val cache = cacheWith(ttl = 100, now = now)
        val loads = AtomicInteger(0)
        val loader = { loads.incrementAndGet(); "v1" }

        assertEquals("v1", cache.get(loader))
        now.set(50) // still within ttl
        assertEquals("v1", cache.get(loader))

        assertEquals(1, loads.get())
    }

    @Test
    fun `reloads after ttl expires`() {
        val now = AtomicLong(0)
        val cache = cacheWith(ttl = 100, now = now)
        val values = ArrayDeque(listOf("v1", "v2"))
        val loads = AtomicInteger(0)
        val loader = { loads.incrementAndGet(); values.removeFirst() }

        assertEquals("v1", cache.get(loader))
        now.set(100) // ttl boundary is exclusive: now - at (100) < 100 is false -> expired
        assertEquals("v2", cache.get(loader))

        assertEquals(2, loads.get())
    }

    @Test
    fun `serves stale value when loader returns null after expiry`() {
        val now = AtomicLong(0)
        val cache = cacheWith(ttl = 100, now = now)

        assertEquals("good", cache.get { "good" })
        now.set(1_000) // far past ttl -> refresh attempted
        // Loader now yields null (e.g. frozen module process): keep last good value.
        assertEquals("good", cache.get { null })
    }

    @Test
    fun `serves stale value when loader throws after expiry`() {
        val now = AtomicLong(0)
        val cache = cacheWith(ttl = 100, now = now)

        assertEquals("good", cache.get { "good" })
        now.set(1_000)
        assertEquals("good", cache.get { error("binder timeout") })
    }

    @Test
    fun `returns null when first load fails with no prior value`() {
        val now = AtomicLong(0)
        val cache = cacheWith(ttl = 100, now = now)

        assertNull(cache.get { null })
        assertNull(cache.get { error("binder timeout") })
    }

    @Test
    fun `invalidate forces reload on next get`() {
        val now = AtomicLong(0)
        val cache = cacheWith(ttl = 10_000, now = now)
        val values = ArrayDeque(listOf("v1", "v2"))
        val loads = AtomicInteger(0)
        val loader = { loads.incrementAndGet(); values.removeFirst() }

        assertEquals("v1", cache.get(loader))
        cache.invalidate()
        assertEquals("v2", cache.get(loader)) // reloaded despite ttl not elapsed

        assertEquals(2, loads.get())
    }

    @Test
    fun `concurrent miss loads exactly once under the lock`() {
        val now = AtomicLong(0)
        val cache = cacheWith(ttl = 10_000, now = now)
        val loads = AtomicInteger(0)
        val threads = 16
        val barrier = CyclicBarrier(threads)
        val loader = {
            loads.incrementAndGet()
            Thread.sleep(20) // widen the race window
            "only"
        }

        val executor = Executors.newFixedThreadPool(threads)
        val tasks = (1..threads).map {
            Callable {
                barrier.await()
                cache.get(loader)
            }
        }
        val results = executor.invokeAll(tasks).map { it.get() }
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)

        assertEquals(List(threads) { "only" }, results)
        assertEquals(1, loads.get())
    }
}
