package com.example.model

import android.graphics.Color as AndroidColor
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt

const val PARAM_KEY_INTENSITY = "__intensity__"
const val PARAM_KEY_OPACITY = "__opacity__"

/**
 * Easing curve options for per-parameter keyframe interpolation:
 * Supports Linear, Cubic Bezier presets & custom bezier curves, and Step (Hold).
 */
@Serializable
enum class KeyframeEasing(
    val id: String,
    val arabicLabel: String,
    val englishLabel: String = arabicLabel
) {
    LINEAR("linear", "خطي", "Linear"),
    CUBIC_BEZIER("cubic_bezier", "بيزيه مخصص", "Cubic Bezier"),
    EASE_IN("ease_in", "دخول تدريجي", "Ease In"),
    EASE_OUT("ease_out", "خروج تدريجي", "Ease Out"),
    EASE_IN_OUT("ease_in_out", "ناعم", "Ease In-Out"),
    BOUNCE("bounce", "ارتداد", "Bounce"),
    STEP("step", "تثبيت", "Hold");

    fun evaluate(progress: Float, customCurve: BezierCurveSpec? = null): Float {
        val x = progress.coerceIn(0f, 1f)
        return when (this) {
            LINEAR -> x
            CUBIC_BEZIER -> (customCurve ?: BezierCurveSpec(0.35f, 0f, 0.25f, 1f)).evaluate(x)
            EASE_IN -> BezierCurveSpec(0.42f, 0f, 1f, 1f).evaluate(x)
            EASE_OUT -> BezierCurveSpec(0f, 0f, 0.58f, 1f).evaluate(x)
            EASE_IN_OUT -> BezierCurveSpec(0.42f, 0f, 0.58f, 1f).evaluate(x)
            BOUNCE -> BezierCurveSpec(0.34f, 1.38f, 0.64f, 1f).evaluate(x)
            STEP -> if (x >= 1f) 1f else 0f
        }
    }

    fun toBezierCurveSpec(customCurve: BezierCurveSpec? = null): BezierCurveSpec = when (this) {
        LINEAR -> BezierCurveSpec(0f, 0f, 1f, 1f)
        CUBIC_BEZIER -> customCurve ?: BezierCurveSpec(0.35f, 0f, 0.25f, 1f)
        EASE_IN -> BezierCurveSpec(0.42f, 0f, 1f, 1f)
        EASE_OUT -> BezierCurveSpec(0f, 0f, 0.58f, 1f)
        EASE_IN_OUT -> BezierCurveSpec(0.42f, 0f, 0.58f, 1f)
        BOUNCE -> BezierCurveSpec(0.34f, 1.38f, 0.64f, 1f)
        STEP -> BezierCurveSpec(1f, 0f, 1f, 0f)
    }
}

/**
 * Stores a single {time, value, easing, customCurve, colorArgb} keyframe for any effect parameter.
 * [time] is normalized in 0f..1f relative to the scene/layer duration.
 * [easing] governs interpolation from this keyframe to the next keyframe.
 */
@Serializable
data class ParameterKeyframe(
    val id: String,
    val time: Float,
    val value: Float,
    val easing: KeyframeEasing = KeyframeEasing.LINEAR,
    val customBezier: BezierCurveSpec? = null,
    val colorArgb: Long? = null
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
    val easedT = prev.easing.evaluate(rawT, prev.customBezier)
    return (prev.value + (next.value - prev.value) * easedT).coerceIn(minValue, maxValue)
}

/**
 * Evaluates color keyframes cleanly across ARGB channels using Linear / Cubic Bezier / Hold curves.
 */
fun interpolateColorKeyframes(
    keyframes: List<ParameterKeyframe>,
    progress: Float,
    staticArgb: Long
): Long {
    if (keyframes.isEmpty()) return staticArgb
    val sorted = keyframes.sortedBy { it.time }
    val p = progress.coerceIn(0f, 1f)
    val firstColor = sorted.first().colorArgb ?: staticArgb
    if (sorted.size == 1 || p <= sorted.first().time) return firstColor
    val lastColor = sorted.last().colorArgb ?: staticArgb
    if (p >= sorted.last().time) return lastColor

    val nextIdx = sorted.indexOfFirst { it.time >= p }.coerceAtLeast(1)
    val prev = sorted[nextIdx - 1]
    val next = sorted[nextIdx]
    val c1 = (prev.colorArgb ?: staticArgb).toInt()
    val c2 = (next.colorArgb ?: staticArgb).toInt()
    if (abs(next.time - p) <= 0.0001f) return next.colorArgb ?: staticArgb

    val span = (next.time - prev.time).takeIf { abs(it) > 0.0001f } ?: 1f
    val rawT = ((p - prev.time) / span).coerceIn(0f, 1f)
    val t = prev.easing.evaluate(rawT, prev.customBezier).coerceIn(0f, 1f)

    val a = (AndroidColor.alpha(c1) + (AndroidColor.alpha(c2) - AndroidColor.alpha(c1)) * t).roundToInt().coerceIn(0, 255)
    val r = (AndroidColor.red(c1) + (AndroidColor.red(c2) - AndroidColor.red(c1)) * t).roundToInt().coerceIn(0, 255)
    val g = (AndroidColor.green(c1) + (AndroidColor.green(c2) - AndroidColor.green(c1)) * t).roundToInt().coerceIn(0, 255)
    val b = (AndroidColor.blue(c1) + (AndroidColor.blue(c2) - AndroidColor.blue(c1)) * t).roundToInt().coerceIn(0, 255)
    return AndroidColor.argb(a, r, g, b).toLong() and 0xFFFFFFFFL
}
