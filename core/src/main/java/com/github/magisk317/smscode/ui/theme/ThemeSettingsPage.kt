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
import io.github.magisk317.uikit.surface.SectionColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.magisk317.smscode.ui.home.SettingsViewModel
import com.github.magisk317.smscode.core.R
import io.github.magisk317.uikit.R as UiKitR
import io.github.magisk317.uikit.surface.PageScaffoldExpressive
import io.github.magisk317.uikit.surface.PageScaffoldMiuix
import io.github.magisk317.uikit.preference.Item as SettingsItem
import io.github.magisk317.uikit.preference.SingleChoiceOptionDialog
import io.github.magisk317.uikit.preference.StateSwitchItem as SettingsSwitchItem
import io.github.magisk317.uikit.theme.UiKitColorSpec
import io.github.magisk317.uikit.theme.UiKitLayoutScale
import io.github.magisk317.uikit.theme.UiKitPaletteStyle
import io.github.magisk317.uikit.theme.UiKitStyle
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import io.github.magisk317.uikit.theme.currentUiKitStyle

@Composable
fun ThemeSettingsPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val themeState by viewModel.themeState.collectAsStateWithLifecycle()
    var choice by remember { mutableStateOf<ThemeChoice?>(null) }

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
                ThemeChoiceRow(
                    title = stringResource(R.string.pref_choose_theme_title),
                    summary = themeOptions[selectedTheme],
                    miuix = isMiuixStyle,
                    expanded = choice == ThemeChoice.Theme,
                    onOpen = { choice = ThemeChoice.Theme },
                    onDismiss = { choice = null },
                    options = themeOptions,
                    selectedIndex = selectedTheme,
                    onSelect = { index ->
                        viewModel.setThemeMode(index)
                        choice = null
                    },
                )
                ThemeChoiceRow(
                    title = stringResource(R.string.pref_ui_kit_style_title),
                    summary = uiKitStyleOptions[selectedUiKitStyle],
                    miuix = isMiuixStyle,
                    expanded = choice == ThemeChoice.UiKitStyle,
                    onOpen = { choice = ThemeChoice.UiKitStyle },
                    onDismiss = { choice = null },
                    options = uiKitStyleOptions,
                    selectedIndex = selectedUiKitStyle,
                    onSelect = { index ->
                        viewModel.setUiKitStyle(index)
                        choice = null
                    },
                )
                ThemeChoiceRow(
                    title = stringResource(R.string.pref_layout_scale_title),
                    summary = layoutScaleOptions[selectedLayoutScale],
                    miuix = isMiuixStyle,
                    expanded = choice == ThemeChoice.LayoutScale,
                    onOpen = { choice = ThemeChoice.LayoutScale },
                    onDismiss = { choice = null },
                    options = layoutScaleOptions,
                    selectedIndex = selectedLayoutScale,
                    onSelect = { index ->
                        viewModel.setLayoutScale(index)
                        choice = null
                    },
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
                ThemeChoiceRow(
                    title = stringResource(R.string.pref_palette_style_title),
                    summary = paletteStyleOptions[selectedPaletteStyle],
                    miuix = isMiuixStyle,
                    expanded = choice == ThemeChoice.PaletteStyle,
                    onOpen = { choice = ThemeChoice.PaletteStyle },
                    onDismiss = { choice = null },
                    options = paletteStyleOptions,
                    selectedIndex = selectedPaletteStyle,
                    onSelect = { index ->
                        viewModel.setPaletteStyle(index)
                        choice = null
                    },
                )
                ThemeChoiceRow(
                    title = stringResource(R.string.pref_color_spec_title),
                    summary = colorSpecOptions[selectedColorSpec],
                    miuix = isMiuixStyle,
                    expanded = choice == ThemeChoice.ColorSpec,
                    onOpen = { choice = ThemeChoice.ColorSpec },
                    onDismiss = { choice = null },
                    options = colorSpecOptions,
                    selectedIndex = selectedColorSpec,
                    onSelect = { index ->
                        viewModel.setColorSpec(index)
                        choice = null
                    },
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
                ThemeChoiceRow(
                    title = stringResource(R.string.pref_accent_color_title),
                    summary = accentOptions[selectedAccent],
                    miuix = isMiuixStyle,
                    expanded = choice == ThemeChoice.Accent,
                    onOpen = { choice = ThemeChoice.Accent },
                    onDismiss = { choice = null },
                    options = accentOptions,
                    selectedIndex = selectedAccent,
                    onSelect = { index ->
                        ThemeAccent.fromValue(index).colorArgb?.let(viewModel::setAccentColor)
                            ?: viewModel.setAccentColor(0)
                        choice = null
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

    val activeChoice = choice
    if (activeChoice != null && !isMiuixStyle) {
        val options = when (activeChoice) {
            ThemeChoice.Theme -> themeOptions
            ThemeChoice.UiKitStyle -> uiKitStyleOptions
            ThemeChoice.LayoutScale -> layoutScaleOptions
            ThemeChoice.PaletteStyle -> paletteStyleOptions
            ThemeChoice.ColorSpec -> colorSpecOptions
            ThemeChoice.Accent -> accentOptions
        }
        val selectedIndex = when (activeChoice) {
            ThemeChoice.Theme -> selectedTheme
            ThemeChoice.UiKitStyle -> selectedUiKitStyle
            ThemeChoice.LayoutScale -> selectedLayoutScale
            ThemeChoice.PaletteStyle -> selectedPaletteStyle
            ThemeChoice.ColorSpec -> selectedColorSpec
            ThemeChoice.Accent -> selectedAccent
        }
        SingleChoiceOptionDialog(
            title = when (activeChoice) {
                ThemeChoice.Theme -> stringResource(R.string.pref_choose_theme_title)
                ThemeChoice.UiKitStyle -> stringResource(R.string.pref_ui_kit_style_title)
                ThemeChoice.LayoutScale -> stringResource(R.string.pref_layout_scale_title)
                ThemeChoice.PaletteStyle -> stringResource(R.string.pref_palette_style_title)
                ThemeChoice.ColorSpec -> stringResource(R.string.pref_color_spec_title)
                ThemeChoice.Accent -> stringResource(R.string.pref_accent_color_title)
            },
            options = options,
            selectedIndex = selectedIndex,
            onSelectionChange = { index ->
                when (activeChoice) {
                    ThemeChoice.Theme -> viewModel.setThemeMode(index)
                    ThemeChoice.UiKitStyle -> viewModel.setUiKitStyle(index)
                    ThemeChoice.LayoutScale -> viewModel.setLayoutScale(index)
                    ThemeChoice.PaletteStyle -> viewModel.setPaletteStyle(index)
                    ThemeChoice.ColorSpec -> viewModel.setColorSpec(index)
                    ThemeChoice.Accent -> {
                        ThemeAccent.fromValue(index).colorArgb?.let(viewModel::setAccentColor)
                            ?: viewModel.setAccentColor(0)
                    }
                }
                choice = null
            },
            onDismissRequest = { choice = null },
        )
    }
}

private enum class ThemeChoice {
    Theme, UiKitStyle, LayoutScale, PaletteStyle, ColorSpec, Accent,
}

/**
 * A selectable settings row. Expressive keeps the classic choice dialog (page level);
 * miuix replaces it with the stock dropdown: an [OverlayListPopup] anchored right under
 * the row, styled by [DropdownImpl] with the selected check — the KernelSU/HyperOS form.
 */
@Composable
private fun ThemeChoiceRow(
    title: String,
    summary: String,
    miuix: Boolean,
    expanded: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        SettingsItem(
            title = title,
            summary = summary,
            onClick = onOpen,
        )
        if (miuix) {
            OverlayListPopup(
                show = expanded,
                onDismissRequest = onDismiss,
                content = {
                    ListPopupColumn {
                        options.forEachIndexed { index, option ->
                            DropdownImpl(
                                text = option,
                                optionSize = options.size,
                                isSelected = index == selectedIndex,
                                index = index,
                                onSelectedIndexChange = { picked ->
                                    onSelect(picked)
                                    onDismiss()
                                },
                            )
                        }
                    }
                },
            )
        }
    }
}
