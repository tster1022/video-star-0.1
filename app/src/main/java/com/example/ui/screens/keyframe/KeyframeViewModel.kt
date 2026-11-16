package com.example.ui.screens.keyframe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.VideoStarRepository
import com.example.model.EditorLayer
import com.example.model.LayerKeyframe
import com.example.model.MediaClip
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs

sealed interface KeyframeDialogState {
    data object QrImport : KeyframeDialogState
    data object Info : KeyframeDialogState
    data object Options : KeyframeDialogState
    data object LayoutEffects : KeyframeDialogState
}

data class KeyframeUiState(
    val projectId: String = "",
    val layerId: String = "",
    val layerNumber: Int = 1,
    val mediaClip: MediaClip? = null,
    val currentProgress: Float = 0.25f,
    val keyframes: List<LayerKeyframe> = emptyList(),
    val displayOffsetX: Float = 0f,
    val displayOffsetY: Float = 0f,
    val displayScale: Float = 1f,
    val displayRotation: Float = 0f,
    val displayRotationX: Float = 0f,
    val displayRotationY: Float = 0f,
    val displayPerspectiveFov: Float = 45f,
    val is3DEnabled: Boolean = false,
    val displayAnchorX: Float = 0.5f,
    val displayAnchorY: Float = 0.5f,
    val displayEffectIntensity: Float = 0f,
    val layoutEffectName: String = "Layout Effect Not Selected",
    val isFlippedVertical: Boolean = false,
    val isPlaying: Boolean = false,
    val isMuted: Boolean = false,
    val isGridVisible: Boolean = true,
    val isTransparencyCheckerVisible: Boolean = false,
    val isPathVisible: Boolean = true,
    val isFlipMode: Boolean = false,
    val copiedKeyframe: LayerKeyframe? = null,
    val activeDialog: KeyframeDialogState? = null,
    val statusMessage: String? = null
)

class KeyframeViewModel(
    private val projectId: String,
    private val layerId: String
) : ViewModel() {

    private val project = VideoStarRepository.getProjectById(projectId)
    private val initialLayer: EditorLayer = project
        ?.layers
        ?.firstOrNull { it.id == layerId }
        ?: project?.layers?.firstOrNull()
        ?: EditorLayer(id = layerId, number = 1, mediaClip = project?.primaryClip)

    private val initialKeyframes: List<LayerKeyframe> = initialLayer.keyframes.ifEmpty {
        listOf(
            LayerKeyframe(
                id = "kf_start",
                timeProgress = 0.1f,
                offsetX = initialLayer.offsetX,
                offsetY = initialLayer.offsetY,
                scale = initialLayer.scale,
                rotation = initialLayer.rotation,
                rotationX = initialLayer.rotationX,
                rotationY = initialLayer.rotationY,
                perspectiveFov = initialLayer.perspectiveFov,
                anchorX = initialLayer.anchorX,
                anchorY = initialLayer.anchorY,
                effectIntensity = initialLayer.effectIntensity
            ),
            LayerKeyframe(
                id = "kf_end",
                timeProgress = 0.8f,
                offsetX = initialLayer.offsetX + 20f,
                offsetY = initialLayer.offsetY - 12f,
                scale = (initialLayer.scale * 1.12f).coerceIn(0.25f, 2.5f),
                rotation = initialLayer.rotation + 12f,
                rotationX = initialLayer.rotationX,
                rotationY = initialLayer.rotationY,
                perspectiveFov = initialLayer.perspectiveFov,
                anchorX = initialLayer.anchorX,
                anchorY = initialLayer.anchorY,
                effectIntensity = initialLayer.effectIntensity
            )
        )
    }.sortedBy { it.timeProgress }

    private val _uiState = MutableStateFlow(
        applyInterpolation(
            KeyframeUiState(
                projectId = projectId,
                layerId = initialLayer.id,
                layerNumber = initialLayer.number,
                mediaClip = initialLayer.mediaClip ?: project?.primaryClip,
                currentProgress = initialKeyframes.firstOrNull()?.timeProgress ?: 0.2f,
                keyframes = initialKeyframes,
                is3DEnabled = initialLayer.is3DEnabled ||
                    abs(initialLayer.rotationX) > 0.1f ||
                    abs(initialLayer.rotationY) > 0.1f,
                layoutEffectName = initialLayer.layoutEffectName,
                isFlippedVertical = initialLayer.isFlippedVertical
            )
        )
    )
    val uiState: StateFlow<KeyframeUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null

    fun scrubTo(progress: Float) {
        val clamped = progress.coerceIn(0f, 1f)
        _uiState.update { state ->
            applyInterpolation(state.copy(currentProgress = clamped))
        }
    }

    fun stepBackward() {
        scrubTo(_uiState.value.currentProgress - 0.04f)
    }

    fun stepForward() {
        scrubTo(_uiState.value.currentProgress + 0.04f)
    }

    fun togglePlayback() {
        val next = !_uiState.value.isPlaying
        _uiState.update { it.copy(isPlaying = next) }
        playbackJob?.cancel()
        if (next) {
            playbackJob = viewModelScope.launch {
                while (_uiState.value.isPlaying) {
                    delay(35L)
                    _uiState.update { state ->
                        val nextProgress = state.currentProgress + 0.014f
                        if (nextProgress >= 1f) {
                            applyInterpolation(state.copy(currentProgress = 0f, isPlaying = false))
                        } else {
                            applyInterpolation(state.copy(currentProgress = nextProgress))
                        }
                    }
                }
            }
        }
    }

    fun toggleMute() {
        _uiState.update { it.copy(isMuted = !it.isMuted) }
    }

    fun toggleGrid() {
        _uiState.update { it.copy(isGridVisible = !it.isGridVisible) }
    }

    fun toggleTransparencyChecker() {
        _uiState.update { it.copy(isTransparencyCheckerVisible = !it.isTransparencyCheckerVisible) }
    }

    fun togglePathVisible() {
        _uiState.update { it.copy(isPathVisible = !it.isPathVisible) }
    }

    fun toggleRotateFlipMode() {
        _uiState.update { state ->
            val nextFlipMode = !state.isFlipMode
            state.copy(
                isFlipMode = nextFlipMode,
                isFlippedVertical = if (nextFlipMode) !state.isFlippedVertical else state.isFlippedVertical
            )
        }
    }

    fun nudgePosition(dx: Float, dy: Float) {
        mutateCurrentKeyframe { kf ->
            kf.copy(
                offsetX = (kf.offsetX + dx).coerceIn(-300f, 300f),
                offsetY = (kf.offsetY + dy).coerceIn(-300f, 300f)
            )
        }
    }

    fun onStageDrag(dx: Float, dy: Float) {
        mutateCurrentKeyframe { kf ->
            kf.copy(
                offsetX = (kf.offsetX + dx).coerceIn(-350f, 350f),
                offsetY = (kf.offsetY + dy).coerceIn(-350f, 350f)
            )
        }
    }

    fun updateAnchor(anchorX: Float, anchorY: Float) {
        mutateCurrentKeyframe { kf ->
            kf.copy(
                anchorX = anchorX.coerceIn(0.08f, 0.92f),
                anchorY = anchorY.coerceIn(0.08f, 0.92f)
            )
        }
    }

    fun resetAnchorToCenter() {
        updateAnchor(0.5f, 0.5f)
        _uiState.update { it.copy(statusMessage = "تمت إعادة نقطة الارتكاز إلى المنتصف") }
    }

    fun updateScale(newScale: Float) {
        val clamped = newScale.coerceIn(0.25f, 2.5f)
        mutateCurrentKeyframe { kf -> kf.copy(scale = clamped) }
    }

    fun resetScaleTo100() {
        updateScale(1f)
    }

    fun jogRotate(deltaDegrees: Float) {
        mutateCurrentKeyframe { kf ->
            val next = (kf.rotation + deltaDegrees).coerceIn(-360f, 360f)
            kf.copy(rotation = next)
        }
    }

    fun resetRotationToZero() {
        mutateCurrentKeyframe { kf -> kf.copy(rotation = 0f) }
    }

    fun toggle3DEnabled() {
        _uiState.update { state ->
            val next = !state.is3DEnabled
            state.copy(is3DEnabled = next)
        }
    }

    fun updateRotationX(deg: Float) {
        val clamped = deg.coerceIn(-75f, 75f)
        mutateCurrentKeyframe { kf -> kf.copy(rotationX = clamped) }
    }

    fun updateRotationY(deg: Float) {
        val clamped = deg.coerceIn(-75f, 75f)
        mutateCurrentKeyframe { kf -> kf.copy(rotationY = clamped) }
    }

    fun updatePerspectiveFov(fov: Float) {
        val clamped = fov.coerceIn(25f, 90f)
        mutateCurrentKeyframe { kf -> kf.copy(perspectiveFov = clamped) }
    }

    fun reset3DTransform() {
        mutateCurrentKeyframe { kf -> kf.copy(rotationX = 0f, rotationY = 0f, perspectiveFov = 45f) }
    }

    fun setEffectIntensity(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        mutateCurrentKeyframe { kf -> kf.copy(effectIntensity = clamped) }
    }

    fun selectLayoutEffect(name: String) {
        _uiState.update {
            it.copy(
                layoutEffectName = name,
                activeDialog = null,
                statusMessage = "تم تحديد تأثير التخطيط: $name"
            )
        }
    }

    fun addOrUpdateKeyframeAtCurrentTime() {
        _uiState.update { state ->
            val p = state.currentProgress
            val existingIdx = state.keyframes.indexOfFirst { abs(it.timeProgress - p) < 0.03f }
            val newKf = LayerKeyframe(
                id = if (existingIdx >= 0) state.keyframes[existingIdx].id else "kf_${UUID.randomUUID().toString().take(6)}",
                timeProgress = p,
                offsetX = state.displayOffsetX,
                offsetY = state.displayOffsetY,
                scale = state.displayScale,
                rotation = state.displayRotation,
                rotationX = state.displayRotationX,
                rotationY = state.displayRotationY,
                perspectiveFov = state.displayPerspectiveFov,
                anchorX = state.displayAnchorX,
                anchorY = state.displayAnchorY,
                effectIntensity = state.displayEffectIntensity
            )
            val updated = if (existingIdx >= 0) {
                state.keyframes.mapIndexed { idx, kf -> if (idx == existingIdx) newKf else kf }
            } else {
                (state.keyframes + newKf).sortedBy { it.timeProgress }
            }
            applyInterpolation(
                state.copy(
                    keyframes = updated,
                    statusMessage = "تمت إضافة Keyframe عند ${(p * 100).toInt()}%"
                )
            )
        }
    }

    fun copyCurrentValuesAsKeyframe() {
        val state = _uiState.value
        val snapshot = LayerKeyframe(
            id = "copied",
            timeProgress = state.currentProgress,
            offsetX = state.displayOffsetX,
            offsetY = state.displayOffsetY,
            scale = state.displayScale,
            rotation = state.displayRotation,
            rotationX = state.displayRotationX,
            rotationY = state.displayRotationY,
            perspectiveFov = state.displayPerspectiveFov,
            anchorX = state.displayAnchorX,
            anchorY = state.displayAnchorY,
            effectIntensity = state.displayEffectIntensity
        )
        _uiState.update {
            it.copy(
                copiedKeyframe = snapshot,
                statusMessage = "تم نسخ قيم الـ Keyframe الحالي"
            )
        }
    }

    fun pasteCopiedKeyframeAtCurrentTime() {
        val copied = _uiState.value.copiedKeyframe ?: return
        mutateCurrentKeyframe { kf ->
            kf.copy(
                offsetX = copied.offsetX,
                offsetY = copied.offsetY,
                scale = copied.scale,
                rotation = copied.rotation,
                rotationX = copied.rotationX,
                rotationY = copied.rotationY,
                perspectiveFov = copied.perspectiveFov,
                anchorX = copied.anchorX,
                anchorY = copied.anchorY,
                effectIntensity = copied.effectIntensity
            )
        }
        _uiState.update { it.copy(statusMessage = "تم لصق قيم الـ Keyframe") }
    }

    fun deleteNearestKeyframe() {
        _uiState.update { state ->
            if (state.keyframes.size <= 1) {
                return@update state.copy(statusMessage = "يجب الاحتفاظ بنقطة Keyframe واحدة على الأقل")
            }
            val nearest = state.keyframes.minByOrNull { abs(it.timeProgress - state.currentProgress) }
                ?: return@update state
            val updated = state.keyframes.filterNot { it.id == nearest.id }
            applyInterpolation(
                state.copy(
                    keyframes = updated,
                    statusMessage = "تم حذف نقطة Keyframe"
                )
            )
        }
    }

    fun clearAllKeyframesExceptCurrent() {
        _uiState.update { state ->
            val single = LayerKeyframe(
                id = "kf_reset",
                timeProgress = state.currentProgress,
                offsetX = 0f,
                offsetY = 0f,
                scale = 1f,
                rotation = 0f,
                anchorX = 0.5f,
                anchorY = 0.5f,
                effectIntensity = 0f
            )
            applyInterpolation(
                state.copy(
                    keyframes = listOf(single),
                    activeDialog = null,
                    statusMessage = "تم تصفير كافة نقاط الـ Keyframes"
                )
            )
        }
    }

    fun applyQrKeyframePreset(name: String) {
        val presetKfs = listOf(
            LayerKeyframe("qr_k0", 0.05f, offsetX = -40f, offsetY = 10f, scale = 0.8f, rotation = -18f),
            LayerKeyframe("qr_k1", 0.50f, offsetX = 0f, offsetY = -14f, scale = 1.28f, rotation = 6f),
            LayerKeyframe("qr_k2", 0.92f, offsetX = 18f, offsetY = 0f, scale = 1.0f, rotation = 0f)
        )
        _uiState.update { state ->
            applyInterpolation(
                state.copy(
                    keyframes = presetKfs,
                    currentProgress = 0.50f,
                    activeDialog = null,
                    statusMessage = "تم استيراد حركة QR: $name"
                )
            )
        }
    }

    fun openDialog(dialog: KeyframeDialogState) {
        _uiState.update { it.copy(activeDialog = dialog) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(activeDialog = null) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun activeKeyframeIndex(): Int {
        val state = _uiState.value
        val sorted = state.keyframes.sortedBy { it.timeProgress }
        if (sorted.isEmpty()) return 0
        val idx = sorted.indexOfLast { it.timeProgress <= state.currentProgress + 0.02f }
        return idx.coerceIn(0, sorted.lastIndex)
    }

    fun refreshFromRepository() {
        val latest = VideoStarRepository.getProjectById(projectId)
            ?.layers
            ?.firstOrNull { it.id == layerId }
            ?: return
        val kfs = latest.keyframes.ifEmpty { _uiState.value.keyframes }.sortedBy { it.timeProgress }
        _uiState.update { state ->
            applyInterpolation(state.copy(keyframes = kfs))
        }
    }

    fun saveAndReturn(onDone: () -> Unit) {
        val state = _uiState.value
        VideoStarRepository.updateLayerFromKeyframes(
            projectId = projectId,
            layerId = state.layerId,
            keyframes = state.keyframes,
            offsetX = state.displayOffsetX,
            offsetY = state.displayOffsetY,
            scale = state.displayScale,
            rotation = state.displayRotation,
            rotationX = state.displayRotationX,
            rotationY = state.displayRotationY,
            perspectiveFov = state.displayPerspectiveFov,
            is3DEnabled = state.is3DEnabled,
            anchorX = state.displayAnchorX,
            anchorY = state.displayAnchorY,
            isFlippedVertical = state.isFlippedVertical,
            layoutEffectName = state.layoutEffectName,
            effectIntensity = state.displayEffectIntensity
        )
        onDone()
    }

    private fun mutateCurrentKeyframe(transform: (LayerKeyframe) -> LayerKeyframe) {
        _uiState.update { state ->
            val p = state.currentProgress
            val existingIdx = state.keyframes.indexOfFirst { abs(it.timeProgress - p) < 0.03f }
            val updatedList = if (existingIdx >= 0) {
                state.keyframes.mapIndexed { idx, kf -> if (idx == existingIdx) transform(kf) else kf }
            } else {
                val base = LayerKeyframe(
                    id = "kf_${UUID.randomUUID().toString().take(6)}",
                    timeProgress = p,
                    offsetX = state.displayOffsetX,
                    offsetY = state.displayOffsetY,
                    scale = state.displayScale,
                    rotation = state.displayRotation,
                    rotationX = state.displayRotationX,
                    rotationY = state.displayRotationY,
                    perspectiveFov = state.displayPerspectiveFov,
                    anchorX = state.displayAnchorX,
                    anchorY = state.displayAnchorY,
                    effectIntensity = state.displayEffectIntensity
                )
                (state.keyframes + transform(base)).sortedBy { it.timeProgress }
            }
            applyInterpolation(state.copy(keyframes = updatedList))
        }
    }

    private fun applyInterpolation(state: KeyframeUiState): KeyframeUiState {
        val sorted = state.keyframes.sortedBy { it.timeProgress }
        if (sorted.isEmpty()) return state
        val p = state.currentProgress
        if (sorted.size == 1 || p <= sorted.first().timeProgress) {
            val k = sorted.first()
            return state.copy(
                keyframes = sorted,
                displayOffsetX = k.offsetX,
                displayOffsetY = k.offsetY,
                displayScale = k.scale,
                displayRotation = k.rotation,
                displayRotationX = k.rotationX,
                displayRotationY = k.rotationY,
                displayPerspectiveFov = k.perspectiveFov,
                displayAnchorX = k.anchorX,
                displayAnchorY = k.anchorY,
                displayEffectIntensity = k.effectIntensity
            )
        }
        if (p >= sorted.last().timeProgress) {
            val k = sorted.last()
            return state.copy(
                keyframes = sorted,
                displayOffsetX = k.offsetX,
                displayOffsetY = k.offsetY,
                displayScale = k.scale,
                displayRotation = k.rotation,
                displayRotationX = k.rotationX,
                displayRotationY = k.rotationY,
                displayPerspectiveFov = k.perspectiveFov,
                displayAnchorX = k.anchorX,
                displayAnchorY = k.anchorY,
                displayEffectIntensity = k.effectIntensity
            )
        }
        val nextIdx = sorted.indexOfFirst { it.timeProgress >= p }.coerceAtLeast(1)
        val prev = sorted[nextIdx - 1]
        val next = sorted[nextIdx]
        val span = (next.timeProgress - prev.timeProgress).takeIf { abs(it) > 0.0001f } ?: 1f
        val rawT = ((p - prev.timeProgress) / span).coerceIn(0f, 1f)
        val t = prev.curve.evaluate(rawT)
        return state.copy(
            keyframes = sorted,
            displayOffsetX = lerp(prev.offsetX, next.offsetX, t),
            displayOffsetY = lerp(prev.offsetY, next.offsetY, t),
            displayScale = lerp(prev.scale, next.scale, t),
            displayRotation = lerp(prev.rotation, next.rotation, t),
            displayRotationX = lerp(prev.rotationX, next.rotationX, t),
            displayRotationY = lerp(prev.rotationY, next.rotationY, t),
            displayPerspectiveFov = lerp(prev.perspectiveFov, next.perspectiveFov, t),
            displayAnchorX = lerp(prev.anchorX, next.anchorX, t),
            displayAnchorY = lerp(prev.anchorY, next.anchorY, t),
            displayEffectIntensity = lerp(prev.effectIntensity, next.effectIntensity, t)
        )
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    override fun onCleared() {
        playbackJob?.cancel()
        super.onCleared()
    }
}
