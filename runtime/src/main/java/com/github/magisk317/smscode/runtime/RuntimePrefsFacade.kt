package com.github.magisk317.smscode.runtime

import android.content.Context
import com.github.magisk317.smscode.common.utils.PrefsReader

object RuntimePrefsFacade {
    fun isEnabled(context: Context): Boolean = PrefsReader.isEnabled(context)

    fun isVerboseLogMode(context: Context): Boolean = PrefsReader.isVerboseLogMode(context)

    fun isSensitiveDebugLogMode(context: Context): Boolean = PrefsReader.isSensitiveDebugLogMode(context)

    fun autoInputCodeEnabled(context: Context): Boolean = PrefsReader.autoInputCodeEnabled(context)

    fun autoEnterCodeEnabled(context: Context): Boolean = PrefsReader.autoEnterCodeEnabled(context)

    fun getAutoInputCodeDelay(context: Context): Long = PrefsReader.getAutoInputCodeDelay(context)

    fun getAutoInputCodeIntervalMs(context: Context): Long = PrefsReader.getAutoInputCodeIntervalMs(context)

    fun shouldShowToast(context: Context): Boolean = PrefsReader.shouldShowToast(context)

    fun getSMSCodeKeywords(context: Context): String? = PrefsReader.getSMSCodeKeywords(context)

    fun getSmsCodeKeywords(context: Context): String? = getSMSCodeKeywords(context)

    fun markAsReadEnabled(context: Context): Boolean = PrefsReader.markAsReadEnabled(context)

    fun deleteSmsEnabled(context: Context): Boolean = PrefsReader.deleteSmsEnabled(context)

    fun copyToClipboardEnabled(context: Context): Boolean = PrefsReader.copyToClipboardEnabled(context)

    fun recordCodeSmsEnabled(context: Context): Boolean = PrefsReader.recordCodeSmsEnabled(context)

    fun recordSmsCodeEnabled(context: Context): Boolean = recordCodeSmsEnabled(context)

    fun blockSmsEnabled(context: Context): Boolean = PrefsReader.blockSmsEnabled(context)

    fun killMeEnabled(context: Context): Boolean = PrefsReader.killMeEnabled(context)

    fun showCodeNotification(context: Context): Boolean = PrefsReader.showCodeNotification(context)

    fun getCodeNotificationOwner(context: Context): String = PrefsReader.getCodeNotificationOwner(context)

    fun autoCancelCodeNotification(context: Context): Boolean = PrefsReader.autoCancelCodeNotification(context)

    fun getNotificationRetentionTime(context: Context): Int = PrefsReader.getNotificationRetentionTime(context)

    fun deduplicateSms(context: Context): Boolean = PrefsReader.deduplicateSms(context)

    fun getHistoryLimit(context: Context): Int = PrefsReader.getHistoryLimit(context)

    fun getHistoryLimit(context: Context, msgType: Int, isCodeSms: Boolean): Int {
        return PrefsReader.getHistoryLimit(context, msgType, isCodeSms)
    }

    fun getIpcToken(context: Context): String = PrefsReader.getIpcToken(context)

    fun getSimSlotRemark(context: Context, simSlot: Int): String = PrefsReader.getSimSlotRemark(context, simSlot)
}
