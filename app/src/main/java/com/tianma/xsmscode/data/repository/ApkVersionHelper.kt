package com.tianma.xsmscode.data.repository

import androidx.core.text.HtmlCompat
import com.tianma.xsmscode.data.db.entity.ApkVersion
import org.jsoup.Jsoup
import java.util.regex.Pattern

object ApkVersionHelper {

    fun parseFromCoolApk(html: String): ApkVersion {
        val document = Jsoup.parse(html)
        var versionName = "-1"
        var versionInfo: String? = null
        if (document != null) {
            // version name
            val element = document.selectFirst("title")
            if (element != null) {
                val text = element.text()
                val p = Pattern.compile("\\d(\\.\\d)+")
                val m = p.matcher(text)
                if (m.find()) {
                    versionName = m.group()
                }
            }

            // version info
            val rootInfoEle = document.selectFirst(".apk_left_title:contains(新版特性)")
            if (rootInfoEle != null) {
                val infoEle = rootInfoEle.selectFirst(".apk_left_title_info")
                if (infoEle != null) {
                    versionInfo = HtmlCompat.fromHtml(
                        infoEle.toString(),
                        HtmlCompat.FROM_HTML_MODE_COMPACT
                    ).toString().trim()
                }
            }
        }
        return ApkVersion(versionName, versionInfo)
    }
}
