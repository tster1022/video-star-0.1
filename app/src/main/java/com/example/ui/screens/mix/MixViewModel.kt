package com.example.ui.screens.mix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.VideoStarRepository
import com.example.model.AspectRatioMode
import com.example.model.SceneTransition
import com.example.model.TimelineScene
import com.example.model.TransitionCatalog
import com.example.model.TransitionCatalogItem
import com.example.model.TransitionCategory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MixUiState(
    val projectId: String = "",
    val sceneIndex: Int = 0,
    val aspectRatio: AspectRatioMode = AspectRatioMode.SQUARE,
    val outgoingScene: TimelineScene? = null,
    val incomingScene: TimelineScene? = null,
    val selectedCategory: TransitionCategory = TransitionCategory.MOTION_BLUR,
    val selectedTransition: TransitionCatalogItem = TransitionCatalog.allTransitions.first(),
    val durationMs: Long = 600L,
    val easing: String = "Smooth",
    val isDrawerExpanded: Boolean = true,
    val isPlaying: Boolean = true,
    val previewProgress: Float = 0.5f
) {
    val hasValidPair: Boolean
        get() = outgoingScene != null && incomingScene != null

    val outgoingClip: com.example.model.MediaClip?
        get() = outgoingScene?.clip

    val previewScene: TimelineScene?
        get() = outgoingScene?.copy(
            transitionToNext = SceneTransition(
                category = selectedTransition.category,
                transitionId = selectedTransition.id,
                transitionName = selectedTransition.name,
                durationMs = durationMs,
                easing = easing
            )
        )

    val previewLocalPositionMs: Long
        get() {
            val out = previewScene ?: return 0L
            val sceneDur = out.effectiveDurationMs.coerceAtLeast(300L)
            val transDur = durationMs.coerceIn(200L, sceneDur)
            val startTrans = (sceneDur - transDur).coerceAtLeast(0L)
            return (startTrans + (previewProgress * transDur).toLong()).coerceIn(0L, sceneDur)
        }
}

class MixViewModel(
    private val projectId: String,
    private val sceneIndex: Int = 0
) : ViewModel() {

    private val project = VideoStarRepository.getProjectById(projectId)
    private val outScene = project?.scenes?.getOrNull(sceneIndex)
    private val inScene = project?.scenes?.getOrNull(sceneIndex + 1)
    private val existingTransition = outScene?.transitionToNext
    private val initialItem = TransitionCatalog.allTransitions.firstOrNull {
        it.id == existingTransition?.transitionId
    } ?: TransitionCatalog.allTransitions.first()

    private val _uiState = MutableStateFlow(
        MixUiState(
            projectId = projectId,
            sceneIndex = sceneIndex,
            aspectRatio = project?.aspectRatio ?: AspectRatioMode.SQUARE,
            outgoingScene = outScene,
            incomingScene = inScene,
            selectedCategory = initialItem.category,
            selectedTransition = initialItem,
            durationMs = existingTransition?.durationMs ?: 600L,
            easing = existingTransition?.easing ?: "Smooth"
        )
    )
    val uiState: StateFlow<MixUiState> = _uiState.asStateFlow()

    private var playJob: Job? = null

    init {
        startTransitionLoop()
    }

    private fun startTransitionLoop() {
        playJob?.cancel()
        playJob = viewModelScope.launch {
            while (true) {
                delay(32L)
                if (_uiState.value.isPlaying) {
                    _uiState.update { state ->
                        val step = (32f / state.durationMs.coerceAtLeast(200L).toFloat()).coerceIn(0.01f, 0.1f)
                        val next = (state.previewProgress + step).let { if (it > 1f) 0f else it }
                        state.copy(previewProgress = next)
                    }
                }
            }
        }
    }

    fun selectCategory(category: TransitionCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun selectTransition(item: TransitionCatalogItem) {
        _uiState.update { it.copy(selectedTransition = item, selectedCategory = item.category) }
    }

    fun updateDurationSeconds(seconds: Float) {
        val ms = (seconds.coerceIn(0.2f, 2.0f) * 1000f).toLong()
        _uiState.update { it.copy(durationMs = ms) }
    }

    fun selectEasing(easing: String) {
        _uiState.update { it.copy(easing = easing) }
    }

    fun toggleDrawer() {
        _uiState.update { it.copy(isDrawerExpanded = !it.isDrawerExpanded) }
    }

    fun togglePlayback() {
        _uiState.update { it.copy(isPlaying = !it.isPlaying) }
    }

    fun scrubTransition(progress: Float) {
        _uiState.update { it.copy(previewProgress = progress.coerceIn(0f, 1f), isPlaying = false) }
    }

    fun createAndSave(onDone: () -> Unit) {
        val state = _uiState.value
        VideoStarRepository.applySceneTransition(
            projectId = projectId,
            sceneIndex = sceneIndex,
            transition = SceneTransition(
                category = state.selectedTransition.category,
                transitionId = state.selectedTransition.id,
                transitionName = state.selectedTransition.name,
                durationMs = state.durationMs,
                easing = state.easing
            )
        )
        onDone()
    }

    override fun onCleared() {
        playJob?.cancel()
        super.onCleared()
    }
}
