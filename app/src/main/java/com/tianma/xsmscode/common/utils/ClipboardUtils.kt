package com.tianma.xsmscode.common.utils

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context

object ClipboardUtils {

    @JvmStatic
    fun copyToClipboard(context: Context, text: String?) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        if (cm == null) {
            XLog.e("Copy failed, clipboard manager is null")
            return
        }
        val clipData = ClipData.newPlainText("Copy text", text)
        cm.setPrimaryClip(clipData)
        XLog.i("Copy to clipboard succeed")
    }

    @JvmStatic
    fun clearClipboard(context: Context) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        if (cm == null) {
            XLog.e("Clear failed, clipboard manager is null")
            return
        }
        if (cm.hasPrimaryClip()) {
            val cd = cm.primaryClipDescription
            if (cd != null) {
                if (cd.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                    cm.setPrimaryClip(ClipData.newPlainText("Copy text", ""))
                    XLog.i("Clear clipboard succeed")
                }
            }
        }
    }
}
