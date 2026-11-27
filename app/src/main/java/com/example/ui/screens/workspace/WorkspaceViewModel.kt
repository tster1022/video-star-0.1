package com.example.ui.screens.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.VideoStarRepository
import com.example.engine.render.StudioRenderErrorBoundary
import com.example.engine.render.StudioRenderMemoryCoordinator
import com.example.engine.render.StudioScrubThrottleController
import com.example.model.AeLayerKind
import com.example.model.AspectRatioMode
import com.example.model.EditorLayer
import com.example.model.EffectCatalog
import com.example.model.EffectKeyframe
import com.example.model.LayerKeyframe
import com.example.model.MediaClip
import com.example.model.NewClipAction
import com.example.model.PARAM_KEY_INTENSITY
import com.example.model.ParameterKeyframe
import com.example.model.ProjectAudioConfig
import com.example.model.ShaderEffectPass
import com.example.model.StudioLayoutMode
import com.example.model.TimelineScene
import com.example.model.TimingMode
import com.example.model.VideoProject
import com.example.model.WorkspaceTab
import com.example.model.evaluateVelocityAt
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

enum class ClipModeTrigger {
    TRIM,
    TIMING
}

sealed interface WorkspaceDialogState {
    data object AudioManager : WorkspaceDialogState
    data class TrimActiveScene(val sceneIndex: Int, val scene: TimelineScene) : WorkspaceDialogState
    data class ConfirmDeleteScene(val sceneIndex: Int, val sceneTitle: String) : WorkspaceDialogState
    data object ConfirmBatchDelete : WorkspaceDialogState
    data class MixNoNextSceneAlert(val message: String) : WorkspaceDialogState
    data class ClipModeFixedFlexible(
        val sceneIndex: Int,
        val trigger: ClipModeTrigger
    ) : WorkspaceDialogState
}

data class SceneWindow(
    val index: Int,
    val scene: TimelineScene,
    val startMs: Long,
    val endMs: Long
)

data class WorkspaceUiState(
    val project: VideoProject? = null,
    val currentPositionMs: Long = 0L, // Global timeline position across all scenes
    val isPlaying: Boolean = false,
    val activeTab: WorkspaceTab = WorkspaceTab.EDIT,
    val isEditPopoverVisible: Boolean = false,
    val isNewPopoverVisible: Boolean = false,
    val isInfoPopoverVisible: Boolean = false,
    val isAddPopoverVisible: Boolean = false,
    val isSplitMode: Boolean = false,
    val isTrimMode: Boolean = false,
    val trimSceneIndex: Int = 0,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 3_000L,
    val trimMaxMs: Long = 3_000L,
    val trimIsFixedDuration: Boolean = false,
    val copiedScene: TimelineScene? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isSelectionMode: Boolean = false,
    val selectedSceneIndices: Set<Int> = emptySet(),
    val activeSceneIndex: Int = 0,
    val showSceneStrip: Boolean = false,
    val activeDialog: WorkspaceDialogState? = null,
    val isLayoutModalVisible: Boolean = false,
    val isInlineEffectMode: Boolean = false,
    val activeEffectFilterCategory: String = "All",
    val selectedNodeGraphStage: String = "EFFECTS",
    val selectedAeLayerId: String? = null,
    val snapshotFlashTrigger: Int = 0,
    val speedMultiplier: Float = 1f,
    val isScrubbing: Boolean = false,
    val snackbarMessage: String? = null
) {
    val studioLayout: StudioLayoutMode
        get() = project?.studioLayout ?: StudioLayoutMode.NOVA

    val aeLayers: List<EditorLayer>
        get() = project?.aeLayers.orEmpty()

    val selectedAeLayer: EditorLayer?
        get() = selectedAeLayerId?.let { id -> aeLayers.firstOrNull { it.id == id } }

    /**
     * Strict Mode Rule:
     * When true (Editing / Effect Mode active), the top-left Layout Switcher trigger MUST stay hidden.
     */
    val isInEditingOrEffectMode: Boolean
        get() = isInlineEffectMode || isTrimMode || isSplitMode || isSelectionMode || activeDialog != null
    val sceneWindows: List<SceneWindow>
        get() {
            val scenes = project?.scenes ?: return emptyList()
            val result = ArrayList<SceneWindow>(scenes.size)
            var cursor = 0L
            scenes.forEachIndexed { idx, sc ->
                val dur = sc.effectiveDurationMs.coerceAtLeast(200L)
                result.add(SceneWindow(idx, sc, cursor, cursor + dur))
                cursor += dur
            }
            return result
        }

    val totalDurationMs: Long
        get() {
            if (studioLayout == StudioLayoutMode.AFTER_EFFECTS && aeLayers.isNotEmpty()) {
                return aeLayers.maxOf { (it.startTimeMs + it.effectiveLayerDurationMs).coerceAtLeast(500L) }
            }
            return sceneWindows.lastOrNull()?.endMs
                ?: project?.durationMs?.takeIf { it > 0L }
                ?: 10_020L
        }

    val activeWindow: SceneWindow?
        get() {
            val windows = sceneWindows
            if (windows.isEmpty()) return null
            return windows.firstOrNull { currentPositionMs >= it.startMs && currentPositionMs < it.endMs }
                ?: windows.getOrNull(activeSceneIndex.coerceIn(0, windows.lastIndex))
                ?: windows.last()
        }

    val activeScene: TimelineScene?
        get() = activeWindow?.scene ?: project?.scenes?.getOrNull(activeSceneIndex)

    val localPositionInActiveSceneMs: Long
        get() {
            val win = activeWindow ?: return currentPositionMs
            return (currentPositionMs - win.startMs).coerceIn(0L, win.scene.effectiveDurationMs)
        }

    val sceneBoundaryProgresses: List<Float>
        get() {
            val windows = sceneWindows
            val total = totalDurationMs.toFloat().coerceAtLeast(1f)
            if (windows.size <= 1) return emptyList()
            return windows.dropLast(1).map { (it.endMs.toFloat() / total).coerceIn(0.01f, 0.99f) }
        }
}

class WorkspaceViewModel(private val projectId: String) : ViewModel() {
    private val _uiState = MutableStateFlow(
        WorkspaceUiState(project = VideoStarRepository.getProjectById(projectId))
    )
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null
    private val playbackSessionToken = AtomicLong(0L)
    val scrubController = StudioScrubThrottleController(minFrameIntervalMs = 16L)

    fun currentPlaybackSessionId(): Long = playbackSessionToken.get()
    private data class WorkspaceHistorySnapshot(
        val scenes: List<TimelineScene>,
        val aeLayers: List<EditorLayer>
    )
    private val undoStack = ArrayDeque<WorkspaceHistorySnapshot>()
    private val redoStack = ArrayDeque<WorkspaceHistorySnapshot>()
    private var pendingTimingNavigation: ((Int) -> Unit)? = null

    private fun pushUndoSnapshot() {
        val proj = _uiState.value.project ?: return
        undoStack.addLast(WorkspaceHistorySnapshot(proj.scenes, proj.aeLayers))
        while (undoStack.size > 30) undoStack.removeFirst()
        redoStack.clear()
        _uiState.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = false) }
    }

    init {
        if (VideoStarRepository.getProjectById(projectId)?.studioLayout == StudioLayoutMode.AFTER_EFFECTS) {
            VideoStarRepository.ensureAeLayersHydrated(projectId)
        }
        VideoStarRepository.projects
            .onEach { projects ->
                val match = projects.firstOrNull { it.id == projectId }
                _uiState.update { state ->
                    val maxSceneIdx = ((match?.scenes?.size ?: 1) - 1).coerceAtLeast(0)
                    val safeMaxDur = (match?.totalTimelineDurationMs ?: 3000L).coerceAtLeast(500L)
                    val safePos = state.currentPositionMs.coerceIn(0L, safeMaxDur)
                    val nextActiveIdx = resolveSceneIndexAt(match, safePos, state.activeSceneIndex).coerceIn(0, maxSceneIdx)
                    val validSelectedAe = state.selectedAeLayerId?.takeIf { id ->
                        match?.aeLayers?.any { it.id == id } == true
                    }
                    state.copy(
                        project = match,
                        currentPositionMs = safePos,
                        activeSceneIndex = nextActiveIdx,
                        selectedAeLayerId = validSelectedAe,
                        selectedSceneIndices = state.selectedSceneIndices.filter { it <= maxSceneIdx }.toSet(),
                        showSceneStrip = state.showSceneStrip || state.isSelectionMode || ((match?.scenes?.size ?: 0) > 1)
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun resolveSceneIndexAt(project: VideoProject?, globalMs: Long, fallback: Int): Int {
        val scenes = project?.scenes ?: return 0
        if (scenes.isEmpty()) return 0
        var cursor = 0L
        scenes.forEachIndexed { idx, sc ->
            val end = cursor + sc.effectiveDurationMs.coerceAtLeast(200L)
            if (globalMs < end) return idx
            cursor = end
        }
        return scenes.lastIndex.coerceAtLeast(0)
    }

    private fun cancelPlaybackLoopOnly(): Long {
        val nextToken = playbackSessionToken.incrementAndGet()
        playbackJob?.cancel()
        playbackJob = null
        return nextToken
    }

    fun pausePlayback() {
        cancelPlaybackLoopOnly()
        if (_uiState.value.isPlaying) {
            _uiState.update { it.copy(isPlaying = false) }
        }
    }

    fun beginScrubbing() {
        cancelPlaybackLoopOnly()
        scrubController.beginScrub(_uiState.value.currentPositionMs)
        _uiState.update { it.copy(isPlaying = false, isScrubbing = true) }
    }

    fun endScrubbing() {
        scrubController.endScrub()
        if (_uiState.value.isScrubbing) {
            _uiState.update { it.copy(isScrubbing = false) }
        }
    }

    fun togglePlayback() {
        val currentState = _uiState.value
        val nextPlaying = !currentState.isPlaying
        val sessionToken = cancelPlaybackLoopOnly()
        scrubController.endScrub()

        if (!nextPlaying) {
            _uiState.update { it.copy(isPlaying = false, isScrubbing = false) }
            return
        }

        val atEnd = currentState.currentPositionMs >= (currentState.totalDurationMs - 40L).coerceAtLeast(100L)
        val startState = if (atEnd) {
            _uiState.update {
                it.copy(
                    currentPositionMs = 0L,
                    activeSceneIndex = 0,
                    isPlaying = true,
                    isScrubbing = false
                )
            }
            _uiState.value
        } else {
            _uiState.update { it.copy(isPlaying = true, isScrubbing = false) }
            _uiState.value
        }

        val activeWin = startState.activeWindow
        val isAeMultiLayer = startState.studioLayout == StudioLayoutMode.AFTER_EFFECTS && startState.aeLayers.isNotEmpty()
        val isLiveForwardVideo = !isAeMultiLayer &&
            activeWin != null &&
            activeWin.scene.isVideo &&
            !activeWin.scene.isReversed &&
            !activeWin.scene.timing.freezeFrameEnabled &&
            !activeWin.scene.isMultiLayerComposite &&
            activeWin.scene.clip is MediaClip.Video

        if (!isLiveForwardVideo) {
            startSoftwareTimelineLoop(sessionToken)
        }
    }

    private fun startSoftwareTimelineLoop(sessionToken: Long) {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            var lastTickNanos = System.nanoTime()
            while (
                isActive &&
                playbackSessionToken.get() == sessionToken &&
                _uiState.value.isPlaying
            ) {
                val fps = (_uiState.value.project?.frameRate ?: 30).coerceIn(24, 60)
                val frameTickMs = (1000L / fps).coerceIn(16L, 42L)
                delay(frameTickMs)

                if (!isActive || playbackSessionToken.get() != sessionToken || !_uiState.value.isPlaying) {
                    break
                }

                val nowNanos = System.nanoTime()
                val elapsedMs = ((nowNanos - lastTickNanos) / 1_000_000L).coerceIn(8L, 50L)
                lastTickNanos = nowNanos

                val shouldBreak = StudioRenderErrorBoundary.safeEvaluate("playback_loop_tick", true) {
                    val current = _uiState.value
                    val isAeMultiLayer = current.studioLayout == StudioLayoutMode.AFTER_EFFECTS && current.aeLayers.isNotEmpty()
                    val win = current.activeWindow
                    val step = if (!isAeMultiLayer && win != null) {
                        val scene = win.scene
                        val localPos = (current.currentPositionMs - win.startMs).coerceAtLeast(0L)
                        val localProgress = (localPos.toFloat() / scene.effectiveDurationMs.toFloat()).coerceIn(0f, 1f)
                        val curveSpeed = evaluateVelocityAt(localProgress, scene.velocityPoints, scene.isVelocitySmooth)
                        (elapsedMs * current.speedMultiplier * curveSpeed).toLong().coerceAtLeast(8L)
                    } else {
                        (elapsedMs * current.speedMultiplier).toLong().coerceAtLeast(8L)
                    }
                    val nextGlobal = current.currentPositionMs + step

                    if (nextGlobal >= current.totalDurationMs) {
                        if (isAeMultiLayer) {
                            _uiState.update {
                                it.copy(
                                    currentPositionMs = 0L,
                                    activeSceneIndex = 0,
                                    isPlaying = true
                                )
                            }
                            false
                        } else {
                            _uiState.update {
                                it.copy(
                                    currentPositionMs = 0L,
                                    activeSceneIndex = 0,
                                    isPlaying = false
                                )
                            }
                            true
                        }
                    } else {
                        val nextIdx = resolveSceneIndexAt(current.project, nextGlobal, current.activeSceneIndex)
                        val switchedToNormalVideo = !isAeMultiLayer &&
                            win != null &&
                            nextIdx != current.activeSceneIndex &&
                            current.project?.scenes?.getOrNull(nextIdx)?.let { nextSc ->
                                nextSc.isVideo && !nextSc.isReversed && !nextSc.timing.freezeFrameEnabled && !nextSc.isMultiLayerComposite
                            } == true
                        _uiState.update {
                            it.copy(
                                currentPositionMs = nextGlobal,
                                activeSceneIndex = nextIdx
                            )
                        }
                        switchedToNormalVideo
                    }
                }
                if (shouldBreak) break
            }
        }
    }

    fun syncPositionFromLocalVideo(localPosMs: Long) {
        _uiState.update { state ->
            if (!state.isPlaying || state.isScrubbing) return@update state
            if (state.studioLayout == StudioLayoutMode.AFTER_EFFECTS && state.aeLayers.isNotEmpty()) {
                return@update state
            }
            val win = state.activeWindow ?: return@update state
            val clampedLocal = localPosMs.coerceIn(0L, win.scene.effectiveDurationMs)
            val globalPos = (win.startMs + clampedLocal).coerceIn(0L, state.totalDurationMs)
            state.copy(currentPositionMs = globalPos)
        }
    }

    fun onScenePlaybackEnded() {
        val current = _uiState.value
        val windows = current.sceneWindows
        val nextIndex = current.activeSceneIndex + 1
        if (nextIndex in windows.indices) {
            val nextWin = windows[nextIndex]
            _uiState.update {
                it.copy(
                    activeSceneIndex = nextIndex,
                    currentPositionMs = nextWin.startMs
                )
            }
            val nextScene = nextWin.scene
            val needsSoftwareClock = !nextScene.isVideo || nextScene.isReversed ||
                nextScene.timing.freezeFrameEnabled || nextScene.isMultiLayerComposite
            if (current.isPlaying && needsSoftwareClock) {
                val token = cancelPlaybackLoopOnly()
                startSoftwareTimelineLoop(token)
            }
        } else {
            cancelPlaybackLoopOnly()
            _uiState.update {
                it.copy(
                    isPlaying = false,
                    currentPositionMs = 0L,
                    activeSceneIndex = 0
                )
            }
        }
    }

    fun seekToProgress(progress: Float) {
        val state = _uiState.value
        val total = state.totalDurationMs.coerceAtLeast(1L)
        val targetGlobal = (progress.coerceIn(0f, 1f) * total).toLong()
        seekToPositionMs(targetGlobal)
    }

    fun seekToPositionMs(targetMs: Long) {
        if (_uiState.value.isPlaying) {
            cancelPlaybackLoopOnly()
        }
        val state = _uiState.value
        val maxMs = state.totalDurationMs.coerceAtLeast(500L)
        val clampedMs = targetMs.coerceIn(0L, maxMs)
        scrubController.requestFrame(clampedMs)
        val targetIdx = resolveSceneIndexAt(state.project, clampedMs, state.activeSceneIndex)
        _uiState.update {
            it.copy(
                currentPositionMs = clampedMs,
                activeSceneIndex = targetIdx,
                isPlaying = false
            )
        }
        scrubController.completeFrameRender()
    }

    /**
     * '<' button: steps backward by 1 frame at the project's selected FPS.
     */
    fun stepBackward() {
        cancelPlaybackLoopOnly()
        _uiState.update { state ->
            val fps = (state.project?.frameRate ?: 30).coerceIn(24, 60)
            val frameStepMs = (1000.0 / fps.toDouble()).toLong().coerceAtLeast(16L)
            val targetPos = (state.currentPositionMs - frameStepMs).coerceAtLeast(0L)
            val targetIdx = resolveSceneIndexAt(state.project, targetPos, state.activeSceneIndex)
            state.copy(
                currentPositionMs = targetPos,
                activeSceneIndex = targetIdx,
                isPlaying = false
            )
        }
    }

    /**
     * '>' button: steps forward by 1 frame at the project's selected FPS.
     */
    fun stepForward() {
        cancelPlaybackLoopOnly()
        _uiState.update { state ->
            val fps = (state.project?.frameRate ?: 30).coerceIn(24, 60)
            val frameStepMs = (1000.0 / fps.toDouble()).toLong().coerceAtLeast(16L)
            val targetPos = (state.currentPositionMs + frameStepMs).coerceAtMost(state.totalDurationMs)
            val targetIdx = resolveSceneIndexAt(state.project, targetPos, state.activeSceneIndex)
            state.copy(
                currentPositionMs = targetPos,
                activeSceneIndex = targetIdx,
                isPlaying = false
            )
        }
    }

    fun selectScene(index: Int) {
        cancelPlaybackLoopOnly()
        _uiState.update { state ->
            val win = state.sceneWindows.getOrNull(index)
            state.copy(
                activeSceneIndex = index.coerceAtLeast(0),
                currentPositionMs = win?.startMs ?: 0L,
                isPlaying = false
            )
        }
    }

    fun toggleSceneSelection(index: Int) {
        _uiState.update { state ->
            val next = if (index in state.selectedSceneIndices) {
                state.selectedSceneIndices - index
            } else {
                state.selectedSceneIndices + index
            }
            state.copy(selectedSceneIndices = next, activeSceneIndex = index)
        }
    }

    fun selectAllScenes() {
        _uiState.update { state ->
            val count = state.project?.scenes?.size ?: 0
            state.copy(selectedSceneIndices = (0 until count).toSet())
        }
    }

    fun selectRangeScenes() {
        _uiState.update { state ->
            val count = state.project?.scenes?.size ?: 0
            if (count == 0) return@update state
            val indices = state.selectedSceneIndices
            val range = if (indices.size >= 2) {
                (indices.min()..indices.max()).toSet()
            } else {
                val start = minOf(state.activeSceneIndex, (count - 1).coerceAtLeast(0))
                val end = (start + 1).coerceAtMost(count - 1)
                (start..end).toSet()
            }
            state.copy(selectedSceneIndices = range)
        }
    }

    fun clearSceneSelection() {
        _uiState.update { it.copy(selectedSceneIndices = emptySet()) }
    }

    fun exitSelectionMode() {
        _uiState.update {
            it.copy(
                isSelectionMode = false,
                selectedSceneIndices = emptySet(),
                activeTab = WorkspaceTab.EDIT
            )
        }
    }

    fun dismissAllPopovers() {
        _uiState.update {
            it.copy(
                isEditPopoverVisible = false,
                isNewPopoverVisible = false,
                isInfoPopoverVisible = false,
                isAddPopoverVisible = false
            )
        }
    }

    fun onTabSelected(tab: WorkspaceTab) {
        when (tab) {
            WorkspaceTab.NEW -> {
                _uiState.update { state ->
                    state.copy(
                        activeTab = WorkspaceTab.NEW,
                        isNewPopoverVisible = !state.isNewPopoverVisible,
                        isEditPopoverVisible = false,
                        isInfoPopoverVisible = false,
                        isAddPopoverVisible = false
                    )
                }
            }
            WorkspaceTab.EDIT -> {
                _uiState.update { state ->
                    state.copy(
                        activeTab = WorkspaceTab.EDIT,
                        isEditPopoverVisible = !state.isEditPopoverVisible,
                        isNewPopoverVisible = false,
                        isInfoPopoverVisible = false,
                        isAddPopoverVisible = false
                    )
                }
            }
            WorkspaceTab.SELECT -> {
                _uiState.update { state ->
                    val nextSelect = !state.isSelectionMode
                    state.copy(
                        activeTab = WorkspaceTab.SELECT,
                        isSelectionMode = nextSelect,
                        showSceneStrip = nextSelect || ((state.project?.scenes?.size ?: 0) > 1),
                        selectedSceneIndices = if (nextSelect && state.project?.scenes?.isNotEmpty() == true) {
                            setOf(state.activeSceneIndex.coerceIn(0, state.project.scenes.lastIndex))
                        } else emptySet(),
                        isNewPopoverVisible = false,
                        isEditPopoverVisible = false,
                        isInfoPopoverVisible = false,
                        isAddPopoverVisible = false
                    )
                }
            }
        }
    }

    fun toggleInfoPopover() {
        _uiState.update { state ->
            state.copy(
                isInfoPopoverVisible = !state.isInfoPopoverVisible,
                isEditPopoverVisible = false,
                isNewPopoverVisible = false,
                isAddPopoverVisible = false
            )
        }
    }

    fun toggleAddPopover() {
        _uiState.update { state ->
            state.copy(
                isAddPopoverVisible = !state.isAddPopoverVisible,
                isEditPopoverVisible = false,
                isNewPopoverVisible = false,
                isInfoPopoverVisible = false
            )
        }
    }

    // --- Edit Popover Actions ---

    fun copyActiveScene() {
        val state = _uiState.value
        val active = state.activeScene
        if (active == null) {
            _uiState.update {
                it.copy(
                    isEditPopoverVisible = false,
                    snackbarMessage = "لا يوجد مشهد لنسخه"
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                copiedScene = active,
                isEditPopoverVisible = false,
                snackbarMessage = "تم نسخ المشهد إلى الحافظة"
            )
        }
    }

    fun pasteCopiedScene() {
        val state = _uiState.value
        val copied = state.copiedScene
        if (copied == null) {
            _uiState.update {
                it.copy(
                    isEditPopoverVisible = false,
                    snackbarMessage = "الحافظة فارغة — انسخ مشهداً أولاً"
                )
            }
            return
        }
        pushUndoSnapshot()
        VideoStarRepository.pasteSceneAt(projectId, copied, state.activeSceneIndex)
        val insertIdx = if ((state.project?.scenes?.size ?: 0) == 0) 0 else state.activeSceneIndex + 1
        _uiState.update {
            it.copy(
                isEditPopoverVisible = false,
                showSceneStrip = true,
                activeSceneIndex = insertIdx,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                snackbarMessage = "تم لصق المشهد بنجاح"
            )
        }
    }

    fun undoTimelineEdit() {
        if (undoStack.isEmpty()) {
            _uiState.update { it.copy(isEditPopoverVisible = false, snackbarMessage = "لا توجد خطوة للتراجع عنها") }
            return
        }
        val proj = _uiState.value.project
        redoStack.addLast(WorkspaceHistorySnapshot(proj?.scenes.orEmpty(), proj?.aeLayers.orEmpty()))
        val previous = undoStack.removeLast()
        VideoStarRepository.restoreProjectSnapshot(projectId, previous.scenes, previous.aeLayers)
        _uiState.update {
            it.copy(
                isEditPopoverVisible = false,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                snackbarMessage = "تم التراجع عن آخر تعديل"
            )
        }
    }

    fun redoTimelineEdit() {
        if (redoStack.isEmpty()) {
            _uiState.update { it.copy(isEditPopoverVisible = false, snackbarMessage = "لا توجد خطوة لإعادتها") }
            return
        }
        val proj = _uiState.value.project
        undoStack.addLast(WorkspaceHistorySnapshot(proj?.scenes.orEmpty(), proj?.aeLayers.orEmpty()))
        val restored = redoStack.removeLast()
        VideoStarRepository.restoreProjectSnapshot(projectId, restored.scenes, restored.aeLayers)
        _uiState.update {
            it.copy(
                isEditPopoverVisible = false,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                snackbarMessage = "تمت إعادة التعديل"
            )
        }
    }

    fun quickSplitActiveScene() {
        val state = _uiState.value
        if (state.project?.scenes?.isEmpty() != false && (state.project?.durationMs ?: 0L) < 600L) {
            _uiState.update {
                it.copy(
                    isEditPopoverVisible = false,
                    snackbarMessage = "أضف مشهداً أولاً لتتمكن من تقسيمه"
                )
            }
            return
        }
        pushUndoSnapshot()
        val localSplit = state.localPositionInActiveSceneMs
        VideoStarRepository.splitSceneAt(projectId, state.activeSceneIndex, localSplit)
        _uiState.update {
            it.copy(
                isEditPopoverVisible = false,
                showSceneStrip = true,
                snackbarMessage = "تم تقسيم المشهد فوراً إلى مشهدين"
            )
        }
    }

    fun enterSplitMode() {
        _uiState.update { state ->
            val win = state.activeWindow
            val dur = win?.scene?.effectiveDurationMs ?: state.totalDurationMs
            val local = state.localPositionInActiveSceneMs
            val splitLocal = if (local in 250L..(dur - 250L)) local else dur / 2L
            val globalPos = (win?.startMs ?: 0L) + splitLocal
            state.copy(
                isEditPopoverVisible = false,
                isNewPopoverVisible = false,
                isSplitMode = true,
                isPlaying = false,
                currentPositionMs = globalPos
            )
        }
    }

    fun cancelSplitMode() {
        _uiState.update { it.copy(isSplitMode = false) }
    }

    fun confirmSplitScene() {
        val state = _uiState.value
        pushUndoSnapshot()
        val localPos = state.localPositionInActiveSceneMs
        VideoStarRepository.splitSceneAt(projectId, state.activeSceneIndex, localPos)
        _uiState.update {
            it.copy(
                isSplitMode = false,
                showSceneStrip = true,
                activeSceneIndex = (state.activeSceneIndex + 1).coerceAtLeast(1),
                snackbarMessage = "تم تقسيم المشهد إلى جزأين بنجاح"
            )
        }
    }

    fun mergeActiveSceneWithNext() {
        val state = _uiState.value
        val canMerge = state.activeSceneIndex in 0 until ((state.project?.scenes?.size ?: 0) - 1)
        if (canMerge) pushUndoSnapshot()
        val merged = VideoStarRepository.mergeSceneWithNext(projectId, state.activeSceneIndex)
        _uiState.update {
            it.copy(
                isEditPopoverVisible = false,
                snackbarMessage = if (merged) "تم دمج المشهد مع المشهد التالي" else "يلزم وجود مشهد تالٍ للدمج"
            )
        }
    }

    fun mergeSelectedScenes() {
        val indices = _uiState.value.selectedSceneIndices
        if (indices.size >= 2) pushUndoSnapshot()
        val merged = VideoStarRepository.mergeScenesByIndices(projectId, indices)
        _uiState.update {
            it.copy(
                selectedSceneIndices = if (merged) setOf(indices.min()) else indices,
                snackbarMessage = if (merged) "تم دمج المشاهد المحددة بنجاح" else "اختر مشهدين على الأقل للدمج"
            )
        }
    }

    fun duplicateActiveScene() {
        val state = _uiState.value
        if (state.project?.scenes?.isEmpty() != false) {
            _uiState.update { it.copy(isEditPopoverVisible = false, snackbarMessage = "لا يوجد مشهد لنسخه") }
            return
        }
        pushUndoSnapshot()
        VideoStarRepository.duplicateScene(projectId, state.activeSceneIndex)
        _uiState.update {
            it.copy(
                isEditPopoverVisible = false,
                showSceneStrip = true,
                snackbarMessage = "تم نسخ المشهد بكامل تعديلاته"
            )
        }
    }

    fun toggleReverseActiveScene() {
        val state = _uiState.value
        if (state.project?.scenes?.isEmpty() != false) {
            _uiState.update { it.copy(isEditPopoverVisible = false, snackbarMessage = "لا يوجد مشهد لعكسه") }
            return
        }
        pushUndoSnapshot()
        VideoStarRepository.toggleReverseScene(projectId, state.activeSceneIndex)
        val nowReversed = VideoStarRepository.getProjectById(projectId)?.scenes?.getOrNull(state.activeSceneIndex)?.isReversed == true
        _uiState.update {
            it.copy(
                isEditPopoverVisible = false,
                snackbarMessage = if (nowReversed) "تم تفعيل التشغيل العكسي للمشهد" else "تمت إعادة التشغيل للأمام"
            )
        }
    }

    fun openTrimDialog() {
        val state = _uiState.value
        val sc = state.activeScene
        if (sc == null || state.project?.scenes?.isEmpty() != false) {
            _uiState.update { it.copy(isEditPopoverVisible = false, snackbarMessage = "لا يوجد مشهد لقصه") }
            return
        }
        launchTrimMode(isFixedDuration = false)
    }

    fun openTrimWithClipModeCheck() {
        val state = _uiState.value
        val scenes = state.project?.scenes ?: emptyList()
        val sc = state.activeScene
        if (sc == null || scenes.isEmpty()) {
            _uiState.update { it.copy(isEditPopoverVisible = false, snackbarMessage = "لا يوجد مشهد لقصه") }
            return
        }
        val isLastScene = state.activeSceneIndex == scenes.lastIndex
        if (isLastScene) {
            _uiState.update {
                it.copy(
                    isEditPopoverVisible = false,
                    activeDialog = WorkspaceDialogState.ClipModeFixedFlexible(
                        sceneIndex = state.activeSceneIndex,
                        trigger = ClipModeTrigger.TRIM
                    )
                )
            }
        } else {
            launchTrimMode(isFixedDuration = sc.timing.mode == TimingMode.FIXED)
        }
    }

    fun openTimingWithClipModeCheck(onNavigateToTiming: (Int) -> Unit) {
        val state = _uiState.value
        val scenes = state.project?.scenes ?: emptyList()
        if (scenes.isEmpty()) {
            _uiState.update { it.copy(snackbarMessage = "أضف مشهداً أولاً لضبط التوقيت") }
            return
        }
        val activeIdx = state.activeSceneIndex.coerceIn(0, scenes.lastIndex)
        if (activeIdx == scenes.lastIndex) {
            pendingTimingNavigation = onNavigateToTiming
            _uiState.update {
                it.copy(
                    isNewPopoverVisible = false,
                    activeDialog = WorkspaceDialogState.ClipModeFixedFlexible(
                        sceneIndex = activeIdx,
                        trigger = ClipModeTrigger.TIMING
                    )
                )
            }
        } else {
            onNavigateToTiming(activeIdx)
        }
    }

    fun applyFixedClipMode() {
        val dialog = _uiState.value.activeDialog as? WorkspaceDialogState.ClipModeFixedFlexible ?: return
        pushUndoSnapshot()
        VideoStarRepository.updateSceneTimingMode(projectId, dialog.sceneIndex, TimingMode.FIXED)
        _uiState.update { it.copy(activeDialog = null) }
        when (dialog.trigger) {
            ClipModeTrigger.TRIM -> launchTrimMode(isFixedDuration = true)
            ClipModeTrigger.TIMING -> {
                val nav = pendingTimingNavigation
                pendingTimingNavigation = null
                nav?.invoke(dialog.sceneIndex)
            }
        }
    }

    fun applyFlexibleClipMode() {
        val dialog = _uiState.value.activeDialog as? WorkspaceDialogState.ClipModeFixedFlexible ?: return
        pushUndoSnapshot()
        VideoStarRepository.updateSceneTimingMode(projectId, dialog.sceneIndex, TimingMode.FLEXIBLE)
        _uiState.update { it.copy(activeDialog = null) }
        when (dialog.trigger) {
            ClipModeTrigger.TRIM -> launchTrimMode(isFixedDuration = false)
            ClipModeTrigger.TIMING -> {
                val nav = pendingTimingNavigation
                pendingTimingNavigation = null
                nav?.invoke(dialog.sceneIndex)
            }
        }
    }

    fun dismissClipModeDialog() {
        pendingTimingNavigation = null
        _uiState.update { it.copy(activeDialog = null) }
    }

    private fun launchTrimMode(isFixedDuration: Boolean) {
        val state = _uiState.value
        val sc = state.activeScene ?: return
        val maxDur = when (val c = sc.clip) {
            is MediaClip.Video -> c.durationMs.coerceAtLeast(500L)
            is MediaClip.Photo -> 30_000L
            null -> 10_000L
        }
        val start = (sc.clip as? MediaClip.Video)?.startTrimMs ?: 0L
        val end = ((sc.clip as? MediaClip.Video)?.endTrimMs ?: sc.durationMs).coerceIn(start + 250L, maxDur)
        playbackJob?.cancel()
        _uiState.update {
            it.copy(
                isEditPopoverVisible = false,
                isSplitMode = false,
                isPlaying = false,
                isTrimMode = true,
                trimSceneIndex = state.activeSceneIndex,
                trimStartMs = start,
                trimEndMs = end,
                trimMaxMs = maxDur,
                trimIsFixedDuration = isFixedDuration
            )
        }
    }

    fun updateTrimRange(startMs: Long, endMs: Long) {
        _uiState.update { state ->
            val maxDur = state.trimMaxMs.coerceAtLeast(500L)
            if (state.trimIsFixedDuration) {
                val fixedLen = (state.trimEndMs - state.trimStartMs).coerceIn(250L, maxDur)
                val movedStart = startMs != state.trimStartMs
                val newStart = if (movedStart) {
                    startMs.coerceIn(0L, (maxDur - fixedLen).coerceAtLeast(0L))
                } else {
                    (endMs - fixedLen).coerceIn(0L, (maxDur - fixedLen).coerceAtLeast(0L))
                }
                val newEnd = (newStart + fixedLen).coerceAtMost(maxDur)
                state.copy(trimStartMs = newStart, trimEndMs = newEnd)
            } else {
                val safeStart = startMs.coerceIn(0L, (endMs - 250L).coerceAtLeast(0L))
                val safeEnd = endMs.coerceIn((safeStart + 250L).coerceAtMost(maxDur), maxDur)
                state.copy(trimStartMs = safeStart, trimEndMs = safeEnd)
            }
        }
    }

    fun cancelTrimMode() {
        _uiState.update { it.copy(isTrimMode = false) }
    }

    fun confirmTrimMode() {
        val state = _uiState.value
        saveTrimmedScene(state.trimSceneIndex, state.trimStartMs, state.trimEndMs)
    }

    fun saveTrimmedScene(sceneIndex: Int, startMs: Long, endMs: Long) {
        pushUndoSnapshot()
        VideoStarRepository.trimScene(projectId, sceneIndex, startMs, endMs)
        _uiState.update {
            it.copy(
                isTrimMode = false,
                activeDialog = null,
                snackbarMessage = "تم تحديث قص المشهد بنجاح"
            )
        }
    }

    fun clearActiveSceneEffects() {
        val state = _uiState.value
        if (state.project?.scenes?.isEmpty() != false) {
            _uiState.update { it.copy(isEditPopoverVisible = false, snackbarMessage = "لا يوجد مشهد لمسح مؤثراته") }
            return
        }
        pushUndoSnapshot()
        VideoStarRepository.clearSceneEffects(projectId, state.activeSceneIndex)
        _uiState.update {
            it.copy(
                isEditPopoverVisible = false,
                snackbarMessage = "تمت إزالة جميع المؤثرات من المشهد"
            )
        }
    }

    fun requestDeleteActiveScene() {
        val state = _uiState.value
        val sc = state.activeScene
        if (sc == null) {
            _uiState.update { it.copy(isEditPopoverVisible = false) }
            return
        }
        _uiState.update {
            it.copy(
                isEditPopoverVisible = false,
                activeDialog = WorkspaceDialogState.ConfirmDeleteScene(state.activeSceneIndex, sc.title)
            )
        }
    }

    fun confirmDeleteActiveScene(sceneIndex: Int) {
        pushUndoSnapshot()
        VideoStarRepository.deleteSceneAt(projectId, sceneIndex)
        _uiState.update {
            it.copy(
                activeDialog = null,
                currentPositionMs = 0L,
                activeSceneIndex = (sceneIndex - 1).coerceAtLeast(0),
                snackbarMessage = "تم حذف المشهد من التايم لاين"
            )
        }
    }

    fun requestBatchDeleteScenes() {
        if (_uiState.value.selectedSceneIndices.isEmpty()) return
        _uiState.update { it.copy(activeDialog = WorkspaceDialogState.ConfirmBatchDelete) }
    }

    fun confirmBatchDeleteScenes() {
        val indices = _uiState.value.selectedSceneIndices
        pushUndoSnapshot()
        VideoStarRepository.deleteScenesByIndices(projectId, indices)
        _uiState.update {
            it.copy(
                activeDialog = null,
                selectedSceneIndices = emptySet(),
                isSelectionMode = false,
                currentPositionMs = 0L,
                activeSceneIndex = 0,
                snackbarMessage = "تم حذف المشاهد المحددة"
            )
        }
    }

    // --- New 10-Action Routing ---

    fun onNewActionSelected(
        action: NewClipAction,
        onRecordVideo: () -> Unit,
        onImportVideo: () -> Unit,
        onImportPhoto: () -> Unit,
        onOpenMotion: (Int) -> Unit,
        onOpenVelocity: (Int) -> Unit,
        onOpenReEffect: (List<Int>) -> Unit,
        onOpenTransform: (Int) -> Unit,
        onOpenMix: (Int) -> Unit,
        onOpenLayers: () -> Unit,
        onOpenTiming: (Int) -> Unit
    ) {
        _uiState.update { it.copy(isNewPopoverVisible = false, isPlaying = false) }
        playbackJob?.cancel()
        val state = _uiState.value
        val activeIdx = state.activeSceneIndex
        val sceneCount = state.project?.scenes?.size ?: 0

        when (action) {
            NewClipAction.RECORD_VIDEO -> onRecordVideo()
            NewClipAction.IMPORT_VIDEO -> onImportVideo()
            NewClipAction.IMPORT_PHOTO -> onImportPhoto()
            NewClipAction.MULTI_LAYER -> onOpenLayers()
            NewClipAction.MOTION -> {
                if (sceneCount == 0) {
                    _uiState.update { it.copy(snackbarMessage = "أضف مشهداً أولاً لتطبيق الحركة") }
                } else onOpenMotion(activeIdx)
            }
            NewClipAction.VELOCITY -> {
                if (sceneCount == 0) {
                    _uiState.update { it.copy(snackbarMessage = "أضف مشهداً أولاً لتعديل السرعة") }
                } else onOpenVelocity(activeIdx)
            }
            NewClipAction.RE_EFFECT -> {
                if (sceneCount == 0) {
                    _uiState.update { it.copy(snackbarMessage = "أضف مشهداً أولاً لتطبيق المؤثرات") }
                } else onOpenReEffect(listOf(activeIdx))
            }
            NewClipAction.TRANSFORM -> {
                if (sceneCount == 0) {
                    _uiState.update { it.copy(snackbarMessage = "أضف مشهداً أولاً للتحجيم والتدوير") }
                } else onOpenTransform(activeIdx)
            }
            NewClipAction.MIX -> {
                if (activeIdx >= sceneCount - 1) {
                    _uiState.update {
                        it.copy(
                            activeDialog = WorkspaceDialogState.MixNoNextSceneAlert(
                                "Select a scene that has another scene after it."
                            )
                        )
                    }
                } else {
                    onOpenMix(activeIdx)
                }
            }
            NewClipAction.TIMING -> {
                if (sceneCount == 0) {
                    _uiState.update { it.copy(snackbarMessage = "أضف مشهداً أولاً لضبط التوقيت") }
                } else onOpenTiming(activeIdx)
            }
        }
    }

    fun triggerShutterFlash(message: String) {
        _uiState.update {
            it.copy(
                snapshotFlashTrigger = it.snapshotFlashTrigger + 1,
                snackbarMessage = message
            )
        }
    }

    fun openAudioDialog() {
        _uiState.update {
            it.copy(
                activeDialog = WorkspaceDialogState.AudioManager,
                isNewPopoverVisible = false,
                isEditPopoverVisible = false,
                isInfoPopoverVisible = false,
                isAddPopoverVisible = false
            )
        }
    }

    fun updateAudioConfig(config: ProjectAudioConfig) {
        VideoStarRepository.updateAudioConfig(projectId, config)
    }

    fun selectAudioTrack(track: String) {
        val current = _uiState.value.project?.audioConfig ?: ProjectAudioConfig()
        VideoStarRepository.updateAudioConfig(
            projectId,
            current.copy(songTitle = track, songUriString = null)
        )
    }

    fun selectAspectRatio(ratio: AspectRatioMode) {
        val current = _uiState.value.project ?: return
        VideoStarRepository.updateProject(current.id, current.title, ratio)
    }

    fun toggleSceneStrip() {
        _uiState.update { it.copy(showSceneStrip = !it.showSceneStrip) }
    }

    // --- Multi-Layout Switcher & Strict Effect Mode State Machine ---

    fun openLayoutModal() {
        if (_uiState.value.isInEditingOrEffectMode) return
        _uiState.update {
            it.copy(
                isLayoutModalVisible = true,
                isEditPopoverVisible = false,
                isNewPopoverVisible = false,
                isInfoPopoverVisible = false,
                isAddPopoverVisible = false
            )
        }
    }

    fun dismissLayoutModal() {
        _uiState.update { it.copy(isLayoutModalVisible = false) }
    }

    fun selectStudioLayout(layout: StudioLayoutMode) {
        cancelPlaybackLoopOnly()
        scrubController.reset()
        StudioRenderMemoryCoordinator.trimTemporaryBuffers(aggressive = false)
        VideoStarRepository.updateStudioLayout(projectId, layout)
        if (layout == StudioLayoutMode.AFTER_EFFECTS) {
            VideoStarRepository.ensureAeLayersHydrated(projectId)
        }
        _uiState.update {
            it.copy(
                isPlaying = false,
                isScrubbing = false,
                isLayoutModalVisible = false,
                isEditPopoverVisible = false,
                isNewPopoverVisible = false,
                isInfoPopoverVisible = false,
                isAddPopoverVisible = false,
                activeEffectFilterCategory = "All"
            )
        }
    }

    fun enterEffectMode() {
        _uiState.update {
            it.copy(
                isInlineEffectMode = true,
                isLayoutModalVisible = false,
                isEditPopoverVisible = false,
                isNewPopoverVisible = false,
                isInfoPopoverVisible = false,
                isAddPopoverVisible = false
            )
        }
    }

    fun exitEffectMode() {
        _uiState.update { it.copy(isInlineEffectMode = false) }
    }

    fun setInlineEffectCategory(category: String) {
        _uiState.update { it.copy(activeEffectFilterCategory = category) }
    }

    fun selectNodeGraphStage(stage: String) {
        _uiState.update { it.copy(selectedNodeGraphStage = stage) }
    }

    fun toggleQuickShaderEffect(effectId: String) {
        val state = _uiState.value
        val scenes = state.project?.scenes ?: emptyList()
        if (scenes.isEmpty()) {
            _uiState.update {
                it.copy(snackbarMessage = "أضف فيديو أو صورة أولاً لتطبيق التأثير على المشهد")
            }
            return
        }
        val activeIdx = state.activeSceneIndex.coerceIn(0, scenes.lastIndex)
        val scene = scenes[activeIdx]
        val catalogItem = EffectCatalog.findById(effectId) ?: return
        val existingPasses = scene.resolvedEffectPasses()
        val xmlPreset = com.example.engine.packs.CustomPackRepository.findXmlPreset(effectId)
        val expandedPasses = com.example.engine.packs.CustomPackRepository.expandEffectPassesForSelection(effectId)
        if (expandedPasses.isEmpty()) return

        val alreadyApplied = if (xmlPreset != null) {
            val presetTargetIds = expandedPasses.map { it.effectId }.toSet()
            presetTargetIds.isNotEmpty() && presetTargetIds.all { tid -> existingPasses.any { it.effectId == tid } }
        } else {
            existingPasses.any { it.effectId == catalogItem.id }
        }

        pushUndoSnapshot()
        val updatedPasses = if (alreadyApplied) {
            val removeIds = if (xmlPreset != null) expandedPasses.map { it.effectId }.toSet() else setOf(catalogItem.id)
            existingPasses.filterNot { it.effectId in removeIds }
        } else {
            existingPasses + expandedPasses
        }
        VideoStarRepository.applySceneEffectPasses(projectId, setOf(activeIdx), updatedPasses)
        _uiState.update {
            it.copy(
                snackbarMessage = if (alreadyApplied) {
                    "تمت إزالة ${catalogItem.name}"
                } else if (expandedPasses.size > 1) {
                    "تم تطبيق ${catalogItem.name} (${expandedPasses.size} مؤثرات)"
                } else {
                    "تم تطبيق ${catalogItem.name}"
                }
            )
        }
    }

    // --- After Effects Mode Multi-Layer Stack & Live Effect Editing ---

    fun selectAeLayer(layerId: String?) {
        _uiState.update { state ->
            val proj = state.project
            val targetLayer = layerId?.let { id -> proj?.aeLayers?.firstOrNull { it.id == id } }
            val mappedSceneIdx = proj?.resolveSceneIndexForAeLayer(targetLayer)
            state.copy(
                selectedAeLayerId = layerId,
                activeSceneIndex = mappedSceneIdx ?: state.activeSceneIndex
            )
        }
    }

    private fun ensureAePlaybackRunning() {
        val current = _uiState.value
        if (current.studioLayout != StudioLayoutMode.AFTER_EFFECTS) return
        if (current.isPlaying) return
        val sessionToken = cancelPlaybackLoopOnly()
        scrubController.endScrub()
        val atEnd = current.currentPositionMs >= (current.totalDurationMs - 40L).coerceAtLeast(100L)
        _uiState.update {
            it.copy(
                currentPositionMs = if (atEnd) 0L else it.currentPositionMs,
                activeSceneIndex = if (atEnd) 0 else it.activeSceneIndex,
                isPlaying = true,
                isScrubbing = false
            )
        }
        val updated = _uiState.value
        val isAeMultiLayer = updated.aeLayers.isNotEmpty()
        val activeWin = updated.activeWindow
        val isLiveForwardVideo = !isAeMultiLayer &&
            activeWin != null &&
            activeWin.scene.isVideo &&
            !activeWin.scene.isReversed &&
            !activeWin.scene.timing.freezeFrameEnabled &&
            !activeWin.scene.isMultiLayerComposite &&
            activeWin.scene.clip is MediaClip.Video
        if (!isLiveForwardVideo) {
            startSoftwareTimelineLoop(sessionToken)
        }
    }

    fun addAeMediaLayer(clip: MediaClip) {
        pushUndoSnapshot()
        val atMs = _uiState.value.currentPositionMs
        val created = VideoStarRepository.addAeStackedLayer(
            projectId = projectId,
            kind = AeLayerKind.MEDIA,
            clip = clip,
            startTimeMs = atMs,
            durationMs = clip.effectiveDurationMs
        )
        if (created != null) {
            _uiState.update {
                it.copy(
                    selectedAeLayerId = created.id,
                    snackbarMessage = "Stacked layer: ${created.displayTitle}"
                )
            }
            ensureAePlaybackRunning()
        }
    }

    fun addAeLayerByKind(kind: AeLayerKind, customTitle: String = "") {
        pushUndoSnapshot()
        val atMs = _uiState.value.currentPositionMs
        val created = VideoStarRepository.addAeStackedLayer(
            projectId = projectId,
            kind = kind,
            clip = null,
            title = customTitle,
            startTimeMs = atMs,
            durationMs = 3_000L
        )
        if (created != null) {
            _uiState.update {
                it.copy(
                    selectedAeLayerId = created.id,
                    snackbarMessage = "Added ${created.displayTitle} layer"
                )
            }
        }
    }

    fun toggleAeLayerVisibility(layerId: String) {
        pushUndoSnapshot()
        VideoStarRepository.toggleAeLayerVisibility(projectId, layerId)
    }

    fun toggleAeLayerLock(layerId: String) {
        pushUndoSnapshot()
        VideoStarRepository.toggleAeLayerLock(projectId, layerId)
    }

    fun renameAeLayer(layerId: String, newTitle: String) {
        pushUndoSnapshot()
        VideoStarRepository.renameAeLayer(projectId, layerId, newTitle)
    }

    fun duplicateSelectedAeLayer() {
        val targetId = _uiState.value.selectedAeLayerId ?: return
        pushUndoSnapshot()
        val cloned = VideoStarRepository.duplicateAeLayer(projectId, targetId)
        if (cloned != null) {
            _uiState.update {
                it.copy(
                    selectedAeLayerId = cloned.id,
                    snackbarMessage = "Duplicated ${cloned.displayTitle}"
                )
            }
        }
    }

    fun deleteSelectedAeLayer() {
        val targetId = _uiState.value.selectedAeLayerId ?: return
        pushUndoSnapshot()
        VideoStarRepository.deleteAeLayer(projectId, targetId)
        _uiState.update {
            it.copy(
                selectedAeLayerId = null,
                snackbarMessage = "Layer deleted"
            )
        }
    }

    fun beginAeLayerReorder() {
        pushUndoSnapshot()
    }

    fun reorderAeLayer(fromIndex: Int, toIndex: Int) {
        VideoStarRepository.reorderAeLayer(projectId, fromIndex, toIndex)
    }

    fun moveAeLayerTiming(layerId: String, newStartMs: Long) {
        VideoStarRepository.moveAeLayerTiming(projectId, layerId, newStartMs)
    }

    fun commitAeLayerTimingDrag(layerId: String, initialStartMs: Long, finalStartMs: Long) {
        val proj = _uiState.value.project ?: return
        val targetLayer = proj.aeLayers.firstOrNull { it.id == layerId } ?: return
        if (targetLayer.isLocked) return
        val clampedInitial = initialStartMs.coerceAtLeast(0L)
        val clampedFinal = finalStartMs.coerceAtLeast(0L)
        if (clampedInitial == clampedFinal && targetLayer.startTimeMs == clampedFinal) return

        val preDragLayers = proj.aeLayers.map { layer ->
            if (layer.id == layerId) layer.copy(startTimeMs = clampedInitial) else layer
        }
        undoStack.addLast(WorkspaceHistorySnapshot(proj.scenes, preDragLayers))
        while (undoStack.size > 30) undoStack.removeFirst()
        redoStack.clear()
        _uiState.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = false) }
        VideoStarRepository.moveAeLayerTiming(projectId, layerId, clampedFinal)
    }

    fun addAeLayerEffect(layerId: String, effectId: String): ShaderEffectPass? {
        if (_uiState.value.aeLayers.isEmpty()) {
            VideoStarRepository.ensureAeLayersHydrated(projectId)
        }
        val availableLayers = VideoStarRepository.getProjectById(projectId)?.aeLayers
            ?: _uiState.value.aeLayers
        val resolvedLayer = availableLayers.firstOrNull { it.id == layerId }
            ?: run {
                val sceneMatch = if (layerId.startsWith("ae_sync_")) {
                    availableLayers.firstOrNull { l ->
                        l.sourceSceneId != null && layerId.contains(l.sourceSceneId)
                    }
                } else null
                sceneMatch
                    ?: _uiState.value.selectedAeLayerId?.let { sel -> availableLayers.firstOrNull { it.id == sel } }
                    ?: availableLayers.firstOrNull()
            }
            ?: return null

        val passesToAdd = com.example.engine.ae.AeEffectEngine.createPassesForSelection(effectId)
        if (passesToAdd.isEmpty()) return null
        val presetName = com.example.engine.packs.CustomPackRepository.findXmlPreset(effectId)?.name
        pushUndoSnapshot()
        VideoStarRepository.updateAeLayer(projectId, resolvedLayer.id) { cur ->
            cur.copy(effectPasses = cur.effectPasses + passesToAdd)
        }
        val toastName = presetName ?: passesToAdd.first().effectName
        val suffix = if (passesToAdd.size > 1) " (${passesToAdd.size} passes)" else ""
        _uiState.update {
            it.copy(
                selectedAeLayerId = resolvedLayer.id,
                snackbarMessage = "Applied $toastName$suffix"
            )
        }
        ensureAePlaybackRunning()
        return passesToAdd.lastOrNull()
    }

    fun updateAeLayerEffectPass(layerId: String, updatedPass: ShaderEffectPass, recordUndo: Boolean = false) {
        if (recordUndo) pushUndoSnapshot()
        VideoStarRepository.updateAeLayer(projectId, layerId) { cur ->
            cur.copy(
                effectPasses = cur.effectPasses.map { pass ->
                    if (pass.id == updatedPass.id) updatedPass else pass
                }
            )
        }
    }

    fun removeAeLayerEffectPass(layerId: String, passId: String) {
        pushUndoSnapshot()
        VideoStarRepository.updateAeLayer(projectId, layerId) { cur ->
            cur.copy(effectPasses = cur.effectPasses.filterNot { it.id == passId || it.effectId == passId })
        }
    }

    fun updateAeLayerProperties(layerId: String, updatedLayer: EditorLayer, recordUndo: Boolean = false) {
        if (recordUndo) pushUndoSnapshot()
        VideoStarRepository.updateAeLayer(projectId, layerId) { cur ->
            if (cur.isLocked) cur else updatedLayer.copy(id = cur.id, number = cur.number)
        }
    }

    fun toggleAeLayerTransformKeyframe(layerId: String) {
        val layer = _uiState.value.aeLayers.firstOrNull { it.id == layerId } ?: return
        if (layer.isLocked) return
        val layerDur = layer.effectiveLayerDurationMs.toFloat().coerceAtLeast(1f)
        val progress = ((_uiState.value.currentPositionMs - layer.startTimeMs).toFloat() / layerDur).coerceIn(0f, 1f)
        pushUndoSnapshot()

        val nearby = layer.keyframes.firstOrNull { abs(it.timeProgress - progress) <= 0.03f }
        val nextKeyframes = if (nearby != null) {
            layer.keyframes.filterNot { it.id == nearby.id }
        } else {
            val evaluated = com.example.engine.ae.AeEffectEngine.composeAeLayerTransformAt(
                layer = layer,
                timelinePositionMs = _uiState.value.currentPositionMs
            )
            (layer.keyframes + LayerKeyframe(
                id = "lkf_${UUID.randomUUID().toString().take(6)}",
                timeProgress = progress,
                offsetX = evaluated.offsetX,
                offsetY = evaluated.offsetY,
                scale = evaluated.scale,
                rotation = evaluated.rotation,
                opacity = evaluated.opacity
            )).sortedBy { it.timeProgress }
        }
        VideoStarRepository.updateAeLayer(projectId, layerId) { cur ->
            cur.copy(keyframes = nextKeyframes)
        }
    }

    fun trimAeLayerStartToPlayhead(layerId: String) {
        val layer = _uiState.value.aeLayers.firstOrNull { it.id == layerId } ?: return
        if (layer.isLocked) return
        val playhead = _uiState.value.currentPositionMs
        val layerEnd = layer.startTimeMs + layer.effectiveLayerDurationMs
        val newStart = playhead.coerceIn(0L, (layerEnd - 300L).coerceAtLeast(0L))
        val newDur = (layerEnd - newStart).coerceAtLeast(300L)
        val trimmedDelta = newStart - layer.startTimeMs
        pushUndoSnapshot()
        VideoStarRepository.updateAeLayer(projectId, layerId) { cur ->
            val updatedClip = when (val c = cur.mediaClip) {
                is MediaClip.Video -> c.copy(
                    startTrimMs = (c.startTrimMs + trimmedDelta).coerceIn(0L, (c.endTrimMs - 300L).coerceAtLeast(0L))
                )
                is MediaClip.Photo -> c.copy(durationMs = newDur)
                null -> null
            }
            cur.copy(startTimeMs = newStart, durationMs = newDur, mediaClip = updatedClip)
        }
    }

    fun trimAeLayerEndToPlayhead(layerId: String) {
        val layer = _uiState.value.aeLayers.firstOrNull { it.id == layerId } ?: return
        if (layer.isLocked) return
        val playhead = _uiState.value.currentPositionMs
        val newEnd = playhead.coerceAtLeast(layer.startTimeMs + 300L)
        val newDur = (newEnd - layer.startTimeMs).coerceAtLeast(300L)
        pushUndoSnapshot()
        VideoStarRepository.updateAeLayer(projectId, layerId) { cur ->
            val updatedClip = when (val c = cur.mediaClip) {
                is MediaClip.Video -> c.copy(
                    endTrimMs = (c.startTrimMs + newDur).coerceIn(c.startTrimMs + 300L, c.durationMs)
                )
                is MediaClip.Photo -> c.copy(durationMs = newDur)
                null -> null
            }
            cur.copy(durationMs = newDur, mediaClip = updatedClip)
        }
    }

    fun splitAeLayerAtPlayhead(layerId: String): String? {
        val layer = _uiState.value.aeLayers.firstOrNull { it.id == layerId } ?: return null
        if (layer.isLocked) return null
        val atMs = _uiState.value.currentPositionMs
        pushUndoSnapshot()
        val created = VideoStarRepository.splitAeLayerAt(projectId, layerId, atMs) ?: return null
        _uiState.update {
            it.copy(
                selectedAeLayerId = created.id,
                snackbarMessage = "Split ${layer.displayTitle} into 2 layers"
            )
        }
        return created.id
    }

    fun toggleAeLayerEffectKeyframe(
        layerId: String,
        passId: String,
        propertyKey: String,
        currentValue: Float
    ) {
        val layer = _uiState.value.aeLayers.firstOrNull { it.id == layerId } ?: return
        val pass = layer.effectPasses.firstOrNull { it.id == passId } ?: return
        val layerDur = layer.effectiveLayerDurationMs.toFloat().coerceAtLeast(1f)
        val progress = ((_uiState.value.currentPositionMs - layer.startTimeMs).toFloat() / layerDur).coerceIn(0f, 1f)
        pushUndoSnapshot()

        val existingList = pass.keyframesForProperty(propertyKey)
        val nearby = existingList.firstOrNull { abs(it.time - progress) <= 0.03f }
        val nextList = if (nearby != null) {
            existingList.filterNot { it.id == nearby.id }
        } else {
            (existingList + ParameterKeyframe(
                id = "pkf_${UUID.randomUUID().toString().take(6)}",
                time = progress,
                value = currentValue
            )).sortedBy { it.time }
        }
        val nextMap = if (nextList.isEmpty()) {
            pass.paramKeyframes - propertyKey
        } else {
            pass.paramKeyframes + (propertyKey to nextList)
        }
        val legacyKfs = if (propertyKey == PARAM_KEY_INTENSITY) {
            nextList.map { EffectKeyframe(id = it.id, timeProgress = it.time, intensity = it.value, easing = it.easing) }
        } else pass.keyframes

        val updated = pass.copy(keyframes = legacyKfs, paramKeyframes = nextMap)
        VideoStarRepository.updateAeLayer(projectId, layerId) { cur ->
            cur.copy(effectPasses = cur.effectPasses.map { if (it.id == passId) updated else it })
        }
    }

    fun saveBeforeClose() {
        cancelPlaybackLoopOnly()
        scrubController.reset()
        StudioRenderMemoryCoordinator.trimTemporaryBuffers(aggressive = true)
        VideoStarRepository.saveNow()
    }

    fun showMessage(msg: String) {
        _uiState.update { it.copy(snackbarMessage = msg) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(activeDialog = null) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    override fun onCleared() {
        cancelPlaybackLoopOnly()
        scrubController.reset()
        StudioRenderMemoryCoordinator.trimTemporaryBuffers(aggressive = true)
        VideoStarRepository.saveNow()
        super.onCleared()
    }
}
