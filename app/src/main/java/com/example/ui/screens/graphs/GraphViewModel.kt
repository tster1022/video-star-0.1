package com.example.ui.screens.graphs

import androidx.lifecycle.ViewModel
import com.example.data.VideoStarRepository
import com.example.model.BezierCurveSpec
import com.example.model.GraphPreset
import com.example.model.defaultGraphPresets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs

data class GraphUiState(
    val projectId: String = "",
    val layerId: String = "",
    val keyframeIndex: Int = 0,
    val curve: BezierCurveSpec = BezierCurveSpec(0.25f, 0.1f, 0.25f, 1f),
    val selectedPresetName: String? = "Smooth",
    val presets: List<GraphPreset> = defaultGraphPresets,
    val hasCopiedCurve: Boolean = false,
    val statusMessage: String? = null
)

class GraphViewModel(
    private val projectId: String,
    private val layerId: String,
    private val keyframeIndex: Int
) : ViewModel() {

    private val initialCurve: BezierCurveSpec = VideoStarRepository.getProjectById(projectId)
        ?.layers
        ?.firstOrNull { it.id == layerId }
        ?.keyframes
        ?.sortedBy { it.timeProgress }
        ?.getOrNull(keyframeIndex)
        ?.curve
        ?: BezierCurveSpec(0.25f, 0.1f, 0.25f, 1f)

    private val _uiState = MutableStateFlow(
        GraphUiState(
            projectId = projectId,
            layerId = layerId,
            keyframeIndex = keyframeIndex,
            curve = initialCurve,
            selectedPresetName = matchingPresetName(initialCurve),
            hasCopiedCurve = VideoStarRepository.copiedGraphCurve != null
        )
    )
    val uiState: StateFlow<GraphUiState> = _uiState.asStateFlow()

    fun updateP1(x: Float, y: Float) {
        _uiState.update { state ->
            val next = state.curve.copy(
                p1x = x.coerceIn(0f, 1f),
                p1y = y.coerceIn(-0.45f, 1.45f)
            )
            state.copy(curve = next, selectedPresetName = matchingPresetName(next))
        }
    }

    fun updateP2(x: Float, y: Float) {
        _uiState.update { state ->
            val next = state.curve.copy(
                p2x = x.coerceIn(0f, 1f),
                p2y = y.coerceIn(-0.45f, 1.45f)
            )
            state.copy(curve = next, selectedPresetName = matchingPresetName(next))
        }
    }

    fun selectPreset(preset: GraphPreset) {
        _uiState.update {
            it.copy(
                curve = preset.spec,
                selectedPresetName = preset.name
            )
        }
    }

    fun copyCurve() {
        val current = _uiState.value.curve
        VideoStarRepository.saveCopiedGraphCurve(current)
        _uiState.update {
            it.copy(
                hasCopiedCurve = true,
                statusMessage = "تم نسخ منحنى الحركة"
            )
        }
    }

    fun pasteCurve() {
        val copied = VideoStarRepository.copiedGraphCurve ?: return
        _uiState.update {
            it.copy(
                curve = copied,
                selectedPresetName = matchingPresetName(copied),
                statusMessage = "تم لصق منحنى الحركة"
            )
        }
    }

    fun invertCurve() {
        _uiState.update { state ->
            val inv = state.curve.inverted()
            state.copy(
                curve = inv,
                selectedPresetName = matchingPresetName(inv),
                statusMessage = "تم عكس اتجاه المنحنى"
            )
        }
    }

    fun applyToAllKeyframes() {
        val current = _uiState.value.curve
        VideoStarRepository.applyKeyframeGraph(
            projectId = projectId,
            layerId = layerId,
            keyframeIndex = keyframeIndex,
            curve = current,
            applyToAll = true
        )
        _uiState.update {
            it.copy(statusMessage = "تم تطبيق المنحنى على جميع نقاط الـ Keyframes")
        }
    }

    fun applyAndReturn(onDone: () -> Unit) {
        VideoStarRepository.applyKeyframeGraph(
            projectId = projectId,
            layerId = layerId,
            keyframeIndex = keyframeIndex,
            curve = _uiState.value.curve,
            applyToAll = false
        )
        onDone()
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun matchingPresetName(spec: BezierCurveSpec): String? =
        defaultGraphPresets.firstOrNull { preset ->
            abs(preset.spec.p1x - spec.p1x) < 0.02f &&
                abs(preset.spec.p1y - spec.p1y) < 0.02f &&
                abs(preset.spec.p2x - spec.p2x) < 0.02f &&
                abs(preset.spec.p2y - spec.p2y) < 0.02f
        }?.name
}
