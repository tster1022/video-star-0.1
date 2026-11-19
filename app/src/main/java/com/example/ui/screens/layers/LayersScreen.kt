package com.example.ui.screens.layers

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.os.Build
import androidx.compose.ui.platform.LocalInspectionMode
import com.example.R
import com.example.data.MediaImportHelper
import com.example.gl.GlLayerRenderStage
import com.example.model.ClassicLayerTab
import com.example.model.EditorLayer
import com.example.model.LayerEffectType
import com.example.model.LayerMediaType
import com.example.model.MaskMode
import com.example.model.MaskShape
import com.example.model.MediaClip
import com.example.model.TextLayerSpec
import com.example.ui.components.IosThinSlider
import com.example.ui.components.MediaClipStageContent
import com.example.ui.components.RichLayerTextOverlay
import com.example.ui.components.applyLayerMask
import com.example.ui.components.geometricTriangleBackground
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.ClassicGreen
import com.example.ui.theme.ClassicPlayerOverlay
import com.example.ui.theme.ClassicRed
import com.example.ui.theme.VideoStarInkDark
import com.example.ui.theme.VideoStarInkMuted
import com.example.ui.theme.VideoStarIosDivider
import com.example.ui.theme.VideoStarIosTitle
import com.example.ui.theme.VideoStarLightToolbar
import com.example.ui.theme.VideoStarWhite
import com.example.viewmodel.SharedProjectViewModel

@Composable
fun LayersScreen(
    projectId: String,
    sharedProjectViewModel: SharedProjectViewModel,
    onCancel: () -> Unit,
    onCreated: () -> Unit,
    onOpenKeyframeEditor: (String, String) -> Unit,
    onOpenAdjustVideoForLayer: (projectId: String, mediaUri: String, durationMs: Long, sourceFps: Int, targetLayerIndex: Int) -> Unit,
    onOpenAdjustPhotoForLayer: (projectId: String, mediaUri: String, targetLayerIndex: Int) -> Unit,
    onSwitchToDarkTheme: () -> Unit,
    onOpenLayerEffectsStudio: (String, String) -> Unit = { _, _ -> }
) {
    val viewModel: LayersViewModel = viewModel(
        key = "classic_layers_$projectId",
        factory = remember(projectId) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LayersViewModel(projectId) as T
                }
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isImportingMedia by sharedProjectViewModel.isImportingMedia.collectAsStateWithLifecycle()
    val importError by sharedProjectViewModel.importError.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var pendingLayerIndex by remember { mutableStateOf<Int?>(null) }

    val unifiedMediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { pickedUri ->
        val layerIdx = pendingLayerIndex ?: uiState.selectedLayerIndex ?: 0
        pendingLayerIndex = null
        if (pickedUri != null) {
            val isVid = MediaImportHelper.isVideoUri(context, pickedUri)
            viewModel.syncToRepository()
            sharedProjectViewModel.importMediaToProject(
                context = context,
                projectId = projectId,
                sourceUri = pickedUri,
                isVideo = isVid
            ) { imported ->
                if (isVid) {
                    onOpenAdjustVideoForLayer(
                        projectId,
                        imported.localUriString,
                        imported.durationMs,
                        imported.sourceFps,
                        layerIdx
                    )
                } else {
                    onOpenAdjustPhotoForLayer(
                        projectId,
                        imported.localUriString,
                        layerIdx
                    )
                }
            }
        }
    }

    val launchMediaPickerForSlot: (Int, ActivityResultContracts.PickVisualMedia.VisualMediaType) -> Unit =
        { targetIdx, mediaType ->
            pendingLayerIndex = targetIdx
            unifiedMediaPickerLauncher.launch(PickVisualMediaRequest(mediaType))
        }

    DisposableEffect(lifecycleOwner, projectId) {
        val observer = LifecycleEventObserver { _, event ->
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

    LaunchedEffect(importError) {
        val err = importError
        if (err != null) {
            snackbarHostState.showSnackbar(err)
            sharedProjectViewModel.clearImportError()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            LayersTopBar(
                onCancelClick = onCancel,
                onQrClick = { viewModel.openDialog(LayersDialogState.QrImport) },
                onSwitchThemeClick = {
                    viewModel.syncToRepository()
                    onSwitchToDarkTheme()
                },
                onInfoClick = { viewModel.openDialog(LayersDialogState.Info) },
                onCameraClick = viewModel::captureSnapshot,
                onCreateClick = { viewModel.commitAndCreate(onCreated) }
            )
        },
        bottomBar = {
            ClassicLayersBottomBar(
                activeTab = uiState.activeBottomTab,
                hasSelectedLayer = uiState.selectedLayerIndex != null,
                onTabSelected = { tab ->
                    when (tab) {
                        ClassicLayerTab.LAYER_EFFECTS -> {
                            viewModel.syncToRepository()
                            val layerId = viewModel.ensureActiveLayerId()
                            onOpenLayerEffectsStudio(projectId, layerId)
                        }
                        ClassicLayerTab.LAYOUT -> {
                            viewModel.syncToRepository()
                            val layerId = viewModel.ensureActiveLayerId()
                            onOpenKeyframeEditor(projectId, layerId)
                        }
                        else -> viewModel.onBottomTabClick(tab)
                    }
                }
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .geometricTriangleBackground()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
        ) {
            val bodyHeight = maxHeight
            val minStageHeight = bodyHeight * 0.45f

            Column(modifier = Modifier.fillMaxSize()) {
                // Single compact 36x36dp horizontally scrollable layers strip directly below top bar
                CompactHorizontalLayersStrip(
                    layers = uiState.layers,
                    selectedIndex = uiState.selectedLayerIndex,
                    stageBackgroundColor = uiState.stageBackgroundColor,
                    isDarkTheme = false,
                    colorWheelTag = "layers_color_wheel_button",
                    layerButtonTagPrefix = "classic_layer_square_",
                    addLayerTag = "layers_add_layer_button",
                    manageLayersTag = "classic_layers_stack_button",
                    onColorWheelClick = { viewModel.openDialog(LayersDialogState.ColorWheel) },
                    onSelectLayer = { slotIdx ->
                        val isEmpty = viewModel.onLayerSlotClicked(slotIdx)
                        if (isEmpty) {
                            launchMediaPickerForSlot(
                                slotIdx,
                                ActivityResultContracts.PickVisualMedia.ImageAndVideo
                            )
                        }
                    },
                    onAddLayerClick = viewModel::addNewLayer,
                    onManageLayersClick = { viewModel.openDialog(LayersDialogState.Hierarchy) }
                )

                // Expanded Full-Width Preview Stage taking flex:1 remaining space (min 45% height)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .heightIn(min = minStageHeight)
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    ClassicStagePreviewArea(
                        layers = uiState.layers,
                        selectedLayerIndex = uiState.selectedLayerIndex,
                        projectAspectRatio = uiState.projectAspectRatio,
                        stageBackgroundColor = uiState.stageBackgroundColor,
                        stageProgress = uiState.stageProgress,
                        isPlaying = uiState.isPlaying,
                        isMuted = uiState.isMuted,
                        hasTouchedStage = uiState.hasTouchedStage,
                        interpolateLayer = viewModel::interpolatedLayerAt,
                        onStageTouched = viewModel::markStageTouched,
                        onTransformGesture = viewModel::onLayerTransformGesture,
                        onDoubleTapReset = viewModel::resetActiveLayerTransform,
                        onRequestImportLayerMedia = { targetIdx ->
                            launchMediaPickerForSlot(
                                targetIdx,
                                ActivityResultContracts.PickVisualMedia.ImageAndVideo
                            )
                        },
                        onTogglePlay = viewModel::togglePlayback,
                        onScrub = viewModel::onScrubStage,
                        onToggleMute = viewModel::toggleMute,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Floating translucent corner tools overlaid over top-end corner of preview
                    FloatingPreviewToolsOverlay(
                        isDarkTheme = false,
                        is3DEnabled = uiState.activeLayer?.is3DEnabled == true,
                        isSecondaryActive = false,
                        useGridForThirdButton = false,
                        hierarchyTag = "layers_hierarchy_button",
                        threeDTag = "layers_3d_button",
                        thirdButtonTag = "layers_tips_button",
                        onHierarchyClick = { viewModel.openDialog(LayersDialogState.Hierarchy) },
                        onToggle3DClick = viewModel::toggle3D,
                        onThirdButtonClick = { viewModel.openDialog(LayersDialogState.TipsAndPresets) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                    )
                }
            }

            // Draggable Bottom Sheet (~25% default height, draggable up to ~64%)
            AnimatedVisibility(
                visible = uiState.activeBottomTab != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val tab = uiState.activeBottomTab
                val activeLayer = uiState.activeLayer ?: uiState.layers.firstOrNull()
                if (tab != null && activeLayer != null) {
                    ClassicLayersToolPanel(
                        tab = tab,
                        activeLayer = activeLayer,
                        containerHeight = bodyHeight,
                        onSelectEffect = viewModel::setLayerEffect,
                        onOpenEffectsStudio = {
                            viewModel.syncToRepository()
                            onOpenLayerEffectsStudio(projectId, activeLayer.id)
                        },
                        onSelectTint = viewModel::setLayerTint,
                        onOpacityChange = viewModel::setLayerOpacity,
                        onTextSpecUpdated = viewModel::setLayerTextSpec,
                        onMaskUpdated = viewModel::setLayerMask,
                        onToggleMirror = viewModel::toggleHorizontalMirror,
                        onToggleFlipVertical = viewModel::toggleVerticalFlip,
                        onClose = viewModel::dismissBottomPanel
                    )
                }
            }
        }
    }

    if (isImportingMedia) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = VideoStarWhite,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = ClassicGreen,
                        strokeWidth = 2.5.dp
                    )
                    Text(
                        text = "جاري تجهيز الملف للطبقة...",
                        fontFamily = CairoFontFamily,
                        color = VideoStarInkDark,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }

    LayersScreenDialogs(
        uiState = uiState,
        viewModel = viewModel,
        launchMediaPickerForSlot = launchMediaPickerForSlot
    )
}

@Composable
private fun LayersTopBar(
    onCancelClick: () -> Unit,
    onQrClick: () -> Unit,
    onSwitchThemeClick: () -> Unit,
    onInfoClick: () -> Unit,
    onCameraClick: () -> Unit,
    onCreateClick: () -> Unit
) {
    Surface(
        color = VideoStarLightToolbar,
        shadowElevation = 1.dp
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Right (RTL start): إلغاء + QR
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .height(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onCancelClick)
                                .padding(horizontal = 8.dp)
                                .testTag("layers_cancel_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.ml_cancel),
                                fontFamily = CairoFontFamily,
                                color = ClassicRed,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        IconButton(
                            onClick = onQrClick,
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("layers_qr_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.QrCode2,
                                contentDescription = stringResource(R.string.cd_qr_import),
                                tint = ClassicGreen,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }

                    // Center: الطبقات + تبديل داكن
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "الطبقات",
                            fontFamily = CairoFontFamily,
                            color = VideoStarIosTitle,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF222226),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onSwitchThemeClick)
                                .testTag("layers_switch_dark_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DarkMode,
                                    contentDescription = "الوضع الداكن",
                                    tint = VideoStarWhite,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "داكن",
                                    fontFamily = CairoFontFamily,
                                    color = VideoStarWhite,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Left (RTL end): معلومات + كاميرا + إنشاء
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onInfoClick,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("layers_info_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = stringResource(R.string.cd_info),
                                tint = ClassicGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onCameraClick,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("layers_camera_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CameraAlt,
                                contentDescription = stringResource(R.string.cd_capture_snapshot),
                                tint = ClassicGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .height(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onCreateClick)
                                .padding(horizontal = 8.dp)
                                .testTag("layers_create_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.ml_create),
                                fontFamily = CairoFontFamily,
                                color = ClassicGreen,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                HorizontalDivider(thickness = 0.8.dp, color = VideoStarIosDivider)
            }
        }
    }
}

@Composable
private fun ClassicStagePreviewArea(
    layers: List<EditorLayer>,
    selectedLayerIndex: Int?,
    projectAspectRatio: Float,
    stageBackgroundColor: Color,
    stageProgress: Float,
    isPlaying: Boolean,
    isMuted: Boolean,
    hasTouchedStage: Boolean,
    interpolateLayer: (EditorLayer, Float) -> EditorLayer,
    onStageTouched: () -> Unit,
    onTransformGesture: (Float, Float, Float, Float) -> Unit,
    onDoubleTapReset: () -> Unit,
    onRequestImportLayerMedia: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    onScrub: (Float) -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF101014).copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        val safeRatio = projectAspectRatio.coerceIn(0.4f, 2.2f)
        val stageW = minOf(maxWidth, maxHeight * safeRatio)
        val stageH = stageW / safeRatio

        Box(
            modifier = Modifier
                .size(width = stageW, height = stageH)
                .clip(RoundedCornerShape(8.dp))
                .clipToBounds()
                .background(stageBackgroundColor)
                .border(1.dp, VideoStarIosDivider, RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        onStageTouched()
                    }
                }
                .pointerInput(selectedLayerIndex) {
                    detectTransformGestures { _, pan, zoom, rotation ->
                        onTransformGesture(pan.x, pan.y, zoom, rotation)
                    }
                }
                .pointerInput(selectedLayerIndex) {
                    detectTapGestures(
                        onDoubleTap = { onDoubleTapReset() }
                    )
                }
                .testTag("classic_layers_stage")
        ) {
            val activeVideoCount = layers.count { it.mediaClip is MediaClip.Video }
            val isHardwareGlSupported = !LocalInspectionMode.current &&
                !Build.FINGERPRINT.contains("robolectric", ignoreCase = true)

            layers.forEachIndexed { index, rawLayer ->
                val layer = if (isPlaying || rawLayer.keyframes.isNotEmpty()) {
                    interpolateLayer(rawLayer, stageProgress)
                } else rawLayer
                val isSelected = selectedLayerIndex == index
                val clip = layer.mediaClip
                val layerColor = layer.backgroundColorArgb?.let { Color(it.toInt()) }
                val activeLayerPasses = layer.effectPasses.filter { it.isEnabled && it.effectId != "none" }
                val useLayerGl = activeLayerPasses.isNotEmpty() &&
                    isHardwareGlSupported &&
                    (clip != null || layerColor != null)

                if (clip != null || layerColor != null || layer.effectiveText.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .applyLayerMask(layer)
                            .graphicsLayer {
                                translationX = layer.offsetX
                                translationY = layer.offsetY
                                val mirrorMult = if (layer.isMirrored) -1f else 1f
                                val flipMult = if (layer.isFlippedVertical) -1f else 1f
                                scaleX = layer.scale * mirrorMult
                                scaleY = layer.scale * flipMult
                                rotationZ = layer.rotation
                                if (layer.is3DEnabled) {
                                    rotationX = layer.rotationX
                                    rotationY = layer.rotationY
                                    cameraDistance = (layer.perspectiveFov / 3.5f).coerceIn(6f, 24f) * density
                                }
                                alpha = layer.opacity
                            }
                            .then(
                                if (isSelected) {
                                    Modifier.border(1.5.dp, ClassicRed, RoundedCornerShape(6.dp))
                                } else Modifier
                            )
                    ) {
                        if (useLayerGl) {
                            GlLayerRenderStage(
                                layer = layer,
                                progress = stageProgress,
                                isPlaying = isPlaying,
                                isMuted = isMuted,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            if (clip != null) {
                                MediaClipStageContent(
                                    clip = clip,
                                    isPlaying = isPlaying,
                                    positionMs = (stageProgress * clip.effectiveDurationMs).toLong(),
                                    isMuted = isMuted,
                                    isFitMode = clip.isFitMode,
                                    useLiveVideoPlayer = clip is MediaClip.Video && (isSelected || activeVideoCount <= 2),
                                    contentDescription = "الطبقة ${layer.number}",
                                    colorFilter = if (layer.tintColor != Color.Transparent) {
                                        ColorFilter.tint(layer.tintColor.copy(alpha = 0.42f), BlendMode.SrcAtop)
                                    } else null,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (layerColor != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(layerColor)
                                )
                            }

                            if ((clip != null || layerColor != null) && layer.effect != LayerEffectType.NONE) {
                                val tintOverlay = when (layer.effect) {
                                    LayerEffectType.NEON_GLOW -> ClassicRed.copy(alpha = 0.18f)
                                    LayerEffectType.RGB_SPLIT -> Color.Cyan.copy(alpha = 0.16f)
                                    LayerEffectType.DREAM_BLUR -> ClassicGreen.copy(alpha = 0.16f)
                                    LayerEffectType.VINTAGE_PULSE -> Color(0xFFFFB74D).copy(alpha = 0.18f)
                                    LayerEffectType.NONE -> Color.Transparent
                                }
                                Box(modifier = Modifier.fillMaxSize().background(tintOverlay))
                            }
                        }

                        if (layer.effectiveText.isNotBlank()) {
                            RichLayerTextOverlay(layer = layer)
                        }
                    }
                }
            }

            if (layers.all { it.layerMediaType == LayerMediaType.NONE }) {
                val targetIdx = selectedLayerIndex ?: 0
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, ClassicGreen.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                        .clickable { onRequestImportLayerMedia(targetIdx) }
                        .testTag("layers_empty_stage_import_button")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AddPhotoAlternate,
                            contentDescription = "استيراد وسائط",
                            tint = ClassicGreen,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "استيراد فيديو أو صورة\nللطبقة ${targetIdx + 1}",
                            fontFamily = CairoFontFamily,
                            color = VideoStarWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val selIdx = selectedLayerIndex
                if (selIdx != null && layers.getOrNull(selIdx)?.layerMediaType == LayerMediaType.NONE) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .clickable { onRequestImportLayerMedia(selIdx) }
                    ) {
                        Text(
                            text = "استيراد وسائط للطبقة ${layers[selIdx].number}",
                            fontFamily = CairoFontFamily,
                            color = VideoStarWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Small semi-transparent Arabic gesture hint inside preview, disappears on first touch
            StageGestureHintBadge(
                visible = !hasTouchedStage,
                text = stringResource(R.string.ml_hint_drag),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
            )

            // Sleek semi-hidden translucent Video Star player bar overlaid gently at bottom of preview
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.28f),
                    border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.16f)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onTogglePlay,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("classic_layers_play_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(R.string.cd_play_pause),
                                tint = VideoStarWhite,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        IosThinSlider(
                            value = stageProgress,
                            onValueChange = onScrub,
                            activeTrackColor = VideoStarWhite.copy(alpha = 0.92f),
                            inactiveTrackColor = Color.White.copy(alpha = 0.24f),
                            thumbColor = VideoStarWhite,
                            thumbSize = 11.dp,
                            trackHeight = 2.dp,
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                        )

                        IconButton(
                            onClick = onToggleMute,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("classic_layers_mute_button")
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = stringResource(R.string.cd_mute_toggle),
                                tint = VideoStarWhite.copy(alpha = 0.85f),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassicLayersToolPanel(
    tab: ClassicLayerTab,
    activeLayer: EditorLayer,
    containerHeight: androidx.compose.ui.unit.Dp,
    onSelectEffect: (LayerEffectType) -> Unit,
    onOpenEffectsStudio: () -> Unit,
    onSelectTint: (Color) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onTextSpecUpdated: (TextLayerSpec) -> Unit,
    onMaskUpdated: (MaskMode, MaskShape, Float, Boolean, Float) -> Unit,
    onToggleMirror: () -> Unit,
    onToggleFlipVertical: () -> Unit,
    onClose: () -> Unit
) {
    DraggableLayerToolSheet(
        title = "${tab.label} • الطبقة ${activeLayer.number}",
        isDarkTheme = false,
        containerHeight = containerHeight,
        onClose = onClose
    ) {
        when (tab) {
            ClassicLayerTab.LAYER_EFFECTS -> {
                Button(
                    onClick = onOpenEffectsStudio,
                    colors = ButtonDefaults.buttonColors(containerColor = ClassicGreen),
                    modifier = Modifier.fillMaxWidth().testTag("layers_open_gpu_effects_studio")
                ) {
                    Text(
                        text = "فتح استوديو مؤثرات GPU (35 فلتر)",
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LayerEffectType.entries.forEach { effect ->
                        FilterChip(
                            selected = activeLayer.effect == effect,
                            onClick = { onSelectEffect(effect) },
                            label = { Text(effect.arabicLabel, fontFamily = CairoFontFamily) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ClassicRed,
                                selectedLabelColor = VideoStarWhite
                            )
                        )
                    }
                }
            }
            ClassicLayerTab.COLOR -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(
                        Color.Transparent,
                        ClassicRed,
                        ClassicGreen,
                        Color(0xFF00BCD4),
                        Color(0xFFFFB74D)
                    ).forEach { color ->
                        val selected = activeLayer.tintColor == color
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (color == Color.Transparent) Color(0xFFE0E0E5) else color)
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) VideoStarInkDark else VideoStarIosDivider,
                                    shape = CircleShape
                                )
                                .clickable { onSelectTint(color) }
                        )
                    }
                    Slider(
                        value = activeLayer.opacity,
                        onValueChange = onOpacityChange,
                        valueRange = 0.2f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = ClassicGreen,
                            activeTrackColor = ClassicGreen
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            ClassicLayerTab.TEXT -> {
                TextLayerEditor(
                    activeLayer = activeLayer,
                    isDarkTheme = false,
                    onTextSpecUpdated = onTextSpecUpdated,
                    onClose = onClose
                )
            }
            ClassicLayerTab.MIRROR -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onToggleMirror,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (activeLayer.isMirrored) "إلغاء الانعكاس الأفقي" else "انعكاس أفقي",
                            fontFamily = CairoFontFamily,
                            color = if (activeLayer.isMirrored) ClassicRed else ClassicGreen
                        )
                    }
                    OutlinedButton(
                        onClick = onToggleFlipVertical,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (activeLayer.isFlippedVertical) "إلغاء القلب العمودي" else "قلب عمودي",
                            fontFamily = CairoFontFamily,
                            color = if (activeLayer.isFlippedVertical) ClassicRed else ClassicGreen
                        )
                    }
                }
                MaskEditorSheet(
                    activeLayer = activeLayer,
                    isDarkTheme = false,
                    onMaskUpdated = onMaskUpdated,
                    onClose = onClose
                )
            }
            ClassicLayerTab.LAYOUT -> Unit
        }
    }
}

@Composable
private fun ClassicLayersBottomBar(
    activeTab: ClassicLayerTab?,
    hasSelectedLayer: Boolean,
    onTabSelected: (ClassicLayerTab) -> Unit
) {
    Surface(
        color = VideoStarLightToolbar,
        shadowElevation = 2.dp
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            ) {
                HorizontalDivider(thickness = 0.8.dp, color = VideoStarIosDivider)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val effectsColor = when {
                        !hasSelectedLayer -> Color(0xFFB0B0B6)
                        activeTab == ClassicLayerTab.LAYER_EFFECTS -> ClassicRed
                        else -> ClassicGreen
                    }
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onTabSelected(ClassicLayerTab.LAYER_EFFECTS) }
                            .testTag("classic_tab_layer_effects"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "تأثيرات\nالطبقة",
                            fontFamily = CairoFontFamily,
                            color = effectsColor,
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }

                    ClassicBottomIconTab(
                        icon = Icons.Outlined.GridView,
                        label = ClassicLayerTab.LAYOUT.label,
                        isSelected = activeTab == ClassicLayerTab.LAYOUT,
                        onClick = { onTabSelected(ClassicLayerTab.LAYOUT) },
                        testTag = "classic_tab_layout",
                        modifier = Modifier.weight(1f)
                    )

                    ClassicBottomIconTab(
                        icon = Icons.Outlined.Brush,
                        label = ClassicLayerTab.COLOR.label,
                        isSelected = activeTab == ClassicLayerTab.COLOR,
                        onClick = { onTabSelected(ClassicLayerTab.COLOR) },
                        testTag = "classic_tab_color",
                        modifier = Modifier.weight(1f)
                    )

                    Column(
                        modifier = Modifier
                            .weight(0.95f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onTabSelected(ClassicLayerTab.TEXT) }
                            .testTag("classic_tab_text"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "أ ب",
                            fontFamily = CairoFontFamily,
                            color = if (activeTab == ClassicLayerTab.TEXT) ClassicRed else ClassicGreen,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = ClassicLayerTab.TEXT.label,
                            fontFamily = CairoFontFamily,
                            color = if (activeTab == ClassicLayerTab.TEXT) ClassicRed else ClassicGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    ClassicBottomIconTab(
                        icon = Icons.Outlined.Flip,
                        label = ClassicLayerTab.MIRROR.label,
                        isSelected = activeTab == ClassicLayerTab.MIRROR,
                        onClick = { onTabSelected(ClassicLayerTab.MIRROR) },
                        testTag = "classic_tab_mirror",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ClassicBottomIconTab(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val tint = if (isSelected) ClassicRed else ClassicGreen
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontFamily = CairoFontFamily,
            color = tint,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LayersScreenDialogs(
    uiState: LayersUiState,
    viewModel: LayersViewModel,
    launchMediaPickerForSlot: (Int, ActivityResultContracts.PickVisualMedia.VisualMediaType) -> Unit
) {
    when (uiState.activeDialog) {
        LayersDialogState.ColorWheel -> {
            val palette = listOf(
                Color(0xFF141418),
                Color(0xFF000000),
                Color(0xFFFFFFFF),
                Color(0xFF1A237E),
                Color(0xFF880E4F),
                Color(0xFF1B5E20)
            )
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                containerColor = VideoStarWhite,
                title = { Text("لون خلفية العرض", fontFamily = CairoFontFamily, color = VideoStarInkDark) },
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        palette.forEach { swatch ->
                            val selected = uiState.stageBackgroundColor == swatch
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) ClassicRed else VideoStarIosDivider,
                                        shape = CircleShape
                                    )
                                    .clickable { viewModel.setStageBackgroundColor(swatch) }
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = viewModel::dismissDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = ClassicGreen)
                    ) {
                        Text(stringResource(R.string.btn_close), fontFamily = CairoFontFamily)
                    }
                }
            )
        }
        LayersDialogState.QrImport -> {
            val presets = listOf(
                "حركة زوم + إمالة سريعة",
                "نبض نيون متدرج",
                "تدوير سينمائي مزدوج"
            )
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                containerColor = VideoStarWhite,
                title = { Text("استيراد أكواد QR", fontFamily = CairoFontFamily, color = VideoStarInkDark) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("اختر قالباً لتطبيق مفاتيح الحركة فوراً:", fontFamily = CairoFontFamily, color = VideoStarInkMuted)
                        presets.forEach { preset ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF6F6F9),
                                border = BorderStroke(1.dp, ClassicGreen.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.applyQrPreset(preset) }
                            ) {
                                Text(
                                    text = preset,
                                    fontFamily = CairoFontFamily,
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
                        Text(stringResource(R.string.btn_close), fontFamily = CairoFontFamily, color = ClassicRed)
                    }
                }
            )
        }
        LayersDialogState.Info -> {
            val layer = uiState.activeLayer
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                containerColor = VideoStarWhite,
                title = { Text("معلومات الطبقة", fontFamily = CairoFontFamily, color = VideoStarInkDark) },
                text = {
                    if (layer == null) {
                        Text("لا توجد طبقة محددة حالياً. اختر أي طبقة من الشريط العلوي.", fontFamily = CairoFontFamily, color = VideoStarInkMuted)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("الطبقة: ${layer.number}", fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = ClassicGreen)
                            Text("الموضع: X=${layer.offsetX.toInt()}, Y=${layer.offsetY.toInt()}", fontFamily = CairoFontFamily)
                            Text("الحجم: ${(layer.scale * 100).toInt()}%", fontFamily = CairoFontFamily)
                            Text("الدوران: ${layer.rotation.toInt()}°", fontFamily = CairoFontFamily)
                            Text("وضع 3D: ${if (layer.is3DEnabled) "مفعل" else "غير مفعل"}", fontFamily = CairoFontFamily)
                            Text("الانعكاس: ${if (layer.isMirrored) "أفقي مفعل" else "عادي"}", fontFamily = CairoFontFamily)
                            Text("نقاط Keyframes: ${layer.keyframes.size}", fontFamily = CairoFontFamily)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = viewModel::dismissDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = ClassicGreen)
                    ) {
                        Text("موافق", fontFamily = CairoFontFamily)
                    }
                }
            )
        }
        LayersDialogState.Hierarchy -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                containerColor = VideoStarWhite,
                title = { Text("ترتيب وإدارة الطبقات", fontFamily = CairoFontFamily, color = VideoStarInkDark) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        uiState.layers.forEachIndexed { idx, layer ->
                            val isSelected = idx == uiState.selectedLayerIndex
                            val statusTag = when (layer.layerMediaType) {
                                LayerMediaType.VIDEO -> "فيديو"
                                LayerMediaType.PHOTO -> "صورة"
                                LayerMediaType.COLOR -> "لون"
                                LayerMediaType.NONE -> "فارغة"
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) ClassicGreen.copy(alpha = 0.1f) else Color(0xFFF6F6F9))
                                    .clickable { viewModel.selectLayer(idx) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "L${layer.number} ($statusTag)",
                                    fontFamily = CairoFontFamily,
                                    color = if (isSelected) ClassicGreen else VideoStarInkDark,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.openDialog(LayersDialogState.PickLayerMedia(idx)) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = "استيراد أو تبديل", tint = ClassicGreen, modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { viewModel.openDialog(LayersDialogState.PickLayerColor(idx)) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Outlined.Palette, contentDescription = "تعيين لون", tint = ClassicRed, modifier = Modifier.size(18.dp))
                                }
                                if (layer.layerMediaType != LayerMediaType.NONE) {
                                    IconButton(
                                        onClick = { viewModel.clearLayerSlot(idx) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "تفريغ", tint = VideoStarInkMuted, modifier = Modifier.size(18.dp))
                                    }
                                }
                                IconButton(
                                    onClick = { viewModel.moveLayerUp(idx) },
                                    enabled = idx > 0,
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Rounded.ArrowUpward, contentDescription = "أعلى", tint = ClassicGreen, modifier = Modifier.size(17.dp))
                                }
                                IconButton(
                                    onClick = { viewModel.moveLayerDown(idx) },
                                    enabled = idx < uiState.layers.lastIndex,
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Rounded.ArrowDownward, contentDescription = "أسفل", tint = ClassicRed, modifier = Modifier.size(17.dp))
                                }
                            }
                        }
                        Button(
                            onClick = viewModel::addNewLayer,
                            colors = ButtonDefaults.buttonColors(containerColor = ClassicGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("إضافة طبقة جديدة (+)", fontFamily = CairoFontFamily)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissDialog) {
                        Text("تم", fontFamily = CairoFontFamily, color = ClassicGreen, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
        LayersDialogState.TipsAndPresets -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                containerColor = VideoStarWhite,
                title = { Text("نصائح سريعة", fontFamily = CairoFontFamily, color = VideoStarInkDark) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("• اضغط على أي طبقة فارغة (1-5) لاستيراد فيديو أو صورة مباشرة.", fontFamily = CairoFontFamily, color = VideoStarInkMuted)
                        Text("• اسحب بإصبع واحد لتحريك الطبقة، وبإصبعين للتكبير والتدوير.", fontFamily = CairoFontFamily, color = VideoStarInkMuted)
                        Text("• انقر مرتين على المعاينة لإعادة ضبط الموضع والحجم فوراً.", fontFamily = CairoFontFamily, color = VideoStarInkMuted)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = viewModel::dismissDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = ClassicRed)
                    ) {
                        Text("فهمت", fontFamily = CairoFontFamily)
                    }
                }
            )
        }
        is LayersDialogState.PickLayerMedia -> {
            val targetIdx = (uiState.activeDialog as LayersDialogState.PickLayerMedia).layerIndex
            val layerNum = uiState.layers.getOrNull(targetIdx)?.number ?: (targetIdx + 1)
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                containerColor = VideoStarWhite,
                title = { Text("محتوى الطبقة $layerNum", fontFamily = CairoFontFamily, color = VideoStarInkDark) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "اختر فيديو أو صورة من معرض هاتفك لضبطها، أو لوناً للطبقة.",
                            fontFamily = CairoFontFamily,
                            color = VideoStarInkMuted,
                            fontSize = 13.sp
                        )
                        Button(
                            onClick = {
                                viewModel.dismissDialog()
                                launchMediaPickerForSlot(targetIdx, ActivityResultContracts.PickVisualMedia.VideoOnly)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ClassicRed),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("layers_pick_video_button")
                        ) {
                            Icon(Icons.Outlined.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("استيراد فيديو من المعرض", fontFamily = CairoFontFamily)
                        }
                        Button(
                            onClick = {
                                viewModel.dismissDialog()
                                launchMediaPickerForSlot(targetIdx, ActivityResultContracts.PickVisualMedia.ImageOnly)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ClassicGreen),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("layers_pick_photo_button")
                        ) {
                            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("استيراد صورة من المعرض", fontFamily = CairoFontFamily)
                        }
                        OutlinedButton(
                            onClick = { viewModel.openDialog(LayersDialogState.PickLayerColor(targetIdx)) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Palette, contentDescription = null, tint = ClassicRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تعيين لون للطبقة", fontFamily = CairoFontFamily, color = VideoStarInkDark)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissDialog) {
                        Text(stringResource(R.string.btn_cancel_ar), fontFamily = CairoFontFamily, color = VideoStarInkMuted)
                    }
                }
            )
        }
        is LayersDialogState.PickLayerColor -> {
            val targetIdx = (uiState.activeDialog as LayersDialogState.PickLayerColor).layerIndex
            val layerNum = uiState.layers.getOrNull(targetIdx)?.number ?: (targetIdx + 1)
            val palette = listOf(
                Color(0xFFD91E18),
                Color(0xFF2E7D32),
                Color(0xFF1565C0),
                Color(0xFF6A1B9A),
                Color(0xFFFFB300),
                Color(0xFFFFFFFF),
                Color(0xFF000000)
            )
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                containerColor = VideoStarWhite,
                title = { Text("لون الطبقة $layerNum", fontFamily = CairoFontFamily, color = VideoStarInkDark) },
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        palette.forEach { swatch ->
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .border(1.5.dp, VideoStarIosDivider, CircleShape)
                                    .clickable { viewModel.assignColorToLayer(targetIdx, swatch) }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissDialog) {
                        Text(stringResource(R.string.btn_close), fontFamily = CairoFontFamily, color = ClassicRed)
                    }
                }
            )
        }
        null -> Unit
    }
}
