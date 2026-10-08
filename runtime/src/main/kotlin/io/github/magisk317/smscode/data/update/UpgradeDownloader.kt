package io.github.magisk317.smscode.data.update

import android.content.Context
import io.github.magisk317.smscode.runtime.common.update.UpdateArtifactConfig
import io.github.magisk317.smscode.runtime.contract.update.UpgradeApkAsset
import io.github.magisk317.smscode.runtime.common.update.UpgradeDownloader as SharedUpgradeDownloader
import java.io.File

/**
 * Download entry point for XSC.
 *
 * The work lives in core; this only pins the artifact name prefix and keeps the
 * progress type identical to the shared one so callers need no local data class.
 */
object UpgradeDownloader {

    typealias Progress = SharedUpgradeDownloader.Progress

    private val artifactConfig = UpdateArtifactConfig(
        apkFilePrefix = "XposedSmsCode",
    )

    suspend fun download(
        context: Context,
        versionCode: Long,
        asset: UpgradeApkAsset,
        onProgress: (Progress) -> Unit = {},
    ): File = SharedUpgradeDownloader.download(
        context = context,
        versionCode = versionCode,
        asset = asset,
        artifactConfig = artifactConfig,
        onProgress = onProgress,
    )
}
