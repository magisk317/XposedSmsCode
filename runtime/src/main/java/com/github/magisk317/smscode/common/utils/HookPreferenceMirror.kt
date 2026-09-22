package com.github.magisk317.smscode.common.utils

import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import android.content.Context
import com.github.magisk317.smscode.data.db.DBProvider

/**
 * Mirrors DataStore-backed settings into the remote-prefs surface that the hook
 * process reads, then signals the hook process to drop its in-process caches.
 */
object HookPreferenceMirror {
    suspend fun publish(context: Context): Boolean {
        if (AppPreferencesDataStore.syncToRemotePrefs(context) != true) {
            XLog.w("Hook preference publish failed; cache invalidation skipped")
            return false
        }
        DBProvider.notifyPrefsCacheChanged(context)
        return true
    }
}
