package io.github.magisk317.smscode.common.utils

import android.content.Context
import android.os.SystemClock
import io.github.magisk317.smscode.runtime.BuildConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The hook boundary must follow the same distribution switch the app process
 * follows. Distributions that ship the gate off never publish a signed lease, so
 * a hook that still demands one blocks every SMS code with no way for the user
 * to clear it.
 *
 * The gate flag is baked in per flavor, so each case only runs on the flavor it
 * describes.
 */
class HookPrefsReaderEntitlementGateTest {

    @BeforeEach
    fun setUpClock() {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 1_000L
    }

    @BeforeEach
    fun setUp() {
        // No published entitlement state: the remote provider has never served
        // an always-allowed snapshot or a lease.
        HookPrefsReader.setRemotePrefsProvider { null }
        HookPrefsReader.invalidateCache()
    }

    @AfterEach
    fun tearDown() {
        HookPrefsReader.setRemotePrefsProvider(null)
        HookPrefsReader.invalidateCache()
        unmockkStatic(SystemClock::class)
    }

    @Test
    fun `sideload distributions fail closed without a published lease`() {
        assumeTrue(BuildConfig.ENABLE_MOBILE_ENTITLEMENT)
        val context = mockk<Context>(relaxed = true)

        assertFalse(HookPrefsReader.mobileAutomationAllowed(context))
    }

    @Test
    fun `gate-off distributions stay open without a published lease`() {
        assumeFalse(BuildConfig.ENABLE_MOBILE_ENTITLEMENT)
        val context = mockk<Context>(relaxed = true)

        assertTrue(HookPrefsReader.mobileAutomationAllowed(context))
    }
}
