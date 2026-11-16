package com.example.ui.screens.reeffect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.VideoStarRepository
import com.example.model.AspectRatioMode
import com.example.model.EffectCatalog
import com.example.model.EffectCatalogItem
import com.example.model.EffectCategory
import com.example.model.SceneEffect
import com.example.model.ShaderEffectPass
import com.example.model.TimelineScene
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReEffectUiState(
    val projectId: String = "",
    val targetSceneIndices: List<Int> = listOf(0),
    val aspectRatio: AspectRatioMode = AspectRatioMode.SQUARE,
    val baseScene: TimelineScene? = null,
    val selectedCategory: EffectCategory = EffectCategory.COLOR_GLOW,
    val searchQuery: String = "",
    val isSearchVisible: Boolean = false,
    val selectedEffect: EffectCatalogItem = EffectCatalog.allEffects.first(),
    val intensity: Float = 0f,
    val isPlaying: Boolean = true,
    val previewLocalMs: Long = 0L
) {
    val displayedEffects: List<EffectCatalogItem>
        get() {
            val q = searchQuery.trim()
            return if (q.isNotEmpty()) {
                EffectCatalog.allEffects.filter {
                    it.name.contains(q, ignoreCase = true) || it.category.label.contains(q, ignoreCase = true)
                }
            } else {
                EffectCatalog.byCategory(selectedCategory)
            }
        }

    val previewScene: TimelineScene?
        get() {
            val scene = baseScene ?: return null
            val fx = SceneEffect(
                category = selectedEffect.category,
                effectId = selectedEffect.id,
                effectName = selectedEffect.name,
                intensity = if (selectedEffect.id == "none") 0f else intensity
            )
            val pass = if (selectedEffect.id != "none") {
                listOf(
                    ShaderEffectPass(
                        id = "preview_${selectedEffect.id}",
                        effectId = selectedEffect.id,
                        effectName = selectedEffect.name,
                        category = selectedEffect.category,
                        intensity = intensity,
                        params = selectedEffect.defaultParamMap()
                    )
                )
            } else emptyList()
            return scene.copy(effect = fx, effectPasses = pass)
        }
}

class ReEffectViewModel(
    private val projectId: String,
    sceneIndices: List<Int>
) : ViewModel() {

    private val safeIndices = sceneIndices.ifEmpty { listOf(0) }
    private val project = VideoStarRepository.getProjectById(projectId)
    private val firstScene = project?.scenes?.getOrNull(safeIndices.first())
    private val initialEffect = firstScene?.resolvedEffectPasses()?.firstOrNull()?.let {
        EffectCatalog.findById(it.effectId)
    } ?: EffectCatalog.findById(firstScene?.effect?.effectId ?: "none")
        ?: EffectCatalog.allEffects.first()

    private val _uiState = MutableStateFlow(
        ReEffectUiState(
            projectId = projectId,
            targetSceneIndices = safeIndices,
            aspectRatio = project?.aspectRatio ?: AspectRatioMode.SQUARE,
            baseScene = firstScene,
            selectedCategory = initialEffect.category,
            selectedEffect = initialEffect,
            intensity = firstScene?.resolvedEffectPasses()?.firstOrNull()?.intensity
                ?: firstScene?.effect?.intensity
                ?: initialEffect.defaultIntensity
        )
    )
    val uiState: StateFlow<ReEffectUiState> = _uiState.asStateFlow()

    private var playJob: Job? = null

    init {
        startPreviewLoop()
    }

    private fun startPreviewLoop() {
        playJob?.cancel()
        playJob = viewModelScope.launch {
            while (true) {
                delay(40L)
                if (_uiState.value.isPlaying) {
                    _uiState.update { state ->
                        val dur = (state.baseScene?.effectiveDurationMs ?: 3_000L).coerceAtLeast(300L)
                        val next = (state.previewLocalMs + 40L) % dur
                        state.copy(previewLocalMs = next)
                    }
                }
            }
        }
    }

    fun selectCategory(category: EffectCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun toggleSearch() {
        _uiState.update { state ->
            val nextVisible = !state.isSearchVisible
            state.copy(
                isSearchVisible = nextVisible,
                searchQuery = if (!nextVisible) "" else state.searchQuery
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectEffect(item: EffectCatalogItem) {
        _uiState.update {
            it.copy(
                selectedEffect = item,
                intensity = if (item.id == "none") 0f else item.defaultIntensity
            )
        }
    }

    fun updateIntensity(value: Float) {
        _uiState.update { it.copy(intensity = value.coerceIn(0f, 1f)) }
    }

    fun togglePlayback() {
        _uiState.update { it.copy(isPlaying = !it.isPlaying) }
    }

    fun resetEffect() {
        val none = EffectCatalog.allEffects.first()
        _uiState.update {
            it.copy(
                selectedCategory = EffectCategory.COLOR_GLOW,
                selectedEffect = none,
                intensity = 0f
            )
        }
    }

    fun createAndSave(onDone: () -> Unit) {
        val state = _uiState.value
        val fx = SceneEffect(
            category = state.selectedEffect.category,
            effectId = state.selectedEffect.id,
            effectName = state.selectedEffect.name,
            intensity = if (state.selectedEffect.id == "none") 0f else state.intensity
        )
        VideoStarRepository.applySceneEffect(
            projectId = projectId,
            sceneIndices = state.targetSceneIndices.toSet(),
            effect = fx
        )
        onDone()
    }

    override fun onCleared() {
        playJob?.cancel()
        super.onCleared()
    }
}
