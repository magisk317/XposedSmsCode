package io.github.magisk317.smscode.di

import io.github.magisk317.smscode.core.BuildConfig
import io.github.magisk317.smscode.security.CiUpdateGateVault
import io.github.magisk317.smscode.ui.home.AppConfigViewModel
import io.github.magisk317.smscode.ui.home.SettingsViewModel
import io.github.magisk317.smscode.ui.record.CodeRecordViewModel
import io.github.magisk317.uikit.update.AppUpdateChecker
import io.github.magisk317.uikit.update.AppUpdateController
import io.github.magisk317.uikit.update.AppUpdateDownloader
import io.github.magisk317.uikit.update.GitLabCiArtifactUpdateSource
import io.github.magisk317.uikit.update.GitLabReleaseUpdateSource
import io.github.magisk317.uikit.update.GitHubReleaseUpdateSource
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val uiModule = module {
    viewModelOf(::AppConfigViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::CodeRecordViewModel)

    single {
        val context = androidContext()
        val isCiOrDebug = BuildConfig.DEBUG || BuildConfig.VERSION_NAME.contains("-202") || BuildConfig.VERSION_NAME.contains("-test") || BuildConfig.VERSION_NAME.contains("-ci")

        val sources = if (isCiOrDebug) {
            listOf(
                GitLabCiArtifactUpdateSource(
                    projectPath = "magisk3171/XposedSmsCode",
                    branch = "beta",
                    jobName = "android:build",
                    artifactPath = "app/build/outputs/apk/github/release/universal_XposedSmsCode_release.apk",
                    currentCommitHash = BuildConfig.COMMIT_HASH,
                ),
                GitLabReleaseUpdateSource("magisk3171/XposedSmsCode"),
            )
        } else {
            listOf(
                GitLabReleaseUpdateSource("magisk3171/XposedSmsCode"),
                GitHubReleaseUpdateSource("magisk317/XposedSmsCode"),
            )
        }

        val checker = AppUpdateChecker(
            context = context,
            sources = sources,
        )
        val downloader = AppUpdateDownloader(context)
        AppUpdateController(
            context = context,
            checker = checker,
            downloader = downloader,
            fileProviderAuthority = "${context.packageName}.files",
            onForceUpdateDetected = {
                // 门禁逻辑：一旦检测到 CI 强制更新，封存激活并熔断功能，向 Hook 进程发送拒止通知
                CiUpdateGateVault.sealAndRevoke(context)
            },
        )
    }
}
