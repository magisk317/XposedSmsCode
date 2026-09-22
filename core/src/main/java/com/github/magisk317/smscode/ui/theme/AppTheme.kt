package com.github.magisk317.smscode.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.github.magisk317.smscode.common.constant.PrefConst
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import io.github.magisk317.smscode.runtime.common.prefs.AppearancePreferences
import io.github.magisk317.uikit.theme.MagiskUiKitTheme
import io.github.magisk317.uikit.theme.UiKitColorSpec
import io.github.magisk317.uikit.theme.UiKitLayoutScale
import io.github.magisk317.uikit.theme.UiKitPaletteStyle
import io.github.magisk317.uikit.theme.UiKitStyle

/**
 * Applies the appearance configuration to the composition, MiPush style: the
 * stored values are observed as DataStore flows right here, so any write from
 * any screen recomposes the tree immediately. Callers may still pass explicit
 * overrides; `null` (the default) means "follow storage".
 */
@Composable
fun AppTheme(
    themeMode: Int? = null,
    uiKitStyle: Int? = null,
    dynamicColor: Boolean? = null,
    accentColor: Int? = null,
    monetEnabled: Boolean? = null,
    paletteStyle: Int? = null,
    colorSpec: Int? = null,
    surfaceBlur: Boolean? = null,
    layoutScale: Int? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val storedThemeMode by AppPreferencesDataStore.getIntFlow(
        context, PrefConst.KEY_CHOOSE_THEME, 0,
    ).collectAsState(initial = 0)
    val storedUiKitStyle by AppPreferencesDataStore.getIntFlow(
        context, PrefConst.KEY_UI_KIT_STYLE, 0,
    ).collectAsState(initial = 0)
    val storedLayoutScale by AppearancePreferences.layoutScaleFlow(context)
        .collectAsState(initial = 1)
    val storedPaletteStyle by AppearancePreferences.paletteStyleFlow(context)
        .collectAsState(initial = 0)
    val storedColorSpec by AppearancePreferences.colorSpecFlow(context)
        .collectAsState(initial = 1)
    val storedAccentColor by AppearancePreferences.accentColorFlow(context)
        .collectAsState(initial = 0)
    val storedMonet by AppearancePreferences.monetEnabledFlow(context)
        .collectAsState(initial = false)
    val storedSurfaceBlur by AppearancePreferences.surfaceBlurFlow(context)
        .collectAsState(initial = false)
    val storedDynamicColor by AppearancePreferences.dynamicColorFlow(context)
        .collectAsState(initial = true)

    MagiskUiKitTheme(
        themeMode = themeMode ?: storedThemeMode,
        dynamicColor = dynamicColor ?: storedDynamicColor,
        accentColor = accentColor ?: storedAccentColor,
        monetEnabled = monetEnabled ?: storedMonet,
        paletteStyle = UiKitPaletteStyle.fromValue(paletteStyle ?: storedPaletteStyle),
        colorSpec = UiKitColorSpec.fromValue(colorSpec ?: storedColorSpec),
        surfaceBlur = surfaceBlur ?: storedSurfaceBlur,
        uiKitStyle = UiKitStyle.fromValue(uiKitStyle ?: storedUiKitStyle),
        layoutScale = UiKitLayoutScale.fromValue(layoutScale ?: storedLayoutScale),
        content = content,
    )
}
