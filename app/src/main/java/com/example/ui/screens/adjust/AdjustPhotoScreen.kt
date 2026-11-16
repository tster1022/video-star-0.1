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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.model.AspectRatioMode
import com.example.ui.components.ColorWheelIcon
import com.example.ui.components.formatTimeDecimal
import com.example.ui.components.geometricTriangleBackground
import com.example.ui.components.resolveCoilModel
import com.example.ui.theme.AdjustVideoBadgeGray
import com.example.ui.theme.AdjustVideoColorWheelBg
import com.example.ui.theme.AdjustVideoHintGray
import com.example.ui.theme.AdjustVideoMagenta
import com.example.ui.theme.AdjustVideoOverlayScrim
import com.example.ui.theme.AdjustVideoTitleDark
import com.example.ui.theme.AdjustVideoTopBar
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.VideoStarIosDivider
import com.example.ui.theme.VideoStarWhite
import java.util.Locale
import kotlin.math.abs

@Composable
fun AdjustPhotoScreen(
    projectId: String,
    mediaUriString: String,
    targetLayerIndex: Int? = null,
    replaceSceneIndex: Int? = null,
    onBack: () -> Unit,
    onMakePhotoComplete: () -> Unit
) {
    val viewModel: AdjustPhotoViewModel = viewModel(
        key = "adjust_photo_${projectId}_${mediaUriString.hashCode()}_${targetLayerIndex}_$replaceSceneIndex",
        factory = remember(projectId, mediaUriString, targetLayerIndex, replaceSceneIndex) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AdjustPhotoViewModel(
                        projectId = projectId,
                        initialUriString = mediaUriString,
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
                    AdjustPhotoTopBar(
                        onBackClick = onBack,
                        onMakePhotoClick = { viewModel.commitMakePhoto(onMakePhotoComplete) }
                    )
                },
                bottomBar = {
                    AdjustPhotoDurationBar(
                        durationMs = uiState.durationMs,
                        onDurationSecondsChanged = viewModel::setDurationSeconds
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
                    // Upper Toolbar: Left = Rotate 90 + Crop + Reset; Right = 4x 1px nudges
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
                                testTag = "adjust_photo_reset_button"
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
                                testTag = "adjust_photo_crop_button"
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
                                onClick = { viewModel.nudge(0f, 1f) },
                                testTag = "adjust_photo_nudge_down"
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "تحريك للأسفل",
                                    tint = AdjustVideoMagenta,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            AdjustMicroNudgeButton(
                                onClick = { viewModel.nudge(1f, 0f) },
                                testTag = "adjust_photo_nudge_right"
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = "تحريك لليمين",
                                    tint = AdjustVideoMagenta,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            AdjustMicroNudgeButton(
                                onClick = { viewModel.nudge(-1f, 0f) },
                                testTag = "adjust_photo_nudge_left"
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                    contentDescription = "تحريك لليسار",
                                    tint = AdjustVideoMagenta,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            AdjustMicroNudgeButton(
                                onClick = { viewModel.nudge(0f, -1f) },
                                testTag = "adjust_photo_nudge_up"
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowUp,
                                    contentDescription = "تحريك للأعلى",
                                    tint = AdjustVideoMagenta,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    val aspectMode = uiState.effectiveAspectRatio
                    val composeColorFilter = remember(uiState.colorGrading) {
                        if (uiState.colorGrading.isNeutral) null
                        else ColorFilter.colorMatrix(ColorMatrix(uiState.colorGrading.toColorMatrixArray()))
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
                                .testTag("adjust_photo_preview_canvas")
                        ) {
                            AsyncImage(
                                model = resolveCoilModel(uiState.mediaUriString),
                                contentDescription = "معاينة الصورة المحددة",
                                contentScale = if (uiState.isFitMode) ContentScale.Fit else ContentScale.Crop,
                                colorFilter = composeColorFilter,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val hSign = if (uiState.isFlippedHorizontal) -1f else 1f
                                        val vSign = if (uiState.isFlippedVertical) -1f else 1f
                                        translationX = uiState.offsetX
                                        translationY = uiState.offsetY
                                        scaleX = uiState.scale * hSign
                                        scaleY = uiState.scale * vSign
                                        rotationZ = uiState.rotation
                                    }
                            )

                            if (uiState.isGridVisible) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val gridColor = AdjustVideoMagenta.copy(alpha = 0.65f)
                                    val strokeW = 1.dp.toPx()
                                    for (i in 1..3) {
                                        val x = size.width * (i / 4f)
                                        val y = size.height * (i / 4f)
                                        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = strokeW)
                                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = strokeW)
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AdjustVideoBadgeGray)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = formatTimeDecimal(uiState.durationMs),
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

                    // Middle Action Row: Fit/Fill, Flip H, Flip V, Zoom Slider, Grid, Color Wheel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AdjustToolbarPillButton(
                            onClick = viewModel::toggleFitMode,
                            testTag = "adjust_photo_fit_fill_button"
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AspectRatio,
                                contentDescription = "ملاءمة أو ملء",
                                tint = AdjustVideoMagenta,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        AdjustToolbarPillButton(
                            onClick = viewModel::toggleFlipHorizontal,
                            testTag = "adjust_photo_flip_h_button"
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Flip,
                                contentDescription = "انعكاس أفقي",
                                tint = if (uiState.isFlippedHorizontal) Color(0xFF2E7D32) else AdjustVideoMagenta,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        AdjustToolbarPillButton(
                            onClick = viewModel::toggleFlipVertical,
                            testTag = "adjust_photo_flip_v_button"
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Flip,
                                contentDescription = "انعكاس عمودي",
                                tint = if (uiState.isFlippedVertical) Color(0xFF2E7D32) else AdjustVideoMagenta,
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer { rotationZ = 90f }
                            )
                        }

                        AdjustToolbarPillButton(
                            onClick = viewModel::toggleZoomSlider,
                            testTag = "adjust_photo_zoom_button"
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ZoomIn,
                                contentDescription = "شريط التكبير",
                                tint = if (uiState.isZoomSliderVisible) VideoStarWhite else AdjustVideoMagenta,
                                modifier = Modifier
                                    .size(22.dp)
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
                            testTag = "adjust_photo_grid_button"
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.GridOn,
                                contentDescription = "تبديل الشبكة",
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
                                .testTag("adjust_photo_color_wheel"),
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
private fun AdjustPhotoTopBar(
    onBackClick: () -> Unit,
    onMakePhotoClick: () -> Unit
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
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onMakePhotoClick)
                        .padding(horizontal = 8.dp)
                        .testTag("adjust_make_photo_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "أنشئ صورة",
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = AdjustVideoMagenta
                    )
                }

                Text(
                    text = "عدل الصورة",
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = AdjustVideoTitleDark,
                    modifier = Modifier.align(Alignment.Center)
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onBackClick)
                        .padding(horizontal = 6.dp)
                        .testTag("adjust_photo_back_button"),
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
private fun AdjustPhotoDurationBar(
    durationMs: Long,
    onDurationSecondsChanged: (Float) -> Unit
) {
    val currentSec = (durationMs / 1000f).coerceIn(0.5f, 10.0f)
    val presets = listOf(1.0f, 2.0f, 3.0f, 5.0f)

    Surface(color = AdjustVideoOverlayScrim) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "المدة ${formatTimeDecimal(durationMs)}",
                    style = TextStyle(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        fontFeatureSettings = "tnum",
                        color = VideoStarWhite
                    ),
                    modifier = Modifier.testTag("adjust_photo_duration_text")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEach { preset ->
                        val selected = abs(currentSec - preset) < 0.08f
                        Surface(
                            color = if (selected) AdjustVideoMagenta else VideoStarWhite.copy(alpha = 0.92f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onDurationSecondsChanged(preset) }
                                .testTag("photo_duration_preset_${preset.toInt()}s")
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.1fs", preset),
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = if (selected) VideoStarWhite else AdjustVideoMagenta,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            Slider(
                value = currentSec,
                onValueChange = onDurationSecondsChanged,
                valueRange = 0.5f..10.0f,
                colors = SliderDefaults.colors(
                    thumbColor = VideoStarWhite,
                    activeTrackColor = AdjustVideoMagenta,
                    inactiveTrackColor = VideoStarWhite.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("adjust_photo_duration_slider")
            )
        }
    }
}
