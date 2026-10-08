package io.github.magisk317.smscode.ui.performance

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TopLevelNavigationPerformanceRecorderTest {
    private val clock = FakeClock()
    private val recorder = TopLevelNavigationPerformanceRecorder(clockNanos = clock::read)

    @Test
    fun `out of order callbacks retain independent milestone timestamps`() {
        clock.now = millis(10)
        val token = recorder.start("overview", "records", NavigationInput.SWIPE)

        clock.now = millis(50)
        assertTrue(recorder.dataReady(token))
        clock.now = millis(30)
        assertTrue(recorder.firstFrame(token))
        clock.now = millis(40)
        assertTrue(recorder.settled(token))
        clock.now = millis(20)
        assertTrue(recorder.cacheHit(token))

        val result = checkNotNull(recorder.result(token))
        assertEquals(NavigationTransitionStatus.COMPLETED, result.status)
        assertEquals(40, result.durations.totalMillis)
        assertEquals(20, result.durations.firstFrameMillis)
        assertEquals(30, result.durations.settledMillis)
        assertEquals(40, result.durations.dataReadyMillis)
        assertEquals(10, result.durations.cacheHitMillis)
        assertTrue(result.missingMilestones.isEmpty())
    }

    @Test
    fun `explicit cancellation rejects later callbacks`() {
        clock.now = millis(1)
        val token = recorder.start("overview", "settings", NavigationInput.CLICK)
        clock.now = millis(6)

        assertTrue(recorder.cancel(token))
        assertFalse(recorder.firstFrame(token))

        val result = checkNotNull(recorder.result(token))
        assertEquals(NavigationTransitionStatus.CANCELLED, result.status)
        assertEquals(NavigationCancellationReason.EXPLICIT, result.cancellationReason)
        assertEquals(5, result.durations.totalMillis)
        assertFalse(result.cacheHit)
    }

    @Test
    fun `new transition supersedes incomplete current token`() {
        clock.now = millis(1)
        val oldToken = recorder.start("overview", "records", NavigationInput.CLICK)
        clock.now = millis(2)
        recorder.firstFrame(oldToken)
        clock.now = millis(3)
        val currentToken = recorder.start("records", "settings", NavigationInput.DEEP_LINK)

        assertEquals(
            NavigationCancellationReason.SUPERSEDED,
            recorder.result(oldToken)?.cancellationReason,
        )
        assertFalse(recorder.settled(oldToken))
        assertEquals(NavigationTransitionStatus.ACTIVE, recorder.result(currentToken)?.status)
    }

    @Test
    fun `completed transition reports missing optional milestones without losing sample`() {
        clock.now = millis(5)
        val token = recorder.start("overview", "records", NavigationInput.CLICK)
        clock.now = millis(15)
        recorder.settled(token)
        clock.now = millis(25)
        recorder.dataReady(token)

        val result = checkNotNull(recorder.result(token))
        assertEquals(NavigationTransitionStatus.COMPLETED, result.status)
        assertEquals(
            listOf(NavigationMilestone.FIRST_FRAME, NavigationMilestone.CACHE_HIT),
            result.missingMilestones,
        )
        assertNull(result.durations.firstFrameMillis)
        assertEquals(1, recorder.report().metrics.endToEnd.sampleCount)
        assertEquals(0, recorder.report().metrics.firstFrame.sampleCount)
    }

    @Test
    fun `aggregate uses nearest rank P50 P95 and P99`() {
        (1L..100L).forEach { durationMillis ->
            val startMillis = durationMillis * 1_000L
            clock.now = millis(startMillis)
            val token = recorder.start("overview", "records", NavigationInput.CLICK)
            clock.now = millis(startMillis + durationMillis)
            recorder.firstFrame(token)
            recorder.settled(token)
            recorder.dataReady(token)
        }

        val report = recorder.report()
        assertEquals(100, report.completedTransitions)
        assertEquals(50, report.metrics.endToEnd.p50Millis)
        assertEquals(95, report.metrics.endToEnd.p95Millis)
        assertEquals(99, report.metrics.endToEnd.p99Millis)
        assertEquals(report.metrics.endToEnd, report.metrics.firstFrame)
    }

    @Test
    fun `duplicate and pre-start timestamps are rejected`() {
        clock.now = millis(10)
        val token = recorder.start("overview", "records", NavigationInput.CLICK)
        clock.now = millis(9)
        assertFalse(recorder.firstFrame(token))
        clock.now = millis(11)
        assertTrue(recorder.firstFrame(token))
        clock.now = millis(12)
        assertFalse(recorder.firstFrame(token))

        assertEquals(1, recorder.result(token)?.durations?.firstFrameMillis)
    }

    @Test
    fun `result and aggregate report serialize and remain single line log friendly`() {
        clock.now = millis(10)
        val token = recorder.start("overview", "records", NavigationInput.DEEP_LINK)
        clock.now = millis(12)
        recorder.firstFrame(token)
        recorder.cacheHit(token)
        clock.now = millis(15)
        recorder.settled(token)
        recorder.dataReady(token)

        val result = checkNotNull(recorder.result(token))
        val resultJson = Json.encodeToString(result)
        val reportJson = Json.encodeToString(recorder.report())

        assertTrue(resultJson.contains("\"source\":\"overview\""))
        assertTrue(resultJson.contains("\"cacheHit\":true"))
        assertTrue(reportJson.contains("\"p95Millis\":5"))
        assertTrue(result.toLogLine().startsWith("ui.navigation token=nav-1"))
        assertFalse(result.toLogLine().contains('\n'))
        assertFalse(recorder.report().toLogLine().contains('\n'))
        assertEquals(
            setOf(
                "token",
                "source_page",
                "target_page",
                "input_kind",
                "result",
                "process",
                "stage",
                "cache_hit",
                "missing_milestones",
                "duration_ms",
                "first_frame_ms",
                "settled_ms",
                "data_ready_ms",
                "cache_hit_ms",
            ),
            result.toTelemetryAttributes().keys,
        )
    }

    @Test
    fun `parameterized route IDs are rejected before entering logs`() {
        clock.now = 1
        val error = runCatching {
            recorder.start("overview", "records/secret-id?query=value", NavigationInput.DEEP_LINK)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals(0, recorder.report().totalTransitions)
    }

    private class FakeClock(var now: Long = 0L) {
        fun read(): Long = now
    }

    private companion object {
        private const val NANOS_PER_MILLISECOND = 1_000_000L

        private fun millis(value: Long): Long = value * NANOS_PER_MILLISECOND
    }
}
