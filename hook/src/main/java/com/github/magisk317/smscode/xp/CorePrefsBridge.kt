package com.github.magisk317.smscode.xp

import android.content.SharedPreferences
import com.github.magisk317.smscode.common.utils.HookPrefsReader
import io.github.magisk317.smscode.xposed.prefs.CorePrefs
import io.github.magisk317.smscode.xposed.prefs.CorePrefsAccess

object CorePrefsBridge {
    fun installRemote(remotePrefsProvider: () -> SharedPreferences?) {
        HookPrefsReader.setRemotePrefsProvider(remotePrefsProvider)
        CorePrefs.install(buildAccess())
    }

    private fun buildAccess(): CorePrefsAccess {
        return object : CorePrefsAccess {
            override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
                return HookPrefsReader.getBooleanPreference(key, defaultValue)
            }

            override fun getString(key: String, defaultValue: String): String {
                return HookPrefsReader.getStringPreference(key, defaultValue)
            }

            override fun getInt(key: String, defaultValue: Int): Int {
                return HookPrefsReader.getIntPreference(key, defaultValue)
            }
        }
    }
}
