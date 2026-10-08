package io.github.magisk317.smscode.data.update

import io.github.magisk317.smscode.runtime.common.update.UpdatePolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdatePolicyTest {

    @Test
    fun shouldRunAutoCheck_respectsWifiConstraint() {
        assertTrue(UpdatePolicy.shouldRunAutoCheck(enabled = true, wifiOnly = false, onWifi = false))
        assertTrue(UpdatePolicy.shouldRunAutoCheck(enabled = true, wifiOnly = true, onWifi = true))
        assertFalse(UpdatePolicy.shouldRunAutoCheck(enabled = true, wifiOnly = true, onWifi = false))
        assertFalse(UpdatePolicy.shouldRunAutoCheck(enabled = false, wifiOnly = false, onWifi = true))
    }

    @Test
    fun resolveStartupTarget_prefersPlayWhenInstalledFromPlay() {
        assertEquals(UpdatePolicy.StartupTarget.PLAY, UpdatePolicy.resolveStartupTarget(installedFromPlay = true))
        assertEquals(UpdatePolicy.StartupTarget.GITHUB, UpdatePolicy.resolveStartupTarget(installedFromPlay = false))
    }

    @Test
    fun shouldSkipGithubCheckOnStartup_coversMainBranches() {
        assertTrue(
            UpdatePolicy.shouldSkipGithubCheckOnStartup(
                installedFromPlay = true,
                autoCheckEnabled = true,
                wifiOnly = false,
                onWifi = true,
            ),
        )
        assertTrue(
            UpdatePolicy.shouldSkipGithubCheckOnStartup(
                installedFromPlay = false,
                autoCheckEnabled = false,
                wifiOnly = false,
                onWifi = true,
            ),
        )
        assertTrue(
            UpdatePolicy.shouldSkipGithubCheckOnStartup(
                installedFromPlay = false,
                autoCheckEnabled = true,
                wifiOnly = true,
                onWifi = false,
            ),
        )
        assertFalse(
            UpdatePolicy.shouldSkipGithubCheckOnStartup(
                installedFromPlay = false,
                autoCheckEnabled = true,
                wifiOnly = true,
                onWifi = true,
            ),
        )
    }

    @Test
    fun shouldSkipIgnoredVersion_respectsFlagAndExactMatch() {
        assertTrue(UpdatePolicy.shouldSkipIgnoredVersion(true, "3.1.1", "3.1.1"))
        assertFalse(UpdatePolicy.shouldSkipIgnoredVersion(false, "3.1.1", "3.1.1"))
        assertFalse(UpdatePolicy.shouldSkipIgnoredVersion(true, "3.1.1", "3.1.2"))
    }
}
