package com.github.magisk317.smscode.runtime

import android.content.Context
import com.github.magisk317.smscode.common.utils.PrefsReader
import com.github.magisk317.smscode.runtime.bridge.HookPrefsAccess

object RuntimePrefsFacade : HookPrefsAccess {
    override fun isEnabled(context: Context): Boolean = PrefsReader.isEnabled(context)

    override fun isVerboseLogMode(context: Context): Boolean = PrefsReader.isVerboseLogMode(context)

    override fun isSensitiveDebugLogMode(context: Context): Boolean = PrefsReader.isSensitiveDebugLogMode(context)

    override fun autoInputCodeEnabled(context: Context): Boolean = PrefsReader.autoInputCodeEnabled(context)

    fun autoEnterCodeEnabled(context: Context): Boolean = PrefsReader.autoEnterCodeEnabled(context)

    override fun getAutoInputCodeDelay(context: Context): Long = PrefsReader.getAutoInputCodeDelay(context)

    fun getAutoInputCodeIntervalMs(context: Context): Long = PrefsReader.getAutoInputCodeIntervalMs(context)

    override fun shouldShowToast(context: Context): Boolean = PrefsReader.shouldShowToast(context)

    fun getSMSCodeKeywords(context: Context): String? = PrefsReader.getSMSCodeKeywords(context)

    fun getSmsCodeKeywords(context: Context): String? = getSMSCodeKeywords(context)

    override fun markAsReadEnabled(context: Context): Boolean = PrefsReader.markAsReadEnabled(context)

    override fun deleteSmsEnabled(context: Context): Boolean = PrefsReader.deleteSmsEnabled(context)

    override fun copyToClipboardEnabled(context: Context): Boolean = PrefsReader.copyToClipboardEnabled(context)

    override fun recordCodeSmsEnabled(context: Context): Boolean = PrefsReader.recordCodeSmsEnabled(context)

    fun recordSmsCodeEnabled(context: Context): Boolean = recordCodeSmsEnabled(context)

    override fun recordAppNotifyEnabled(context: Context): Boolean = PrefsReader.recordAppNotifyEnabled(context)

    override fun recordCallNotifyEnabled(context: Context): Boolean = PrefsReader.recordCallNotifyEnabled(context)

    override fun blockSmsEnabled(context: Context): Boolean = PrefsReader.blockSmsEnabled(context)

    override fun killMeEnabled(context: Context): Boolean = PrefsReader.killMeEnabled(context)

    override fun showCodeNotification(context: Context): Boolean = PrefsReader.showCodeNotification(context)

    fun getCodeNotificationOwner(context: Context): String = PrefsReader.getCodeNotificationOwner(context)

    override fun autoCancelCodeNotification(context: Context): Boolean = PrefsReader.autoCancelCodeNotification(context)

    override fun getNotificationRetentionTime(context: Context): Int = PrefsReader.getNotificationRetentionTime(context)

    override fun deduplicateSms(context: Context): Boolean = PrefsReader.deduplicateSms(context)

    override fun getHistoryLimit(context: Context): Int = PrefsReader.getHistoryLimit(context)

    override fun getHistoryLimit(context: Context, msgType: Int, isCodeSms: Boolean): Int {
        return PrefsReader.getHistoryLimit(context, msgType, isCodeSms)
    }

    override fun getIpcToken(context: Context): String = PrefsReader.getIpcToken(context)

    fun getSimSlotRemark(context: Context, simSlot: Int): String = PrefsReader.getSimSlotRemark(context, simSlot)
}
