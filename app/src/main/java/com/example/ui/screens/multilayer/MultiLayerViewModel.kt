package com.example.ui.screens.multilayer

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.VideoStarRepository
import com.example.engine.sam.SamCutoutSegmenter
import com.example.engine.sam.SamLayerBitmapResolver
import com.example.model.EditorLayer
import com.example.model.LayerEffectType
import com.example.model.LayerMediaType
import com.example.model.LayerSlot
import com.example.model.MaskMode
import com.example.model.MaskShape
import com.example.model.MultiLayerTab
import com.example.model.SamCutoutSpec
import com.example.model.SamTapPoint
import com.example.model.TextLayerSpec
import com.example.model.defaultLayers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

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
    val statusMessage: String? = null,
    val isSamProcessing: Boolean = false,
    val isSamTapMode: Boolean = false,
    val samTapInclude: Boolean = true
) {
    val activeLayer: EditorLayer?
        get() = layers.getOrNull(selectedLayerIndex.coerceIn(0, (layers.size - 1).coerceAtLeast(0)))

    val layerSlots: List<LayerSlot>
        get() = layers.map { it.toLayerSlot() }
}

class MultiLayerViewModel(
    private val projectId: String,
    private val targetSceneIndex: Int? = null
) : ViewModel() {
    private val initialProject = VideoStarRepository.getProjectById(projectId)
    private val resolvedSceneIndex: Int? = initialProject?.scenes?.let { scenes ->
        when {
            targetSceneIndex != null && targetSceneIndex in scenes.indices -> targetSceneIndex
            scenes.isNotEmpty() -> 0
            else -> null
        }
    }

    private val initialLayers: List<EditorLayer> = run {
        val scenes = initialProject?.scenes.orEmpty()
        val scene = resolvedSceneIndex?.let { scenes.getOrNull(it) }
        when {
            scene != null && scene.isMultiLayerComposite && scene.layers.isNotEmpty() -> scene.layers
            scene != null -> {
                val base = defaultLayers()
                val first = base.first()
                val seededFirst = first.copy(
                    mediaClip = scene.clip,
                    backgroundColorArgb = if (scene.clip == null) scene.transform.bgColorArgb else null,
                    offsetX = scene.transform.offsetX,
                    offsetY = scene.transform.offsetY,
                    scale = scene.transform.scale,
                    rotation = scene.transform.rotation,
                    isMirrored = scene.transform.flipH,
                    isFlippedVertical = scene.transform.flipV,
                    effectPasses = scene.resolvedEffectPasses(),
                    sourceSceneId = scene.id
                )
                listOf(seededFirst) + base.drop(1)
            }
            else -> initialProject?.layers ?: defaultLayers()
        }
    }

    init {
        if (resolvedSceneIndex != null) {
            VideoStarRepository.syncProjectLayers(projectId, initialLayers)
        }
    }

    private val _uiState = MutableStateFlow(
        MultiLayerUiState(
            projectId = projectId,
            projectAspectRatio = initialProject?.aspectRatio?.ratio ?: (9f / 16f),
            layers = initialLayers
        )
    )
    val uiState: StateFlow<MultiLayerUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null
    private var samJob: Job? = null

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

    fun toggleSamTapMode() {
        _uiState.update { it.copy(isSamTapMode = !it.isSamTapMode) }
    }

    fun setSamTapInclude(include: Boolean) {
        _uiState.update { it.copy(samTapInclude = include, isSamTapMode = true) }
    }

    fun runSamAutoCutout(context: Context) {
        val targetLayer = _uiState.value.activeLayer ?: return
        val nextSpec = targetLayer.samCutout.copy(
            enabled = true,
            isolateAllSubjects = true,
            tapPoints = emptyList()
        )
        executeSamSegmentation(context, targetLayer, nextSpec)
    }

    fun addSamTapPoint(context: Context, normX: Float, normY: Float) {
        val state = _uiState.value
        val targetLayer = state.activeLayer ?: return
        val pt = SamTapPoint(
            id = "sam_${UUID.randomUUID().toString().take(6)}",
            x = normX.coerceIn(0f, 1f),
            y = normY.coerceIn(0f, 1f),
            include = state.samTapInclude
        )
        val updatedPoints = targetLayer.samCutout.tapPoints + pt
        val nextSpec = targetLayer.samCutout.copy(
            enabled = true,
            isolateAllSubjects = updatedPoints.none { it.include },
            tapPoints = updatedPoints
        )
        executeSamSegmentation(context, targetLayer, nextSpec)
    }

    fun updateSamRefinement(context: Context, feather: Float, choke: Float, invert: Boolean) {
        val targetLayer = _uiState.value.activeLayer ?: return
        val nextSpec = targetLayer.samCutout.copy(
            feather = feather.coerceIn(0f, 1f),
            choke = choke.coerceIn(-1f, 1f),
            invert = invert
        )
        updateActiveLayer { it.copy(samCutout = nextSpec) }
        if (nextSpec.enabled) {
            executeSamSegmentation(context, targetLayer.copy(samCutout = nextSpec), nextSpec, silent = true)
        }
    }

    fun clearSamCutout() {
        samJob?.cancel()
        updateActiveLayer { it.copy(samCutout = SamCutoutSpec()) }
        _uiState.update {
            it.copy(
                isSamProcessing = false,
                isSamTapMode = false,
                statusMessage = "SAM cutout cleared"
            )
        }
        syncToRepository()
    }

    private fun executeSamSegmentation(
        context: Context,
        targetLayer: EditorLayer,
        spec: SamCutoutSpec,
        silent: Boolean = false
    ) {
        samJob?.cancel()
        samJob = viewModelScope.launch {
            _uiState.update { it.copy(isSamProcessing = true) }
            val progress = _uiState.value.stageProgress
            val srcBmp = SamLayerBitmapResolver.extractLayerSourceBitmap(context, targetLayer, progress)
            if (srcBmp == null) {
                _uiState.update {
                    it.copy(
                        isSamProcessing = false,
                        statusMessage = "Add a photo or video to this layer first"
                    )
                }
                return@launch
            }
            val outcome = runCatching {
                SamCutoutSegmenter.runCutout(context, targetLayer.id, srcBmp, spec)
            }.getOrNull()

            if (outcome != null) {
                _uiState.update { state ->
                    state.copy(
                        isSamProcessing = false,
                        layers = state.layers.map { l ->
                            if (l.id == targetLayer.id) l.copy(samCutout = outcome.updatedSpec) else l
                        },
                        statusMessage = if (silent) state.statusMessage else "SAM isolated ${outcome.subjectCount} subject(s)"
                    )
                }
                syncToRepository()
            } else {
                _uiState.update {
                    it.copy(isSamProcessing = false, statusMessage = "Subject cutout could not run on this frame")
                }
            }
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
        VideoStarRepository.saveProjectLayers(
            projectId = projectId,
            layers = _uiState.value.layers,
            targetSceneIndex = resolvedSceneIndex,
            createNewSceneIfEmpty = resolvedSceneIndex == null
        )
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
        samJob?.cancel()
        super.onCleared()
    }
}
