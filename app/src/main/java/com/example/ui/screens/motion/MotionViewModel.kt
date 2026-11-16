package com.example.ui.screens.motion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.VideoStarRepository
import com.example.model.AspectRatioMode
import com.example.model.MotionPreset
import com.example.model.SceneMotion
import com.example.model.TimelineScene
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MotionUiState(
    val projectId: String = "",
    val sceneIndex: Int = 0,
    val aspectRatio: AspectRatioMode = AspectRatioMode.SQUARE,
    val baseScene: TimelineScene? = null,
    val preset: MotionPreset = MotionPreset.NONE,
    val speed: Float = 1.0f,
    val intensity: Float = 0.5f,
    val isPlaying: Boolean = true,
    val previewLocalMs: Long = 0L
) {
    val previewClip: com.example.model.MediaClip?
        get() = baseScene?.clip

    val previewScene: TimelineScene?
        get() = baseScene?.copy(
            motion = SceneMotion(
                preset = preset,
                speed = speed,
                intensity = if (preset == MotionPreset.NONE) 0f else intensity
            )
        )
}

class MotionViewModel(
    private val projectId: String,
    private val sceneIndex: Int = 0
) : ViewModel() {

    private val project = VideoStarRepository.getProjectById(projectId)
    private val scene = project?.scenes?.getOrNull(sceneIndex)

    private val _uiState = MutableStateFlow(
        MotionUiState(
            projectId = projectId,
            sceneIndex = sceneIndex,
            aspectRatio = project?.aspectRatio ?: AspectRatioMode.SQUARE,
            baseScene = scene,
            preset = scene?.motion?.preset ?: MotionPreset.NONE,
            speed = scene?.motion?.speed ?: 1.0f,
            intensity = scene?.motion?.intensity ?: 0.5f
        )
    )
    val uiState: StateFlow<MotionUiState> = _uiState.asStateFlow()

    private var playJob: Job? = null

    init {
        startLoop()
    }

    private fun startLoop() {
        playJob?.cancel()
        playJob = viewModelScope.launch {
            while (true) {
                delay(32L)
                if (_uiState.value.isPlaying) {
                    _uiState.update { state ->
                        val dur = (state.baseScene?.effectiveDurationMs ?: 3_000L).coerceAtLeast(300L)
                        state.copy(previewLocalMs = (state.previewLocalMs + 32L) % dur)
                    }
                }
            }
        }
    }

    fun selectPreset(preset: MotionPreset) {
        _uiState.update { it.copy(preset = preset) }
    }

    fun updateSpeed(speed: Float) {
        _uiState.update { it.copy(speed = speed.coerceIn(0.2f, 3.0f)) }
    }

    fun updateIntensity(intensity: Float) {
        _uiState.update { it.copy(intensity = intensity.coerceIn(0f, 1.0f)) }
    }

    fun togglePlayback() {
        _uiState.update { it.copy(isPlaying = !it.isPlaying) }
    }

    fun resetMotion() {
        _uiState.update {
            it.copy(
                preset = MotionPreset.NONE,
                speed = 1.0f,
                intensity = 0.5f
            )
        }
    }

    fun createAndSave(onDone: () -> Unit) {
        val state = _uiState.value
        VideoStarRepository.applySceneMotion(
            projectId = projectId,
            sceneIndex = sceneIndex,
            motion = SceneMotion(
                preset = state.preset,
                speed = state.speed,
                intensity = if (state.preset == MotionPreset.NONE) 0f else state.intensity
            )
        )
        onDone()
    }

    override fun onCleared() {
        playJob?.cancel()
        super.onCleared()
    }
}
