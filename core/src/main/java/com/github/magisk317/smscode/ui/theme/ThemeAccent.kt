package com.github.magisk317.smscode.ui.theme

/**
 * Fixed accent choices offered by the theme settings page.
 *
 * Values match the storage indexes used by the shared appearance accessors;
 * [colorArgb] is what gets handed to the theme when a fixed accent is picked,
 * while [System] keeps dynamic colour generation on.
 */
enum class ThemeAccent(val value: Int, val colorArgb: Int?) {
    System(0, null),
    Blue(1, 0xFF3F51B5.toInt()),
    Purple(2, 0xFF6750A4.toInt()),
    Green(3, 0xFF2E7D32.toInt()),
    Orange(4, 0xFFE65100.toInt()),
    Rose(5, 0xFFB3261E.toInt()),
    ;

    companion object {
        fun fromValue(value: Int): ThemeAccent =
            entries.firstOrNull { it.value == value } ?: System

        fun fromArgb(colorArgb: Int): ThemeAccent =
            entries.firstOrNull { it.colorArgb == colorArgb } ?: System
    }
}
