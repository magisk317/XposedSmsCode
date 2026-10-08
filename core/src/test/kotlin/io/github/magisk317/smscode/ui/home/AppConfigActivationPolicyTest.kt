package io.github.magisk317.smscode.ui.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppConfigActivationPolicyTest {

    @Test
    fun reactivation_reusesSnapshotWithoutForcingRefresh() {
        val policy = PageRefreshTriggerConsumer()

        assertEquals(PageRefreshAction.INITIAL_LOAD, policy.consume(isActive = true, refreshTrigger = 0))
        assertNull(policy.consume(isActive = false, refreshTrigger = 0))
        assertEquals(PageRefreshAction.NO_OP, policy.consume(isActive = true, refreshTrigger = 0))
    }

    @Test
    fun refreshTrigger_forcesOnlyOnce() {
        val policy = PageRefreshTriggerConsumer()

        assertEquals(PageRefreshAction.FORCE_REFRESH, policy.consume(isActive = true, refreshTrigger = 1))
        assertEquals(PageRefreshAction.NO_OP, policy.consume(isActive = true, refreshTrigger = 1))
        assertNull(policy.consume(isActive = false, refreshTrigger = 2))
        assertEquals(PageRefreshAction.FORCE_REFRESH, policy.consume(isActive = true, refreshTrigger = 2))
    }

    @Test
    fun latestRequestGeneration_rejectsInvalidatedAndSupersededRequests() {
        val generation = LatestRequestGeneration()
        val first = generation.next()
        val second = generation.next()

        assertFalse(generation.isCurrent(first))
        assertTrue(generation.isCurrent(second))

        generation.invalidate()

        assertFalse(generation.isCurrent(second))
        assertEquals(4L, generation.next())
    }
}
