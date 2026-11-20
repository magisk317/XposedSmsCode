@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.smscode.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.magisk317.uikit.R as UiKitR
import io.github.magisk317.uikit.surface.PageScaffoldExpressive

/** Expressive settings surface using the shared secondary-page scaffold contract. */
@Composable
internal fun ComposeSettingsScreenMaterial(
    viewModel: SettingsViewModel? = null,
    refreshTrigger: Int = 0,
    onExit: () -> Unit = {},
) {
    PageScaffoldExpressive(
        title = stringResource(UiKitR.string.pref_general_title),
    ) { listPadding, scrollModifier ->
        ComposeSettingsScreenBody(
            viewModel = viewModel,
            refreshTrigger = refreshTrigger,
            onExit = onExit,
            listPadding = listPadding,
            scrollModifier = scrollModifier,
        )
    }
}
