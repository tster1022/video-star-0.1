package com.example.ui.screens.transform

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.VideoStarRepository
import com.example.model.AspectRatioMode
import com.example.model.MediaClip
import com.example.model.SceneTransform
import com.example.model.TimelineScene
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TransformAnimationMode(val label: String) {
    STATIC("Static"),
    ANIMATED("Start ➔ End")
}

enum class TransformEndpoint(val label: String) {
    START("Start"),
    END("End")
}

data class TransformUiState(
    val projectId: String = "",
    val sceneIndex: Int = 0,
    val baseScene: TimelineScene? = null,
    val mediaClip: MediaClip? = null,
    val aspectRatio: AspectRatioMode = AspectRatioMode.SQUARE,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val endOffsetX: Float = 0f,
    val endOffsetY: Float = 0f,
    val endScale: Float = 1f,
    val endRotation: Float = 0f,
    val isAnimatedMode: Boolean = false,
    val editingEndpoint: TransformEndpoint = TransformEndpoint.START,
    val easing: String = "Smooth",
    val isPlaying: Boolean = false,
    val previewProgress: Float = 0f,
    val isFlippedHorizontal: Boolean = false,
    val isFlippedVertical: Boolean = false,
    val mirrorEdges: Boolean = false,
    val isGridVisible: Boolean = true,
    val backgroundColor: Color = Color(0xFF000000),
    val isColorPickerVisible: Boolean = false
) {
    val activeScale: Float
        get() = if (isAnimatedMode && editingEndpoint == TransformEndpoint.END) endScale else scale

    val activeRotation: Float
        get() = if (isAnimatedMode && editingEndpoint == TransformEndpoint.END) endRotation else rotation

    val activeOffsetX: Float
        get() = if (isAnimatedMode && editingEndpoint == TransformEndpoint.END) endOffsetX else offsetX

    val activeOffsetY: Float
        get() = if (isAnimatedMode && editingEndpoint == TransformEndpoint.END) endOffsetY else offsetY

    val effectivePreviewProgress: Float
        get() = when {
            !isAnimatedMode -> 0f
            isPlaying -> previewProgress
            editingEndpoint == TransformEndpoint.END -> 1f
            else -> 0f
        }

    val previewLocalPositionMs: Long
        get() {
            val dur = (baseScene?.effectiveDurationMs ?: mediaClip?.effectiveDurationMs ?: 3_000L).coerceAtLeast(300L)
            return (effectivePreviewProgress * dur).toLong().coerceIn(0L, dur)
        }

    val previewScene: TimelineScene?
        get() {
            val bgArgb = (backgroundColor.value shr 32).toLong() and 0xFFFFFFFFL
            val editedTransform = SceneTransform(
                scale = scale,
                rotation = rotation,
                offsetX = offsetX,
                offsetY = offsetY,
                flipH = isFlippedHorizontal,
                flipV = isFlippedVertical,
                bgColorArgb = bgArgb,
                mirrorEdges = mirrorEdges,
                isFitMode = baseScene?.transform?.isFitMode ?: mediaClip?.isFitMode ?: false,
                endScale = if (isAnimatedMode) endScale else scale,
                endRotation = if (isAnimatedMode) endRotation else rotation,
                endOffsetX = if (isAnimatedMode) endOffsetX else offsetX,
                endOffsetY = if (isAnimatedMode) endOffsetY else offsetY,
                isAnimated = isAnimatedMode,
                easing = easing
            )
            return baseScene?.copy(transform = editedTransform)
                ?: mediaClip?.let { clip ->
                    TimelineScene(
                        id = "preview_transform",
                        title = "Preview",
                        durationMs = clip.effectiveDurationMs,
                        clipId = clip.id,
                        mediaUriString = clip.uriString,
                        isVideo = clip is MediaClip.Video,
                        clip = clip,
                        transform = editedTransform
                    )
                }
        }
}

class TransformViewModel(
    private val projectId: String,
    private val sceneIndex: Int = 0
) : ViewModel() {

    private val project = VideoStarRepository.getProjectById(projectId)
    private val targetScene = project?.scenes?.getOrNull(sceneIndex)
    private val clip = targetScene?.clip ?: project?.primaryClip
    private val trim = project?.trimAdjustment
    private val sceneTransform = targetScene?.transform

    private val initialOffsetX = sceneTransform?.offsetX ?: clip?.offsetX ?: trim?.offsetX ?: 0f
    private val initialOffsetY = sceneTransform?.offsetY ?: clip?.offsetY ?: trim?.offsetY ?: 0f
    private val initialScale = sceneTransform?.scale ?: clip?.scale ?: trim?.scale ?: 1f
    private val initialRotation = sceneTransform?.rotation ?: clip?.rotation ?: trim?.rotation ?: 0f

    private val _uiState = MutableStateFlow(
        TransformUiState(
            projectId = projectId,
            sceneIndex = sceneIndex,
            baseScene = targetScene,
            mediaClip = clip,
            aspectRatio = project?.aspectRatio ?: AspectRatioMode.SQUARE,
            offsetX = initialOffsetX,
            offsetY = initialOffsetY,
            scale = initialScale,
            rotation = initialRotation,
            endOffsetX = if (sceneTransform?.isAnimated == true) sceneTransform.endOffsetX else initialOffsetX,
            endOffsetY = if (sceneTransform?.isAnimated == true) sceneTransform.endOffsetY else initialOffsetY,
            endScale = if (sceneTransform?.isAnimated == true) sceneTransform.endScale else initialScale,
            endRotation = if (sceneTransform?.isAnimated == true) sceneTransform.endRotation else initialRotation,
            isAnimatedMode = sceneTransform?.isAnimated ?: false,
            easing = sceneTransform?.easing ?: "Smooth",
            isFlippedHorizontal = sceneTransform?.flipH ?: clip?.isFlippedHorizontal ?: trim?.isFlippedHorizontal ?: false,
            isFlippedVertical = sceneTransform?.flipV ?: clip?.isFlippedVertical ?: trim?.isFlippedVertical ?: false,
            mirrorEdges = sceneTransform?.mirrorEdges ?: false,
            backgroundColor = (sceneTransform?.bgColorArgb ?: clip?.backgroundColorArgb ?: trim?.backgroundColorArgb)
                ?.let { Color(it.toInt()) } ?: Color(0xFF000000)
        )
    )
    val uiState: StateFlow<TransformUiState> = _uiState.asStateFlow()

    private var playJob: Job? = null

    fun setAnimationMode(mode: TransformAnimationMode) {
        val animated = mode == TransformAnimationMode.ANIMATED
        if (!animated) {
            playJob?.cancel()
        }
        _uiState.update { state ->
            val syncEnd = animated && !state.isAnimatedMode
            state.copy(
                isAnimatedMode = animated,
                isPlaying = if (!animated) false else state.isPlaying,
                editingEndpoint = if (!animated) TransformEndpoint.START else state.editingEndpoint,
                endScale = if (syncEnd) state.scale else state.endScale,
                endRotation = if (syncEnd) state.rotation else state.endRotation,
                endOffsetX = if (syncEnd) state.offsetX else state.endOffsetX,
                endOffsetY = if (syncEnd) state.offsetY else state.endOffsetY
            )
        }
    }

    fun selectEndpoint(endpoint: TransformEndpoint) {
        playJob?.cancel()
        _uiState.update { it.copy(editingEndpoint = endpoint, isPlaying = false) }
    }

    fun selectEasing(easing: String) {
        _uiState.update { it.copy(easing = easing) }
    }

    fun togglePlayback() {
        val nextPlaying = !_uiState.value.isPlaying
        if (!nextPlaying) {
            playJob?.cancel()
            _uiState.update { it.copy(isPlaying = false) }
            return
        }
        _uiState.update { it.copy(isAnimatedMode = true, isPlaying = true, previewProgress = 0f) }
        playJob?.cancel()
        playJob = viewModelScope.launch {
            while (_uiState.value.isPlaying) {
                delay(30L)
                _uiState.update { state ->
                    val dur = (state.baseScene?.effectiveDurationMs ?: state.mediaClip?.effectiveDurationMs ?: 3_000L)
                        .coerceAtLeast(300L)
                    val step = (30f / dur.toFloat()).coerceIn(0.005f, 0.08f)
                    val next = (state.previewProgress + step).let { if (it > 1f) 0f else it }
                    state.copy(previewProgress = next)
                }
            }
        }
    }

    fun onTransformGesture(panX: Float, panY: Float, zoomChange: Float, rotationChange: Float) {
        _uiState.update { state ->
            if (state.isAnimatedMode && state.editingEndpoint == TransformEndpoint.END) {
                state.copy(
                    isPlaying = false,
                    endOffsetX = (state.endOffsetX + panX).coerceIn(-600f, 600f),
                    endOffsetY = (state.endOffsetY + panY).coerceIn(-600f, 600f),
                    endScale = (state.endScale * zoomChange).coerceIn(0.25f, 3.0f),
                    endRotation = normalizeAngle(state.endRotation + rotationChange)
                )
            } else {
                state.copy(
                    isPlaying = false,
                    offsetX = (state.offsetX + panX).coerceIn(-600f, 600f),
                    offsetY = (state.offsetY + panY).coerceIn(-600f, 600f),
                    scale = (state.scale * zoomChange).coerceIn(0.25f, 3.0f),
                    rotation = normalizeAngle(state.rotation + rotationChange)
                )
            }
        }
    }

    fun resetAll() {
        _uiState.update { state ->
            if (state.isAnimatedMode && state.editingEndpoint == TransformEndpoint.END) {
                state.copy(
                    isPlaying = false,
                    endOffsetX = 0f,
                    endOffsetY = 0f,
                    endScale = 1f,
                    endRotation = 0f
                )
            } else {
                state.copy(
                    isPlaying = false,
                    offsetX = 0f,
                    offsetY = 0f,
                    scale = 1f,
                    rotation = 0f,
                    isFlippedHorizontal = false,
                    isFlippedVertical = false,
                    mirrorEdges = false
                )
            }
        }
    }

    fun toggleFlipHorizontal() {
        _uiState.update { it.copy(isFlippedHorizontal = !it.isFlippedHorizontal) }
    }

    fun toggleFlipVertical() {
        _uiState.update { it.copy(isFlippedVertical = !it.isFlippedVertical) }
    }

    fun toggleMirrorEdges() {
        _uiState.update { it.copy(mirrorEdges = !it.mirrorEdges) }
    }

    fun rotateBy90() {
        _uiState.update { state ->
            if (state.isAnimatedMode && state.editingEndpoint == TransformEndpoint.END) {
                state.copy(isPlaying = false, endRotation = normalizeAngle(state.endRotation + 90f))
            } else {
                state.copy(isPlaying = false, rotation = normalizeAngle(state.rotation + 90f))
            }
        }
    }

    fun toggleGrid() {
        _uiState.update { it.copy(isGridVisible = !it.isGridVisible) }
    }

    fun nudge(dx: Float, dy: Float) {
        _uiState.update { state ->
            if (state.isAnimatedMode && state.editingEndpoint == TransformEndpoint.END) {
                state.copy(
                    isPlaying = false,
                    endOffsetX = (state.endOffsetX + dx).coerceIn(-600f, 600f),
                    endOffsetY = (state.endOffsetY + dy).coerceIn(-600f, 600f)
                )
            } else {
                state.copy(
                    isPlaying = false,
                    offsetX = (state.offsetX + dx).coerceIn(-600f, 600f),
                    offsetY = (state.offsetY + dy).coerceIn(-600f, 600f)
                )
            }
        }
    }

    fun setZoom(scale: Float) {
        val clamped = scale.coerceIn(0.25f, 3.0f)
        _uiState.update { state ->
            if (state.isAnimatedMode && state.editingEndpoint == TransformEndpoint.END) {
                state.copy(isPlaying = false, endScale = clamped)
            } else {
                state.copy(isPlaying = false, scale = clamped)
            }
        }
    }

    fun resetZoom() {
        setZoom(1f)
    }

    fun setAngle(angle: Float) {
        val clamped = angle.coerceIn(-180f, 180f)
        _uiState.update { state ->
            if (state.isAnimatedMode && state.editingEndpoint == TransformEndpoint.END) {
                state.copy(isPlaying = false, endRotation = clamped)
            } else {
                state.copy(isPlaying = false, rotation = clamped)
            }
        }
    }

    fun resetAngle() {
        setAngle(0f)
    }

    fun toggleColorPicker() {
        _uiState.update { it.copy(isColorPickerVisible = !it.isColorPickerVisible) }
    }

    fun selectBackgroundColor(color: Color) {
        _uiState.update {
            it.copy(
                backgroundColor = color,
                isColorPickerVisible = false
            )
        }
    }

    fun createAndSave(onDone: () -> Unit) {
        playJob?.cancel()
        val state = _uiState.value
        val colorArgb = (state.backgroundColor.value shr 32).toLong() and 0xFFFFFFFFL
        VideoStarRepository.applyQuickTransform(
            projectId = projectId,
            offsetX = state.offsetX,
            offsetY = state.offsetY,
            scale = state.scale,
            rotation = state.rotation,
            flipH = state.isFlippedHorizontal,
            flipV = state.isFlippedVertical,
            backgroundColorArgb = colorArgb,
            mirrorEdges = state.mirrorEdges,
            sceneIndex = sceneIndex,
            endOffsetX = if (state.isAnimatedMode) state.endOffsetX else state.offsetX,
            endOffsetY = if (state.isAnimatedMode) state.endOffsetY else state.offsetY,
            endScale = if (state.isAnimatedMode) state.endScale else state.scale,
            endRotation = if (state.isAnimatedMode) state.endRotation else state.rotation,
            isAnimated = state.isAnimatedMode,
            easing = state.easing
        )
        onDone()
    }

    private fun normalizeAngle(raw: Float): Float {
        var a = raw % 360f
        if (a > 180f) a -= 360f
        if (a < -180f) a += 360f
        return a
    }

    override fun onCleared() {
        playJob?.cancel()
        super.onCleared()
    }
}
