package com.example.ui.screens.graphs

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.model.BezierCurveSpec
import com.example.model.GraphPreset
import com.example.ui.components.geometricTriangleBackground
import com.example.ui.theme.AdjustVideoTitleDark
import com.example.ui.theme.ClassicGreen
import com.example.ui.theme.ClassicRed
import com.example.ui.theme.VideoStarInkMuted
import com.example.ui.theme.VideoStarIosDivider
import com.example.ui.theme.VideoStarIosTrack
import com.example.ui.theme.VideoStarLightToolbar
import com.example.ui.theme.VideoStarWhite
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun KeyframeGraphsScreen(
    projectId: String,
    layerId: String,
    keyframeIndex: Int,
    onBack: () -> Unit
) {
    val viewModel: GraphViewModel = viewModel(
        key = "graph_${projectId}_${layerId}_$keyframeIndex",
        factory = remember(projectId, layerId, keyframeIndex) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return GraphViewModel(projectId, layerId, keyframeIndex) as T
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
            GraphTopBar(
                onBackClick = onBack,
                onApplyClick = { viewModel.applyAndReturn(onBack) }
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Interactive Cubic Bezier Canvas Card (280.dp x 280.dp)
                InteractiveBezierCanvasCard(
                    curve = uiState.curve,
                    onDragP1 = viewModel::updateP1,
                    onDragP2 = viewModel::updateP2
                )

                // Bezier Coordinates Pill Readout
                Text(
                    text = String.format(
                        Locale.US,
                        "P1(%.2f, %.2f)  •  P2(%.2f, %.2f)",
                        uiState.curve.p1x,
                        uiState.curve.p1y,
                        uiState.curve.p2x,
                        uiState.curve.p2y
                    ),
                    color = VideoStarInkMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.testTag("graph_coordinates_text")
                )

                // Graph Action Row: Copy, Paste, Invert, Apply All
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GraphActionButton(
                        label = "Copy",
                        tint = ClassicGreen,
                        testTag = "graph_copy_button",
                        onClick = viewModel::copyCurve
                    )
                    GraphActionButton(
                        label = "Paste",
                        tint = if (uiState.hasCopiedCurve) ClassicGreen else Color(0xFFB0B0B6),
                        enabled = uiState.hasCopiedCurve,
                        testTag = "graph_paste_button",
                        onClick = viewModel::pasteCurve
                    )
                    GraphActionButton(
                        label = "Invert",
                        tint = ClassicRed,
                        testTag = "graph_invert_button",
                        onClick = viewModel::invertCurve
                    )
                    GraphActionButton(
                        label = "Apply All",
                        tint = ClassicRed,
                        testTag = "graph_apply_all_button",
                        onClick = viewModel::applyToAllKeyframes
                    )
                }

                // Preset Curves Strip (54.dp x 54.dp white cards)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.presets, key = { it.name }) { preset ->
                        PresetCurveCard(
                            preset = preset,
                            isSelected = uiState.selectedPresetName == preset.name,
                            onClick = { viewModel.selectPreset(preset) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GraphTopBar(
    onBackClick: () -> Unit,
    onApplyClick: () -> Unit
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
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onBackClick)
                            .padding(horizontal = 8.dp)
                            .testTag("graph_back_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = "Back",
                            tint = ClassicGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Back",
                            color = ClassicGreen,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Text(
                        text = "Graph",
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
                            .clickable(onClick = onApplyClick)
                            .padding(horizontal = 12.dp)
                            .testTag("graph_apply_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Apply",
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
private fun InteractiveBezierCanvasCard(
    curve: BezierCurveSpec,
    onDragP1: (Float, Float) -> Unit,
    onDragP2: (Float, Float) -> Unit
) {
    var isDraggingHandle by remember { mutableStateOf(false) }

    val animatedP1 by animateOffsetAsState(
        targetValue = Offset(curve.p1x, curve.p1y),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = if (isDraggingHandle) Spring.StiffnessHigh else Spring.StiffnessMediumLow
        ),
        label = "bezier_p1"
    )
    val animatedP2 by animateOffsetAsState(
        targetValue = Offset(curve.p2x, curve.p2y),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = if (isDraggingHandle) Spring.StiffnessHigh else Spring.StiffnessMediumLow
        ),
        label = "bezier_p2"
    )

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = VideoStarWhite,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, VideoStarIosDivider),
        modifier = Modifier
            .size(280.dp)
            .testTag("graph_bezier_card")
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
            val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

            val startPt = Offset(0f, heightPx)
            val endPt = Offset(widthPx, 0f)
            val p1Px = Offset(animatedP1.x * widthPx, (1f - animatedP1.y) * heightPx)
            val p2Px = Offset(animatedP2.x * widthPx, (1f - animatedP2.y) * heightPx)

            Canvas(modifier = Modifier.fillMaxSize()) {
                // Fine #E5E5EA grid
                val gridSteps = 4
                for (i in 0..gridSteps) {
                    val x = size.width * i / gridSteps
                    val y = size.height * i / gridSteps
                    drawLine(VideoStarIosTrack, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                    drawLine(VideoStarIosTrack, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }

                // Dashed linear diagonal reference line (0,0) -> (1,1)
                drawLine(
                    color = Color(0xFFB8B8C0),
                    start = startPt,
                    end = endPt,
                    strokeWidth = 1.4.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                )

                // Thin tangent lines connecting (0,0)->P1 and (1,1)->P2
                drawLine(
                    color = ClassicRed.copy(alpha = 0.55f),
                    start = startPt,
                    end = p1Px,
                    strokeWidth = 1.6.dp.toPx()
                )
                drawLine(
                    color = ClassicGreen.copy(alpha = 0.55f),
                    start = endPt,
                    end = p2Px,
                    strokeWidth = 1.6.dp.toPx()
                )

                // Thick Cubic Bezier Curve with Red -> Green Gradient
                val bezierPath = Path().apply {
                    moveTo(startPt.x, startPt.y)
                    cubicTo(p1Px.x, p1Px.y, p2Px.x, p2Px.y, endPt.x, endPt.y)
                }
                drawPath(
                    path = bezierPath,
                    brush = Brush.linearGradient(
                        colors = listOf(ClassicRed, ClassicGreen),
                        start = startPt,
                        end = endPt
                    ),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )

                // Endpoint dots
                drawCircle(color = ClassicRed, radius = 4.5.dp.toPx(), center = startPt)
                drawCircle(color = ClassicGreen, radius = 4.5.dp.toPx(), center = endPt)
            }

            val handleSize = 26.dp
            val handleHalfPx = with(density) { (handleSize / 2).toPx() }

            // Draggable Handle P1 (Red)
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (curve.p1x * widthPx - handleHalfPx).roundToInt(),
                            ((1f - curve.p1y) * heightPx - handleHalfPx).roundToInt()
                        )
                    }
                    .size(handleSize)
                    .clip(CircleShape)
                    .background(ClassicRed)
                    .pointerInput(widthPx, heightPx) {
                        detectDragGestures(
                            onDragStart = { isDraggingHandle = true },
                            onDragEnd = { isDraggingHandle = false },
                            onDragCancel = { isDraggingHandle = false }
                        ) { change, drag ->
                            change.consume()
                            val nextX = (curve.p1x + drag.x / widthPx).coerceIn(0f, 1f)
                            val nextY = (curve.p1y - drag.y / heightPx).coerceIn(-0.45f, 1.45f)
                            onDragP1(nextX, nextY)
                        }
                    }
                    .testTag("graph_handle_p1"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(VideoStarWhite)
                )
            }

            // Draggable Handle P2 (Green)
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (curve.p2x * widthPx - handleHalfPx).roundToInt(),
                            ((1f - curve.p2y) * heightPx - handleHalfPx).roundToInt()
                        )
                    }
                    .size(handleSize)
                    .clip(CircleShape)
                    .background(ClassicGreen)
                    .pointerInput(widthPx, heightPx) {
                        detectDragGestures(
                            onDragStart = { isDraggingHandle = true },
                            onDragEnd = { isDraggingHandle = false },
                            onDragCancel = { isDraggingHandle = false }
                        ) { change, drag ->
                            change.consume()
                            val nextX = (curve.p2x + drag.x / widthPx).coerceIn(0f, 1f)
                            val nextY = (curve.p2y - drag.y / heightPx).coerceIn(-0.45f, 1.45f)
                            onDragP2(nextX, nextY)
                        }
                    }
                    .testTag("graph_handle_p2"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(VideoStarWhite)
                )
            }
        }
    }
}

@Composable
private fun GraphActionButton(
    label: String,
    tint: Color,
    enabled: Boolean = true,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = VideoStarWhite,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, VideoStarIosDivider),
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = tint,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PresetCurveCard(
    preset: GraphPreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("graph_preset_${preset.name.lowercase().replace(" ", "_").replace("-", "_")}")
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = VideoStarWhite,
            shadowElevation = if (isSelected) 3.dp else 1.dp,
            border = BorderStroke(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) ClassicGreen else VideoStarIosDivider
            ),
            modifier = Modifier.size(54.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            ) {
                val startPt = Offset(0f, size.height)
                val endPt = Offset(size.width, 0f)
                val c1 = Offset(
                    preset.spec.p1x * size.width,
                    ((1f - preset.spec.p1y) * size.height).coerceIn(-6f, size.height + 6f)
                )
                val c2 = Offset(
                    preset.spec.p2x * size.width,
                    ((1f - preset.spec.p2y) * size.height).coerceIn(-6f, size.height + 6f)
                )
                val miniPath = Path().apply {
                    moveTo(startPt.x, startPt.y)
                    cubicTo(c1.x, c1.y, c2.x, c2.y, endPt.x, endPt.y)
                }
                drawPath(
                    path = miniPath,
                    brush = Brush.linearGradient(listOf(ClassicRed, ClassicGreen)),
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        Text(
            text = preset.name,
            color = if (isSelected) ClassicGreen else AdjustVideoTitleDark,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
