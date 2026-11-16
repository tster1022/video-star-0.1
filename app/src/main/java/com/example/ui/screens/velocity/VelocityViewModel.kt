package com.example.ui.screens.velocity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.VideoStarRepository
import com.example.model.AspectRatioMode
import com.example.model.MediaClip
import com.example.model.TimelineScene
import com.example.model.VelocityPoint
import com.example.model.defaultVelocityPoints
import com.example.model.evaluateVelocityAt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs

enum class VelocityPreset(val label: String) {
    SPEED_0_25X("0.25x"),
    SPEED_0_5X("0.5x"),
    SPEED_1_0X("1.0x"),
    SPEED_2_0X("2.0x"),
    RAMP_UP("Ramp Up"),
    RAMP_DOWN("Ramp Down"),
    PULSE("Pulse")
}

data class VelocityUiState(
    val projectId: String = "",
    val sceneIndex: Int = 0,
    val baseScene: TimelineScene? = null,
    val mediaClip: MediaClip? = null,
    val durationMs: Long = 3_000L,
    val aspectRatio: AspectRatioMode = AspectRatioMode.SQUARE,
    val points: List<VelocityPoint> = defaultVelocityPoints(),
    val isSmooth: Boolean = true,
    val selectedPointId: String? = null,
    val activePreset: VelocityPreset? = null,
    val currentProgress: Float = 0.0f,
    val instantSpeed: Float = 1.0f,
    val isPlaying: Boolean = false,
    val statusMessage: String? = null
)

class VelocityViewModel(
    private val projectId: String,
    private val sceneIndex: Int = 0
) : ViewModel() {

    private val project = VideoStarRepository.getProjectById(projectId)
    private val targetScene = project?.scenes?.getOrNull(sceneIndex)
    private val initialPoints = (targetScene?.velocityPoints ?: project?.velocityPoints ?: defaultVelocityPoints()).sortedBy { it.timeProgress }
    private val initialSmooth = targetScene?.isVelocitySmooth ?: project?.isVelocitySmooth ?: true

    private val _uiState = MutableStateFlow(
        VelocityUiState(
            projectId = projectId,
            sceneIndex = sceneIndex,
            baseScene = targetScene,
            mediaClip = targetScene?.clip ?: project?.primaryClip,
            durationMs = (targetScene?.effectiveDurationMs ?: project?.primaryClip?.effectiveDurationMs ?: project?.durationMs ?: 3_000L).coerceAtLeast(500L),
            aspectRatio = project?.aspectRatio ?: AspectRatioMode.SQUARE,
            points = initialPoints,
            isSmooth = initialSmooth,
            selectedPointId = initialPoints.firstOrNull()?.id,
            instantSpeed = evaluateVelocityAt(0f, initialPoints, initialSmooth)
        )
    )
    val uiState: StateFlow<VelocityUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null

    fun scrubTo(progress: Float) {
        val clamped = progress.coerceIn(0f, 1f)
        _uiState.update { state ->
            val speed = evaluateVelocityAt(clamped, state.points, state.isSmooth)
            state.copy(
                currentProgress = clamped,
                instantSpeed = speed
            )
        }
    }

    fun togglePlayback() {
        val next = !_uiState.value.isPlaying
        _uiState.update { it.copy(isPlaying = next) }
        playbackJob?.cancel()
        if (next) {
            playbackJob = viewModelScope.launch {
                while (_uiState.value.isPlaying) {
                    delay(40L)
                    _uiState.update { state ->
                        val speed = evaluateVelocityAt(state.currentProgress, state.points, state.isSmooth)
                        val step = 0.012f * speed
                        val nextProgress = state.currentProgress + step
                        if (nextProgress >= 1f) {
                            val startSpeed = evaluateVelocityAt(0f, state.points, state.isSmooth)
                            state.copy(
                                currentProgress = 0f,
                                instantSpeed = startSpeed,
                                isPlaying = false
                            )
                        } else {
                            val nextSpeed = evaluateVelocityAt(nextProgress, state.points, state.isSmooth)
                            state.copy(
                                currentProgress = nextProgress,
                                instantSpeed = nextSpeed
                            )
                        }
                    }
                }
            }
        }
    }

    fun selectPoint(pointId: String) {
        _uiState.update { it.copy(selectedPointId = pointId) }
    }

    fun updatePoint(pointId: String, targetTime: Float, targetSpeed: Float) {
        _uiState.update { state ->
            val sorted = state.points.sortedBy { it.timeProgress }
            val idx = sorted.indexOfFirst { it.id == pointId }
            if (idx < 0) return@update state

            val clampedSpeed = targetSpeed.coerceIn(0.1f, 4.0f)
            val clampedTime = when (idx) {
                0 -> 0.0f
                sorted.lastIndex -> 1.0f
                else -> targetTime.coerceIn(0.03f, 0.97f)
            }

            val updated = sorted.mapIndexed { i, pt ->
                if (i == idx) pt.copy(timeProgress = clampedTime, speedMultiplier = clampedSpeed) else pt
            }.sortedBy { it.timeProgress }

            val speedAtCursor = evaluateVelocityAt(state.currentProgress, updated, state.isSmooth)
            state.copy(
                points = updated,
                selectedPointId = pointId,
                activePreset = null,
                instantSpeed = speedAtCursor
            )
        }
    }

    fun addPointAtCurrentTime() {
        _uiState.update { state ->
            val p = state.currentProgress.coerceIn(0.05f, 0.95f)
            val existing = state.points.firstOrNull { abs(it.timeProgress - p) < 0.035f }
            if (existing != null) {
                return@update state.copy(
                    selectedPointId = existing.id,
                    statusMessage = "توجد نقطة سرعة قريبة من هذا الموضع بالفعل"
                )
            }
            val currentSpeed = evaluateVelocityAt(p, state.points, state.isSmooth)
            val newId = "vp_${UUID.randomUUID().toString().take(6)}"
            val newPt = VelocityPoint(id = newId, timeProgress = p, speedMultiplier = currentSpeed)
            val updated = (state.points + newPt).sortedBy { it.timeProgress }
            state.copy(
                points = updated,
                selectedPointId = newId,
                activePreset = null,
                statusMessage = "تمت إضافة نقطة سرعة عند ${(p * 100).toInt()}%"
            )
        }
    }

    fun toggleSmooth() {
        _uiState.update { state ->
            val nextSmooth = !state.isSmooth
            val speed = evaluateVelocityAt(state.currentProgress, state.points, nextSmooth)
            state.copy(
                isSmooth = nextSmooth,
                instantSpeed = speed
            )
        }
    }

    fun applyPreset(preset: VelocityPreset) {
        val points = when (preset) {
            VelocityPreset.SPEED_0_25X -> listOf(
                VelocityPoint("vp_0", 0f, 0.25f),
                VelocityPoint("vp_1", 0.5f, 0.25f),
                VelocityPoint("vp_2", 1f, 0.25f)
            )
            VelocityPreset.SPEED_0_5X -> listOf(
                VelocityPoint("vp_0", 0f, 0.5f),
                VelocityPoint("vp_1", 0.5f, 0.5f),
                VelocityPoint("vp_2", 1f, 0.5f)
            )
            VelocityPreset.SPEED_1_0X -> listOf(
                VelocityPoint("vp_0", 0f, 1.0f),
                VelocityPoint("vp_1", 0.5f, 1.0f),
                VelocityPoint("vp_2", 1f, 1.0f)
            )
            VelocityPreset.SPEED_2_0X -> listOf(
                VelocityPoint("vp_0", 0f, 2.0f),
                VelocityPoint("vp_1", 0.5f, 2.0f),
                VelocityPoint("vp_2", 1f, 2.0f)
            )
            VelocityPreset.RAMP_UP -> listOf(
                VelocityPoint("vp_0", 0f, 0.35f),
                VelocityPoint("vp_1", 0.5f, 1.25f),
                VelocityPoint("vp_2", 1f, 3.20f)
            )
            VelocityPreset.RAMP_DOWN -> listOf(
                VelocityPoint("vp_0", 0f, 3.20f),
                VelocityPoint("vp_1", 0.5f, 1.15f),
                VelocityPoint("vp_2", 1f, 0.30f)
            )
            VelocityPreset.PULSE -> listOf(
                VelocityPoint("vp_0", 0f, 0.45f),
                VelocityPoint("vp_1", 0.30f, 3.10f),
                VelocityPoint("vp_2", 0.65f, 0.40f),
                VelocityPoint("vp_3", 1f, 2.60f)
            )
        }
        _uiState.update { state ->
            val speed = evaluateVelocityAt(state.currentProgress, points, state.isSmooth)
            state.copy(
                points = points,
                activePreset = preset,
                selectedPointId = points.firstOrNull()?.id,
                instantSpeed = speed
            )
        }
    }

    fun createAndSave(onDone: () -> Unit) {
        val state = _uiState.value
        VideoStarRepository.applyVelocityCurve(
            projectId = projectId,
            points = state.points,
            isSmooth = state.isSmooth,
            sceneIndex = sceneIndex
        )
        onDone()
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    override fun onCleared() {
        playbackJob?.cancel()
        super.onCleared()
    }
}
