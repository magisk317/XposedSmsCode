package com.github.magisk317.smscode.xp

import android.content.SharedPreferences
import com.github.magisk317.smscode.common.utils.PrefsReader
import io.github.magisk317.smscode.xposed.prefs.CorePrefs
import io.github.magisk317.smscode.xposed.prefs.CorePrefsAccess

object CorePrefsBridge {
    fun installRemote(remotePrefsProvider: () -> SharedPreferences?) {
        PrefsReader.setRemotePrefsProvider(remotePrefsProvider)
        CorePrefs.install(buildAccess())
    }

    private fun buildAccess(): CorePrefsAccess {
        return object : CorePrefsAccess {
            override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
                return PrefsReader.getBooleanPreference(key, defaultValue)
            }

            override fun getString(key: String, defaultValue: String): String {
                return PrefsReader.getStringPreference(key, defaultValue)
            }

            override fun getInt(key: String, defaultValue: Int): Int {
                return PrefsReader.getIntPreference(key, defaultValue)
            }
        }
    }
}
