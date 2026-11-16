package com.example.ui.screens.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.VideoStarRepository
import com.example.model.AspectRatioMode
import com.example.model.MediaClip
import com.example.model.NewClipAction
import com.example.model.ProjectAudioConfig
import com.example.model.TimelineScene
import com.example.model.TimingMode
import com.example.model.VideoProject
import com.example.model.WorkspaceTab
import com.example.model.evaluateVelocityAt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val snapshotFlashTrigger: Int = 0,
    val speedMultiplier: Float = 1f,
    val snackbarMessage: String? = null
) {
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
        get() = sceneWindows.lastOrNull()?.endMs
            ?: project?.durationMs?.takeIf { it > 0L }
            ?: 10_020L

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
    private val undoStack = ArrayDeque<List<TimelineScene>>()
    private val redoStack = ArrayDeque<List<TimelineScene>>()
    private var pendingTimingNavigation: ((Int) -> Unit)? = null

    private fun pushUndoSnapshot() {
        val currentScenes = _uiState.value.project?.scenes ?: return
        undoStack.addLast(currentScenes)
        while (undoStack.size > 30) undoStack.removeFirst()
        redoStack.clear()
        _uiState.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = false) }
    }

    init {
        VideoStarRepository.projects
            .onEach { projects ->
                val match = projects.firstOrNull { it.id == projectId }
                _uiState.update { state ->
                    val maxSceneIdx = ((match?.scenes?.size ?: 1) - 1).coerceAtLeast(0)
                    val safeMaxDur = (match?.totalTimelineDurationMs ?: 3000L).coerceAtLeast(500L)
                    val safePos = state.currentPositionMs.coerceIn(0L, safeMaxDur)
                    val nextActiveIdx = resolveSceneIndexAt(match, safePos, state.activeSceneIndex).coerceIn(0, maxSceneIdx)
                    state.copy(
                        project = match,
                        currentPositionMs = safePos,
                        activeSceneIndex = nextActiveIdx,
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

    fun togglePlayback() {
        val state = _uiState.value
        val nextPlaying = !state.isPlaying
        _uiState.update { it.copy(isPlaying = nextPlaying) }
        playbackJob?.cancel()

        if (nextPlaying) {
            val activeWin = state.activeWindow
            val isLiveForwardVideo = activeWin != null &&
                activeWin.scene.isVideo &&
                !activeWin.scene.isReversed &&
                !activeWin.scene.timing.freezeFrameEnabled &&
                !activeWin.scene.isMultiLayerComposite &&
                activeWin.scene.clip is MediaClip.Video

            if (!isLiveForwardVideo) {
                startSoftwareTimelineLoop()
            }
        }
    }

    private fun startSoftwareTimelineLoop() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (_uiState.value.isPlaying) {
                val fps = (_uiState.value.project?.frameRate ?: 30).coerceIn(24, 60)
                val frameTickMs = (1000L / fps).coerceIn(16L, 42L)
                delay(frameTickMs)
                val current = _uiState.value
                val win = current.activeWindow
                val step = if (win != null) {
                    val scene = win.scene
                    val localPos = (current.currentPositionMs - win.startMs).coerceAtLeast(0L)
                    val localProgress = (localPos.toFloat() / scene.effectiveDurationMs.toFloat()).coerceIn(0f, 1f)
                    val curveSpeed = evaluateVelocityAt(localProgress, scene.velocityPoints, scene.isVelocitySmooth)
                    (frameTickMs * current.speedMultiplier * curveSpeed).toLong().coerceAtLeast(8L)
                } else {
                    (frameTickMs * current.speedMultiplier).toLong().coerceAtLeast(8L)
                }
                val nextGlobal = current.currentPositionMs + step

                if (nextGlobal >= current.totalDurationMs) {
                    _uiState.update {
                        it.copy(
                            currentPositionMs = 0L,
                            activeSceneIndex = 0,
                            isPlaying = false
                        )
                    }
                    break
                } else {
                    val nextIdx = resolveSceneIndexAt(current.project, nextGlobal, current.activeSceneIndex)
                    val switchedToNormalVideo = win != null &&
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
                    if (switchedToNormalVideo) {
                        break
                    }
                }
            }
        }
    }

    fun syncPositionFromLocalVideo(localPosMs: Long) {
        _uiState.update { state ->
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
                startSoftwareTimelineLoop()
            }
        } else {
            playbackJob?.cancel()
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
        val targetIdx = resolveSceneIndexAt(state.project, targetGlobal, state.activeSceneIndex)
        _uiState.update {
            it.copy(
                currentPositionMs = targetGlobal,
                activeSceneIndex = targetIdx
            )
        }
    }

    /**
     * '<' button: steps backward by 1 frame at the project's selected FPS.
     */
    fun stepBackward() {
        playbackJob?.cancel()
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
        playbackJob?.cancel()
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
        playbackJob?.cancel()
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
        val currentScenes = _uiState.value.project?.scenes ?: emptyList()
        redoStack.addLast(currentScenes)
        val previous = undoStack.removeLast()
        VideoStarRepository.restoreScenes(projectId, previous)
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
        val currentScenes = _uiState.value.project?.scenes ?: emptyList()
        undoStack.addLast(currentScenes)
        val restored = redoStack.removeLast()
        VideoStarRepository.restoreScenes(projectId, restored)
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

    fun saveBeforeClose() {
        playbackJob?.cancel()
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
        playbackJob?.cancel()
        VideoStarRepository.saveNow()
        super.onCleared()
    }
}
