package io.github.magisk317.smscode.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.magisk317.smscode.common.constant.PrefConst
import io.github.magisk317.smscode.presentation.BuildConfig
import io.github.magisk317.smscode.presentation.R
import io.github.magisk317.uikit.preference.AppArrowItem
import io.github.magisk317.uikit.update.AppUpdateController
import io.github.magisk317.uikit.update.R as UiKitUpdateR
import org.koin.compose.koinInject

/**
 * The update rows of the settings shell.
 *
 * They live here instead of inside `ComposeSettingsScreen` because that file carries a reviewed
 * god-file entry that may not grow: `verifyGodFileLimits` counts exact lines and rejects growth.
 * Keeping new settings surface area in small composables like this one is the documented split
 * direction (docs/ARCHITECTURE.md).
 */
@Composable
fun UpdateSettingsSection() {
    val updateController = koinInject<AppUpdateController>()
    AppArrowItem(
        title = stringResource(id = R.string.check_update_title),
        summary = stringResource(id = R.string.check_update_summary),
    ) {
        updateController.check(userInitiated = true)
    }
    // Unset falls back to how this build was produced; toggling pins the track explicitly, so
    // `defaultValue` renders that fallback as the state the user sees.
    SwitchItem(
        title = stringResource(id = R.string.settings_track_ci_builds_title),
        summary = stringResource(id = R.string.settings_track_ci_builds_summary),
        key = PrefConst.KEY_TRACK_CI_BUILDS,
        defaultValue = BuildConfig.DEBUG || BuildConfig.IS_CI_BUILD,
    )
    // A CI build checks unconditionally, so the two release switches would be rows that do
    // nothing: they only exist where the rule they feed actually applies.
    if (!BuildConfig.DEBUG && !BuildConfig.IS_CI_BUILD) {
        SwitchItem(
            title = stringResource(id = UiKitUpdateR.string.uikit_update_auto_check_on_start),
            summary = stringResource(id = R.string.settings_auto_check_on_start_summary),
            key = PrefConst.KEY_AUTO_UPDATE_ON_START,
            defaultValue = true,
        )
        SwitchItem(
            title = stringResource(id = UiKitUpdateR.string.uikit_update_auto_check_wifi_only),
            summary = stringResource(id = R.string.settings_auto_check_wifi_only_summary),
            key = PrefConst.KEY_AUTO_UPDATE_WIFI_ONLY,
            defaultValue = false,
        )
    }
}
