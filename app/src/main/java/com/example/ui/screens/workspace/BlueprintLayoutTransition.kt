package com.example.ui.screens.workspace

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.StudioLayoutMode
import com.example.ui.theme.InterFontFamily
import kotlin.math.roundToInt

private val BlueprintBackdropDark = Color(0xFF06120E)
private val BlueprintBoxFill = Color(0xFF0A1B14)
private val BlueprintNeonGreen = Color(0xFF00FF88)
private val BlueprintMutedMint = Color(0xFF88D4B4)

private const val SCRAMBLE_GLYPHS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#/%*"

@Immutable
data class BlueprintTransitionRequest(
    val id: Long,
    val fromLayout: StudioLayoutMode,
    val toLayout: StudioLayoutMode
)

/**
 * Computes smoothly interpolated phase timings across the 1000ms multi-stage Blueprint transition:
 * - Phase 1 (0ms - 200ms): Fade out current UI chrome while fading in Blueprint Wireframe overlay
 * - Phase 2 (200ms - 720ms): Hold Wireframe, scramble text title, morph structural containers toward target layout,
 *                            and commit target layout theme behind the overlay at ~650ms
 * - Phase 3 (720ms - 1000ms): Cross-fade Blueprint Wireframe out cleanly to reveal the new layout
 */
@Composable
fun BlueprintLayoutTransitionHost(
    request: BlueprintTransitionRequest?,
    previewBoundsInRoot: Rect?,
    rootOffsetInRoot: Offset,
    onCommitTargetLayout: (StudioLayoutMode) -> Unit,
    onTransitionFinished: () -> Unit,
    onChromeAlphaChanged: (Float) -> Unit
) {
    val progress = remember { Animatable(0f) }
    var committedForId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(request?.id) {
        if (request == null) {
            progress.snapTo(0f)
            onChromeAlphaChanged(1f)
            return@LaunchedEffect
        }
        committedForId = null
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = LinearEasing)
        ) {
            if (value >= 0.65f && committedForId != request.id) {
                committedForId = request.id
                onCommitTargetLayout(request.toLayout)
            }
            val chromeAlpha = when {
                value < 0.20f -> 1f - (value / 0.18f).coerceIn(0f, 1f)
                value < 0.72f -> 0f
                else -> ((value - 0.72f) / 0.28f).coerceIn(0f, 1f)
            }
            onChromeAlphaChanged(chromeAlpha)
        }
        onChromeAlphaChanged(1f)
        onTransitionFinished()
    }

    if (request == null) return

    val p = progress.value
    val enterEasing = remember { CubicBezierEasing(0.0f, 0.0f, 0.2f, 1f) }
    val morphEasing = remember { CubicBezierEasing(0.22f, 1f, 0.36f, 1f) }
    val exitEasing = remember { CubicBezierEasing(0.4f, 0f, 0.2f, 1f) }

    val overlayAlpha = when {
        p < 0.20f -> enterEasing.transform(((p - 0.04f) / 0.16f).coerceIn(0f, 1f))
        p < 0.72f -> 1f
        else -> 1f - exitEasing.transform(((p - 0.72f) / 0.28f).coerceIn(0f, 1f))
    }

    val morphFraction = morphEasing.transform(((p - 0.20f) / 0.50f).coerceIn(0f, 1f))
    val scrambleFraction = ((p - 0.16f) / 0.44f).coerceIn(0f, 1f)

    if (overlayAlpha <= 0.005f) return

    val localCutoutRect = remember(previewBoundsInRoot, rootOffsetInRoot) {
        previewBoundsInRoot?.let {
            Rect(
                left = it.left - rootOffsetInRoot.x,
                top = it.top - rootOffsetInRoot.y,
                right = it.right - rootOffsetInRoot.x,
                bottom = it.bottom - rootOffsetInRoot.y
            )
        }
    }

    BlueprintWireframeOverlay(
        fromLayout = request.fromLayout,
        toLayout = request.toLayout,
        overlayAlpha = overlayAlpha,
        morphProgress = morphFraction,
        scrambleProgress = scrambleFraction,
        previewCutout = localCutoutRect
    )
}

@Composable
private fun BlueprintWireframeOverlay(
    fromLayout: StudioLayoutMode,
    toLayout: StudioLayoutMode,
    overlayAlpha: Float,
    morphProgress: Float,
    scrambleProgress: Float,
    previewCutout: Rect?
) {
    val density = LocalDensity.current
    val gridSpacingPx = with(density) { 16.dp.toPx() }
    val dotRadiusPx = with(density) { 1.dp.toPx() }
    val cornerPx = with(density) { 12.dp.toPx() }
    val borderStrokePx = with(density) { 1.5.dp.toPx() }
    val glowStrokePx = with(density) { 5.dp.toPx() }

    val targetUpper = remember(toLayout) { toLayout.displayName.uppercase() }
    val scrambledTitle = remember(targetUpper, scrambleProgress) {
        scrambleTextReveal(targetUpper, scrambleProgress)
    }

    val timelineTrackCount = lerpFloat(
        start = layoutTimelineTracks(fromLayout).toFloat(),
        stop = layoutTimelineTracks(toLayout).toFloat(),
        fraction = morphProgress
    )
    val toolbarSlotCount = layoutTools(toLayout).size

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = overlayAlpha }
                .testTag("blueprint_transition_overlay")
        ) {
            val totalW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val totalH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

            val cutout = previewCutout ?: Rect(
                left = totalW * 0.14f,
                top = totalH * 0.14f,
                right = totalW * 0.86f,
                bottom = totalH * 0.50f
            )

            // Full-screen blueprint canvas with cutout window keeping central media preview sharp & live
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
            ) {
                // 1. Deep dark teal/black background
                drawRect(color = BlueprintBackdropDark.copy(alpha = 0.94f))

                // 2. Subtle neon-green 16dp dotted grid pattern
                var y = gridSpacingPx * 0.5f
                while (y < size.height) {
                    var x = gridSpacingPx * 0.5f
                    while (x < size.width) {
                        drawCircle(
                            color = BlueprintNeonGreen.copy(alpha = 0.20f),
                            radius = dotRadiusPx,
                            center = Offset(x, y)
                        )
                        x += gridSpacingPx
                    }
                    y += gridSpacingPx
                }

                // 3. Clear cutout around the live central preview so video/card remains crisp & static
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(cutout.left, cutout.top),
                    size = Size(cutout.width.coerceAtLeast(4f), cutout.height.coerceAtLeast(4f)),
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                    blendMode = BlendMode.Clear
                )

                // 4. Glowing wireframe container around the Preview Frame cutout
                drawWireframeFrame(
                    topLeft = Offset(cutout.left, cutout.top),
                    size = Size(cutout.width.coerceAtLeast(4f), cutout.height.coerceAtLeast(4f)),
                    cornerRadius = cornerPx,
                    strokePx = borderStrokePx,
                    glowPx = glowStrokePx,
                    fillAlpha = 0f
                )
            }

            // Structural Wireframe Zones (Top Bar + Middle Status Banner + Timeline Track Skeleton + Bottom Toolbar Skeleton)
            val horizontalPad = 16.dp
            val bannerTopDp = with(density) {
                (cutout.bottom.coerceAtMost(totalH * 0.58f)).toDp() + 10.dp
            }

            // Top Wireframe Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = horizontalPad, vertical = 8.dp)
            ) {
                val topBoxWidth = lerpFloat(140f, 175f, morphProgress).dp
                WireframeBox(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(topBoxWidth)
                        .height(38.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Canvas(modifier = Modifier.size(12.dp)) {
                            drawCircle(BlueprintNeonGreen, style = Stroke(width = 1.5.dp.toPx()))
                        }
                        Text(
                            text = targetUpper,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.5.sp,
                            color = BlueprintNeonGreen
                        )
                    }
                }

                WireframeBox(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(38.dp)
                ) {}
            }

            // Lower Structural Zones positioned below the live preview cutout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = bannerTopDp, start = horizontalPad, end = horizontalPad, bottom = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Zone 2: Middle Status Banner (Title + "Building layout..." + 5-6 neon wireframe icons)
                WireframeBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = scrambledTitle,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            letterSpacing = 4.sp,
                            color = BlueprintNeonGreen,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("blueprint_layout_title")
                        )
                        Text(
                            text = "Building layout...",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = BlueprintMutedMint,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("blueprint_building_subtitle")
                        )
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            layoutTools(toLayout).forEach { icon ->
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = BlueprintNeonGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Zone 3: Morphing Timeline Track Wireframe Container
                val timelineHeight = lerpFloat(
                    start = layoutTimelineHeightDp(fromLayout),
                    stop = layoutTimelineHeightDp(toLayout),
                    fraction = morphProgress
                ).dp

                WireframeBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                        .height(timelineHeight)
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        val tracks = timelineTrackCount.roundToInt().coerceIn(1, 4)
                        val gap = 6.dp.toPx()
                        val trackH = ((size.height - gap * (tracks - 1)) / tracks).coerceAtLeast(8.dp.toPx())
                        for (i in 0 until tracks) {
                            val topY = i * (trackH + gap)
                            val fracWidth = when {
                                toLayout == StudioLayoutMode.NODE_VIDEO -> 0.21f
                                i == 0 -> 0.86f
                                i == 1 -> 0.68f
                                else -> 0.54f
                            }
                            if (toLayout == StudioLayoutMode.NODE_VIDEO && i == 0) {
                                // Node boxes wireframe
                                val nodeW = size.width * 0.20f
                                val step = (size.width - nodeW * 4) / 3f
                                val midY = size.height * 0.5f
                                drawLine(
                                    color = BlueprintNeonGreen.copy(alpha = 0.45f),
                                    start = Offset(0f, midY),
                                    end = Offset(size.width, midY),
                                    strokeWidth = 1.5.dp.toPx()
                                )
                                repeat(4) { idx ->
                                    drawRoundRect(
                                        color = BlueprintNeonGreen.copy(alpha = 0.65f),
                                        topLeft = Offset(idx * (nodeW + step), size.height * 0.15f),
                                        size = Size(nodeW, size.height * 0.70f),
                                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                                        style = Stroke(width = 1.2.dp.toPx())
                                    )
                                }
                            } else {
                                drawRoundRect(
                                    color = BlueprintNeonGreen.copy(alpha = 0.55f),
                                    topLeft = Offset(size.width * (0.04f + i * 0.03f), topY),
                                    size = Size(size.width * fracWidth, trackH),
                                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                                    style = Stroke(width = 1.2.dp.toPx())
                                )
                            }
                        }
                        // Center playhead hairline wireframe
                        if (toLayout != StudioLayoutMode.NODE_VIDEO) {
                            drawLine(
                                color = BlueprintNeonGreen.copy(alpha = 0.85f),
                                start = Offset(size.width * 0.5f, 0f),
                                end = Offset(size.width * 0.5f, size.height),
                                strokeWidth = 1.5.dp.toPx()
                            )
                        }
                    }
                }

                // Zone 4: Bottom Toolbar Wireframe Container
                WireframeBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                        .height(54.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(toolbarSlotCount.coerceIn(4, 6)) {
                            Canvas(modifier = Modifier.size(width = 28.dp, height = 22.dp)) {
                                drawRoundRect(
                                    color = BlueprintNeonGreen.copy(alpha = 0.65f),
                                    cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                                    style = Stroke(width = 1.2.dp.toPx())
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WireframeBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val radiusPx = 12.dp.toPx()
            drawWireframeFrame(
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = radiusPx,
                strokePx = 1.5.dp.toPx(),
                glowPx = 5.dp.toPx(),
                fillAlpha = 0.78f
            )
        }
        content()
    }
}

private fun DrawScope.drawWireframeFrame(
    topLeft: Offset,
    size: Size,
    cornerRadius: Float,
    strokePx: Float,
    glowPx: Float,
    fillAlpha: Float
) {
    val corner = CornerRadius(cornerRadius, cornerRadius)
    if (fillAlpha > 0f) {
        drawRoundRect(
            color = BlueprintBoxFill.copy(alpha = fillAlpha),
            topLeft = topLeft,
            size = size,
            cornerRadius = corner
        )
    }
    // Outer neon-green glow halo (subtle box-shadow effect)
    drawRoundRect(
        color = BlueprintNeonGreen.copy(alpha = 0.22f),
        topLeft = topLeft,
        size = size,
        cornerRadius = corner,
        style = Stroke(width = glowPx)
    )
    // Sharp 1.5dp neon-green structural border
    drawRoundRect(
        color = BlueprintNeonGreen,
        topLeft = topLeft,
        size = size,
        cornerRadius = corner,
        style = Stroke(width = strokePx)
    )
}

private fun scrambleTextReveal(target: String, progress: Float): String {
    if (progress >= 0.96f) return target
    val revealedCount = (target.length * progress).toInt()
    val stepSeed = (progress * 18f).toInt()
    return buildString(target.length) {
        target.forEachIndexed { idx, ch ->
            if (ch == ' ' || idx < revealedCount) {
                append(ch)
            } else {
                val glyphIdx = (idx * 7 + stepSeed * 5) % SCRAMBLE_GLYPHS.length
                append(SCRAMBLE_GLYPHS[glyphIdx])
            }
        }
    }
}

private fun layoutTimelineTracks(mode: StudioLayoutMode): Int = when (mode) {
    StudioLayoutMode.NOVA -> 1
    StudioLayoutMode.CAPCUT -> 3
    StudioLayoutMode.BLURRR -> 2
    StudioLayoutMode.NODE_VIDEO -> 1
    StudioLayoutMode.AFTER_EFFECTS -> 3
}

private fun layoutTimelineHeightDp(mode: StudioLayoutMode): Float = when (mode) {
    StudioLayoutMode.NOVA -> 48f
    StudioLayoutMode.CAPCUT -> 92f
    StudioLayoutMode.BLURRR -> 74f
    StudioLayoutMode.NODE_VIDEO -> 84f
    StudioLayoutMode.AFTER_EFFECTS -> 96f
}

private fun layoutTools(mode: StudioLayoutMode): List<ImageVector> = when (mode) {
    StudioLayoutMode.NOVA -> listOf(
        Icons.Outlined.Videocam,
        Icons.Outlined.Image,
        Icons.Outlined.Layers,
        Icons.Outlined.AutoAwesome,
        Icons.Outlined.Tune
    )
    StudioLayoutMode.CAPCUT -> listOf(
        Icons.Outlined.ContentCut,
        Icons.Outlined.MusicNote,
        Icons.Outlined.Title,
        Icons.Outlined.AutoAwesome,
        Icons.Outlined.PhotoLibrary,
        Icons.Outlined.Subtitles
    )
    StudioLayoutMode.BLURRR -> listOf(
        Icons.Outlined.Bolt,
        Icons.Outlined.GraphicEq,
        Icons.Outlined.AutoFixHigh,
        Icons.Outlined.Animation,
        Icons.Outlined.Tune
    )
    StudioLayoutMode.NODE_VIDEO -> listOf(
        Icons.Outlined.AccountTree,
        Icons.Outlined.Hub,
        Icons.Outlined.PlayArrow,
        Icons.Outlined.AutoAwesome,
        Icons.Outlined.Layers
    )
    StudioLayoutMode.AFTER_EFFECTS -> listOf(
        Icons.Outlined.DesktopWindows,
        Icons.Outlined.Timeline,
        Icons.Outlined.Layers,
        Icons.Outlined.AutoAwesome,
        Icons.Outlined.Tune
    )
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction
