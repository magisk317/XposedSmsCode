package com.github.magisk317.smscode.runtime

import android.content.Context
import android.content.Intent
import com.github.magisk317.smscode.data.update.GithubUpdateChecker
import com.github.magisk317.smscode.data.update.UpgradeDownloader
import com.github.magisk317.smscode.data.update.UpgradeInstaller
import com.github.magisk317.smscode.runtime.bridge.UiUpdateAccess
import io.github.magisk317.smscode.runtime.common.update.ApkSecurityVerifier
import io.github.magisk317.smscode.runtime.common.update.UpdateCoordinator
import io.github.magisk317.smscode.runtime.common.update.UpdatePolicy
import java.io.File

object RuntimeUpdateFacade : UiUpdateAccess {
    override suspend fun fetchUpgradeInfo(): RuntimeUpgradeCheckResult = GithubUpdateChecker.fetchUpgradeInfo().toRuntime()

    override fun isNewer(current: String, latest: String): Boolean = GithubUpdateChecker.isNewer(current, latest)

    override fun isNewer(current: Long, latest: Long): Boolean = GithubUpdateChecker.isNewer(current, latest)

    override fun selectBestApkForDevice(
        apks: List<RuntimeUpgradeApkAsset>,
    ): RuntimeUpgradeApkAsset? {
        return GithubUpdateChecker.selectBestApkForDevice(
            apks = apks.map(RuntimeUpgradeApkAsset::toInternal),
        )?.toRuntime()
    }

    override suspend fun download(
        context: Context,
        versionCode: Long,
        asset: RuntimeUpgradeApkAsset,
        onProgress: (RuntimeUpgradeDownloadProgress) -> Unit,
    ): File {
        return UpgradeDownloader.download(
            context = context,
            versionCode = versionCode,
            asset = asset.toInternal(),
        ) { progress ->
            onProgress(progress.toRuntime())
        }
    }

    override fun verifyDownloadedApk(
        context: Context,
        apkFile: File,
        expectedSha256: String,
        expectedSigningCertSha256: String,
    ): RuntimeApkVerificationResult {
        val result = ApkSecurityVerifier.verifyDownloadedApk(
            context = context,
            apkFile = apkFile,
            expectedSha256 = expectedSha256,
            expectedSigningCertSha256 = expectedSigningCertSha256,
        )
        return RuntimeApkVerificationResult(
            success = result.success,
            reason = result.reason,
        )
    }

    override fun canRequestPackageInstalls(context: Context): Boolean = UpgradeInstaller.canRequestPackageInstalls(context)

    override fun buildUnknownSourceSettingsIntent(context: Context): Intent =
        UpgradeInstaller.buildUnknownSourceSettingsIntent(context)

    override fun installApk(context: Context, apkFile: File): Result<Unit> = UpgradeInstaller.installApk(context, apkFile)

    override fun shouldRunAutoCheck(enabled: Boolean, wifiOnly: Boolean, onWifi: Boolean): Boolean {
        return UpdatePolicy.shouldRunAutoCheck(enabled, wifiOnly, onWifi)
    }

    override fun resolveStartupTarget(installedFromPlay: Boolean): RuntimeStartupTarget {
        return when (UpdatePolicy.resolveStartupTarget(installedFromPlay)) {
            UpdatePolicy.StartupTarget.PLAY -> RuntimeStartupTarget.PLAY
            UpdatePolicy.StartupTarget.GITHUB -> RuntimeStartupTarget.GITHUB
        }
    }

    override fun shouldSkipGithubCheckOnStartup(
        installedFromPlay: Boolean,
        autoCheckEnabled: Boolean,
        wifiOnly: Boolean,
        onWifi: Boolean,
    ): Boolean {
        return UpdatePolicy.shouldSkipGithubCheckOnStartup(
            installedFromPlay = installedFromPlay,
            autoCheckEnabled = autoCheckEnabled,
            wifiOnly = wifiOnly,
            onWifi = onWifi,
        )
    }

    override fun shouldSkipIgnoredVersion(
        respectIgnoredVersion: Boolean,
        ignoredVersion: String,
        latestVersion: String,
    ): Boolean {
        return UpdatePolicy.shouldSkipIgnoredVersion(
            respectIgnoredVersion = respectIgnoredVersion,
            ignoredVersion = ignoredVersion,
            latestVersion = latestVersion,
        )
    }

    override fun decidePlayAction(
        updateAvailable: Boolean,
        flexibleAllowed: Boolean,
        inProgress: Boolean,
        silentIfNoUpdate: Boolean,
    ): RuntimePlayAction {
        return when (
            UpdateCoordinator.decidePlayAction(
                updateAvailable = updateAvailable,
                flexibleAllowed = flexibleAllowed,
                inProgress = inProgress,
                silentIfNoUpdate = silentIfNoUpdate,
            )
        ) {
            UpdateCoordinator.PlayAction.START_UPDATE_FLOW -> RuntimePlayAction.START_UPDATE_FLOW
            UpdateCoordinator.PlayAction.OPEN_STORE_OR_GITHUB -> RuntimePlayAction.OPEN_STORE_OR_GITHUB
            UpdateCoordinator.PlayAction.NO_OP -> RuntimePlayAction.NO_OP
        }
    }

    override fun decidePlayFailureAction(fallbackOnQueryFailure: Boolean): RuntimePlayAction {
        return when (UpdateCoordinator.decidePlayFailureAction(fallbackOnQueryFailure)) {
            UpdateCoordinator.PlayAction.START_UPDATE_FLOW -> RuntimePlayAction.START_UPDATE_FLOW
            UpdateCoordinator.PlayAction.OPEN_STORE_OR_GITHUB -> RuntimePlayAction.OPEN_STORE_OR_GITHUB
            UpdateCoordinator.PlayAction.NO_OP -> RuntimePlayAction.NO_OP
        }
    }
}
