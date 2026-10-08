package io.github.magisk317.smscode.ui.record

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecordSwipeGestureCoordinatorTest {

    @Test
    fun reportsOnlyAggregateHeldStateTransitions() {
        val transitions = mutableListOf<Boolean>()
        val coordinator = RecordSwipeGestureCoordinator(transitions::add)

        coordinator.update("first", true)
        coordinator.update("first", true)
        coordinator.update("second", true)
        coordinator.update("first", false)
        coordinator.update("second", false)

        assertEquals(listOf(true, false), transitions)
    }

    @Test
    fun releaseAll_isPairedAndIdempotent() {
        val transitions = mutableListOf<Boolean>()
        val coordinator = RecordSwipeGestureCoordinator(transitions::add)

        coordinator.update("row", true)
        coordinator.releaseAll()
        coordinator.releaseAll()
        coordinator.update("row", false)

        assertEquals(listOf(true, false), transitions)
    }

    @Test
    fun refreshGeneration_rejectsCancelledJobFinalizers() {
        val generation = RecordRequestGeneration()
        val first = generation.next()
        val second = generation.next()

        assertFalse(generation.isCurrent(first))
        assertTrue(generation.isCurrent(second))

        generation.invalidate()

        assertFalse(generation.isCurrent(second))
    }
}
