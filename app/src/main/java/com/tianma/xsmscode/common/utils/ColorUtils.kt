package com.tianma.xsmscode.common.utils

import androidx.annotation.ColorInt

object ColorUtils {

    /**
     * Darken or lighten a specific color.<p/>
     * Calculate an new color according to originColor and factor. Dark color returned if factor < 1.0f, light color returned if factor > 1.0f
     *
     * @param originColor The specific color value.
     * @param factor      The factor that can influence the color returned.
     * @return Dark color returned if factor < 1.0f, light color returned if factor > 1.0f
     */
    @JvmStatic
    fun gradientColor(@ColorInt originColor: Int, factor: Float): Int {
        if (factor == 1.0f) {
            return originColor
        }
        val a = originColor ushr 24
        var r = (((originColor ushr 16) and 0xff) * factor).toInt()
        r = r.coerceAtMost(0xff)
        var g = (((originColor ushr 8) and 0xff) * factor).toInt()
        g = g.coerceAtMost(0xff)
        var b = ((originColor and 0xff) * factor).toInt()
        b = b.coerceAtMost(0xff)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
