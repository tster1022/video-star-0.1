package com.example.ui.screens.keyframe

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.Anchor
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.ui.components.PrecisionRulerDialSlider
import com.example.ui.components.rememberStudioTickHaptics
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.LayerKeyframe
import com.example.ui.components.MediaClipStageContent
import com.example.ui.components.geometricTriangleBackground
import com.example.ui.theme.ClassicGreen
import com.example.ui.theme.ClassicKeyframePurple
import com.example.ui.theme.ClassicRed
import com.example.ui.theme.VideoStarInkDark
import com.example.ui.theme.VideoStarInkMuted
import com.example.ui.theme.VideoStarIosDivider
import com.example.ui.theme.VideoStarIosTitle
import com.example.ui.theme.VideoStarLightToolbar
import com.example.ui.theme.VideoStarWhite
import kotlin.math.roundToInt

@Composable
fun KeyframeEditorScreen(
    projectId: String,
    layerId: String,
    onBack: () -> Unit,
    onOpenGraphs: (String, String, Int) -> Unit = { _, _, _ -> }
) {
    val viewModel: KeyframeViewModel = viewModel(
        key = "keyframe_${projectId}_$layerId",
        factory = remember(projectId, layerId) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return KeyframeViewModel(projectId, layerId) as T
                }
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    androidx.compose.runtime.DisposableEffect(lifecycleOwner, projectId, layerId) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshFromRepository()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
            KeyframeTopBar(
                layerNumber = uiState.layerNumber,
                onBackClick = { viewModel.saveAndReturn(onBack) },
                onQrClick = { viewModel.openDialog(KeyframeDialogState.QrImport) },
                onInfoClick = { viewModel.openDialog(KeyframeDialogState.Info) },
                onOptionsClick = { viewModel.openDialog(KeyframeDialogState.Options) }
            )
        }
    ) { innerPadding ->
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .geometricTriangleBackground()
                    .padding(innerPadding)
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                val isLandscape = maxWidth > maxHeight && maxWidth >= 560.dp

                if (isLandscape) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .width(176.dp)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DPadAndAnchorPanel(
                                isGridVisible = uiState.isGridVisible,
                                isTransparencyCheckerVisible = uiState.isTransparencyCheckerVisible,
                                isPathVisible = uiState.isPathVisible,
                                onToggleGrid = viewModel::toggleGrid,
                                onToggleTransparency = viewModel::toggleTransparencyChecker,
                                onResetAnchor = viewModel::resetAnchorToCenter,
                                onNudgeUp = { viewModel.nudgePosition(0f, -12f) },
                                onNudgeDown = { viewModel.nudgePosition(0f, 12f) },
                                onNudgeLeft = { viewModel.nudgePosition(-12f, 0f) },
                                onNudgeRight = { viewModel.nudgePosition(12f, 0f) },
                                onTogglePath = {
                                    viewModel.saveAndReturn {
                                        onOpenGraphs(projectId, uiState.layerId, viewModel.activeKeyframeIndex())
                                    }
                                },
                                onAddKeyframe = viewModel::addOrUpdateKeyframeAtCurrentTime
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            KeyframeStagePreview(
                                uiState = uiState,
                                onStageDrag = viewModel::onStageDrag,
                                onAnchorDrag = viewModel::updateAnchor,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            KeyframeTimelineBar(
                                currentProgress = uiState.currentProgress,
                                keyframes = uiState.keyframes,
                                isPlaying = uiState.isPlaying,
                                isMuted = uiState.isMuted,
                                onStepBack = viewModel::stepBackward,
                                onStepForward = viewModel::stepForward,
                                onTogglePlay = viewModel::togglePlayback,
                                onScrub = viewModel::scrubTo,
                                onToggleMute = viewModel::toggleMute
                            )
                        }

                        Column(
                            modifier = Modifier
                                .width(235.dp)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Center
                        ) {
                            KeyframeBottomPropertiesSection(
                                uiState = uiState,
                                onOpenLayoutEffects = { viewModel.openDialog(KeyframeDialogState.LayoutEffects) },
                                onEffectIntensityChange = viewModel::setEffectIntensity,
                                onScaleChange = viewModel::updateScale,
                                onResetScale = viewModel::resetScaleTo100,
                                onCopyKeyframe = viewModel::copyCurrentValuesAsKeyframe,
                                onPasteKeyframe = viewModel::pasteCopiedKeyframeAtCurrentTime,
                                onDeleteKeyframe = viewModel::deleteNearestKeyframe,
                                onToggleRotateFlip = viewModel::toggleRotateFlipMode,
                                onToggle3D = viewModel::toggle3DEnabled,
                                onRotationXChange = viewModel::updateRotationX,
                                onRotationYChange = viewModel::updateRotationY,
                                onPerspectiveFovChange = viewModel::updatePerspectiveFov,
                                onReset3D = viewModel::reset3DTransform,
                                onJogRotate = viewModel::jogRotate,
                                onResetRotation = viewModel::resetRotationToZero
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(255.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DPadAndAnchorPanel(
                                isGridVisible = uiState.isGridVisible,
                                isTransparencyCheckerVisible = uiState.isTransparencyCheckerVisible,
                                isPathVisible = uiState.isPathVisible,
                                onToggleGrid = viewModel::toggleGrid,
                                onToggleTransparency = viewModel::toggleTransparencyChecker,
                                onResetAnchor = viewModel::resetAnchorToCenter,
                                onNudgeUp = { viewModel.nudgePosition(0f, -12f) },
                                onNudgeDown = { viewModel.nudgePosition(0f, 12f) },
                                onNudgeLeft = { viewModel.nudgePosition(-12f, 0f) },
                                onNudgeRight = { viewModel.nudgePosition(12f, 0f) },
                                onTogglePath = {
                                    viewModel.saveAndReturn {
                                        onOpenGraphs(projectId, uiState.layerId, viewModel.activeKeyframeIndex())
                                    }
                                },
                                onAddKeyframe = viewModel::addOrUpdateKeyframeAtCurrentTime
                            )

                            KeyframeStagePreview(
                                uiState = uiState,
                                onStageDrag = viewModel::onStageDrag,
                                onAnchorDrag = viewModel::updateAnchor,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }

                        KeyframeTimelineBar(
                            currentProgress = uiState.currentProgress,
                            keyframes = uiState.keyframes,
                            isPlaying = uiState.isPlaying,
                            isMuted = uiState.isMuted,
                            onStepBack = viewModel::stepBackward,
                            onStepForward = viewModel::stepForward,
                            onTogglePlay = viewModel::togglePlayback,
                            onScrub = viewModel::scrubTo,
                            onToggleMute = viewModel::toggleMute
                        )

                        KeyframeBottomPropertiesSection(
                            uiState = uiState,
                            onOpenLayoutEffects = { viewModel.openDialog(KeyframeDialogState.LayoutEffects) },
                            onEffectIntensityChange = viewModel::setEffectIntensity,
                            onScaleChange = viewModel::updateScale,
                            onResetScale = viewModel::resetScaleTo100,
                            onCopyKeyframe = viewModel::copyCurrentValuesAsKeyframe,
                            onPasteKeyframe = viewModel::pasteCopiedKeyframeAtCurrentTime,
                            onDeleteKeyframe = viewModel::deleteNearestKeyframe,
                            onToggleRotateFlip = viewModel::toggleRotateFlipMode,
                            onToggle3D = viewModel::toggle3DEnabled,
                            onRotationXChange = viewModel::updateRotationX,
                            onRotationYChange = viewModel::updateRotationY,
                            onPerspectiveFovChange = viewModel::updatePerspectiveFov,
                            onReset3D = viewModel::reset3DTransform,
                            onJogRotate = viewModel::jogRotate,
                            onResetRotation = viewModel::resetRotationToZero
                        )
                    }
                }
            }
        }
    }

    when (uiState.activeDialog) {
        KeyframeDialogState.QrImport -> {
            val presets = listOf(
                "قفزة زوم سريعة (Zoom Bounce)",
                "دوران سلسل بين 3 نقاط",
                "انزلاق أفقي سينمائي"
            )
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                containerColor = VideoStarWhite,
                title = { Text("استيراد حركة QR", color = VideoStarInkDark) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        presets.forEach { preset ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF6F6F9),
                                border = BorderStroke(1.dp, ClassicKeyframePurple.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.applyQrKeyframePreset(preset) }
                            ) {
                                Text(
                                    text = preset,
                                    color = VideoStarInkDark,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissDialog) {
                        Text("إغلاق", color = ClassicRed)
                    }
                }
            )
        }
        KeyframeDialogState.Info -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                containerColor = VideoStarWhite,
                title = { Text("دليل محرر Keyframe", color = VideoStarInkDark) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("• العلامات البنفسجية على المسار تمثل نقاط Keyframes.", color = VideoStarInkMuted)
                        Text("• حرك النقطة البيضاء داخل المعاينة لتغيير نقطة الارتكاز (Anchor Point).", color = VideoStarInkMuted)
                        Text("• اسحب قرص Jog Dial أفقياً أسفل الشاشة لتدوير الطبقة بدقة.", color = VideoStarInkMuted)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = viewModel::dismissDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = ClassicGreen)
                    ) {
                        Text("فهمت")
                    }
                }
            )
        }
        KeyframeDialogState.Options -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                containerColor = VideoStarWhite,
                title = { Text("خيارات Keyframe", color = VideoStarInkDark) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = viewModel::clearAllKeyframesExceptCurrent,
                            colors = ButtonDefaults.buttonColors(containerColor = ClassicRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("تصفير جميع الـ Keyframes")
                        }
                        Button(
                            onClick = {
                                viewModel.resetAnchorToCenter()
                                viewModel.dismissDialog()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ClassicGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("إعادة ضبط نقطة الارتكاز")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissDialog) {
                        Text("إغلاق", color = VideoStarInkDark)
                    }
                }
            )
        }
        KeyframeDialogState.LayoutEffects -> {
            val layoutEffects = listOf(
                "Layout Effect Not Selected",
                "Motion Blur Trail",
                "Perspective Skew",
                "Elastic Shake",
                "Mirror Tiles"
            )
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                containerColor = VideoStarWhite,
                title = { Text("تأثيرات التخطيط (Layout Effect)", color = VideoStarInkDark) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        layoutEffects.forEach { name ->
                            val selected = uiState.layoutEffectName == name
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) ClassicGreen.copy(alpha = 0.14f) else Color(0xFFF6F6F9),
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) ClassicGreen else VideoStarIosDivider
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectLayoutEffect(name) }
                            ) {
                                Text(
                                    text = name,
                                    color = if (selected) ClassicGreen else VideoStarInkDark,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissDialog) {
                        Text("إغلاق", color = ClassicRed)
                    }
                }
            )
        }
        null -> Unit
    }
}

@Composable
private fun KeyframeTopBar(
    layerNumber: Int,
    onBackClick: () -> Unit,
    onQrClick: () -> Unit,
    onInfoClick: () -> Unit,
    onOptionsClick: () -> Unit
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
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onBackClick)
                            .padding(horizontal = 6.dp)
                            .testTag("keyframe_back_button"),
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
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    IconButton(
                        onClick = onQrClick,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("keyframe_qr_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.QrCode2,
                            contentDescription = "QR Code",
                            tint = ClassicRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = "Keyframe Layer $layerNumber",
                        color = VideoStarIosTitle,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = onInfoClick,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("keyframe_info_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Info",
                            tint = ClassicGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onOptionsClick)
                            .padding(horizontal = 8.dp)
                            .testTag("keyframe_options_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Options",
                            color = ClassicGreen,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                HorizontalDivider(thickness = 0.8.dp, color = VideoStarIosDivider)
            }
        }
    }
}

@Composable
private fun DPadAndAnchorPanel(
    isGridVisible: Boolean,
    isTransparencyCheckerVisible: Boolean,
    isPathVisible: Boolean,
    onToggleGrid: () -> Unit,
    onToggleTransparency: () -> Unit,
    onResetAnchor: () -> Unit,
    onNudgeUp: () -> Unit,
    onNudgeDown: () -> Unit,
    onNudgeLeft: () -> Unit,
    onNudgeRight: () -> Unit,
    onTogglePath: () -> Unit,
    onAddKeyframe: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 3x3 Control Grid
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Row 1: Grid Toggle | Up Arrow | Empty
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyframeSquareBtn(
                    isActive = isGridVisible,
                    testTag = "keyframe_grid_toggle",
                    onClick = onToggleGrid
                ) {
                    Icon(
                        imageVector = Icons.Outlined.GridOn,
                        contentDescription = "Toggle Grid",
                        tint = if (isGridVisible) ClassicRed else ClassicGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                KeyframeSquareBtn(
                    testTag = "keyframe_dpad_up",
                    onClick = onNudgeUp
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = "Move Up",
                        tint = ClassicGreen,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.size(44.dp))
            }

            // Row 2: Left Arrow | Anchor Point | Right Arrow
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyframeSquareBtn(
                    testTag = "keyframe_dpad_left",
                    onClick = onNudgeLeft
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = "Move Left",
                        tint = ClassicGreen,
                        modifier = Modifier.size(26.dp)
                    )
                }
                KeyframeSquareBtn(
                    testTag = "keyframe_anchor_reset",
                    onClick = onResetAnchor
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Anchor,
                        contentDescription = "Anchor Point",
                        tint = ClassicGreen,
                        modifier = Modifier.size(21.dp)
                    )
                }
                KeyframeSquareBtn(
                    testTag = "keyframe_dpad_right",
                    onClick = onNudgeRight
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Move Right",
                        tint = ClassicGreen,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Row 3: Transparency Checkerboard | Down Arrow | Empty
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyframeSquareBtn(
                    isActive = isTransparencyCheckerVisible,
                    testTag = "keyframe_transparency_toggle",
                    onClick = onToggleTransparency
                ) {
                    CheckerboardIcon(isActive = isTransparencyCheckerVisible)
                }
                KeyframeSquareBtn(
                    testTag = "keyframe_dpad_down",
                    onClick = onNudgeDown
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Move Down",
                        tint = ClassicGreen,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.size(44.dp))
            }
        }

        // Path Row: Curved Path icon + Green Circular Add Keyframe button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isPathVisible) ClassicGreen.copy(alpha = 0.14f) else VideoStarWhite,
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, if (isPathVisible) ClassicGreen else VideoStarIosDivider),
                modifier = Modifier
                    .height(36.dp)
                    .clickable(onClick = onTogglePath)
                    .testTag("keyframe_path_toggle")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Timeline,
                        contentDescription = "Path",
                        tint = ClassicGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Path",
                        color = ClassicGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = ClassicGreen,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .size(36.dp)
                    .clickable(onClick = onAddKeyframe)
                    .testTag("keyframe_add_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Add Keyframe",
                        tint = VideoStarWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyframeSquareBtn(
    isActive: Boolean = false,
    testTag: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isActive) ClassicRed.copy(alpha = 0.12f) else VideoStarWhite,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, if (isActive) ClassicRed else VideoStarIosDivider),
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun CheckerboardIcon(isActive: Boolean) {
    Canvas(modifier = Modifier.size(20.dp).clip(RoundedCornerShape(3.dp))) {
        val cells = 4
        val step = size.width / cells
        for (r in 0 until cells) {
            for (c in 0 until cells) {
                val dark = (r + c) % 2 == 0
                drawRect(
                    color = if (dark) (if (isActive) ClassicRed else Color(0xFF8E8E93)) else Color.White,
                    topLeft = Offset(c * step, r * step),
                    size = Size(step, step)
                )
            }
        }
    }
}

@Composable
private fun KeyframeStagePreview(
    uiState: KeyframeUiState,
    onStageDrag: (Float, Float) -> Unit,
    onAnchorDrag: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var stageSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
                .clip(RoundedCornerShape(8.dp))
                .background(if (uiState.isTransparencyCheckerVisible) Color(0xFFCCCCCC) else Color(0xFF141418))
                .border(1.dp, VideoStarIosDivider, RoundedCornerShape(8.dp))
                .onSizeChanged { stageSize = it }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onStageDrag(dragAmount.x, dragAmount.y)
                    }
                }
                .testTag("keyframe_stage_preview")
        ) {
            // Optional Transparency Checkerboard Canvas
            if (uiState.isTransparencyCheckerVisible) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cell = 14.dp.toPx()
                    val cols = (size.width / cell).toInt() + 1
                    val rows = (size.height / cell).toInt() + 1
                    for (r in 0..rows) {
                        for (c in 0..cols) {
                            drawRect(
                                color = if ((r + c) % 2 == 0) Color(0xFFE0E0E5) else Color(0xFFB5B5BC),
                                topLeft = Offset(c * cell, r * cell),
                                size = Size(cell, cell)
                            )
                        }
                    }
                }
            }

            // Transformed Layer Visual anchored around displayAnchorX/Y
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(uiState.displayAnchorX, uiState.displayAnchorY)
                        translationX = uiState.displayOffsetX
                        translationY = uiState.displayOffsetY
                        scaleX = uiState.displayScale
                        scaleY = uiState.displayScale * (if (uiState.isFlippedVertical) -1f else 1f)
                        rotationZ = uiState.displayRotation
                        rotationX = uiState.displayRotationX
                        rotationY = uiState.displayRotationY
                        cameraDistance = (uiState.displayPerspectiveFov / 3.5f).coerceIn(6f, 24f) * this.density
                    }
            ) {
                MediaClipStageContent(
                    clip = uiState.mediaClip,
                    isPlaying = uiState.isPlaying,
                    positionMs = (uiState.currentProgress * (uiState.mediaClip?.effectiveDurationMs ?: 3_000L)).toLong(),
                    isMuted = uiState.isMuted,
                    isFitMode = uiState.mediaClip?.isFitMode ?: false,
                    contentDescription = "Keyframe Layer ${uiState.layerNumber}",
                    emptyLabel = "لا توجد وسائط للطبقة ${uiState.layerNumber}",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(4.dp))
                )

                if (uiState.displayEffectIntensity > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ClassicRed.copy(alpha = (uiState.displayEffectIntensity * 0.25f)))
                    )
                }
            }

            // 4x4 Red/Pink Grid Overlay
            if (uiState.isGridVisible) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val lineColor = ClassicRed.copy(alpha = 0.52f)
                    for (i in 1..3) {
                        val x = w * i / 4f
                        val y = h * i / 4f
                        drawLine(lineColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1.dp.toPx())
                        drawLine(lineColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
                    }
                }
            }

            // Optional Keyframe Motion Path Curve
            if (uiState.isPathVisible && uiState.keyframes.size > 1) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val points = uiState.keyframes.map { kf ->
                        Offset(
                            (cx + kf.offsetX * 0.55f).coerceIn(12f, size.width - 12f),
                            (cy + kf.offsetY * 0.55f).coerceIn(12f, size.height - 12f)
                        )
                    }
                    for (i in 0 until points.lastIndex) {
                        drawLine(
                            color = ClassicKeyframePurple.copy(alpha = 0.85f),
                            start = points[i],
                            end = points[i + 1],
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                        )
                    }
                    points.forEach { pt ->
                        drawCircle(color = ClassicKeyframePurple, radius = 4.dp.toPx(), center = pt)
                    }
                }
            }

            // Draggable White Anchor Point Dot
            if (stageSize.width > 0 && stageSize.height > 0) {
                val dotSizePx = with(density) { 18.dp.toPx() }
                val anchorPxX = (uiState.displayAnchorX * stageSize.width - dotSizePx / 2f).roundToInt()
                val anchorPxY = (uiState.displayAnchorY * stageSize.height - dotSizePx / 2f).roundToInt()
                Box(
                    modifier = Modifier
                        .offset { IntOffset(anchorPxX, anchorPxY) }
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(VideoStarWhite)
                        .border(2.dp, ClassicGreen, CircleShape)
                        .pointerInput(stageSize) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                val nextX = uiState.displayAnchorX + drag.x / stageSize.width.coerceAtLeast(1)
                                val nextY = uiState.displayAnchorY + drag.y / stageSize.height.coerceAtLeast(1)
                                onAnchorDrag(nextX, nextY)
                            }
                        }
                        .testTag("keyframe_anchor_dot")
                )
            }
        }
    }
}

@Composable
private fun KeyframeTimelineBar(
    currentProgress: Float,
    keyframes: List<LayerKeyframe>,
    isPlaying: Boolean,
    isMuted: Boolean,
    onStepBack: () -> Unit,
    onStepForward: () -> Unit,
    onTogglePlay: () -> Unit,
    onScrub: (Float) -> Unit,
    onToggleMute: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFD8D8DD),
        border = BorderStroke(0.8.dp, VideoStarIosDivider),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onStepBack,
                modifier = Modifier
                    .size(34.dp)
                    .testTag("keyframe_step_back")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "Step Backward",
                    tint = ClassicGreen,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("keyframe_play_button")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = ClassicGreen,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = onStepForward,
                modifier = Modifier
                    .size(34.dp)
                    .testTag("keyframe_step_forward")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Step Forward",
                    tint = ClassicGreen,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Timeline Track with Purple Keyframe Markers + White Thumb
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val trackY = size.height / 2f
                    val trackH = 5.dp.toPx()
                    drawLine(
                        color = Color(0xFFB4B4BC),
                        start = Offset(0f, trackY),
                        end = Offset(size.width, trackY),
                        strokeWidth = trackH
                    )
                    drawLine(
                        color = ClassicGreen,
                        start = Offset(0f, trackY),
                        end = Offset(size.width * currentProgress, trackY),
                        strokeWidth = trackH
                    )

                    // Purple vertical markers for added keyframes
                    keyframes.forEach { kf ->
                        val x = (size.width * kf.timeProgress).coerceIn(2f, size.width - 2f)
                        drawLine(
                            color = ClassicKeyframePurple,
                            start = Offset(x, trackY - 10.dp.toPx()),
                            end = Offset(x, trackY + 10.dp.toPx()),
                            strokeWidth = 3.dp.toPx()
                        )
                    }
                }

                Slider(
                    value = currentProgress,
                    onValueChange = onScrub,
                    colors = SliderDefaults.colors(
                        thumbColor = VideoStarWhite,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("keyframe_timeline_slider")
                )
            }

            IconButton(
                onClick = onToggleMute,
                modifier = Modifier
                    .size(34.dp)
                    .testTag("keyframe_mute_button")
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = "Mute",
                    tint = VideoStarInkDark,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
private fun KeyframeBottomPropertiesSection(
    uiState: KeyframeUiState,
    onOpenLayoutEffects: () -> Unit,
    onEffectIntensityChange: (Float) -> Unit,
    onScaleChange: (Float) -> Unit,
    onResetScale: () -> Unit,
    onCopyKeyframe: () -> Unit,
    onPasteKeyframe: () -> Unit,
    onDeleteKeyframe: () -> Unit,
    onToggleRotateFlip: () -> Unit,
    onToggle3D: () -> Unit,
    onRotationXChange: (Float) -> Unit,
    onRotationYChange: (Float) -> Unit,
    onPerspectiveFovChange: (Float) -> Unit,
    onReset3D: () -> Unit,
    onJogRotate: (Float) -> Unit,
    onResetRotation: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = VideoStarWhite.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, VideoStarIosDivider),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Layout Effect Row + Red Slider
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = uiState.layoutEffectName,
                        color = VideoStarInkDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onOpenLayoutEffects)
                            .testTag("keyframe_layout_effect_label")
                    )
                    Surface(
                        shape = CircleShape,
                        color = ClassicGreen,
                        modifier = Modifier
                            .size(30.dp)
                            .clickable(onClick = onOpenLayoutEffects)
                            .testTag("keyframe_layout_effect_grid_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.GridView,
                                contentDescription = "Select Layout Effect",
                                tint = VideoStarWhite,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
                PrecisionRulerDialSlider(
                    label = "Effect (%)",
                    valueText = "${(uiState.displayEffectIntensity * 100f).roundToInt()}%",
                    value = uiState.displayEffectIntensity,
                    onValueChange = onEffectIntensityChange,
                    valueRange = 0f..1f,
                    height = 40.dp,
                    sliderTestTag = "keyframe_effect_slider"
                )
            }

            HorizontalDivider(thickness = 0.6.dp, color = VideoStarIosDivider)

            // 2. Size XXX% + Copy/Paste/Delete Keyframes + Green X Reset + Red Slider
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Size ${(uiState.displayScale * 100).roundToInt()}%",
                        color = VideoStarInkDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("keyframe_size_label")
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Copy / Paste / Delete Keyframe tools
                    IconButton(
                        onClick = onCopyKeyframe,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("keyframe_copy_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy Keyframe",
                            tint = ClassicGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onPasteKeyframe,
                        enabled = uiState.copiedKeyframe != null,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("keyframe_paste_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentPaste,
                            contentDescription = "Paste Keyframe",
                            tint = if (uiState.copiedKeyframe != null) ClassicGreen else Color(0xFFB5B5BC),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDeleteKeyframe,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("keyframe_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete Keyframe",
                            tint = ClassicRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Green X button to reset Size to 100%
                    Surface(
                        shape = CircleShape,
                        color = ClassicGreen,
                        modifier = Modifier
                            .size(28.dp)
                            .clickable(onClick = onResetScale)
                            .testTag("keyframe_reset_size_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Reset Size to 100%",
                                tint = VideoStarWhite,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                PrecisionRulerDialSlider(
                    label = "Size (%)",
                    valueText = "${(uiState.displayScale * 100f).roundToInt()}%",
                    value = uiState.displayScale,
                    onValueChange = onScaleChange,
                    valueRange = 0.25f..2.5f,
                    defaultValue = 1.0f,
                    height = 40.dp,
                    sliderTestTag = "keyframe_size_slider"
                )
            }

            HorizontalDivider(thickness = 0.6.dp, color = VideoStarIosDivider)

            // 3. Rotate / Flip + 3D Toggle + Numeric Angle Readout + Horizontal Jog Dial
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (uiState.isFlipMode) ClassicRed.copy(alpha = 0.12f) else ClassicGreen.copy(alpha = 0.10f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onToggleRotateFlip)
                                .testTag("keyframe_rotate_flip_toggle")
                        ) {
                            Text(
                                text = if (uiState.isFlipMode) "Rotate / Flip (Flipped)" else "Rotate / Flip",
                                color = if (uiState.isFlipMode) ClassicRed else ClassicGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (uiState.is3DEnabled) ClassicRed else Color(0xFFEEEEF3),
                            border = BorderStroke(0.5.dp, if (uiState.is3DEnabled) ClassicRed else VideoStarIosDivider),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onToggle3D)
                                .testTag("keyframe_3d_toggle")
                        ) {
                            Text(
                                text = "3D",
                                color = if (uiState.is3DEnabled) VideoStarWhite else VideoStarInkDark,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = "${uiState.displayRotation.roundToInt()}°",
                        color = VideoStarInkDark,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onResetRotation)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("keyframe_rotation_readout")
                    )
                }

                HorizontalJogDial(
                    rotationDegrees = uiState.displayRotation,
                    onRotateDelta = onJogRotate
                )

                if (uiState.is3DEnabled) {
                    HorizontalDivider(thickness = 0.6.dp, color = VideoStarIosDivider)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "3D Camera Perspective",
                            color = ClassicRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Reset 3D",
                            color = ClassicGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(onClick = onReset3D)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    PrecisionRulerDialSlider(
                        label = "Rot X",
                        valueText = "${uiState.displayRotationX.roundToInt()}°",
                        value = uiState.displayRotationX,
                        onValueChange = onRotationXChange,
                        valueRange = -75f..75f,
                        defaultValue = 0f,
                        height = 38.dp,
                        sliderTestTag = "keyframe_rot_x_slider"
                    )
                    PrecisionRulerDialSlider(
                        label = "Rot Y",
                        valueText = "${uiState.displayRotationY.roundToInt()}°",
                        value = uiState.displayRotationY,
                        onValueChange = onRotationYChange,
                        valueRange = -75f..75f,
                        defaultValue = 0f,
                        height = 38.dp,
                        sliderTestTag = "keyframe_rot_y_slider"
                    )
                    PrecisionRulerDialSlider(
                        label = "FOV",
                        valueText = "${uiState.displayPerspectiveFov.roundToInt()}°",
                        value = uiState.displayPerspectiveFov,
                        onValueChange = onPerspectiveFovChange,
                        valueRange = 25f..90f,
                        defaultValue = 45f,
                        height = 38.dp,
                        sliderTestTag = "keyframe_fov_slider"
                    )
                }
            }
        }
    }
}

@Composable
private fun HorizontalJogDial(
    rotationDegrees: Float,
    onRotateDelta: (Float) -> Unit
) {
    val haptics = rememberStudioTickHaptics()
    var accumulatedDrag by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFEEEEF2),
        border = BorderStroke(1.dp, VideoStarIosDivider),
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { accumulatedDrag = 0f }
                ) { change, dragAmount ->
                    change.consume()
                    val deltaDeg = dragAmount * 0.45f
                    accumulatedDrag += deltaDeg
                    if (kotlin.math.abs(accumulatedDrag) >= 2f) {
                        accumulatedDrag = 0f
                        haptics.tick()
                    }
                    onRotateDelta(deltaDeg)
                }
            }
            .testTag("keyframe_jog_dial")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val tickSpacing = 8.dp.toPx()
            val phaseOffset = (rotationDegrees * 1.4f) % tickSpacing
            val count = (size.width / tickSpacing).toInt() + 2
            val centerX = size.width / 2f

            for (i in -1..count) {
                val x = i * tickSpacing + phaseOffset
                if (x in 0f..size.width) {
                    val isMajor = i % 5 == 0
                    val topY = if (isMajor) 5.dp.toPx() else 10.dp.toPx()
                    val botY = size.height - if (isMajor) 5.dp.toPx() else 10.dp.toPx()
                    drawLine(
                        color = if (isMajor) Color(0xFF5C5C64) else Color(0xFF9E9EA6),
                        start = Offset(x, topY),
                        end = Offset(x, botY),
                        strokeWidth = if (isMajor) 1.5.dp.toPx() else 1.dp.toPx()
                    )
                }
            }

            // Center precision indicator needle
            drawLine(
                color = ClassicRed,
                start = Offset(centerX, 2.dp.toPx()),
                end = Offset(centerX, size.height - 2.dp.toPx()),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}
