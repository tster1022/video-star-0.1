package com.example.ui.components

import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.SceneBitmapRenderer
import com.example.engine.render.StudioRenderErrorBoundary
import com.example.gl.GlLayerRenderStage
import com.example.gl.GlRenderStage
import com.example.model.EditorLayer
import com.example.model.LayerEffectType
import com.example.model.LayerMediaType
import com.example.model.MediaClip
import com.example.model.ShaderEffectPass
import com.example.model.TimelineScene
import com.example.model.TransitionCategory
import com.example.model.evaluateVelocityAt
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun ProjectSongAudioPlayer(
    songUriString: String?,
    isPlaying: Boolean,
    timelinePositionMs: Long,
    songVolume: Float
) {
    if (songUriString.isNullOrBlank() || LocalInspectionMode.current) return
    val context = LocalContext.current
    var hasAudioError by remember(songUriString) { mutableStateOf(false) }
    val audioPlayer = remember(songUriString) {
        runCatching {
            val songAudioAttrs = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()
            ExoPlayer.Builder(context)
                .setAudioAttributes(songAudioAttrs, false)
                .build()
                .apply {
                    setMediaItem(MediaItem.fromUri(Uri.parse(songUriString)))
                    repeatMode = Player.REPEAT_MODE_ALL
                    prepare()
                }
        }.getOrNull()
    }

    DisposableEffect(audioPlayer) {
        val player = audioPlayer ?: return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                hasAudioError = true
            }
        }
        player.addListener(listener)
        onDispose {
            runCatching {
                player.removeListener(listener)
                player.playWhenReady = false
                player.release()
            }
        }
    }

    LaunchedEffect(audioPlayer, songVolume) {
        runCatching { audioPlayer?.volume = songVolume.coerceIn(0f, 1f) }
    }

    LaunchedEffect(audioPlayer, isPlaying, hasAudioError) {
        val player = audioPlayer ?: return@LaunchedEffect
        if (hasAudioError) {
            runCatching { player.playWhenReady = false }
            return@LaunchedEffect
        }
        runCatching {
            if (isPlaying) {
                val dur = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                val target = if (dur > 0L && dur != Long.MAX_VALUE) timelinePositionMs % dur else timelinePositionMs
                if (abs(player.currentPosition - target) > 120L) {
                    player.seekTo(target.coerceAtLeast(0L))
                }
                player.playWhenReady = true
            } else {
                player.playWhenReady = false
            }
        }
    }

    val pausedSongPosMs = if (isPlaying) Long.MIN_VALUE else timelinePositionMs
    LaunchedEffect(audioPlayer, pausedSongPosMs, hasAudioError) {
        if (pausedSongPosMs == Long.MIN_VALUE || hasAudioError) return@LaunchedEffect
        val player = audioPlayer ?: return@LaunchedEffect
        kotlinx.coroutines.delay(24L)
        runCatching {
            val dur = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
            val target = if (dur > 0L && dur != Long.MAX_VALUE) pausedSongPosMs % dur else pausedSongPosMs
            if (abs(player.currentPosition - target) > 120L) {
                player.seekTo(target.coerceAtLeast(0L))
            }
        }
    }
}

@Composable
fun SceneRenderStage(
    scene: TimelineScene?,
    nextScene: TimelineScene? = null,
    localPositionMs: Long,
    isPlaying: Boolean,
    clipVolume: Float = 1f,
    speedMultiplier: Float = 1f,
    useLiveVideoPlayer: Boolean = true,
    forceGlStage: Boolean = false,
    samCutout: SamCutoutSpec = SamCutoutSpec(),
    layerText: String = "",
    layerTextSpec: TextLayerSpec = TextLayerSpec(),
    parentLayer: EditorLayer? = null,
    contentDescription: String = "Scene Preview",
    modifier: Modifier = Modifier,
    onVideoPositionChanged: (Long) -> Unit = {},
    onScenePlaybackEnded: () -> Unit = {}
) {
    if (scene == null) {
        Box(modifier = modifier.background(Color.Black))
        return
    }

    val sceneDur = scene.effectiveDurationMs.coerceAtLeast(200L)
    val repeatCount = scene.timing.repeatCount.coerceIn(1, 3)
    val loopLocalMs = ((localPositionMs * repeatCount) % sceneDur).coerceIn(0L, sceneDur)

    // Freeze frame clamp
    val effectiveLocalMs = if (scene.timing.freezeFrameEnabled) {
        val baseDur = scene.timing.durationMs.coerceAtLeast(200L)
        val freezeStart = (scene.timing.freezeAtProgress.coerceIn(0f, 1f) * baseDur).toLong()
        val freezeEnd = freezeStart + scene.timing.freezeDurationMs
        when {
            loopLocalMs < freezeStart -> loopLocalMs
            loopLocalMs <= freezeEnd -> freezeStart
            else -> (loopLocalMs - scene.timing.freezeDurationMs).coerceIn(0L, baseDur)
        }
    } else {
        loopLocalMs
    }

    val localProgress = (effectiveLocalMs.toFloat() / sceneDur.toFloat()).coerceIn(0f, 1f)
    val activeTransform = StudioRenderErrorBoundary.safeEvaluate("scene_transform", scene.transform) {
        scene.transform.evaluatedAt(localProgress)
    }
    val curveSpeed = StudioRenderErrorBoundary.safeEvaluate("scene_velocity", speedMultiplier.coerceIn(0.25f, 3f)) {
        evaluateVelocityAt(localProgress, scene.velocityPoints, scene.isVelocitySmooth) * speedMultiplier
    }
    val activePasses = remember(scene) {
        StudioRenderErrorBoundary.safeEvaluate("scene_passes", emptyList()) {
            scene.resolvedEffectPasses()
        }
    }

    val isHardwareGlSupported = !LocalInspectionMode.current &&
        !Build.FINGERPRINT.contains("robolectric", ignoreCase = true)
    val canUseGlStage = isHardwareGlSupported &&
        !scene.isMultiLayerComposite &&
        !scene.isReversed &&
        !scene.timing.freezeFrameEnabled &&
        scene.transitionToNext == null &&
        (forceGlStage || (scene.clip != null && (useLiveVideoPlayer || activePasses.isNotEmpty())) || activePasses.isNotEmpty())

    if (canUseGlStage) {
        var isGlFrameRendered by remember(scene.id, scene.clip?.id, scene.mediaUriString) {
            mutableStateOf(false)
        }
        val fallbackColorFilter = remember(activePasses, localProgress, scene.colorGrading) {
            val gradingMatrix = if (!scene.colorGrading.isNeutral) scene.colorGrading.toColorMatrixArray() else null
            buildSceneColorFilter(activePasses, localProgress, gradingMatrix)
        }
        Box(
            modifier = modifier
                .clipToBounds()
                .background(Color(activeTransform.bgColorArgb.toInt()))
        ) {
            if (!isGlFrameRendered) {
                if (scene.clip == null) {
                    ProceduralCalibrationGridBox(modifier = Modifier.fillMaxSize())
                } else {
                    val fallbackPos = when (val c = scene.clip) {
                        is MediaClip.Video -> (c.startTrimMs + effectiveLocalMs).coerceIn(c.startTrimMs, c.endTrimMs)
                        else -> effectiveLocalMs
                    }
                    MediaClipStageContent(
                        clip = scene.clip,
                        isPlaying = false,
                        positionMs = fallbackPos,
                        isMuted = true,
                        isFitMode = activeTransform.isFitMode,
                        useLiveVideoPlayer = false,
                        contentDescription = contentDescription,
                        colorFilter = fallbackColorFilter,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            GlRenderStage(
                scene = scene,
                localPositionMs = effectiveLocalMs,
                isPlaying = isPlaying,
                clipVolume = clipVolume,
                speedMultiplier = speedMultiplier,
                enableExoPlayer = useLiveVideoPlayer,
                samCutout = samCutout,
                layerText = layerText,
                layerTextSpec = layerTextSpec,
                parentLayer = parentLayer,
                modifier = Modifier.fillMaxSize(),
                onVideoPositionChanged = onVideoPositionChanged,
                onScenePlaybackEnded = onScenePlaybackEnded,
                onFirstFrameRendered = { isGlFrameRendered = true }
            )
        }
        return
    }

    // Motion calculations with safe fallback
    val motionOffsets = StudioRenderErrorBoundary.safeEvaluate("scene_motion", floatArrayOf(0f, 0f, 1f, 0f)) {
        SceneBitmapRenderer.computeMotionOffsets(scene, localProgress)
    }
    val effectMotion = StudioRenderErrorBoundary.safeEvaluate("effect_motion", floatArrayOf(0f, 0f, 1f, 0f)) {
        computeEffectMotionOffsets(activePasses, localProgress)
    }

    // Outgoing transition blend near end of scene
    val transition = scene.transitionToNext
    val transitionFactor = if (transition != null && nextScene != null) {
        val transDur = transition.durationMs.coerceIn(200L, sceneDur)
        val startTrans = (sceneDur - transDur).coerceAtLeast(0L)
        if (localPositionMs >= startTrans) {
            ((localPositionMs - startTrans).toFloat() / transDur.toFloat()).coerceIn(0f, 1f)
        } else 0f
    } else 0f

    val bgColor = Color(activeTransform.bgColorArgb.toInt())

    Box(
        modifier = modifier
            .clipToBounds()
            .background(bgColor)
    ) {
        DrawBackgroundEffect(activePasses = activePasses, progress = localProgress)

        val hSign = if (activeTransform.flipH) -1f else 1f
        val vSign = if (activeTransform.flipV) -1f else 1f

        val blurIntensity = activePasses.firstOrNull {
            it.effectId in setOf("radial_zoom_blur", "dir_motion_blur", "gaussian_blur", "lens_bokeh", "spin_vortex", "glow_bloom")
        }?.let { pass ->
            val factor = if (pass.effectId == "glow_bloom") 1.5f else 10f
            pass.evaluateIntensityAt(localProgress) * factor
        } ?: 0f

        val blurMod = if (blurIntensity > 0.2f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.blur(blurIntensity.dp)
        } else {
            Modifier
        }

        // Transition transform for outgoing scene
        val transOffsetX = when (transition?.transitionId) {
            "slide_blur", "slide_left", "wipe" -> -transitionFactor * 360f
            "cube_right" -> transitionFactor * 160f
            "cube_left", "cube_rotate" -> -transitionFactor * 160f
            else -> 0f
        }
        val transScale = when (transition?.transitionId) {
            "zoom_blur" -> 1f + transitionFactor * 0.65f
            "zoom_spin" -> 1f + transitionFactor * 0.5f
            else -> 1f
        }
        val transRotY = when {
            transition?.transitionId == "cube_right" -> -transitionFactor * 90f
            transition?.transitionId == "back_flip" -> 0f
            transition != null && transition.category == TransitionCategory.FLIP_3D -> transitionFactor * 90f
            else -> 0f
        }
        val transRotX = if (transition?.transitionId == "back_flip") transitionFactor * 90f else 0f
        val transAlpha = if (transition != null) (1f - transitionFactor * 0.85f).coerceIn(0f, 1f) else 1f

        val stageContentModifier = Modifier
            .fillMaxSize()
            .then(blurMod)
            .graphicsLayer {
                translationX = activeTransform.offsetX + motionOffsets[0] + effectMotion[0] + transOffsetX
                translationY = activeTransform.offsetY + motionOffsets[1] + effectMotion[1]
                scaleX = activeTransform.scale * motionOffsets[2] * effectMotion[2] * hSign * transScale
                scaleY = activeTransform.scale * motionOffsets[2] * effectMotion[2] * vSign * transScale
                rotationZ = activeTransform.rotation + motionOffsets[3] + effectMotion[3]
                rotationY = transRotY
                rotationX = transRotX
                cameraDistance = 12f * density
                alpha = transAlpha
            }

        val gradingMatrix = if (!scene.colorGrading.isNeutral) scene.colorGrading.toColorMatrixArray() else null
        val colorFilter = buildSceneColorFilter(activePasses, localProgress, gradingMatrix)

        if (scene.isMultiLayerComposite && scene.layers.isNotEmpty()) {
            MultiLayerCompositeContent(
                layers = scene.layers,
                progress = localProgress,
                isPlaying = isPlaying,
                isMuted = clipVolume <= 0.01f,
                modifier = stageContentModifier
            )
        } else {
            val clip = scene.clip
            val absSeekMs = when (clip) {
                is MediaClip.Video -> {
                    val span = clip.trimmedDurationMs.coerceAtLeast(200L)
                    val clampedLocal = effectiveLocalMs.coerceIn(0L, span)
                    if (scene.isReversed) (clip.endTrimMs - clampedLocal).coerceIn(clip.startTrimMs, clip.endTrimMs)
                    else (clip.startTrimMs + clampedLocal).coerceIn(clip.startTrimMs, clip.endTrimMs)
                }
                is MediaClip.Photo -> effectiveLocalMs
                null -> 0L
            }

            val tilePass = activePasses.firstOrNull { it.effectId == "tile_grid" && it.evaluateIntensityAt(localProgress) > 0.1f }
            if (tilePass != null) {
                val gridSize = tilePass.evaluateParamAt("grid_size", localProgress, 2f, 2f, 4f).roundToInt().coerceIn(2, 4)
                TileGridContent(
                    gridSize = gridSize,
                    clip = clip,
                    absSeekMs = absSeekMs,
                    colorFilter = colorFilter,
                    isFitMode = activeTransform.isFitMode,
                    modifier = stageContentModifier
                )
            } else {
                val hasMotionTile = activeTransform.mirrorEdges ||
                    activePasses.any { it.effectId == "motion_tile" && it.evaluateIntensityAt(localProgress) > 0.1f }
                if (hasMotionTile) {
                    MediaClipStageContent(
                        clip = clip,
                        isPlaying = false,
                        positionMs = absSeekMs,
                        isMuted = true,
                        isFitMode = false,
                        useLiveVideoPlayer = false,
                        contentDescription = contentDescription,
                        colorFilter = colorFilter,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = -1.35f
                                scaleY = -1.35f
                                alpha = 0.65f
                            }
                    )
                }

                if (clip == null && forceGlStage) {
                    ProceduralCalibrationGridBox(modifier = stageContentModifier)
                } else {
                    MediaClipStageContent(
                        clip = clip,
                        isPlaying = isPlaying && !scene.isReversed && !scene.timing.freezeFrameEnabled,
                        positionMs = absSeekMs,
                        isMuted = clipVolume <= 0.01f,
                        volume = clipVolume,
                        playbackSpeed = curveSpeed,
                        isFitMode = activeTransform.isFitMode,
                        useLiveVideoPlayer = useLiveVideoPlayer && !scene.isReversed && !scene.timing.freezeFrameEnabled,
                        contentDescription = contentDescription,
                        colorFilter = colorFilter,
                        colorMatrixArray = gradingMatrix,
                        onPositionChanged = { absPos ->
                            val startOffset = if (clip is MediaClip.Video) clip.startTrimMs else 0L
                            onVideoPositionChanged((absPos - startOffset).coerceAtLeast(0L))
                        },
                        onPlaybackEnded = onScenePlaybackEnded,
                        modifier = stageContentModifier
                    )
                }
            }
        }

        // Incoming transition peek if transitioning to next scene
        if (transition != null && nextScene != null && transitionFactor > 0.02f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = transitionFactor
                        translationX = when (transition.transitionId) {
                            "slide_blur", "slide_left", "wipe" -> (1f - transitionFactor) * 360f
                            "cube_right" -> -(1f - transitionFactor) * 160f
                            "cube_left", "cube_rotate" -> (1f - transitionFactor) * 160f
                            else -> 0f
                        }
                        rotationY = when {
                            transition.transitionId == "cube_right" -> (1f - transitionFactor) * 90f
                            transition.transitionId == "back_flip" -> 0f
                            transition.category == TransitionCategory.FLIP_3D -> -(1f - transitionFactor) * 90f
                            else -> 0f
                        }
                        rotationX = if (transition.transitionId == "back_flip") -(1f - transitionFactor) * 90f else 0f
                        if (transition.transitionId == "radial_wipe") {
                            scaleX = 0.35f + 0.65f * transitionFactor
                            scaleY = 0.35f + 0.65f * transitionFactor
                        }
                        cameraDistance = 12f * density
                    }
            ) {
                MediaClipStageContent(
                    clip = nextScene.clip,
                    isPlaying = false,
                    positionMs = (nextScene.clip as? MediaClip.Video)?.startTrimMs ?: 0L,
                    isMuted = true,
                    useLiveVideoPlayer = false,
                    contentDescription = nextScene.title,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Overlay & Post-Process Canvas FX
        DrawForegroundEffectOverlays(
            activePasses = activePasses,
            transition = transition,
            progress = localProgress,
            transitionFactor = transitionFactor
        )
    }
}

@Composable
private fun MultiLayerCompositeContent(
    layers: List<EditorLayer>,
    progress: Float,
    isPlaying: Boolean,
    isMuted: Boolean,
    modifier: Modifier = Modifier
) {
    val isHardwareGlSupported = !LocalInspectionMode.current &&
        !Build.FINGERPRINT.contains("robolectric", ignoreCase = true)

    Box(modifier = modifier) {
        layers.forEach { layer ->
            if (!layer.isVisible) return@forEach
            val interpolated = StudioRenderErrorBoundary.safeEvaluate("layer_interp_${layer.id}", layer) {
                interpolateLayerAt(layer, progress)
            }
            if (interpolated.layerMediaType != LayerMediaType.NONE || interpolated.effectiveText.isNotBlank()) {
                val activeLayerPasses = interpolated.effectPasses.filter { it.isEnabled && it.effectId != "none" }
                val parentLayer = interpolated.parentLayerId?.let { pid -> layers.firstOrNull { it.id == pid } }
                val useLayerGl = (activeLayerPasses.isNotEmpty() || interpolated.samCutout.isActive) &&
                    isHardwareGlSupported &&
                    (interpolated.mediaClip != null || interpolated.backgroundColorArgb != null || interpolated.effectiveText.isNotBlank())

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .applyLayerMask(interpolated)
                        .graphicsLayer {
                            translationX = interpolated.offsetX
                            translationY = interpolated.offsetY
                            val hSign = if (interpolated.isMirrored) -1f else 1f
                            val vSign = if (interpolated.isFlippedVertical) -1f else 1f
                            scaleX = interpolated.scale * hSign
                            scaleY = interpolated.scale * vSign
                            rotationZ = interpolated.rotation
                            if (interpolated.is3DEnabled) {
                                rotationX = interpolated.rotationX
                                rotationY = interpolated.rotationY
                                cameraDistance = (interpolated.perspectiveFov / 3.5f).coerceIn(6f, 24f) * density
                            }
                            alpha = interpolated.opacity
                        }
                ) {
                    if (useLayerGl) {
                        GlLayerRenderStage(
                            layer = interpolated,
                            progress = progress,
                            isPlaying = isPlaying,
                            isMuted = isMuted,
                            parentLayer = parentLayer,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        if (interpolated.backgroundColorArgb != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(interpolated.backgroundColorArgb.toInt()))
                            )
                        }
                        if (interpolated.mediaClip != null) {
                            val tintFilter = if (interpolated.tintColor != Color.Transparent) {
                                ColorFilter.tint(interpolated.tintColor.copy(alpha = 0.45f), BlendMode.SrcAtop)
                            } else null
                            MediaClipStageContent(
                                clip = interpolated.mediaClip,
                                isPlaying = isPlaying,
                                positionMs = (progress * interpolated.mediaClip.effectiveDurationMs).toLong(),
                                isMuted = isMuted,
                                useLiveVideoPlayer = false,
                                contentDescription = "Layer ${interpolated.number}",
                                colorFilter = tintFilter,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        if (activeLayerPasses.isNotEmpty()) {
                            DrawForegroundEffectOverlays(
                                activePasses = activeLayerPasses,
                                transition = null,
                                progress = progress,
                                transitionFactor = 0f
                            )
                        } else if (interpolated.effect != LayerEffectType.NONE) {
                            val overlayColor = when (interpolated.effect) {
                                LayerEffectType.NEON_GLOW -> Color(0x33FF2A85)
                                LayerEffectType.RGB_SPLIT -> Color(0x2800E5FF)
                                LayerEffectType.DREAM_BLUR -> Color(0x2AFFFFFF)
                                LayerEffectType.VINTAGE_PULSE -> Color(0x33FFA000)
                                LayerEffectType.NONE -> Color.Transparent
                            }
                            Box(modifier = Modifier.fillMaxSize().background(overlayColor))
                        }
                    }
                    if (interpolated.effectiveText.isNotBlank()) {
                        RichLayerTextOverlay(layer = interpolated)
                    }
                }
            }
        }
    }
}

private fun interpolateLayerAt(layer: EditorLayer, progress: Float): EditorLayer {
    val kfs = layer.keyframes.sortedBy { it.timeProgress }
    if (kfs.isEmpty()) return layer
    if (kfs.size == 1 || progress <= kfs.first().timeProgress) {
        val k = kfs.first()
        return layer.copy(
            offsetX = k.offsetX,
            offsetY = k.offsetY,
            scale = k.scale,
            rotation = k.rotation,
            rotationX = k.rotationX,
            rotationY = k.rotationY,
            perspectiveFov = k.perspectiveFov
        )
    }
    if (progress >= kfs.last().timeProgress) {
        val k = kfs.last()
        return layer.copy(
            offsetX = k.offsetX,
            offsetY = k.offsetY,
            scale = k.scale,
            rotation = k.rotation,
            rotationX = k.rotationX,
            rotationY = k.rotationY,
            perspectiveFov = k.perspectiveFov
        )
    }
    val nextIdx = kfs.indexOfFirst { it.timeProgress >= progress }.coerceAtLeast(1)
    val prev = kfs[nextIdx - 1]
    val next = kfs[nextIdx]
    val span = (next.timeProgress - prev.timeProgress).takeIf { abs(it) > 0.0001f } ?: 1f
    val rawT = ((progress - prev.timeProgress) / span).coerceIn(0f, 1f)
    val t = prev.curve.evaluate(rawT)
    return layer.copy(
        offsetX = prev.offsetX + (next.offsetX - prev.offsetX) * t,
        offsetY = prev.offsetY + (next.offsetY - prev.offsetY) * t,
        scale = prev.scale + (next.scale - prev.scale) * t,
        rotation = prev.rotation + (next.rotation - prev.rotation) * t,
        rotationX = prev.rotationX + (next.rotationX - prev.rotationX) * t,
        rotationY = prev.rotationY + (next.rotationY - prev.rotationY) * t,
        perspectiveFov = prev.perspectiveFov + (next.perspectiveFov - prev.perspectiveFov) * t
    )
}

@Composable
private fun TileGridContent(
    gridSize: Int,
    clip: MediaClip?,
    absSeekMs: Long,
    colorFilter: ColorFilter?,
    isFitMode: Boolean,
    modifier: Modifier
) {
    Column(modifier = modifier) {
        repeat(gridSize) {
            Row(modifier = Modifier.weight(1f).fillMaxSize()) {
                repeat(gridSize) {
                    MediaClipStageContent(
                        clip = clip,
                        isPlaying = false,
                        positionMs = absSeekMs,
                        isMuted = true,
                        isFitMode = isFitMode,
                        useLiveVideoPlayer = false,
                        contentDescription = "Tile",
                        colorFilter = colorFilter,
                        modifier = Modifier.weight(1f).fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawBackgroundEffect(activePasses: List<ShaderEffectPass>, progress: Float) {
    val glow = activePasses.firstOrNull { it.effectId == "glow_bloom" }
    val duotone = activePasses.firstOrNull { it.effectId == "duotone" }
    when {
        glow != null -> {
            val k = glow.evaluateIntensityAt(progress)
            if (k > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFF2A85).copy(alpha = 0.55f * k),
                                    Color(0xFF1A237E).copy(alpha = 0.75f * k),
                                    Color.Black
                                )
                            )
                        )
                )
            }
        }
        duotone != null -> {
            val k = duotone.evaluateIntensityAt(progress)
            if (k > 0.01f) {
                val shift = sin(progress * 2f * PI.toFloat()) * 0.25f + 0.5f
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFC81D77).copy(alpha = 0.45f * k),
                                    Color(0xFF2E7D32).copy(alpha = (0.3f + shift * 0.3f) * k)
                                )
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun DrawForegroundEffectOverlays(
    activePasses: List<ShaderEffectPass>,
    transition: com.example.model.SceneTransition?,
    progress: Float,
    transitionFactor: Float
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        activePasses.forEach { pass ->
            val k = pass.evaluateIntensityAt(progress)
            if (k > 0.01f) {
                when (pass.effectId) {
                    "vignette" -> {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f * k)),
                                center = center,
                                radius = maxOf(size.width, size.height) * 0.68f
                            )
                        )
                    }
                    "rgb_split", "glitch" -> {
                        val shift = size.width * 0.028f * k * (0.6f + 0.4f * abs(sin(progress * 18f)))
                        drawRect(
                            color = Color(0xFFFF0055).copy(alpha = 0.22f * k),
                            topLeft = Offset.Zero,
                            size = Size(shift, size.height)
                        )
                        drawRect(
                            color = Color(0xFF00E5FF).copy(alpha = 0.22f * k),
                            topLeft = Offset(size.width - shift, 0f),
                            size = Size(shift, size.height)
                        )
                        if (pass.effectId == "glitch") {
                            val barY = ((progress * 5.7f) % 1f) * size.height
                            drawRect(
                                color = Color.White.copy(alpha = 0.28f * k),
                                topLeft = Offset(0f, barY),
                                size = Size(size.width, 6.dp.toPx())
                            )
                        }
                    }
                    "scanlines", "vhs_tape" -> {
                        val step = 5.dp.toPx()
                        var y = 0f
                        while (y < size.height) {
                            drawLine(
                                color = Color.Black.copy(alpha = 0.38f * k),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1.4.dp.toPx()
                            )
                            y += step
                        }
                        if (pass.effectId == "vhs_tape") {
                            val bandY = ((progress * 1.8f) % 1f) * size.height
                            drawRect(
                                color = Color.White.copy(alpha = 0.25f * k),
                                topLeft = Offset(0f, bandY),
                                size = Size(size.width, 8.dp.toPx())
                            )
                        }
                    }
                    "film_grain", "halftone" -> {
                        val step = 18.dp.toPx()
                        val phase = (progress * 100f).toInt()
                        var y = 0f
                        var row = 0
                        while (y < size.height) {
                            var x = 0f
                            var col = 0
                            while (x < size.width) {
                                if (((row * 31 + col * 17 + phase) % 7) == 0) {
                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.22f * k),
                                        radius = 1.5.dp.toPx(),
                                        center = Offset(x, y)
                                    )
                                }
                                x += step
                                col++
                            }
                            y += step
                            row++
                        }
                    }
                    "mirror_lr", "mirror_tb", "quad_mirror", "kaleidoscope", "turbulent_displace", "ripple", "twirl", "bulge_pinch", "fisheye" -> {
                        val strokeColor = Color.White.copy(alpha = 0.20f * k)
                        drawLine(strokeColor, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), 1.dp.toPx())
                        if (pass.effectId != "mirror_lr") {
                            drawLine(strokeColor, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1.dp.toPx())
                        }
                    }
                }
            }
        }

        if (transition != null && transitionFactor > 0.01f && transition.category == TransitionCategory.LIGHT_LEAK) {
            val flashAlpha = sin(transitionFactor * PI.toFloat()).coerceIn(0f, 1f) * 0.85f
            val flashColor = when (transition.transitionId) {
                "warm_leak" -> Color(0xFFFF8F00)
                "luminate" -> Color(0xFFFF80AB)
                else -> Color.White
            }
            drawRect(color = flashColor.copy(alpha = flashAlpha))
        }
    }
}

private fun computeEffectMotionOffsets(passes: List<ShaderEffectPass>, progress: Float): FloatArray {
    var tx = 0f
    var ty = 0f
    var sc = 1f
    var rot = 0f
    val phase = progress * 2f * PI.toFloat() * 4f
    passes.forEach { fx ->
        val k = fx.evaluateIntensityAt(progress)
        if (k > 0.01f) {
            when (fx.effectId) {
                "hard_shake" -> {
                    tx += sin(phase * 3f) * 26f * k
                    ty += cos(phase * 2.5f) * 20f * k
                    sc *= 1.05f
                }
                "smooth_shake" -> {
                    tx += sin(phase) * 14f * k
                    ty += cos(phase * 0.8f) * 10f * k
                }
                "zoom_pulse" -> {
                    sc *= 1f + abs(sin(phase)) * 0.22f * k
                }
                "spin_vortex" -> {
                    sc *= 1.06f
                    rot += sin(phase * 0.5f) * 18f * k
                }
            }
        }
    }
    return floatArrayOf(tx, ty, sc, rot)
}

private fun buildSceneColorFilter(
    passes: List<ShaderEffectPass>,
    progress: Float,
    gradingMatrix: FloatArray? = null
): ColorFilter? {
    val active = passes.filter { it.effectId != "none" && it.evaluateIntensityAt(progress) > 0.01f }
    if (active.isEmpty() && gradingMatrix == null) return null
    val matrix = ColorMatrix()
    var modified = false
    if (gradingMatrix != null) {
        matrix.timesAssign(ColorMatrix(gradingMatrix))
        modified = true
    }
    active.forEach { fx ->
        val k = fx.evaluateIntensityAt(progress)
        val m = ColorMatrix()
        val hit = when (fx.effectId) {
            "bw" -> {
                m.setToSaturation(1f - k); true
            }
            "exposure_flash" -> {
                val b = 1f + k * 0.85f
                m.setToScale(b, b, b, 1f); true
            }
            "hue_shift" -> {
                m.setToRotateRed(k * 180f); true
            }
            "color_adjust" -> {
                val sat = 0.5f + fx.evaluateParamAt("saturation", progress, 0.7f, 0f, 1f) * 1.2f * k
                val bright = 0.7f + fx.evaluateParamAt("brightness", progress, 0.55f, 0f, 1f) * 0.7f
                m.setToSaturation(sat)
                val sc = ColorMatrix().apply { setToScale(bright, bright, bright, 1f) }
                m.timesAssign(sc)
                true
            }
            "duotone" -> {
                m.setToSaturation(0.15f)
                val tint = ColorMatrix().apply { setToScale(1.1f + 0.35f * k, 0.78f, 1.18f + 0.25f * k, 1f) }
                m.timesAssign(tint)
                true
            }
            "glow_bloom" -> {
                val b = 1f + k * 0.35f
                m.setToScale(b, b, b, 1f); true
            }
            "invert" -> {
                m.setToScale(-1f, -1f, -1f, 1f); true
            }
            else -> false
        }
        if (hit) {
            matrix.timesAssign(m)
            modified = true
        }
    }
    return if (modified) ColorFilter.colorMatrix(matrix) else null
}

@Composable
private fun ProceduralCalibrationGridBox(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val barColors = listOf(
            Color(0xFFEAEAF0),
            Color(0xFFFFD54F),
            Color(0xFF26C6DA),
            Color(0xFF66BB6A),
            Color(0xFFD92B7F),
            Color(0xFFEF5350),
            Color(0xFF42A5F5)
        )
        val barW = size.width / barColors.size
        val topH = size.height * 0.68f
        barColors.forEachIndexed { idx, col ->
            drawRect(
                color = col.copy(alpha = 0.85f),
                topLeft = Offset(idx * barW, 0f),
                size = Size(barW + 1f, topH)
            )
        }
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFB81D6F), Color(0xFF2E7D32), Color(0xFF1565C0))
            ),
            topLeft = Offset(0f, topH),
            size = Size(size.width, size.height - topH)
        )
        val cols = 8
        val rows = 8
        for (i in 1 until cols) {
            val x = (size.width / cols) * i
            drawLine(Color.White.copy(alpha = 0.3f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
        }
        for (j in 1 until rows) {
            val y = (size.height / rows) * j
            drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
    }
}

