package io.github.magisk317.smscode.common.utils

import io.github.magisk317.smscode.common.constant.Const
import io.github.magisk317.smscode.core.R
import io.github.magisk317.smscode.runtime.BuildConfig
import io.github.magisk317.smscode.runtime.common.utils.ConfiguredPackageActions
import io.github.magisk317.smscode.runtime.common.utils.PackageActionConfig

/**
 * The values that make package queries app-specific.
 *
 * Everything the queries actually do lives in ConfiguredPackageActions, so callers
 * build one of these and use it directly.
 */
val xscPackageActions: ConfiguredPackageActions = ConfiguredPackageActions(
    PackageActionConfig(
        applicationId = BuildConfig.APPLICATION_ID,
        githubLatestReleaseUrl = Const.PROJECT_GITHUB_LATEST_RELEASE_URL,
        browserMissingMessageResId = R.string.browser_install_or_enable_prompt,
    ),
)
