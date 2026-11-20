package com.example.ui.screens.workspace

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CairoFontFamily
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.MediaImportHelper
import com.example.model.AspectRatioMode
import com.example.model.NewClipAction
import com.example.model.TimelineScene
import com.example.model.WorkspaceTab
import com.example.model.evaluateVelocityAt
import com.example.ui.components.IosAlertDialog
import com.example.ui.components.IosHairlineDivider
import com.example.ui.components.MediaUriThumbnail
import com.example.ui.components.ProjectSongAudioPlayer
import com.example.ui.components.SceneRenderStage
import com.example.ui.components.TimelineScrubber
import com.example.ui.components.VideoStarPopover
import com.example.ui.components.geometricTriangleBackground
import com.example.ui.components.iosPressable
import com.example.ui.theme.ClassicGreen
import com.example.ui.theme.ClassicRed
import com.example.ui.theme.ClassicSplitUpArrowBg
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalVideoStarColors
import com.example.ui.theme.VideoStarBlack
import com.example.ui.theme.VideoStarCameraPill
import com.example.ui.theme.VideoStarInkDark
import com.example.ui.theme.VideoStarInkMuted
import com.example.ui.theme.VideoStarIosDivider
import com.example.ui.theme.VideoStarIosTrack
import com.example.ui.theme.VideoStarLightActiveTab
import com.example.ui.theme.VideoStarMagenta
import com.example.ui.theme.VideoStarPopoverDivider
import com.example.ui.theme.VideoStarScrimDark
import com.example.ui.theme.VideoStarWhite
import com.example.viewmodel.SharedProjectViewModel
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun WorkspaceScreen(
    projectId: String,
    sharedProjectViewModel: SharedProjectViewModel,
    onCloseToHome: () -> Unit,
    onOpenRecordVideo: (projectId: String) -> Unit,
    onOpenAdjustVideo: (projectId: String, mediaUri: String, durationMs: Long, sourceFps: Int, replaceSceneIndex: Int?) -> Unit,
    onOpenAdjustPhoto: (projectId: String, mediaUri: String, replaceSceneIndex: Int?) -> Unit,
    onOpenMotion: (projectId: String, sceneIndex: Int) -> Unit,
    onOpenVelocity: (projectId: String, sceneIndex: Int) -> Unit,
    onOpenReEffect: (projectId: String, sceneIndices: List<Int>) -> Unit,
    onOpenTransform: (projectId: String, sceneIndex: Int) -> Unit,
    onOpenMix: (projectId: String, sceneIndex: Int) -> Unit,
    onOpenLayers: (projectId: String) -> Unit,
    onOpenTiming: (projectId: String, sceneIndex: Int) -> Unit
) {
    val viewModel: WorkspaceViewModel = viewModel(
        key = "workspace_$projectId",
        factory = remember(projectId) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return WorkspaceViewModel(projectId) as T
                }
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isImportingMedia by sharedProjectViewModel.isImportingMedia.collectAsStateWithLifecycle()
    val importError by sharedProjectViewModel.importError.collectAsStateWithLifecycle()
    val exportProgress by sharedProjectViewModel.exportProgress.collectAsStateWithLifecycle()
    val exportStatusMessage by sharedProjectViewModel.exportStatusMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var newTabAnchorBounds by remember { mutableStateOf<Rect?>(null) }
    var editTabAnchorBounds by remember { mutableStateOf<Rect?>(null) }
    var infoAnchorBounds by remember { mutableStateOf<Rect?>(null) }
    var addAnchorBounds by remember { mutableStateOf<Rect?>(null) }
    var thumbAnchorBounds by remember { mutableStateOf<Rect?>(null) }
    var showShutterFlash by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { pickedUri ->
        if (pickedUri != null) {
            sharedProjectViewModel.importMediaToProject(
                context = context,
                projectId = projectId,
                sourceUri = pickedUri,
                isVideo = true
            ) { imported ->
                onOpenAdjustVideo(
                    projectId,
                    imported.localUriString,
                    imported.durationMs,
                    imported.sourceFps,
                    null
                )
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { pickedUri ->
        if (pickedUri != null) {
            sharedProjectViewModel.importMediaToProject(
                context = context,
                projectId = projectId,
                sourceUri = pickedUri,
                isVideo = false
            ) { imported ->
                onOpenAdjustPhoto(projectId, imported.localUriString, null)
            }
        }
    }

    val songFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { pickedUri ->
        if (pickedUri != null) {
            sharedProjectViewModel.importAudioToProject(
                context = context,
                projectId = projectId,
                sourceUri = pickedUri,
                extractFromVideo = false
            ) { info ->
                viewModel.showMessage("تم اختيار الأغنية: ${info.title}")
            }
        }
    }

    val extractAudioVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { pickedUri ->
        if (pickedUri != null) {
            sharedProjectViewModel.importAudioToProject(
                context = context,
                projectId = projectId,
                sourceUri = pickedUri,
                extractFromVideo = true
            ) {
                viewModel.showMessage("تم استخراج الصوت من الفيديو وتعيينه للمشروع")
            }
        }
    }

    BackHandler(enabled = uiState.isTrimMode || uiState.isSplitMode || uiState.isSelectionMode) {
        when {
            uiState.isTrimMode -> viewModel.cancelTrimMode()
            uiState.isSplitMode -> viewModel.cancelSplitMode()
            uiState.isSelectionMode -> viewModel.exitSelectionMode()
        }
    }

    LaunchedEffect(uiState.snapshotFlashTrigger) {
        if (uiState.snapshotFlashTrigger > 0) {
            showShutterFlash = true
            delay(140L)
            showShutterFlash = false
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    LaunchedEffect(importError) {
        val err = importError
        if (err != null) {
            snackbarHostState.showSnackbar(err)
            sharedProjectViewModel.clearImportError()
        }
    }

    LaunchedEffect(exportStatusMessage) {
        val msg = exportStatusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            sharedProjectViewModel.clearExportStatus()
        }
    }

    val project = uiState.project
    if (project == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .geometricTriangleBackground(),
            contentAlignment = Alignment.Center
        ) {
            Button(onClick = onCloseToHome) {
                Text(stringResource(R.string.btn_close))
            }
        }
        return
    }

    // Sync project song playback with global timeline position
    ProjectSongAudioPlayer(
        songUriString = project.audioConfig.songUriString,
        isPlaying = uiState.isPlaying,
        timelinePositionMs = uiState.currentPositionMs,
        songVolume = project.audioConfig.songVolume
    )

    val activeScene = uiState.activeScene
    val nextScene = project.scenes.getOrNull(uiState.activeSceneIndex + 1)
    val localScenePosMs = uiState.localPositionInActiveSceneMs

    Box(
        modifier = Modifier
            .fillMaxSize()
            .geometricTriangleBackground()
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                when {
                    uiState.isTrimMode -> {
                        TrimBottomBar(
                            onCancelClick = viewModel::cancelTrimMode,
                            onConfirmClick = viewModel::confirmTrimMode
                        )
                    }
                    uiState.isSplitMode -> {
                        SplitBottomBar(
                            onCancelClick = viewModel::cancelSplitMode,
                            onUpArrowClick = viewModel::toggleSceneStrip,
                            onSplitClick = viewModel::confirmSplitScene
                        )
                    }
                    else -> {
                        WorkspaceBottomBar(
                            activeTab = uiState.activeTab,
                            onAudioClick = viewModel::openAudioDialog,
                            onTabSelected = viewModel::onTabSelected,
                            onEditBoundsChanged = { editTabAnchorBounds = it },
                            onNewBoundsChanged = { newTabAnchorBounds = it },
                            onInfoBoundsChanged = { infoAnchorBounds = it },
                            onAddBoundsChanged = { addAnchorBounds = it },
                            onInfoClick = viewModel::toggleInfoPopover,
                            onAddClick = viewModel::toggleAddPopover
                        )
                    }
                }
            }
        ) { innerPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding()
            ) {
                val isLandscapeOrWide = maxWidth > 620.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Clean Video Star iOS top bar: circular dark X close button only (no intrusive header pill)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(48.dp)
                                .padding(6.dp)
                                .clip(CircleShape)
                                .background(VideoStarScrimDark)
                                .iosPressable {
                                    viewModel.saveBeforeClose()
                                    onCloseToHome()
                                }
                                .testTag("workspace_close_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.cd_close_workspace),
                                tint = VideoStarWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Centered Preview Canvas Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val ratioModifier = when (project.aspectRatio) {
                        AspectRatioMode.SQUARE -> Modifier
                            .fillMaxWidth(if (isLandscapeOrWide) 0.55f else 0.98f)
                            .aspectRatio(1f)
                        AspectRatioMode.PORTRAIT -> Modifier
                            .fillMaxHeight(0.95f)
                            .aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
                        AspectRatioMode.PORTRAIT_3_4 -> Modifier
                            .fillMaxHeight(0.92f)
                            .aspectRatio(3f / 4f, matchHeightConstraintsFirst = true)
                        AspectRatioMode.PORTRAIT_4_5 -> Modifier
                            .fillMaxHeight(0.92f)
                            .aspectRatio(4f / 5f, matchHeightConstraintsFirst = true)
                        AspectRatioMode.LANDSCAPE_4_3 -> Modifier
                            .fillMaxWidth(1f)
                            .aspectRatio(4f / 3f)
                        AspectRatioMode.LANDSCAPE -> Modifier
                            .fillMaxWidth(1f)
                            .aspectRatio(16f / 9f)
                    }

                    Box(
                        modifier = ratioModifier
                            .shadow(10.dp, RoundedCornerShape(4.dp))
                            .clip(RoundedCornerShape(4.dp))
                            .background(VideoStarBlack)
                            .border(1.5.dp, VideoStarBlack, RoundedCornerShape(4.dp))
                            .clickable(onClick = viewModel::togglePlayback)
                            .testTag("workspace_preview_canvas")
                    ) {
                        val hasMediaScene = activeScene != null && (activeScene.clip != null || activeScene.isMultiLayerComposite)
                        if (!hasMediaScene) {
                            if (!uiState.isPlaying) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(R.string.cd_play_pause),
                                    tint = VideoStarWhite,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(56.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(10.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(VideoStarMagenta.copy(alpha = 0.85f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.Pause,
                                            contentDescription = stringResource(R.string.cd_play_pause),
                                            tint = VideoStarWhite,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "${project.frameRate} fps",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = VideoStarWhite
                                        )
                                    }
                                }
                            }
                        } else {
                            SceneRenderStage(
                                scene = activeScene,
                                nextScene = nextScene,
                                localPositionMs = localScenePosMs,
                                isPlaying = uiState.isPlaying,
                                clipVolume = project.audioConfig.clipVolume,
                                speedMultiplier = uiState.speedMultiplier,
                                useLiveVideoPlayer = true,
                                contentDescription = project.title,
                                onVideoPositionChanged = viewModel::syncPositionFromLocalVideo,
                                onScenePlaybackEnded = viewModel::onScenePlaybackEnded,
                                modifier = Modifier.fillMaxSize()
                            )

                            val localProg = (localScenePosMs.toFloat() / activeScene.effectiveDurationMs.toFloat()).coerceIn(0f, 1f)
                            val liveSpeed = evaluateVelocityAt(localProg, activeScene.velocityPoints, activeScene.isVelocitySmooth) *
                                uiState.speedMultiplier

                            if (!uiState.isPlaying) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(R.string.cd_play_pause),
                                    tint = VideoStarWhite,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(56.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(10.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(VideoStarMagenta.copy(alpha = 0.85f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.Pause,
                                            contentDescription = null,
                                            tint = VideoStarWhite,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = String.format(Locale.US, "%.2fx", liveSpeed),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = VideoStarWhite
                                        )
                                    }
                                }
                            }
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = showShutterFlash,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.82f)))
                        }
                    }
                }

                // Multi-Select Controls Bar when Select Mode is active
                if (uiState.isSelectionMode && project.scenes.isNotEmpty()) {
                    MultiSceneSelectActionBar(
                        selectedCount = uiState.selectedSceneIndices.size,
                        onSelectAll = viewModel::selectAllScenes,
                        onSelectRange = viewModel::selectRangeScenes,
                        onSelectNone = viewModel::clearSceneSelection,
                        onBatchReEffect = {
                            val targets = uiState.selectedSceneIndices.sorted()
                            if (targets.isNotEmpty()) {
                                onOpenReEffect(projectId, targets)
                            }
                        },
                        onBatchMerge = viewModel::mergeSelectedScenes,
                        onBatchDelete = viewModel::requestBatchDeleteScenes,
                        onDone = viewModel::exitSelectionMode
                    )
                }

                // Scene Thumbnails Strip (shown when > 1 scene or Selection Mode or Split Mode)
                if ((uiState.showSceneStrip || uiState.isSelectionMode || project.scenes.size > 1) && project.scenes.isNotEmpty()) {
                    SceneThumbnailsStrip(
                        scenes = project.scenes,
                        activeSceneIndex = uiState.activeSceneIndex,
                        isSelectionMode = uiState.isSelectionMode,
                        selectedIndices = uiState.selectedSceneIndices,
                        onSceneSelected = { idx ->
                            if (uiState.isSelectionMode) viewModel.toggleSceneSelection(idx)
                            else viewModel.selectScene(idx)
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                // Interactive Timeline Trim Bar (Dual Red/Green Handles) or Scrubber
                if (uiState.isTrimMode) {
                    InteractiveTimelineTrimBar(
                        startMs = uiState.trimStartMs,
                        endMs = uiState.trimEndMs,
                        maxMs = uiState.trimMaxMs,
                        onRangeChange = viewModel::updateTrimRange
                    )
                } else {
                    // Scrubber & Timeline Bar with gray PNG camera snapshot button directly above '>'
                    TimelineScrubber(
                        currentPositionMs = uiState.currentPositionMs,
                        durationMs = uiState.totalDurationMs,
                        onSeekProgress = viewModel::seekToProgress,
                        onStepBackward = viewModel::stepBackward,
                        onStepForward = viewModel::stepForward,
                        sceneBoundaryProgresses = uiState.sceneBoundaryProgresses,
                        onThumbBoundsChanged = { thumbAnchorBounds = it },
                        onSnapshotClick = {
                            sharedProjectViewModel.captureSnapshotToGallery(
                                context = context,
                                project = project,
                                scene = activeScene,
                                localPositionMs = localScenePosMs
                            ) { savedMsg ->
                                viewModel.triggerShutterFlash(savedMsg)
                            }
                        },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
        }
    }

    // 1. Timeline Split Popover anchored directly above the white circular thumb
    VideoStarPopover(
        expanded = uiState.isSplitMode,
        onDismissRequest = viewModel::cancelSplitMode,
        anchorBounds = thumbAnchorBounds,
        width = 264.dp,
        focusable = false,
        dismissOnClickOutside = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "سيتم تقسيم المشهد في موضع المؤشر هذا إلى مشهدين منفصلين.",
                fontFamily = CairoFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = Color(0xFF333333),
                textAlign = TextAlign.Center
            )
            HorizontalDivider(thickness = 1.dp, color = VideoStarPopoverDivider)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = viewModel::confirmSplitScene)
                    .testTag("split_popover_confirm_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "تقسيم المشهد",
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = VideoStarMagenta
                )
            }
        }
    }

    // 2. Edit 7-Row Arabic Popover anchored above "تحرير" tab
    EditActionsPopover(
        expanded = uiState.isEditPopoverVisible && !uiState.isSplitMode && !uiState.isTrimMode,
        anchorBounds = editTabAnchorBounds,
        hasNextScene = uiState.activeSceneIndex < project.scenes.lastIndex,
        hasCopiedScene = uiState.copiedScene != null,
        canUndo = uiState.canUndo,
        canRedo = uiState.canRedo,
        onDismiss = viewModel::dismissAllPopovers,
        onCopy = viewModel::copyActiveScene,
        onPaste = viewModel::pasteCopiedScene,
        onUndo = viewModel::undoTimelineEdit,
        onRedo = viewModel::redoTimelineEdit,
        onMerge = viewModel::mergeActiveSceneWithNext,
        onSplit = viewModel::enterSplitMode,
        onDelete = viewModel::requestDeleteActiveScene,
        onShareClip = {
            viewModel.dismissAllPopovers()
            sharedProjectViewModel.exportSingleSceneToShare(
                context = context,
                projectId = projectId,
                sceneIndex = uiState.activeSceneIndex
            ) { _, shareUri ->
                if (shareUri != null) {
                    runCatching {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "video/mp4"
                            putExtra(Intent.EXTRA_STREAM, shareUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "شارك مقطع الفيديو"))
                    }
                }
            }
        }
    )

    // 3. New 10-Action Arabic 2x5 Popover Grid anchored above "جديد" tab
    VideoStarPopover(
        expanded = uiState.isNewPopoverVisible && !uiState.isSplitMode,
        onDismissRequest = viewModel::dismissAllPopovers,
        anchorBounds = newTabAnchorBounds,
        width = 308.dp
    ) {
        val leftColumn = listOf(
            NewClipAction.RECORD_VIDEO,
            NewClipAction.IMPORT_VIDEO,
            NewClipAction.IMPORT_PHOTO,
            NewClipAction.MOTION,
            NewClipAction.VELOCITY
        )
        val rightColumn = listOf(
            NewClipAction.RE_EFFECT,
            NewClipAction.TRANSFORM,
            NewClipAction.MIX,
            NewClipAction.MULTI_LAYER,
            NewClipAction.TIMING
        )

        val handleNewAction: (NewClipAction) -> Unit = { action ->
            viewModel.onNewActionSelected(
                action = action,
                onRecordVideo = { onOpenRecordVideo(projectId) },
                onImportVideo = {
                    videoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
                onImportPhoto = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onOpenMotion = { sceneIdx -> onOpenMotion(projectId, sceneIdx) },
                onOpenVelocity = { sceneIdx -> onOpenVelocity(projectId, sceneIdx) },
                onOpenReEffect = { indices -> onOpenReEffect(projectId, indices) },
                onOpenTransform = { sceneIdx -> onOpenTransform(projectId, sceneIdx) },
                onOpenMix = { sceneIdx -> onOpenMix(projectId, sceneIdx) },
                onOpenLayers = { onOpenLayers(projectId) },
                onOpenTiming = { _ ->
                    viewModel.openTimingWithClipModeCheck { sceneIdx ->
                        onOpenTiming(projectId, sceneIdx)
                    }
                }
            )
        }

        for (row in 0 until 5) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val leftAction = leftColumn[row]
                val rightAction = rightColumn[row]

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .iosPressable { handleNewAction(leftAction) }
                        .testTag("new_action_${leftAction.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = leftAction.label,
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = VideoStarMagenta,
                        textAlign = TextAlign.Center
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(VideoStarPopoverDivider)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .iosPressable { handleNewAction(rightAction) }
                        .testTag("new_action_${rightAction.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = rightAction.label,
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = VideoStarMagenta,
                        textAlign = TextAlign.Center
                    )
                }
            }
            if (row < 4) {
                HorizontalDivider(thickness = 1.dp, color = VideoStarPopoverDivider)
            }
        }
    }

    // 4. Info & Export Video Popover anchored above (i)
    ProjectInfoPopover(
        expanded = uiState.isInfoPopoverVisible && !uiState.isSplitMode,
        anchorBounds = infoAnchorBounds,
        project = project,
        onDismiss = viewModel::dismissAllPopovers,
        onExportVideo = {
            viewModel.dismissAllPopovers()
            sharedProjectViewModel.exportProjectVideo(context, projectId)
        }
    )

    // 5. Quick Add Popover anchored above "إضافة"
    QuickAddPopover(
        expanded = uiState.isAddPopoverVisible && !uiState.isSplitMode,
        anchorBounds = addAnchorBounds,
        onDismiss = viewModel::dismissAllPopovers,
        onRecordVideo = {
            viewModel.dismissAllPopovers()
            onOpenRecordVideo(projectId)
        },
        onImportVideo = {
            viewModel.dismissAllPopovers()
            videoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
        },
        onImportPhoto = {
            viewModel.dismissAllPopovers()
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    )

    // Dialogs (Arabic Alerts & Config Modals)
    when (val dialog = uiState.activeDialog) {
        WorkspaceDialogState.AudioManager -> {
            AudioManagerDialog(
                audioConfig = project.audioConfig,
                onUpdateConfig = viewModel::updateAudioConfig,
                onPickSongFile = {
                    songFilePickerLauncher.launch(arrayOf("audio/*"))
                },
                onExtractFromVideo = {
                    extractAudioVideoLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
                onDismiss = viewModel::dismissDialog
            )
        }
        is WorkspaceDialogState.TrimActiveScene -> {
            TrimActiveSceneDialog(
                scene = dialog.scene,
                onSave = { startMs, endMs ->
                    viewModel.saveTrimmedScene(dialog.sceneIndex, startMs, endMs)
                },
                onDismiss = viewModel::dismissDialog
            )
        }
        is WorkspaceDialogState.ConfirmDeleteScene -> {
            IosAlertDialog(
                title = "حذف المشهد",
                message = "هل تريد حذف \"${dialog.sceneTitle}\" من التايم لاين؟",
                confirmText = "حذف",
                confirmColor = VideoStarMagenta,
                dismissText = "إلغاء",
                dismissColor = VideoStarInkDark,
                onConfirm = { viewModel.confirmDeleteActiveScene(dialog.sceneIndex) },
                onDismiss = viewModel::dismissDialog,
                confirmTestTag = "delete_scene_confirm",
                dismissTestTag = "delete_scene_cancel"
            )
        }
        WorkspaceDialogState.ConfirmBatchDelete -> {
            IosAlertDialog(
                title = "حذف المشاهد",
                message = "هل تريد حذف ${uiState.selectedSceneIndices.size} من المشاهد المحددة؟",
                confirmText = "حذف",
                confirmColor = VideoStarMagenta,
                dismissText = "إلغاء",
                dismissColor = VideoStarInkDark,
                onConfirm = viewModel::confirmBatchDeleteScenes,
                onDismiss = viewModel::dismissDialog,
                confirmTestTag = "batch_delete_confirm",
                dismissTestTag = "batch_delete_cancel"
            )
        }
        is WorkspaceDialogState.MixNoNextSceneAlert -> {
            IosAlertDialog(
                title = "مزج المشاهد",
                message = "اختر مشهداً يتبعه مشهد آخر لتتمكن من دمجهما.",
                confirmText = "حسناً",
                confirmColor = VideoStarMagenta,
                onConfirm = viewModel::dismissDialog,
                onDismiss = viewModel::dismissDialog,
                confirmTestTag = "mix_alert_ok"
            )
        }
        is WorkspaceDialogState.ClipModeFixedFlexible -> {
            ClipModeFixedFlexibleDialog(
                onSelectFixed = viewModel::applyFixedClipMode,
                onSelectFlexible = viewModel::applyFlexibleClipMode,
                onCancel = viewModel::dismissClipModeDialog
            )
        }
        null -> Unit
    }

    exportProgress?.let { progress ->
        ExportProgressDialog(progress = progress)
    }

    if (isImportingMedia) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = VideoStarWhite,
                shape = RoundedCornerShape(14.dp),
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = ClassicRed)
                    Text("جارٍ تجهيز الملف للمشروع...", color = VideoStarInkDark, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
internal fun DualColorSplitText(fontSizeSp: Float) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = ClassicRed, fontWeight = FontWeight.Bold)) {
                append("تقسيم ")
            }
            withStyle(SpanStyle(color = ClassicGreen, fontWeight = FontWeight.Bold)) {
                append("المشهد")
            }
        },
        fontFamily = CairoFontFamily,
        fontSize = fontSizeSp.sp
    )
}

@Composable
private fun MultiSceneSelectActionBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onSelectRange: () -> Unit,
    onSelectNone: () -> Unit,
    onBatchReEffect: () -> Unit,
    onBatchMerge: () -> Unit,
    onBatchDelete: () -> Unit,
    onDone: () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(
            color = VideoStarWhite,
            shadowElevation = 2.dp,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, VideoStarIosDivider),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SelectPillButton("تحديد الكل", ClassicGreen, onSelectAll, "select_all_button")
                SelectPillButton("تحديد نطاق", ClassicGreen, onSelectRange, "select_range_button")
                SelectPillButton("بلا", ClassicRed, onSelectNone, "select_none_button")
                if (selectedCount > 0) {
                    SelectPillButton("مؤثرات ($selectedCount)", ClassicRed, onBatchReEffect, "select_batch_reeffect")
                    if (selectedCount >= 2) {
                        SelectPillButton("دمج", ClassicGreen, onBatchMerge, "select_batch_merge")
                    }
                    SelectPillButton("حذف", ClassicRed, onBatchDelete, "select_batch_delete")
                }
                SelectPillButton("تم", VideoStarInkDark, onDone, "select_done_button")
            }
        }
    }
}

@Composable
private fun SelectPillButton(
    label: String,
    color: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SceneThumbnailsStrip(
    scenes: List<TimelineScene>,
    activeSceneIndex: Int,
    isSelectionMode: Boolean,
    selectedIndices: Set<Int>,
    onSceneSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            scenes.forEachIndexed { index, scene ->
                val isSelected = if (isSelectionMode) index in selectedIndices else index == activeSceneIndex
                val borderColor = when {
                    isSelectionMode && isSelected -> ClassicGreen
                    isSelected -> ClassicRed
                    else -> VideoStarIosDivider
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) VideoStarWhite else VideoStarWhite.copy(alpha = 0.72f))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onSceneSelected(index) }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("scene_strip_item_$index"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box {
                        MediaUriThumbnail(
                            uriString = scene.mediaUriString,
                            isVideo = scene.isVideo,
                            contentDescription = scene.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        if (isSelectionMode && isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = ClassicGreen,
                                modifier = Modifier
                                    .size(14.dp)
                                    .align(Alignment.TopEnd)
                                    .background(VideoStarWhite, CircleShape)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = scene.title,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = if (isSelected) borderColor else VideoStarInkDark
                        )
                        Text(
                            text = String.format(Locale.US, "%.1fs", scene.effectiveDurationMs / 1000f),
                            fontFamily = InterFontFamily,
                            fontSize = 10.sp,
                            color = VideoStarInkMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrimBottomBar(
    onCancelClick: () -> Unit,
    onConfirmClick: () -> Unit
) {
    val extraColors = LocalVideoStarColors.current
    Surface(
        color = extraColors.toolbarBackground,
        shadowElevation = 4.dp
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(60.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .iosPressable(onClick = onCancelClick)
                        .padding(horizontal = 10.dp)
                        .testTag("trim_bar_cancel_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "إلغاء",
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = ClassicRed
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "قص المشهد",
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = VideoStarInkDark
                )

                Spacer(modifier = Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .iosPressable(onClick = onConfirmClick)
                        .padding(horizontal = 10.dp)
                        .testTag("trim_bar_confirm_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "تأكيد القص",
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = ClassicGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun SplitBottomBar(
    onCancelClick: () -> Unit,
    onUpArrowClick: () -> Unit,
    onSplitClick: () -> Unit
) {
    val extraColors = LocalVideoStarColors.current
    Surface(
        color = extraColors.toolbarBackground,
        shadowElevation = 4.dp
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(60.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .iosPressable(onClick = onCancelClick)
                        .padding(horizontal = 10.dp)
                        .testTag("split_bar_cancel_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "إلغاء",
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = ClassicRed
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(ClassicSplitUpArrowBg)
                        .iosPressable(onClick = onUpArrowClick)
                        .testTag("split_bar_up_arrow_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = "Toggle Scenes",
                        tint = VideoStarWhite,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .iosPressable(onClick = onSplitClick)
                        .padding(horizontal = 10.dp)
                        .testTag("split_bar_confirm_button"),
                    contentAlignment = Alignment.Center
                ) {
                    DualColorSplitText(fontSizeSp = 16f)
                }
            }
        }
    }
}

@Composable
private fun WorkspaceBottomBar(
    activeTab: WorkspaceTab,
    onAudioClick: () -> Unit,
    onTabSelected: (WorkspaceTab) -> Unit,
    onEditBoundsChanged: (Rect) -> Unit,
    onNewBoundsChanged: (Rect) -> Unit,
    onInfoBoundsChanged: (Rect) -> Unit,
    onAddBoundsChanged: (Rect) -> Unit,
    onInfoClick: () -> Unit,
    onAddClick: () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Centered light-gray segmented tab bar with 3 tabs: "تحرير" (left), "اختر" (center), "جديد" (right)
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .fillMaxWidth(0.76f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(VideoStarIosTrack)
                    .padding(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tabs = WorkspaceTab.entries
                    tabs.forEachIndexed { index, tab ->
                        val isSelected = activeTab == tab
                        val showRightDivider =
                            index < tabs.lastIndex &&
                                !isSelected &&
                                tabs[index + 1] != activeTab

                        val baseModifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(
                                if (isSelected) {
                                    Modifier
                                        .shadow(2.dp, RoundedCornerShape(7.dp))
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(VideoStarLightActiveTab)
                                } else {
                                    Modifier.clip(RoundedCornerShape(7.dp))
                                }
                            )
                            .iosPressable { onTabSelected(tab) }
                            .testTag("workspace_tab_${tab.name.lowercase()}")

                        val trackedModifier = when (tab) {
                            WorkspaceTab.EDIT -> baseModifier.onGloballyPositioned {
                                onEditBoundsChanged(it.boundsInWindow())
                            }
                            WorkspaceTab.NEW -> baseModifier.onGloballyPositioned {
                                onNewBoundsChanged(it.boundsInWindow())
                            }
                            WorkspaceTab.SELECT -> baseModifier
                        }

                        Box(
                            modifier = trackedModifier,
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.label,
                                fontFamily = CairoFontFamily,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = VideoStarMagenta,
                                maxLines = 1
                            )

                            if (showRightDivider) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .width(1.dp)
                                        .height(18.dp)
                                        .background(VideoStarIosDivider)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom row: "صوت" (left, magenta), (i) info icon (center, magenta), "إضافة" (right, magenta)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .iosPressable(onClick = onAudioClick)
                        .padding(horizontal = 12.dp)
                        .testTag("workspace_audio_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.action_audio),
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = VideoStarMagenta
                    )
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .iosPressable(onClick = onInfoClick)
                        .onGloballyPositioned { onInfoBoundsChanged(it.boundsInWindow()) }
                        .testTag("workspace_info_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.cd_info),
                        tint = VideoStarMagenta,
                        modifier = Modifier.size(25.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .iosPressable(onClick = onAddClick)
                        .onGloballyPositioned { onAddBoundsChanged(it.boundsInWindow()) }
                        .padding(horizontal = 12.dp)
                        .testTag("workspace_add_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.action_add),
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = VideoStarMagenta
                    )
                }
            }
        }
    }
}
