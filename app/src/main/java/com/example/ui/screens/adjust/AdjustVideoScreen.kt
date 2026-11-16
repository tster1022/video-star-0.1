package com.example.ui.screens.adjust

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
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
import com.example.model.ClipColorGrading
import com.example.ui.components.ColorWheelIcon
import com.example.ui.components.Media3TextureVideoPlayer
import com.example.ui.components.MediaUriThumbnail
import com.example.ui.components.formatTimeDecimal
import com.example.ui.components.geometricTriangleBackground
import com.example.ui.theme.AdjustVideoBackground
import com.example.ui.theme.AdjustVideoBadgeGray
import com.example.ui.theme.AdjustVideoColorWheelBg
import com.example.ui.theme.AdjustVideoHintGray
import com.example.ui.theme.AdjustVideoMagenta
import com.example.ui.theme.AdjustVideoOverlayScrim
import com.example.ui.theme.AdjustVideoTimestampPill
import com.example.ui.theme.AdjustVideoTitleDark
import com.example.ui.theme.AdjustVideoTopBar
import com.example.ui.theme.AdjustVideoTrimmerButton
import com.example.ui.theme.AdjustVideoWaveformBar
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.VideoStarIosDivider
import com.example.ui.theme.VideoStarWhite
import kotlin.math.roundToInt

@Composable
fun AdjustVideoScreen(
    projectId: String,
    mediaUriString: String,
    initialDurationMs: Long,
    initialSourceFps: Int,
    targetLayerIndex: Int? = null,
    replaceSceneIndex: Int? = null,
    onBack: () -> Unit,
    onMakeVideoComplete: () -> Unit
) {
    val viewModel: AdjustVideoViewModel = viewModel(
        key = "adjust_vid_${projectId}_${mediaUriString.hashCode()}_${targetLayerIndex}_$replaceSceneIndex",
        factory = remember(projectId, mediaUriString, initialDurationMs, initialSourceFps, targetLayerIndex, replaceSceneIndex) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AdjustVideoViewModel(
                        projectId = projectId,
                        initialUriString = mediaUriString,
                        initialDurationMs = initialDurationMs,
                        initialSourceFps = initialSourceFps,
                        targetLayerIndex = targetLayerIndex,
                        replaceSceneIndex = replaceSceneIndex
                    ) as T
                }
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .geometricTriangleBackground()
        ) {
            Scaffold(
                containerColor = AdjustVideoOverlayScrim,
                topBar = {
                    AdjustVideoTopBar(
                        onBackClick = onBack,
                        onMakeVideoClick = { viewModel.commitMakeVideo(onMakeVideoComplete) }
                    )
                },
                bottomBar = {
                    AdjustVideoBottomTrimmerBar(
                        currentPositionMs = uiState.currentPositionMs,
                        startMs = uiState.startMs,
                        endMs = uiState.endMs,
                        totalDurationMs = uiState.durationMs,
                        trimmedDurationMs = uiState.trimmedDurationMs,
                        waveformSamples = uiState.waveformSamples,
                        onSetStart = viewModel::setStartToCurrent,
                        onSetEnd = viewModel::setEndToCurrent,
                        onSeekProgress = viewModel::seekProgress
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Upper Toolbar above canvas:
                    // Left: Rotate 90 deg + Crop aspect ratio cycle
                    // Right: 4x 1px micro-nudge arrows (Down, Right, Left, Up)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AdjustToolbarPillButton(
                                onClick = viewModel::rotateBy90,
                                testTag = "adjust_rotate90_button"
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.RotateRight,
                                    contentDescription = "تدوير 90 درجة",
                                    tint = AdjustVideoMagenta,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            AdjustToolbarPillButton(
                                onClick = viewModel::cycleCropAspectRatio,
                                testTag = "adjust_crop_button"
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Crop,
                                    contentDescription = "تبديل نسبة القص",
                                    tint = AdjustVideoMagenta,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AdjustMicroNudgeButton(
                                onClick = viewModel::nudgeDown,
                                testTag = "adjust_nudge_down_button"
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "تحريك 1 بكسل للأسفل",
                                    tint = AdjustVideoMagenta,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            AdjustMicroNudgeButton(
                                onClick = viewModel::nudgeRight,
                                testTag = "adjust_nudge_right_button"
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = "تحريك 1 بكسل لليمين",
                                    tint = AdjustVideoMagenta,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            AdjustMicroNudgeButton(
                                onClick = viewModel::nudgeLeft,
                                testTag = "adjust_nudge_left_button"
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                    contentDescription = "تحريك 1 بكسل لليسار",
                                    tint = AdjustVideoMagenta,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            AdjustMicroNudgeButton(
                                onClick = viewModel::nudgeUp,
                                testTag = "adjust_nudge_up_button"
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowUp,
                                    contentDescription = "تحريك 1 بكسل للأعلى",
                                    tint = AdjustVideoMagenta,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // Interactive Video Preview Canvas
                    val aspectMode = uiState.effectiveAspectRatio
                    val colorMatrixArray = remember(uiState.colorGrading) {
                        if (uiState.colorGrading.isNeutral) null else uiState.colorGrading.toColorMatrixArray()
                    }
                    val composeColorFilter = remember(colorMatrixArray) {
                        colorMatrixArray?.let { ColorFilter.colorMatrix(ColorMatrix(it)) }
                    }

                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        val frameModifier = when (aspectMode) {
                            AspectRatioMode.SQUARE -> Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                            AspectRatioMode.LANDSCAPE -> Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                            AspectRatioMode.LANDSCAPE_4_3 -> Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f)
                            AspectRatioMode.PORTRAIT_3_4 -> Modifier
                                .fillMaxHeight(0.96f)
                                .aspectRatio(3f / 4f, matchHeightConstraintsFirst = true)
                            AspectRatioMode.PORTRAIT_4_5 -> Modifier
                                .fillMaxHeight(0.96f)
                                .aspectRatio(4f / 5f, matchHeightConstraintsFirst = true)
                            AspectRatioMode.PORTRAIT -> Modifier
                                .fillMaxHeight(0.96f)
                                .aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
                        }

                        Box(
                            modifier = frameModifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(uiState.backgroundColor)
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, rotation ->
                                        viewModel.onTransformGesture(pan.x, pan.y, zoom, rotation)
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = { viewModel.resetTransform() }
                                    )
                                }
                                .testTag("adjust_preview_canvas")
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        translationX = uiState.offsetX
                                        translationY = uiState.offsetY
                                        scaleX = uiState.scale
                                        scaleY = uiState.scale
                                        rotationZ = uiState.rotation
                                    }
                            ) {
                                if (!LocalInspectionMode.current && uiState.mediaUriString.isNotBlank()) {
                                    Media3TextureVideoPlayer(
                                        uriString = uiState.mediaUriString,
                                        startTrimMs = uiState.startMs,
                                        endTrimMs = uiState.endMs,
                                        isPlaying = uiState.isPlaying,
                                        seekPositionMs = uiState.currentPositionMs,
                                        isMuted = false,
                                        isFitMode = uiState.isFitMode,
                                        colorMatrixArray = colorMatrixArray,
                                        onPositionChanged = viewModel::syncPlaybackPosition,
                                        onPlaybackEnded = viewModel::onPlaybackEnded,
                                        onDurationDiscovered = viewModel::onMediaDurationDiscovered,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    MediaUriThumbnail(
                                        uriString = uiState.mediaUriString,
                                        isVideo = true,
                                        contentDescription = "معاينة الفيديو المحدد",
                                        colorFilter = composeColorFilter,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            // 4x4 Magenta Grid Overlay
                            if (uiState.isGridVisible) {
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("adjust_grid_overlay")
                                ) {
                                    val gridColor = AdjustVideoMagenta.copy(alpha = 0.65f)
                                    val strokeW = 1.dp.toPx()
                                    for (i in 1..3) {
                                        val x = size.width * (i / 4f)
                                        val y = size.height * (i / 4f)
                                        drawLine(
                                            color = gridColor,
                                            start = Offset(x, 0f),
                                            end = Offset(x, size.height),
                                            strokeWidth = strokeW
                                        )
                                        drawLine(
                                            color = gridColor,
                                            start = Offset(0f, y),
                                            end = Offset(size.width, y),
                                            strokeWidth = strokeW
                                        )
                                    }
                                }
                            }

                            // Top-Left FPS Badge (e.g. "30|30")
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AdjustVideoBadgeGray)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("adjust_fps_badge")
                            ) {
                                Text(
                                    text = "${uiState.projectFps}|${uiState.sourceFps}",
                                    style = TextStyle(
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        fontFeatureSettings = "tnum",
                                        color = VideoStarWhite
                                    )
                                )
                            }

                            // Top-Right Trimmed Duration Badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AdjustVideoBadgeGray)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("adjust_duration_badge")
                            ) {
                                Text(
                                    text = formatTimeDecimal(uiState.trimmedDurationMs),
                                    style = TextStyle(
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        fontFeatureSettings = "tnum",
                                        color = VideoStarWhite
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Arabic multi-touch gesture hint
                    Text(
                        text = "استخدم إصبع واحد للسحب وإصبعين لتغيير الحجم والتدوير، اضغط مرتين لإعادة الضبط.",
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = AdjustVideoHintGray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    )

                    // Expandable Zoom Slider Drawer
                    AnimatedVisibility(
                        visible = uiState.isZoomSliderVisible,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        AdjustZoomDrawer(
                            scale = uiState.scale,
                            onScaleChange = viewModel::setScale,
                            onResetTransform = viewModel::resetTransform
                        )
                    }

                    // Expandable Color & Grading Panel (Brightness, Contrast, Saturation, Hue + Background Swatches)
                    AnimatedVisibility(
                        visible = uiState.isColorPickerVisible,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        AdjustColorGradingDrawer(
                            colorGrading = uiState.colorGrading,
                            backgroundColor = uiState.backgroundColor,
                            onBrightnessChange = viewModel::updateBrightness,
                            onContrastChange = viewModel::updateContrast,
                            onSaturationChange = viewModel::updateSaturation,
                            onHueChange = viewModel::updateHue,
                            onSelectBackgroundColor = viewModel::selectBackgroundColor,
                            onResetGrading = viewModel::resetColorGrading
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Middle 5-Button Action Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AdjustToolbarPillButton(
                            onClick = viewModel::togglePlayback,
                            testTag = "adjust_play_button"
                        ) {
                            Icon(
                                imageVector = if (uiState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "تشغيل أو إيقاف",
                                tint = AdjustVideoMagenta,
                                modifier = Modifier.size(25.dp)
                            )
                        }

                        AdjustToolbarPillButton(
                            onClick = viewModel::toggleFitMode,
                            testTag = "adjust_fit_fill_button"
                        ) {
                            FilmFramePlayIcon(
                                tint = AdjustVideoMagenta,
                                isFitMode = uiState.isFitMode
                            )
                        }

                        AdjustToolbarPillButton(
                            onClick = viewModel::toggleZoomSlider,
                            testTag = "adjust_zoom_button"
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ZoomIn,
                                contentDescription = "شريط التكبير",
                                tint = if (uiState.isZoomSliderVisible) VideoStarWhite else AdjustVideoMagenta,
                                modifier = Modifier
                                    .size(24.dp)
                                    .let { mod ->
                                        if (uiState.isZoomSliderVisible) {
                                            mod
                                                .clip(CircleShape)
                                                .background(AdjustVideoMagenta)
                                                .padding(2.dp)
                                        } else {
                                            mod
                                        }
                                    }
                            )
                        }

                        AdjustToolbarPillButton(
                            onClick = viewModel::toggleGrid,
                            testTag = "adjust_grid_toggle_button"
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.GridOn,
                                contentDescription = "تبديل الشبكة والمحاذاة",
                                tint = if (uiState.isGridVisible) AdjustVideoMagenta else AdjustVideoMagenta.copy(alpha = 0.4f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .shadow(2.dp, RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                                .background(AdjustVideoColorWheelBg)
                                .border(
                                    width = if (uiState.isColorPickerVisible) 1.5.dp else 0.dp,
                                    color = if (uiState.isColorPickerVisible) AdjustVideoMagenta else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable(onClick = viewModel::toggleColorPicker)
                                .testTag("adjust_color_wheel_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            ColorWheelIcon(selectedColor = uiState.backgroundColor)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun AdjustVideoTopBar(
    onBackClick: () -> Unit,
    onMakeVideoClick: () -> Unit
) {
    Surface(color = AdjustVideoTopBar) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp)
            ) {
                // Left: "أنشئ فيديو"
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onMakeVideoClick)
                        .padding(horizontal = 8.dp)
                        .testTag("adjust_make_video_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "أنشئ فيديو",
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = AdjustVideoMagenta
                    )
                }

                // Center: "عدل الفيديو"
                Text(
                    text = "عدل الفيديو",
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = AdjustVideoTitleDark,
                    modifier = Modifier.align(Alignment.Center)
                )

                // Right: "> الخلف"
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onBackClick)
                        .padding(horizontal = 6.dp)
                        .testTag("adjust_videos_back_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الخلف",
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = AdjustVideoMagenta
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "الخلف",
                        tint = AdjustVideoMagenta,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            HorizontalDivider(thickness = 1.dp, color = VideoStarIosDivider)
        }
    }
}

@Composable
internal fun AdjustZoomDrawer(
    scale: Float,
    onScaleChange: (Float) -> Unit,
    onResetTransform: () -> Unit
) {
    Surface(
        color = VideoStarWhite.copy(alpha = 0.96f),
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .testTag("adjust_zoom_slider_panel")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "تكبير ${String.format(java.util.Locale.US, "%d%%", (scale * 100f).roundToInt())}",
                fontFamily = CairoFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = AdjustVideoTitleDark
            )
            Slider(
                value = scale,
                onValueChange = onScaleChange,
                valueRange = 0.35f..3.5f,
                colors = SliderDefaults.colors(
                    thumbColor = AdjustVideoMagenta,
                    activeTrackColor = AdjustVideoMagenta,
                    inactiveTrackColor = VideoStarIosDivider
                ),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "إعادة",
                fontFamily = CairoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = AdjustVideoMagenta,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onResetTransform)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
internal fun AdjustColorGradingDrawer(
    colorGrading: ClipColorGrading,
    backgroundColor: Color,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onHueChange: (Float) -> Unit,
    onSelectBackgroundColor: (Color) -> Unit,
    onResetGrading: () -> Unit
) {
    val quickColors = remember {
        listOf(
            Color.Black,
            Color.White,
            AdjustVideoMagenta,
            Color(0xFF1A4B6E),
            Color(0xFF280B1E),
            Color(0xFF00C2FF),
            Color(0xFFFFB74D)
        )
    }

    Surface(
        color = VideoStarWhite.copy(alpha = 0.97f),
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .testTag("adjust_floating_color_bar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    quickColors.forEach { swatch ->
                        val isSelected = backgroundColor == swatch
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) AdjustVideoMagenta else VideoStarIosDivider,
                                    shape = CircleShape
                                )
                                .clickable { onSelectBackgroundColor(swatch) }
                        )
                    }
                }
                Text(
                    text = "إعادة ضبط الألوان",
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = AdjustVideoMagenta,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onResetGrading)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }

            GradingSliderRow(
                label = "السطوع",
                value = colorGrading.brightness,
                valueRange = -0.5f..0.5f,
                onValueChange = onBrightnessChange
            )
            GradingSliderRow(
                label = "التباين",
                value = colorGrading.contrast,
                valueRange = 0.5f..1.8f,
                onValueChange = onContrastChange
            )
            GradingSliderRow(
                label = "التشبع",
                value = colorGrading.saturation,
                valueRange = 0f..2f,
                onValueChange = onSaturationChange
            )
            GradingSliderRow(
                label = "تدرج اللون",
                value = colorGrading.hue,
                valueRange = -180f..180f,
                onValueChange = onHueChange
            )
        }
    }
}

@Composable
private fun GradingSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            fontFamily = CairoFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = AdjustVideoTitleDark,
            modifier = Modifier.width(62.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = AdjustVideoMagenta,
                activeTrackColor = AdjustVideoMagenta,
                inactiveTrackColor = VideoStarIosDivider
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun AdjustToolbarPillButton(
    onClick: () -> Unit,
    testTag: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(VideoStarWhite)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
internal fun AdjustMicroNudgeButton(
    onClick: () -> Unit,
    testTag: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .shadow(elevation = 1.5.dp, shape = RoundedCornerShape(7.dp))
            .clip(RoundedCornerShape(7.dp))
            .background(VideoStarWhite)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun FilmFramePlayIcon(
    tint: Color,
    isFitMode: Boolean
) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = tint,
            topLeft = Offset(1.dp.toPx(), 3.dp.toPx()),
            size = Size(w - 2.dp.toPx(), h - 6.dp.toPx()),
            cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
            style = Stroke(width = if (isFitMode) 2.2.dp.toPx() else 1.5.dp.toPx())
        )
        val triPath = Path().apply {
            moveTo(w * 0.42f, h * 0.34f)
            lineTo(w * 0.68f, h * 0.50f)
            lineTo(w * 0.42f, h * 0.66f)
            close()
        }
        drawPath(path = triPath, color = tint)
    }
}

@Composable
private fun AdjustVideoBottomTrimmerBar(
    currentPositionMs: Long,
    startMs: Long,
    endMs: Long,
    totalDurationMs: Long,
    trimmedDurationMs: Long,
    waveformSamples: List<Float>,
    onSetStart: () -> Unit,
    onSetEnd: () -> Unit,
    onSeekProgress: (Float) -> Unit
) {
    val safeTotal = totalDurationMs.coerceAtLeast(500L)
    val currentFraction = (currentPositionMs.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)
    val startFraction = (startMs.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)
    val endFraction = (endMs.toFloat() / safeTotal.toFloat()).coerceIn(startFraction, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AdjustVideoOverlayScrim)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "المدة ${formatTimeDecimal(trimmedDurationMs)}",
            style = TextStyle(
                fontFamily = CairoFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                fontFeatureSettings = "tnum",
                color = VideoStarWhite
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(bottom = 2.dp)
                .testTag("adjust_duration_text")
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(54.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Button: "تعيين البداية"
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .fillMaxHeight()
                        .background(AdjustVideoTrimmerButton)
                        .clickable(onClick = onSetStart)
                        .testTag("adjust_set_start_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "تعيين البداية",
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = AdjustVideoMagenta,
                        textAlign = TextAlign.Center
                    )
                }

                // Center Real Extracted PCM Waveform Scrubber + White Circular Handle
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(AdjustVideoBackground)
                        .pointerInput(safeTotal) {
                            detectTapGestures { offset ->
                                val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                onSeekProgress(frac)
                            }
                        }
                        .pointerInput(safeTotal) {
                            detectHorizontalDragGestures { change, _ ->
                                change.consume()
                                val frac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                onSeekProgress(frac)
                            }
                        }
                        .testTag("adjust_waveform_scrubber"),
                    contentAlignment = Alignment.CenterStart
                ) {
                    val density = LocalDensity.current
                    val trackWidthPx = with(density) { maxWidth.toPx() }
                    val thumbSize = 22.dp
                    val thumbPx = with(density) { thumbSize.toPx() }
                    val usablePx = (trackWidthPx - thumbPx).coerceAtLeast(1f)
                    val thumbOffsetX = (currentFraction * usablePx).roundToInt()

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val startX = startFraction * w
                        val endX = endFraction * w

                        drawRect(
                            color = Color.White.copy(alpha = 0.14f),
                            topLeft = Offset(startX, 0f),
                            size = Size((endX - startX).coerceAtLeast(2f), h)
                        )

                        val count = waveformSamples.size.coerceAtLeast(1)
                        val step = w / count.toFloat()
                        val barWidth = (step * 0.45f).coerceIn(1.5.dp.toPx(), 3.dp.toPx())
                        waveformSamples.forEachIndexed { idx, amp ->
                            val x = idx * step + (step - barWidth) / 2f
                            val barH = (h * 0.72f * amp).coerceAtLeast(4.dp.toPx())
                            val top = (h - barH) / 2f
                            val inSelection = x in startX..endX
                            drawRoundRect(
                                color = if (inSelection) AdjustVideoWaveformBar else AdjustVideoWaveformBar.copy(alpha = 0.55f),
                                topLeft = Offset(x, top),
                                size = Size(barWidth, barH),
                                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                            )
                        }

                        drawLine(
                            color = VideoStarWhite.copy(alpha = 0.35f),
                            start = Offset(0f, h / 2f),
                            end = Offset(w, h / 2f),
                            strokeWidth = 1.5.dp.toPx()
                        )

                        drawLine(
                            color = AdjustVideoMagenta,
                            start = Offset(startX, 0f),
                            end = Offset(startX, h),
                            strokeWidth = 2.dp.toPx()
                        )
                        drawLine(
                            color = AdjustVideoMagenta,
                            start = Offset(endX, 0f),
                            end = Offset(endX, h),
                            strokeWidth = 2.dp.toPx()
                        )
                    }

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(thumbOffsetX, 0) }
                            .size(thumbSize)
                            .shadow(4.dp, CircleShape)
                            .clip(CircleShape)
                            .background(VideoStarWhite)
                    )
                }

                // Right Button: "تعيين النهاية"
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .fillMaxHeight()
                        .background(AdjustVideoTrimmerButton)
                        .clickable(onClick = onSetEnd)
                        .testTag("adjust_set_end_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "تعيين النهاية",
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = AdjustVideoMagenta,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Floating Playhead Timestamp Pill above waveform
            BoxWithConstraints(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 96.dp)
            ) {
                val density = LocalDensity.current
                val pillWidth = 54.dp
                val pillPx = with(density) { pillWidth.toPx() }
                val usableWidthPx = with(density) { maxWidth.toPx() - pillPx }.coerceAtLeast(1f)
                val pillX = (currentFraction * usableWidthPx).roundToInt()

                Box(
                    modifier = Modifier
                        .offset { IntOffset(pillX, 0) }
                        .width(pillWidth)
                        .height(22.dp)
                        .shadow(2.dp, RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .background(AdjustVideoTimestampPill)
                        .testTag("adjust_floating_timestamp"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatTimeDecimal(currentPositionMs),
                        style = TextStyle(
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            fontFeatureSettings = "tnum",
                            color = AdjustVideoTitleDark
                        )
                    )
                }
            }
        }
    }
}
