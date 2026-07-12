package com.github.magisk317.smscode.common.utils

/**
 * Tiny thread-safe single-value cache with a time-to-live.
 *
 * Motivation: the SmsCode hook runs inside a system process (e.g. com.android.phone)
 * and reads rules/prefs from the module app process over Binder for every incoming
 * SMS. When the module process is frozen those IPC calls block for tens of seconds.
 * Caching the last successful result for a short window lets the hook keep parsing
 * with the previous configuration instead of paying (and blocking on) an IPC per SMS.
 *
 * Design notes:
 * - A single [@Volatile] [Entry] holder gives atomic visibility of value+timestamp,
 *   so the lock-free fast path never observes a value with a stale timestamp.
 * - The refresh is guarded by a lock so only one thread loads on a miss (the hook
 *   process parses on several binder threads concurrently).
 * - If the [loader] fails or returns null while a previous value is still held, the
 *   stale value is served rather than propagating the failure. This is the graceful
 *   degradation the freeze scenario needs.
 * - The [clock] is injectable purely so behaviour can be unit-tested without Android;
 *   production uses the monotonic [android.os.SystemClock.elapsedRealtime].
 */
internal class TtlValueCache<T : Any>(
    private val ttlMillis: Long,
    private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() },
) {
    private class Entry<T>(val value: T, val at: Long)

    @Volatile
    private var entry: Entry<T>? = null
    private val lock = Any()

    /**
     * Returns the cached value if it is still within [ttlMillis]; otherwise invokes
     * [loader] once (under the lock) to refresh it. Returns null only when there is
     * no cached value and [loader] yields null or throws.
     */
    fun get(loader: () -> T?): T? {
        val now = clock()
        val fast = entry
        if (fast != null && now - fast.at < ttlMillis) {
            return fast.value
        }
        synchronized(lock) {
            val now2 = clock()
            val current = entry
            if (current != null && now2 - current.at < ttlMillis) {
                return current.value
            }
            val fresh = try {
                loader()
            } catch (_: Throwable) {
                null
            }
            if (fresh != null) {
                entry = Entry(fresh, now2)
                return fresh
            }
            // Loader failed/empty: keep serving the last good value during freeze.
            return current?.value
        }
    }

    /** Drops the cached value so the next [get] reloads. Used on config changes. */
    fun invalidate() {
        synchronized(lock) {
            entry = null
        }
    }
}
