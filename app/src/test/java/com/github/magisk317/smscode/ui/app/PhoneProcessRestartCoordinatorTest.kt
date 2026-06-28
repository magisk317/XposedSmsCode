package com.github.magisk317.smscode.ui.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhoneProcessRestartCoordinatorTest {

    @Test
    fun parseRestartCommandSummary_readsShellCounters() {
        assertEquals(
            PhoneProcessRestartCoordinator.RestartCommandSummary(found = 2, killed = 2, failed = 0),
            PhoneProcessRestartCoordinator.parseRestartCommandSummary("found=2 killed=2 failed=0\n"),
        )
    }

    @Test
    fun parseRestartCommandSummary_ignoresUnexpectedOutput() {
        assertNull(PhoneProcessRestartCoordinator.parseRestartCommandSummary("su: inaccessible or not found"))
    }

    @Test
    fun restartAttemptResult_marksHandledOnlyAfterSuccessfulSummary() {
        assertTrue(
            PhoneProcessRestartCoordinator.RestartAttemptResult(
                exitCode = 0,
                summary = PhoneProcessRestartCoordinator.RestartCommandSummary(found = 1, killed = 1, failed = 0),
                rawOutput = "found=1 killed=1 failed=0\n",
            ).shouldMarkInstallHandled,
        )

        assertFalse(
            PhoneProcessRestartCoordinator.RestartAttemptResult(
                exitCode = 0,
                summary = null,
                rawOutput = "",
            ).shouldMarkInstallHandled,
        )
        assertFalse(
            PhoneProcessRestartCoordinator.RestartAttemptResult(
                exitCode = 1,
                summary = PhoneProcessRestartCoordinator.RestartCommandSummary(found = 1, killed = 0, failed = 1),
                rawOutput = "found=1 killed=0 failed=1\n",
            ).shouldMarkInstallHandled,
        )
    }
}
