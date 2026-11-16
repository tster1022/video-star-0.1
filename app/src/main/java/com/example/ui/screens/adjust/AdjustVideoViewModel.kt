package com.example.ui.screens.adjust

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MediaImportHelper
import com.example.data.VideoStarRepository
import com.example.model.AspectRatioMode
import com.example.model.ClipColorGrading
import com.example.model.MediaClip
import com.example.model.VideoProject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sin

data class AdjustVideoUiState(
    val project: VideoProject? = null,
    val mediaUriString: String = "",
    val durationMs: Long = 5_000L,
    val sourceFps: Int = 30,
    val currentPositionMs: Long = 0L,
    val startMs: Long = 0L,
    val endMs: Long = 5_000L,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val isFitMode: Boolean = false,
    val isGridVisible: Boolean = true,
    val snapToCenterEnabled: Boolean = true,
    val isPlaying: Boolean = false,
    val cropAspectRatio: AspectRatioMode? = null,
    val backgroundColor: Color = Color.Black,
    val isColorPickerVisible: Boolean = false,
    val isZoomSliderVisible: Boolean = false,
    val colorGrading: ClipColorGrading = ClipColorGrading(),
    val waveformSamples: List<Float> = emptyList()
) {
    val trimmedDurationMs: Long
        get() = (endMs - startMs).coerceAtLeast(200L)

    val projectFps: Int
        get() = project?.frameRate ?: 30

    val effectiveAspectRatio: AspectRatioMode
        get() = cropAspectRatio ?: project?.aspectRatio ?: AspectRatioMode.SQUARE
}

class AdjustVideoViewModel(
    private val projectId: String,
    initialUriString: String,
    initialDurationMs: Long,
    initialSourceFps: Int = 30,
    private val targetLayerIndex: Int? = null,
    private val replaceSceneIndex: Int? = null
) : ViewModel() {

    private val initialProject = VideoStarRepository.getProjectById(projectId)
    private val safeDuration = initialDurationMs.coerceAtLeast(500L)

    private val _uiState = MutableStateFlow(
        AdjustVideoUiState(
            project = initialProject,
            mediaUriString = initialUriString,
            durationMs = safeDuration,
            sourceFps = initialSourceFps.coerceIn(12, 120),
            currentPositionMs = 0L,
            startMs = 0L,
            endMs = safeDuration,
            waveformSamples = generateWaveformSamples(56, safeDuration)
        )
    )
    val uiState: StateFlow<AdjustVideoUiState> = _uiState.asStateFlow()

    init {
        if (initialUriString.isNotBlank()) {
            viewModelScope.launch {
                val extracted = MediaImportHelper.extractAudioWaveformSamples(initialUriString, barCount = 56)
                if (extracted.isNotEmpty()) {
                    _uiState.update { it.copy(waveformSamples = extracted) }
                }
            }
        }
    }

    fun onMediaDurationDiscovered(discoveredMs: Long) {
        if (discoveredMs <= 0L) return
        _uiState.update { state ->
            val wasAtEnd = abs(state.endMs - state.durationMs) < 120L
            val newEnd = if (wasAtEnd) discoveredMs else state.endMs.coerceAtMost(discoveredMs)
            state.copy(
                durationMs = discoveredMs,
                endMs = newEnd.coerceAtLeast((state.startMs + 200L).coerceAtMost(discoveredMs))
            )
        }
    }

    fun syncPlaybackPosition(positionMs: Long) {
        _uiState.update { state ->
            state.copy(currentPositionMs = positionMs.coerceIn(0L, state.durationMs))
        }
    }

    fun onPlaybackEnded() {
        _uiState.update { state ->
            state.copy(
                isPlaying = false,
                currentPositionMs = state.startMs
            )
        }
    }

    fun togglePlayback() {
        _uiState.update { state ->
            val next = !state.isPlaying
            val resetPos = if (next && (state.currentPositionMs < state.startMs || state.currentPositionMs >= state.endMs)) {
                state.startMs
            } else {
                state.currentPositionMs
            }
            state.copy(isPlaying = next, currentPositionMs = resetPos)
        }
    }

    fun seekProgress(progress: Float) {
        val dur = _uiState.value.durationMs.coerceAtLeast(1L)
        val pos = (progress.coerceIn(0f, 1f) * dur).toLong()
        _uiState.update { it.copy(currentPositionMs = pos, isPlaying = false) }
    }

    fun stepFrameBackward() {
        _uiState.update { state ->
            val frameStep = (1000L / state.sourceFps.coerceAtLeast(1)).coerceAtLeast(16L)
            val next = (state.currentPositionMs - frameStep).coerceIn(0L, state.durationMs)
            state.copy(currentPositionMs = next, isPlaying = false)
        }
    }

    fun stepFrameForward() {
        _uiState.update { state ->
            val frameStep = (1000L / state.sourceFps.coerceAtLeast(1)).coerceAtLeast(16L)
            val next = (state.currentPositionMs + frameStep).coerceIn(0L, state.durationMs)
            state.copy(currentPositionMs = next, isPlaying = false)
        }
    }

    fun setStartToCurrent() {
        _uiState.update { state ->
            val clampedStart = state.currentPositionMs.coerceIn(0L, (state.endMs - 200L).coerceAtLeast(0L))
            state.copy(startMs = clampedStart)
        }
    }

    fun setEndToCurrent() {
        _uiState.update { state ->
            val clampedEnd = state.currentPositionMs.coerceIn(
                (state.startMs + 200L).coerceAtMost(state.durationMs),
                state.durationMs
            )
            state.copy(endMs = clampedEnd)
        }
    }

    fun onTransformGesture(panX: Float, panY: Float, zoomChange: Float, rotationChange: Float) {
        _uiState.update { state ->
            val rawX = (state.offsetX + panX).coerceIn(-600f, 600f)
            val rawY = (state.offsetY + panY).coerceIn(-600f, 600f)
            val snappedX = if (state.snapToCenterEnabled && abs(rawX) < 3.5f) 0f else rawX
            val snappedY = if (state.snapToCenterEnabled && abs(rawY) < 3.5f) 0f else rawY
            state.copy(
                offsetX = snappedX,
                offsetY = snappedY,
                scale = (state.scale * zoomChange).coerceIn(0.35f, 3.5f),
                rotation = state.rotation + rotationChange
            )
        }
    }

    fun resetTransform() {
        _uiState.update {
            it.copy(offsetX = 0f, offsetY = 0f, scale = 1f, rotation = 0f)
        }
    }

    fun rotateBy90() {
        _uiState.update { state ->
            state.copy(rotation = (state.rotation + 90f) % 360f)
        }
    }

    fun cycleCropAspectRatio() {
        val order = listOf(
            AspectRatioMode.PORTRAIT,
            AspectRatioMode.PORTRAIT_3_4,
            AspectRatioMode.SQUARE,
            AspectRatioMode.LANDSCAPE_4_3,
            AspectRatioMode.LANDSCAPE
        )
        _uiState.update { state ->
            val current = state.effectiveAspectRatio
            val nextIdx = (order.indexOf(current) + 1) % order.size
            state.copy(cropAspectRatio = order[nextIdx])
        }
    }

    fun nudgeUp() {
        _uiState.update { it.copy(offsetY = (it.offsetY - 1f).coerceIn(-600f, 600f)) }
    }

    fun nudgeDown() {
        _uiState.update { it.copy(offsetY = (it.offsetY + 1f).coerceIn(-600f, 600f)) }
    }

    fun nudgeLeft() {
        _uiState.update { it.copy(offsetX = (it.offsetX - 1f).coerceIn(-600f, 600f)) }
    }

    fun nudgeRight() {
        _uiState.update { it.copy(offsetX = (it.offsetX + 1f).coerceIn(-600f, 600f)) }
    }

    fun toggleFitMode() {
        _uiState.update { it.copy(isFitMode = !it.isFitMode) }
    }

    fun setScale(scale: Float) {
        _uiState.update { it.copy(scale = scale.coerceIn(0.35f, 3.5f)) }
    }

    fun stepZoom() {
        _uiState.update { state ->
            val nextScale = when {
                state.scale < 1.2f -> 1.25f
                state.scale < 1.55f -> 1.6f
                else -> 1f
            }
            state.copy(scale = nextScale)
        }
    }

    fun toggleZoomSlider() {
        _uiState.update {
            it.copy(
                isZoomSliderVisible = !it.isZoomSliderVisible,
                isColorPickerVisible = false
            )
        }
    }

    fun toggleGrid() {
        _uiState.update {
            val next = !it.isGridVisible
            it.copy(isGridVisible = next, snapToCenterEnabled = next)
        }
    }

    fun toggleColorPicker() {
        _uiState.update {
            it.copy(
                isColorPickerVisible = !it.isColorPickerVisible,
                isZoomSliderVisible = false
            )
        }
    }

    fun selectBackgroundColor(color: Color) {
        _uiState.update { it.copy(backgroundColor = color, isColorPickerVisible = false) }
    }

    fun updateBrightness(value: Float) {
        _uiState.update {
            it.copy(colorGrading = it.colorGrading.copy(brightness = value.coerceIn(-0.5f, 0.5f)))
        }
    }

    fun updateContrast(value: Float) {
        _uiState.update {
            it.copy(colorGrading = it.colorGrading.copy(contrast = value.coerceIn(0.5f, 1.8f)))
        }
    }

    fun updateSaturation(value: Float) {
        _uiState.update {
            it.copy(colorGrading = it.colorGrading.copy(saturation = value.coerceIn(0f, 2f)))
        }
    }

    fun updateHue(value: Float) {
        _uiState.update {
            it.copy(colorGrading = it.colorGrading.copy(hue = value.coerceIn(-180f, 180f)))
        }
    }

    fun resetColorGrading() {
        _uiState.update { it.copy(colorGrading = ClipColorGrading()) }
    }

    fun commitMakeVideo(onDone: () -> Unit) {
        val state = _uiState.value
        val clip = MediaClip.Video(
            id = "vid_${UUID.randomUUID().toString().take(8)}",
            uriString = state.mediaUriString,
            startTrimMs = state.startMs,
            endTrimMs = state.endMs,
            durationMs = state.durationMs,
            sourceFps = state.sourceFps,
            scale = state.scale,
            offsetX = state.offsetX,
            offsetY = state.offsetY,
            rotation = state.rotation,
            isFitMode = state.isFitMode,
            backgroundColorArgb = state.backgroundColor.toArgb().toLong() and 0xFFFFFFFFL,
            colorGrading = state.colorGrading
        )
        VideoStarRepository.addOrUpdateVideoClip(
            projectId = projectId,
            clip = clip,
            targetLayerIndex = targetLayerIndex,
            replaceSceneIndex = replaceSceneIndex
        )
        onDone()
    }
}

private fun generateWaveformSamples(count: Int, seedMs: Long): List<Float> {
    val phaseShift = (seedMs % 100L) / 25f
    return List(count) { i ->
        val wave = abs(sin((i + phaseShift) * 0.42f)) * 0.65f + abs(sin((i + phaseShift) * 1.13f)) * 0.35f
        wave.coerceIn(0.18f, 1.0f)
    }
}
