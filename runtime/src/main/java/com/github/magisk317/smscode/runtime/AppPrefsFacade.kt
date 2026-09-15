package com.github.magisk317.smscode.runtime

import android.content.Context
import com.github.magisk317.smscode.common.constant.PrefConst
import com.github.magisk317.smscode.common.utils.AppPreferencesDataStore
import com.github.magisk317.smscode.runtime.bridge.UiPrefsAccess

/** App-process preference access. It never reads the hook's remote Provider. */
object AppPrefsFacade : UiPrefsAccess {
    override suspend fun isSensitiveDebugLogMode(context: Context): Boolean = getBoolean(
        context,
        PrefConst.KEY_SENSITIVE_DEBUG_LOG_MODE,
        false,
    )

    override suspend fun getSimSlotRemark(context: Context, simSlot: Int): String {
        val key = when (simSlot) {
            0 -> PrefConst.KEY_SIM_SLOT1_REMARK
            1 -> PrefConst.KEY_SIM_SLOT2_REMARK
            else -> return ""
        }
        return getString(context, key, "").trim()
    }

    suspend fun showCodeNotification(context: Context): Boolean = getBoolean(
        context,
        PrefConst.KEY_SHOW_CODE_NOTIFICATION,
        true,
    )

    suspend fun autoCancelCodeNotification(context: Context): Boolean = getBoolean(
        context,
        PrefConst.KEY_AUTO_CANCEL_CODE_NOTIFICATION,
        false,
    )

    suspend fun getNotificationRetentionTime(context: Context): Int =
        getString(context, PrefConst.KEY_NOTIFICATION_RETENTION_TIME, PrefConst.NOTIFICATION_RETENTION_TIME_DEFAULT)
            .toIntOrNull()
            ?: 0

    suspend fun autoInputCodeEnabled(context: Context): Boolean = getBoolean(
        context,
        PrefConst.KEY_ENABLE_AUTO_INPUT_CODE,
        true,
    )

    suspend fun getAutoInputCodeDelay(context: Context): Long {
        val value = getString(context, PrefConst.KEY_AUTO_INPUT_CODE_DELAY, "")
        value.toLongOrNull()?.let { return it.coerceAtLeast(0L) }
        return getString(
            context,
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY_LEGACY,
            PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT,
        ).toLongOrNull()?.coerceAtLeast(0L)?.times(1000L)
            ?: PrefConst.KEY_AUTO_INPUT_CODE_DELAY_DEFAULT.toLong()
    }

    suspend fun getAutoInputCodeIntervalMs(context: Context): Long =
        getString(context, PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL, PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL_DEFAULT)
            .toLongOrNull()
            ?.coerceAtLeast(0L)
            ?: PrefConst.KEY_AUTO_INPUT_CODE_INTERVAL_DEFAULT.toLong()

    suspend fun copyToClipboardEnabled(context: Context): Boolean = getBoolean(
        context,
        PrefConst.KEY_COPY_TO_CLIPBOARD,
        false,
    )

    suspend fun shouldShowToast(context: Context): Boolean = getBoolean(
        context,
        PrefConst.KEY_SHOW_TOAST,
        true,
    )

    suspend fun recordCodeSmsEnabled(context: Context): Boolean = getBoolean(
        context,
        PrefConst.KEY_ENABLE_CODE_RECORDS_CODE,
        true,
    )

    suspend fun blockSmsEnabled(context: Context): Boolean = getBoolean(
        context,
        PrefConst.KEY_BLOCK_SMS,
        false,
    )

    suspend fun markAsReadEnabled(context: Context): Boolean = getBoolean(
        context,
        PrefConst.KEY_MARK_AS_READ,
        false,
    )

    suspend fun deleteSmsEnabled(context: Context): Boolean = getBoolean(
        context,
        PrefConst.KEY_DELETE_SMS,
        false,
    )

    suspend fun deduplicateSms(context: Context): Boolean = getBoolean(
        context,
        PrefConst.KEY_DEDUPLICATE_SMS,
        true,
    )

    suspend fun getSmsCodeKeywords(context: Context): String = getString(
        context,
        PrefConst.KEY_SMSCODE_KEYWORDS,
        PrefConst.SMSCODE_KEYWORDS_DEFAULT,
    )

    private suspend fun getBoolean(context: Context, key: String, defaultValue: Boolean): Boolean =
        AppPreferencesDataStore.getBoolean(context, key, defaultValue)

    private suspend fun getString(context: Context, key: String, defaultValue: String): String =
        AppPreferencesDataStore.getString(context, key, defaultValue)
}
