package com.example.ui.screens.multilayer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.VideoStarRepository
import com.example.model.EditorLayer
import com.example.model.LayerEffectType
import com.example.model.LayerMediaType
import com.example.model.LayerSlot
import com.example.model.MaskMode
import com.example.model.MaskShape
import com.example.model.MultiLayerTab
import com.example.model.TextLayerSpec
import com.example.model.defaultLayers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface MultiLayerDialogState {
    data object QrImport : MultiLayerDialogState
    data object Info : MultiLayerDialogState
    data object ColorWheelPicker : MultiLayerDialogState
    data object ManageLayers : MultiLayerDialogState
    data class PickLayerMedia(val layerIndex: Int) : MultiLayerDialogState
    data class PickLayerColor(val layerIndex: Int) : MultiLayerDialogState
}

data class MultiLayerUiState(
    val projectId: String = "",
    val projectAspectRatio: Float = 9f / 16f,
    val layers: List<EditorLayer> = defaultLayers(),
    val selectedLayerIndex: Int = 0,
    val stageBackgroundColor: Color = Color(0xFF141418),
    val activeBottomTab: MultiLayerTab? = null,
    val isPlaying: Boolean = false,
    val isMuted: Boolean = false,
    val stageProgress: Float = 0.25f,
    val isGridOverlayVisible: Boolean = false,
    val hasTouchedStage: Boolean = false,
    val activeDialog: MultiLayerDialogState? = null,
    val statusMessage: String? = null
) {
    val activeLayer: EditorLayer?
        get() = layers.getOrNull(selectedLayerIndex.coerceIn(0, (layers.size - 1).coerceAtLeast(0)))

    val layerSlots: List<LayerSlot>
        get() = layers.map { it.toLayerSlot() }
}

class MultiLayerViewModel(private val projectId: String) : ViewModel() {
    private val initialProject = VideoStarRepository.getProjectById(projectId)

    private val _uiState = MutableStateFlow(
        MultiLayerUiState(
            projectId = projectId,
            projectAspectRatio = initialProject?.aspectRatio?.ratio ?: (9f / 16f),
            layers = initialProject?.layers ?: defaultLayers()
        )
    )
    val uiState: StateFlow<MultiLayerUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null

    fun refreshFromRepository() {
        val project = VideoStarRepository.getProjectById(projectId) ?: return
        val latest = project.layers
        _uiState.update { state ->
            state.copy(
                projectAspectRatio = project.aspectRatio.ratio,
                layers = latest,
                selectedLayerIndex = state.selectedLayerIndex.coerceIn(0, (latest.size - 1).coerceAtLeast(0))
            )
        }
    }

    fun syncToRepository() {
        VideoStarRepository.syncProjectLayers(projectId, _uiState.value.layers)
    }

    fun onLayerSlotClicked(index: Int): Boolean {
        val layers = _uiState.value.layers
        if (index !in layers.indices) return false
        _uiState.update { it.copy(selectedLayerIndex = index) }
        return layers[index].layerMediaType == LayerMediaType.NONE
    }

    fun selectLayer(index: Int) {
        _uiState.update { state ->
            state.copy(selectedLayerIndex = index.coerceIn(0, (state.layers.size - 1).coerceAtLeast(0)))
        }
    }

    fun assignColorToLayer(index: Int, color: Color) {
        val argb = color.toArgb().toLong() and 0xFFFFFFFFL
        _uiState.update { state ->
            if (index !in state.layers.indices) return@update state
            val updated = state.layers.mapIndexed { i, layer ->
                if (i == index) {
                    layer.copy(mediaClip = null, backgroundColorArgb = argb)
                } else layer
            }
            state.copy(
                layers = updated,
                selectedLayerIndex = index,
                activeDialog = null,
                statusMessage = "تم تعيين لون للطبقة ${updated[index].number}"
            )
        }
        syncToRepository()
    }

    fun clearLayerSlot(index: Int) {
        _uiState.update { state ->
            if (index !in state.layers.indices) return@update state
            val updated = state.layers.mapIndexed { i, layer ->
                if (i == index) {
                    layer.copy(
                        mediaClip = null,
                        backgroundColorArgb = null,
                        offsetX = 0f,
                        offsetY = 0f,
                        scale = 1f,
                        rotation = 0f,
                        textOverlay = ""
                    )
                } else layer
            }
            state.copy(
                layers = updated,
                selectedLayerIndex = index,
                statusMessage = "تم تفريغ الطبقة ${updated[index].number}"
            )
        }
        syncToRepository()
    }

    fun markStageTouched() {
        if (!_uiState.value.hasTouchedStage) {
            _uiState.update { it.copy(hasTouchedStage = true) }
        }
    }

    fun onLayerTransformGesture(panX: Float, panY: Float, zoomChange: Float, rotationChange: Float) {
        _uiState.update { state ->
            val targetIdx = state.selectedLayerIndex
            val updated = state.layers.mapIndexed { idx, layer ->
                if (idx == targetIdx) {
                    layer.copy(
                        offsetX = (layer.offsetX + panX).coerceIn(-600f, 600f),
                        offsetY = (layer.offsetY + panY).coerceIn(-800f, 800f),
                        scale = (layer.scale * zoomChange).coerceIn(0.3f, 3.2f),
                        rotation = layer.rotation + rotationChange
                    )
                } else layer
            }
            state.copy(layers = updated, hasTouchedStage = true)
        }
    }

    fun resetActiveLayerTransform() {
        updateActiveLayer { layer ->
            layer.copy(offsetX = 0f, offsetY = 0f, scale = 1f, rotation = 0f)
        }
        _uiState.update { it.copy(statusMessage = "تمت إعادة ضبط موضع وحجم الطبقة") }
    }

    fun toggle3DMode() {
        updateActiveLayer { layer ->
            layer.copy(is3DEnabled = !layer.is3DEnabled)
        }
    }

    fun toggleGridOverlay() {
        _uiState.update { it.copy(isGridOverlayVisible = !it.isGridOverlayVisible) }
    }

    fun togglePlayback() {
        val next = !_uiState.value.isPlaying
        _uiState.update { it.copy(isPlaying = next) }
        playbackJob?.cancel()
        if (next) {
            playbackJob = viewModelScope.launch {
                while (_uiState.value.isPlaying) {
                    delay(45L)
                    _uiState.update { state ->
                        val nextProgress = state.stageProgress + 0.015f
                        if (nextProgress >= 1f) state.copy(stageProgress = 0f, isPlaying = false)
                        else state.copy(stageProgress = nextProgress)
                    }
                }
            }
        }
    }

    fun onScrubStage(progress: Float) {
        _uiState.update { it.copy(stageProgress = progress.coerceIn(0f, 1f)) }
    }

    fun toggleMute() {
        _uiState.update { it.copy(isMuted = !it.isMuted) }
    }

    fun onBottomTabClicked(tab: MultiLayerTab) {
        _uiState.update { state ->
            val next = if (state.activeBottomTab == tab) null else tab
            state.copy(activeBottomTab = next)
        }
    }

    fun dismissBottomPanel() {
        _uiState.update { it.copy(activeBottomTab = null) }
    }

    fun setStageBackgroundColor(color: Color) {
        _uiState.update { it.copy(stageBackgroundColor = color) }
    }

    fun setLayerEffect(effect: LayerEffectType) {
        updateActiveLayer { it.copy(effect = effect) }
    }

    fun setLayerMask(mask: MaskShape) {
        updateActiveLayer {
            it.copy(
                maskMode = if (mask == MaskShape.NONE) MaskMode.NONE else MaskMode.SHAPE,
                maskShape = mask
            )
        }
    }

    fun updateLayerMask(
        mode: MaskMode,
        shape: MaskShape,
        feather: Float,
        invert: Boolean,
        linearAngle: Float
    ) {
        updateActiveLayer {
            it.copy(
                maskMode = mode,
                maskShape = shape,
                maskFeather = feather.coerceIn(0f, 1f),
                maskInvert = invert,
                maskLinearAngle = linearAngle.coerceIn(0f, 360f)
            )
        }
    }

    fun setLayerTextSpec(spec: TextLayerSpec) {
        updateActiveLayer {
            it.copy(
                textOverlay = spec.text,
                textSpec = spec
            )
        }
    }

    fun setLayerTint(color: Color) {
        updateActiveLayer { it.copy(tintColor = color) }
    }

    fun setLayerOpacity(opacity: Float) {
        updateActiveLayer { it.copy(opacity = opacity.coerceIn(0.15f, 1f)) }
    }

    fun applyLayoutPreset(preset: String) {
        updateActiveLayer { layer ->
            when (preset) {
                "مركز" -> layer.copy(offsetX = 0f, offsetY = 0f, scale = 1f, rotation = 0f, layoutPreset = preset)
                "انقسام علوي" -> layer.copy(offsetX = 0f, offsetY = -120f, scale = 0.72f, rotation = 0f, layoutPreset = preset)
                "تدوير مائل" -> layer.copy(offsetX = 0f, offsetY = 0f, scale = 0.9f, rotation = 15f, layoutPreset = preset)
                "تكبير درامي" -> layer.copy(offsetX = 0f, offsetY = 0f, scale = 1.35f, rotation = 0f, layoutPreset = preset)
                else -> layer.copy(layoutPreset = preset)
            }
        }
    }

    fun addNewLayer() {
        _uiState.update { state ->
            val nextNumber = (state.layers.maxOfOrNull { it.number } ?: 0) + 1
            val newLayer = EditorLayer(
                id = "layer_$nextNumber",
                number = nextNumber,
                mediaClip = null,
                scale = 1f
            )
            val updated = state.layers + newLayer
            state.copy(
                layers = updated,
                selectedLayerIndex = updated.lastIndex,
                activeDialog = null,
                statusMessage = "تمت إضافة الطبقة رقم $nextNumber"
            )
        }
    }

    fun applyQrPreset(presetName: String) {
        updateActiveLayer {
            it.copy(
                scale = 1.08f,
                rotation = -8f,
                is3DEnabled = true,
                effect = LayerEffectType.NEON_GLOW
            )
        }
        _uiState.update {
            it.copy(
                activeDialog = null,
                statusMessage = "تم استيراد قالب QR: $presetName"
            )
        }
    }

    fun captureSnapshot() {
        _uiState.update { it.copy(statusMessage = "تم التقاط معاينة الطبقة الحالية") }
    }

    fun openDialog(dialog: MultiLayerDialogState) {
        _uiState.update { it.copy(activeDialog = dialog) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(activeDialog = null) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun commitAndCreate(onDone: () -> Unit) {
        VideoStarRepository.saveProjectLayers(projectId, _uiState.value.layers)
        onDone()
    }

    private fun updateActiveLayer(transform: (EditorLayer) -> EditorLayer) {
        _uiState.update { state ->
            val targetIdx = state.selectedLayerIndex
            state.copy(
                layers = state.layers.mapIndexed { idx, layer ->
                    if (idx == targetIdx) transform(layer) else layer
                }
            )
        }
    }

    override fun onCleared() {
        playbackJob?.cancel()
        super.onCleared()
    }
}
