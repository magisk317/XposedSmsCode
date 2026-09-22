package com.github.magisk317.smscode.data.prefs

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import androidx.core.net.toUri
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import com.github.magisk317.smscode.common.utils.ProviderCallerGuard
import io.github.magisk317.smscode.xposed.utils.XLog
import kotlinx.coroutines.runBlocking

class PrefsProvider : ContentProvider() {
    private lateinit var uriMatcher: UriMatcher
    private lateinit var authority: String

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        authority = "${ctx.packageName}.pref.provider"
        uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(authority, PATH_BOOL, TYPE_BOOL)
            addURI(authority, PATH_STRING, TYPE_STRING)
            addURI(authority, PATH_INT, TYPE_INT)
        }
        return true
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? {
        val ctx = context ?: return null
        if (!isCallerAllowed(ctx)) return null
        val type = uriMatcher.match(uri)
        val key = uri.getQueryParameter("key") ?: return null
        val defaultValue = uri.getQueryParameter("default")
        val cursor = MatrixCursor(arrayOf(COLUMN_VALUE))

        when (type) {
            TYPE_BOOL -> {
                val def = defaultValue?.toBooleanStrictOrNull() ?: false
                val value = runBlocking { AppPreferencesDataStore.getBoolean(ctx, key, def) }
                cursor.addRow(arrayOf(if (value) "1" else "0"))
            }

            TYPE_STRING -> {
                val def = defaultValue ?: ""
                val value = runBlocking { AppPreferencesDataStore.getString(ctx, key, def) }
                cursor.addRow(arrayOf(value))
            }

            TYPE_INT -> {
                val def = defaultValue?.toIntOrNull() ?: 0
                val value = runBlocking { AppPreferencesDataStore.getInt(ctx, key, def) }
                cursor.addRow(arrayOf(value.toString()))
            }

            else -> return null
        }
        return cursor
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        if (!isCallerAllowed(ctx)) return null
        return super.call(method, arg, extras)
    }

    private fun isCallerAllowed(ctx: Context): Boolean {
        return ProviderCallerGuard.isCallerAllowed(ctx).also { allowed ->
            if (!allowed) {
                XLog.w("PrefsProvider: deny caller uid=%d", Binder.getCallingUid())
            }
        }
    }

    companion object {
        private const val PATH_BOOL = "bool"
        private const val PATH_STRING = "string"
        private const val PATH_INT = "int"
        private const val TYPE_BOOL = 1
        private const val TYPE_STRING = 2
        private const val TYPE_INT = 3
        private const val COLUMN_VALUE = "value"

        fun authority(context: Context): String = "${context.packageName}.pref.provider"

        fun buildBoolUri(context: Context): Uri =
            "content://${context.packageName}.pref.provider/$PATH_BOOL".toUri()

        fun buildStringUri(context: Context): Uri =
            "content://${context.packageName}.pref.provider/$PATH_STRING".toUri()

        fun buildIntUri(context: Context): Uri =
            "content://${context.packageName}.pref.provider/$PATH_INT".toUri()
    }
}
