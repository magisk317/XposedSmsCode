package io.github.magisk317.smscode.ui.performance

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NavigationPerformanceWiringTest {
    @Test
    fun `page readiness is accepted only for the token target`() {
        val recorder = TopLevelNavigationPerformanceRecorder(clockNanos = { 1L })
        val token = recorder.start("overview", "records", NavigationInput.CLICK)

        assertFalse(isPageReadyForTransition(token, "overview"))
        assertTrue(isPageReadyForTransition(token, "records"))
    }

    @Test
    fun `native swipe starts from pager target while programmatic motion does not`() {
        assertTrue(
            shouldStartUserSwipeTransition(
                isScrollInProgress = true,
                isProgrammaticNavigation = false,
                targetPage = 1,
                settledPage = 0,
            ),
        )
        assertFalse(
            shouldStartUserSwipeTransition(
                isScrollInProgress = true,
                isProgrammaticNavigation = true,
                targetPage = 1,
                settledPage = 0,
            ),
        )
    }

    @Test
    fun `pending deep route wins while other programmatic motion is click`() {
        assertEquals(
            NavigationInput.DEEP_LINK,
            resolveNavigationInput(
                pendingTargetPage = 3,
                pendingInput = NavigationInput.DEEP_LINK,
                observedTargetPage = 3,
                isProgrammaticNavigation = true,
            ),
        )
        assertEquals(
            NavigationInput.CLICK,
            resolveNavigationInput(
                pendingTargetPage = null,
                pendingInput = null,
                observedTargetPage = 2,
                isProgrammaticNavigation = true,
            ),
        )
        assertEquals(
            NavigationInput.SWIPE,
            resolveNavigationInput(
                pendingTargetPage = null,
                pendingInput = null,
                observedTargetPage = 2,
                isProgrammaticNavigation = false,
            ),
        )
    }

    @Test
    fun `completed sample waits for normal first frame but terminal fallback can emit it`() {
        var now = 1L
        val recorder = TopLevelNavigationPerformanceRecorder(clockNanos = { now })
        val token = recorder.start("overview", "settings", NavigationInput.DEEP_LINK)
        now = 2L
        recorder.settled(token)
        now = 3L
        recorder.dataReady(token)
        val result = checkNotNull(recorder.result(token))

        assertFalse(shouldEmitTerminalTransition(result, allowMissingFirstFrame = false))
        assertTrue(shouldEmitTerminalTransition(result, allowMissingFirstFrame = true))
    }
}
