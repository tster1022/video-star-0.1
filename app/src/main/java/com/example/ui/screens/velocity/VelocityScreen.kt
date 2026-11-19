package com.example.ui.screens.velocity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.AspectRatioMode
import com.example.model.VelocityPoint
import com.example.ui.components.IosThinSlider
import com.example.ui.components.MediaClipStageContent
import com.example.ui.components.SceneRenderStage
import com.example.ui.components.geometricTriangleBackground
import com.example.ui.theme.AdjustVideoTitleDark
import com.example.ui.theme.ClassicGreen
import com.example.ui.theme.ClassicPlayerOverlay
import com.example.ui.theme.ClassicRed
import com.example.ui.theme.VideoStarInkDark
import com.example.ui.theme.VideoStarInkMuted
import com.example.ui.theme.VideoStarIosDivider
import com.example.ui.theme.VideoStarIosTrack
import com.example.ui.theme.VideoStarLightToolbar
import com.example.ui.theme.VideoStarWhite
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun VelocityScreen(
    projectId: String,
    sceneIndex: Int = 0,
    onCancel: () -> Unit,
    onCreated: () -> Unit
) {
    val viewModel: VelocityViewModel = viewModel(
        key = "velocity_${projectId}_$sceneIndex",
        factory = remember(projectId, sceneIndex) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return VelocityViewModel(projectId, sceneIndex) as T
                }
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            VelocityTopBar(
                onCancelClick = onCancel,
                onCreateClick = { viewModel.createAndSave(onCreated) }
            )
        }
    ) { innerPadding ->
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .geometricTriangleBackground()
                    .padding(innerPadding)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Upper Half: Video Preview Frame + Semi-Transparent Playback & Speed Bar
                Column(
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val ratioModifier = when (uiState.aspectRatio) {
                        AspectRatioMode.SQUARE -> Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true)
                        AspectRatioMode.PORTRAIT -> Modifier.fillMaxHeight().aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
                        AspectRatioMode.PORTRAIT_3_4 -> Modifier.fillMaxHeight().aspectRatio(3f / 4f, matchHeightConstraintsFirst = true)
                        AspectRatioMode.PORTRAIT_4_5 -> Modifier.fillMaxHeight().aspectRatio(4f / 5f, matchHeightConstraintsFirst = true)
                        AspectRatioMode.LANDSCAPE_4_3 -> Modifier.fillMaxHeight().aspectRatio(4f / 3f, matchHeightConstraintsFirst = true)
                        AspectRatioMode.LANDSCAPE -> Modifier.fillMaxWidth(0.92f).aspectRatio(16f / 9f)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = ratioModifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black)
                                .border(1.dp, VideoStarIosDivider, RoundedCornerShape(10.dp))
                                .clickable(onClick = viewModel::togglePlayback)
                                .testTag("velocity_preview_frame")
                        ) {
                            val activeScene = uiState.baseScene
                            if (activeScene != null) {
                                SceneRenderStage(
                                    scene = activeScene.copy(
                                        velocityPoints = uiState.points,
                                        isVelocitySmooth = uiState.isSmooth
                                    ),
                                    localPositionMs = (uiState.currentProgress * uiState.durationMs).toLong(),
                                    isPlaying = uiState.isPlaying,
                                    speedMultiplier = uiState.instantSpeed,
                                    contentDescription = "Velocity Preview",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                MediaClipStageContent(
                                    clip = uiState.mediaClip,
                                    isPlaying = uiState.isPlaying,
                                    positionMs = (uiState.currentProgress * uiState.durationMs).toLong(),
                                    playbackSpeed = uiState.instantSpeed,
                                    isFitMode = uiState.mediaClip?.isFitMode ?: false,
                                    contentDescription = "Velocity Preview",
                                    emptyLabel = "استورد مقطعاً أولاً",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            val motionScale = 1f + uiState.currentProgress * 0.14f
                                            scaleX = motionScale
                                            scaleY = motionScale
                                            rotationZ = (uiState.currentProgress - 0.5f) * 6f
                                        }
                                )
                            }
                        }
                    }

                    // Semi-Hidden Frosted Scrubber Bar with Play, Hairline Thumb, and Live Speed Readout
                    Surface(
                        shape = RoundedCornerShape(11.dp),
                        color = Color(0xFF101015).copy(alpha = 0.28f),
                        border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.16f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = viewModel::togglePlayback,
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("velocity_play_button")
                            ) {
                                Icon(
                                    imageVector = if (uiState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = ClassicGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IosThinSlider(
                                value = uiState.currentProgress,
                                onValueChange = viewModel::scrubTo,
                                activeTrackColor = ClassicGreen,
                                inactiveTrackColor = VideoStarWhite.copy(alpha = 0.24f),
                                thumbSize = 11.dp,
                                trackHeight = 2.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("velocity_scrubber_slider")
                            )

                            Surface(
                                shape = RoundedCornerShape(7.dp),
                                color = VideoStarWhite.copy(alpha = 0.90f),
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%.2fx", uiState.instantSpeed),
                                    color = if (uiState.instantSpeed >= 1.0f) ClassicGreen else ClassicRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                        .testTag("velocity_instant_speed_text")
                                )
                            }
                        }
                    }
                }

                // Lower Half: Interactive Velocity Curve Editor (0.1x .. 4.0x around 1.0x center line)
                VelocityCurveEditorCard(
                    points = uiState.points,
                    selectedPointId = uiState.selectedPointId,
                    currentProgress = uiState.currentProgress,
                    isSmooth = uiState.isSmooth,
                    onPointDragged = viewModel::updatePoint,
                    onSelectPoint = viewModel::selectPoint,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                // Bottom Bar: Quick Speed Presets + Smooth Toggle + Add Point
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VelocityPreset.entries.forEach { preset ->
                            val selected = uiState.activePreset == preset
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) ClassicGreen else VideoStarWhite,
                                shadowElevation = 1.dp,
                                border = BorderStroke(1.dp, if (selected) ClassicGreen else VideoStarIosDivider),
                                modifier = Modifier
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { viewModel.applyPreset(preset) }
                                    .testTag("velocity_preset_${preset.name.lowercase()}")
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = preset.label,
                                        color = if (selected) VideoStarWhite else AdjustVideoTitleDark,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // Smooth Toggle Button (Green when enabled)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (uiState.isSmooth) ClassicGreen else VideoStarWhite,
                        shadowElevation = 2.dp,
                        border = BorderStroke(1.dp, if (uiState.isSmooth) ClassicGreen else VideoStarIosDivider),
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = viewModel::toggleSmooth)
                            .testTag("velocity_smooth_toggle")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Smooth",
                                color = if (uiState.isSmooth) VideoStarWhite else ClassicGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // + Point Button
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = VideoStarWhite,
                        shadowElevation = 2.dp,
                        border = BorderStroke(1.dp, ClassicRed),
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = viewModel::addPointAtCurrentTime)
                            .testTag("velocity_add_point_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Add Point",
                                tint = ClassicRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Point",
                                color = ClassicRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VelocityTopBar(
    onCancelClick: () -> Unit,
    onCreateClick: () -> Unit
) {
    Surface(
        color = VideoStarLightToolbar,
        shadowElevation = 1.dp
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onCancelClick)
                            .padding(horizontal = 10.dp)
                            .testTag("velocity_cancel_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            color = ClassicRed,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Text(
                        text = "Velocity",
                        color = AdjustVideoTitleDark,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )

                    Box(
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onCreateClick)
                            .padding(horizontal = 10.dp)
                            .testTag("velocity_create_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Create",
                            color = ClassicGreen,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                HorizontalDivider(thickness = 0.8.dp, color = VideoStarIosDivider)
            }
        }
    }
}

@Composable
private fun VelocityCurveEditorCard(
    points: List<VelocityPoint>,
    selectedPointId: String?,
    currentProgress: Float,
    isSmooth: Boolean,
    onPointDragged: (String, Float, Float) -> Unit,
    onSelectPoint: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = VideoStarWhite,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, VideoStarIosDivider),
        modifier = modifier.testTag("velocity_curve_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Vertical Scale Axis Labels (4.0x top, 1.0x middle, 0.1x bottom)
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text("4.0x", fontSize = 10.sp, color = ClassicGreen, fontWeight = FontWeight.Bold)
                Text("1.0x", fontSize = 10.sp, color = VideoStarInkDark, fontWeight = FontWeight.Bold)
                Text("0.1x", fontSize = 10.sp, color = ClassicRed, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(8.dp))

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(vertical = 12.dp, horizontal = 8.dp)
            ) {
                val density = LocalDensity.current
                val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
                val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
                val sorted = points.sortedBy { it.timeProgress }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val midY = size.height / 2f

                    // Subtle grid lines
                    drawLine(VideoStarIosTrack, Offset(0f, size.height * 0.25f), Offset(size.width, size.height * 0.25f), 1.dp.toPx())
                    drawLine(VideoStarIosTrack, Offset(0f, size.height * 0.75f), Offset(size.width, size.height * 0.75f), 1.dp.toPx())

                    // Horizontal dashed 1.0x reference line across center
                    drawLine(
                        color = Color(0xFF9E9EA6),
                        start = Offset(0f, midY),
                        end = Offset(size.width, midY),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                    )

                    // Vertical playhead line
                    val cursorX = currentProgress * size.width
                    drawLine(
                        color = VideoStarInkMuted.copy(alpha = 0.45f),
                        start = Offset(cursorX, 0f),
                        end = Offset(cursorX, size.height),
                        strokeWidth = 1.2.dp.toPx()
                    )

                    // Draw Green/Red Speed Curve through control points
                    if (sorted.size >= 2) {
                        val coords = sorted.map { pt ->
                            Offset(
                                x = pt.timeProgress * size.width,
                                y = speedToCanvasY(pt.speedMultiplier, size.height)
                            )
                        }
                        val curvePath = Path().apply {
                            moveTo(coords.first().x, coords.first().y)
                            for (i in 0 until coords.lastIndex) {
                                val a = coords[i]
                                val b = coords[i + 1]
                                if (isSmooth) {
                                    val midX = (a.x + b.x) / 2f
                                    cubicTo(midX, a.y, midX, b.y, b.x, b.y)
                                } else {
                                    lineTo(b.x, b.y)
                                }
                            }
                        }
                        drawPath(
                            path = curvePath,
                            brush = Brush.verticalGradient(
                                colors = listOf(ClassicGreen, ClassicRed)
                            ),
                            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // Draggable circular control points
                val dotSize = 22.dp
                val halfDotPx = with(density) { (dotSize / 2).toPx() }
                sorted.forEach { pt ->
                    val cx = pt.timeProgress * widthPx
                    val cy = speedToCanvasY(pt.speedMultiplier, heightPx)
                    val isSelected = pt.id == selectedPointId
                    val dotColor = if (pt.speedMultiplier >= 1.0f) ClassicGreen else ClassicRed

                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (cx - halfDotPx).roundToInt(),
                                    (cy - halfDotPx).roundToInt()
                                )
                            }
                            .size(dotSize)
                            .clip(CircleShape)
                            .background(dotColor)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.5.dp,
                                color = VideoStarWhite,
                                shape = CircleShape
                            )
                            .pointerInput(pt.id, widthPx, heightPx) {
                                detectDragGestures(
                                    onDragStart = { onSelectPoint(pt.id) }
                                ) { change, drag ->
                                    change.consume()
                                    val nextTime = pt.timeProgress + drag.x / widthPx
                                    val nextY = (speedToCanvasY(pt.speedMultiplier, heightPx) + drag.y)
                                        .coerceIn(0f, heightPx)
                                    val nextSpeed = canvasYToSpeed(nextY, heightPx)
                                    onPointDragged(pt.id, nextTime, nextSpeed)
                                }
                            }
                            .testTag("velocity_point_${pt.id}")
                    )
                }
            }
        }
    }
}

/**
 * Piece-wise linear Y mapping:
 * - speed in [1.0f .. 4.0f] maps to top half [height * 0.5f .. 0f]
 * - speed in [0.1f .. 1.0f] maps to bottom half [height .. height * 0.5f]
 */
private fun speedToCanvasY(speed: Float, height: Float): Float {
    val half = height / 2f
    return if (speed >= 1.0f) {
        val ratio = ((speed - 1.0f) / 3.0f).coerceIn(0f, 1f)
        half * (1f - ratio)
    } else {
        val ratio = ((1.0f - speed) / 0.9f).coerceIn(0f, 1f)
        half + half * ratio
    }
}

private fun canvasYToSpeed(y: Float, height: Float): Float {
    val half = (height / 2f).coerceAtLeast(1f)
    return if (y <= half) {
        val ratio = ((half - y) / half).coerceIn(0f, 1f)
        1.0f + ratio * 3.0f
    } else {
        val ratio = ((y - half) / half).coerceIn(0f, 1f)
        (1.0f - ratio * 0.9f).coerceIn(0.1f, 1.0f)
    }
}
