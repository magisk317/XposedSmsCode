package com.github.magisk317.smscode.runtime.bridge

import android.content.Context
import android.net.Uri
import com.github.magisk317.smscode.data.db.DBManager
import com.github.magisk317.smscode.data.db.entity.SmsMsg

/**
 * Bridge interfaces for hook-layer access to runtime services.
 *
 * These interfaces decouple the Xposed hook code from the concrete Facade
 * objects, enabling extraction into an independent :hook module.
 *
 * The Facade objects implement these interfaces during the transition period;
 * once all callers migrate, DI-provided implementations replace them.
 */

/**
 * Read-only access to user preferences needed by hook logic.
 */
interface HookPrefsAccess {
    fun isEnabled(context: Context): Boolean
    fun isVerboseLogMode(context: Context): Boolean
    fun isSensitiveDebugLogMode(context: Context): Boolean
    fun autoInputCodeEnabled(context: Context): Boolean
    fun getAutoInputCodeDelay(context: Context): Long
    fun shouldShowToast(context: Context): Boolean
    fun markAsReadEnabled(context: Context): Boolean
    fun deleteSmsEnabled(context: Context): Boolean
    fun copyToClipboardEnabled(context: Context): Boolean
    fun recordCodeSmsEnabled(context: Context): Boolean
    fun recordAppNotifyEnabled(context: Context): Boolean
    fun recordCallNotifyEnabled(context: Context): Boolean
    fun blockSmsEnabled(context: Context): Boolean
    fun killMeEnabled(context: Context): Boolean
    fun showCodeNotification(context: Context): Boolean
    fun autoCancelCodeNotification(context: Context): Boolean
    fun getNotificationRetentionTime(context: Context): Int
    fun deduplicateSms(context: Context): Boolean
    fun getHistoryLimit(context: Context): Int
    fun getHistoryLimit(context: Context, msgType: Int, isCodeSms: Boolean): Int
    fun getIpcToken(context: Context): String
}

/**
 * Notification channel management needed by hook code.
 */
interface HookNotificationAccess {
    fun createNotificationChannel(
        context: Context,
        channelId: String,
        channelName: String,
        importance: Int,
    )
}

/**
 * Database access for hook code (query deduplication, insert records).
 */
interface HookStorageAccess {
    fun dbManager(context: Context): DBManager
}

/**
 * Code record export for cross-process file-based IPC.
 */
interface HookCodeRecordAccess {
    fun exportToFile(context: Context, smsMsg: SmsMsg): Boolean
}

/**
 * ContentProvider URI resolution for hook-side DB operations.
 */
interface HookContentProviderAccess {
    fun smsMsgContentUri(context: Context): Uri
    fun appInfoContentUri(context: Context): Uri
    fun autoInputEventContentUri(context: Context): Uri
    fun authority(context: Context): String
}

/**
 * Singleton holder that hook code uses to access runtime services.
 * Installed once during module initialization.
 */
object HookRuntimeBridge {
    @Volatile var prefs: HookPrefsAccess? = null
    @Volatile var notification: HookNotificationAccess? = null
    @Volatile var storage: HookStorageAccess? = null
    @Volatile var codeRecord: HookCodeRecordAccess? = null
    @Volatile var contentProvider: HookContentProviderAccess? = null
    @Volatile var hookProcessInit: ((android.content.Context) -> Unit)? = null

    /** Non-null accessor — call only after [install]. */
    val prefsAccess: HookPrefsAccess get() = prefs ?: error("HookRuntimeBridge.prefs not installed")
    val notificationAccess: HookNotificationAccess get() = notification ?: error("HookRuntimeBridge.notification not installed")
    val storageAccess: HookStorageAccess get() = storage ?: error("HookRuntimeBridge.storage not installed")
    val codeRecordAccess: HookCodeRecordAccess get() = codeRecord ?: error("HookRuntimeBridge.codeRecord not installed")
    val contentProviderAccess: HookContentProviderAccess get() = contentProvider ?: error("HookRuntimeBridge.contentProvider not installed")

    fun install(
        prefs: HookPrefsAccess,
        notification: HookNotificationAccess,
        storage: HookStorageAccess,
        codeRecord: HookCodeRecordAccess,
        contentProvider: HookContentProviderAccess,
    ) {
        this.prefs = prefs
        this.notification = notification
        this.storage = storage
        this.codeRecord = codeRecord
        this.contentProvider = contentProvider
    }
}
