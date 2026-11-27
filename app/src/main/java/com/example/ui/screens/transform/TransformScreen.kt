package com.example.ui.screens.transform

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.PrecisionGoldAccent
import com.example.ui.components.PrecisionRulerDialSlider
import com.example.ui.components.rememberStudioTickHaptics
import com.example.ui.components.ColorWheelIcon
import com.example.ui.components.IosHairlineDivider
import com.example.ui.components.IosSegmentedControl
import com.example.ui.components.IosThinSlider
import com.example.ui.components.RulerJogWheel
import com.example.ui.components.SceneRenderStage
import com.example.ui.components.geometricTriangleBackground
import com.example.ui.components.iosPressable
import com.example.ui.theme.AdjustVideoTitleDark
import com.example.ui.theme.ClassicGreen
import com.example.ui.theme.ClassicRed
import com.example.ui.theme.IosHairlineGray
import com.example.ui.theme.SfProDisplayFontFamily
import com.example.ui.theme.SfProTextFontFamily
import com.example.ui.theme.VideoStarInkDark
import com.example.ui.theme.VideoStarInkMuted
import com.example.ui.theme.VideoStarLightToolbar
import com.example.ui.theme.VideoStarNeonPink
import com.example.ui.theme.VideoStarWhite
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TransformScreen(
    projectId: String,
    sceneIndex: Int = 0,
    onCancel: () -> Unit,
    onCreated: () -> Unit
) {
    val viewModel: TransformViewModel = viewModel(
        key = "transform_${projectId}_$sceneIndex",
        factory = remember(projectId, sceneIndex) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TransformViewModel(projectId, sceneIndex) as T
                }
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val density = LocalDensity.current

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TransformTopBar(
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
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Center True Aspect-Ratio Preview Stage flanked by iOS 40dp white tool squares
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Column (4 buttons): Flip H, Flip V, Rotate 90°, Grid Toggle
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TransformToolSquareButton(
                            isActive = uiState.isFlippedHorizontal,
                            activeTint = ClassicGreen,
                            testTag = "transform_flip_h_button",
                            onClick = viewModel::toggleFlipHorizontal
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SwapHoriz,
                                contentDescription = "Flip Horizontal",
                                tint = ClassicGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        TransformToolSquareButton(
                            isActive = uiState.isFlippedVertical,
                            activeTint = ClassicGreen,
                            testTag = "transform_flip_v_button",
                            onClick = viewModel::toggleFlipVertical
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SwapVert,
                                contentDescription = "Flip Vertical",
                                tint = ClassicGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        TransformToolSquareButton(
                            isActive = false,
                            activeTint = ClassicGreen,
                            testTag = "transform_rotate_90_button",
                            onClick = viewModel::rotateBy90
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.RotateRight,
                                contentDescription = "Rotate 90 Degrees",
                                tint = ClassicGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        TransformToolSquareButton(
                            isActive = uiState.isGridVisible,
                            activeTint = ClassicRed,
                            testTag = "transform_grid_toggle_button",
                            onClick = viewModel::toggleGrid
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.GridOn,
                                contentDescription = "Toggle Alignment Grid",
                                tint = if (uiState.isGridVisible) ClassicRed else VideoStarInkMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Center Stage: Maximized box respecting true projectAspectRatio
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val availW = maxWidth
                        val availH = maxHeight
                        val targetRatio = uiState.aspectRatio.ratio.coerceAtLeast(0.25f)
                        val availRatio = if (availH > 0.dp) availW / availH else targetRatio
                        val (stageW, stageH) = if (availRatio > targetRatio) {
                            (availH * targetRatio) to availH
                        } else {
                            availW to (availW / targetRatio)
                        }

                        Box(
                            modifier = Modifier
                                .size(width = stageW, height = stageH)
                                .shadow(elevation = 4.dp, shape = RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                                .background(uiState.backgroundColor)
                                .border(0.5.dp, IosHairlineGray, RoundedCornerShape(8.dp))
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, rotation ->
                                        viewModel.onTransformGesture(pan.x, pan.y, zoom, rotation)
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = { viewModel.resetAll() }
                                    )
                                }
                                .testTag("transform_interactive_stage")
                        ) {
                            val preview = uiState.previewScene
                            if (preview != null) {
                                SceneRenderStage(
                                    scene = preview,
                                    localPositionMs = uiState.previewLocalPositionMs,
                                    isPlaying = false,
                                    useLiveVideoPlayer = false,
                                    contentDescription = "Transform Preview",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Import a clip first",
                                        color = VideoStarInkMuted,
                                        fontFamily = SfProTextFontFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            if (uiState.isGridVisible) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val gridColor = VideoStarNeonPink.copy(alpha = 0.56f)
                                    val strokePx = with(density) { 1.dp.toPx() }
                                    for (i in 1..3) {
                                        val x = size.width * i / 4f
                                        val y = size.height * i / 4f
                                        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = strokePx)
                                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = strokePx)
                                    }
                                }
                            }
                        }
                    }

                    // Right Column (5 buttons): 1px Nudge Arrows (^, <, >, v) + Color Wheel
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TransformToolSquareButton(
                            isActive = false,
                            activeTint = ClassicGreen,
                            testTag = "transform_dpad_up",
                            onClick = { viewModel.nudge(0f, -1f) }
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Nudge Up 1px", tint = ClassicGreen, modifier = Modifier.size(23.dp))
                        }

                        TransformToolSquareButton(
                            isActive = false,
                            activeTint = ClassicGreen,
                            testTag = "transform_dpad_left",
                            onClick = { viewModel.nudge(-1f, 0f) }
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Nudge Left 1px", tint = ClassicGreen, modifier = Modifier.size(23.dp))
                        }

                        TransformToolSquareButton(
                            isActive = false,
                            activeTint = ClassicGreen,
                            testTag = "transform_dpad_right",
                            onClick = { viewModel.nudge(1f, 0f) }
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Nudge Right 1px", tint = ClassicGreen, modifier = Modifier.size(23.dp))
                        }

                        TransformToolSquareButton(
                            isActive = false,
                            activeTint = ClassicGreen,
                            testTag = "transform_dpad_down",
                            onClick = { viewModel.nudge(0f, 1f) }
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Nudge Down 1px", tint = ClassicGreen, modifier = Modifier.size(23.dp))
                        }

                        TransformToolSquareButton(
                            isActive = uiState.isColorPickerVisible,
                            activeTint = ClassicRed,
                            testTag = "transform_color_wheel_button",
                            onClick = viewModel::toggleColorPicker
                        ) {
                            ColorWheelIcon(selectedColor = uiState.backgroundColor)
                        }
                    }
                }

                // Expandable iOS Background Swatch Bar
                if (uiState.isColorPickerVisible) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = VideoStarWhite,
                        shadowElevation = 2.dp,
                        border = BorderStroke(0.5.dp, IosHairlineGray),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(
                                Color(0xFF000000),
                                Color(0xFFFFFFFF),
                                ClassicRed,
                                ClassicGreen,
                                Color(0xFF1A237E),
                                Color(0xFFC81D77)
                            ).forEach { swatch ->
                                val selected = uiState.backgroundColor == swatch
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(swatch)
                                        .border(
                                            width = if (selected) 2.dp else 0.5.dp,
                                            color = if (selected) ClassicGreen else IosHairlineGray,
                                            shape = CircleShape
                                        )
                                        .iosPressable { viewModel.selectBackgroundColor(swatch) }
                                )
                            }
                        }
                    }
                }

                // Bottom iOS UIKit Control Dock
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = VideoStarWhite,
                    shadowElevation = 2.dp,
                    border = BorderStroke(0.5.dp, IosHairlineGray),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Row 1: Static / Start -> End Segmented Control + Mirror Edges active pill
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IosSegmentedControl(
                                items = TransformAnimationMode.entries,
                                selectedItem = if (uiState.isAnimatedMode) TransformAnimationMode.ANIMATED else TransformAnimationMode.STATIC,
                                onItemSelected = viewModel::setAnimationMode,
                                itemTagPrefix = "transform_mode",
                                modifier = Modifier.weight(1f)
                            ) { mode, isSelected ->
                                Text(
                                    text = mode.label,
                                    fontFamily = SfProTextFontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isSelected) VideoStarInkDark else VideoStarInkMuted,
                                    maxLines = 1
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (uiState.mirrorEdges) ClassicGreen else Color(0xFFF2F2F7),
                                border = BorderStroke(0.5.dp, if (uiState.mirrorEdges) ClassicGreen else IosHairlineGray),
                                modifier = Modifier
                                    .height(36.dp)
                                    .iosPressable(onClick = viewModel::toggleMirrorEdges)
                                    .testTag("transform_mirror_chip")
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Mirror Edges",
                                        fontFamily = SfProTextFontFamily,
                                        color = if (uiState.mirrorEdges) VideoStarWhite else VideoStarInkDark,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Endpoint & Easing Sub-Row when Start -> End animation mode is active
                        if (uiState.isAnimatedMode) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TransformEndpoint.entries.forEach { ep ->
                                    val selected = uiState.editingEndpoint == ep
                                    Surface(
                                        shape = RoundedCornerShape(7.dp),
                                        color = if (selected) ClassicGreen else Color(0xFFF2F2F7),
                                        border = BorderStroke(0.5.dp, if (selected) ClassicGreen else IosHairlineGray),
                                        modifier = Modifier
                                            .height(30.dp)
                                            .iosPressable { viewModel.selectEndpoint(ep) }
                                            .testTag("transform_endpoint_${ep.name.lowercase()}")
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = ep.label,
                                                fontFamily = SfProTextFontFamily,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (selected) VideoStarWhite else VideoStarInkDark
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(7.dp),
                                    color = if (uiState.isPlaying) ClassicRed else Color(0xFFF2F2F7),
                                    border = BorderStroke(0.5.dp, if (uiState.isPlaying) ClassicRed else IosHairlineGray),
                                    modifier = Modifier
                                        .size(width = 34.dp, height = 30.dp)
                                        .iosPressable(onClick = viewModel::togglePlayback)
                                        .testTag("transform_preview_play")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (uiState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                            contentDescription = "Preview Animation",
                                            tint = if (uiState.isPlaying) VideoStarWhite else ClassicGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                val haptics = rememberStudioTickHaptics()
                                listOf("Smooth", "Ease In", "Ease Out", "Bounce").forEach { ease ->
                                    val selected = uiState.easing == ease
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (selected) AdjustVideoTitleDark else Color(0xFFF2F2F7),
                                        modifier = Modifier
                                            .height(28.dp)
                                            .iosPressable {
                                                haptics.pulseSelection()
                                                viewModel.selectEasing(ease)
                                            }
                                            .testTag("transform_easing_${ease.lowercase().replace(" ", "_")}")
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 7.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = ease,
                                                fontFamily = SfProTextFontFamily,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (selected) VideoStarWhite else VideoStarInkDark,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        IosHairlineDivider()

                        // Row 2: Precision Tick-Ruler Dial for Size + Circular Green Reset
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val sizeHaptics = rememberStudioTickHaptics()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Size: ${(uiState.activeScale * 100f).roundToInt()}%",
                                    fontFamily = SfProDisplayFontFamily,
                                    color = AdjustVideoTitleDark,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.testTag("transform_zoom_label")
                                )
                                IosCircularResetButton(
                                    tint = ClassicGreen,
                                    contentDescription = "Reset Size",
                                    testTag = "transform_zoom_reset_button",
                                    onClick = {
                                        sizeHaptics.pulseSelection()
                                        viewModel.resetZoom()
                                    }
                                )
                            }
                            PrecisionRulerDialSlider(
                                label = "Size (%)",
                                valueText = "${(uiState.activeScale * 100f).roundToInt()}%",
                                value = uiState.activeScale,
                                onValueChange = viewModel::setZoom,
                                valueRange = 0.25f..3.0f,
                                defaultValue = 1.0f,
                                accentColor = PrecisionGoldAccent,
                                height = 42.dp,
                                sliderTestTag = "transform_zoom_slider"
                            )
                        }

                        IosHairlineDivider()

                        // Row 3: Angle Label + Circular Red Reset + Interactive Ruler Jog Wheel (0.5° step)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = String.format(Locale.US, "Angle: %.1f°", uiState.activeRotation),
                                    fontFamily = SfProDisplayFontFamily,
                                    color = AdjustVideoTitleDark,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.testTag("transform_angle_label")
                                )
                                IosCircularResetButton(
                                    tint = ClassicRed,
                                    contentDescription = "Reset Angle",
                                    testTag = "transform_angle_reset_button",
                                    onClick = viewModel::resetAngle
                                )
                            }
                            RulerJogWheel(
                                angleDegrees = uiState.activeRotation,
                                onAngleChange = viewModel::setAngle,
                                valueRange = -180f..180f,
                                stepDegrees = 0.5f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransformTopBar(
    onCancelClick: () -> Unit,
    onCreateClick: () -> Unit
) {
    Surface(
        color = VideoStarLightToolbar,
        shadowElevation = 0.dp
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
                            .iosPressable(onClick = onCancelClick)
                            .padding(horizontal = 10.dp)
                            .testTag("transform_cancel_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            color = ClassicRed,
                            fontFamily = SfProTextFontFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }

                    Text(
                        text = "Transform",
                        color = AdjustVideoTitleDark,
                        fontFamily = SfProDisplayFontFamily,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )

                    Box(
                        modifier = Modifier
                            .height(48.dp)
                            .iosPressable(onClick = onCreateClick)
                            .padding(horizontal = 10.dp)
                            .testTag("transform_create_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Create",
                            color = ClassicGreen,
                            fontFamily = SfProDisplayFontFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                IosHairlineDivider()
            }
        }
    }
}

@Composable
private fun TransformToolSquareButton(
    isActive: Boolean,
    activeTint: Color,
    testTag: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .iosPressable(onClick = onClick)
            .testTag(testTag)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isActive) activeTint.copy(alpha = 0.12f) else VideoStarWhite,
            shadowElevation = 2.dp,
            border = BorderStroke(0.5.dp, if (isActive) activeTint else IosHairlineGray),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                content()
            }
        }
    }
}

@Composable
private fun IosCircularResetButton(
    tint: Color,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f))
            .border(0.5.dp, tint.copy(alpha = 0.6f), CircleShape)
            .iosPressable(onClick = onClick)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
    }
}
