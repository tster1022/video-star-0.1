package com.example.model

import kotlinx.serialization.Serializable
import kotlin.math.abs

const val PARAM_KEY_INTENSITY = "__intensity__"
const val PARAM_KEY_OPACITY = "__opacity__"

/**
 * Easing curve options for per-parameter keyframe interpolation.
 */
@Serializable
enum class KeyframeEasing(
    val id: String,
    val arabicLabel: String
) {
    LINEAR("linear", "خطي"),
    EASE_IN("ease_in", "دخول تدريجي"),
    EASE_OUT("ease_out", "خروج تدريجي"),
    EASE_IN_OUT("ease_in_out", "ناعم"),
    BOUNCE("bounce", "ارتداد"),
    STEP("step", "قفز فوري");

    fun evaluate(progress: Float): Float {
        val x = progress.coerceIn(0f, 1f)
        return when (this) {
            LINEAR -> x
            EASE_IN -> BezierCurveSpec(0.42f, 0f, 1f, 1f).evaluate(x)
            EASE_OUT -> BezierCurveSpec(0f, 0f, 0.58f, 1f).evaluate(x)
            EASE_IN_OUT -> BezierCurveSpec(0.42f, 0f, 0.58f, 1f).evaluate(x)
            BOUNCE -> BezierCurveSpec(0.34f, 1.38f, 0.64f, 1f).evaluate(x)
            STEP -> if (x >= 1f) 1f else 0f
        }
    }

    fun toBezierCurveSpec(): BezierCurveSpec = when (this) {
        LINEAR -> BezierCurveSpec(0f, 0f, 1f, 1f)
        EASE_IN -> BezierCurveSpec(0.42f, 0f, 1f, 1f)
        EASE_OUT -> BezierCurveSpec(0f, 0f, 0.58f, 1f)
        EASE_IN_OUT -> BezierCurveSpec(0.42f, 0f, 0.58f, 1f)
        BOUNCE -> BezierCurveSpec(0.34f, 1.38f, 0.64f, 1f)
        STEP -> BezierCurveSpec(1f, 0f, 1f, 0f)
    }
}

/**
 * Stores a single {time, value, easing} keyframe for any numeric effect parameter.
 * [time] is normalized in 0f..1f relative to the scene/layer duration.
 * [easing] governs interpolation from this keyframe to the next keyframe.
 */
@Serializable
data class ParameterKeyframe(
    val id: String,
    val time: Float,
    val value: Float,
    val easing: KeyframeEasing = KeyframeEasing.LINEAR
)

/**
 * Evaluates a sorted or unsorted list of [ParameterKeyframe]s at normalized [progress] (0f..1f).
 */
fun interpolateParameterKeyframes(
    keyframes: List<ParameterKeyframe>,
    progress: Float,
    staticValue: Float,
    minValue: Float = -Float.MAX_VALUE,
    maxValue: Float = Float.MAX_VALUE
): Float {
    if (keyframes.isEmpty()) return staticValue.coerceIn(minValue, maxValue)
    val sorted = keyframes.sortedBy { it.time }
    val p = progress.coerceIn(0f, 1f)
    if (sorted.size == 1 || p <= sorted.first().time) {
        return sorted.first().value.coerceIn(minValue, maxValue)
    }
    if (p >= sorted.last().time) {
        return sorted.last().value.coerceIn(minValue, maxValue)
    }
    val nextIdx = sorted.indexOfFirst { it.time >= p }.coerceAtLeast(1)
    val prev = sorted[nextIdx - 1]
    val next = sorted[nextIdx]
    if (abs(next.time - p) <= 0.0001f) {
        return next.value.coerceIn(minValue, maxValue)
    }
    val span = (next.time - prev.time).takeIf { abs(it) > 0.0001f } ?: 1f
    val rawT = ((p - prev.time) / span).coerceIn(0f, 1f)
    val easedT = prev.easing.evaluate(rawT)
    return (prev.value + (next.value - prev.value) * easedT).coerceIn(minValue, maxValue)
}
