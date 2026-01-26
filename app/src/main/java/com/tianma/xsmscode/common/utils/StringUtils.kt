package com.tianma.xsmscode.common.utils

object StringUtils {

    @JvmStatic
    fun escape(str: String?): String? {
        if (str == null) return null

        val sb = StringBuilder(str.length + 2)
        sb.append('"')
        for (c in str) {
            when (c) {
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\u000C' -> sb.append("\\f") // '\f' is not allowed in Kotlin character literal sometimes, using unicode
                '\\' -> sb.append("\\\\")
                '\'' -> sb.append("\\'")
                '\"' -> sb.append("\\\"")
                else -> {
                    if (c.code < 32 || c.code >= 127) {
                        sb.append(String.format("\\u%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
