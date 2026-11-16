package com.example.ui.screens.effects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.VideoStarRepository
import com.example.model.AspectRatioMode
import com.example.model.EditorLayer
import com.example.model.EffectCatalog
import com.example.model.EffectCatalogItem
import com.example.model.EffectCategory
import com.example.model.EffectKeyframe
import com.example.model.EffectStudioTab
import com.example.model.MediaClip
import com.example.model.SceneEffect
import com.example.model.ShaderEffectPass
import com.example.model.TimelineScene
import com.example.model.toStudioTab
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs

data class EffectsStudioUiState(
    val projectId: String = "",
    val targetSceneIndices: List<Int> = listOf(0),
    val targetLayerId: String? = null,
    val aspectRatio: AspectRatioMode = AspectRatioMode.SQUARE,
    val baseScene: TimelineScene? = null,
    val targetLayer: EditorLayer? = null,
    val passes: List<ShaderEffectPass> = emptyList(),
    val selectedPassIndex: Int = 0,
    val selectedTab: EffectStudioTab = EffectStudioTab.ALL,
    val selectedCategory: EffectCategory = EffectCategory.SHAKE_MOTION,
    val searchQuery: String = "",
    val isSearchVisible: Boolean = false,
    val isPlaying: Boolean = true,
    val previewLocalMs: Long = 0L
) {
    val activePass: ShaderEffectPass?
        get() = passes.getOrNull(selectedPassIndex)

    val activeCatalogItem: EffectCatalogItem
        get() = activePass?.let { EffectCatalog.findById(it.effectId) } ?: EffectCatalog.noneItem

    val previewClip: MediaClip?
        get() = baseScene?.clip ?: targetLayer?.mediaClip

    val totalDurationMs: Long
        get() = (baseScene?.effectiveDurationMs
            ?: targetLayer?.mediaClip?.effectiveDurationMs
            ?: 3_000L).coerceAtLeast(300L)

    val previewProgress: Float
        get() = (previewLocalMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)

    val hasKeyframeNearCurrentTime: Boolean
        get() = activePass?.keyframes?.any { abs(it.timeProgress - previewProgress) <= 0.03f } == true

    val activeEvaluatedIntensity: Float
        get() = activePass?.evaluateIntensityAt(previewProgress) ?: 0f

    val displayedEffects: List<EffectCatalogItem>
        get() {
            val q = searchQuery.trim()
            return if (q.isNotEmpty()) {
                EffectCatalog.shaderEffects.filter {
                    it.name.contains(q, ignoreCase = true) ||
                        it.category.label.contains(q, ignoreCase = true) ||
                        it.toStudioTab().label.contains(q, ignoreCase = true)
                }
            } else if (selectedTab == EffectStudioTab.ALL) {
                EffectCatalog.shaderEffects
            } else {
                EffectCatalog.shaderEffects.filter { it.toStudioTab() == selectedTab }
            }
        }

    val previewScene: TimelineScene?
        get() {
            val base = baseScene ?: targetLayer?.let { layer ->
                val clip = layer.mediaClip
                TimelineScene(
                    id = "layer_preview_${layer.id}",
                    title = "Layer ${layer.number}",
                    durationMs = clip?.effectiveDurationMs ?: 3_000L,
                    clipId = clip?.id,
                    mediaUriString = clip?.uriString,
                    isVideo = clip is MediaClip.Video,
                    clip = clip
                )
            } ?: return null

            val firstPass = passes.firstOrNull { it.effectId != "none" }
            val primaryFx = if (firstPass != null) {
                SceneEffect(
                    category = firstPass.category,
                    effectId = firstPass.effectId,
                    effectName = firstPass.effectName,
                    intensity = firstPass.evaluateIntensityAt(previewProgress)
                )
            } else SceneEffect()

            return base.copy(
                effect = primaryFx,
                effectPasses = passes
            )
        }
}

class EffectsStudioViewModel(
    private val projectId: String,
    sceneIndices: List<Int> = listOf(0),
    private val targetLayerId: String? = null
) : ViewModel() {

    private val safeIndices = sceneIndices.ifEmpty { listOf(0) }
    private val project = VideoStarRepository.getProjectById(projectId)
    private val firstScene = project?.scenes?.getOrNull(safeIndices.first())
    private val layer = targetLayerId?.let { id -> project?.layers?.firstOrNull { it.id == id } }

    private val initialPasses: List<ShaderEffectPass> = when {
        layer != null -> layer.effectPasses
        firstScene != null -> firstScene.resolvedEffectPasses()
        else -> emptyList()
    }

    private val initialCategory = initialPasses.firstOrNull()?.category ?: EffectCategory.SHAKE_MOTION

    private val _uiState = MutableStateFlow(
        EffectsStudioUiState(
            projectId = projectId,
            targetSceneIndices = safeIndices,
            targetLayerId = targetLayerId,
            aspectRatio = project?.aspectRatio ?: AspectRatioMode.SQUARE,
            baseScene = firstScene,
            targetLayer = layer,
            passes = initialPasses,
            selectedPassIndex = 0,
            selectedTab = EffectStudioTab.ALL,
            selectedCategory = initialCategory
        )
    )
    val uiState: StateFlow<EffectsStudioUiState> = _uiState.asStateFlow()

    private var playJob: Job? = null

    init {
        startPreviewLoop()
    }

    private fun startPreviewLoop() {
        playJob?.cancel()
        playJob = viewModelScope.launch {
            while (true) {
                delay(33L)
                if (_uiState.value.isPlaying) {
                    _uiState.update { state ->
                        val dur = state.totalDurationMs
                        val next = (state.previewLocalMs + 33L) % dur
                        state.copy(previewLocalMs = next)
                    }
                }
            }
        }
    }

    fun selectTab(tab: EffectStudioTab) {
        val mappedCat = when (tab) {
            EffectStudioTab.ALL -> _uiState.value.selectedCategory
            EffectStudioTab.COLOR -> EffectCategory.COLOR_GLOW
            EffectStudioTab.LAYOUT -> EffectCategory.MIRROR_KALEIDOSCOPE
            EffectStudioTab.MOTION -> EffectCategory.SHAKE_MOTION
            EffectStudioTab.VHS_GLITCH -> EffectCategory.RETRO_VHS
        }
        _uiState.update { it.copy(selectedTab = tab, selectedCategory = mappedCat) }
    }

    fun selectCategory(category: EffectCategory) {
        val mappedTab = when (category) {
            EffectCategory.COLOR_GLOW -> EffectStudioTab.COLOR
            EffectCategory.MIRROR_KALEIDOSCOPE -> EffectStudioTab.LAYOUT
            EffectCategory.SHAKE_MOTION, EffectCategory.BLUR_FOCUS -> EffectStudioTab.MOTION
            EffectCategory.DISTORTION_GLITCH, EffectCategory.RETRO_VHS -> EffectStudioTab.VHS_GLITCH
        }
        _uiState.update { it.copy(selectedCategory = category, selectedTab = mappedTab) }
    }

    fun toggleSearch() {
        _uiState.update { state ->
            val next = !state.isSearchVisible
            state.copy(
                isSearchVisible = next,
                searchQuery = if (!next) "" else state.searchQuery
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectEffect(item: EffectCatalogItem) {
        if (item.id == "none") {
            resetEffects()
            return
        }
        _uiState.update { state ->
            val existing = state.passes
            val newPass = ShaderEffectPass(
                id = existing.getOrNull(state.selectedPassIndex)?.id ?: "pass_${UUID.randomUUID().toString().take(6)}",
                effectId = item.id,
                effectName = item.name,
                category = item.category,
                intensity = item.defaultIntensity,
                params = item.defaultParamMap(),
                keyframes = emptyList()
            )
            val updatedPasses = if (existing.isEmpty()) {
                listOf(newPass)
            } else {
                existing.mapIndexed { idx, p ->
                    if (idx == state.selectedPassIndex) newPass else p
                }
            }
            val safeIdx = if (existing.isEmpty()) 0 else state.selectedPassIndex.coerceIn(0, updatedPasses.lastIndex)
            state.copy(
                passes = updatedPasses,
                selectedPassIndex = safeIdx,
                selectedCategory = item.category
            )
        }
    }

    fun addPass() {
        _uiState.update { state ->
            val candidate = state.displayedEffects.firstOrNull { fx ->
                state.passes.none { it.effectId == fx.id }
            } ?: EffectCatalog.shaderEffects.firstOrNull { fx ->
                state.passes.none { it.effectId == fx.id }
            } ?: EffectCatalog.shaderEffects.first()

            val newPass = ShaderEffectPass(
                id = "pass_${UUID.randomUUID().toString().take(6)}",
                effectId = candidate.id,
                effectName = candidate.name,
                category = candidate.category,
                intensity = candidate.defaultIntensity,
                params = candidate.defaultParamMap()
            )
            val nextList = state.passes + newPass
            state.copy(
                passes = nextList,
                selectedPassIndex = nextList.lastIndex
            )
        }
    }

    fun selectPass(index: Int) {
        _uiState.update { state ->
            if (index !in state.passes.indices) return@update state
            state.copy(
                selectedPassIndex = index,
                selectedCategory = state.passes[index].category
            )
        }
    }

    fun removePass(index: Int) {
        _uiState.update { state ->
            if (index !in state.passes.indices) return@update state
            val updated = state.passes.filterIndexed { i, _ -> i != index }
            val nextIdx = state.selectedPassIndex.coerceAtMost((updated.size - 1).coerceAtLeast(0))
            state.copy(passes = updated, selectedPassIndex = nextIdx)
        }
    }

    fun updateIntensity(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        _uiState.update { state ->
            val idx = state.selectedPassIndex
            if (idx !in state.passes.indices) return@update state
            val p = state.previewProgress
            val updated = state.passes.mapIndexed { i, pass ->
                if (i != idx) pass
                else {
                    val updatedKfs = if (pass.keyframes.any { abs(it.timeProgress - p) <= 0.03f }) {
                        pass.keyframes.map { kf ->
                            if (abs(kf.timeProgress - p) <= 0.03f) kf.copy(intensity = clamped) else kf
                        }
                    } else pass.keyframes
                    pass.copy(intensity = clamped, keyframes = updatedKfs)
                }
            }
            state.copy(passes = updated)
        }
    }

    fun updatePassParam(key: String, value: Float) {
        _uiState.update { state ->
            val idx = state.selectedPassIndex
            if (idx !in state.passes.indices) return@update state
            val spec = state.activeCatalogItem.params.firstOrNull { it.key == key }
            val clamped = if (spec != null) value.coerceIn(spec.minValue, spec.maxValue) else value
            val updated = state.passes.mapIndexed { i, pass ->
                if (i != idx) pass
                else pass.copy(params = pass.params + (key to clamped))
            }
            state.copy(passes = updated)
        }
    }

    fun toggleKeyframeAtCurrentTime() {
        _uiState.update { state ->
            val idx = state.selectedPassIndex
            if (idx !in state.passes.indices) return@update state
            val progress = state.previewProgress
            val updated = state.passes.mapIndexed { i, pass ->
                if (i != idx) pass
                else {
                    val existing = pass.keyframes.firstOrNull { abs(it.timeProgress - progress) <= 0.03f }
                    val nextKfs = if (existing != null) {
                        pass.keyframes.filterNot { it.id == existing.id }
                    } else {
                        val currentVal = pass.evaluateIntensityAt(progress)
                        (pass.keyframes + EffectKeyframe(
                            id = "ekf_${UUID.randomUUID().toString().take(6)}",
                            timeProgress = progress,
                            intensity = currentVal
                        )).sortedBy { it.timeProgress }
                    }
                    pass.copy(keyframes = nextKfs)
                }
            }
            state.copy(passes = updated)
        }
    }

    fun seekToProgress(progress: Float) {
        _uiState.update { state ->
            val targetMs = (progress.coerceIn(0f, 1f) * state.totalDurationMs).toLong()
            state.copy(isPlaying = false, previewLocalMs = targetMs)
        }
    }

    fun togglePlayback() {
        _uiState.update { it.copy(isPlaying = !it.isPlaying) }
    }

    fun resetEffects() {
        _uiState.update { it.copy(passes = emptyList(), selectedPassIndex = 0) }
    }

    fun createAndSave(onDone: () -> Unit) {
        val state = _uiState.value
        if (state.targetLayerId != null) {
            VideoStarRepository.applyLayerEffectPasses(
                projectId = projectId,
                layerId = state.targetLayerId,
                passes = state.passes
            )
        } else {
            VideoStarRepository.applySceneEffectPasses(
                projectId = projectId,
                sceneIndices = state.targetSceneIndices.toSet(),
                passes = state.passes
            )
        }
        onDone()
    }

    override fun onCleared() {
        playJob?.cancel()
        super.onCleared()
    }
}
