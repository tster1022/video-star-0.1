package com.example.ui.screens.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.VideoStarRepository
import com.example.engine.render.StudioRenderErrorBoundary
import com.example.model.AeLayerKind
import com.example.model.AspectRatioMode
import com.example.model.EditorLayer
import com.example.model.MediaClip
import com.example.model.StudioLayoutMode
import com.example.model.TimelineScene
import com.example.model.VideoProject
import android.os.Build
import androidx.compose.ui.platform.LocalInspectionMode
import com.example.gl.AeCompositeGlStage
import com.example.ui.components.MediaUriThumbnail
import com.example.ui.components.SceneRenderStage
import com.example.ui.theme.InterFontFamily
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// Exact After Effects Mode Color Tokens
private val AeDeepNavyBg = Color(0xFF0B0D17)
private val AeCanvasBackdrop = Color(0xFF10121E)
private val AeSlateSurface = Color(0xFF151828)
private val AeCardSurface = Color(0xFF181C2E)
private val AeTealSheetBg = Color(0xFF132226)
private val AeTealSidebarBg = Color(0xFF0F1B1F)
private val AeTealMenuBg = Color(0xFF132C33)
private val AeNeonGreen = Color(0xFF00E676)
private val AeNeonGreenGlow = Color(0xFF00C853)
private val AeRedPlayhead = Color(0xFFFF3B30)
private val AeMutedGray = Color(0xFF8E94A5)
private val AeDiamondPurple = Color(0xFF7C5CFF)
private val AeSelectionBlue = Color(0xFF3B82F6)
private val AePivotYellow = Color(0xFFFACC15)

private enum class AeBottomSheetState {
    IDLE,
    ADD_SHEET,
    LAYER_PROPERTIES,
    LAYER_EFFECTS,
    TRANSFORM_PANEL,
    BLENDING_PANEL,
    BORDER_SHADOW_PANEL,
    TIMING_SPEED_PANEL,
    ANIMATE_PANEL
}

private enum class AeAddTab(val label: String) {
    MEDIA("Media"),
    AUDIO("Audio"),
    SHAPE("Shape"),
    ELEMENT("Element")
}

internal data class AeTimelineZoomScale(
    val zoomFactor: Float,
    val dpPerSecond: Float,
    val majorStepMs: Long,
    val minorStepMs: Long
)

internal fun computeAeTimelineZoomScale(zoomFactor: Float): AeTimelineZoomScale {
    val clampedZoom = zoomFactor.coerceIn(0.22f, 3.6f)
    val dpPerSecond = 88f * clampedZoom
    val majorStepMs = when {
        dpPerSecond < 30f -> 5_000L
        dpPerSecond < 56f -> 2_000L
        dpPerSecond > 190f -> 500L
        else -> 1_000L
    }
    val minorStepMs = when {
        majorStepMs >= 5_000L -> 1_000L
        majorStepMs >= 2_000L -> 500L
        majorStepMs <= 500L -> 100L
        else -> 200L
    }
    return AeTimelineZoomScale(
        zoomFactor = clampedZoom,
        dpPerSecond = dpPerSecond,
        majorStepMs = majorStepMs,
        minorStepMs = minorStepMs
    )
}

@Composable
fun AfterEffectsModeScreen(
    project: VideoProject,
    uiState: WorkspaceUiState,
    showShutterFlash: Boolean,
    callbacks: StudioLayoutCallbacks,
    modifier: Modifier = Modifier,
    chromeAlpha: Float = 1f
) {
    var sheetState by remember(project.id) { mutableStateOf(AeBottomSheetState.IDLE) }
    var selectedAddTab by remember { mutableStateOf(AeAddTab.MEDIA) }
    var isFitCanvasExpanded by remember { mutableStateOf(false) }
    var resolutionBadge by remember { mutableStateOf("Auto") }
    var showMoreMenu by remember { mutableStateOf(false) }
    var renameTargetLayer by remember { mutableStateOf<EditorLayer?>(null) }
    var renameDraftText by remember { mutableStateOf("") }
    var aeTimelineExtraHeightDp by remember(project.id) { mutableFloatStateOf(0f) }
    var aeTimelineZoom by remember(project.id) { mutableFloatStateOf(1f) }
    var aeOpenBrowserSeq by remember(project.id) { mutableIntStateOf(0) }
    var aeBrowserInitialCategory by remember(project.id) { mutableStateOf<String?>(null) }

    // Two-step transparent -> highlighted trigger state + outside-tap disarm bounds
    var isAddEffectArmed by remember(project.id) { mutableStateOf(false) }
    var addEffectTriggerBounds by remember { mutableStateOf<Rect?>(null) }
    var isColorizeArmed by remember(project.id) { mutableStateOf(false) }
    var colorizeTriggerBounds by remember { mutableStateOf<Rect?>(null) }

    // Hydrate legacy scenes into vertical AE layer stack on mount if needed
    LaunchedEffect(project.id, project.scenes.size, project.aeLayers.size) {
        if (project.aeLayers.isEmpty() && project.scenes.isNotEmpty()) {
            VideoStarRepository.ensureAeLayersHydrated(project.id)
        }
    }

    val effectiveAeLayers = remember(project.aeLayers, project.scenes) {
        StudioRenderErrorBoundary.safeEvaluate("ae_effective_layers", emptyList()) {
            if (project.aeLayers.isNotEmpty()) {
                project.aeLayers
            } else {
                var cursor = 0L
                project.scenes.mapIndexed { idx, scene ->
                    val dur = scene.effectiveDurationMs
                    val fallbackLayer = EditorLayer(
                        id = "ae_sync_${scene.id}_$idx",
                        number = idx + 1,
                        title = scene.title,
                        layerKind = AeLayerKind.MEDIA,
                        mediaClip = scene.clip,
                        effectPasses = scene.resolvedEffectPasses(),
                        scale = scene.transform.scale,
                        rotation = scene.transform.rotation,
                        offsetX = scene.transform.offsetX,
                        offsetY = scene.transform.offsetY,
                        isMirrored = scene.transform.flipH,
                        isFlippedVertical = scene.transform.flipV,
                        isVisible = true,
                        isLocked = false,
                        startTimeMs = cursor,
                        durationMs = dur,
                        zIndex = idx,
                        sourceSceneId = scene.id
                    )
                    cursor += dur
                    fallbackLayer
                }.asReversed()
            }
        }
    }

    val dragStartOverrides = remember(project.id) { mutableStateMapOf<String, Long>() }
    var isDraggingLayerBlock by remember(project.id) { mutableStateOf(false) }
    var isCanvasScrubbing by remember(project.id) { mutableStateOf(false) }
    var isTimelineScrubbing by remember(project.id) { mutableStateOf(false) }

    val displayAeLayers = remember(effectiveAeLayers, dragStartOverrides.toMap()) {
        if (dragStartOverrides.isEmpty()) {
            effectiveAeLayers
        } else {
            effectiveAeLayers.map { layer ->
                val overrideStart = dragStartOverrides[layer.id]
                if (overrideStart != null && !layer.isLocked) {
                    layer.copy(startTimeMs = overrideStart.coerceAtLeast(0L))
                } else {
                    layer
                }
            }
        }
    }

    val resolvedTotalDurationMs = remember(displayAeLayers, uiState.totalDurationMs) {
        if (displayAeLayers.isEmpty()) {
            uiState.totalDurationMs
        } else {
            maxOf(
                uiState.totalDurationMs,
                displayAeLayers.maxOf { (it.startTimeMs + it.effectiveLayerDurationMs).coerceAtLeast(500L) }
            )
        }
    }

    val selectedLayer = remember(displayAeLayers, uiState.selectedAeLayerId, uiState.currentPositionMs) {
        uiState.selectedAeLayerId?.let { selId -> displayAeLayers.firstOrNull { it.id == selId } }
            ?: displayAeLayers.firstOrNull { layer ->
                val end = layer.startTimeMs + layer.effectiveLayerDurationMs
                uiState.currentPositionMs >= layer.startTimeMs && uiState.currentPositionMs < end
            }
            ?: displayAeLayers.firstOrNull { it.isActiveAt(uiState.currentPositionMs) }
            ?: displayAeLayers.minByOrNull { it.startTimeMs }
            ?: displayAeLayers.firstOrNull()
    }
    val isLayerSelected = displayAeLayers.isNotEmpty() &&
        sheetState !in setOf(AeBottomSheetState.IDLE, AeBottomSheetState.ADD_SHEET)
    val isAeEffectsOpen = sheetState == AeBottomSheetState.LAYER_EFFECTS
    val isInEditOrAddSheet = sheetState != AeBottomSheetState.IDLE || isLayerSelected
    val returnToEditAndAddMode: () -> Unit = {
        aeOpenBrowserSeq = 0
        aeBrowserInitialCategory = null
        callbacks.onSelectAeLayer(null)
        sheetState = AeBottomSheetState.IDLE
    }

    LaunchedEffect(effectiveAeLayers.size) {
        if (effectiveAeLayers.isEmpty() && isLayerSelected) {
            sheetState = AeBottomSheetState.IDLE
        }
    }

    LaunchedEffect(sheetState) {
        isAddEffectArmed = false
        isColorizeArmed = false
    }

    LaunchedEffect(isAeEffectsOpen) {
        callbacks.onAfterEffectsOverlayChanged(isAeEffectsOpen)
    }

    DisposableEffect(Unit) {
        onDispose { callbacks.onAfterEffectsOverlayChanged(false) }
    }

    BackHandler(
        enabled = (isCanvasScrubbing || isTimelineScrubbing || isDraggingLayerBlock || uiState.isPlaying || sheetState != AeBottomSheetState.IDLE) &&
            !uiState.isLayoutModalVisible &&
            !uiState.isInlineEffectMode
    ) {
        when {
            isCanvasScrubbing || isTimelineScrubbing || isDraggingLayerBlock -> {
                // Absorb accidental edge-back while user is actively scrubbing canvas/timeline or dragging a layer
            }
            uiState.isPlaying -> callbacks.onPausePlayback()
            sheetState == AeBottomSheetState.LAYER_EFFECTS ||
                sheetState == AeBottomSheetState.TRANSFORM_PANEL ||
                sheetState == AeBottomSheetState.BLENDING_PANEL ||
                sheetState == AeBottomSheetState.BORDER_SHADOW_PANEL ||
                sheetState == AeBottomSheetState.TIMING_SPEED_PANEL ||
                sheetState == AeBottomSheetState.ANIMATE_PANEL -> sheetState = AeBottomSheetState.LAYER_PROPERTIES
            sheetState == AeBottomSheetState.LAYER_PROPERTIES -> sheetState = AeBottomSheetState.IDLE
            sheetState == AeBottomSheetState.ADD_SHEET -> sheetState = AeBottomSheetState.IDLE
        }
    }

    var rootPosInRoot by remember { mutableStateOf(Offset.Zero) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(AeDeepNavyBg)
                .onGloballyPositioned { rootPosInRoot = it.boundsInRoot().topLeft }
                .pointerInput(isAddEffectArmed, isColorizeArmed) {
                    if (!isAddEffectArmed && !isColorizeArmed) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val ptInRoot = rootPosInRoot + down.position
                        if (isAddEffectArmed && addEffectTriggerBounds?.contains(ptInRoot) != true) {
                            isAddEffectArmed = false
                        }
                        if (isColorizeArmed && colorizeTriggerBounds?.contains(ptInRoot) != true) {
                            isColorizeArmed = false
                        }
                    }
                }
                .testTag("studio_layout_after_effects")
        ) {
            // 1. TOP APP BAR
            AeTopAppBar(
                projectTitle = project.title,
                isLayerSelected = isLayerSelected,
                layerTitle = selectedLayer?.displayTitle ?: "Layer",
                showMoreMenu = showMoreMenu,
                onDismissMenu = { showMoreMenu = false },
                onBackClick = {
                    when {
                        sheetState == AeBottomSheetState.LAYER_EFFECTS ||
                            sheetState == AeBottomSheetState.TRANSFORM_PANEL ||
                            sheetState == AeBottomSheetState.BLENDING_PANEL ||
                            sheetState == AeBottomSheetState.BORDER_SHADOW_PANEL ||
                            sheetState == AeBottomSheetState.TIMING_SPEED_PANEL ||
                            sheetState == AeBottomSheetState.ANIMATE_PANEL -> sheetState = AeBottomSheetState.LAYER_PROPERTIES
                        isLayerSelected -> {
                            callbacks.onSelectAeLayer(null)
                            sheetState = AeBottomSheetState.IDLE
                        }
                        sheetState == AeBottomSheetState.ADD_SHEET -> sheetState = AeBottomSheetState.IDLE
                        else -> callbacks.onCloseWorkspace()
                    }
                },
                onRenameClick = {
                    selectedLayer?.let { layer ->
                        renameTargetLayer = layer
                        renameDraftText = layer.displayTitle
                    }
                },
                onDuplicateLayer = {
                    selectedLayer?.let { callbacks.onSelectAeLayer(it.id) }
                    callbacks.onDuplicateAeLayer()
                },
                onDeleteLayer = {
                    selectedLayer?.let { callbacks.onSelectAeLayer(it.id) }
                    callbacks.onDeleteAeLayer()
                    if (effectiveAeLayers.size <= 1) {
                        sheetState = AeBottomSheetState.IDLE
                    }
                },
                onDiamondClick = {
                    val target = selectedLayer
                    if (target != null) {
                        callbacks.onSelectAeLayer(target.id)
                        aeBrowserInitialCategory = null
                        aeOpenBrowserSeq += 1
                        sheetState = AeBottomSheetState.LAYER_EFFECTS
                    } else {
                        sheetState = AeBottomSheetState.ADD_SHEET
                    }
                },
                onMoreMenuToggle = { showMoreMenu = !showMoreMenu },
                onExportClick = {
                    showMoreMenu = false
                    callbacks.onExportVideo()
                },
                onOpenAeEffectsCatalogClick = {
                    showMoreMenu = false
                    val target = selectedLayer
                    if (target != null) {
                        callbacks.onSelectAeLayer(target.id)
                        aeBrowserInitialCategory = null
                        aeOpenBrowserSeq += 1
                        sheetState = AeBottomSheetState.LAYER_EFFECTS
                    } else {
                        sheetState = AeBottomSheetState.ADD_SHEET
                    }
                },
                onOpenAnimateClick = {
                    showMoreMenu = false
                    val target = selectedLayer
                    if (target != null) {
                        callbacks.onSelectAeLayer(target.id)
                        sheetState = AeBottomSheetState.ANIMATE_PANEL
                    } else {
                        sheetState = AeBottomSheetState.ADD_SHEET
                    }
                },
                modifier = Modifier.graphicsLayer { alpha = chromeAlpha }
            )

            // 2. SPLITTABLE WORKSPACE BODY (Preview + Draggable Handle + Multi-Layer Stack Timeline + Bottom Sheet)
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val totalBodyHeightDp = maxHeight.value.coerceAtLeast(320f)
                val bottomSheetReserveDp = when (sheetState) {
                    AeBottomSheetState.IDLE -> 74f
                    AeBottomSheetState.ADD_SHEET -> 256f
                    AeBottomSheetState.LAYER_PROPERTIES -> 246f
                    AeBottomSheetState.LAYER_EFFECTS,
                    AeBottomSheetState.TRANSFORM_PANEL,
                    AeBottomSheetState.BLENDING_PANEL,
                    AeBottomSheetState.BORDER_SHADOW_PANEL,
                    AeBottomSheetState.TIMING_SPEED_PANEL,
                    AeBottomSheetState.ANIMATE_PANEL -> 260f
                }
                val baseTimelineDp = when {
                    effectiveAeLayers.isEmpty() -> 108f
                    effectiveAeLayers.size == 1 -> 136f
                    else -> 168f
                }
                val minTimelineDp = 88f
                val minPreviewDp = 100f
                val transportAndHandleDp = 60f
                val maxTimelineDp = (totalBodyHeightDp - bottomSheetReserveDp - transportAndHandleDp - minPreviewDp)
                    .coerceAtLeast(minTimelineDp)
                val targetTimelineDp = (baseTimelineDp + aeTimelineExtraHeightDp)
                    .coerceIn(minTimelineDp, maxTimelineDp)
                    .dp
                val resolvedTimelineDp by animateDpAsState(
                    targetValue = targetTimelineDp,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    label = "ae_timeline_height"
                )
                val estimatedPreviewDp = (totalBodyHeightDp - targetTimelineDp.value - bottomSheetReserveDp - transportAndHandleDp)
                val isCompactSplit = estimatedPreviewDp < 200f

                Column(modifier = Modifier.fillMaxSize()) {
                    AeCanvasViewportStage(
                        project = project,
                        layers = displayAeLayers,
                        selectedLayer = if (isLayerSelected) selectedLayer else null,
                        currentPositionMs = uiState.currentPositionMs,
                        totalDurationMs = resolvedTotalDurationMs,
                        isPlaying = uiState.isPlaying,
                        isCanvasScrubbing = isCanvasScrubbing,
                        speedMultiplier = uiState.speedMultiplier,
                        showShutterFlash = showShutterFlash,
                        isFitExpanded = isFitCanvasExpanded,
                        isCompactSplit = isCompactSplit,
                        resolutionBadge = resolutionBadge,
                        canShowLayoutSwitcher = !uiState.isInEditingOrEffectMode && !isAeEffectsOpen,
                        isInEditOrAddSheet = isInEditOrAddSheet,
                        chromeAlpha = chromeAlpha,
                        onReturnToEditAndAdd = returnToEditAndAddMode,
                        onTogglePlayback = callbacks.onTogglePlayback,
                        onPausePlayback = callbacks.onPausePlayback,
                        onBeginScrubbing = callbacks.onBeginScrubbing,
                        onEndScrubbing = callbacks.onEndScrubbing,
                        onCanvasScrubStateChange = { isCanvasScrubbing = it },
                        onSeekPositionMs = { targetMs ->
                            val seekMsCb = callbacks.onSeekPositionMs
                            if (seekMsCb != null) {
                                seekMsCb(targetMs)
                            } else {
                                val span = resolvedTotalDurationMs.coerceAtLeast(1L).toFloat()
                                callbacks.onSeekProgress((targetMs.toFloat() / span).coerceIn(0f, 1f))
                            }
                        },
                        onPreviewBoundsChanged = callbacks.onPreviewBoundsChanged,
                        onOpenLayoutModal = callbacks.onOpenLayoutModal,
                        onCycleResolution = {
                            resolutionBadge = when (resolutionBadge) {
                                "Auto" -> "1080p"
                                "1080p" -> "4K"
                                else -> "Auto"
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 90.dp)
                            .fillMaxWidth()
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = chromeAlpha }
                    ) {
                        AeTransportControlBar(
                            isPlaying = uiState.isPlaying,
                            canUndo = uiState.canUndo,
                            canRedo = uiState.canRedo,
                            isCompactSplit = isCompactSplit,
                            isInEditOrAddSheet = isInEditOrAddSheet,
                            onReturnToEditAndAdd = returnToEditAndAddMode,
                            onVerticalSplitDragDp = { deltaDp ->
                                aeTimelineExtraHeightDp = (aeTimelineExtraHeightDp - deltaDp)
                                    .coerceIn(minTimelineDp - baseTimelineDp, maxTimelineDp - baseTimelineDp)
                            },
                            onToggleCompactSplit = {
                                aeTimelineExtraHeightDp = if (isCompactSplit) {
                                    0f
                                } else {
                                    (maxTimelineDp * 0.72f - baseTimelineDp).coerceAtLeast(0f)
                                }
                            },
                            onUndo = callbacks.onUndo,
                            onRedo = callbacks.onRedo,
                            onStepBackward = callbacks.onStepBackward,
                            onTogglePlayback = callbacks.onTogglePlayback,
                            onStepForward = callbacks.onStepForward,
                            onAddKeyframeOrLayers = {
                                val target = selectedLayer
                                if (target != null) {
                                    callbacks.onSelectAeLayer(target.id)
                                    callbacks.onToggleAeTransformKeyframe(target.id)
                                    sheetState = AeBottomSheetState.ANIMATE_PANEL
                                } else {
                                    sheetState = AeBottomSheetState.ADD_SHEET
                                }
                            },
                            onToggleFitCanvas = { isFitCanvasExpanded = !isFitCanvasExpanded }
                        )

                        AeTimelineSection(
                            project = project,
                            layers = displayAeLayers,
                            selectedLayerId = if (isLayerSelected) selectedLayer?.id else null,
                            currentPositionMs = uiState.currentPositionMs,
                            totalDurationMs = resolvedTotalDurationMs,
                            timelineHeight = resolvedTimelineDp,
                            timelineZoom = aeTimelineZoom,
                            isDraggingLayerBlock = isDraggingLayerBlock,
                            onTimelineZoomBy = { factor ->
                                aeTimelineZoom = (aeTimelineZoom * factor).coerceIn(0.22f, 3.6f)
                            },
                            onResetTimelineZoom = { aeTimelineZoom = 1f },
                            onLayerDragStateChange = { dragging -> isDraggingLayerBlock = dragging },
                            onTimelineScrubStateChange = { scrubbing -> isTimelineScrubbing = scrubbing },
                            onPausePlayback = callbacks.onPausePlayback,
                            onBeginScrubbing = callbacks.onBeginScrubbing,
                            onEndScrubbing = callbacks.onEndScrubbing,
                            onSeekPositionMs = { targetMs ->
                                val seekMsCb = callbacks.onSeekPositionMs
                                if (seekMsCb != null) {
                                    seekMsCb(targetMs)
                                } else {
                                    val span = resolvedTotalDurationMs.coerceAtLeast(1L).toFloat()
                                    callbacks.onSeekProgress((targetMs.toFloat() / span).coerceIn(0f, 1f))
                                }
                            },
                            onToggleVisibility = { layerId -> callbacks.onToggleAeLayerVisibility(layerId) },
                            onToggleLock = { layerId -> callbacks.onToggleAeLayerLock(layerId) },
                            onReorderLayer = { fromIdx, toIdx ->
                                callbacks.onBeginAeLayerReorder()
                                callbacks.onReorderAeLayer(fromIdx, toIdx)
                            },
                            onLiveDragLayerTiming = { layerId, nextStartMs ->
                                dragStartOverrides[layerId] = nextStartMs.coerceAtLeast(0L)
                            },
                            onCommitMoveLayerTiming = { layerId, initialStartMs, finalStartMs ->
                                dragStartOverrides.remove(layerId)
                                callbacks.onCommitAeLayerTiming(layerId, initialStartMs, finalStartMs)
                            },
                            onSelectLayer = { _, layer ->
                                callbacks.onSelectAeLayer(layer.id)
                                sheetState = AeBottomSheetState.LAYER_PROPERTIES
                            },
                            onClearLayerSelection = {
                                if (isInEditOrAddSheet) {
                                    returnToEditAndAddMode()
                                }
                            }
                        )

                        AnimatedContent(
                            targetState = sheetState,
                            transitionSpec = {
                                (slideInVertically(
                                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                                    initialOffsetY = { it / 3 }
                                ) + fadeIn(tween(220, easing = FastOutSlowInEasing))) togetherWith
                                    (slideOutVertically(
                                        animationSpec = tween(170, easing = FastOutSlowInEasing),
                                        targetOffsetY = { it / 3 }
                                    ) + fadeOut(tween(160)))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(animationSpec = tween(220, easing = FastOutSlowInEasing)),
                            label = "ae_bottom_drawer_transition"
                        ) { activeSheet ->
                        when (activeSheet) {
                            AeBottomSheetState.IDLE -> {
                                AeIdleBottomToolbar(
                                    isColorizeArmed = isColorizeArmed,
                                    onColorizeArmChange = { isColorizeArmed = it },
                                    onColorizeBoundsChanged = { colorizeTriggerBounds = it },
                                    onOpenAddSheet = { sheetState = AeBottomSheetState.ADD_SHEET },
                                    onOpenAeBrowser = { categoryId ->
                                        val target = selectedLayer
                                        if (target != null) {
                                            callbacks.onSelectAeLayer(target.id)
                                            aeBrowserInitialCategory = categoryId
                                            aeOpenBrowserSeq += 1
                                            sheetState = AeBottomSheetState.LAYER_EFFECTS
                                        } else {
                                            sheetState = AeBottomSheetState.ADD_SHEET
                                        }
                                    },
                                    onImportVideo = callbacks.onImportVideo,
                                    onImportPhoto = callbacks.onImportPhoto,
                                    onExportVideo = callbacks.onExportVideo,
                                    onSplitAeLayer = {
                                        val target = selectedLayer
                                        if (target != null) {
                                            callbacks.onSplitAeLayer(target.id)
                                        } else {
                                            sheetState = AeBottomSheetState.ADD_SHEET
                                        }
                                    }
                                )
                            }

                            AeBottomSheetState.ADD_SHEET -> {
                                AeAddMediaBottomSheet(
                                    selectedTab = selectedAddTab,
                                    onSelectTab = { selectedAddTab = it },
                                    onCloseAddSheet = returnToEditAndAddMode,
                                    onOpenGallery = callbacks.onImportGalleryMedia,
                                    onImportVideo = callbacks.onImportVideo,
                                    onImportPhoto = callbacks.onImportPhoto,
                                    onAddLayerKind = { kind ->
                                        callbacks.onAddAeLayerKind(kind)
                                        sheetState = AeBottomSheetState.LAYER_PROPERTIES
                                    }
                                )
                            }

                            AeBottomSheetState.LAYER_PROPERTIES -> {
                                AeLayerPropertiesGridPanel(
                                    layerTitle = selectedLayer?.displayTitle ?: "Layer",
                                    onBackToIdle = returnToEditAndAddMode,
                                    onOpenTimingSpeed = { sheetState = AeBottomSheetState.TIMING_SPEED_PANEL },
                                    onTrimStart = { selectedLayer?.let { callbacks.onTrimAeLayerStart(it.id) } },
                                    onSplitLayer = { selectedLayer?.let { callbacks.onSplitAeLayer(it.id) } },
                                    onTrimEnd = { selectedLayer?.let { callbacks.onTrimAeLayerEnd(it.id) } },
                                    onOpenBorderShadow = { sheetState = AeBottomSheetState.BORDER_SHADOW_PANEL },
                                    onOpenBlending = { sheetState = AeBottomSheetState.BLENDING_PANEL },
                                    onOpenAnimate = { sheetState = AeBottomSheetState.ANIMATE_PANEL },
                                    onOpenTransform = { sheetState = AeBottomSheetState.TRANSFORM_PANEL },
                                    onOpenEffectsSheet = {
                                        aeBrowserInitialCategory = null
                                        aeOpenBrowserSeq = 0
                                        sheetState = AeBottomSheetState.LAYER_EFFECTS
                                    }
                                )
                            }

                            AeBottomSheetState.LAYER_EFFECTS -> {
                                AeLayerEffectsPanel(
                                    selectedLayer = selectedLayer,
                                    currentPositionMs = uiState.currentPositionMs,
                                    openBrowserSeq = aeOpenBrowserSeq,
                                    initialBrowserCategoryId = aeBrowserInitialCategory,
                                    isAddEffectArmed = isAddEffectArmed,
                                    onAddEffectArmChange = { isAddEffectArmed = it },
                                    onAddEffectBoundsChanged = { addEffectTriggerBounds = it },
                                    onBackClick = {
                                        aeOpenBrowserSeq = 0
                                        aeBrowserInitialCategory = null
                                        sheetState = AeBottomSheetState.LAYER_PROPERTIES
                                    },
                                    onExitToIdle = returnToEditAndAddMode,
                                    onPresetsClick = {
                                        aeBrowserInitialCategory = null
                                        aeOpenBrowserSeq += 1
                                    },
                                    onAddAeEffect = { effectId ->
                                        val targetId = selectedLayer?.id ?: return@AeLayerEffectsPanel null
                                        callbacks.onAddAeLayerEffect(targetId, effectId)
                                    },
                                    onUpdatePass = { updatedPass, recordUndo ->
                                        val targetId = selectedLayer?.id ?: return@AeLayerEffectsPanel
                                        callbacks.onUpdateAeLayerEffectPass(targetId, updatedPass, recordUndo)
                                    },
                                    onRemovePass = { passId ->
                                        val targetId = selectedLayer?.id ?: return@AeLayerEffectsPanel
                                        callbacks.onRemoveAeLayerEffectPass(targetId, passId)
                                    },
                                    onTogglePassKeyframe = { passId, propKey, curVal ->
                                        val targetId = selectedLayer?.id ?: return@AeLayerEffectsPanel
                                        callbacks.onToggleAeLayerEffectKeyframe(targetId, passId, propKey, curVal)
                                    }
                                )
                            }

                            AeBottomSheetState.TRANSFORM_PANEL -> {
                                val target = selectedLayer
                                if (target == null) {
                                    sheetState = AeBottomSheetState.IDLE
                                } else {
                                    AeTransformInlinePanel(
                                        layer = target,
                                        currentPositionMs = uiState.currentPositionMs,
                                        onBack = { sheetState = AeBottomSheetState.LAYER_PROPERTIES },
                                        onExitToIdle = returnToEditAndAddMode,
                                        onUpdateLayer = { updated ->
                                            callbacks.onUpdateAeLayerProps(target.id, updated, false)
                                        },
                                        onToggleKeyframe = { callbacks.onToggleAeTransformKeyframe(target.id) }
                                    )
                                }
                            }

                            AeBottomSheetState.BLENDING_PANEL -> {
                                val target = selectedLayer
                                if (target == null) {
                                    sheetState = AeBottomSheetState.IDLE
                                } else {
                                    AeBlendingInlinePanel(
                                        layer = target,
                                        currentPositionMs = uiState.currentPositionMs,
                                        onBack = { sheetState = AeBottomSheetState.LAYER_PROPERTIES },
                                        onExitToIdle = returnToEditAndAddMode,
                                        onUpdateLayer = { updated ->
                                            callbacks.onUpdateAeLayerProps(target.id, updated, false)
                                        },
                                        onToggleKeyframe = { callbacks.onToggleAeTransformKeyframe(target.id) }
                                    )
                                }
                            }

                            AeBottomSheetState.BORDER_SHADOW_PANEL -> {
                                val target = selectedLayer
                                if (target == null) {
                                    sheetState = AeBottomSheetState.IDLE
                                } else {
                                    AeBorderShadowInlinePanel(
                                        layer = target,
                                        onBack = { sheetState = AeBottomSheetState.LAYER_PROPERTIES },
                                        onExitToIdle = returnToEditAndAddMode,
                                        onUpdateLayer = { updated ->
                                            callbacks.onUpdateAeLayerProps(target.id, updated, false)
                                        }
                                    )
                                }
                            }

                            AeBottomSheetState.TIMING_SPEED_PANEL -> {
                                val target = selectedLayer
                                if (target == null) {
                                    sheetState = AeBottomSheetState.IDLE
                                } else {
                                    AeTimingSpeedInlinePanel(
                                        layer = target,
                                        currentPositionMs = uiState.currentPositionMs,
                                        onBack = { sheetState = AeBottomSheetState.LAYER_PROPERTIES },
                                        onExitToIdle = returnToEditAndAddMode,
                                        onUpdateLayer = { updated ->
                                            callbacks.onUpdateAeLayerProps(target.id, updated, false)
                                        },
                                        onTrimStartToPlayhead = { callbacks.onTrimAeLayerStart(target.id) },
                                        onTrimEndToPlayhead = { callbacks.onTrimAeLayerEnd(target.id) },
                                        onSplitAtPlayhead = { callbacks.onSplitAeLayer(target.id) }
                                    )
                                }
                            }

                            AeBottomSheetState.ANIMATE_PANEL -> {
                                val target = selectedLayer
                                if (target == null) {
                                    sheetState = AeBottomSheetState.IDLE
                                } else {
                                    AeAnimateInlinePanel(
                                        layer = target,
                                        currentPositionMs = uiState.currentPositionMs,
                                        onBack = { sheetState = AeBottomSheetState.LAYER_PROPERTIES },
                                        onExitToIdle = returnToEditAndAddMode,
                                        onUpdateLayer = { updated ->
                                            callbacks.onUpdateAeLayerProps(target.id, updated, true)
                                        },
                                        onToggleKeyframe = { callbacks.onToggleAeTransformKeyframe(target.id) },
                                        onSeekToMs = { targetMs ->
                                            val seekMsCb = callbacks.onSeekPositionMs
                                            if (seekMsCb != null) {
                                                seekMsCb(targetMs)
                                            } else {
                                                val span = resolvedTotalDurationMs.coerceAtLeast(1L).toFloat()
                                                callbacks.onSeekProgress((targetMs.toFloat() / span).coerceIn(0f, 1f))
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        }
                    }
                }
            }
        }

        val pendingRename = renameTargetLayer
        if (pendingRename != null) {
            AlertDialog(
                onDismissRequest = { renameTargetLayer = null },
                containerColor = AeSlateSurface,
                titleContentColor = Color.White,
                textContentColor = Color.White,
                title = { Text("Rename Layer", fontFamily = InterFontFamily, fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = renameDraftText,
                        onValueChange = { renameDraftText = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ae_rename_layer_input")
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val trimmed = renameDraftText.trim()
                            if (trimmed.isNotEmpty()) {
                                callbacks.onRenameAeLayer(pendingRename.id, trimmed)
                            }
                            renameTargetLayer = null
                        },
                        modifier = Modifier.testTag("ae_rename_layer_confirm")
                    ) {
                        Text("Save", color = AeNeonGreen, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { renameTargetLayer = null }) {
                        Text("Cancel", color = AeMutedGray)
                    }
                },
                modifier = Modifier.testTag("ae_rename_layer_dialog")
            )
        }
    }
}

@Composable
private fun AeTopAppBar(
    projectTitle: String,
    isLayerSelected: Boolean,
    layerTitle: String,
    showMoreMenu: Boolean,
    onDismissMenu: () -> Unit,
    onBackClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDuplicateLayer: () -> Unit,
    onDeleteLayer: () -> Unit,
    onDiamondClick: () -> Unit,
    onMoreMenuToggle: () -> Unit,
    onExportClick: () -> Unit,
    onOpenAeEffectsCatalogClick: () -> Unit,
    onOpenAnimateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AeDeepNavyBg)
            .statusBarsPadding()
            .height(46.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onBackClick)
                .testTag(if (isLayerSelected) "ae_layer_back_button" else "workspace_close_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.cd_close_workspace),
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        if (isLayerSelected) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onRenameClick)
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .testTag("ae_edit_layer_name_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = layerTitle,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Edit Layer Name",
                    tint = AeMutedGray,
                    modifier = Modifier.size(15.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDuplicateLayer)
                    .testTag("ae_duplicate_layer_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Duplicate Layer",
                    tint = Color(0xFFD1D5DB),
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDeleteLayer)
                    .testTag("ae_delete_layer_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete Layer",
                    tint = AeRedPlayhead,
                    modifier = Modifier.size(22.dp)
                )
            }
        } else {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = projectTitle,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(AeRedPlayhead)
                )
            }
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onDiamondClick),
            contentAlignment = Alignment.Center
        ) {
            AeDiamondGlyph(modifier = Modifier.size(22.dp))
        }

        Box {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClick = onMoreMenuToggle),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(AeTealMenuBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Composition Options",
                        tint = Color(0xFF4DD0E1),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = onDismissMenu,
                modifier = Modifier.background(AeSlateSurface)
            ) {
                DropdownMenuItem(
                    text = { Text("Export Video (MP4)", color = Color.White, fontFamily = InterFontFamily, fontSize = 13.sp) },
                    onClick = onExportClick
                )
                DropdownMenuItem(
                    text = { Text("AE Effects Browser", color = AeNeonGreen, fontFamily = InterFontFamily, fontSize = 13.sp) },
                    onClick = onOpenAeEffectsCatalogClick
                )
                DropdownMenuItem(
                    text = { Text("Animate & Keyframes", color = Color(0xFF4DD0E1), fontFamily = InterFontFamily, fontSize = 13.sp) },
                    onClick = onOpenAnimateClick
                )
            }
        }
    }
}

@Composable
private fun AeCanvasViewportStage(
    project: VideoProject,
    layers: List<EditorLayer>,
    selectedLayer: EditorLayer?,
    currentPositionMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    isCanvasScrubbing: Boolean,
    speedMultiplier: Float,
    showShutterFlash: Boolean,
    isFitExpanded: Boolean,
    isCompactSplit: Boolean,
    resolutionBadge: String,
    canShowLayoutSwitcher: Boolean,
    isInEditOrAddSheet: Boolean,
    chromeAlpha: Float,
    onReturnToEditAndAdd: () -> Unit,
    onTogglePlayback: () -> Unit,
    onPausePlayback: () -> Unit,
    onBeginScrubbing: () -> Unit = {},
    onEndScrubbing: () -> Unit = {},
    onCanvasScrubStateChange: (Boolean) -> Unit,
    onSeekPositionMs: (Long) -> Unit,
    onPreviewBoundsChanged: (Rect) -> Unit,
    onOpenLayoutModal: () -> Unit,
    onCycleResolution: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewportAspect = when (project.aspectRatio) {
        AspectRatioMode.PORTRAIT -> 9f / 16f
        AspectRatioMode.PORTRAIT_3_4 -> 3f / 4f
        AspectRatioMode.PORTRAIT_4_5 -> 4f / 5f
        AspectRatioMode.SQUARE -> 1f
        AspectRatioMode.LANDSCAPE_4_3 -> 4f / 3f
        AspectRatioMode.LANDSCAPE -> 16f / 9f
    }

    // Bottom-to-top Z-index rendering order; keep all visible layers keyed by layer.id so
    // sliding layers across playhead 00:00:00 does NOT destroy & recreate hardware ExoPlayer/EGL surfaces.
    val visibleLayersBottomToTop = remember(layers) {
        layers.filter { it.isVisible }.asReversed()
    }
    val videoLayersBottomToTop = remember(visibleLayersBottomToTop) {
        visibleLayersBottomToTop.filter {
            it.layerKind == AeLayerKind.MEDIA && it.mediaClip is MediaClip.Video
        }
    }
    val selectedVideoLayerId = selectedLayer?.takeIf {
        it.isVisible && it.layerKind == AeLayerKind.MEDIA && it.mediaClip is MediaClip.Video
    }?.id
    // Avoid re-evaluating/thrashing liveVideoLayerId every frame when there is only 1 video or selected video is stable.
    val liveVideoLayerId = if (selectedVideoLayerId != null) {
        selectedVideoLayerId
    } else if (videoLayersBottomToTop.size <= 1) {
        videoLayersBottomToTop.firstOrNull()?.id
    } else {
        videoLayersBottomToTop.lastOrNull { it.isActiveAt(currentPositionMs) }?.id
            ?: videoLayersBottomToTop.lastOrNull()?.id
    }

    val latestCurrentPosMs by rememberUpdatedState(currentPositionMs)
    val latestTotalDurMs by rememberUpdatedState(totalDurationMs.coerceAtLeast(500L))
    val latestInEditOrAddSheet by rememberUpdatedState(isInEditOrAddSheet)
    val latestOnReturnToEditAndAdd by rememberUpdatedState(onReturnToEditAndAdd)
    val latestOnTogglePlayback by rememberUpdatedState(onTogglePlayback)
    var canvasWidthPx by remember { mutableFloatStateOf(360f) }

    BoxWithConstraints(
        modifier = modifier
            .background(AeCanvasBackdrop)
            .clipToBounds()
            .systemGestureExclusion()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (latestInEditOrAddSheet) {
                            latestOnReturnToEditAndAdd()
                        }
                    }
                )
            }
            .padding(horizontal = if (isFitExpanded || isCompactSplit) 0.dp else 4.dp, vertical = if (isFitExpanded || isCompactSplit) 0.dp else 4.dp)
            .testTag("ae_canvas_backdrop")
    ) {
        val (stageW, stageH) = if (isFitExpanded) {
            maxWidth.coerceAtLeast(64.dp) to maxHeight.coerceAtLeast(64.dp)
        } else {
            val hFromW = maxWidth / viewportAspect
            if (hFromW <= maxHeight) {
                maxWidth.coerceAtLeast(64.dp) to hFromW.coerceAtLeast(64.dp)
            } else {
                (maxHeight * viewportAspect).coerceAtLeast(64.dp) to maxHeight.coerceAtLeast(64.dp)
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = stageW, height = stageH)
                .onSizeChanged { size ->
                    if (size.width > 0) canvasWidthPx = size.width.toFloat()
                }
                .onGloballyPositioned { onPreviewBoundsChanged(it.boundsInRoot()) }
                .background(Color.Black)
                .systemGestureExclusion()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (latestInEditOrAddSheet) {
                                latestOnReturnToEditAndAdd()
                            } else {
                                latestOnTogglePlayback()
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    var dragStartPosMs = 0L
                    var accumDragPx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            onCanvasScrubStateChange(true)
                            onBeginScrubbing()
                            onPausePlayback()
                            dragStartPosMs = latestCurrentPosMs
                            accumDragPx = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            accumDragPx += dragAmount
                            val widthRef = canvasWidthPx.coerceAtLeast(180f)
                            val spanMs = minOf(latestTotalDurMs, 6_000L).coerceAtLeast(1_200L)
                            val deltaMs = ((accumDragPx / widthRef) * spanMs).roundToLong()
                            val nextPosMs = (dragStartPosMs + deltaMs).coerceIn(0L, latestTotalDurMs)
                            onSeekPositionMs(nextPosMs)
                        },
                        onDragEnd = {
                            onCanvasScrubStateChange(false)
                            onEndScrubbing()
                        },
                        onDragCancel = {
                            onCanvasScrubStateChange(false)
                            onEndScrubbing()
                        }
                    )
                }
                .testTag("workspace_preview_canvas")
        ) {
            val isHardwareGlSupported = !LocalInspectionMode.current &&
                !Build.FINGERPRINT.contains("robolectric", ignoreCase = true)

            if (isHardwareGlSupported && visibleLayersBottomToTop.isNotEmpty()) {
                AeCompositeGlStage(
                    layers = layers,
                    currentPositionMs = currentPositionMs,
                    isPlaying = isPlaying,
                    clipVolume = project.audioConfig.clipVolume,
                    speedMultiplier = speedMultiplier,
                    modifier = Modifier.fillMaxSize()
                )
                // Optional visual Null layer pivot overlay in preview (non-exported)
                visibleLayersBottomToTop
                    .filter { it.layerKind == AeLayerKind.NULL && it.isActiveAt(currentPositionMs) }
                    .forEach { layer ->
                        val evalTransform = com.example.engine.ae.AeEffectEngine.composeAeLayerTransformAt(
                            layer = layer,
                            timelinePositionMs = currentPositionMs
                        )
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = evalTransform.opacity
                                    translationX = evalTransform.offsetX
                                    translationY = evalTransform.offsetY
                                    scaleX = evalTransform.scaleXSign * evalTransform.scale
                                    scaleY = evalTransform.scaleYSign * evalTransform.scale
                                    rotationZ = evalTransform.rotation
                                }
                        ) {
                            val r = 16.dp.toPx()
                            drawCircle(
                                color = AePivotYellow,
                                radius = r,
                                style = Stroke(
                                    width = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                                )
                            )
                            drawLine(AePivotYellow, Offset(center.x - r, center.y), Offset(center.x + r, center.y), 1.5f)
                            drawLine(AePivotYellow, Offset(center.x, center.y - r), Offset(center.x, center.y + r), 1.5f)
                        }
                    }
            } else {
                visibleLayersBottomToTop.forEach { layer ->
                    key(layer.id) {
                        val isSingleZeroLayerAtEnd = layers.size == 1 &&
                            layer.startTimeMs == 0L &&
                            currentPositionMs >= layer.effectiveLayerDurationMs
                        val isCurrentlyActive = layer.isActiveAt(currentPositionMs) || isSingleZeroLayerAtEnd
                        val isHeavyRenderSurface = layer.layerKind == AeLayerKind.MEDIA &&
                            (layer.mediaClip is MediaClip.Video || layer.effectPasses.any { it.isEnabled })
                        if (isCurrentlyActive || isHeavyRenderSurface) {

                        val localPosMs = (currentPositionMs - layer.startTimeMs).coerceIn(0L, layer.effectiveLayerDurationMs)
                        val evalTransform = com.example.engine.ae.AeEffectEngine.composeAeLayerTransformAt(
                            layer = layer,
                            timelinePositionMs = currentPositionMs
                        )
                        val layerModifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = if (isCurrentlyActive) evalTransform.opacity else 0f
                                translationX = evalTransform.offsetX
                                translationY = evalTransform.offsetY
                                scaleX = evalTransform.scaleXSign * evalTransform.scale
                                scaleY = evalTransform.scaleYSign * evalTransform.scale
                                rotationZ = evalTransform.rotation
                                if (layer.shadowOpacity > 0.01f && layer.shadowRadiusDp > 0.5f) {
                                    shadowElevation = (layer.shadowRadiusDp * layer.shadowOpacity).dp.toPx()
                                }
                            }
                            .then(
                                if (layer.borderWidthDp > 0.2f) {
                                    Modifier.border(
                                        width = layer.borderWidthDp.dp,
                                        color = Color(layer.borderColorArgb.toInt())
                                    )
                                } else Modifier
                            )

                        when (layer.layerKind) {
                            AeLayerKind.MEDIA -> {
                                val layerScene = remember(
                                    layer.id,
                                    layer.mediaClip,
                                    layer.durationMs,
                                    layer.effectPasses,
                                    layer.displayTitle
                                ) {
                                    TimelineScene(
                                        id = "ae_render_${layer.id}",
                                        title = layer.displayTitle,
                                        durationMs = layer.effectiveLayerDurationMs,
                                        clipId = layer.mediaClip?.id,
                                        mediaUriString = layer.mediaClip?.uriString,
                                        isVideo = layer.mediaClip is MediaClip.Video,
                                        clip = layer.mediaClip,
                                        effectPasses = layer.effectPasses
                                    )
                                }
                                if (layer.mediaClip != null) {
                                    SceneRenderStage(
                                        scene = layerScene,
                                        localPositionMs = localPosMs,
                                        isPlaying = isPlaying && isCurrentlyActive,
                                        clipVolume = (project.audioConfig.clipVolume * layer.audioVolume).coerceIn(0f, 1.5f),
                                        speedMultiplier = (speedMultiplier * layer.speedMultiplier).coerceIn(0.25f, 4f),
                                        useLiveVideoPlayer = layer.id == liveVideoLayerId,
                                        forceGlStage = layer.effectPasses.any { it.isEnabled },
                                        contentDescription = layer.displayTitle,
                                        onVideoPositionChanged = {},
                                        onScenePlaybackEnded = {},
                                        modifier = layerModifier
                                    )
                                }
                            }

                        AeLayerKind.TEXT -> {
                            Box(modifier = layerModifier, contentAlignment = Alignment.Center) {
                                Text(
                                    text = layer.effectiveText.ifBlank { "Sample Text" },
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = layer.textSpec.fontSizeSp.coerceIn(16f, 44f).sp,
                                    color = Color(layer.textSpec.textColorArgb.toInt()),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        AeLayerKind.SHAPE -> {
                            Box(modifier = layerModifier, contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .size(92.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(AeNeonGreen.copy(alpha = 0.25f))
                                        .border(2.5.dp, AeNeonGreen, RoundedCornerShape(18.dp))
                                )
                            }
                        }

                        AeLayerKind.ADJUSTMENT -> {
                            Box(
                                modifier = layerModifier.background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(0x3300E676),
                                            Color(0x227C5CFF)
                                        )
                                    )
                                )
                            )
                        }

                        AeLayerKind.ELEMENT, AeLayerKind.DRAW -> {
                            Canvas(modifier = layerModifier) {
                                drawCircle(
                                    color = Color(0xFF4DD0E1).copy(alpha = 0.7f),
                                    radius = size.minDimension * 0.22f,
                                    style = Stroke(width = 3.dp.toPx())
                                )
                            }
                        }

                        AeLayerKind.NULL -> {
                            Canvas(modifier = layerModifier) {
                                val r = 16.dp.toPx()
                                drawCircle(
                                    color = AePivotYellow,
                                    radius = r,
                                    style = Stroke(
                                        width = 1.5.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                                    )
                                )
                                drawLine(AePivotYellow, Offset(center.x - r, center.y), Offset(center.x + r, center.y), 1.5f)
                                drawLine(AePivotYellow, Offset(center.x, center.y - r), Offset(center.x, center.y + r), 1.5f)
                            }
                        }

                        AeLayerKind.AUDIO -> Unit
                        }
                        }
                    }
                }
            }

            // Corner brackets + Selection Bounding Box
            Canvas(modifier = Modifier.fillMaxSize()) {
                val arm = 8.dp.toPx()
                val stroke = 1.2.dp.toPx()
                val inset = 1.dp.toPx()
                val c = Color.White.copy(alpha = 0.85f)
                val w = size.width
                val h = size.height
                drawLine(c, Offset(inset, inset), Offset(inset + arm, inset), strokeWidth = stroke)
                drawLine(c, Offset(inset, inset), Offset(inset, inset + arm), strokeWidth = stroke)
                drawLine(c, Offset(w - inset - arm, inset), Offset(w - inset, inset), strokeWidth = stroke)
                drawLine(c, Offset(w - inset, inset), Offset(w - inset, inset + arm), strokeWidth = stroke)
                drawLine(c, Offset(inset, h - inset), Offset(inset + arm, h - inset), strokeWidth = stroke)
                drawLine(c, Offset(inset, h - inset - arm), Offset(inset, h - inset), strokeWidth = stroke)
                drawLine(c, Offset(w - inset - arm, h - inset), Offset(w - inset, h - inset), strokeWidth = stroke)
                drawLine(c, Offset(w - inset, h - inset - arm), Offset(w - inset, h - inset), strokeWidth = stroke)

                if (selectedLayer != null) {
                    val left = w * 0.08f
                    val top = h * 0.08f
                    val right = w * 0.92f
                    val bottom = h * 0.92f
                    val boxW = right - left
                    val boxH = bottom - top
                    val midX = (left + right) * 0.5f
                    val midY = (top + bottom) * 0.5f

                    drawRect(
                        color = AeSelectionBlue,
                        topLeft = Offset(left, top),
                        size = Size(boxW, boxH),
                        style = Stroke(width = 1.3.dp.toPx())
                    )

                    val handleR = 3.5.dp.toPx()
                    listOf(
                        Offset(left, top),
                        Offset(midX, top),
                        Offset(right, top),
                        Offset(right, midY),
                        Offset(right, bottom),
                        Offset(midX, bottom),
                        Offset(left, bottom),
                        Offset(left, midY)
                    ).forEach { pt ->
                        drawCircle(Color.White, radius = handleR, center = pt)
                        drawCircle(AeSelectionBlue, radius = handleR, center = pt, style = Stroke(width = 1.2.dp.toPx()))
                    }

                    drawCircle(AeNeonGreen, radius = 3.2.dp.toPx(), center = Offset(right + 8.dp.toPx(), midY))
                    val cross = 4.dp.toPx()
                    drawCircle(AePivotYellow, radius = 2.5.dp.toPx(), center = Offset(midX, midY), style = Stroke(width = 1.2.dp.toPx()))
                    drawLine(AePivotYellow, Offset(midX - cross, midY), Offset(midX + cross, midY), strokeWidth = 1.2.dp.toPx())
                    drawLine(AePivotYellow, Offset(midX, midY - cross), Offset(midX, midY + cross), strokeWidth = 1.2.dp.toPx())
                }
            }

            AnimatedVisibility(
                visible = isCanvasScrubbing,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            ) {
                Surface(
                    color = Color(0xCC0B0D17),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AeNeonGreen.copy(alpha = 0.65f))
                ) {
                    Text(
                        text = formatAeTimecode(currentPositionMs, project.frameRate.coerceIn(24, 60)),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = AeNeonGreen,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = showShutterFlash,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.8f)))
            }
        }

        AnimatedVisibility(
            visible = isInEditOrAddSheet,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 2.dp, start = 2.dp)
                .graphicsLayer { alpha = chromeAlpha }
        ) {
            Surface(
                color = Color(0xD9151A2B),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, AeNeonGreen.copy(alpha = 0.65f)),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onReturnToEditAndAdd)
                    .testTag("ae_exit_edit_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to Edit & Add",
                        tint = AeNeonGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Edit & Add",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = Color.White
                    )
                }
            }
        }

        if (canShowLayoutSwitcher) {
            LayoutSwitcherPillButton(
                activeLayout = StudioLayoutMode.AFTER_EFFECTS,
                onClick = onOpenLayoutModal,
                testTag = "workspace_layout_switcher_button",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .graphicsLayer { alpha = chromeAlpha }
            )
        }

        Surface(
            color = Color(0xFF1A1D2B),
            shape = RoundedCornerShape(7.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 4.dp, end = 2.dp)
                .clip(RoundedCornerShape(7.dp))
                .clickable(onClick = onCycleResolution)
                .graphicsLayer { alpha = chromeAlpha }
        ) {
            Text(
                text = resolutionBadge,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = Color(0xFFD1D5DB),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun AeTransportControlBar(
    isPlaying: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    isCompactSplit: Boolean,
    isInEditOrAddSheet: Boolean,
    onReturnToEditAndAdd: () -> Unit,
    onVerticalSplitDragDp: (Float) -> Unit,
    onToggleCompactSplit: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onStepBackward: () -> Unit,
    onTogglePlayback: () -> Unit,
    onStepForward: () -> Unit,
    onAddKeyframeOrLayers: () -> Unit,
    onToggleFitCanvas: () -> Unit
) {
    val density = LocalDensity.current
    var isDraggingSplit by remember { mutableStateOf(false) }
    val latestInEditOrAdd by rememberUpdatedState(isInEditOrAddSheet)
    val latestReturnToIdle by rememberUpdatedState(onReturnToEditAndAdd)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AeSlateSurface)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (latestInEditOrAdd) {
                            latestReturnToIdle()
                        }
                    }
                )
            }
    ) {
        // Draggable vertical splitter bar between Preview Canvas & Transport Controls
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .pointerInput(density) {
                    detectVerticalDragGestures(
                        onDragStart = { isDraggingSplit = true },
                        onDragEnd = { isDraggingSplit = false },
                        onDragCancel = { isDraggingSplit = false },
                        onVerticalDrag = { change, dragAmountPx ->
                            change.consume()
                            val deltaDp = with(density) { dragAmountPx.toDp().value }
                            onVerticalSplitDragDp(deltaDp)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { onToggleCompactSplit() })
                }
                .testTag("ae_split_divider_handle"),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(if (isDraggingSplit || isCompactSplit) 48.dp else 38.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(
                        if (isDraggingSplit || isCompactSplit) AeNeonGreen else Color(0xFF3A415E)
                    )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AeTransportIconButton(
                icon = Icons.AutoMirrored.Rounded.Undo,
                contentDescription = "Undo",
                tint = if (canUndo) Color.White else Color(0xFF4B5268),
                onClick = onUndo
            )
            AeTransportIconButton(
                icon = Icons.AutoMirrored.Rounded.Redo,
                contentDescription = "Redo",
                tint = if (canRedo) Color.White else Color(0xFF4B5268),
                onClick = onRedo
            )
            AeTransportIconButton(
                icon = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous Frame",
                tint = Color(0xFFE5E7EB),
                onClick = onStepBackward
            )
            AeTransportIconButton(
                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(R.string.cd_play_pause),
                tint = Color.White,
                iconSize = 28.dp,
                onClick = onTogglePlayback,
                testTag = "ae_play_pause_button"
            )
            AeTransportIconButton(
                icon = Icons.Rounded.SkipNext,
                contentDescription = "Next Frame",
                tint = Color(0xFFE5E7EB),
                onClick = onStepForward
            )
            AeTransportIconButton(
                icon = Icons.Outlined.LibraryAdd,
                contentDescription = "Layers & Keyframes",
                tint = Color(0xFFD1D5DB),
                onClick = onAddKeyframeOrLayers
            )
            AeTransportIconButton(
                icon = Icons.Rounded.Fullscreen,
                contentDescription = "Fit Canvas",
                tint = Color(0xFFD1D5DB),
                onClick = onToggleFitCanvas
            )
        }
    }
}

@Composable
private fun AeTransportIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    iconSize: Dp = 21.dp,
    testTag: String? = null
) {
    val mod = Modifier
        .size(48.dp)
        .clip(CircleShape)
        .clickable(onClick = onClick)
        .let { if (testTag != null) it.testTag(testTag) else it }

    Box(modifier = mod, contentAlignment = Alignment.Center) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun AeTimelineSection(
    project: VideoProject,
    layers: List<EditorLayer>,
    selectedLayerId: String?,
    currentPositionMs: Long,
    totalDurationMs: Long,
    timelineHeight: Dp,
    timelineZoom: Float,
    isDraggingLayerBlock: Boolean,
    onTimelineZoomBy: (Float) -> Unit,
    onResetTimelineZoom: () -> Unit,
    onLayerDragStateChange: (Boolean) -> Unit,
    onTimelineScrubStateChange: (Boolean) -> Unit,
    onPausePlayback: () -> Unit,
    onBeginScrubbing: () -> Unit = {},
    onEndScrubbing: () -> Unit = {},
    onSeekPositionMs: (Long) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onToggleLock: (String) -> Unit,
    onReorderLayer: (fromIndex: Int, toIndex: Int) -> Unit,
    onLiveDragLayerTiming: (layerId: String, nextStartMs: Long) -> Unit,
    onCommitMoveLayerTiming: (layerId: String, initialStartMs: Long, finalStartMs: Long) -> Unit,
    onSelectLayer: (Int, EditorLayer) -> Unit,
    onClearLayerSelection: () -> Unit
) {
    val fps = project.frameRate.coerceIn(24, 60)
    val safePositionMs = currentPositionMs.coerceAtLeast(0L)
    val timecode = remember(safePositionMs, fps) {
        formatAeTimecode(safePositionMs, fps)
    }
    val effectiveMaxDurationMs = remember(layers, totalDurationMs) {
        maxOf(
            totalDurationMs,
            layers.maxOfOrNull { it.startTimeMs + it.effectiveLayerDurationMs } ?: 0L,
            3_000L
        )
    }
    var isPinchingTimeline by remember { mutableStateOf(false) }
    var expandedControlsLayerId by remember(project.id) { mutableStateOf<String?>(null) }
    val zoomScale = remember(timelineZoom) { computeAeTimelineZoomScale(timelineZoom) }
    val density = LocalDensity.current
    val pxPerMs = remember(density, zoomScale.dpPerSecond) {
        with(density) { zoomScale.dpPerSecond.dp.toPx() / 1000f }
    }
    val latestCurrentPosMs by rememberUpdatedState(safePositionMs)
    val latestMaxDurationMs by rememberUpdatedState(effectiveMaxDurationMs)
    val latestIsDraggingLayer by rememberUpdatedState(isDraggingLayerBlock)
    val latestIsPinching by rememberUpdatedState(isPinchingTimeline)
    val latestPxPerMs by rememberUpdatedState(pxPerMs)
    val latestOnClearLayerSelection by rememberUpdatedState(onClearLayerSelection)
    val textMeasurer = rememberTextMeasurer()
    val rulerLabelStyle = remember {
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = Color(0xFF8E94A5)
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(timelineHeight)
            .background(AeDeepNavyBg)
            .systemGestureExclusion()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var pinchEngaged = false
                    do {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2) {
                            pinchEngaged = true
                            if (!isPinchingTimeline) {
                                isPinchingTimeline = true
                                onTimelineScrubStateChange(false)
                            }
                            val zoomFactor = event.calculateZoom()
                            if (abs(zoomFactor - 1f) > 0.002f) {
                                onTimelineZoomBy(zoomFactor)
                            }
                            event.changes.forEach { if (it.pressed) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (pinchEngaged) {
                        isPinchingTimeline = false
                    }
                }
            }
            .pointerInput(Unit) {
                var accumPosMs = 0L
                detectHorizontalDragGestures(
                    onDragStart = {
                        if (latestIsPinching) return@detectHorizontalDragGestures
                        onTimelineScrubStateChange(true)
                        onBeginScrubbing()
                        onPausePlayback()
                        accumPosMs = latestCurrentPosMs
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (latestIsDraggingLayer || latestIsPinching) return@detectHorizontalDragGestures
                        change.consume()
                        val deltaMs = (-dragAmount / latestPxPerMs.coerceAtLeast(0.001f)).roundToLong()
                        accumPosMs = (accumPosMs + deltaMs).coerceIn(0L, latestMaxDurationMs)
                        onSeekPositionMs(accumPosMs)
                    },
                    onDragEnd = {
                        onTimelineScrubStateChange(false)
                        onEndScrubbing()
                    },
                    onDragCancel = {
                        onTimelineScrubStateChange(false)
                        onEndScrubbing()
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        expandedControlsLayerId = null
                        latestOnClearLayerSelection()
                    }
                )
            }
            .testTag("ae_timeline_section")
    ) {
        val totalWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val trackViewportWidthPx = totalWidthPx
        val playheadGlobalX = totalWidthPx * 0.5f
        val playheadInTrackPx = playheadGlobalX

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Ruler Strip: full-width ruler aligned 1:1 with centered playhead
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .testTag("ae_timeline_ruler")
                ) {
                    if (size.width <= 0f || size.height <= 0f) return@Canvas
                    val minVisMs = maxOf(
                        0L,
                        safePositionMs - (playheadInTrackPx / pxPerMs).toLong() - zoomScale.majorStepMs
                    )
                    val maxVisMs = minOf(
                        effectiveMaxDurationMs + zoomScale.majorStepMs * 2L,
                        safePositionMs + ((trackViewportWidthPx - playheadInTrackPx) / pxPerMs).toLong() + zoomScale.majorStepMs
                    )
                    val stepMs = zoomScale.minorStepMs
                    val firstTickMs = ((minVisMs / stepMs) * stepMs).coerceAtLeast(0L)
                    var tickMs = firstTickMs
                    while (tickMs <= maxVisMs) {
                        val x = playheadInTrackPx + (tickMs - safePositionMs) * pxPerMs
                        if (x >= -24f && x <= size.width + 24f) {
                            val isMajor = tickMs % zoomScale.majorStepMs == 0L
                            val tickH = if (isMajor) 12.dp.toPx() else 6.dp.toPx()
                            val yStart = (size.height - tickH).coerceAtLeast(0f)
                            drawLine(
                                color = if (tickMs == 0L) {
                                    AeNeonGreen.copy(alpha = 0.85f)
                                } else {
                                    Color.White.copy(alpha = if (isMajor) 0.52f else 0.24f)
                                },
                                start = Offset(x, yStart),
                                end = Offset(x, size.height),
                                strokeWidth = if (isMajor) 1.3.dp.toPx() else 1.dp.toPx()
                            )
                            if (isMajor) {
                                val secLabel = if (tickMs % 1000L == 0L) {
                                    "${tickMs / 1000L}s"
                                } else {
                                    String.format(Locale.US, "%.1fs", tickMs / 1000f)
                                }
                                val labelX = x + 3.dp.toPx()
                                val labelY = (size.height - 24.dp.toPx()).coerceAtLeast(0f)
                                val measuredLabel = textMeasurer.measure(
                                    text = secLabel,
                                    style = rulerLabelStyle,
                                    softWrap = false,
                                    maxLines = 1
                                )
                                if (
                                    labelX < size.width &&
                                    labelX + measuredLabel.size.width > 0f &&
                                    labelY < size.height
                                ) {
                                    drawText(
                                        textLayoutResult = measuredLabel,
                                        topLeft = Offset(labelX, labelY)
                                    )
                                }
                            }
                        }
                        tickMs += stepMs
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0x99141926))
                        .clickable { onSeekPositionMs(0L) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = "Scroll timeline to start",
                        tint = AeMutedGray,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Surface(
                    color = Color(0xFF1A1E2E),
                    shape = RoundedCornerShape(7.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("ae_timecode_pill")
                ) {
                    Text(
                        text = timecode,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = isPinchingTimeline || abs(zoomScale.zoomFactor - 1f) > 0.05f,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                ) {
                    Surface(
                        color = Color(0xCC132226),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AeNeonGreen.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onResetTimelineZoom)
                            .testTag("ae_timeline_zoom_badge")
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.1fx", zoomScale.zoomFactor),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = AeNeonGreen,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // UNLIMITED VERTICAL MULTI-LAYER STACK (full-width tracks + subtle left-edge circular control bubble)
            if (layers.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    expandedControlsLayerId = null
                                    latestOnClearLayerSelection()
                                }
                            )
                        }
                        .padding(top = 4.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    layers.forEachIndexed { idx, layer ->
                        key(layer.id) {
                        val isSelected = layer.id == selectedLayerId
                        val isControlsExpanded = expandedControlsLayerId == layer.id
                        val hasKeyframes = layer.keyframes.isNotEmpty() ||
                            layer.effectPasses.any { it.keyframes.isNotEmpty() || it.paramKeyframes.isNotEmpty() }
                        val latestLayerStartMs by rememberUpdatedState(layer.startTimeMs)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            expandedControlsLayerId = null
                                            latestOnClearLayerSelection()
                                        }
                                    )
                                }
                                .testTag("ae_layer_row_$idx")
                        ) {
                            // Full-width Track Lane
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clipToBounds()
                            ) {
                                val clipLeftPx = playheadInTrackPx + (layer.startTimeMs - safePositionMs) * pxPerMs
                                val clipWidthPx = (layer.effectiveLayerDurationMs * pxPerMs)
                                    .coerceAtLeast(with(density) { 56.dp.toPx() })
                                val barAccent = when (layer.layerKind) {
                                    AeLayerKind.MEDIA -> AeNeonGreen
                                    AeLayerKind.TEXT -> Color(0xFF4DD0E1)
                                    AeLayerKind.SHAPE -> Color(0xFFA78BFA)
                                    AeLayerKind.AUDIO -> Color(0xFFFBBF24)
                                    AeLayerKind.ADJUSTMENT -> Color(0xFFF472B6)
                                    AeLayerKind.NULL -> Color(0xFFFACC15)
                                    else -> Color(0xFF60A5FA)
                                }
                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(clipLeftPx.roundToInt(), 0) }
                                        .width(with(density) { clipWidthPx.toDp() })
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (layer.isVisible) Color(0xFF111522) else Color(0xFF0D101A))
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) AeNeonGreen else barAccent.copy(alpha = 0.45f),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .pointerInput(layer.id, layer.isLocked) {
                                            if (layer.isLocked) return@pointerInput
                                            var dragBaseStartMs = 0L
                                            var lastDraggedStartMs = 0L
                                            var accumDragPx = 0f
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    if (latestIsPinching) return@detectDragGesturesAfterLongPress
                                                    onLayerDragStateChange(true)
                                                    dragBaseStartMs = latestLayerStartMs
                                                    lastDraggedStartMs = dragBaseStartMs
                                                    accumDragPx = 0f
                                                },
                                                onDrag = { change, dragAmount ->
                                                    if (latestIsPinching) return@detectDragGesturesAfterLongPress
                                                    change.consume()
                                                    accumDragPx += dragAmount.x
                                                    val nextStart = (dragBaseStartMs + (accumDragPx / latestPxPerMs.coerceAtLeast(0.001f)).roundToLong())
                                                        .coerceAtLeast(0L)
                                                    lastDraggedStartMs = nextStart
                                                    onLiveDragLayerTiming(layer.id, nextStart)
                                                },
                                                onDragEnd = {
                                                    onLayerDragStateChange(false)
                                                    onCommitMoveLayerTiming(layer.id, dragBaseStartMs, lastDraggedStartMs)
                                                },
                                                onDragCancel = {
                                                    onLayerDragStateChange(false)
                                                    onCommitMoveLayerTiming(layer.id, dragBaseStartMs, lastDraggedStartMs)
                                                }
                                            )
                                        }
                                        .clickable {
                                            if (!layer.isLocked) onSelectLayer(idx, layer)
                                        }
                                        .testTag("ae_timeline_clip_$idx")
                                ) {
                                    if (layer.mediaClip != null) {
                                        val stripFrameTimeMs = (layer.mediaClip as? MediaClip.Video)?.startTrimMs ?: 0L
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            repeat(3) {
                                                MediaUriThumbnail(
                                                    uriString = layer.mediaClip.uriString,
                                                    isVideo = layer.mediaClip is MediaClip.Video,
                                                    contentDescription = layer.displayTitle,
                                                    frameTimeMs = stripFrameTimeMs,
                                                    maxDimPx = 144,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxHeight()
                                                        .alpha(if (layer.isVisible) 0.52f else 0.2f)
                                                )
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(barAccent.copy(alpha = 0.12f))
                                        )
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.TopStart)
                                            .padding(horizontal = 6.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (isSelected && !layer.isLocked) {
                                                var gripDragBaseMs by remember(layer.id) { mutableStateOf(0L) }
                                                var gripLastDraggedMs by remember(layer.id) { mutableStateOf(0L) }
                                                var gripAccumPx by remember(layer.id) { mutableFloatStateOf(0f) }
                                                Icon(
                                                    imageVector = Icons.Outlined.DragIndicator,
                                                    contentDescription = "Drag clip timing",
                                                    tint = AeNeonGreen,
                                                    modifier = Modifier
                                                        .size(15.dp)
                                                        .pointerInput(layer.id) {
                                                            detectHorizontalDragGestures(
                                                                onDragStart = {
                                                                    if (latestIsPinching) return@detectHorizontalDragGestures
                                                                    onLayerDragStateChange(true)
                                                                    gripDragBaseMs = latestLayerStartMs
                                                                    gripLastDraggedMs = gripDragBaseMs
                                                                    gripAccumPx = 0f
                                                                },
                                                                onHorizontalDrag = { change, dragAmount ->
                                                                    if (latestIsPinching) return@detectHorizontalDragGestures
                                                                    change.consume()
                                                                    gripAccumPx += dragAmount
                                                                    val nextStart = (gripDragBaseMs + (gripAccumPx / latestPxPerMs.coerceAtLeast(0.001f)).roundToLong())
                                                                        .coerceAtLeast(0L)
                                                                    gripLastDraggedMs = nextStart
                                                                    onLiveDragLayerTiming(layer.id, nextStart)
                                                                },
                                                                onDragEnd = {
                                                                    onLayerDragStateChange(false)
                                                                    onCommitMoveLayerTiming(layer.id, gripDragBaseMs, gripLastDraggedMs)
                                                                },
                                                                onDragCancel = {
                                                                    onLayerDragStateChange(false)
                                                                    onCommitMoveLayerTiming(layer.id, gripDragBaseMs, gripLastDraggedMs)
                                                                }
                                                            )
                                                        }
                                                        .testTag("ae_layer_drag_grip_$idx")
                                                )
                                            }
                                            Text(
                                                text = layer.displayTitle,
                                                fontFamily = InterFontFamily,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp,
                                                color = if (layer.isVisible) Color.White else AeMutedGray,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (layer.effectPasses.isNotEmpty()) {
                                            Text(
                                                text = "${layer.effectPasses.size} FX",
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp,
                                                color = AeNeonGreen
                                            )
                                        }
                                    }

                                    if (hasKeyframes) {
                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(bottom = 2.dp),
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            repeat(3) {
                                                Text("◆", fontSize = 8.sp, color = AeNeonGreen)
                                            }
                                        }
                                    }
                                }
                            }

                            // Subtle floating circular edge bubble emerging from left edge (expands on tap into compact pill)
                            Row(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .offset(x = (-4).dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val bubbleBorderColor = when {
                                    layer.isLocked -> Color(0x88FBBF24)
                                    !layer.isVisible -> Color(0x668E94A5)
                                    isControlsExpanded -> AeNeonGreen.copy(alpha = 0.65f)
                                    else -> Color.White.copy(alpha = 0.14f)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            expandedControlsLayerId = if (isControlsExpanded) null else layer.id
                                        }
                                        .testTag("ae_layer_controls_bubble_$idx"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(Color(0x9E121624))
                                            .border(0.8.dp, bubbleBorderColor, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                layer.isLocked -> Icons.Outlined.Lock
                                                !layer.isVisible -> Icons.Outlined.VisibilityOff
                                                else -> Icons.Outlined.LockOpen
                                            },
                                            contentDescription = "Layer Visibility & Lock",
                                            tint = when {
                                                layer.isLocked -> Color(0xFFFBBF24)
                                                !layer.isVisible -> AeMutedGray
                                                else -> Color.White.copy(alpha = 0.55f)
                                            },
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }

                                androidx.compose.animation.AnimatedVisibility(
                                    visible = isControlsExpanded,
                                    enter = fadeIn(tween(150)) + expandHorizontally(tween(170)),
                                    exit = fadeOut(tween(130)) + shrinkHorizontally(tween(150))
                                ) {
                                    Surface(
                                        color = Color(0xE6141928),
                                        shape = RoundedCornerShape(18.dp),
                                        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.16f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (layer.isVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                                contentDescription = "Toggle Layer Visibility",
                                                tint = if (layer.isVisible) AeNeonGreen else AeMutedGray,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable { onToggleVisibility(layer.id) }
                                                    .testTag("ae_layer_eye_$idx")
                                            )
                                            Icon(
                                                imageVector = if (layer.isLocked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                                                contentDescription = "Lock Layer",
                                                tint = if (layer.isLocked) Color(0xFFFBBF24) else AeMutedGray,
                                                modifier = Modifier
                                                    .size(15.dp)
                                                    .clickable { onToggleLock(layer.id) }
                                                    .testTag("ae_layer_lock_$idx")
                                            )
                                            if (layers.size > 1) {
                                                if (idx > 0) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.KeyboardArrowUp,
                                                        contentDescription = "Move layer up",
                                                        tint = Color(0xFFD1D5DB),
                                                        modifier = Modifier
                                                            .size(16.dp)
                                                            .clickable { onReorderLayer(idx, idx - 1) }
                                                            .testTag("ae_layer_up_$idx")
                                                    )
                                                }
                                                if (idx < layers.lastIndex) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.KeyboardArrowDown,
                                                        contentDescription = "Move layer down",
                                                        tint = Color(0xFFD1D5DB),
                                                        modifier = Modifier
                                                            .size(16.dp)
                                                            .clickable { onReorderLayer(idx, idx + 1) }
                                                            .testTag("ae_layer_down_$idx")
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }
                }
            }
        }

        // Centered Red Playhead Hairline
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 26.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(width = 7.dp, height = 4.dp)
                        .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                        .background(AeRedPlayhead)
                )
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .fillMaxHeight()
                        .background(AeRedPlayhead)
                )
            }
        }
    }
}

@Composable
private fun AeIdleBottomToolbar(
    isColorizeArmed: Boolean,
    onColorizeArmChange: (Boolean) -> Unit,
    onColorizeBoundsChanged: (Rect) -> Unit,
    onOpenAddSheet: () -> Unit,
    onOpenAeBrowser: (String?) -> Unit,
    onImportVideo: () -> Unit,
    onImportPhoto: () -> Unit,
    onExportVideo: () -> Unit,
    onSplitAeLayer: () -> Unit
) {
    Surface(
        color = AeSlateSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .width(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenAddSheet)
                    .padding(vertical = 2.dp)
                    .testTag("ae_add_button"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        AeNeonGreenGlow.copy(alpha = 0.42f),
                                        Color.Transparent
                                    )
                                ),
                                radius = size.maxDimension * 0.78f
                            )
                        }
                        .clip(RoundedCornerShape(10.dp))
                        .background(AeNeonGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Add",
                        tint = Color(0xFF04140B),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = "Add",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = AeNeonGreen,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AeToolbarIconItem(
                    icon = Icons.Outlined.AutoAwesome,
                    label = "AE FX",
                    onClick = { onOpenAeBrowser(null) },
                    testTag = "ae_tool_ai"
                )
                AeToolbarIconItem(
                    icon = Icons.Outlined.PermMedia,
                    label = "Video",
                    onClick = onImportVideo,
                    testTag = "ae_tool_import_video"
                )
                AeCustomGlyphToolItem(
                    label = "Photo",
                    onClick = onImportPhoto
                ) {
                    AeLibraryCirclePGlyph()
                }
                AeCustomGlyphToolItem(
                    label = "Upscale",
                    onClick = onExportVideo
                ) {
                    AeBoxedBadgeGlyph("HD")
                }
                AeCustomGlyphToolItem(
                    label = "Text FX",
                    onClick = { onOpenAeBrowser("text") }
                ) {
                    AeBoxedBadgeGlyph("TT")
                }
                AeToolbarIconItem(
                    icon = Icons.Outlined.AutoFixHigh,
                    label = "Matte Key",
                    onClick = { onOpenAeBrowser("cutout") },
                    testTag = "ae_tool_remove_bg"
                )
                AeToolbarIconItem(
                    icon = Icons.Outlined.GridView,
                    label = "Split Layer",
                    onClick = onSplitAeLayer
                )
                // Two-step Transparent-to-Highlighted trigger in Idle Toolbar
                AeTwoStepTriggerPill(
                    label = "Colorize",
                    isArmed = isColorizeArmed,
                    testTag = "layout_add_effect_button",
                    onArmChange = onColorizeArmChange,
                    onBoundsChanged = onColorizeBoundsChanged,
                    onConfirmedAction = { onOpenAeBrowser("color") },
                    accentColor = AeNeonGreen
                )
                AeToolbarIconItem(
                    icon = Icons.Outlined.Terrain,
                    label = "3D Space",
                    onClick = { onOpenAeBrowser("3d") }
                )
            }
        }
    }
}

@Composable
private fun AeToolbarIconItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String? = null
) {
    val mod = Modifier
        .height(54.dp)
        .clip(RoundedCornerShape(10.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 6.dp, vertical = 4.dp)
        .let { if (testTag != null) it.testTag(testTag) else it }

    Column(
        modifier = mod,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color(0xFFD1D5DB),
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            fontFamily = InterFontFamily,
            fontSize = 11.sp,
            color = AeMutedGray,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun AeCustomGlyphToolItem(
    label: String,
    onClick: () -> Unit,
    glyph: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .height(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            glyph()
        }
        Text(
            text = label,
            fontFamily = InterFontFamily,
            fontSize = 11.sp,
            color = AeMutedGray,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun AeAddMediaBottomSheet(
    selectedTab: AeAddTab,
    onSelectTab: (AeAddTab) -> Unit,
    onCloseAddSheet: () -> Unit,
    onOpenGallery: () -> Unit,
    onImportVideo: () -> Unit,
    onImportPhoto: () -> Unit,
    onAddLayerKind: (AeLayerKind) -> Unit
) {
    Surface(
        color = AeTealSheetBg,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ae_add_bottom_sheet")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(256.dp)
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1B3036))
                            .clickable(onClick = onCloseAddSheet)
                            .testTag("ae_add_sheet_back_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to Edit & Add",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AeAddTopTab(Icons.Outlined.PermMedia, AeAddTab.MEDIA.label, selectedTab == AeAddTab.MEDIA) {
                            onSelectTab(AeAddTab.MEDIA)
                        }
                        AeAddTopTab(Icons.Outlined.MusicNote, AeAddTab.AUDIO.label, selectedTab == AeAddTab.AUDIO) {
                            onSelectTab(AeAddTab.AUDIO)
                        }
                        AeAddTopTab(Icons.Outlined.Category, AeAddTab.SHAPE.label, selectedTab == AeAddTab.SHAPE) {
                            onSelectTab(AeAddTab.SHAPE)
                        }
                        AeAddTopTab(Icons.Outlined.Settings, AeAddTab.ELEMENT.label, selectedTab == AeAddTab.ELEMENT) {
                            onSelectTab(AeAddTab.ELEMENT)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = when (selectedTab) {
                                AeAddTab.MEDIA -> Icons.Outlined.Folder
                                AeAddTab.AUDIO -> Icons.Outlined.MusicNote
                                AeAddTab.SHAPE -> Icons.Outlined.Category
                                AeAddTab.ELEMENT -> Icons.Outlined.AutoAwesome
                            },
                            contentDescription = null,
                            tint = AeMutedGray,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = when (selectedTab) {
                                AeAddTab.MEDIA -> "Add videos or photos directly onto the After Effects timeline."
                                AeAddTab.AUDIO -> "Stack an independent Audio layer at the current timestamp."
                                AeAddTab.SHAPE -> "Stack a new vector Shape layer onto your multi-layer timeline."
                                AeAddTab.ELEMENT -> "Stack an animated Element layer ready for AE effects."
                            },
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = Color(0xFFC0C6D4),
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = {
                                when (selectedTab) {
                                    AeAddTab.MEDIA -> onOpenGallery()
                                    AeAddTab.AUDIO -> onAddLayerKind(AeLayerKind.AUDIO)
                                    AeAddTab.SHAPE -> onAddLayerKind(AeLayerKind.SHAPE)
                                    AeAddTab.ELEMENT -> onAddLayerKind(AeLayerKind.ELEMENT)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AeNeonGreen,
                                contentColor = Color(0xFF04140B)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .height(46.dp)
                                .testTag("ae_open_gallery_button")
                        ) {
                            Text(
                                text = when (selectedTab) {
                                    AeAddTab.MEDIA -> "Open gallery"
                                    AeAddTab.AUDIO -> "Add audio layer"
                                    AeAddTab.SHAPE -> "Add shape layer"
                                    AeAddTab.ELEMENT -> "Add element layer"
                                },
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        if (selectedTab == AeAddTab.MEDIA) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = Color(0xFF1B3036),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable(onClick = onImportVideo)
                                        .testTag("ae_add_video_button")
                                ) {
                                    Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                                        Text("Video", fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color.White)
                                    }
                                }
                                Surface(
                                    color = Color(0xFF1B3036),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable(onClick = onImportPhoto)
                                        .testTag("ae_add_photo_button")
                                ) {
                                    Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                                        Text("Photo", fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            VerticalDivider(thickness = 1.dp, color = Color(0xFF1D3338))

            // Right Vertical Sidebar: Quick Close + Text (TT), Draw, Adjustment, Null
            Column(
                modifier = Modifier
                    .width(78.dp)
                    .fillMaxHeight()
                    .background(AeTealSidebarBg)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                AeSidebarToolButton(
                    label = "Back",
                    testTag = "ae_sidebar_back_button",
                    onClick = onCloseAddSheet
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Add Panel",
                        tint = AeNeonGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
                AeSidebarToolButton(
                    label = "Text",
                    testTag = "ae_sidebar_text_button",
                    onClick = { onAddLayerKind(AeLayerKind.TEXT) }
                ) {
                    Text(
                        text = "TT",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = Color(0xFFD1D5DB)
                    )
                }
                AeSidebarToolButton(
                    label = "Draw",
                    testTag = "ae_sidebar_draw_button",
                    onClick = { onAddLayerKind(AeLayerKind.DRAW) }
                ) {
                    Icon(Icons.Outlined.Brush, contentDescription = "Draw", tint = Color(0xFFD1D5DB), modifier = Modifier.size(19.dp))
                }
                AeSidebarToolButton(
                    label = "Adjustment",
                    testTag = "ae_sidebar_adjustment_button",
                    onClick = { onAddLayerKind(AeLayerKind.ADJUSTMENT) }
                ) {
                    Icon(Icons.Outlined.WbSunny, contentDescription = "Adjustment", tint = Color(0xFFD1D5DB), modifier = Modifier.size(19.dp))
                }
                AeSidebarToolButton(
                    label = "Null",
                    testTag = "ae_sidebar_null_button",
                    onClick = { onAddLayerKind(AeLayerKind.NULL) }
                ) {
                    Icon(Icons.Outlined.OpenWith, contentDescription = "Null", tint = Color(0xFFD1D5DB), modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

@Composable
private fun AeAddTopTab(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val tint = if (active) AeNeonGreen else AeMutedGray
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            text = label,
            fontFamily = InterFontFamily,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 11.sp,
            color = tint,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun AeSidebarToolButton(
    label: String,
    testTag: String,
    onClick: () -> Unit,
    glyph: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        glyph()
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontFamily = InterFontFamily,
            fontSize = 10.sp,
            color = AeMutedGray,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun AeLayerPropertiesGridPanel(
    layerTitle: String,
    onBackToIdle: () -> Unit,
    onOpenTimingSpeed: () -> Unit,
    onTrimStart: () -> Unit,
    onSplitLayer: () -> Unit,
    onTrimEnd: () -> Unit,
    onOpenBorderShadow: () -> Unit,
    onOpenBlending: () -> Unit,
    onOpenAnimate: () -> Unit,
    onOpenTransform: () -> Unit,
    onOpenEffectsSheet: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AeDeepNavyBg)
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("ae_layer_properties_panel"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                color = AeCardSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AeNeonGreen.copy(alpha = 0.45f)),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onBackToIdle)
                    .testTag("ae_properties_back_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to Edit & Add",
                        tint = AeNeonGreen,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Edit & Add",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = Color.White
                    )
                }
            }

            Text(
                text = layerTitle,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = AeMutedGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 100.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AeCompactTrimCard(onClick = onOpenTimingSpeed, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Speed, contentDescription = "Speed", tint = Color(0xFFD1D5DB), modifier = Modifier.size(22.dp))
            }
            AeCompactTrimCard(onClick = onTrimStart, showCornerNotch = true, modifier = Modifier.weight(1f)) {
                AeTrimGlyph(leftCut = true)
            }
            AeCompactTrimCard(onClick = onSplitLayer, showCornerNotch = true, modifier = Modifier.weight(1f)) {
                AeSplitBarGlyph()
            }
            AeCompactTrimCard(onClick = onTrimEnd, showCornerNotch = true, modifier = Modifier.weight(1f)) {
                AeTrimGlyph(leftCut = false)
            }
            AeCompactTrimCard(onClick = onOpenTimingSpeed, modifier = Modifier.weight(1f)) {
                Icon(
                    Icons.AutoMirrored.Outlined.VolumeUp,
                    contentDescription = "Audio",
                    tint = Color(0xFFD1D5DB),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AePropertyFeatureCard(
                label = "Border & Shadow",
                onClick = onOpenBorderShadow,
                testTag = "ae_border_shadow_card_button",
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.GridView, contentDescription = null, tint = Color(0xFFE5E7EB), modifier = Modifier.size(22.dp))
            }
            AePropertyFeatureCard(
                label = "Opacity & Blending",
                onClick = onOpenBlending,
                testTag = "ae_blending_card_button",
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.WaterDropOrBlend(), contentDescription = null, tint = Color(0xFFE5E7EB), modifier = Modifier.size(22.dp))
            }
            AePropertyFeatureCard(
                label = "Animate",
                onClick = onOpenAnimate,
                testTag = "ae_animate_card_button",
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Animation, contentDescription = null, tint = Color(0xFFE5E7EB), modifier = Modifier.size(22.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AePropertyFeatureCard(
                label = "Move &\nTransform",
                onClick = onOpenTransform,
                testTag = "ae_transform_card_button",
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.OpenWith, contentDescription = null, tint = Color(0xFFE5E7EB), modifier = Modifier.size(21.dp))
            }
            AePropertyFeatureCard(
                label = "Timing &\nSpeed",
                onClick = onOpenTimingSpeed,
                testTag = "ae_timing_speed_card_button",
                modifier = Modifier.weight(1f)
            ) {
                AePuppetSplineGlyph()
            }
            AePropertyFeatureCard(
                label = "Keyframes",
                onClick = onOpenAnimate,
                testTag = "ae_keyframes_card_button",
                modifier = Modifier.weight(1f)
            ) {
                AeBonesChainGlyph()
            }
            AePropertyFeatureCard(
                label = "Effects",
                onClick = onOpenEffectsSheet,
                testTag = "ae_effects_card_button",
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = "Effects", tint = Color(0xFFE5E7EB), modifier = Modifier.size(21.dp))
            }
        }
    }
}

@Composable
private fun AeCompactTrimCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showCornerNotch: Boolean = false,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AeCardSurface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
        if (showCornerNotch) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(5.dp)
            ) {
                val p = Path().apply {
                    moveTo(size.width, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(p, color = Color(0xFF9CA3AF))
            }
        }
    }
}

@Composable
private fun AePropertyFeatureCard(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    iconContent: @Composable () -> Unit
) {
    val mod = modifier
        .height(74.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(AeCardSurface)
        .clickable(onClick = onClick)
        .padding(horizontal = 6.dp, vertical = 8.dp)
        .let { if (testTag != null) it.testTag(testTag) else it }

    Column(
        modifier = mod,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        iconContent()
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            color = Color(0xFFD1D5DB),
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

private fun Icons.Outlined.WaterDropOrBlend(): ImageVector = Layers

@Composable
private fun AeDiamondGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.25f, h * 0.16f)
            lineTo(w * 0.75f, h * 0.16f)
            lineTo(w * 0.94f, h * 0.42f)
            lineTo(w * 0.50f, h * 0.88f)
            lineTo(w * 0.06f, h * 0.42f)
            close()
        }
        drawPath(path, color = AeDiamondPurple)
        drawLine(Color.White.copy(alpha = 0.28f), Offset(w * 0.06f, h * 0.42f), Offset(w * 0.94f, h * 0.42f), strokeWidth = 1.dp.toPx())
    }
}

@Composable
private fun AeLibraryCirclePGlyph() {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .border(1.4.dp, Color(0xFFD1D5DB), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "P",
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
            color = Color(0xFFD1D5DB)
        )
    }
}

@Composable
private fun AeBoxedBadgeGlyph(badge: String) {
    Box(
        modifier = Modifier
            .size(width = 22.dp, height = 18.dp)
            .border(1.4.dp, Color(0xFFD1D5DB), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = badge,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 8.sp,
            color = Color(0xFFD1D5DB),
            maxLines = 1
        )
    }
}

@Composable
private fun AeTrimGlyph(leftCut: Boolean) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val s = 1.6.dp.toPx()
        val solid = Color(0xFFE5E7EB)
        val dim = Color(0xFF6B7280)
        drawLine(solid, Offset(w * 0.5f, h * 0.12f), Offset(w * 0.5f, h * 0.88f), strokeWidth = s)
        drawRect(
            color = if (leftCut) dim else solid,
            topLeft = Offset(w * 0.12f, h * 0.24f),
            size = Size(w * 0.26f, h * 0.52f),
            style = Stroke(width = s)
        )
        drawRect(
            color = if (leftCut) solid else dim,
            topLeft = Offset(w * 0.62f, h * 0.24f),
            size = Size(w * 0.26f, h * 0.52f),
            style = Stroke(width = s)
        )
    }
}

@Composable
private fun AeSplitBarGlyph() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val s = 1.6.dp.toPx()
        val solid = Color(0xFFE5E7EB)
        drawLine(solid, Offset(w * 0.5f, h * 0.12f), Offset(w * 0.5f, h * 0.88f), strokeWidth = s)
        drawLine(solid, Offset(w * 0.18f, h * 0.26f), Offset(w * 0.36f, h * 0.26f), strokeWidth = s)
        drawLine(solid, Offset(w * 0.18f, h * 0.26f), Offset(w * 0.18f, h * 0.74f), strokeWidth = s)
        drawLine(solid, Offset(w * 0.18f, h * 0.74f), Offset(w * 0.36f, h * 0.74f), strokeWidth = s)
        drawLine(solid, Offset(w * 0.82f, h * 0.26f), Offset(w * 0.64f, h * 0.26f), strokeWidth = s)
        drawLine(solid, Offset(w * 0.82f, h * 0.26f), Offset(w * 0.82f, h * 0.74f), strokeWidth = s)
        drawLine(solid, Offset(w * 0.82f, h * 0.74f), Offset(w * 0.64f, h * 0.74f), strokeWidth = s)
    }
}

@Composable
private fun AePuppetSplineGlyph() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.25f, h * 0.22f)
            cubicTo(w * 0.85f, h * 0.15f, w * 0.15f, h * 0.75f, w * 0.78f, h * 0.78f)
        }
        drawPath(
            path,
            color = Color(0xFFE5E7EB),
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
private fun AeBonesChainGlyph() {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val y = h * 0.5f
        drawLine(Color(0xFFE5E7EB), Offset(w * 0.16f, y), Offset(w * 0.84f, y), strokeWidth = 1.8.dp.toPx())
        listOf(0.16f, 0.50f, 0.84f).forEach { fx ->
            drawCircle(Color(0xFFE5E7EB), radius = 2.6.dp.toPx(), center = Offset(w * fx, y))
        }
    }
}

private fun formatAeTimecode(ms: Long, fps: Int): String {
    val safeMs = ms.coerceAtLeast(0L)
    val totalSecs = safeMs / 1000L
    val mins = (totalSecs / 60L) % 60L
    val secs = totalSecs % 60L
    val frames = ((safeMs % 1000L) * fps / 1000L).coerceIn(0L, (fps - 1).toLong())
    return String.format(Locale.US, "%02d:%02d:%02d", mins, secs, frames)
}
