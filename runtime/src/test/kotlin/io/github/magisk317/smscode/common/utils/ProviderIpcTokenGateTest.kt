package io.github.magisk317.smscode.common.utils

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProviderIpcTokenGateTest {
    @Test
    fun evaluate_allowsTrustedCallerRegardlessOfToken() {
        assertTrue(
            ProviderIpcTokenGate.evaluate(
                callerAllowed = true,
                expectedToken = null,
                presentedToken = null,
            ),
        )
        assertTrue(
            ProviderIpcTokenGate.evaluate(
                callerAllowed = true,
                expectedToken = "expected",
                presentedToken = "different",
            ),
        )
    }

    @Test
    fun evaluate_requiresTokenMatchForUntrustedCaller() {
        assertFalse(
            ProviderIpcTokenGate.evaluate(
                callerAllowed = false,
                expectedToken = "expected",
                presentedToken = null,
            ),
        )
        assertFalse(
            ProviderIpcTokenGate.evaluate(
                callerAllowed = false,
                expectedToken = "expected",
                presentedToken = "",
            ),
        )
        assertFalse(
            ProviderIpcTokenGate.evaluate(
                callerAllowed = false,
                expectedToken = "expected",
                presentedToken = "wrong",
            ),
        )
        assertFalse(
            ProviderIpcTokenGate.evaluate(
                callerAllowed = false,
                expectedToken = null,
                presentedToken = "presented",
            ),
        )
        assertTrue(
            ProviderIpcTokenGate.evaluate(
                callerAllowed = false,
                expectedToken = "expected",
                presentedToken = "expected",
            ),
        )
    }

    @Test
    fun evaluate_rejectsOversizedToken() {
        val oversized = "x".repeat(5 * 1024)
        assertFalse(
            ProviderIpcTokenGate.evaluate(
                callerAllowed = false,
                expectedToken = oversized,
                presentedToken = oversized,
            ),
        )
    }
}
