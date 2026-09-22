package com.github.magisk317.smscode.ui.theme

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import io.github.magisk317.uikit.theme.spacing
import io.github.magisk317.uikit.preference.SectionCard
import io.github.magisk317.uikit.preference.SettingsChoiceRow
import io.github.magisk317.uikit.surface.SectionColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.magisk317.smscode.ui.home.SettingsViewModel
import com.github.magisk317.smscode.core.R
import io.github.magisk317.uikit.R as UiKitR
import io.github.magisk317.uikit.surface.PageScaffoldExpressive
import io.github.magisk317.uikit.surface.PageScaffoldMiuix
import io.github.magisk317.uikit.preference.StateSwitchItem as SettingsSwitchItem
import io.github.magisk317.uikit.theme.UiKitColorSpec
import io.github.magisk317.uikit.theme.UiKitLayoutScale
import io.github.magisk317.uikit.theme.UiKitPaletteStyle
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle

@Composable
fun ThemeSettingsPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val themeState by viewModel.themeState.collectAsStateWithLifecycle()

    val themeOptions = listOf(
        stringResource(UiKitR.string.uikit_theme_follow_system),
        stringResource(UiKitR.string.uikit_theme_light),
        stringResource(UiKitR.string.uikit_theme_dark),
        stringResource(UiKitR.string.uikit_theme_black),
    )
    val uiKitStyleOptions = listOf(
        stringResource(R.string.ui_kit_style_expressive),
        stringResource(R.string.ui_kit_style_miuix),
    )
    val layoutScaleOptions = listOf(
        stringResource(R.string.layout_scale_compact),
        stringResource(R.string.layout_scale_standard),
        stringResource(R.string.layout_scale_comfortable),
    )
    val paletteStyleOptions = listOf(
        stringResource(R.string.palette_style_tonal_spot),
        stringResource(R.string.palette_style_neutral),
        stringResource(R.string.palette_style_vibrant),
        stringResource(R.string.palette_style_expressive),
        stringResource(R.string.palette_style_rainbow),
        stringResource(R.string.palette_style_fruit_salad),
        stringResource(R.string.palette_style_monochrome),
        stringResource(R.string.palette_style_fidelity),
        stringResource(R.string.palette_style_content),
    )
    val colorSpecOptions = listOf(
        stringResource(R.string.color_spec_2021),
        stringResource(R.string.color_spec_2025),
    )
    val accentOptions = listOf(
        stringResource(R.string.accent_system),
        stringResource(R.string.accent_blue),
        stringResource(R.string.accent_purple),
        stringResource(R.string.accent_green),
        stringResource(R.string.accent_orange),
        stringResource(R.string.accent_rose),
    )
    val selectedTheme = themeState.mode.coerceIn(0, themeOptions.lastIndex)
    val selectedUiKitStyle = UiKitStyle.fromValue(themeState.uiKitStyle).value
    val selectedLayoutScale = UiKitLayoutScale.fromValue(themeState.layoutScale).value
    val selectedPaletteStyle = UiKitPaletteStyle.fromValue(themeState.paletteStyle).value
    val selectedColorSpec = UiKitColorSpec.fromValue(themeState.colorSpec).value
    val selectedAccent = ThemeAccent.fromArgb(themeState.accentColor).value
    val floatingBottomBar = themeState.floatingBottomBar
    val bottomBarBlur = themeState.bottomBarBlur
    val bottomBarBackdrop = themeState.bottomBarBackdrop

    val isMiuixStyle = currentUiKitStyle() == UiKitStyle.Miuix
    val body: @Composable (PaddingValues, Modifier) -> Unit = { listPadding, scrollModifier ->
        val scrollState = rememberScrollState()
        SectionColumn(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .then(scrollModifier),
            contentPadding = PaddingValues(
                start = MaterialTheme.spacing.medium,
                top = listPadding.calculateTopPadding() + MaterialTheme.spacing.small,
                end = MaterialTheme.spacing.medium,
                bottom = MaterialTheme.spacing.large,
            ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            SectionCard(
                title = stringResource(R.string.settings_home_appearance_title),
                accordionMode = false,
                sectionExpanded = true,
                onExpandedChange = {},
            ) {
                SettingsChoiceRow(
                    title = stringResource(R.string.pref_choose_theme_title),
                    summary = themeOptions[selectedTheme],
                    options = themeOptions,
                    selectedIndex = selectedTheme,
                    onSelect = { index -> viewModel.setThemeMode(index) },
                )
                SettingsChoiceRow(
                    title = stringResource(R.string.pref_ui_kit_style_title),
                    summary = uiKitStyleOptions[selectedUiKitStyle],
                    options = uiKitStyleOptions,
                    selectedIndex = selectedUiKitStyle,
                    onSelect = { index -> viewModel.setUiKitStyle(index) },
                )
                SettingsChoiceRow(
                    title = stringResource(R.string.pref_layout_scale_title),
                    summary = layoutScaleOptions[selectedLayoutScale],
                    options = layoutScaleOptions,
                    selectedIndex = selectedLayoutScale,
                    onSelect = { index -> viewModel.setLayoutScale(index) },
                )
            }

            SectionCard(
                title = stringResource(R.string.pref_theme_group_color),
                accordionMode = false,
                sectionExpanded = true,
                onExpandedChange = {},
            ) {
                SettingsSwitchItem(
                    title = stringResource(R.string.pref_monet_title),
                    summary = stringResource(R.string.pref_monet_summary),
                    checked = themeState.monetEnabled,
                ) { enabled ->
                    viewModel.setMonetEnabled(enabled)
                }
                SettingsChoiceRow(
                    title = stringResource(R.string.pref_palette_style_title),
                    summary = paletteStyleOptions[selectedPaletteStyle],
                    options = paletteStyleOptions,
                    selectedIndex = selectedPaletteStyle,
                    onSelect = { index -> viewModel.setPaletteStyle(index) },
                )
                SettingsChoiceRow(
                    title = stringResource(R.string.pref_color_spec_title),
                    summary = colorSpecOptions[selectedColorSpec],
                    options = colorSpecOptions,
                    selectedIndex = selectedColorSpec,
                    onSelect = { index -> viewModel.setColorSpec(index) },
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.pref_surface_blur_title),
                    summary = stringResource(R.string.pref_surface_blur_summary),
                    checked = themeState.surfaceBlur,
                ) { enabled ->
                    viewModel.setSurfaceBlurEnabled(enabled)
                }
                SettingsSwitchItem(
                    title = stringResource(R.string.pref_dynamic_color_title),
                    summary = stringResource(R.string.pref_dynamic_color_summary),
                    checked = themeState.dynamicColor,
                    enabled = selectedAccent == ThemeAccent.System.value,
                ) { enabled ->
                    viewModel.setDynamicColorEnabled(enabled)
                }
                SettingsChoiceRow(
                    title = stringResource(R.string.pref_accent_color_title),
                    summary = accentOptions[selectedAccent],
                    options = accentOptions,
                    selectedIndex = selectedAccent,
                    onSelect = { index ->
                        ThemeAccent.fromValue(index).colorArgb?.let(viewModel::setAccentColor)
                            ?: viewModel.setAccentColor(0)
                    },
                )
            }

            SectionCard(
                title = stringResource(R.string.pref_theme_group_navigation),
                accordionMode = false,
                sectionExpanded = true,
                onExpandedChange = {},
            ) {
                SettingsSwitchItem(
                    title = stringResource(R.string.pref_floating_bottom_bar_title),
                    summary = if (isMiuixStyle) {
                        stringResource(R.string.pref_floating_bottom_bar_summary_miuix)
                    } else {
                        stringResource(R.string.pref_floating_bottom_bar_summary)
                    },
                    checked = floatingBottomBar,
                ) { enabled ->
                    viewModel.setFloatingBottomBarEnabled(enabled)
                }
                SettingsSwitchItem(
                    title = stringResource(R.string.pref_bottom_bar_blur_title),
                    summary = stringResource(R.string.pref_bottom_bar_blur_summary),
                    checked = bottomBarBlur,
                    enabled = floatingBottomBar && isMiuixStyle,
                ) { enabled ->
                    viewModel.setBottomBarBlurEnabled(enabled)
                }
                SettingsSwitchItem(
                    title = stringResource(R.string.pref_bottom_bar_backdrop_title),
                    summary = stringResource(R.string.pref_bottom_bar_backdrop_summary),
                    checked = bottomBarBackdrop,
                    enabled = floatingBottomBar && bottomBarBlur &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        isMiuixStyle,
                ) { enabled ->
                    viewModel.setBottomBarBackdropEnabled(enabled)
                }
        }
    }
    }

    val pageTitle = stringResource(R.string.pref_theme_details_title)
    when (currentUiKitStyle()) {
        UiKitStyle.Miuix -> PageScaffoldMiuix(title = pageTitle, onBack = onBack, content = body)
        UiKitStyle.Expressive -> PageScaffoldExpressive(title = pageTitle, onBack = onBack, content = body)
    }

}
