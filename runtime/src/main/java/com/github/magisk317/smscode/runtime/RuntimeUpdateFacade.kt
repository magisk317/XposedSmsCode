package com.github.magisk317.smscode.runtime

import android.content.Context
import android.content.Intent
import com.github.magisk317.smscode.data.update.ApkSecurityVerifier
import com.github.magisk317.smscode.data.update.GithubUpdateChecker
import com.github.magisk317.smscode.data.update.UpgradeDownloader
import com.github.magisk317.smscode.data.update.UpgradeInstaller
import io.github.magisk317.smscode.runtime.common.update.UpdatePolicy
import io.github.magisk317.smscode.runtime.common.update.UpdateCoordinator
import java.io.File

object RuntimeUpdateFacade {
    suspend fun fetchUpgradeInfo(): RuntimeUpgradeCheckResult = GithubUpdateChecker.fetchUpgradeInfo().toRuntime()

    fun isNewer(current: String, latest: String): Boolean = GithubUpdateChecker.isNewer(current, latest)

    fun isNewer(current: Long, latest: Long): Boolean = GithubUpdateChecker.isNewer(current, latest)

    fun selectBestApkForDevice(
        apks: List<RuntimeUpgradeApkAsset>,
    ): RuntimeUpgradeApkAsset? {
        return GithubUpdateChecker.selectBestApkForDevice(
            apks = apks.map(RuntimeUpgradeApkAsset::toInternal),
        )?.toRuntime()
    }

    suspend fun download(
        context: Context,
        versionCode: Long,
        asset: RuntimeUpgradeApkAsset,
        onProgress: (RuntimeUpgradeDownloadProgress) -> Unit = {},
    ): File {
        return UpgradeDownloader.download(
            context = context,
            versionCode = versionCode,
            asset = asset.toInternal(),
        ) { progress ->
            onProgress(progress.toRuntime())
        }
    }

    fun verifyDownloadedApk(
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

    fun canRequestPackageInstalls(context: Context): Boolean = UpgradeInstaller.canRequestPackageInstalls(context)

    fun buildUnknownSourceSettingsIntent(context: Context): Intent = UpgradeInstaller.buildUnknownSourceSettingsIntent(context)

    fun installApk(context: Context, apkFile: File): Result<Unit> = UpgradeInstaller.installApk(context, apkFile)

    fun shouldRunAutoCheck(enabled: Boolean, wifiOnly: Boolean, onWifi: Boolean): Boolean {
        return UpdatePolicy.shouldRunAutoCheck(enabled, wifiOnly, onWifi)
    }

    fun resolveStartupTarget(installedFromPlay: Boolean): RuntimeStartupTarget {
        return when (UpdatePolicy.resolveStartupTarget(installedFromPlay)) {
            UpdatePolicy.StartupTarget.PLAY -> RuntimeStartupTarget.PLAY
            UpdatePolicy.StartupTarget.GITHUB -> RuntimeStartupTarget.GITHUB
        }
    }

    fun shouldSkipGithubCheckOnStartup(
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

    fun shouldSkipIgnoredVersion(
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

    fun decidePlayAction(
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

    fun decidePlayFailureAction(fallbackOnQueryFailure: Boolean): RuntimePlayAction {
        return when (UpdateCoordinator.decidePlayFailureAction(fallbackOnQueryFailure)) {
            UpdateCoordinator.PlayAction.START_UPDATE_FLOW -> RuntimePlayAction.START_UPDATE_FLOW
            UpdateCoordinator.PlayAction.OPEN_STORE_OR_GITHUB -> RuntimePlayAction.OPEN_STORE_OR_GITHUB
            UpdateCoordinator.PlayAction.NO_OP -> RuntimePlayAction.NO_OP
        }
    }
}
