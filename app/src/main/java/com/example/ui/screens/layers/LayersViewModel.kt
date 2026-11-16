package com.example.ui.screens.layers

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.VideoStarRepository
import com.example.model.ClassicLayerTab
import com.example.model.EditorLayer
import com.example.model.LayerEffectType
import com.example.model.LayerKeyframe
import com.example.model.LayerMediaType
import com.example.model.LayerSlot
import com.example.model.MaskMode
import com.example.model.MaskShape
import com.example.model.TextLayerSpec
import com.example.model.defaultLayers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

sealed interface LayersDialogState {
    data object QrImport : LayersDialogState
    data object Info : LayersDialogState
    data object ColorWheel : LayersDialogState
    data object Hierarchy : LayersDialogState
    data object TipsAndPresets : LayersDialogState
    data class PickLayerMedia(val layerIndex: Int) : LayersDialogState
    data class PickLayerColor(val layerIndex: Int) : LayersDialogState
}

data class LayersUiState(
    val projectId: String = "",
    val projectAspectRatio: Float = 9f / 16f,
    val layers: List<EditorLayer> = defaultLayers(),
    val selectedLayerIndex: Int? = 0,
    val stageBackgroundColor: Color = Color(0xFF141418),
    val activeBottomTab: ClassicLayerTab? = null,
    val isPlaying: Boolean = false,
    val isMuted: Boolean = false,
    val stageProgress: Float = 0.15f,
    val hasTouchedStage: Boolean = false,
    val activeDialog: LayersDialogState? = null,
    val statusMessage: String? = null
) {
    val activeLayer: EditorLayer?
        get() = selectedLayerIndex?.let { layers.getOrNull(it) }

    val layerSlots: List<LayerSlot>
        get() = layers.map { it.toLayerSlot() }
}

class LayersViewModel(private val projectId: String) : ViewModel() {

    private val initialProject = VideoStarRepository.getProjectById(projectId)

    private val _uiState = MutableStateFlow(
        LayersUiState(
            projectId = projectId,
            projectAspectRatio = initialProject?.aspectRatio?.ratio ?: (9f / 16f),
            layers = initialProject?.layers ?: defaultLayers()
        )
    )
    val uiState: StateFlow<LayersUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null

    fun refreshFromRepository() {
        val project = VideoStarRepository.getProjectById(projectId) ?: return
        val latest = project.layers
        _uiState.update { state ->
            val safeIdx = (state.selectedLayerIndex ?: 0).coerceIn(0, (latest.size - 1).coerceAtLeast(0))
            state.copy(
                projectAspectRatio = project.aspectRatio.ratio,
                layers = latest,
                selectedLayerIndex = if (latest.isEmpty()) null else safeIdx
            )
        }
    }

    /**
     * Selects the tapped layer slot cleanly (never toggles off to null) and returns true
     * when the slot is currently empty (LayerMediaType.NONE) so the UI launches Photo Picker.
     */
    fun onLayerSlotClicked(index: Int): Boolean {
        val layers = _uiState.value.layers
        if (index !in layers.indices) return false
        _uiState.update { it.copy(selectedLayerIndex = index) }
        return layers[index].layerMediaType == LayerMediaType.NONE
    }

    fun selectLayer(index: Int) {
        _uiState.update { state ->
            if (index !in state.layers.indices) return@update state
            state.copy(selectedLayerIndex = index)
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

    fun ensureActiveLayerId(): String {
        val state = _uiState.value
        val active = state.activeLayer
        if (active != null) return active.id
        val first = state.layers.firstOrNull()
        if (first != null) {
            _uiState.update { it.copy(selectedLayerIndex = 0) }
            VideoStarRepository.syncProjectLayers(projectId, state.layers)
            return first.id
        }
        return "layer_1"
    }

    fun markStageTouched() {
        if (!_uiState.value.hasTouchedStage) {
            _uiState.update { it.copy(hasTouchedStage = true) }
        }
    }

    fun onLayerTransformGesture(panX: Float, panY: Float, zoomChange: Float, rotationChange: Float) {
        _uiState.update { state ->
            val idx = state.selectedLayerIndex ?: return@update state.copy(hasTouchedStage = true)
            state.copy(
                hasTouchedStage = true,
                layers = state.layers.mapIndexed { i, layer ->
                    if (i == idx) {
                        layer.copy(
                            offsetX = (layer.offsetX + panX).coerceIn(-600f, 600f),
                            offsetY = (layer.offsetY + panY).coerceIn(-800f, 800f),
                            scale = (layer.scale * zoomChange).coerceIn(0.25f, 3.2f),
                            rotation = layer.rotation + rotationChange
                        )
                    } else layer
                }
            )
        }
    }

    fun resetActiveLayerTransform() {
        updateActiveLayer { layer ->
            layer.copy(offsetX = 0f, offsetY = 0f, scale = 1f, rotation = 0f)
        }
        _uiState.update { it.copy(statusMessage = "تمت إعادة ضبط موضع وحجم الطبقة") }
    }

    fun toggle3D() {
        val target = _uiState.value.activeLayer ?: run {
            _uiState.update { it.copy(selectedLayerIndex = 0) }
            _uiState.value.layers.firstOrNull()
        } ?: return
        val next3D = !target.is3DEnabled
        updateSelectedOrFirstLayer { it.copy(is3DEnabled = next3D) }
        _uiState.update {
            it.copy(statusMessage = if (next3D) "تم تفعيل وضع 3D للطبقة ${target.number}" else "تم إيقاف وضع 3D")
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

    fun onBottomTabClick(tab: ClassicLayerTab) {
        if (tab == ClassicLayerTab.LAYER_EFFECTS && _uiState.value.selectedLayerIndex == null) {
            _uiState.update { it.copy(statusMessage = "اختر طبقة أولاً لتفعيل مؤثرات الطبقات") }
            return
        }
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

    fun setLayerTint(color: Color) {
        updateSelectedOrFirstLayer { it.copy(tintColor = color) }
    }

    fun setLayerOpacity(opacity: Float) {
        updateSelectedOrFirstLayer { it.copy(opacity = opacity.coerceIn(0.2f, 1f)) }
    }

    fun setLayerTextOverlay(text: String) {
        updateSelectedOrFirstLayer { layer ->
            layer.copy(
                textOverlay = text,
                textSpec = layer.textSpec.copy(text = text)
            )
        }
    }

    fun setLayerTextSpec(spec: TextLayerSpec) {
        updateSelectedOrFirstLayer {
            it.copy(
                textOverlay = spec.text,
                textSpec = spec
            )
        }
    }

    fun setLayerMask(
        mode: MaskMode,
        shape: MaskShape,
        feather: Float,
        invert: Boolean,
        linearAngle: Float
    ) {
        updateSelectedOrFirstLayer {
            it.copy(
                maskMode = mode,
                maskShape = shape,
                maskFeather = feather.coerceIn(0f, 1f),
                maskInvert = invert,
                maskLinearAngle = linearAngle.coerceIn(0f, 360f)
            )
        }
    }

    fun toggleHorizontalMirror() {
        updateSelectedOrFirstLayer { it.copy(isMirrored = !it.isMirrored) }
    }

    fun toggleVerticalFlip() {
        updateSelectedOrFirstLayer { it.copy(isFlippedVertical = !it.isFlippedVertical) }
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
                statusMessage = "تمت إضافة الطبقة $nextNumber — يمكنك استيراد فيديو أو صورة لها"
            )
        }
    }

    fun moveLayerUp(index: Int) {
        _uiState.update { state ->
            if (index <= 0 || index !in state.layers.indices) return@update state
            val mutable = state.layers.toMutableList()
            val item = mutable.removeAt(index)
            mutable.add(index - 1, item)
            state.copy(layers = mutable, selectedLayerIndex = index - 1)
        }
    }

    fun moveLayerDown(index: Int) {
        _uiState.update { state ->
            if (index < 0 || index >= state.layers.lastIndex) return@update state
            val mutable = state.layers.toMutableList()
            val item = mutable.removeAt(index)
            mutable.add(index + 1, item)
            state.copy(layers = mutable, selectedLayerIndex = index + 1)
        }
    }

    fun applyQrPreset(name: String) {
        updateSelectedOrFirstLayer { layer ->
            layer.copy(
                is3DEnabled = true,
                effect = LayerEffectType.NEON_GLOW,
                keyframes = listOf(
                    LayerKeyframe("qr_0", 0.0f, offsetX = -30f, scale = 0.85f, rotation = -10f),
                    LayerKeyframe("qr_1", 0.6f, offsetX = 0f, scale = 1.15f, rotation = 6f),
                    LayerKeyframe("qr_2", 1.0f, offsetX = 12f, scale = 1.0f, rotation = 0f)
                )
            )
        }
        _uiState.update {
            it.copy(
                activeDialog = null,
                statusMessage = "تم تطبيق كود QR: $name"
            )
        }
    }

    fun captureSnapshot() {
        _uiState.update { it.copy(statusMessage = "تم التقاط لقطة معاينة من الطبقات") }
    }

    fun syncToRepository() {
        VideoStarRepository.syncProjectLayers(projectId, _uiState.value.layers)
    }

    fun commitAndCreate(onDone: () -> Unit) {
        VideoStarRepository.saveProjectLayers(projectId, _uiState.value.layers)
        onDone()
    }

    fun openDialog(dialog: LayersDialogState) {
        _uiState.update { it.copy(activeDialog = dialog) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(activeDialog = null) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun interpolatedLayerAt(layer: EditorLayer, progress: Float): EditorLayer {
        val kfs = layer.keyframes.sortedBy { it.timeProgress }
        if (kfs.isEmpty()) return layer
        if (kfs.size == 1 || progress <= kfs.first().timeProgress) {
            val k = kfs.first()
            return layer.copy(
                offsetX = k.offsetX,
                offsetY = k.offsetY,
                scale = k.scale,
                rotation = k.rotation,
                rotationX = k.rotationX,
                rotationY = k.rotationY,
                perspectiveFov = k.perspectiveFov,
                anchorX = k.anchorX,
                anchorY = k.anchorY
            )
        }
        if (progress >= kfs.last().timeProgress) {
            val k = kfs.last()
            return layer.copy(
                offsetX = k.offsetX,
                offsetY = k.offsetY,
                scale = k.scale,
                rotation = k.rotation,
                rotationX = k.rotationX,
                rotationY = k.rotationY,
                perspectiveFov = k.perspectiveFov,
                anchorX = k.anchorX,
                anchorY = k.anchorY
            )
        }
        val nextIdx = kfs.indexOfFirst { it.timeProgress >= progress }.coerceAtLeast(1)
        val prev = kfs[nextIdx - 1]
        val next = kfs[nextIdx]
        val span = (next.timeProgress - prev.timeProgress).takeIf { abs(it) > 0.0001f } ?: 1f
        val rawT = ((progress - prev.timeProgress) / span).coerceIn(0f, 1f)
        val t = prev.curve.evaluate(rawT)
        return layer.copy(
            offsetX = prev.offsetX + (next.offsetX - prev.offsetX) * t,
            offsetY = prev.offsetY + (next.offsetY - prev.offsetY) * t,
            scale = prev.scale + (next.scale - prev.scale) * t,
            rotation = prev.rotation + (next.rotation - prev.rotation) * t,
            rotationX = prev.rotationX + (next.rotationX - prev.rotationX) * t,
            rotationY = prev.rotationY + (next.rotationY - prev.rotationY) * t,
            perspectiveFov = prev.perspectiveFov + (next.perspectiveFov - prev.perspectiveFov) * t,
            anchorX = prev.anchorX + (next.anchorX - prev.anchorX) * t,
            anchorY = prev.anchorY + (next.anchorY - prev.anchorY) * t
        )
    }

    private fun updateActiveLayer(transform: (EditorLayer) -> EditorLayer) {
        _uiState.update { state ->
            val idx = state.selectedLayerIndex ?: return@update state
            state.copy(
                layers = state.layers.mapIndexed { i, layer ->
                    if (i == idx) transform(layer) else layer
                }
            )
        }
    }

    private fun updateSelectedOrFirstLayer(transform: (EditorLayer) -> EditorLayer) {
        _uiState.update { state ->
            val idx = state.selectedLayerIndex ?: 0
            if (idx !in state.layers.indices) return@update state
            state.copy(
                selectedLayerIndex = idx,
                layers = state.layers.mapIndexed { i, layer ->
                    if (i == idx) transform(layer) else layer
                }
            )
        }
    }

    override fun onCleared() {
        playbackJob?.cancel()
        super.onCleared()
    }
}
