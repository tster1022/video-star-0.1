package com.example.ui.screens.timing

import androidx.lifecycle.ViewModel
import com.example.data.VideoStarRepository
import com.example.model.AspectRatioMode
import com.example.model.SceneTiming
import com.example.model.TimelineScene
import com.example.model.TimingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TimingUiState(
    val projectId: String = "",
    val sceneIndex: Int = 0,
    val aspectRatio: AspectRatioMode = AspectRatioMode.SQUARE,
    val baseScene: TimelineScene? = null,
    val mode: TimingMode = TimingMode.FIXED,
    val durationMs: Long = 3_000L,
    val freezeFrameEnabled: Boolean = false,
    val freezeAtProgress: Float = 0.5f,
    val freezeDurationMs: Long = 1_000L,
    val repeatCount: Int = 1
) {
    val previewScene: TimelineScene?
        get() = baseScene?.copy(
            durationMs = durationMs,
            timing = SceneTiming(
                mode = mode,
                durationMs = durationMs,
                freezeFrameEnabled = freezeFrameEnabled,
                freezeAtProgress = freezeAtProgress,
                freezeDurationMs = freezeDurationMs,
                repeatCount = repeatCount
            )
        )
}

class TimingViewModel(
    private val projectId: String,
    private val sceneIndex: Int = 0
) : ViewModel() {

    private val project = VideoStarRepository.getProjectById(projectId)
    private val scene = project?.scenes?.getOrNull(sceneIndex)

    private val _uiState = MutableStateFlow(
        TimingUiState(
            projectId = projectId,
            sceneIndex = sceneIndex,
            aspectRatio = project?.aspectRatio ?: AspectRatioMode.SQUARE,
            baseScene = scene,
            mode = scene?.timing?.mode ?: TimingMode.FIXED,
            durationMs = scene?.timing?.durationMs ?: scene?.durationMs ?: 3_000L,
            freezeFrameEnabled = scene?.timing?.freezeFrameEnabled ?: false,
            freezeAtProgress = scene?.timing?.freezeAtProgress ?: 0.5f,
            freezeDurationMs = scene?.timing?.freezeDurationMs ?: 1_000L,
            repeatCount = scene?.timing?.repeatCount ?: 1
        )
    )
    val uiState: StateFlow<TimingUiState> = _uiState.asStateFlow()

    fun setMode(mode: TimingMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun setDurationSeconds(sec: Float) {
        val ms = (sec.coerceIn(0.2f, 30.0f) * 1000f).toLong()
        _uiState.update { it.copy(durationMs = ms) }
    }

    fun setFreezeEnabled(enabled: Boolean) {
        _uiState.update { it.copy(freezeFrameEnabled = enabled) }
    }

    fun setFreezeAtProgress(progress: Float) {
        _uiState.update { it.copy(freezeAtProgress = progress.coerceIn(0f, 1f)) }
    }

    fun setFreezeDurationSeconds(sec: Float) {
        val ms = (sec.coerceIn(0.2f, 5.0f) * 1000f).toLong()
        _uiState.update { it.copy(freezeDurationMs = ms) }
    }

    fun setRepeatCount(count: Int) {
        _uiState.update { it.copy(repeatCount = count.coerceIn(1, 3)) }
    }

    fun createAndSave(onDone: () -> Unit) {
        val state = _uiState.value
        VideoStarRepository.applySceneTiming(
            projectId = projectId,
            sceneIndex = sceneIndex,
            timing = SceneTiming(
                mode = state.mode,
                durationMs = state.durationMs,
                freezeFrameEnabled = state.freezeFrameEnabled,
                freezeAtProgress = state.freezeAtProgress,
                freezeDurationMs = state.freezeDurationMs,
                repeatCount = state.repeatCount
            )
        )
        onDone()
    }
}
