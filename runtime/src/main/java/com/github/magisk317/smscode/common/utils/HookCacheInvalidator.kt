package com.github.magisk317.smscode.common.utils

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import com.github.magisk317.smscode.data.db.DBProvider
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Registers ContentObservers in a hooked process so module-app writes can invalidate
 * the in-process prefs / rules caches without waiting out their TTLs.
 *
 * Writers signal via [DBProvider.notifyPrefsCacheChanged] /
 * [DBProvider.notifyRulesCacheChanged] (ContentResolver.notifyChange on dedicated
 * signal URIs). Observers run on the main looper; invalidation is cheap and thread-safe.
 */
object HookCacheInvalidator {
    private val registered = AtomicBoolean(false)

    @Volatile
    private var prefsObserver: ContentObserver? = null

    @Volatile
    private var rulesObserver: ContentObserver? = null

    /**
     * Idempotent: first successful registration wins. Safe to call from every
     * hook-process init path (SmsHandlerHook plugin context creation, etc.).
     */
    fun register(moduleContext: Context) {
        if (registered.get()) return
        synchronized(this) {
            if (registered.get()) return
            val appContext = moduleContext.applicationContext ?: moduleContext
            val handler = Handler(Looper.getMainLooper())
            val prefsObs = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    PrefsReader.invalidateCache()
                    XLog.d("HookCacheInvalidator: prefs cache cleared")
                }
            }
            val rulesObs = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    SmsCodeUtils.invalidateRuleCache()
                    SmsCodeUtils.invalidateOfficialRuleCache()
                    XLog.d("HookCacheInvalidator: rules caches cleared")
                }
            }
            runCatching {
                appContext.contentResolver.registerContentObserver(
                    DBProvider.prefsCacheContentUri(appContext),
                    false,
                    prefsObs,
                )
                appContext.contentResolver.registerContentObserver(
                    DBProvider.rulesCacheContentUri(appContext),
                    false,
                    rulesObs,
                )
                prefsObserver = prefsObs
                rulesObserver = rulesObs
                registered.set(true)
                XLog.i("HookCacheInvalidator: observers registered")
            }.onFailure { error ->
                // Unregister partial registration if any.
                runCatching { appContext.contentResolver.unregisterContentObserver(prefsObs) }
                runCatching { appContext.contentResolver.unregisterContentObserver(rulesObs) }
                XLog.w(
                    "HookCacheInvalidator: register failed: %s",
                    error.message ?: error.javaClass.simpleName,
                )
            }
        }
    }
}
