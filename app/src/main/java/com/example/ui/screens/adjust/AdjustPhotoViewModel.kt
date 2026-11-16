package com.example.ui.screens.adjust

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import com.example.data.VideoStarRepository
import com.example.model.AspectRatioMode
import com.example.model.ClipColorGrading
import com.example.model.MediaClip
import com.example.model.VideoProject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import kotlin.math.abs

data class AdjustPhotoUiState(
    val project: VideoProject? = null,
    val mediaUriString: String = "",
    val durationMs: Long = 3_000L,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val isFitMode: Boolean = false,
    val isFlippedHorizontal: Boolean = false,
    val isFlippedVertical: Boolean = false,
    val isGridVisible: Boolean = true,
    val snapToCenterEnabled: Boolean = true,
    val cropAspectRatio: AspectRatioMode? = null,
    val backgroundColor: Color = Color.Black,
    val isColorPickerVisible: Boolean = false,
    val isZoomSliderVisible: Boolean = false,
    val colorGrading: ClipColorGrading = ClipColorGrading()
) {
    val effectiveAspectRatio: AspectRatioMode
        get() = cropAspectRatio ?: project?.aspectRatio ?: AspectRatioMode.SQUARE
}

class AdjustPhotoViewModel(
    private val projectId: String,
    initialUriString: String,
    private val targetLayerIndex: Int? = null,
    private val replaceSceneIndex: Int? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AdjustPhotoUiState(
            project = VideoStarRepository.getProjectById(projectId),
            mediaUriString = initialUriString,
            durationMs = 3_000L
        )
    )
    val uiState: StateFlow<AdjustPhotoUiState> = _uiState.asStateFlow()

    fun setDurationSeconds(seconds: Float) {
        val ms = (seconds.coerceIn(0.5f, 10.0f) * 1000f).toLong()
        _uiState.update { it.copy(durationMs = ms) }
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
            it.copy(
                offsetX = 0f,
                offsetY = 0f,
                scale = 1f,
                rotation = 0f,
                isFlippedHorizontal = false,
                isFlippedVertical = false
            )
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

    fun nudge(dx: Float, dy: Float) {
        _uiState.update { state ->
            state.copy(
                offsetX = (state.offsetX + dx).coerceIn(-600f, 600f),
                offsetY = (state.offsetY + dy).coerceIn(-600f, 600f)
            )
        }
    }

    fun toggleFitMode() {
        _uiState.update { it.copy(isFitMode = !it.isFitMode) }
    }

    fun setScale(scale: Float) {
        _uiState.update { it.copy(scale = scale.coerceIn(0.35f, 3.5f)) }
    }

    fun toggleZoomSlider() {
        _uiState.update {
            it.copy(
                isZoomSliderVisible = !it.isZoomSliderVisible,
                isColorPickerVisible = false
            )
        }
    }

    fun toggleFlipHorizontal() {
        _uiState.update { it.copy(isFlippedHorizontal = !it.isFlippedHorizontal) }
    }

    fun toggleFlipVertical() {
        _uiState.update { it.copy(isFlippedVertical = !it.isFlippedVertical) }
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

    fun commitMakePhoto(onDone: () -> Unit) {
        val state = _uiState.value
        val clip = MediaClip.Photo(
            id = "img_${UUID.randomUUID().toString().take(8)}",
            uriString = state.mediaUriString,
            durationMs = state.durationMs,
            scale = state.scale,
            offsetX = state.offsetX,
            offsetY = state.offsetY,
            rotation = state.rotation,
            isFitMode = state.isFitMode,
            backgroundColorArgb = state.backgroundColor.toArgb().toLong() and 0xFFFFFFFFL,
            isFlippedHorizontal = state.isFlippedHorizontal,
            isFlippedVertical = state.isFlippedVertical,
            colorGrading = state.colorGrading
        )
        VideoStarRepository.addOrUpdatePhotoClip(
            projectId = projectId,
            clip = clip,
            targetLayerIndex = targetLayerIndex,
            replaceSceneIndex = replaceSceneIndex
        )
        onDone()
    }
}
