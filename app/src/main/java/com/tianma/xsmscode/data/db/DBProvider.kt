package com.tianma.xsmscode.data.db

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.github.tianma8023.xposed.smscode.BuildConfig

class DBProvider : ContentProvider() {
    private var mDatabase: SQLiteDatabase? = null

    override fun onCreate(): Boolean {
        context?.let {
            @Suppress("DEPRECATION")
            mDatabase = DBManager.get(it).getSQLiteDatabase()
        }
        return true
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val uriType = sUriMatcher.match(uri)
        val id: Long
        val path: String
        when (uriType) {
            SMS_MSG_DIR -> {
                id = mDatabase!!.insert(TABLE_SMS_MSG, null, values)
                path = "$PATH_SMS_MSG/$id"
            }
            else -> throw IllegalArgumentException("Unsupported URI: $uri")
        }
        context?.contentResolver?.notifyChange(uri, null)
        return Uri.parse(path)
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val uriType = sUriMatcher.match(uri)
        val tableName: String = when (uriType) {
            SMS_CODE_RULE_DIR -> TABLE_SMS_CODE_RULE
            SMS_MSG_DIR -> TABLE_SMS_MSG
            APP_INFO_DIR -> TABLE_APP_INFO
            else -> throw IllegalArgumentException("Unsupported URI: $uri")
        }
        return mDatabase!!.query(
            tableName,
            projection,
            selection,
            selectionArgs,
            null,
            null,
            sortOrder
        )
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        val uriType = sUriMatcher.match(uri)
        val rowsDeleted: Int = when (uriType) {
            SMS_MSG_DIR -> mDatabase!!.delete(TABLE_SMS_MSG, selection, selectionArgs)
            else -> throw IllegalArgumentException("Unsupported URI: $uri")
        }
        if (rowsDeleted > 0) {
            context?.contentResolver?.notifyChange(uri, null)
        }
        return rowsDeleted
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        return 0
    }

    companion object {
        const val AUTHORITY = BuildConfig.APPLICATION_ID + ".db.provider"
        private const val PATH_SMS_MSG = "sms_msg"
        private const val PATH_SMS_CODE_RULE = "sms_code_rule"
        private const val PATH_APP_INFO = "app_info"
        
        @JvmField
        val SMS_MSG_CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_SMS_MSG")
        @JvmField
        val SMS_CODE_RULE_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_SMS_CODE_RULE")
        @JvmField
        val APP_INFO_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_APP_INFO")
        
        private const val SMS_MSG_DIR = 0
        private const val SMS_MSG_ID = 1
        private const val SMS_CODE_RULE_DIR = 2
        private const val SMS_CODE_RULE_ID = 3
        private const val APP_INFO_DIR = 4
        private const val APP_INFO_ID = 5
        private const val TABLE_SMS_MSG = "sms_msg"
        private const val TABLE_SMS_CODE_RULE = "sms_code_rule"
        private const val TABLE_APP_INFO = "app_info"
        private val sUriMatcher: UriMatcher = UriMatcher(UriMatcher.NO_MATCH)

        init {
            sUriMatcher.addURI(AUTHORITY, PATH_SMS_MSG, SMS_MSG_DIR)
            sUriMatcher.addURI(AUTHORITY, "$PATH_SMS_MSG/#", SMS_MSG_ID)
            sUriMatcher.addURI(AUTHORITY, PATH_SMS_CODE_RULE, SMS_CODE_RULE_DIR)
            sUriMatcher.addURI(AUTHORITY, "$PATH_SMS_CODE_RULE/#", SMS_CODE_RULE_ID)
            sUriMatcher.addURI(AUTHORITY, PATH_APP_INFO, APP_INFO_DIR)
            sUriMatcher.addURI(AUTHORITY, "$PATH_APP_INFO/#", APP_INFO_ID)
        }
    }
}
