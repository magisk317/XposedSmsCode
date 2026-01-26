package com.tianma.xsmscode.common.utils

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import com.github.tianma8023.xposed.smscode.R
import java.util.*

/**
 * Other Utils
 */
object Utils {

    @JvmStatic
    fun showWebPage(context: Context, url: String) {
        try {
            val cti = CustomTabsIntent.Builder().build()
            cti.intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            cti.launchUrl(context, Uri.parse(url))
        } catch (e: Exception) {
            Toast.makeText(context, R.string.browser_install_or_enable_prompt, Toast.LENGTH_SHORT).show()
        }
    }

    @JvmStatic
    fun copyToClipboard(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("smscode", text))
    }

    private fun getLanguagePath(): String {
        val locale = Locale.getDefault()
        val language = locale.language
        val country = locale.country
        var result = "en"
        if ("zh" == language) {
            result = if ("CN".equals(country, ignoreCase = true)) {
                "zh-CN"
            } else if ("HK".equals(country, ignoreCase = true) || "TW".equals(country, ignoreCase = true)) {
                "zh-TW"
            } else {
                "zh-CN"
            }
        }
        return result
    }

    @JvmStatic
    fun getProjectDocUrl(docBaseUrl: String, docPath: String): String {
        return "$docBaseUrl/${getLanguagePath()}/$docPath"
    }

    @JvmStatic
    fun isValidFilename(filename: String?): Boolean {
        if (filename.isNullOrBlank()) {
            return false
        }

        val trimmed = filename.trim()
        if ("." == trimmed || ".." == trimmed) {
            return false
        }

        for (c in filename) {
            if (!isValidFilenameChar(c)) {
                return false
            }
        }
        return true
    }

    private fun isValidFilenameChar(c: Char): Boolean {
        // check control characters
        if (c.code <= 0x1f || c.code == 0x7f) {
            return false
        }

        // check special characters
        return when (c) {
            '"', '*', '/', '\\', '<', '>', '|', '?', ',', ';', ':' -> false
            else -> true
        }
    }
}
