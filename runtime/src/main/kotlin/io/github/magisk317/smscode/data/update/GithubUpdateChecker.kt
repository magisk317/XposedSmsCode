package io.github.magisk317.smscode.data.update

import io.github.magisk317.smscode.runtime.contract.update.GithubUpdateConfig

/**
 * Update endpoints for XSC.
 *
 * Parsing, version comparison and ABI selection live in core. Callers pass this
 * config to core directly, so there is no per-host wrapper left to keep in sync.
 */
object GithubUpdateConfigHolder {
    val config = GithubUpdateConfig(
        latestReleaseApiUrl = "https://smscode.usdt.edu.kg/repos/magisk3171/XposedSmsCode/releases/latest",
        defaultReleaseHtmlUrl = "https://github.com/magisk3171/XposedSmsCode/releases/latest",
        userAgent = "XposedSmsCode",
    )
}
