package com.example.engine.render

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Shared render engine coordinator enforcing:
 * 1. Strict < 64 MB byte-weighted cache budgets & layout-switch/teardown trimming.
 * 2. rAF / vsync-paced scrubbing lock with intermediate frame dropping (`isRendering` gate).
 * 3. Crash-free error boundary isolation (`safeRenderEvaluate`) without blocking modals.
 */
object StudioRenderMemoryCoordinator {
    const val MAX_TOTAL_MEMORY_BUDGET_KB = 56 * 1024 // Strictly < 64 MB (56 MB combined budget)
    const val MAX_VIDEO_FRAME_CACHE_KB = 32 * 1024   // 32 MB for video scrub/poster frames
    const val MAX_SHADER_THUMB_CACHE_KB = 16 * 1024  // 16 MB for shader browser ImageBitmaps
    const val MAX_SHADER_SOURCE_CACHE_KB = 8 * 1024  // 8 MB for thumbnail source bitmaps

    private val trimListeners = ConcurrentHashMap<String, (Boolean) -> Unit>()
    private val trimInvocations = AtomicLong(0L)

    fun registerTrimListener(key: String, onTrim: (aggressive: Boolean) -> Unit) {
        trimListeners[key] = onTrim
    }

    fun unregisterTrimListener(key: String) {
        trimListeners.remove(key)
    }

    fun trimTemporaryBuffers(aggressive: Boolean = false) {
        trimInvocations.incrementAndGet()
        trimListeners.values.forEach { listener ->
            runCatching { listener(aggressive) }
        }
    }

    fun trimCount(): Long = trimInvocations.get()

    fun bitmapSizeKb(allocationBytes: Int): Int = (allocationBytes / 1024).coerceAtLeast(1)
}

sealed interface ScrubDispatchDecision {
    data class Dispatch(val targetTimeMs: Long) : ScrubDispatchDecision
    data object DroppedWhileBusy : ScrubDispatchDecision
    data object Throttled : ScrubDispatchDecision
}

/**
 * Decouples high-frequency 120Hz/240Hz finger drag events from heavy GPU / MediaCodec frame decoding.
 * Playhead UI moves immediately while rendering executes behind an `isRendering` gate that drops
 * intermediate frames and retains only the latest requested timestamp.
 */
class StudioScrubThrottleController(
    private val minFrameIntervalMs: Long = 16L
) {
    private val lock = Any()
    private val scrubbing = AtomicBoolean(false)
    private var rendering: Boolean = false
    private var hasPendingFrame: Boolean = false
    private var latestRequestedTimeMs: Long = 0L
    private var lastRenderDispatchNanos: Long = Long.MIN_VALUE / 4
    private var dispatchedCount: Long = 0L
    private var droppedCount: Long = 0L

    val isScrubbing: Boolean
        get() = scrubbing.get()

    fun beginScrub(initialPositionMs: Long = latestRequestedTimeMs) {
        synchronized(lock) {
            scrubbing.set(true)
            latestRequestedTimeMs = initialPositionMs.coerceAtLeast(0L)
        }
    }

    fun endScrub() {
        synchronized(lock) {
            scrubbing.set(false)
        }
    }

    fun requestFrame(
        targetTimeMs: Long,
        nowNanos: Long = System.nanoTime()
    ): ScrubDispatchDecision {
        synchronized(lock) {
            val clamped = targetTimeMs.coerceAtLeast(0L)
            latestRequestedTimeMs = clamped
            if (rendering) {
                hasPendingFrame = true
                droppedCount++
                return ScrubDispatchDecision.DroppedWhileBusy
            }
            val elapsedNs = nowNanos - lastRenderDispatchNanos
            val minIntervalNs = minFrameIntervalMs * 1_000_000L
            if (scrubbing.get() && elapsedNs in 0L until minIntervalNs) {
                hasPendingFrame = true
                droppedCount++
                return ScrubDispatchDecision.Throttled
            }
            rendering = true
            hasPendingFrame = false
            lastRenderDispatchNanos = nowNanos
            dispatchedCount++
            return ScrubDispatchDecision.Dispatch(clamped)
        }
    }

    fun completeFrameRender(): Long? {
        synchronized(lock) {
            rendering = false
            if (!hasPendingFrame) return null
            hasPendingFrame = false
            return latestRequestedTimeMs
        }
    }

    fun reset() {
        synchronized(lock) {
            scrubbing.set(false)
            rendering = false
            hasPendingFrame = false
            lastRenderDispatchNanos = Long.MIN_VALUE / 4
        }
    }

    fun stats(): Pair<Long, Long> = synchronized(lock) { dispatchedCount to droppedCount }
}

/**
 * Pure Kotlin error-boundary guard for rendering calculations & stage evaluation.
 * Recovers to the last known safe value instead of throwing "App Error".
 */
object StudioRenderErrorBoundary {
    private val suppressedErrors = AtomicLong(0L)

    @Volatile
    var lastErrorTag: String? = null
        private set

    inline fun <T> safeEvaluate(tag: String, fallback: T, block: () -> T): T {
        return try {
            block()
        } catch (t: Throwable) {
            recordSuppressedError(tag, t)
            fallback
        }
    }

    fun recordSuppressedError(tag: String, throwable: Throwable) {
        suppressedErrors.incrementAndGet()
        lastErrorTag = tag
        runCatching {
            android.util.Log.w("StudioRenderEngine", "Recovered render fault in [$tag]: ${throwable.message}")
        }
    }

    fun errorCount(): Long = suppressedErrors.get()

    fun resetStatsForTest() {
        suppressedErrors.set(0L)
        lastErrorTag = null
    }
}
