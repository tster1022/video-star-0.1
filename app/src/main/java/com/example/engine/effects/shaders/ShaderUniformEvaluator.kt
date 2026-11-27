package com.example.engine.effects.shaders

import android.graphics.Color
import com.example.model.EffectCatalog
import com.example.model.EffectCatalogItem
import com.example.model.EffectParamType
import com.example.model.ShaderEffectPass
import java.text.DecimalFormat
import kotlin.math.floor
import kotlin.math.roundToInt

data class EvaluatedShaderUniforms(
    val params: FloatArray,      // size 12
    val colors: FloatArray,      // size 12 (3 x vec4 RGBA)
    val modes: IntArray,         // size 4
    val intensity: Float,
    val opacity: Float,
    val effectiveTimeSec: Float,
    val effectiveProgress: Float,
    val legacyParam1: Float,
    val legacyParam2: Float,
    val legacyParam3: Float
)

object ShaderUniformEvaluator {

    fun evaluate(
        pass: ShaderEffectPass,
        progress: Float,
        sceneDurationSec: Float,
        quantizedProgressOverride: Float? = null,
        quantizedTimeOverrideSec: Float? = null
    ): EvaluatedShaderUniforms {
        val effProg = (quantizedProgressOverride ?: progress).coerceIn(0f, 1f)
        val effTime = quantizedTimeOverrideSec ?: (effProg * sceneDurationSec.coerceAtLeast(0.2f))

        val opacity = pass.evaluateOpacityAt(effProg).coerceIn(0f, 1f)
        val intensity = (pass.evaluateIntensityAt(effProg) * opacity).coerceIn(0f, 1f)

        val item: EffectCatalogItem? = EffectCatalog.findById(pass.effectId)
        val pArr = FloatArray(12) { 0f }
        val cArr = FloatArray(12) { 0f }
        // Default 3rd color = 1,1,1,1
        cArr[3] = 1f; cArr[7] = 1f; cArr[11] = 1f
        val mArr = IntArray(4) { 0 }

        var fIdx = 0
        var cIdx = 0
        var mIdx = 0

        item?.params?.forEach { spec ->
            when (spec.paramType) {
                EffectParamType.FLOAT,
                EffectParamType.POINT_2D_X,
                EffectParamType.POINT_2D_Y -> {
                    if (fIdx < 12) {
                        pArr[fIdx++] = pass.evaluateParamAt(
                            key = spec.key,
                            progress = effProg,
                            fallback = spec.defaultValue,
                            minValue = spec.minValue,
                            maxValue = spec.maxValue
                        )
                    }
                }
                EffectParamType.COLOR -> {
                    if (cIdx < 3) {
                        val argb = pass.evaluateColorAt(spec.key, effProg, spec.defaultColorArgb).toInt()
                        val base = cIdx * 4
                        cArr[base] = Color.red(argb) / 255f
                        cArr[base + 1] = Color.green(argb) / 255f
                        cArr[base + 2] = Color.blue(argb) / 255f
                        cArr[base + 3] = Color.alpha(argb) / 255f
                        cIdx++
                    }
                }
                EffectParamType.ENUM_MODE,
                EffectParamType.BOOLEAN -> {
                    if (mIdx < 4) {
                        val v = pass.evaluateParamAt(
                            key = spec.key,
                            progress = effProg,
                            fallback = spec.defaultValue,
                            minValue = spec.minValue,
                            maxValue = spec.maxValue
                        )
                        mArr[mIdx++] = v.roundToInt()
                    }
                }
                EffectParamType.TEXT_STRING -> Unit
            }
        }

        // Legacy uParam1..3 compatibility
        val l1 = item?.params?.getOrNull(0)?.let {
            pass.evaluateParamAt(it.key, effProg, it.defaultValue, it.minValue, it.maxValue)
        } ?: 0.5f
        val l2 = item?.params?.getOrNull(1)?.let {
            pass.evaluateParamAt(it.key, effProg, it.defaultValue, it.minValue, it.maxValue)
        } ?: 0.5f
        val l3 = item?.params?.getOrNull(2)?.let {
            pass.evaluateParamAt(it.key, effProg, it.defaultValue, it.minValue, it.maxValue)
        } ?: 0.5f

        return EvaluatedShaderUniforms(
            params = pArr,
            colors = cArr,
            modes = mArr,
            intensity = intensity,
            opacity = opacity,
            effectiveTimeSec = effTime,
            effectiveProgress = effProg,
            legacyParam1 = l1,
            legacyParam2 = l2,
            legacyParam3 = l3
        )
    }

    /**
     * Evaluates Time Quantization effect if present anywhere before a pass in the stack.
     */
    fun computeQuantizedProgress(pass: ShaderEffectPass, progress: Float, durationSec: Float): Pair<Float, Float> {
        val targetFps = pass.evaluateParamAt("target_fps", progress, 8f, 1f, 30f).coerceIn(1f, 60f)
        val phaseOffset = pass.evaluateParamAt("phase_offset", progress, 0f, 0f, 1f)
        val totalFrames = (durationSec * targetFps).coerceAtLeast(1f)
        val stepped = floor((progress * totalFrames) + phaseOffset) - phaseOffset
        val qProg = (stepped / totalFrames).coerceIn(0f, 1f)
        return qProg to (qProg * durationSec)
    }

    /**
     * Evaluates dynamic string for AM Text effects when rendering Text bitmap textures.
     */
    fun formatTextForEffects(
        baseText: String,
        passes: List<ShaderEffectPass>,
        progress: Float,
        durationSec: Float
    ): String {
        var text = baseText.ifBlank { "STAR STUDIO" }
        for (pass in passes) {
            if (!pass.isEnabled) continue
            when (pass.effectId) {
                "am_text_count" -> {
                    val startV = pass.evaluateParamAt("start_val", progress, 0f, -9999f, 9999f)
                    val endV = pass.evaluateParamAt("end_val", progress, 1000f, -9999f, 99999f)
                    val decimals = pass.evaluateParamAt("decimals", progress, 0f, 0f, 4f).roundToInt().coerceIn(0, 4)
                    val useComma = pass.evaluateParamAt("comma_sep", progress, 1f, 0f, 1f) >= 0.5f
                    val prefix = pass.textParam("prefix", "")
                    val suffix = pass.textParam("suffix", "%")
                    val valNow = startV + (endV - startV) * progress
                    val pattern = buildString {
                        append(if (useComma) "#,##0" else "0")
                        if (decimals > 0) {
                            append('.')
                            repeat(decimals) { append('0') }
                        }
                    }
                    text = prefix + DecimalFormat(pattern).format(valNow) + suffix
                }
                "am_text_progress" -> {
                    val startPct = pass.evaluateParamAt("start_pct", progress, 0f, 0f, 1f)
                    val endPct = pass.evaluateParamAt("end_pct", progress, 1f, 0f, 1f)
                    val cursor = pass.textParam("cursor_char", "|")
                    val blinkSpd = pass.evaluateParamAt("blink_speed", progress, 2f, 0f, 10f)
                    val revealFrac = (startPct + (endPct - startPct) * progress).coerceIn(0f, 1f)
                    val count = (text.length * revealFrac).roundToInt().coerceIn(0, text.length)
                    val showCursor = blinkSpd <= 0.05f || ((progress * durationSec * blinkSpd * 2f).toInt() % 2 == 0)
                    text = text.take(count) + if (showCursor && count < text.length) cursor else ""
                }
                "am_text_randomizer" -> {
                    val reveal = pass.evaluateParamAt("progress", progress, progress, 0f, 1f).coerceIn(0f, 1f)
                    val charSetMode = pass.evaluateParamAt("char_set", progress, 0f, 0f, 2f).roundToInt()
                    val seed = pass.evaluateParamAt("seed", progress, 7f, 0f, 99f).roundToInt()
                    val pool = when (charSetMode) {
                        1 -> "0123456789"
                        2 -> "!@#\$%&*?+-="
                        else -> "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
                    }
                    val lockedCount = (text.length * reveal).roundToInt().coerceIn(0, text.length)
                    val tick = (progress * durationSec * 18f).toInt()
                    text = text.mapIndexed { idx, ch ->
                        if (idx < lockedCount || ch == ' ') ch
                        else pool[kotlin.math.abs(idx * 31 + tick * 17 + seed * 13) % pool.length]
                    }.joinToString("")
                }
                "am_text_timecode" -> {
                    val fmt = pass.evaluateParamAt("tc_format", progress, 0f, 0f, 1f).roundToInt()
                    val offsetSec = pass.evaluateParamAt("offset_sec", progress, 0f, -60f, 60f)
                    val fps = pass.evaluateParamAt("frame_rate", progress, 30f, 12f, 60f).roundToInt().coerceIn(12, 60)
                    val t = (progress * durationSec + offsetSec).coerceAtLeast(0f)
                    val totalSecs = t.toInt()
                    val hours = totalSecs / 3600
                    val mins = (totalSecs % 3600) / 60
                    val secs = totalSecs % 60
                    val frac = t - totalSecs
                    text = if (fmt == 1) {
                        val ms = (frac * 100f).toInt().coerceIn(0, 99)
                        "%02d:%02d.%02d".format(mins, secs, ms)
                    } else {
                        val ff = (frac * fps).toInt().coerceIn(0, fps - 1)
                        "%02d:%02d:%02d:%02d".format(hours, mins, secs, ff)
                    }
                }
            }
        }
        return text
    }
}
