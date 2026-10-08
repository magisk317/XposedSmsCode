package io.github.magisk317.smscode.ui.performance

import kotlin.math.ceil
import kotlinx.serialization.Serializable

/** User action that requested a top-level navigation transition. */
@Serializable
enum class NavigationInput {
    CLICK,
    SWIPE,
    DEEP_LINK,
}

/** Observable milestones for a single top-level transition. */
@Serializable
enum class NavigationMilestone {
    START,
    FIRST_FRAME,
    SETTLED,
    DATA_READY,
    CACHE_HIT,
}

@Serializable
enum class NavigationTransitionStatus {
    ACTIVE,
    COMPLETED,
    CANCELLED,
}

@Serializable
enum class NavigationCancellationReason {
    EXPLICIT,
    SUPERSEDED,
}

/**
 * Stable identity carried by MainScreen and the destination page.
 *
 * Route IDs intentionally accept only short, parameter-free names. This keeps tokens and reports
 * safe to attach to diagnostics without leaking deep-link arguments or user content.
 */
@Serializable
@ConsistentCopyVisibility
data class NavigationTransitionToken internal constructor(
    val id: String,
    val source: String,
    val target: String,
    val input: NavigationInput,
    val startedAtNanos: Long,
)

@Serializable
data class NavigationTransitionDurations(
    val totalMillis: Long? = null,
    val firstFrameMillis: Long? = null,
    val settledMillis: Long? = null,
    val dataReadyMillis: Long? = null,
    val cacheHitMillis: Long? = null,
)

/** Immutable, serialization-friendly snapshot for one transition. */
@Serializable
data class NavigationTransitionResult(
    val token: NavigationTransitionToken,
    val status: NavigationTransitionStatus,
    val durations: NavigationTransitionDurations,
    val cacheHit: Boolean,
    val missingMilestones: List<NavigationMilestone>,
    val cancellationReason: NavigationCancellationReason? = null,
) {
    /**
     * Fixed, privacy-safe attributes for the app shell's telemetry adapter.
     *
     * Monotonic timestamps and deep-link values are intentionally absent. [NavigationTransitionToken]
     * already guarantees that source and target are parameter-free route IDs.
     */
    fun toTelemetryAttributes(): Map<String, String> = buildMap {
        put("token", token.id)
        put("source_page", token.source)
        put("target_page", token.target)
        put("input_kind", token.input.logName())
        put("result", status.logName())
        put("process", "app")
        put("stage", "top_level_transition")
        put("cache_hit", cacheHit.toString())
        put("missing_milestones", missingMilestones.joinToString(separator = ",") { it.logName() })
        durations.totalMillis?.let { put("duration_ms", it.toString()) }
        durations.firstFrameMillis?.let { put("first_frame_ms", it.toString()) }
        durations.settledMillis?.let { put("settled_ms", it.toString()) }
        durations.dataReadyMillis?.let { put("data_ready_ms", it.toString()) }
        durations.cacheHitMillis?.let { put("cache_hit_ms", it.toString()) }
        cancellationReason?.let { put("cancellation_reason", it.logName()) }
    }

    /** A bounded, single-line representation suitable for local logs or an OTEL adapter. */
    fun toLogLine(): String = buildString {
        append("ui.navigation")
        append(" token=").append(token.id)
        append(" source=").append(token.source)
        append(" target=").append(token.target)
        append(" input=").append(token.input.logName())
        append(" status=").append(status.logName())
        append(" duration_ms=").append(durations.totalMillis.logValue())
        append(" first_frame_ms=").append(durations.firstFrameMillis.logValue())
        append(" settled_ms=").append(durations.settledMillis.logValue())
        append(" data_ready_ms=").append(durations.dataReadyMillis.logValue())
        append(" cache_hit=").append(cacheHit)
        append(" cache_hit_ms=").append(durations.cacheHitMillis.logValue())
        append(" cancellation=").append(cancellationReason?.logName() ?: MISSING_LOG_VALUE)
        append(" missing=").append(
            missingMilestones.joinToString(separator = ",") { it.logName() }
                .ifEmpty { MISSING_LOG_VALUE },
        )
    }
}

@Serializable
data class NavigationPercentiles(
    val sampleCount: Int,
    val p50Millis: Long? = null,
    val p95Millis: Long? = null,
    val p99Millis: Long? = null,
) {
    internal fun toLogValue(): String =
        "n=$sampleCount,p50=${p50Millis.logValue()},p95=${p95Millis.logValue()},p99=${p99Millis.logValue()}"
}

@Serializable
data class NavigationPerformanceMetrics(
    val endToEnd: NavigationPercentiles,
    val firstFrame: NavigationPercentiles,
    val settled: NavigationPercentiles,
    val dataReady: NavigationPercentiles,
    val cacheHit: NavigationPercentiles,
)

@Serializable
data class NavigationMissingMilestoneCount(
    val milestone: NavigationMilestone,
    val count: Int,
)

/** Aggregate snapshot over the recorder's bounded in-memory history. */
@Serializable
data class NavigationPerformanceReport(
    val totalTransitions: Int,
    val completedTransitions: Int,
    val cancelledTransitions: Int,
    val activeTransitions: Int,
    val cacheHitTransitions: Int,
    val metrics: NavigationPerformanceMetrics,
    val missingMilestones: List<NavigationMissingMilestoneCount>,
) {
    /** Contains only fixed keys, counters, and durations; raw route IDs are deliberately omitted. */
    fun toLogLine(): String = buildString {
        append("ui.navigation.report")
        append(" total=").append(totalTransitions)
        append(" completed=").append(completedTransitions)
        append(" cancelled=").append(cancelledTransitions)
        append(" active=").append(activeTransitions)
        append(" cache_hits=").append(cacheHitTransitions)
        append(" end_to_end={").append(metrics.endToEnd.toLogValue()).append('}')
        append(" first_frame={").append(metrics.firstFrame.toLogValue()).append('}')
        append(" settled={").append(metrics.settled.toLogValue()).append('}')
        append(" data_ready={").append(metrics.dataReady.toLogValue()).append('}')
        append(" cache_hit={").append(metrics.cacheHit.toLogValue()).append('}')
        append(" missing={")
        append(missingMilestones.joinToString(separator = ",") { "${it.milestone.logName()}:${it.count}" })
        append('}')
    }
}

/**
 * Thread-safe, in-memory performance recorder for top-level navigation.
 *
 * A transition is complete when both [settled] and [dataReady] have been observed. First frame
 * and cache hit remain independently reportable, so instrumentation gaps do not discard an
 * otherwise valid end-to-end sample. Starting a new transition atomically cancels an incomplete
 * current transition with [NavigationCancellationReason.SUPERSEDED].
 *
 * The class deliberately performs no Android, logging, persistence, or telemetry I/O. Callers can
 * turn [result] into one production event and periodically serialize or log [report].
 */
class TopLevelNavigationPerformanceRecorder(
    private val maxRetainedTransitions: Int = DEFAULT_MAX_RETAINED_TRANSITIONS,
    private val clockNanos: () -> Long = System::nanoTime,
) {
    init {
        require(maxRetainedTransitions > 0) { "maxRetainedTransitions must be positive" }
    }

    private val lock = Any()
    private val states = LinkedHashMap<String, MutableTransitionState>()
    private var nextSequence = 0L
    private var currentTokenId: String? = null

    fun start(source: String, target: String, input: NavigationInput): NavigationTransitionToken {
        val safeSource = routeId(source)
        val safeTarget = routeId(target)
        return synchronized(lock) {
            val timestampNanos = clockNanos()
            currentTokenId?.let { currentId ->
                states[currentId]?.let { current ->
                    if (current.status() == NavigationTransitionStatus.ACTIVE) {
                        current.cancel(
                            reason = NavigationCancellationReason.SUPERSEDED,
                            timestampNanos = timestampNanos,
                        )
                    }
                }
            }
            currentTokenId = null
            trimForInsertion()

            nextSequence += 1
            val token = NavigationTransitionToken(
                id = "$TOKEN_PREFIX$nextSequence",
                source = safeSource,
                target = safeTarget,
                input = input,
                startedAtNanos = timestampNanos,
            )
            states[token.id] = MutableTransitionState(token)
            currentTokenId = token.id
            token
        }
    }

    fun firstFrame(token: NavigationTransitionToken): Boolean =
        mark(token, NavigationMilestone.FIRST_FRAME)

    fun settled(token: NavigationTransitionToken): Boolean =
        mark(token, NavigationMilestone.SETTLED)

    fun dataReady(token: NavigationTransitionToken): Boolean =
        mark(token, NavigationMilestone.DATA_READY)

    fun cacheHit(token: NavigationTransitionToken): Boolean =
        mark(token, NavigationMilestone.CACHE_HIT)

    fun cancel(token: NavigationTransitionToken): Boolean = synchronized(lock) {
        val state = currentState(token) ?: return@synchronized false
        if (state.status() != NavigationTransitionStatus.ACTIVE) return@synchronized false
        state.cancel(NavigationCancellationReason.EXPLICIT, clockNanos())
        true
    }

    fun result(token: NavigationTransitionToken): NavigationTransitionResult? = synchronized(lock) {
        states[token.id]
            ?.takeIf { it.token == token }
            ?.toResult()
    }

    fun report(): NavigationPerformanceReport = synchronized(lock) {
        val results = states.values.map { it.toResult() }
        val completed = results.filter { it.status == NavigationTransitionStatus.COMPLETED }
        NavigationPerformanceReport(
            totalTransitions = results.size,
            completedTransitions = completed.size,
            cancelledTransitions = results.count { it.status == NavigationTransitionStatus.CANCELLED },
            activeTransitions = results.count { it.status == NavigationTransitionStatus.ACTIVE },
            cacheHitTransitions = results.count { it.cacheHit },
            metrics = NavigationPerformanceMetrics(
                endToEnd = percentiles(completed.mapNotNull { it.durations.totalMillis }),
                firstFrame = percentiles(completed.mapNotNull { it.durations.firstFrameMillis }),
                settled = percentiles(completed.mapNotNull { it.durations.settledMillis }),
                dataReady = percentiles(completed.mapNotNull { it.durations.dataReadyMillis }),
                cacheHit = percentiles(completed.mapNotNull { it.durations.cacheHitMillis }),
            ),
            missingMilestones = NavigationMilestone.entries.map { milestone ->
                NavigationMissingMilestoneCount(
                    milestone = milestone,
                    count = results.count { milestone in it.missingMilestones },
                )
            },
        )
    }

    private fun mark(token: NavigationTransitionToken, milestone: NavigationMilestone): Boolean =
        synchronized(lock) {
            val state = currentState(token) ?: return@synchronized false
            state.mark(milestone, clockNanos())
        }

    private fun currentState(token: NavigationTransitionToken): MutableTransitionState? {
        if (currentTokenId != token.id) return null
        return states[token.id]?.takeIf { it.token == token }
    }

    private fun trimForInsertion() {
        while (states.size >= maxRetainedTransitions) {
            val oldest = states.entries.firstOrNull() ?: return
            states.remove(oldest.key)
        }
    }

    private class MutableTransitionState(val token: NavigationTransitionToken) {
        private val milestones = linkedMapOf(NavigationMilestone.START to token.startedAtNanos)
        private var cancelledAtNanos: Long? = null
        private var cancellationReason: NavigationCancellationReason? = null

        fun mark(milestone: NavigationMilestone, timestampNanos: Long): Boolean {
            if (milestone == NavigationMilestone.START || cancellationReason != null) return false
            if (timestampNanos < token.startedAtNanos || milestone in milestones) return false
            milestones[milestone] = timestampNanos
            return true
        }

        fun cancel(reason: NavigationCancellationReason, timestampNanos: Long) {
            cancellationReason = reason
            cancelledAtNanos = timestampNanos.coerceAtLeast(token.startedAtNanos)
        }

        fun status(): NavigationTransitionStatus = when {
            cancellationReason != null -> NavigationTransitionStatus.CANCELLED
            NavigationMilestone.SETTLED in milestones && NavigationMilestone.DATA_READY in milestones ->
                NavigationTransitionStatus.COMPLETED
            else -> NavigationTransitionStatus.ACTIVE
        }

        fun toResult(): NavigationTransitionResult {
            val status = status()
            val completedAtNanos = if (status == NavigationTransitionStatus.COMPLETED) {
                maxOf(
                    checkNotNull(milestones[NavigationMilestone.SETTLED]),
                    checkNotNull(milestones[NavigationMilestone.DATA_READY]),
                )
            } else {
                null
            }
            return NavigationTransitionResult(
                token = token,
                status = status,
                durations = NavigationTransitionDurations(
                    totalMillis = (completedAtNanos ?: cancelledAtNanos)?.elapsedMillisFromStart(),
                    firstFrameMillis = milestones[NavigationMilestone.FIRST_FRAME]?.elapsedMillisFromStart(),
                    settledMillis = milestones[NavigationMilestone.SETTLED]?.elapsedMillisFromStart(),
                    dataReadyMillis = milestones[NavigationMilestone.DATA_READY]?.elapsedMillisFromStart(),
                    cacheHitMillis = milestones[NavigationMilestone.CACHE_HIT]?.elapsedMillisFromStart(),
                ),
                cacheHit = NavigationMilestone.CACHE_HIT in milestones,
                missingMilestones = NavigationMilestone.entries.filterNot { it in milestones },
                cancellationReason = cancellationReason,
            )
        }

        private fun Long.elapsedMillisFromStart(): Long =
            (this - token.startedAtNanos).coerceAtLeast(0L) / NANOS_PER_MILLISECOND
    }

    companion object {
        const val DEFAULT_MAX_RETAINED_TRANSITIONS = 256
        const val MAX_ROUTE_ID_LENGTH = 64
        private const val TOKEN_PREFIX = "nav-"
        private const val NANOS_PER_MILLISECOND = 1_000_000L
        private val ROUTE_ID_PATTERN = Regex("[A-Za-z][A-Za-z0-9_.-]{0,${MAX_ROUTE_ID_LENGTH - 1}}")

        private fun routeId(value: String): String {
            val routeId = value.trim()
            require(ROUTE_ID_PATTERN.matches(routeId)) {
                "route IDs must be parameter-free names of at most $MAX_ROUTE_ID_LENGTH characters"
            }
            return routeId
        }

        private fun percentiles(values: List<Long>): NavigationPercentiles {
            if (values.isEmpty()) return NavigationPercentiles(sampleCount = 0)
            val sorted = values.sorted()
            return NavigationPercentiles(
                sampleCount = sorted.size,
                p50Millis = sorted.nearestRank(P50),
                p95Millis = sorted.nearestRank(P95),
                p99Millis = sorted.nearestRank(P99),
            )
        }

        private fun List<Long>.nearestRank(percentile: Double): Long {
            val rank = ceil(size * percentile).toInt().coerceIn(1, size)
            return this[rank - 1]
        }

        private const val P50 = 0.50
        private const val P95 = 0.95
        private const val P99 = 0.99
    }
}

private const val MISSING_LOG_VALUE = "-"

private fun Long?.logValue(): String = this?.toString() ?: MISSING_LOG_VALUE

private fun Enum<*>.logName(): String = name.lowercase()
