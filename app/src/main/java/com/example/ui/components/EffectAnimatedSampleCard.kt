package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MediaClip
import com.example.ui.theme.ClassicGreen
import com.example.ui.theme.IosHairlineGray
import com.example.ui.theme.SfProTextFontFamily
import com.example.ui.theme.VideoStarInkDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

sealed interface SampleCardKind {
    data class Effect(val effectId: String) : SampleCardKind
    data class Transition(val transitionId: String) : SampleCardKind
    data class Motion(val presetName: String) : SampleCardKind
}

private object SceneThumbCache {
    private val map = LinkedHashMap<String, ImageBitmap>(8, 0.75f, true)
    fun get(key: String): ImageBitmap? = synchronized(map) { map[key] }
    fun put(key: String, bmp: ImageBitmap) = synchronized(map) {
        if (map.size > 8) {
            val firstKey = map.keys.firstOrNull()
            if (firstKey != null) map.remove(firstKey)
        }
        map[key] = bmp
    }
}

@Composable
fun rememberSceneSampleThumbnail(clip: MediaClip?): ImageBitmap? {
    val context = LocalContext.current
    val isInspection = LocalInspectionMode.current
    val isRobolectric = Build.FINGERPRINT.contains("robolectric", ignoreCase = true)
    val uriStr = clip?.uriString

    val state by produceState<ImageBitmap?>(initialValue = uriStr?.let { SceneThumbCache.get(it) }, key1 = uriStr) {
        if (uriStr.isNullOrBlank() || isInspection || isRobolectric) {
            value = null
            return@produceState
        }
        SceneThumbCache.get(uriStr)?.let {
            value = it
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            runCatching {
                val parsed = Uri.parse(uriStr)
                val rawBmp: Bitmap? = if (clip is MediaClip.Video) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        val path = parsed.path
                        if (parsed.scheme == "file" && path != null && File(path).exists()) {
                            retriever.setDataSource(path)
                        } else {
                            retriever.setDataSource(context, parsed)
                        }
                        retriever.getFrameAtTime(clip.startTrimMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } finally {
                        runCatching { retriever.release() }
                    }
                } else {
                    val path = parsed.path
                    if (parsed.scheme == "file" && path != null && File(path).exists()) {
                        BitmapFactory.decodeFile(path)
                    } else {
                        context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it) }
                    }
                }
                rawBmp?.let { bmp ->
                    val scaled = Bitmap.createScaledBitmap(bmp, 144, 144, true)
                    if (scaled !== bmp) bmp.recycle()
                    val img = scaled.asImageBitmap()
                    SceneThumbCache.put(uriStr, img)
                    img
                }
            }.getOrNull()
        }
    }
    return state
}

/**
 * Video Star live looping mini-animation sample card (76.dp x 92.dp):
 * - 68.dp x 68.dp square preview with 10.dp rounded corners and 1.5.dp border (#2E7D32 active)
 * - Uses current scene thumbnail when available with crisp procedural Video Star star/gradient emblem fallback
 * - Continuously runs a looping mini-animation matching the specific Shader Effect, Mix Transition, or Motion Preset
 */
@Composable
fun EffectAnimatedSampleCard(
    label: String,
    kind: SampleCardKind,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sceneThumbnail: ImageBitmap? = null,
    testTag: String = ""
) {
    val transition = rememberInfiniteTransition(label = "effect_sample_${label}")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loop_t"
    )
    val phase = t * 2f * PI.toFloat()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        modifier = modifier
            .width(76.dp)
            .height(92.dp)
            .iosPressable(onClick = onClick)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF14141A))
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) ClassicGreen else IosHairlineGray,
                    shape = RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedSamplePreviewContent(
                kind = kind,
                t = t,
                phase = phase,
                thumbnail = sceneThumbnail
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            fontFamily = SfProTextFontFamily,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) ClassicGreen else Color(0xFF222222),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
        )
    }
}

@Composable
private fun AnimatedSamplePreviewContent(
    kind: SampleCardKind,
    t: Float,
    phase: Float,
    thumbnail: ImageBitmap?
) {
    val effectId = (kind as? SampleCardKind.Effect)?.effectId
    val transitionId = (kind as? SampleCardKind.Transition)?.transitionId
    val motionId = (kind as? SampleCardKind.Motion)?.presetName?.lowercase()

    // Compute live transform loop for each card
    val tx: Float
    val ty: Float
    val sc: Float
    val rotZ: Float
    val rotY: Float
    val rotX: Float
    val alphaMain: Float

    when {
        effectId == "hard_shake" || motionId == "shake_h" -> {
            tx = sin(phase * 4f) * 9f
            ty = if (effectId == "hard_shake") cos(phase * 3f) * 7f else 0f
            sc = 1.05f; rotZ = 0f; rotY = 0f; rotX = 0f; alphaMain = 1f
        }
        motionId == "shake_v" -> {
            tx = 0f; ty = sin(phase * 4f) * 9f; sc = 1.05f; rotZ = 0f; rotY = 0f; rotX = 0f; alphaMain = 1f
        }
        effectId == "smooth_shake" || motionId == "drift" -> {
            tx = sin(phase) * 7f; ty = cos(phase * 0.8f) * 6f
            sc = 1.06f; rotZ = sin(phase) * 4f; rotY = 0f; rotX = 0f; alphaMain = 1f
        }
        motionId == "pendulum" -> {
            tx = sin(phase) * 5f; ty = 0f; sc = 1.02f; rotZ = sin(phase) * 16f; rotY = 0f; rotX = 0f; alphaMain = 1f
        }
        effectId == "zoom_pulse" || effectId == "radial_zoom_blur" || transitionId == "zoom_blur" || motionId == "zoom_bounce" || motionId == "pulse" -> {
            val pulse = abs(sin(phase))
            tx = 0f; ty = 0f
            sc = 1f + pulse * (if (transitionId == "zoom_blur") 0.45f else 0.28f)
            rotZ = 0f; rotY = 0f; rotX = 0f; alphaMain = 1f
        }
        effectId == "spin_vortex" || transitionId == "zoom_spin" -> {
            tx = 0f; ty = 0f
            sc = if (transitionId == "zoom_spin") 1f + sin(phase * 0.5f) * 0.25f else 1.08f
            rotZ = t * 360f; rotY = 0f; rotX = 0f; alphaMain = 1f
        }
        transitionId == "cube_left" || transitionId == "cube_rotate" -> {
            tx = -t * 14f; ty = 0f; sc = 0.9f; rotZ = 0f; rotY = -t * 90f; rotX = 0f; alphaMain = 1f - t * 0.65f
        }
        transitionId == "cube_right" -> {
            tx = t * 14f; ty = 0f; sc = 0.9f; rotZ = 0f; rotY = t * 90f; rotX = 0f; alphaMain = 1f - t * 0.65f
        }
        transitionId == "back_flip" -> {
            tx = 0f; ty = 0f; sc = 0.92f; rotZ = 0f; rotY = 0f; rotX = t * 180f; alphaMain = 1f
        }
        transitionId == "diagonal_flip" -> {
            tx = 0f; ty = 0f; sc = 0.92f; rotZ = sin(phase) * 18f; rotY = t * 180f; rotX = 0f; alphaMain = 1f
        }
        transitionId == "slide_left" || transitionId == "slide_blur" -> {
            tx = -t * 54f; ty = 0f; sc = 1f; rotZ = 0f; rotY = 0f; rotX = 0f; alphaMain = 1f
        }
        effectId == "motion_tile" -> {
            tx = sin(phase) * 12f; ty = cos(phase) * 8f; sc = 0.72f; rotZ = 0f; rotY = 0f; rotX = 0f; alphaMain = 1f
        }
        else -> {
            tx = 0f; ty = 0f; sc = 1f; rotZ = 0f; rotY = 0f; rotX = 0f; alphaMain = 1f
        }
    }

    val colorFilter: ColorFilter? = when (effectId) {
        "bw" -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1f - (0.5f + 0.5f * sin(phase))) })
        "hue_shift" -> ColorFilter.colorMatrix(ColorMatrix().apply { setToRotateRed(t * 360f) })
        "invert" -> if (sin(phase) > 0f) ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(-1f, -1f, -1f, 1f) }) else null
        "duotone" -> ColorFilter.tint(Color(0xFFFF2A85).copy(alpha = 0.45f), BlendMode.SrcAtop)
        else -> null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Secondary background card visible behind Wipe / Slide / Cube transitions
        if (transitionId != null && (transitionId in setOf("slide_left", "slide_blur", "wipe", "radial_wipe", "cube_left", "cube_right", "crossfade"))) {
            SampleEmblemOrThumbnail(
                thumbnail = null,
                accentAlt = true,
                colorFilter = null,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    if (transitionId == "slide_left" || transitionId == "slide_blur") {
                        translationX = (1f - t) * 54f
                    }
                    if (transitionId == "crossfade") {
                        alpha = t
                    }
                }
            )
        }

        // RGB Split animated ghost channels
        if (effectId == "rgb_split" || effectId == "glitch") {
            val shift = (4f + 4f * abs(sin(phase * 2f)))
            SampleEmblemOrThumbnail(
                thumbnail = thumbnail,
                accentAlt = false,
                colorFilter = ColorFilter.tint(Color(0xFFFF0055).copy(alpha = 0.6f), BlendMode.SrcAtop),
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    translationX = -shift
                    alpha = 0.7f
                }
            )
            SampleEmblemOrThumbnail(
                thumbnail = thumbnail,
                accentAlt = false,
                colorFilter = ColorFilter.tint(Color(0xFF00E5FF).copy(alpha = 0.6f), BlendMode.SrcAtop),
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    translationX = shift
                    alpha = 0.7f
                }
            )
        }

        // Tile grid 2x2 live preview
        if (effectId == "tile_grid") {
            androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
                repeat(2) {
                    androidx.compose.foundation.layout.Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        repeat(2) {
                            SampleEmblemOrThumbnail(
                                thumbnail = thumbnail,
                                accentAlt = false,
                                colorFilter = null,
                                modifier = Modifier.weight(1f).fillMaxSize().graphicsLayer {
                                    translationX = sin(phase) * 3f
                                }
                            )
                        }
                    }
                }
            }
        } else {
            SampleEmblemOrThumbnail(
                thumbnail = thumbnail,
                accentAlt = false,
                colorFilter = colorFilter,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = tx
                        translationY = ty
                        scaleX = sc
                        scaleY = sc
                        rotationZ = rotZ
                        rotationY = rotY
                        rotationX = rotX
                        cameraDistance = 8f * density
                        alpha = alphaMain
                    }
            )
        }

        // Procedural Overlay Animations for Ripple, Kaleidoscope, Mirror, VHS, Glitch, Wipe, Flash, Vignette, Halftone
        Canvas(modifier = Modifier.fillMaxSize()) {
            when {
                effectId in setOf("exposure_flash", "glow_bloom") || transitionId in setOf("white_flash", "warm_leak", "luminate") -> {
                    val flash = abs(sin(phase)) * 0.78f
                    val col = when (transitionId) {
                        "warm_leak" -> Color(0xFFFF9100)
                        "luminate" -> Color(0xFFFF4081)
                        else -> Color.White
                    }
                    drawRect(col.copy(alpha = flash))
                }
                effectId in setOf("ripple", "turbulent_displace", "ripple_dissolve") || transitionId == "ripple_dissolve" -> {
                    val r1 = (t * size.minDimension * 0.7f).coerceAtLeast(2f)
                    val r2 = (((t + 0.5f) % 1f) * size.minDimension * 0.7f).coerceAtLeast(2f)
                    drawCircle(Color.White.copy(alpha = (1f - t) * 0.75f), radius = r1, style = Stroke(2.dp.toPx()))
                    drawCircle(Color(0xFF00E5FF).copy(alpha = (1f - ((t + 0.5f) % 1f)) * 0.7f), radius = r2, style = Stroke(1.8.dp.toPx()))
                }
                effectId in setOf("twirl", "bulge_pinch", "fisheye") -> {
                    val sweep = 180f + 160f * sin(phase)
                    drawArc(
                        color = Color.White.copy(alpha = 0.65f),
                        startAngle = t * 360f,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(2.2.dp.toPx())
                    )
                }
                effectId in setOf("mirror_lr", "mirror_tb", "quad_mirror", "kaleidoscope") -> {
                    val pulsePos = 0.5f + 0.14f * sin(phase)
                    drawLine(Color.White.copy(alpha = 0.75f), Offset(size.width * pulsePos, 0f), Offset(size.width * pulsePos, size.height), 1.5.dp.toPx())
                    if (effectId != "mirror_lr") {
                        drawLine(Color.White.copy(alpha = 0.75f), Offset(0f, size.height * (1f - pulsePos)), Offset(size.width, size.height * (1f - pulsePos)), 1.5.dp.toPx())
                    }
                    if (effectId == "kaleidoscope") {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        for (i in 0 until 6) {
                            val a = Math.toRadians(i * 60.0 + t * 180.0)
                            drawLine(
                                color = Color(0xFFFFD54F).copy(alpha = 0.65f),
                                start = Offset(cx, cy),
                                end = Offset(cx + (size.width * 0.5f * cos(a)).toFloat(), cy + (size.height * 0.5f * sin(a)).toFloat()),
                                strokeWidth = 1.3.dp.toPx()
                            )
                        }
                    }
                }
                effectId in setOf("vhs_tape", "scanlines", "glitch", "pixelate", "film_grain", "halftone") -> {
                    var y = 0f
                    while (y < size.height) {
                        drawLine(Color.Black.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), 1.2.dp.toPx())
                        y += 5.dp.toPx()
                    }
                    val bandY = t * size.height
                    drawRect(
                        color = (if (effectId == "glitch") Color(0xFF00E5FF) else Color.White).copy(alpha = 0.45f),
                        topLeft = Offset(0f, bandY),
                        size = Size(size.width, 6.dp.toPx())
                    )
                }
                effectId == "vignette" -> {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f + 0.25f * abs(sin(phase)))),
                            radius = size.minDimension * 0.62f
                        )
                    )
                }
                transitionId == "wipe" -> {
                    drawRect(
                        color = Color(0xFF1A1A22),
                        topLeft = Offset(0f, 0f),
                        size = Size(size.width * t, size.height)
                    )
                }
                transitionId == "radial_wipe" -> {
                    drawArc(
                        color = Color(0xFF2E7D32).copy(alpha = 0.55f),
                        startAngle = -90f,
                        sweepAngle = t * 360f,
                        useCenter = true
                    )
                }
                effectId in setOf("dir_motion_blur", "gaussian_blur", "lens_bokeh", "sharpen_clarity") -> {
                    val drift = sin(phase) * 8.dp.toPx()
                    drawCircle(
                        color = Color.White.copy(alpha = 0.38f),
                        radius = 8.dp.toPx(),
                        center = Offset(size.width * 0.35f + drift, size.height * 0.35f)
                    )
                    drawCircle(
                        color = Color(0xFFFF80AB).copy(alpha = 0.38f),
                        radius = 6.dp.toPx(),
                        center = Offset(size.width * 0.68f - drift, size.height * 0.65f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SampleEmblemOrThumbnail(
    thumbnail: ImageBitmap?,
    accentAlt: Boolean,
    colorFilter: ColorFilter?,
    modifier: Modifier = Modifier
) {
    if (thumbnail != null) {
        Image(
            bitmap = thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = colorFilter,
            modifier = modifier
        )
    } else {
        Canvas(modifier = modifier) {
            val colors = if (accentAlt) {
                listOf(Color(0xFF00BFA5), Color(0xFF2962FF), Color(0xFF6200EA))
            } else {
                listOf(Color(0xFFFF2A85), Color(0xFFFF6D00), Color(0xFF3D5AFE))
            }
            drawRoundRect(
                brush = Brush.linearGradient(colors),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
            // Subtle geometric horizon & star emblem (classic Video Star look)
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawCircle(
                color = Color.White.copy(alpha = 0.24f),
                radius = size.minDimension * 0.36f,
                center = Offset(cx, cy),
                style = Stroke(1.5.dp.toPx())
            )
            val starPath = Path().apply {
                val outerR = size.minDimension * 0.27f
                val innerR = outerR * 0.44f
                for (i in 0 until 10) {
                    val ang = (-PI / 2.0) + i * (PI / 5.0)
                    val r = if (i % 2 == 0) outerR else innerR
                    val x = cx + (r * cos(ang)).toFloat()
                    val y = cy + (r * sin(ang)).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(path = starPath, color = Color.White.copy(alpha = 0.94f))
        }
    }
}
