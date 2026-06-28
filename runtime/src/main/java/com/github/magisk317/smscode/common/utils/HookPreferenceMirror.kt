package com.github.magisk317.smscode.common.utils

import android.content.Context

object HookPreferenceMirror {
    suspend fun publish(context: Context) {
        AppPreferencesDataStore.syncToSharedPrefs(context)
    }
}
