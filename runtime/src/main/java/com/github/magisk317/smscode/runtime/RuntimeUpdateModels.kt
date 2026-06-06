package com.github.magisk317.smscode.runtime

import com.github.magisk317.smscode.data.update.UpgradeDownloader
import io.github.magisk317.smscode.runtime.common.update.GithubReleaseInfo
import io.github.magisk317.smscode.runtime.common.update.UpgradeApkAsset
import io.github.magisk317.smscode.runtime.common.update.UpgradeCheckResult
import io.github.magisk317.smscode.runtime.common.update.UpgradeInfo
import io.github.magisk317.smscode.runtime.common.update.VersionLog

data class RuntimeVersionLog(
    val name: String = "",
    val code: Long = 0L,
    val desc: String = "",
)

data class RuntimeGithubReleaseInfo(
    val versionName: String,
    val htmlUrl: String,
)

data class RuntimeUpgradeApkAsset(
    val abi: String = "",
    val downloadUrl: String = "",
    val fileSize: Long = 0L,
    val sha256: String = "",
)

data class RuntimeUpgradeInfo(
    val versionCode: Long = 0L,
    val versionName: String = "",
    val htmlUrl: String = "",
    val changelog: String = "",
    val versionLogs: List<RuntimeVersionLog> = emptyList(),
    val apks: List<RuntimeUpgradeApkAsset> = emptyList(),
    val signingCertSha256: String = "",
)

sealed interface RuntimeUpgradeCheckResult {
    data class Structured(val info: RuntimeUpgradeInfo) : RuntimeUpgradeCheckResult
    data class LegacyLink(val release: RuntimeGithubReleaseInfo) : RuntimeUpgradeCheckResult
    data object NoUpdate : RuntimeUpgradeCheckResult
    data class CheckFailed(val message: String? = null) : RuntimeUpgradeCheckResult
}

data class RuntimeUpgradeDownloadProgress(
    val percent: Float,
    val bytesRead: Long,
    val totalBytes: Long,
)

data class RuntimeApkVerificationResult(
    val success: Boolean,
    val reason: String? = null,
)

enum class RuntimePlayAction {
    START_UPDATE_FLOW,
    OPEN_STORE_OR_GITHUB,
    NO_OP,
}

enum class RuntimeStartupTarget {
    PLAY,
    GITHUB,
}

internal fun VersionLog.toRuntime(): RuntimeVersionLog {
    return RuntimeVersionLog(
        name = name,
        code = code,
        desc = desc,
    )
}

internal fun GithubReleaseInfo.toRuntime(): RuntimeGithubReleaseInfo {
    return RuntimeGithubReleaseInfo(
        versionName = versionName,
        htmlUrl = htmlUrl,
    )
}

internal fun UpgradeApkAsset.toRuntime(): RuntimeUpgradeApkAsset {
    return RuntimeUpgradeApkAsset(
        abi = abi,
        downloadUrl = downloadUrl,
        fileSize = fileSize,
        sha256 = sha256,
    )
}

internal fun RuntimeUpgradeApkAsset.toInternal(): UpgradeApkAsset {
    return UpgradeApkAsset(
        abi = abi,
        downloadUrl = downloadUrl,
        fileSize = fileSize,
        sha256 = sha256,
    )
}

internal fun UpgradeInfo.toRuntime(): RuntimeUpgradeInfo {
    return RuntimeUpgradeInfo(
        versionCode = versionCode,
        versionName = versionName,
        htmlUrl = htmlUrl,
        changelog = changelog,
        versionLogs = versionLogs.map(VersionLog::toRuntime),
        apks = apks.map(UpgradeApkAsset::toRuntime),
        signingCertSha256 = signingCertSha256,
    )
}

internal fun UpgradeCheckResult.toRuntime(): RuntimeUpgradeCheckResult {
    return when (this) {
        is UpgradeCheckResult.Structured -> RuntimeUpgradeCheckResult.Structured(info.toRuntime())
        is UpgradeCheckResult.ReleaseLink -> RuntimeUpgradeCheckResult.LegacyLink(release.toRuntime())
        UpgradeCheckResult.NoUpdate -> RuntimeUpgradeCheckResult.NoUpdate
        is UpgradeCheckResult.CheckFailed -> RuntimeUpgradeCheckResult.CheckFailed(message)
    }
}

internal fun UpgradeDownloader.Progress.toRuntime(): RuntimeUpgradeDownloadProgress {
    return RuntimeUpgradeDownloadProgress(
        percent = percent,
        bytesRead = bytesRead,
        totalBytes = totalBytes,
    )
}
