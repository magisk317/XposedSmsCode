package com.github.magisk317.smscode.common.utils

import android.content.Context
import com.github.magisk317.smscode.common.constant.Const
import com.github.magisk317.smscode.core.R
import com.github.magisk317.smscode.runtime.BuildConfig
import io.github.magisk317.smscode.runtime.common.utils.ConfiguredPackageActions
import io.github.magisk317.smscode.runtime.common.utils.FrameworkCompatibilityMonitor
import io.github.magisk317.smscode.runtime.common.utils.PackageActionConfig

object PackageUtils {
    private val actions = ConfiguredPackageActions(
        PackageActionConfig(
            applicationId = BuildConfig.APPLICATION_ID,
            qqGroupUrl = Const.QQ_GROUP_URL,
            githubLatestReleaseUrl = Const.PROJECT_GITHUB_LATEST_RELEASE_URL,
            joinQqGroupFailedMessageResId = R.string.prompt_join_qq_group_failed,
            browserMissingMessageResId = R.string.browser_install_or_enable_prompt,
        ),
    )

    fun isPackageInstalled(context: Context, packageName: String): Boolean =
        actions.isPackageInstalled(context, packageName)

    fun getPackageVersion(context: Context, packageName: String): Pair<String, Long>? =
        actions.getPackageVersion(context, packageName)

    fun inspectFrameworkIssue(context: Context): FrameworkCompatibilityMonitor.FrameworkIssue? =
        actions.inspectFrameworkIssue(context)

    fun getLsposedModuleInfo(context: Context): Pair<String, String>? =
        actions.getLsposedModuleInfo(context)

    fun hasRootAccess(): Boolean = actions.hasRootAccess()

    fun joinQQGroup(context: Context): String? = actions.joinQQGroup(context)

    fun isInstalledFromPlay(context: Context): Boolean = actions.isInstalledFromPlay(context)

    fun openPlayStoreOrGithub(context: Context): String? = actions.openPlayStoreOrGithub(context)

    fun isOnWifi(context: Context): Boolean = actions.isOnWifi(context)
}
