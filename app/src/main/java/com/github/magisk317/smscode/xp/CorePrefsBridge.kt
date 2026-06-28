package com.github.magisk317.smscode.xp

import android.content.SharedPreferences
import io.github.magisk317.smscode.xposed.prefs.CorePrefs
import io.github.magisk317.smscode.xposed.prefs.CorePrefsAccess

object CorePrefsBridge {
    fun installRemote(remotePrefsProvider: () -> SharedPreferences?) {
        CorePrefs.install(buildAccess(remotePrefsProvider = remotePrefsProvider))
    }

    private fun buildAccess(
        remotePrefsProvider: (() -> SharedPreferences?)?,
    ): CorePrefsAccess {
        return object : CorePrefsAccess {
            override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
                remotePrefsProvider?.invoke()?.let { prefs ->
                    if (prefs.contains(key)) {
                        return prefs.getBoolean(key, defaultValue)
                    }
                }
                return defaultValue
            }

            override fun getString(key: String, defaultValue: String): String {
                remotePrefsProvider?.invoke()?.let { prefs ->
                    if (prefs.contains(key)) {
                        return prefs.getString(key, defaultValue) ?: defaultValue
                    }
                }
                return defaultValue
            }

            override fun getInt(key: String, defaultValue: Int): Int {
                remotePrefsProvider?.invoke()?.let { prefs ->
                    if (prefs.contains(key)) {
                        return when (val value = prefs.all[key]) {
                            is Int -> value
                            is Long -> value.toInt()
                            is String -> value.toIntOrNull() ?: defaultValue
                            else -> prefs.getInt(key, defaultValue)
                        }
                    }
                }
                return defaultValue
            }
        }
    }
}
