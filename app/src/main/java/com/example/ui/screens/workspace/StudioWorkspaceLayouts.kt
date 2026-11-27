package com.example.ui.screens.workspace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.outlined.GridView
import com.example.R
import com.example.model.AeLayerKind
import com.example.model.AspectRatioMode
import com.example.model.ShaderEffectPass
import com.example.model.TimelineScene
import com.example.model.VideoProject
import com.example.model.evaluateVelocityAt
import com.example.ui.components.MediaUriThumbnail
import com.example.ui.components.SceneRenderStage
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.VideoStarBlack
import com.example.ui.theme.VideoStarMagenta
import com.example.ui.theme.VideoStarWhite
import java.util.Locale
import kotlin.math.roundToInt

data class StudioLayoutCallbacks(
    val onTogglePlayback: () -> Unit,
    val onPausePlayback: () -> Unit = {},
    val onBeginScrubbing: () -> Unit = {},
    val onEndScrubbing: () -> Unit = {},
    val onSeekProgress: (Float) -> Unit,
    val onSeekPositionMs: ((Long) -> Unit)? = null,
    val onStepBackward: () -> Unit,
    val onStepForward: () -> Unit,
    val onSelectScene: (Int) -> Unit,
    val onSyncVideoPos: (Long) -> Unit,
    val onSceneEnded: () -> Unit,
    val onEnterEffectMode: () -> Unit,
    val onOpenFullEffectsStudio: () -> Unit,
    val onOpenLayerEffectsStudio: (String?) -> Unit = { _ -> onOpenFullEffectsStudio() },
    val onOpenAudio: () -> Unit,
    val onImportVideo: () -> Unit,
    val onImportPhoto: () -> Unit,
    val onOpenLayers: () -> Unit,
    val onOpenTransform: () -> Unit,
    val onOpenMotion: () -> Unit,
    val onOpenVelocity: () -> Unit,
    val onOpenMix: () -> Unit,
    val onEnterSplitMode: () -> Unit,
    val onExportVideo: () -> Unit,
    val onSelectNodeStage: (String) -> Unit,
    val onToggleQuickEffect: (String) -> Unit,
    val onPreviewBoundsChanged: (Rect) -> Unit = {},
    val onUndo: () -> Unit = {},
    val onRedo: () -> Unit = {},
    val onDuplicateScene: () -> Unit = {},
    val onDeleteScene: () -> Unit = {},
    val onEnterTrimMode: () -> Unit = {},
    val onImportGalleryMedia: () -> Unit = {},
    val onOpenLayoutModal: () -> Unit = {},
    val onCloseWorkspace: () -> Unit = {},
    val onAfterEffectsOverlayChanged: (Boolean) -> Unit = {},
    val onOpenShaderGallery: () -> Unit = {},
    val onSelectAeLayer: (String?) -> Unit = {},
    val onAddAeLayerKind: (AeLayerKind) -> Unit = {},
    val onToggleAeLayerVisibility: (String) -> Unit = {},
    val onToggleAeLayerLock: (String) -> Unit = {},
    val onRenameAeLayer: (String, String) -> Unit = { _, _ -> },
    val onDuplicateAeLayer: () -> Unit = {},
    val onDeleteAeLayer: () -> Unit = {},
    val onBeginAeLayerReorder: () -> Unit = {},
    val onReorderAeLayer: (Int, Int) -> Unit = { _, _ -> },
    val onBeginAeLayerTimingDrag: () -> Unit = {},
    val onMoveAeLayerTiming: (String, Long) -> Unit = { _, _ -> },
    val onCommitAeLayerTiming: (String, Long, Long) -> Unit = { id, _, endMs -> onMoveAeLayerTiming(id, endMs) },
    val onAddAeLayerEffect: (String, String) -> ShaderEffectPass? = { _, _ -> null },
    val onUpdateAeLayerEffectPass: (String, ShaderEffectPass, Boolean) -> Unit = { _, _, _ -> },
    val onRemoveAeLayerEffectPass: (String, String) -> Unit = { _, _ -> },
    val onToggleAeLayerEffectKeyframe: (String, String, String, Float) -> Unit = { _, _, _, _ -> },
    val onUpdateAeLayerProps: (String, com.example.model.EditorLayer, Boolean) -> Unit = { _, _, _ -> },
    val onToggleAeTransformKeyframe: (String) -> Unit = {},
    val onTrimAeLayerStart: (String) -> Unit = {},
    val onTrimAeLayerEnd: (String) -> Unit = {},
    val onSplitAeLayer: (String) -> String? = { _ -> null }
)

@Composable
internal fun SharedStudioPreviewStage(
    project: VideoProject,
    activeScene: TimelineScene?,
    nextScene: TimelineScene?,
    localScenePosMs: Long,
    isPlaying: Boolean,
    speedMultiplier: Float,
    showShutterFlash: Boolean,
    isLandscapeOrWide: Boolean,
    borderColor: Color,
    callbacks: StudioLayoutCallbacks,
    modifier: Modifier = Modifier
) {
    val targetAspect = project.aspectRatio.ratio.coerceAtLeast(0.25f)

    BoxWithConstraints(
        modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        val hFromW = maxWidth / targetAspect
        val (stageW, stageH) = if (hFromW <= maxHeight) {
            maxWidth to hFromW
        } else {
            (maxHeight * targetAspect) to maxHeight
        }

        Box(
            modifier = Modifier
                .size(width = stageW, height = stageH)
                .onGloballyPositioned { callbacks.onPreviewBoundsChanged(it.boundsInRoot()) }
                .shadow(10.dp, RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp))
                .background(VideoStarBlack)
                .border(1.5.dp, borderColor, RoundedCornerShape(6.dp))
                .clickable(onClick = callbacks.onTogglePlayback)
                .testTag("workspace_preview_canvas")
        ) {
            val hasMediaScene = activeScene != null && (activeScene.clip != null || activeScene.isMultiLayerComposite)
            if (!hasMediaScene) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.cd_play_pause),
                        tint = VideoStarWhite,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Tap Import or + to add clip",
                        fontFamily = InterFontFamily,
                        fontSize = 11.sp,
                        color = Color(0xFF9195A8)
                    )
                }
            } else {
                SceneRenderStage(
                    scene = activeScene,
                    nextScene = nextScene,
                    localPositionMs = localScenePosMs,
                    isPlaying = isPlaying,
                    clipVolume = project.audioConfig.clipVolume,
                    speedMultiplier = speedMultiplier,
                    useLiveVideoPlayer = true,
                    contentDescription = project.title,
                    onVideoPositionChanged = callbacks.onSyncVideoPos,
                    onScenePlaybackEnded = callbacks.onSceneEnded,
                    modifier = Modifier.fillMaxSize()
                )

                val localProg = (localScenePosMs.toFloat() / activeScene.effectiveDurationMs.toFloat()).coerceIn(0f, 1f)
                val liveSpeed = evaluateVelocityAt(localProg, activeScene.velocityPoints, activeScene.isVelocitySmooth) *
                    speedMultiplier

                if (!isPlaying) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.cd_play_pause),
                        tint = VideoStarWhite.copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(52.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(VideoStarMagenta.copy(alpha = 0.85f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.2fx", liveSpeed),
                            fontFamily = InterFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showShutterFlash,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.82f)))
            }
        }
    }
}

private fun formatStudioTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    val tenths = (ms % 1000L) / 100L
    return String.format(Locale.US, "%02d:%02d.%d", mins, secs, tenths)
}

@Composable
private fun DarkTransportStrip(
    currentMs: Long,
    totalMs: Long,
    isPlaying: Boolean,
    accent: Color,
    onTogglePlayback: () -> Unit,
    onStepBackward: () -> Unit,
    onStepForward: () -> Unit,
    onExport: () -> Unit,
    onSeekProgress: ((Float) -> Unit)? = null,
    onBeginScrubbing: () -> Unit = {},
    onEndScrubbing: () -> Unit = {}
) {
    val safeTotalMs = totalMs.coerceAtLeast(500L)
    val progress = (currentMs.toFloat() / safeTotalMs.toFloat()).coerceIn(0f, 1f)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (onSeekProgress != null) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .pointerInput(safeTotalMs) {
                            detectTapGestures { offset ->
                                onBeginScrubbing()
                                onSeekProgress((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                                onEndScrubbing()
                            }
                        }
                        .pointerInput(safeTotalMs) {
                            detectHorizontalDragGestures(
                                onDragStart = { onBeginScrubbing() },
                                onDragEnd = { onEndScrubbing() },
                                onDragCancel = { onEndScrubbing() },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    onSeekProgress((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                                }
                            )
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    val density = LocalDensity.current
                    val thumbPx = with(density) { 10.dp.toPx() }
                    val trackWidthPx = with(density) { maxWidth.toPx() }
                    val usablePx = (trackWidthPx - thumbPx).coerceAtLeast(1f)
                    val thumbX = (progress * usablePx).roundToInt()

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = progress.coerceIn(0.01f, 1f))
                            .height(2.5.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(thumbX, 0) }
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, accent, CircleShape)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${formatStudioTime(currentMs)} / ${formatStudioTime(totalMs)}",
                    fontFamily = InterFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFB3B7C8)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onStepBackward),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = "Previous frame",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onTogglePlayback),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "Play or pause",
                                tint = if (accent == Color.White || accent == Color(0xFFB8FF29) || accent == Color(0xFF00E5FF)) {
                                    Color(0xFF0E1015)
                                } else {
                                    Color.White
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onStepForward),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "Next frame",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Surface(
                    color = Color(0xFF1F2230),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF33374B)),
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onExport)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FileUpload,
                            contentDescription = "Export MP4",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Export",
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomRailToolButton(
    icon: ImageVector,
    label: String,
    accent: Color = Color(0xFFD5D8E5),
    highlighted: Boolean = false,
    testTag: String? = null,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlighted) accent.copy(alpha = 0.16f) else Color.Transparent)
            .border(
                width = if (highlighted) 1.dp else 0.dp,
                color = if (highlighted) accent.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (highlighted) accent else Color(0xFFD5D8E5),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            fontFamily = InterFontFamily,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
            fontSize = 10.sp,
            color = if (highlighted) accent else Color(0xFF9EA3B8),
            maxLines = 1
        )
    }
}

// =====================================================================
// 1. CAPCUT WORKSPACE LAYOUT
// =====================================================================

@Composable
fun CapCutWorkspaceContent(
    project: VideoProject,
    uiState: WorkspaceUiState,
    showShutterFlash: Boolean,
    isLandscapeOrWide: Boolean,
    callbacks: StudioLayoutCallbacks,
    modifier: Modifier = Modifier,
    chromeAlpha: Float = 1f
) {
    val activeScene = uiState.activeScene
    val nextScene = project.scenes.getOrNull(uiState.activeSceneIndex + 1)
    val activePasses = activeScene?.resolvedEffectPasses().orEmpty()
    val capCutCyan = Color(0xFF00E5FF)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .testTag("studio_layout_capcut"),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SharedStudioPreviewStage(
                project = project,
                activeScene = activeScene,
                nextScene = nextScene,
                localScenePosMs = uiState.localPositionInActiveSceneMs,
                isPlaying = uiState.isPlaying,
                speedMultiplier = uiState.speedMultiplier,
                showShutterFlash = showShutterFlash,
                isLandscapeOrWide = isLandscapeOrWide,
                borderColor = Color(0xFF2E3140),
                callbacks = callbacks,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = chromeAlpha },
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
            DarkTransportStrip(
                currentMs = uiState.currentPositionMs,
                totalMs = uiState.totalDurationMs,
                isPlaying = uiState.isPlaying,
                accent = Color.White,
                onTogglePlayback = callbacks.onTogglePlayback,
                onStepBackward = callbacks.onStepBackward,
                onStepForward = callbacks.onStepForward,
                onExport = callbacks.onExportVideo,
                onSeekProgress = callbacks.onSeekProgress,
                onBeginScrubbing = callbacks.onBeginScrubbing,
                onEndScrubbing = callbacks.onEndScrubbing
            )

            // CapCut Magnetic Multi-Track Timeline with centered white playhead
            Surface(
                color = Color(0xFF13141B),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF252836)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Video Track
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (project.scenes.isEmpty()) {
                                Surface(
                                    color = Color(0xFF1E2130),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFF34384E)),
                                    modifier = Modifier
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(onClick = callbacks.onImportVideo)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Rounded.Add, null, tint = capCutCyan, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = "Add Video / Photo Clip",
                                            fontFamily = InterFontFamily,
                                            fontSize = 11.sp,
                                            color = Color(0xFFC8CAD6)
                                        )
                                    }
                                }
                            } else {
                                project.scenes.forEachIndexed { idx, sc ->
                                    val selected = idx == uiState.activeSceneIndex
                                    Row(
                                        modifier = Modifier
                                            .height(42.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (selected) Color(0xFF242838) else Color(0xFF1B1D29))
                                            .border(
                                                width = if (selected) 1.5.dp else 1.dp,
                                                color = if (selected) Color.White else Color(0xFF2F3346),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { callbacks.onSelectScene(idx) }
                                            .padding(horizontal = 6.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        MediaUriThumbnail(
                                            uriString = sc.mediaUriString,
                                            isVideo = sc.isVideo,
                                            contentDescription = sc.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(5.dp))
                                        )
                                        Column {
                                            Text(
                                                text = sc.title,
                                                fontFamily = InterFontFamily,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = String.format(Locale.US, "%.1fs", sc.effectiveDurationMs / 1000f),
                                                fontFamily = InterFontFamily,
                                                fontSize = 9.sp,
                                                color = Color(0xFF8C90A6)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Audio Track Pill
                        Surface(
                            color = Color(0xFF0E292E),
                            shape = RoundedCornerShape(7.dp),
                            border = BorderStroke(1.dp, capCutCyan.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .clickable(onClick = callbacks.onOpenAudio)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Outlined.MusicNote, null, tint = capCutCyan, modifier = Modifier.size(13.dp))
                                Text(
                                    text = project.audioConfig.songTitle,
                                    fontFamily = CairoFontFamily,
                                    fontSize = 10.sp,
                                    color = Color(0xFFE0F7FA),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Effects Track Pill
                        Surface(
                            color = Color(0xFF23182E),
                            shape = RoundedCornerShape(7.dp),
                            border = BorderStroke(1.dp, Color(0xFFC084FC).copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .clickable(onClick = callbacks.onEnterEffectMode)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Outlined.AutoAwesome, null, tint = Color(0xFFC084FC), modifier = Modifier.size(12.dp))
                                Text(
                                    text = if (activePasses.isEmpty()) {
                                        "Effects Track • Tap Effects / إضافة تأثير"
                                    } else {
                                        "FX: ${activePasses.joinToString(" + ") { it.effectName }}"
                                    },
                                    fontFamily = InterFontFamily,
                                    fontSize = 10.sp,
                                    color = Color(0xFFF3E8FF),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Centered crisp vertical white playhead indicator
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .width(2.dp)
                            .height(104.dp)
                            .background(Color.White)
                    )
                }
            }

            // CapCut Bottom Action Rail (hidden when inline effect drawer replaces bottom area)
            if (!uiState.isInlineEffectMode) {
                Surface(
                    color = Color(0xFF101118),
                    border = BorderStroke(1.dp, Color(0xFF222432)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BottomRailToolButton(Icons.Outlined.ContentCut, "Split", onClick = callbacks.onEnterSplitMode)
                        BottomRailToolButton(Icons.Outlined.MusicNote, "Audio", onClick = callbacks.onOpenAudio)
                        BottomRailToolButton(
                            icon = Icons.Outlined.AutoAwesome,
                            label = "إضافة تأثير",
                            accent = capCutCyan,
                            highlighted = true,
                            testTag = "layout_add_effect_button",
                            onClick = callbacks.onEnterEffectMode
                        )
                        BottomRailToolButton(
                            icon = Icons.Outlined.GridView,
                            label = "145 Gallery",
                            testTag = "layout_shader_gallery_button",
                            onClick = callbacks.onOpenShaderGallery
                        )
                        BottomRailToolButton(Icons.Outlined.Layers, "Overlay", onClick = callbacks.onOpenLayers)
                        BottomRailToolButton(Icons.Outlined.Speed, "Speed", onClick = callbacks.onOpenVelocity)
                        BottomRailToolButton(Icons.Outlined.Tune, "Adjust", onClick = callbacks.onOpenTransform)
                        BottomRailToolButton(Icons.Outlined.VideoLibrary, "Video +", onClick = callbacks.onImportVideo)
                        BottomRailToolButton(Icons.Outlined.AddPhotoAlternate, "Photo +", onClick = callbacks.onImportPhoto)
                    }
                }
            }
            }
        }
    }
}

// =====================================================================
// 2. BLURRR WORKSPACE LAYOUT
// =====================================================================

@Composable
fun BlurrrWorkspaceContent(
    project: VideoProject,
    uiState: WorkspaceUiState,
    showShutterFlash: Boolean,
    isLandscapeOrWide: Boolean,
    callbacks: StudioLayoutCallbacks,
    modifier: Modifier = Modifier,
    chromeAlpha: Float = 1f
) {
    val activeScene = uiState.activeScene
    val nextScene = project.scenes.getOrNull(uiState.activeSceneIndex + 1)
    val blurrrLime = Color(0xFFB8FF29)
    val activePasses = activeScene?.resolvedEffectPasses().orEmpty()

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .testTag("studio_layout_blurrr"),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SharedStudioPreviewStage(
                project = project,
                activeScene = activeScene,
                nextScene = nextScene,
                localScenePosMs = uiState.localPositionInActiveSceneMs,
                isPlaying = uiState.isPlaying,
                speedMultiplier = uiState.speedMultiplier,
                showShutterFlash = showShutterFlash,
                isLandscapeOrWide = isLandscapeOrWide,
                borderColor = blurrrLime.copy(alpha = 0.65f),
                callbacks = callbacks,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = chromeAlpha },
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
            DarkTransportStrip(
                currentMs = uiState.currentPositionMs,
                totalMs = uiState.totalDurationMs,
                isPlaying = uiState.isPlaying,
                accent = blurrrLime,
                onTogglePlayback = callbacks.onTogglePlayback,
                onStepBackward = callbacks.onStepBackward,
                onStepForward = callbacks.onStepForward,
                onExport = callbacks.onExportVideo,
                onSeekProgress = callbacks.onSeekProgress,
                onBeginScrubbing = callbacks.onBeginScrubbing,
                onEndScrubbing = callbacks.onEndScrubbing
            )

            // Kinetic Velocity Curve + Audio Pulse Deck
            Surface(
                color = Color(0xFF111510),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, blurrrLime.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Outlined.Bolt, null, tint = blurrrLime, modifier = Modifier.size(15.dp))
                            Text(
                                text = "Blurrr Kinetic Deck • ${activePasses.size} FX",
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                        Surface(
                            color = Color(0xFF282210),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Unavailable for now • Prototype",
                                fontFamily = InterFontFamily,
                                fontSize = 9.sp,
                                color = Color(0xFFFBBF24),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Mini live velocity spline + quick FX toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Canvas(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0A0D09))
                                .clickable(onClick = callbacks.onOpenVelocity)
                        ) {
                            val w = size.width
                            val h = size.height
                            val path = Path().apply {
                                moveTo(0f, h * 0.72f)
                                cubicTo(w * 0.25f, h * 0.15f, w * 0.55f, h * 0.85f, w, h * 0.28f)
                            }
                            drawPath(path, color = blurrrLime, style = Stroke(width = 3f, cap = StrokeCap.Round))
                        }

                        val presets = listOf(
                            "glow_bloom" to "Neon",
                            "hard_shake" to "Shake",
                            "radial_zoom_blur" to "Zoom"
                        )
                        presets.forEach { (id, label) ->
                            val isActive = activePasses.any { it.effectId == id }
                            Surface(
                                color = if (isActive) blurrrLime else Color(0xFF1A1F17),
                                shape = RoundedCornerShape(9.dp),
                                border = BorderStroke(1.dp, if (isActive) blurrrLime else Color(0xFF313A2A)),
                                modifier = Modifier
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .clickable { callbacks.onToggleQuickEffect(id) }
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (isActive) Color(0xFF0B0E09) else Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!uiState.isInlineEffectMode) {
                Surface(
                    color = Color(0xFF0E1014),
                    border = BorderStroke(1.dp, Color(0xFF242830)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BottomRailToolButton(
                            icon = Icons.Outlined.AutoAwesome,
                            label = "إضافة تأثير",
                            accent = blurrrLime,
                            highlighted = true,
                            testTag = "layout_add_effect_button",
                            onClick = callbacks.onEnterEffectMode
                        )
                        BottomRailToolButton(Icons.Outlined.GridView, "145 Gallery", onClick = callbacks.onOpenShaderGallery)
                        BottomRailToolButton(Icons.Outlined.Bolt, "Velocity", onClick = callbacks.onOpenVelocity)
                        BottomRailToolButton(Icons.Outlined.Animation, "Motion", onClick = callbacks.onOpenMotion)
                        BottomRailToolButton(Icons.Outlined.GraphicEq, "Audio", onClick = callbacks.onOpenAudio)
                        BottomRailToolButton(Icons.Outlined.Layers, "Layers", onClick = callbacks.onOpenLayers)
                        BottomRailToolButton(Icons.Outlined.Tune, "Mix", onClick = callbacks.onOpenMix)
                        BottomRailToolButton(Icons.Outlined.VideoLibrary, "Import", onClick = callbacks.onImportVideo)
                    }
                }
            }
            }
        }
    }
}

// =====================================================================
// 3. NODE VIDEO WORKSPACE LAYOUT
// =====================================================================

@Composable
fun NodeVideoWorkspaceContent(
    project: VideoProject,
    uiState: WorkspaceUiState,
    showShutterFlash: Boolean,
    isLandscapeOrWide: Boolean,
    callbacks: StudioLayoutCallbacks,
    modifier: Modifier = Modifier,
    chromeAlpha: Float = 1f
) {
    val activeScene = uiState.activeScene
    val nextScene = project.scenes.getOrNull(uiState.activeSceneIndex + 1)
    val nodeBlue = Color(0xFF4F8BFF)
    val activePasses = activeScene?.resolvedEffectPasses().orEmpty()
    val scale = activeScene?.transform?.scale ?: 1f

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .testTag("studio_layout_node_video"),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SharedStudioPreviewStage(
                project = project,
                activeScene = activeScene,
                nextScene = nextScene,
                localScenePosMs = uiState.localPositionInActiveSceneMs,
                isPlaying = uiState.isPlaying,
                speedMultiplier = uiState.speedMultiplier,
                showShutterFlash = showShutterFlash,
                isLandscapeOrWide = isLandscapeOrWide,
                borderColor = nodeBlue.copy(alpha = 0.6f),
                callbacks = callbacks,
                modifier = Modifier
                    .weight(0.95f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = chromeAlpha },
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
            DarkTransportStrip(
                currentMs = uiState.currentPositionMs,
                totalMs = uiState.totalDurationMs,
                isPlaying = uiState.isPlaying,
                accent = nodeBlue,
                onTogglePlayback = callbacks.onTogglePlayback,
                onStepBackward = callbacks.onStepBackward,
                onStepForward = callbacks.onStepForward,
                onExport = callbacks.onExportVideo,
                onSeekProgress = callbacks.onSeekProgress,
                onBeginScrubbing = callbacks.onBeginScrubbing,
                onEndScrubbing = callbacks.onEndScrubbing
            )

            // Interactive Node Graph Patchbay
            Surface(
                color = Color(0xFF10131C),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF262C3F)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Outlined.Hub, null, tint = nodeBlue, modifier = Modifier.size(15.dp))
                            Text(
                                text = "Node Graph Pipeline",
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "Tap any node to inspect",
                            fontFamily = InterFontFamily,
                            fontSize = 10.sp,
                            color = Color(0xFF7D839C)
                        )
                    }

                    // Connected Node Chain
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            val yMid = size.height * 0.5f
                            drawLine(
                                color = Color(0xFF3B82F6),
                                start = Offset(size.width * 0.12f, yMid),
                                end = Offset(size.width * 0.88f, yMid),
                                strokeWidth = 3f
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NodeBoxCard(
                                title = "Media",
                                subtitle = "${project.scenes.size} clip",
                                accent = Color(0xFF34D399),
                                isSelected = uiState.selectedNodeGraphStage == "MEDIA",
                                onClick = { callbacks.onSelectNodeStage("MEDIA") },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(6.dp))
                            NodeBoxCard(
                                title = "FX Node",
                                subtitle = "${activePasses.size} pass",
                                accent = nodeBlue,
                                isSelected = uiState.selectedNodeGraphStage == "EFFECTS",
                                onClick = { callbacks.onSelectNodeStage("EFFECTS") },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(6.dp))
                            NodeBoxCard(
                                title = "Transform",
                                subtitle = String.format(Locale.US, "%.1fx", scale),
                                accent = Color(0xFFF472B6),
                                isSelected = uiState.selectedNodeGraphStage == "TRANSFORM",
                                onClick = { callbacks.onSelectNodeStage("TRANSFORM") },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(6.dp))
                            NodeBoxCard(
                                title = "Output",
                                subtitle = project.resolution,
                                accent = Color(0xFFFBBF24),
                                isSelected = uiState.selectedNodeGraphStage == "OUTPUT",
                                onClick = { callbacks.onSelectNodeStage("OUTPUT") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Active Node Quick Action Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = callbacks.onEnterEffectMode,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = nodeBlue,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("layout_add_effect_button")
                        ) {
                            Icon(Icons.Outlined.AutoAwesome, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "إضافة تأثير (+ FX Node)",
                                fontFamily = CairoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }

                        Surface(
                            color = Color(0xFF1A1E2C),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF2D334A)),
                            modifier = Modifier
                                .height(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    when (uiState.selectedNodeGraphStage) {
                                        "MEDIA" -> callbacks.onImportVideo()
                                        "TRANSFORM" -> callbacks.onOpenTransform()
                                        "OUTPUT" -> callbacks.onExportVideo()
                                        else -> callbacks.onOpenFullEffectsStudio()
                                    }
                                }
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (uiState.selectedNodeGraphStage) {
                                        "MEDIA" -> "Import Clip"
                                        "TRANSFORM" -> "Edit Transform"
                                        "OUTPUT" -> "Render MP4"
                                        else -> "Open 145 FX"
                                    },
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            if (!uiState.isInlineEffectMode) {
                Surface(
                    color = Color(0xFF0E1018),
                    border = BorderStroke(1.dp, Color(0xFF232738)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BottomRailToolButton(Icons.Outlined.VideoLibrary, "Root Media", onClick = callbacks.onImportVideo)
                        BottomRailToolButton(Icons.Outlined.GridView, "145 Gallery", onClick = callbacks.onOpenShaderGallery)
                        BottomRailToolButton(Icons.Outlined.Tune, "Transform", onClick = callbacks.onOpenTransform)
                        BottomRailToolButton(Icons.Outlined.Animation, "Motion", onClick = callbacks.onOpenMotion)
                        BottomRailToolButton(Icons.Outlined.Layers, "Sub-Layers", onClick = callbacks.onOpenLayers)
                        BottomRailToolButton(Icons.Outlined.MusicNote, "Audio", onClick = callbacks.onOpenAudio)
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun NodeBoxCard(
    title: String,
    subtitle: String,
    accent: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isSelected) accent.copy(alpha = 0.18f) else Color(0xFF171B28),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) accent else Color(0xFF2D3348)),
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = Color.White,
                maxLines = 1
            )
            Text(
                text = subtitle,
                fontFamily = InterFontFamily,
                fontSize = 9.sp,
                color = accent,
                maxLines = 1
            )
        }
    }
}

// =====================================================================
// 4. AFTER EFFECTS WORKSPACE LAYOUT (5-STATE PIXEL-FIDELITY UI)
// =====================================================================

@Composable
fun AfterEffectsWorkspaceContent(
    project: VideoProject,
    uiState: WorkspaceUiState,
    showShutterFlash: Boolean,
    callbacks: StudioLayoutCallbacks,
    modifier: Modifier = Modifier,
    chromeAlpha: Float = 1f
) {
    AfterEffectsModeScreen(
        project = project,
        uiState = uiState,
        showShutterFlash = showShutterFlash,
        callbacks = callbacks,
        modifier = modifier,
        chromeAlpha = chromeAlpha
    )
}
