package com.github.magisk317.smscode.common.utils

import android.content.Context
import com.github.magisk317.smscode.data.db.DBProvider

/**
 * Mirrors DataStore-backed settings into the SharedPreferences / remote-prefs surface
 * that the hook process reads, then signals the hook process to drop its in-process
 * prefs cache so the next SMS parse reloads fresh values.
 */
object HookPreferenceMirror {
    suspend fun publish(context: Context) {
        AppPreferencesDataStore.syncToSharedPrefs(context)
        // Clear local (app-process) prefs cache too — PrefsReader may be used outside hook.
        PrefsReader.invalidateCache()
        DBProvider.notifyPrefsCacheChanged(context)
    }
}
