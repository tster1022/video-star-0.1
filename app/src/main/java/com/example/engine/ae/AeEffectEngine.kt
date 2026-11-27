package com.example.engine.ae

import com.example.engine.effects.shaders.DistortionWarpShaders
import com.example.engine.effects.shaders.MatteMaskKeyShaders
import com.example.engine.effects.shaders.MoveTransformShaders
import com.example.engine.effects.shaders.OpacityVisibilityShaders
import com.example.engine.effects.shaders.ProceduralShaders
import com.example.engine.effects.shaders.RepeatShaders
import com.example.engine.effects.shaders.ShaderCommonGlsl
import com.example.engine.effects.shaders.TextAndOtherShaders
import com.example.engine.effects.shaders.ThreeDShaders
import com.example.model.EditorLayer
import com.example.model.ShaderEffectPass
import kotlin.math.abs

data class AeEvaluatedLayerTransform(
    val offsetX: Float,
    val offsetY: Float,
    val scale: Float,
    val rotation: Float,
    val opacity: Float,
    val scaleXSign: Float,
    val scaleYSign: Float
)

/**
 * Dedicated After Effects Effects & Composition Engine.
 * Strictly isolated from Video Star (35 effects) and Node Video graph stages.
 */
object AeEffectEngine {

    private val aeShaderBodies: Map<String, String> by lazy {
        MoveTransformShaders.bodies +
            DistortionWarpShaders.bodies +
            MatteMaskKeyShaders.bodies +
            OpacityVisibilityShaders.bodies +
            ProceduralShaders.bodies +
            RepeatShaders.bodies +
            ThreeDShaders.bodies +
            TextAndOtherShaders.bodies
    }

    fun isAeSupportedEffect(effectId: String): Boolean =
        AeEffectEngineCatalog.findById(effectId) != null

    fun createPassForLayer(effectId: String): ShaderEffectPass? {
        val def = AeEffectEngineCatalog.findById(effectId) ?: return null
        val item = def.toCatalogItem()
        return ShaderEffectPass(
            id = "ae_pass_${def.id}_${System.nanoTime() % 100000L}",
            effectId = def.id,
            effectName = def.name,
            category = def.shaderCategory,
            intensity = def.defaultIntensity,
            params = item.defaultParamMap(),
            colorParams = item.defaultColorMap(),
            textParams = item.defaultTextMap()
        )
    }

    fun createPassesForSelection(effectId: String): List<ShaderEffectPass> {
        if (com.example.engine.packs.CustomPackRepository.hasCustomEffect(effectId)) {
            val expanded = com.example.engine.packs.CustomPackRepository.expandEffectPassesForSelection(effectId)
            if (expanded.isNotEmpty()) return expanded
        }
        return listOfNotNull(createPassForLayer(effectId))
    }

    fun fragmentShaderForAeEffect(effectId: String): String {
        val canonical = com.example.model.EffectCatalog.resolveCanonicalId(effectId)
        com.example.engine.packs.CustomPackRepository.findCustomShaderFragmentSource(canonical)?.let {
            return it
        }
        val mappedFallback = com.example.engine.packs.CustomPackRepository.findFallbackEffectId(canonical)
            ?.let { com.example.model.EffectCatalog.resolveCanonicalId(it) }
        val body = aeShaderBodies[canonical]
            ?: mappedFallback?.let { aeShaderBodies[it] }
            ?: return ShaderLibraryFallback.passthrough()
        return ShaderCommonGlsl.buildFragmentShader(body)
    }

    private object ShaderLibraryFallback {
        fun passthrough(): String = """#version 300 es
precision highp float;
in vec2 vTexCoord;
uniform sampler2D uTexture;
out vec4 outColor;
void main() { outColor = texture(uTexture, vTexCoord); }
"""
    }

    fun composeAeLayerTransformAt(
        layer: EditorLayer,
        timelinePositionMs: Long
    ): AeEvaluatedLayerTransform {
        val kfs = layer.keyframes
        if (kfs.isEmpty()) {
            return AeEvaluatedLayerTransform(
                offsetX = layer.offsetX,
                offsetY = layer.offsetY,
                scale = layer.scale.coerceIn(0.1f, 6f),
                rotation = layer.rotation,
                opacity = layer.opacity.coerceIn(0f, 1f),
                scaleXSign = if (layer.isMirrored) -1f else 1f,
                scaleYSign = if (layer.isFlippedVertical) -1f else 1f
            )
        }
        val layerDur = layer.effectiveLayerDurationMs.toFloat().coerceAtLeast(1f)
        val progress = ((timelinePositionMs - layer.startTimeMs).toFloat() / layerDur).coerceIn(0f, 1f)
        val sorted = kfs.sortedBy { it.timeProgress }

        val first = sorted.first()
        val last = sorted.last()
        if (sorted.size == 1 || progress <= first.timeProgress) {
            return AeEvaluatedLayerTransform(
                offsetX = first.offsetX,
                offsetY = first.offsetY,
                scale = first.scale.coerceIn(0.1f, 6f),
                rotation = first.rotation,
                opacity = first.opacity.coerceIn(0f, 1f),
                scaleXSign = if (layer.isMirrored) -1f else 1f,
                scaleYSign = if (layer.isFlippedVertical) -1f else 1f
            )
        }
        if (progress >= last.timeProgress) {
            return AeEvaluatedLayerTransform(
                offsetX = last.offsetX,
                offsetY = last.offsetY,
                scale = last.scale.coerceIn(0.1f, 6f),
                rotation = last.rotation,
                opacity = last.opacity.coerceIn(0f, 1f),
                scaleXSign = if (layer.isMirrored) -1f else 1f,
                scaleYSign = if (layer.isFlippedVertical) -1f else 1f
            )
        }
        val nextIdx = sorted.indexOfFirst { it.timeProgress >= progress }.coerceAtLeast(1)
        val prev = sorted[nextIdx - 1]
        val next = sorted[nextIdx]
        val span = (next.timeProgress - prev.timeProgress).takeIf { abs(it) > 0.0001f } ?: 1f
        val rawT = ((progress - prev.timeProgress) / span).coerceIn(0f, 1f)
        val t = prev.curve.evaluate(rawT)

        fun lerp(a: Float, b: Float): Float = a + (b - a) * t

        return AeEvaluatedLayerTransform(
            offsetX = lerp(prev.offsetX, next.offsetX),
            offsetY = lerp(prev.offsetY, next.offsetY),
            scale = lerp(prev.scale, next.scale).coerceIn(0.1f, 6f),
            rotation = lerp(prev.rotation, next.rotation),
            opacity = lerp(prev.opacity, next.opacity).coerceIn(0f, 1f),
            scaleXSign = if (layer.isMirrored) -1f else 1f,
            scaleYSign = if (layer.isFlippedVertical) -1f else 1f
        )
    }

    fun splitLayerAt(layer: EditorLayer, playheadMs: Long): Pair<EditorLayer, EditorLayer>? {
        val start = layer.startTimeMs
        val dur = layer.effectiveLayerDurationMs
        val end = start + dur
        val splitPoint = playheadMs.coerceIn(start + 250L, (end - 250L).coerceAtLeast(start + 250L))
        if (dur < 550L || splitPoint <= start + 200L || splitPoint >= end - 200L) return null
        val firstDur = (splitPoint - start).coerceAtLeast(250L)
        val secondDur = (end - splitPoint).coerceAtLeast(250L)

        val updatedClip1 = when (val c = layer.mediaClip) {
            is com.example.model.MediaClip.Video -> c.copy(
                endTrimMs = (c.startTrimMs + firstDur).coerceAtMost(c.durationMs)
            )
            is com.example.model.MediaClip.Photo -> c.copy(durationMs = firstDur)
            null -> null
        }
        val updatedClip2 = when (val c = layer.mediaClip) {
            is com.example.model.MediaClip.Video -> c.copy(
                id = "${c.id}_s2",
                startTrimMs = (c.startTrimMs + firstDur).coerceAtMost(c.durationMs - 200L),
                endTrimMs = c.endTrimMs
            )
            is com.example.model.MediaClip.Photo -> c.copy(
                id = "${c.id}_s2",
                durationMs = secondDur
            )
            null -> null
        }

        val firstHalf = layer.copy(
            durationMs = firstDur,
            mediaClip = updatedClip1
        )
        val secondHalf = layer.copy(
            id = "${layer.id}_split_${abs(System.nanoTime() % 10000L)}",
            title = "${layer.displayTitle} (2)",
            startTimeMs = splitPoint,
            durationMs = secondDur,
            mediaClip = updatedClip2,
            sourceSceneId = null
        )
        return firstHalf to secondHalf
    }
}
