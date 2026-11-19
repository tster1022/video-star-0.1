package com.example.ui.screens.multilayer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.unit.Dp
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.LocalInspectionMode
import com.example.R
import com.example.data.MediaImportHelper
import com.example.gl.GlLayerRenderStage
import com.example.model.EditorLayer
import com.example.model.LayerEffectType
import com.example.model.LayerMediaType
import com.example.model.MaskMode
import com.example.model.MaskShape
import com.example.model.MediaClip
import com.example.model.MultiLayerTab
import com.example.model.TextLayerSpec
import com.example.ui.components.IosThinSlider
import com.example.ui.components.MediaClipStageContent
import com.example.ui.components.RichLayerTextOverlay
import com.example.ui.components.applyLayerMask
import com.example.ui.components.resolveMaskShape
import com.example.ui.screens.layers.CompactHorizontalLayersStrip
import com.example.ui.screens.layers.DraggableLayerToolSheet
import com.example.ui.screens.layers.FloatingPreviewToolsOverlay
import com.example.ui.screens.layers.MaskEditorSheet
import com.example.ui.screens.layers.StageGestureHintBadge
import com.example.ui.screens.layers.TextLayerEditor
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.VideoStarDarkBackground
import com.example.ui.theme.VideoStarDarkBorder
import com.example.ui.theme.VideoStarDarkButton
import com.example.ui.theme.VideoStarDarkHintText
import com.example.ui.theme.VideoStarDarkSurface
import com.example.ui.theme.VideoStarMagenta
import com.example.ui.theme.VideoStarNeonPink
import com.example.ui.theme.VideoStarWhite
import com.example.viewmodel.SharedProjectViewModel
import java.util.Locale

@Composable
fun MultiLayerScreen(
    projectId: String,
    sharedProjectViewModel: SharedProjectViewModel,
    onCancel: () -> Unit,
    onCreated: () -> Unit,
    onOpenAdjustVideoForLayer: (projectId: String, mediaUri: String, durationMs: Long, sourceFps: Int, targetLayerIndex: Int) -> Unit,
    onOpenAdjustPhotoForLayer: (projectId: String, mediaUri: String, targetLayerIndex: Int) -> Unit,
    onSwitchToLightTheme: (() -> Unit)? = null,
    onOpenLayerEffectsStudio: (String, String) -> Unit = { _, _ -> }
) {
    val viewModel: MultiLayerViewModel = viewModel(
        key = "multilayer_$projectId",
        factory = remember(projectId) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MultiLayerViewModel(projectId) as T
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
        val layerIdx = pendingLayerIndex ?: uiState.selectedLayerIndex
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
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshFromRepository()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(projectId) {
        viewModel.refreshFromRepository()
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
        containerColor = VideoStarDarkBackground,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VideoStarDarkBackground)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            ) {
                MultiLayerTopBar(
                    onCancelClick = onCancel,
                    onQrClick = { viewModel.openDialog(MultiLayerDialogState.QrImport) },
                    onSwitchToLightClick = onSwitchToLightTheme?.let { switchFn ->
                        {
                            viewModel.syncToRepository()
                            switchFn()
                        }
                    },
                    onInfoClick = { viewModel.openDialog(MultiLayerDialogState.Info) },
                    onCameraClick = viewModel::captureSnapshot,
                    onCreateClick = { viewModel.commitAndCreate(onCreated) }
                )
            }
        },
        bottomBar = {
            MultiLayerBottomBar(
                activeTab = uiState.activeBottomTab,
                onTabSelected = { tab ->
                    if (tab == MultiLayerTab.LAYER_EFFECTS) {
                        viewModel.syncToRepository()
                        val layerId = uiState.activeLayer?.id ?: "layer_1"
                        onOpenLayerEffectsStudio(projectId, layerId)
                    } else {
                        viewModel.onBottomTabClicked(tab)
                    }
                }
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(VideoStarDarkBackground)
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
                    isDarkTheme = true,
                    colorWheelTag = "multilayer_color_wheel_button",
                    layerButtonTagPrefix = "layer_button_",
                    addLayerTag = "multilayer_add_layer_button",
                    manageLayersTag = "manage_layers_grid_button",
                    onColorWheelClick = { viewModel.openDialog(MultiLayerDialogState.ColorWheelPicker) },
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
                    onManageLayersClick = { viewModel.openDialog(MultiLayerDialogState.ManageLayers) }
                )

                // Expanded Full-Width Preview Stage taking flex:1 remaining space (min 45% height)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .heightIn(min = minStageHeight)
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    InteractiveMultiLayerStage(
                        layers = uiState.layers,
                        selectedLayerIndex = uiState.selectedLayerIndex,
                        projectAspectRatio = uiState.projectAspectRatio,
                        stageBackgroundColor = uiState.stageBackgroundColor,
                        showGridOverlay = uiState.isGridOverlayVisible,
                        isPlaying = uiState.isPlaying,
                        isMuted = uiState.isMuted,
                        stageProgress = uiState.stageProgress,
                        hasTouchedStage = uiState.hasTouchedStage,
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
                        isDarkTheme = true,
                        is3DEnabled = uiState.activeLayer?.is3DEnabled == true,
                        isSecondaryActive = uiState.isGridOverlayVisible,
                        useGridForThirdButton = true,
                        hierarchyTag = "multilayer_manage_right_button",
                        threeDTag = "multilayer_3d_button",
                        thirdButtonTag = "multilayer_layout_toggle_button",
                        onHierarchyClick = { viewModel.openDialog(MultiLayerDialogState.ManageLayers) },
                        onToggle3DClick = viewModel::toggle3DMode,
                        onThirdButtonClick = viewModel::toggleGridOverlay,
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
                val activeTab = uiState.activeBottomTab
                val activeLayer = uiState.activeLayer
                if (activeTab != null && activeLayer != null) {
                    MultiLayerInteractiveToolPanel(
                        tab = activeTab,
                        activeLayer = activeLayer,
                        containerHeight = bodyHeight,
                        onSelectEffect = viewModel::setLayerEffect,
                        onOpenEffectsStudio = {
                            viewModel.syncToRepository()
                            onOpenLayerEffectsStudio(projectId, activeLayer.id)
                        },
                        onSelectLayoutPreset = viewModel::applyLayoutPreset,
                        onTextSpecUpdated = viewModel::setLayerTextSpec,
                        onSelectTint = viewModel::setLayerTint,
                        onOpacityChange = viewModel::setLayerOpacity,
                        onMaskUpdated = viewModel::updateLayerMask,
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
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = VideoStarDarkSurface,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = VideoStarNeonPink,
                        strokeWidth = 2.5.dp
                    )
                    Text(
                        text = "جاري تجهيز الملف للطبقة...",
                        fontFamily = CairoFontFamily,
                        color = VideoStarWhite,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }

    MultiLayerDialogs(
        uiState = uiState,
        viewModel = viewModel,
        launchMediaPickerForSlot = launchMediaPickerForSlot
    )
}

@Composable
private fun MultiLayerTopBar(
    onCancelClick: () -> Unit,
    onQrClick: () -> Unit,
    onSwitchToLightClick: (() -> Unit)?,
    onInfoClick: () -> Unit,
    onCameraClick: () -> Unit,
    onCreateClick: () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(horizontal = 6.dp),
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
                        .testTag("multilayer_cancel_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.ml_cancel),
                        fontFamily = CairoFontFamily,
                        color = VideoStarNeonPink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                IconButton(
                    onClick = onQrClick,
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("multilayer_qr_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.QrCode2,
                        contentDescription = stringResource(R.string.cd_qr_import),
                        tint = VideoStarNeonPink,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Center: الطبقات + فاتح
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الطبقات",
                    fontFamily = CairoFontFamily,
                    color = VideoStarWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                if (onSwitchToLightClick != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = VideoStarDarkButton,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, VideoStarDarkBorder, RoundedCornerShape(12.dp))
                            .clickable(onClick = onSwitchToLightClick)
                            .testTag("multilayer_switch_light_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LightMode,
                                contentDescription = "الوضع الفاتح",
                                tint = VideoStarWhite,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "فاتح",
                                fontFamily = CairoFontFamily,
                                color = VideoStarWhite,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Left (RTL end): معلومات + كاميرا + إنشاء
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onInfoClick,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("multilayer_info_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.cd_info),
                        tint = VideoStarNeonPink,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onCameraClick,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("multilayer_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CameraAlt,
                        contentDescription = stringResource(R.string.cd_capture_snapshot),
                        tint = VideoStarNeonPink,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onCreateClick)
                        .padding(horizontal = 10.dp)
                        .testTag("multilayer_create_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.ml_create),
                        fontFamily = CairoFontFamily,
                        color = VideoStarNeonPink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun InteractiveMultiLayerStage(
    layers: List<EditorLayer>,
    selectedLayerIndex: Int,
    projectAspectRatio: Float,
    stageBackgroundColor: Color,
    showGridOverlay: Boolean,
    isPlaying: Boolean,
    isMuted: Boolean,
    stageProgress: Float,
    hasTouchedStage: Boolean,
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
            .background(Color.Black.copy(alpha = 0.28f)),
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
                .border(1.dp, VideoStarDarkBorder, RoundedCornerShape(8.dp))
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
                .testTag("multilayer_interactive_stage")
        ) {
            val activeVideoCount = layers.count { it.mediaClip is MediaClip.Video }
            val isHardwareGlSupported = !LocalInspectionMode.current &&
                !Build.FINGERPRINT.contains("robolectric", ignoreCase = true)

            layers.forEachIndexed { index, layer ->
                val isSelected = index == selectedLayerIndex
                val clip = layer.mediaClip
                val layerColor = layer.backgroundColorArgb?.let { Color(it.toInt()) }
                val maskShape = remember(layer.maskShape) { resolveMaskShape(layer.maskShape) }
                val playPulse = if (isPlaying) 1f + (stageProgress * 0.08f) else 1f
                val activeLayerPasses = layer.effectPasses.filter { it.isEnabled && it.effectId != "none" }
                val useLayerGl = activeLayerPasses.isNotEmpty() &&
                    isHardwareGlSupported &&
                    (clip != null || layerColor != null)

                if (clip != null || layerColor != null || layer.effectiveText.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .graphicsLayer {
                                translationX = layer.offsetX
                                translationY = layer.offsetY
                                scaleX = layer.scale * playPulse
                                scaleY = layer.scale * playPulse
                                rotationZ = layer.rotation
                                if (layer.is3DEnabled) {
                                    rotationX = layer.rotationX
                                    rotationY = layer.rotationY
                                    cameraDistance = (layer.perspectiveFov / 3.5f).coerceIn(6f, 24f) * density
                                }
                                alpha = layer.opacity
                            }
                            .applyLayerMask(layer)
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        width = 1.5.dp,
                                        color = VideoStarNeonPink.copy(alpha = 0.85f),
                                        shape = maskShape
                                    )
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
                                        ColorFilter.tint(layer.tintColor.copy(alpha = 0.4f), BlendMode.SrcAtop)
                                    } else null,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (layerColor != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(layerColor)
                                )
                            }

                            if ((clip != null || layerColor != null) && layer.effect != LayerEffectType.NONE) {
                                val glowColor = when (layer.effect) {
                                    LayerEffectType.NEON_GLOW -> VideoStarNeonPink.copy(alpha = 0.20f)
                                    LayerEffectType.RGB_SPLIT -> Color.Cyan.copy(alpha = 0.18f)
                                    LayerEffectType.DREAM_BLUR -> Color.Magenta.copy(alpha = 0.16f)
                                    LayerEffectType.VINTAGE_PULSE -> Color(0xFFFFB74D).copy(alpha = 0.18f)
                                    LayerEffectType.NONE -> Color.Transparent
                                }
                                Box(modifier = Modifier.fillMaxSize().background(glowColor))
                            }
                        }

                        if (layer.effectiveText.isNotBlank()) {
                            RichLayerTextOverlay(layer = layer)
                        }
                    }
                }
            }

            if (layers.all { it.layerMediaType == LayerMediaType.NONE }) {
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                        .border(1.dp, VideoStarNeonPink.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        .clickable { onRequestImportLayerMedia(selectedLayerIndex) }
                        .testTag("multilayer_empty_stage_import_button")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AddPhotoAlternate,
                            contentDescription = "استيراد وسائط",
                            tint = VideoStarNeonPink,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "استيراد فيديو أو صورة\nللطبقة ${selectedLayerIndex + 1}",
                            fontFamily = CairoFontFamily,
                            color = VideoStarWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (layers.getOrNull(selectedLayerIndex)?.layerMediaType == LayerMediaType.NONE) {
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 10.dp, top = 38.dp)
                        .clickable { onRequestImportLayerMedia(selectedLayerIndex) }
                ) {
                    Text(
                        text = "+ استيراد للطبقة ${selectedLayerIndex + 1}",
                        fontFamily = CairoFontFamily,
                        color = VideoStarNeonPink,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (showGridOverlay) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val lineColor = VideoStarNeonPink.copy(alpha = 0.45f)
                    drawLine(lineColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1.dp.toPx())
                    drawLine(lineColor, Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), strokeWidth = 1.dp.toPx())
                    drawLine(lineColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1.dp.toPx())
                    drawLine(lineColor, Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), strokeWidth = 1.dp.toPx())
                }
            }

            // Active layer indicator pill top-start inside stage
            layers.getOrNull(selectedLayerIndex)?.let { active ->
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "L${active.number}" +
                            (if (active.is3DEnabled) " • 3D" else "") +
                            (if (active.effect != LayerEffectType.NONE) " • ${active.effect.arabicLabel}" else ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = VideoStarNeonPink,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Small semi-transparent Arabic gesture hint inside preview, disappears on first touch
            StageGestureHintBadge(
                visible = !hasTouchedStage,
                text = stringResource(R.string.ml_hint_drag),
                tag = "multilayer_drag_hint",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 50.dp)
            )

            // Sleek semi-hidden translucent floating playback capsule inside Stage
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.30f),
                    border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.15f)),
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
                                .testTag("multilayer_play_button")
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
                            activeTrackColor = VideoStarNeonPink,
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
                                .testTag("multilayer_mute_button")
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
private fun MultiLayerInteractiveToolPanel(
    tab: MultiLayerTab,
    activeLayer: EditorLayer,
    containerHeight: Dp,
    onSelectEffect: (LayerEffectType) -> Unit,
    onOpenEffectsStudio: () -> Unit,
    onSelectLayoutPreset: (String) -> Unit,
    onTextSpecUpdated: (TextLayerSpec) -> Unit,
    onSelectTint: (Color) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onMaskUpdated: (MaskMode, MaskShape, Float, Boolean, Float) -> Unit,
    onClose: () -> Unit
) {
    var showTextEditor by remember(tab) { mutableStateOf(false) }

    DraggableLayerToolSheet(
        title = "${tab.label} — الطبقة ${activeLayer.number}",
        isDarkTheme = true,
        containerHeight = containerHeight,
        onClose = onClose
    ) {
        when (tab) {
            MultiLayerTab.LAYER_EFFECTS -> {
                Button(
                    onClick = onOpenEffectsStudio,
                    colors = ButtonDefaults.buttonColors(containerColor = VideoStarMagenta),
                    modifier = Modifier.fillMaxWidth().testTag("multilayer_open_gpu_effects_studio")
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
                                selectedContainerColor = VideoStarMagenta,
                                selectedLabelColor = VideoStarWhite,
                                labelColor = VideoStarWhite
                            )
                        )
                    }
                }
            }
            MultiLayerTab.LAYOUT -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = showTextEditor,
                        onClick = { showTextEditor = !showTextEditor },
                        label = { Text("استوديو النصوص", fontFamily = CairoFontFamily) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VideoStarNeonPink,
                            selectedLabelColor = VideoStarWhite,
                            labelColor = VideoStarWhite
                        )
                    )
                    listOf("مركز", "انقسام علوي", "تدوير مائل", "تكبير درامي").forEach { preset ->
                        FilterChip(
                            selected = activeLayer.layoutPreset == preset,
                            onClick = { onSelectLayoutPreset(preset) },
                            label = { Text(preset, fontFamily = CairoFontFamily) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VideoStarMagenta,
                                selectedLabelColor = VideoStarWhite,
                                labelColor = VideoStarWhite
                            )
                        )
                    }
                }
                if (showTextEditor) {
                    TextLayerEditor(
                        activeLayer = activeLayer,
                        isDarkTheme = true,
                        onTextSpecUpdated = onTextSpecUpdated,
                        onClose = { showTextEditor = false }
                    )
                }
            }
            MultiLayerTab.COLOR -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        Color.Transparent,
                        VideoStarNeonPink,
                        Color.Cyan,
                        Color(0xFFFFB74D),
                        Color(0xFF9C27B0)
                    ).forEach { color ->
                        val selected = activeLayer.tintColor == color
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(if (color == Color.Transparent) VideoStarDarkButton else color)
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) VideoStarWhite else VideoStarDarkBorder,
                                    shape = CircleShape
                                )
                                .clickable { onSelectTint(color) }
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Slider(
                        value = activeLayer.opacity,
                        onValueChange = onOpacityChange,
                        valueRange = 0.2f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = VideoStarNeonPink,
                            activeTrackColor = VideoStarNeonPink
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            MultiLayerTab.MASK -> {
                MaskEditorSheet(
                    activeLayer = activeLayer,
                    isDarkTheme = true,
                    onMaskUpdated = onMaskUpdated,
                    onClose = onClose
                )
            }
        }
    }
}

@Composable
private fun MultiLayerBottomBar(
    activeTab: MultiLayerTab?,
    onTabSelected: (MultiLayerTab) -> Unit
) {
    Surface(
        color = VideoStarDarkSurface,
        tonalElevation = 2.dp
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .height(60.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val effectsSelected = activeTab == MultiLayerTab.LAYER_EFFECTS
                Box(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                        .padding(4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (effectsSelected) VideoStarDarkButton else Color.Transparent)
                        .clickable { onTabSelected(MultiLayerTab.LAYER_EFFECTS) }
                        .testTag("multilayer_tab_layer_effects"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.ml_tab_layer_effects),
                        fontFamily = CairoFontFamily,
                        color = VideoStarNeonPink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }

                MultiLayerIconTab(
                    icon = Icons.Outlined.GridView,
                    label = stringResource(R.string.ml_tab_layout),
                    isSelected = activeTab == MultiLayerTab.LAYOUT,
                    onClick = { onTabSelected(MultiLayerTab.LAYOUT) },
                    testTag = "multilayer_tab_layout",
                    modifier = Modifier.weight(1f)
                )

                MultiLayerIconTab(
                    icon = Icons.Outlined.Brush,
                    label = stringResource(R.string.ml_tab_color),
                    isSelected = activeTab == MultiLayerTab.COLOR,
                    onClick = { onTabSelected(MultiLayerTab.COLOR) },
                    testTag = "multilayer_tab_color",
                    modifier = Modifier.weight(1f)
                )

                MultiLayerIconTab(
                    icon = Icons.Outlined.TheaterComedy,
                    label = stringResource(R.string.ml_tab_mask),
                    isSelected = activeTab == MultiLayerTab.MASK,
                    onClick = { onTabSelected(MultiLayerTab.MASK) },
                    testTag = "multilayer_tab_mask",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MultiLayerIconTab(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) VideoStarDarkButton else Color.Transparent)
            .clickable(onClick = onClick)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = VideoStarNeonPink,
            modifier = Modifier.size(21.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontFamily = CairoFontFamily,
            color = VideoStarNeonPink,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun MultiLayerDialogs(
    uiState: MultiLayerUiState,
    viewModel: MultiLayerViewModel,
    launchMediaPickerForSlot: (Int, ActivityResultContracts.PickVisualMedia.VisualMediaType) -> Unit
) {
    when (uiState.activeDialog) {
        MultiLayerDialogState.ColorWheelPicker -> {
            val palette = listOf(
                Color(0xFF141418),
                Color(0xFF280B1E),
                Color(0xFF0A2438),
                Color(0xFF1D1035),
                Color(0xFF2E1A08),
                Color(0xFF0B2B22)
            )
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                containerColor = VideoStarDarkSurface,
                titleContentColor = VideoStarWhite,
                textContentColor = VideoStarWhite,
                title = { Text("لون خلفية المسرح", fontFamily = CairoFontFamily) },
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        palette.forEach { swatch ->
                            val selected = uiState.stageBackgroundColor == swatch
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) VideoStarNeonPink else VideoStarDarkBorder,
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
                        colors = ButtonDefaults.buttonColors(containerColor = VideoStarMagenta)
                    ) {
                        Text(stringResource(R.string.btn_close), fontFamily = CairoFontFamily)
                    }
                }
            )
        }
        MultiLayerDialogState.QrImport -> {
            val presets = listOf(
                "انتقال نبض 3D السريع",
                "زوم مع دوران نيون",
                "إطار موسيقي مائل"
            )
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                containerColor = VideoStarDarkSurface,
                titleContentColor = VideoStarWhite,
                textContentColor = VideoStarWhite,
                title = { Text("استيراد أكواد QR الجاهزة", fontFamily = CairoFontFamily) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("اختر كود تأثير جاهز لتطبيقه على الطبقة الحالية:", fontFamily = CairoFontFamily, color = VideoStarDarkHintText)
                        presets.forEach { preset ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(VideoStarDarkButton)
                                    .border(1.dp, VideoStarMagenta.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                    .clickable { viewModel.applyQrPreset(preset) }
                                    .padding(12.dp)
                            ) {
                                Text(preset, fontFamily = CairoFontFamily, color = VideoStarWhite, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissDialog) {
                        Text(stringResource(R.string.btn_close), fontFamily = CairoFontFamily, color = VideoStarNeonPink)
                    }
                }
            )
        }
        MultiLayerDialogState.Info -> {
            val layer = uiState.activeLayer
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                containerColor = VideoStarDarkSurface,
                titleContentColor = VideoStarWhite,
                textContentColor = VideoStarWhite,
                title = { Text("تفاصيل الطبقة النشطة", fontFamily = CairoFontFamily) },
                text = {
                    if (layer != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("رقم الطبقة: ${layer.number}", fontFamily = CairoFontFamily)
                            Text("الموضع: X=${layer.offsetX.toInt()}, Y=${layer.offsetY.toInt()}", fontFamily = CairoFontFamily)
                            Text("التكبير: ${String.format(Locale.US, "%.2f", layer.scale)}x", fontFamily = CairoFontFamily)
                            Text("الدوران: ${layer.rotation.toInt()}°", fontFamily = CairoFontFamily)
                            Text("وضع 3D: ${if (layer.is3DEnabled) "مفعل" else "متوقف"}", fontFamily = CairoFontFamily)
                            Text("القناع: ${layer.maskShape.arabicLabel}", fontFamily = CairoFontFamily)
                            Text("التأثير: ${layer.effect.arabicLabel}", fontFamily = CairoFontFamily)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = viewModel::dismissDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = VideoStarMagenta)
                    ) {
                        Text(stringResource(R.string.btn_close), fontFamily = CairoFontFamily)
                    }
                }
            )
        }
        MultiLayerDialogState.ManageLayers -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                containerColor = VideoStarDarkSurface,
                titleContentColor = VideoStarWhite,
                textContentColor = VideoStarWhite,
                title = { Text("إدارة الطبقات (${uiState.layers.size})", fontFamily = CairoFontFamily) },
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
                                    .background(if (isSelected) VideoStarMagenta.copy(alpha = 0.2f) else VideoStarDarkButton)
                                    .clickable { viewModel.selectLayer(idx) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "L${layer.number} ($statusTag)",
                                    fontFamily = CairoFontFamily,
                                    color = if (isSelected) VideoStarNeonPink else VideoStarWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.openDialog(MultiLayerDialogState.PickLayerMedia(idx)) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = "استيراد أو تبديل", tint = VideoStarNeonPink, modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { viewModel.openDialog(MultiLayerDialogState.PickLayerColor(idx)) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Outlined.Palette, contentDescription = "تعيين لون", tint = Color(0xFFFFB74D), modifier = Modifier.size(18.dp))
                                }
                                if (layer.layerMediaType != LayerMediaType.NONE) {
                                    IconButton(
                                        onClick = { viewModel.clearLayerSlot(idx) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "تفريغ", tint = VideoStarDarkHintText, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        Button(
                            onClick = viewModel::addNewLayer,
                            colors = ButtonDefaults.buttonColors(containerColor = VideoStarMagenta),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("إضافة طبقة جديدة (+)", fontFamily = CairoFontFamily)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissDialog) {
                        Text(stringResource(R.string.btn_close), fontFamily = CairoFontFamily, color = VideoStarNeonPink)
                    }
                }
            )
        }
        is MultiLayerDialogState.PickLayerMedia -> {
            val targetIdx = (uiState.activeDialog as MultiLayerDialogState.PickLayerMedia).layerIndex
            val layerNum = uiState.layers.getOrNull(targetIdx)?.number ?: (targetIdx + 1)
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                containerColor = VideoStarDarkSurface,
                titleContentColor = VideoStarWhite,
                textContentColor = VideoStarWhite,
                title = { Text("محتوى الطبقة $layerNum", fontFamily = CairoFontFamily) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "اختر فيديو أو صورة من معرض هاتفك لضبطها، أو لوناً للطبقة.",
                            fontFamily = CairoFontFamily,
                            color = VideoStarDarkHintText,
                            fontSize = 13.sp
                        )
                        Button(
                            onClick = {
                                viewModel.dismissDialog()
                                launchMediaPickerForSlot(targetIdx, ActivityResultContracts.PickVisualMedia.VideoOnly)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VideoStarMagenta),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("multilayer_pick_video_button")
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
                            colors = ButtonDefaults.buttonColors(containerColor = VideoStarDarkButton),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("multilayer_pick_photo_button")
                        ) {
                            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, tint = VideoStarNeonPink, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("استيراد صورة من المعرض", fontFamily = CairoFontFamily, color = VideoStarWhite)
                        }
                        OutlinedButton(
                            onClick = { viewModel.openDialog(MultiLayerDialogState.PickLayerColor(targetIdx)) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Palette, contentDescription = null, tint = VideoStarNeonPink, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تعيين لون للطبقة", fontFamily = CairoFontFamily, color = VideoStarWhite)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissDialog) {
                        Text(stringResource(R.string.btn_close), fontFamily = CairoFontFamily, color = VideoStarNeonPink)
                    }
                }
            )
        }
        is MultiLayerDialogState.PickLayerColor -> {
            val targetIdx = (uiState.activeDialog as MultiLayerDialogState.PickLayerColor).layerIndex
            val layerNum = uiState.layers.getOrNull(targetIdx)?.number ?: (targetIdx + 1)
            val palette = listOf(
                Color(0xFFFF2A85),
                Color(0xFF9C27B0),
                Color(0xFF00BCD4),
                Color(0xFF4CAF50),
                Color(0xFFFFB300),
                Color(0xFFFFFFFF),
                Color(0xFF000000)
            )
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                containerColor = VideoStarDarkSurface,
                titleContentColor = VideoStarWhite,
                textContentColor = VideoStarWhite,
                title = { Text("لون الطبقة $layerNum", fontFamily = CairoFontFamily) },
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
                                    .border(1.5.dp, VideoStarDarkBorder, CircleShape)
                                    .clickable { viewModel.assignColorToLayer(targetIdx, swatch) }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissDialog) {
                        Text(stringResource(R.string.btn_close), fontFamily = CairoFontFamily, color = VideoStarNeonPink)
                    }
                }
            )
        }
        null -> Unit
    }
}
