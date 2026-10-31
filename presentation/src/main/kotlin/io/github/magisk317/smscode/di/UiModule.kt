package io.github.magisk317.smscode.di

import io.github.magisk317.smscode.presentation.BuildConfig
import io.github.magisk317.smscode.security.CiUpdateGateVault
import io.github.magisk317.smscode.ui.home.AppConfigViewModel
import io.github.magisk317.smscode.ui.home.SettingsViewModel
import io.github.magisk317.smscode.ui.record.CodeRecordViewModel
import io.github.magisk317.uikit.update.AppUpdateChecker
import io.github.magisk317.uikit.update.AppUpdateController
import io.github.magisk317.uikit.update.AppUpdateDownloader
import io.github.magisk317.smscode.common.constant.PrefConst
import io.github.magisk317.smscode.runtime.common.prefs.AppPreferencesDataStore
import io.github.magisk317.smscode.runtime.contract.prefs.PrefRead
import io.github.magisk317.uikit.update.AppUpdateSourceSpec
import io.github.magisk317.uikit.update.AppUpdateSources
import io.github.magisk317.uikit.update.startupFacts
import io.github.magisk317.uikit.update.CiStagingSpec
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val uiModule = module {
    viewModelOf(::AppConfigViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::CodeRecordViewModel)

    single {
        val context = androidContext()
        val isCiOrDebug = BuildConfig.DEBUG || BuildConfig.IS_CI_BUILD

        val isPlayFlavor = BuildConfig.HAS_BILLING

        // One shape for every consumer app: the channel (settings switch, else how the build was
        // produced) is re-read on each check, and a Play flavor never probes the CI track because
        // the store owns that version axis. See ui-kit's AppUpdateSources for the chain shape.
        val spec = AppUpdateSourceSpec(
            gitlabProjectPath = "magisk3171/XposedSmsCode",
            githubRepository = "magisk317/XposedSmsCode",
            ci = CiStagingSpec(
                artifactPath = "ci-dist/com.github.tianma8023.xposed.smscode/{versionName}/app-ci.apk",
                versionSidecarPath = "ci-dist/com.github.tianma8023.xposed.smscode/version.txt",
                manifestPath = "ci-dist/com.github.tianma8023.xposed.smscode/{versionName}/app-ci-manifest.json",
            ),
            isPlayFlavor = { isPlayFlavor },
            buildProvenance = { isCiOrDebug },
            currentCommitHash = { BuildConfig.COMMIT_HASH },
            // Miss / Unavailable stay null so AppUpdateSources falls back to buildProvenance;
            // only an explicit switch value outranks how the build was produced.
            trackCiOverride = {
                when (val read = AppPreferencesDataStore.readBoolean(context, PrefConst.KEY_TRACK_CI_BUILDS)) {
                    is PrefRead.Hit -> read.value
                    else -> null
                }
            },
        )

        val checker = AppUpdateChecker(
            context = context,
            sources = AppUpdateSources.routed(spec),
            allowForceUpdate = !BuildConfig.SKIP_FORCE_UPDATE,
        )
        val downloader = AppUpdateDownloader(context)
        AppUpdateController(
            context = context,
            checker = checker,
            downloader = downloader,
            fileProviderAuthority = "${context.packageName}.files",
            // 进入安装流程前封存当前激活：升级是唯一一个新版本可能自己激活不回来的时刻，
            // 这个风险与构建形态无关，所以不再按 CI/Release 分支。
            onBackupBeforeInstall = { CiUpdateGateVault.backup(context) },
            // 规则在 kit，值在本仓：未设置即 kit 默认（开/不限 Wi-Fi），商店包整条通道交给商店。
            startupCheckFacts = {
                val onStart = AppPreferencesDataStore.getBoolean(
                    context,
                    PrefConst.KEY_AUTO_UPDATE_ON_START,
                    true,
                )
                val wifiOnly = AppPreferencesDataStore.getBoolean(
                    context,
                    PrefConst.KEY_AUTO_UPDATE_WIFI_ONLY,
                    false,
                )
                startupFacts(
                    context = context,
                    trackCi = isCiOrDebug,
                    autoUpdateOnStart = onStart,
                    wifiOnly = wifiOnly,
                    playOwnsChannel = BuildConfig.HAS_BILLING,
                )
            },
        )
    }
}
