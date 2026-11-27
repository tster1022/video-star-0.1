package com.example.ui.screens.effects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.VideoStarRepository
import com.example.engine.packs.CustomPackRepository
import com.example.model.AspectRatioMode
import com.example.model.BezierCurveSpec
import com.example.model.EditorLayer
import com.example.model.EffectBlendMode
import com.example.model.EffectCatalog
import com.example.model.EffectCatalogItem
import com.example.model.EffectCatalogScope
import com.example.model.EffectCategory
import com.example.model.EffectKeyframe
import com.example.model.EffectParamSpec
import com.example.model.EffectParamType
import com.example.model.EffectStudioTab
import com.example.model.KeyframeEasing
import com.example.model.MediaClip
import com.example.model.PARAM_KEY_INTENSITY
import com.example.model.PARAM_KEY_OPACITY
import com.example.model.ParameterKeyframe
import com.example.model.SceneEffect
import com.example.model.SceneTransform
import com.example.model.ShaderEffectPass
import com.example.model.TimelineScene
import com.example.model.arabicDisplayLabel
import com.example.model.arabicDisplayName
import com.example.model.isCustomPackEffect
import com.example.model.toStudioTab
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs

enum class StudioPanelLevel {
    STACK,
    LIBRARY,
    EDITOR
}

data class EffectTimelineKeyframeMarker(
    val keyframeId: String,
    val propertyKey: String,
    val propertyLabel: String,
    val time: Float,
    val value: Float,
    val easing: KeyframeEasing,
    val isFocusedProperty: Boolean
)

data class EffectsStudioUiState(
    val projectId: String = "",
    val targetSceneIndices: List<Int> = listOf(0),
    val targetLayerId: String? = null,
    val aspectRatio: AspectRatioMode = AspectRatioMode.SQUARE,
    val baseScene: TimelineScene? = null,
    val targetLayer: EditorLayer? = null,
    val passes: List<ShaderEffectPass> = emptyList(),
    val selectedPassIndex: Int = 0,
    val panelLevel: StudioPanelLevel = StudioPanelLevel.STACK,
    val selectedTab: EffectStudioTab = EffectStudioTab.ALL,
    val selectedCategory: EffectCategory = EffectCategory.SHAKE_MOTION,
    val searchQuery: String = "",
    val isSearchVisible: Boolean = false,
    val showFavoritesOnly: Boolean = false,
    val favoriteEffectIds: Set<String> = setOf(
        "rgb_split",
        "glow_bloom",
        "vhs_tape",
        "hard_shake",
        "kaleidoscope",
        "gaussian_blur"
    ),
    val layerOrderIds: List<String> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val undoActionLabel: String? = null,
    val redoActionLabel: String? = null,
    val isPlaying: Boolean = true,
    val previewLocalMs: Long = 0L,
    val selectedKeyframePropertyKey: String = PARAM_KEY_INTENSITY,
    val defaultKeyframeEasing: KeyframeEasing = KeyframeEasing.LINEAR,
    val catalogScope: EffectCatalogScope = EffectCatalogScope.ALL,
    val customPackVersion: Int = 0
) {
    internal fun toHistorySnapshot(
        entryType: HistoryEntryType = HistoryEntryType.EFFECT_STACK_CHANGE,
        description: String = ""
    ): EffectsStudioHistorySnapshot = EffectsStudioHistorySnapshot(
        passes = passes,
        selectedPassIndex = selectedPassIndex,
        layerOrderIds = layerOrderIds,
        selectedKeyframePropertyKey = selectedKeyframePropertyKey,
        entryType = entryType,
        description = description
    )
    val activePass: ShaderEffectPass?
        get() = passes.getOrNull(selectedPassIndex)

    val activeCatalogItem: EffectCatalogItem
        get() = activePass?.let { EffectCatalog.findById(it.effectId) } ?: EffectCatalog.noneItem

    val previewClip: MediaClip?
        get() = baseScene?.clip ?: targetLayer?.mediaClip

    val layerDisplayTitle: String
        get() = when {
            targetLayer != null -> "الطبقة ${targetLayer.number}"
            targetLayerId != null -> {
                val parsed = targetLayerId.substringAfterLast('_').toIntOrNull()
                if (parsed != null) "الطبقة $parsed" else "الطبقة"
            }
            targetSceneIndices.size > 1 -> "المؤثرات (${targetSceneIndices.size} مشاهد)"
            else -> "المشهد ${(targetSceneIndices.firstOrNull() ?: 0) + 1}"
        }

    val totalDurationMs: Long
        get() = (baseScene?.effectiveDurationMs
            ?: targetLayer?.mediaClip?.effectiveDurationMs
            ?: 3_000L).coerceAtLeast(300L)

    val previewProgress: Float
        get() = (previewLocalMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)

    val hasKeyframeNearCurrentTime: Boolean
        get() = hasKeyframeNear(selectedKeyframePropertyKey)

    val activeEvaluatedIntensity: Float
        get() = activePass?.evaluateIntensityAt(previewProgress) ?: 0f

    val activeEvaluatedOpacity: Float
        get() = activePass?.evaluateOpacityAt(previewProgress) ?: 1f

    fun evaluatedParamValue(param: EffectParamSpec): Float =
        activePass?.evaluateParamAt(param.key, previewProgress, param.defaultValue, param.minValue, param.maxValue)
            ?: param.defaultValue

    fun evaluatedColorValue(param: EffectParamSpec): Long =
        activePass?.evaluateColorAt(param.key, previewProgress, param.defaultColorArgb)
            ?: param.defaultColorArgb

    fun evaluatedTextValue(param: EffectParamSpec): String =
        activePass?.textParam(param.key, param.defaultText) ?: param.defaultText

    fun keyframesFor(propertyKey: String): List<ParameterKeyframe> =
        activePass?.keyframesForProperty(propertyKey).orEmpty()

    fun hasAnyKeyframes(propertyKey: String): Boolean =
        activePass?.hasKeyframesForProperty(propertyKey) == true

    fun hasKeyframeNear(propertyKey: String, tolerance: Float = 0.03f): Boolean =
        activePass?.hasKeyframeNear(propertyKey, previewProgress, tolerance) == true

    fun activeKeyframeFor(propertyKey: String): ParameterKeyframe? {
        val kfs = keyframesFor(propertyKey)
        if (kfs.isEmpty()) return null
        return kfs.minByOrNull { abs(it.time - previewProgress) }?.takeIf { abs(it.time - previewProgress) <= 0.03f }
            ?: kfs.lastOrNull { it.time <= previewProgress }
            ?: kfs.first()
    }

    fun activeEasingFor(propertyKey: String): KeyframeEasing =
        activeKeyframeFor(propertyKey)?.easing ?: defaultKeyframeEasing

    fun activeCustomBezierFor(propertyKey: String): BezierCurveSpec? =
        activeKeyframeFor(propertyKey)?.customBezier

    val allActivePassKeyframeTimes: List<Float>
        get() = activePass?.allKeyframeTimes().orEmpty()

    val selectedPropertyKeyframeTimes: List<Float>
        get() = keyframesFor(selectedKeyframePropertyKey).map { it.time }

    val activePassKeyframeMarkers: List<EffectTimelineKeyframeMarker>
        get() {
            val pass = activePass ?: return emptyList()
            val catalog = activeCatalogItem
            return buildList {
                pass.keyframesForProperty(PARAM_KEY_INTENSITY).forEach { kf ->
                    add(
                        EffectTimelineKeyframeMarker(
                            keyframeId = kf.id,
                            propertyKey = PARAM_KEY_INTENSITY,
                            propertyLabel = "الشدة",
                            time = kf.time,
                            value = kf.value,
                            easing = kf.easing,
                            isFocusedProperty = selectedKeyframePropertyKey == PARAM_KEY_INTENSITY
                        )
                    )
                }
                catalog.params.forEach { param ->
                    pass.keyframesForProperty(param.key).forEach { kf ->
                        add(
                            EffectTimelineKeyframeMarker(
                                keyframeId = kf.id,
                                propertyKey = param.key,
                                propertyLabel = param.arabicDisplayLabel(),
                                time = kf.time,
                                value = kf.value,
                                easing = kf.easing,
                                isFocusedProperty = selectedKeyframePropertyKey == param.key
                            )
                        )
                    }
                }
                pass.keyframesForProperty(PARAM_KEY_OPACITY).forEach { kf ->
                    add(
                        EffectTimelineKeyframeMarker(
                            keyframeId = kf.id,
                            propertyKey = PARAM_KEY_OPACITY,
                            propertyLabel = "الشفافية",
                            time = kf.time,
                            value = kf.value,
                            easing = kf.easing,
                            isFocusedProperty = selectedKeyframePropertyKey == PARAM_KEY_OPACITY
                        )
                    )
                }
            }.sortedWith(compareBy<EffectTimelineKeyframeMarker> { it.time }.thenBy { it.isFocusedProperty })
        }

    val scopedEffects: List<EffectCatalogItem>
        get() = when (catalogScope) {
            EffectCatalogScope.ALL -> EffectCatalog.combinedShaderEffects
            EffectCatalogScope.VIDEO_STAR_ONLY -> EffectCatalog.combinedShaderEffects.filter {
                !it.category.isAlightMotion || it.isCustomPackEffect()
            }
            EffectCatalogScope.AFTER_EFFECTS_ONLY -> EffectCatalog.combinedShaderEffects.filter {
                it.category.isAlightMotion || it.isCustomPackEffect()
            }
        }

    val availableTabs: List<EffectStudioTab>
        get() = EffectStudioTab.tabsForScope(catalogScope)

    val displayedEffects: List<EffectCatalogItem>
        get() {
            val q = searchQuery.trim()
            val allEffects = scopedEffects
            val base = if (showFavoritesOnly) {
                allEffects.filter { it.id in favoriteEffectIds }
            } else {
                allEffects
            }
            return when {
                q.isNotEmpty() -> base.filter {
                    it.name.contains(q, ignoreCase = true) ||
                        it.arabicDisplayName().contains(q, ignoreCase = true) ||
                        it.category.label.contains(q, ignoreCase = true) ||
                        it.toStudioTab().label.contains(q, ignoreCase = true) ||
                        it.toStudioTab().arabicLabel.contains(q, ignoreCase = true)
                }
                selectedTab == EffectStudioTab.ALL -> base
                else -> base.filter { it.toStudioTab() == selectedTab }
            }
        }

    fun tabCount(tab: EffectStudioTab): Int = when (tab) {
        EffectStudioTab.ALL -> scopedEffects.size
        else -> scopedEffects.count { it.toStudioTab() == tab }
    }

    val previewScene: TimelineScene
        get() {
            val base = if (targetLayer != null) {
                val clip = targetLayer.mediaClip
                    ?: if (targetLayer.backgroundColorArgb == null) baseScene?.clip else null
                TimelineScene(
                    id = "layer_preview_${targetLayer.id}",
                    title = "الطبقة ${targetLayer.number}",
                    durationMs = clip?.effectiveDurationMs ?: baseScene?.effectiveDurationMs ?: 3_000L,
                    clipId = clip?.id,
                    mediaUriString = clip?.uriString,
                    isVideo = clip is MediaClip.Video,
                    clip = clip,
                    transform = SceneTransform(
                        bgColorArgb = targetLayer.backgroundColorArgb ?: 0xFF000000L,
                        isFitMode = clip?.isFitMode ?: false
                    )
                )
            } else {
                baseScene ?: TimelineScene(
                    id = "studio_calibration_preview",
                    title = layerDisplayTitle,
                    durationMs = 3_000L
                )
            }

            val firstPass = passes.firstOrNull { it.effectId != "none" && it.isEnabled }
            val primaryFx = if (firstPass != null) {
                SceneEffect(
                    category = firstPass.category,
                    effectId = firstPass.effectId,
                    effectName = firstPass.effectName,
                    intensity = (firstPass.evaluateIntensityAt(previewProgress) * firstPass.evaluateOpacityAt(previewProgress)).coerceIn(0f, 1f)
                )
            } else {
                SceneEffect()
            }

            return base.copy(
                effect = primaryFx,
                effectPasses = passes
            )
        }
}

class EffectsStudioViewModel(
    private val projectId: String,
    sceneIndices: List<Int> = listOf(0),
    private val targetLayerId: String? = null,
    private val catalogScope: EffectCatalogScope = EffectCatalogScope.ALL,
    private val historyHelper: EffectsStudioHistoryHelper = EffectsStudioHistoryHelper()
) : ViewModel() {

    private val safeIndices = sceneIndices.ifEmpty { listOf(0) }
    private val project = VideoStarRepository.getProjectById(projectId)
    private val firstScene = project?.scenes?.getOrNull(safeIndices.first())
    private val layer = targetLayerId?.let { id ->
        (project?.aeLayers.orEmpty() + project?.layers.orEmpty()).firstOrNull { it.id == id }
    }

    private val initialPasses: List<ShaderEffectPass> = when {
        layer != null -> layer.effectPasses
        firstScene != null -> firstScene.resolvedEffectPasses()
        else -> emptyList()
    }

    private val initialLayerOrderIds: List<String> = project?.layers?.map { it.id }.orEmpty()
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
            layerOrderIds = initialLayerOrderIds,
            selectedPassIndex = 0,
            panelLevel = StudioPanelLevel.STACK,
            selectedTab = EffectStudioTab.ALL,
            selectedCategory = initialCategory,
            catalogScope = catalogScope
        )
    )
    val uiState: StateFlow<EffectsStudioUiState> = _uiState.asStateFlow()

    private var playJob: Job? = null

    init {
        startPreviewLoop()
        CustomPackRepository.importedShaders
            .onEach { _uiState.update { s -> s.copy(customPackVersion = s.customPackVersion + 1) } }
            .launchIn(viewModelScope)
        CustomPackRepository.importedPresets
            .onEach { _uiState.update { s -> s.copy(customPackVersion = s.customPackVersion + 1) } }
            .launchIn(viewModelScope)
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

    private fun EffectsStudioUiState.withHistoryFlags(): EffectsStudioUiState = copy(
        canUndo = historyHelper.canUndo,
        canRedo = historyHelper.canRedo,
        undoActionLabel = historyHelper.nextUndoDescription,
        redoActionLabel = historyHelper.nextRedoDescription
    )

    private fun pushUndoSnapshot(
        entryType: HistoryEntryType = HistoryEntryType.EFFECT_STACK_CHANGE,
        description: String = ""
    ) {
        historyHelper.recordSnapshot(_uiState.value.toHistorySnapshot(entryType, description))
    }

    fun undo() {
        val current = _uiState.value
        val restored = historyHelper.undo(current.toHistorySnapshot()) ?: return
        _uiState.update { state ->
            val safeIdx = if (restored.passes.isEmpty()) 0
            else restored.selectedPassIndex.coerceIn(0, restored.passes.lastIndex)
            state.copy(
                passes = restored.passes,
                selectedPassIndex = safeIdx,
                layerOrderIds = restored.layerOrderIds,
                selectedKeyframePropertyKey = restored.selectedKeyframePropertyKey,
                panelLevel = if (restored.passes.isEmpty() && state.panelLevel == StudioPanelLevel.EDITOR) {
                    StudioPanelLevel.STACK
                } else {
                    state.panelLevel
                }
            ).withHistoryFlags()
        }
    }

    fun redo() {
        val current = _uiState.value
        val restored = historyHelper.redo(current.toHistorySnapshot()) ?: return
        _uiState.update { state ->
            val safeIdx = if (restored.passes.isEmpty()) 0
            else restored.selectedPassIndex.coerceIn(0, restored.passes.lastIndex)
            state.copy(
                passes = restored.passes,
                selectedPassIndex = safeIdx,
                layerOrderIds = restored.layerOrderIds,
                selectedKeyframePropertyKey = restored.selectedKeyframePropertyKey,
                panelLevel = if (restored.passes.isEmpty() && state.panelLevel == StudioPanelLevel.EDITOR) {
                    StudioPanelLevel.STACK
                } else {
                    state.panelLevel
                }
            ).withHistoryFlags()
        }
    }

    fun openEffectLibrary() {
        _uiState.update { it.copy(panelLevel = StudioPanelLevel.LIBRARY) }
    }

    fun backToEffectStack() {
        _uiState.update { it.copy(panelLevel = StudioPanelLevel.STACK, isSearchVisible = false, searchQuery = "") }
    }

    fun openPassEditor(index: Int) {
        _uiState.update { state ->
            if (index !in state.passes.indices) return@update state
            state.copy(
                selectedPassIndex = index,
                selectedCategory = state.passes[index].category,
                panelLevel = StudioPanelLevel.EDITOR
            )
        }
    }

    fun selectTab(tab: EffectStudioTab) {
        val mappedCat = when (tab) {
            EffectStudioTab.ALL -> _uiState.value.selectedCategory
            EffectStudioTab.MY_EFFECTS -> EffectCategory.AM_PROCEDURAL
            EffectStudioTab.VIDEO_STAR -> {
                val cur = _uiState.value.selectedCategory
                if (!cur.isAlightMotion) cur else EffectCategory.SHAKE_MOTION
            }
            EffectStudioTab.MOVE_TRANSFORM -> EffectCategory.AM_MOVE_TRANSFORM
            EffectStudioTab.DISTORTION_WARP -> EffectCategory.AM_DISTORTION_WARP
            EffectStudioTab.MATTE_MASK_KEY -> EffectCategory.AM_MATTE_MASK_KEY
            EffectStudioTab.OPACITY_VISIBILITY -> EffectCategory.AM_OPACITY_VISIBILITY
            EffectStudioTab.PROCEDURAL -> EffectCategory.AM_PROCEDURAL
            EffectStudioTab.REPEAT -> EffectCategory.AM_REPEAT
            EffectStudioTab.THREE_D -> EffectCategory.AM_THREE_D
            EffectStudioTab.TEXT -> EffectCategory.AM_TEXT
            EffectStudioTab.OTHER -> EffectCategory.AM_OTHER
        }
        _uiState.update {
            it.copy(
                selectedTab = tab,
                selectedCategory = mappedCat,
                showFavoritesOnly = false
            )
        }
    }

    fun selectCategory(category: EffectCategory) {
        val mappedTab = when (category) {
            EffectCategory.SHAKE_MOTION,
            EffectCategory.BLUR_FOCUS,
            EffectCategory.COLOR_GLOW,
            EffectCategory.DISTORTION_GLITCH,
            EffectCategory.MIRROR_KALEIDOSCOPE,
            EffectCategory.RETRO_VHS -> EffectStudioTab.VIDEO_STAR
            EffectCategory.AM_MOVE_TRANSFORM -> EffectStudioTab.MOVE_TRANSFORM
            EffectCategory.AM_DISTORTION_WARP -> EffectStudioTab.DISTORTION_WARP
            EffectCategory.AM_MATTE_MASK_KEY -> EffectStudioTab.MATTE_MASK_KEY
            EffectCategory.AM_OPACITY_VISIBILITY -> EffectStudioTab.OPACITY_VISIBILITY
            EffectCategory.AM_PROCEDURAL -> EffectStudioTab.PROCEDURAL
            EffectCategory.AM_REPEAT -> EffectStudioTab.REPEAT
            EffectCategory.AM_THREE_D -> EffectStudioTab.THREE_D
            EffectCategory.AM_TEXT -> EffectStudioTab.TEXT
            EffectCategory.AM_OTHER -> EffectStudioTab.OTHER
        }
        _uiState.update {
            it.copy(
                selectedCategory = category,
                selectedTab = mappedTab,
                showFavoritesOnly = false
            )
        }
    }

    fun toggleFavoritesFilter() {
        _uiState.update { it.copy(showFavoritesOnly = !it.showFavoritesOnly) }
    }

    fun toggleFavoriteEffect(effectId: String) {
        _uiState.update { state ->
            val updated = if (effectId in state.favoriteEffectIds) {
                state.favoriteEffectIds - effectId
            } else {
                state.favoriteEffectIds + effectId
            }
            state.copy(favoriteEffectIds = updated)
        }
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

    fun addEffectFromLibrary(item: EffectCatalogItem) {
        if (item.id == "none") return
        pushUndoSnapshot(HistoryEntryType.EFFECT_STACK_CHANGE, "إضافة ${item.arabicDisplayName()}")
        val expanded = CustomPackRepository.expandEffectPassesForSelection(item.id)
        val passesToAdd = if (expanded.isNotEmpty()) {
            expanded
        } else {
            listOf(
                ShaderEffectPass(
                    id = "pass_${UUID.randomUUID().toString().take(6)}",
                    effectId = item.id,
                    effectName = item.name,
                    category = item.category,
                    intensity = item.defaultIntensity,
                    params = item.defaultParamMap(),
                    colorParams = item.defaultColorMap(),
                    textParams = item.defaultTextMap(),
                    keyframes = emptyList(),
                    isEnabled = true,
                    blendMode = EffectBlendMode.NORMAL,
                    passOpacity = 1.0f
                )
            )
        }
        _uiState.update { state ->
            val nextList = state.passes + passesToAdd
            state.copy(
                passes = nextList,
                selectedPassIndex = nextList.lastIndex,
                selectedCategory = passesToAdd.lastOrNull()?.category ?: item.category,
                panelLevel = StudioPanelLevel.EDITOR,
                isSearchVisible = false,
                searchQuery = ""
            ).withHistoryFlags()
        }
    }

    fun selectEffect(item: EffectCatalogItem) {
        if (item.id == "none") {
            resetEffects()
            return
        }
        pushUndoSnapshot(HistoryEntryType.EFFECT_STACK_CHANGE, "تبديل المؤثر")
        _uiState.update { state ->
            val existing = state.passes
            val newPass = ShaderEffectPass(
                id = existing.getOrNull(state.selectedPassIndex)?.id ?: "pass_${UUID.randomUUID().toString().take(6)}",
                effectId = item.id,
                effectName = item.name,
                category = item.category,
                intensity = item.defaultIntensity,
                params = item.defaultParamMap(),
                colorParams = item.defaultColorMap(),
                textParams = item.defaultTextMap(),
                keyframes = emptyList(),
                isEnabled = true,
                blendMode = EffectBlendMode.NORMAL,
                passOpacity = 1.0f
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
            ).withHistoryFlags()
        }
    }

    fun addPass() {
        pushUndoSnapshot(HistoryEntryType.EFFECT_STACK_CHANGE, "إضافة مؤثر")
        _uiState.update { state ->
            val candidate = state.displayedEffects.firstOrNull { fx ->
                state.passes.none { it.effectId == fx.id }
            } ?: EffectCatalog.combinedShaderEffects.firstOrNull { fx ->
                state.passes.none { it.effectId == fx.id }
            } ?: EffectCatalog.combinedShaderEffects.first()

            val newPass = ShaderEffectPass(
                id = "pass_${UUID.randomUUID().toString().take(6)}",
                effectId = candidate.id,
                effectName = candidate.name,
                category = candidate.category,
                intensity = candidate.defaultIntensity,
                params = candidate.defaultParamMap(),
                colorParams = candidate.defaultColorMap(),
                textParams = candidate.defaultTextMap()
            )
            val nextList = state.passes + newPass
            state.copy(
                passes = nextList,
                selectedPassIndex = nextList.lastIndex
            ).withHistoryFlags()
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

    fun togglePassEnabled(index: Int) {
        if (index !in _uiState.value.passes.indices) return
        pushUndoSnapshot(HistoryEntryType.EFFECT_STACK_CHANGE, "تغيير رؤية المؤثر")
        _uiState.update { state ->
            if (index !in state.passes.indices) return@update state
            val updated = state.passes.mapIndexed { i, pass ->
                if (i == index) pass.copy(isEnabled = !pass.isEnabled) else pass
            }
            state.copy(passes = updated).withHistoryFlags()
        }
    }

    fun duplicatePass(index: Int) {
        if (index !in _uiState.value.passes.indices) return
        pushUndoSnapshot(HistoryEntryType.EFFECT_STACK_CHANGE, "تكرار مؤثر")
        _uiState.update { state ->
            val target = state.passes.getOrNull(index) ?: return@update state
            val copyPass = target.copy(id = "pass_${UUID.randomUUID().toString().take(6)}")
            val nextList = state.passes.toMutableList().apply {
                add((index + 1).coerceAtMost(size), copyPass)
            }
            state.copy(
                passes = nextList,
                selectedPassIndex = (index + 1).coerceAtMost(nextList.lastIndex)
            ).withHistoryFlags()
        }
    }

    fun beginPassReorderDrag() {
        historyHelper.beginTransaction(
            transactionKey = "pass_reorder",
            snapshotBefore = _uiState.value.toHistorySnapshot(
                entryType = HistoryEntryType.LAYER_ORDER_CHANGE,
                description = "إعادة ترتيب المؤثرات"
            )
        )
        _uiState.update { it.withHistoryFlags() }
    }

    fun endPassReorderDrag() {
        historyHelper.commitTransaction(_uiState.value.toHistorySnapshot())
        _uiState.update { it.withHistoryFlags() }
    }

    fun movePass(fromIndex: Int, toIndex: Int, recordUndo: Boolean = true) {
        if (fromIndex == toIndex) return
        val passesNow = _uiState.value.passes
        if (fromIndex !in passesNow.indices || toIndex !in passesNow.indices) return
        if (recordUndo) {
            pushUndoSnapshot(HistoryEntryType.LAYER_ORDER_CHANGE, "إعادة ترتيب المؤثرات")
        }
        _uiState.update { state ->
            val mutable = state.passes.toMutableList()
            val moved = mutable.removeAt(fromIndex)
            mutable.add(toIndex, moved)
            val curSelected = state.selectedPassIndex
            val nextSelected = when {
                curSelected == fromIndex -> toIndex
                fromIndex < toIndex && curSelected in (fromIndex + 1)..toIndex -> curSelected - 1
                fromIndex > toIndex && curSelected in toIndex until fromIndex -> curSelected + 1
                else -> curSelected.coerceIn(0, mutable.lastIndex)
            }
            state.copy(
                passes = mutable,
                selectedPassIndex = nextSelected
            ).withHistoryFlags()
        }
    }

    fun beginLayerOrderAdjust() {
        historyHelper.beginTransaction(
            transactionKey = "layer_order",
            snapshotBefore = _uiState.value.toHistorySnapshot(
                entryType = HistoryEntryType.LAYER_ORDER_CHANGE,
                description = "إعادة ترتيب الطبقات"
            )
        )
        _uiState.update { it.withHistoryFlags() }
    }

    fun endLayerOrderAdjust() {
        historyHelper.commitTransaction(_uiState.value.toHistorySnapshot())
        _uiState.update { it.withHistoryFlags() }
    }

    fun moveLayerOrder(fromIndex: Int, toIndex: Int, recordUndo: Boolean = true) {
        if (fromIndex == toIndex) return
        val orderNow = _uiState.value.layerOrderIds
        if (fromIndex !in orderNow.indices || toIndex !in orderNow.indices) return
        if (recordUndo) {
            pushUndoSnapshot(HistoryEntryType.LAYER_ORDER_CHANGE, "إعادة ترتيب الطبقات")
        }
        _uiState.update { state ->
            val mutable = state.layerOrderIds.toMutableList()
            val moved = mutable.removeAt(fromIndex)
            mutable.add(toIndex, moved)
            state.copy(layerOrderIds = mutable).withHistoryFlags()
        }
    }

    fun removePass(index: Int) {
        if (index !in _uiState.value.passes.indices) return
        pushUndoSnapshot(HistoryEntryType.EFFECT_STACK_CHANGE, "حذف مؤثر")
        _uiState.update { state ->
            if (index !in state.passes.indices) return@update state
            val updated = state.passes.filterIndexed { i, _ -> i != index }
            val nextIdx = state.selectedPassIndex.coerceAtMost((updated.size - 1).coerceAtLeast(0))
            val nextLevel = if (updated.isEmpty() && state.panelLevel == StudioPanelLevel.EDITOR) {
                StudioPanelLevel.STACK
            } else {
                state.panelLevel
            }
            state.copy(
                passes = updated,
                selectedPassIndex = nextIdx,
                panelLevel = nextLevel
            ).withHistoryFlags()
        }
    }

    fun focusKeyframeProperty(propertyKey: String) {
        _uiState.update { it.copy(selectedKeyframePropertyKey = propertyKey) }
    }

    fun commitParameterEdit() {
        historyHelper.commitTransaction(_uiState.value.toHistorySnapshot())
        _uiState.update { it.withHistoryFlags() }
    }

    fun updateIntensity(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        val snapshot = _uiState.value
        val activePass = snapshot.passes.getOrNull(snapshot.selectedPassIndex) ?: return
        historyHelper.recordCoalescedChange(
            coalesceKey = "param:${activePass.id}:$PARAM_KEY_INTENSITY",
            snapshotBefore = snapshot.toHistorySnapshot(
                entryType = HistoryEntryType.PARAMETER_CHANGE,
                description = "تعديل الشدة"
            )
        )
        _uiState.update { state ->
            val idx = state.selectedPassIndex
            if (idx !in state.passes.indices) return@update state
            val p = state.previewProgress
            val updated = state.passes.mapIndexed { i, pass ->
                if (i != idx) pass
                else {
                    val hasKfs = pass.hasKeyframesForProperty(PARAM_KEY_INTENSITY)
                    if (!hasKfs) {
                        pass.copy(intensity = clamped)
                    } else {
                        val existingList = pass.keyframesForProperty(PARAM_KEY_INTENSITY)
                        val nearIdx = existingList.indexOfFirst { abs(it.time - p) <= 0.03f }
                        val nextParamList = if (nearIdx >= 0) {
                            existingList.mapIndexed { kIdx, kf -> if (kIdx == nearIdx) kf.copy(value = clamped) else kf }
                        } else {
                            (existingList + ParameterKeyframe(
                                id = "ekf_${UUID.randomUUID().toString().take(6)}",
                                time = p,
                                value = clamped,
                                easing = state.defaultKeyframeEasing
                            )).sortedBy { it.time }
                        }
                        applyPropertyKeyframesToPass(pass, PARAM_KEY_INTENSITY, nextParamList).copy(intensity = clamped)
                    }
                }
            }
            state.copy(
                passes = updated,
                selectedKeyframePropertyKey = PARAM_KEY_INTENSITY
            ).withHistoryFlags()
        }
    }

    fun updatePassParam(key: String, value: Float) {
        val snapshot = _uiState.value
        val activePass = snapshot.passes.getOrNull(snapshot.selectedPassIndex) ?: return
        val paramLabel = snapshot.activeCatalogItem.params.firstOrNull { it.key == key }?.arabicDisplayLabel() ?: key
        historyHelper.recordCoalescedChange(
            coalesceKey = "param:${activePass.id}:$key",
            snapshotBefore = snapshot.toHistorySnapshot(
                entryType = HistoryEntryType.PARAMETER_CHANGE,
                description = "تعديل $paramLabel"
            )
        )
        _uiState.update { state ->
            val idx = state.selectedPassIndex
            if (idx !in state.passes.indices) return@update state
            val spec = state.activeCatalogItem.params.firstOrNull { it.key == key }
            val clamped = if (spec != null) value.coerceIn(spec.minValue, spec.maxValue) else value
            val p = state.previewProgress
            val updated = state.passes.mapIndexed { i, pass ->
                if (i != idx) pass
                else {
                    val hasKfs = pass.hasKeyframesForProperty(key)
                    if (!hasKfs) {
                        pass.copy(params = pass.params + (key to clamped))
                    } else {
                        val existingList = pass.keyframesForProperty(key)
                        val nearIdx = existingList.indexOfFirst { abs(it.time - p) <= 0.03f }
                        val nextParamList = if (nearIdx >= 0) {
                            existingList.mapIndexed { kIdx, kf -> if (kIdx == nearIdx) kf.copy(value = clamped) else kf }
                        } else {
                            (existingList + ParameterKeyframe(
                                id = "pkf_${UUID.randomUUID().toString().take(6)}",
                                time = p,
                                value = clamped,
                                easing = state.defaultKeyframeEasing
                            )).sortedBy { it.time }
                        }
                        applyPropertyKeyframesToPass(pass, key, nextParamList).copy(params = pass.params + (key to clamped))
                    }
                }
            }
            state.copy(
                passes = updated,
                selectedKeyframePropertyKey = key
            ).withHistoryFlags()
        }
    }

    fun updatePassPoint2D(keyX: String, keyY: String, x: Float, y: Float) {
        val snapshot = _uiState.value
        val activePass = snapshot.passes.getOrNull(snapshot.selectedPassIndex) ?: return
        historyHelper.recordCoalescedChange(
            coalesceKey = "param:${activePass.id}:$keyX:$keyY",
            snapshotBefore = snapshot.toHistorySnapshot(
                entryType = HistoryEntryType.PARAMETER_CHANGE,
                description = "Adjust 2D Position"
            )
        )
        _uiState.update { state ->
            val idx = state.selectedPassIndex
            if (idx !in state.passes.indices) return@update state
            val specX = state.activeCatalogItem.params.firstOrNull { it.key == keyX }
            val specY = state.activeCatalogItem.params.firstOrNull { it.key == keyY }
            val cx = if (specX != null) x.coerceIn(specX.minValue, specX.maxValue) else x
            val cy = if (specY != null) y.coerceIn(specY.minValue, specY.maxValue) else y
            val p = state.previewProgress
            val updated = state.passes.mapIndexed { i, pass ->
                if (i != idx) pass
                else {
                    var nextPass = pass.copy(params = pass.params + (keyX to cx) + (keyY to cy))
                    if (nextPass.hasKeyframesForProperty(keyX)) {
                        val listX = nextPass.keyframesForProperty(keyX)
                        val nearX = listX.indexOfFirst { abs(it.time - p) <= 0.03f }
                        val nextX = if (nearX >= 0) {
                            listX.mapIndexed { kIdx, kf -> if (kIdx == nearX) kf.copy(value = cx) else kf }
                        } else {
                            (listX + ParameterKeyframe("pkf_${UUID.randomUUID().toString().take(6)}", p, cx, state.defaultKeyframeEasing)).sortedBy { it.time }
                        }
                        nextPass = applyPropertyKeyframesToPass(nextPass, keyX, nextX)
                    }
                    if (nextPass.hasKeyframesForProperty(keyY)) {
                        val listY = nextPass.keyframesForProperty(keyY)
                        val nearY = listY.indexOfFirst { abs(it.time - p) <= 0.03f }
                        val nextY = if (nearY >= 0) {
                            listY.mapIndexed { kIdx, kf -> if (kIdx == nearY) kf.copy(value = cy) else kf }
                        } else {
                            (listY + ParameterKeyframe("pkf_${UUID.randomUUID().toString().take(6)}", p, cy, state.defaultKeyframeEasing)).sortedBy { it.time }
                        }
                        nextPass = applyPropertyKeyframesToPass(nextPass, keyY, nextY)
                    }
                    nextPass
                }
            }
            state.copy(
                passes = updated,
                selectedKeyframePropertyKey = keyX
            ).withHistoryFlags()
        }
    }

    fun updatePassColor(key: String, argb: Long) {
        val snapshot = _uiState.value
        val activePass = snapshot.passes.getOrNull(snapshot.selectedPassIndex) ?: return
        val paramLabel = snapshot.activeCatalogItem.params.firstOrNull { it.key == key }?.arabicDisplayLabel() ?: key
        historyHelper.recordCoalescedChange(
            coalesceKey = "color:${activePass.id}:$key",
            snapshotBefore = snapshot.toHistorySnapshot(
                entryType = HistoryEntryType.PARAMETER_CHANGE,
                description = "تعديل $paramLabel"
            )
        )
        _uiState.update { state ->
            val idx = state.selectedPassIndex
            if (idx !in state.passes.indices) return@update state
            val p = state.previewProgress
            val updated = state.passes.mapIndexed { i, pass ->
                if (i != idx) pass
                else {
                    val hasKfs = pass.hasKeyframesForProperty(key)
                    if (!hasKfs) {
                        pass.copy(colorParams = pass.colorParams + (key to argb))
                    } else {
                        val existingList = pass.keyframesForProperty(key)
                        val nearIdx = existingList.indexOfFirst { abs(it.time - p) <= 0.03f }
                        val nextList = if (nearIdx >= 0) {
                            existingList.mapIndexed { kIdx, kf ->
                                if (kIdx == nearIdx) kf.copy(colorArgb = argb) else kf
                            }
                        } else {
                            (existingList + ParameterKeyframe(
                                id = "ckf_${UUID.randomUUID().toString().take(6)}",
                                time = p,
                                value = 1f,
                                easing = state.defaultKeyframeEasing,
                                colorArgb = argb
                            )).sortedBy { it.time }
                        }
                        applyPropertyKeyframesToPass(pass, key, nextList)
                            .copy(colorParams = pass.colorParams + (key to argb))
                    }
                }
            }
            state.copy(
                passes = updated,
                selectedKeyframePropertyKey = key
            ).withHistoryFlags()
        }
    }

    fun updatePassText(key: String, value: String) {
        val snapshot = _uiState.value
        val activePass = snapshot.passes.getOrNull(snapshot.selectedPassIndex) ?: return
        historyHelper.recordCoalescedChange(
            coalesceKey = "text:${activePass.id}:$key",
            snapshotBefore = snapshot.toHistorySnapshot(
                entryType = HistoryEntryType.PARAMETER_CHANGE,
                description = "تعديل النص"
            )
        )
        _uiState.update { state ->
            val idx = state.selectedPassIndex
            if (idx !in state.passes.indices) return@update state
            val updated = state.passes.mapIndexed { i, pass ->
                if (i == idx) pass.copy(textParams = pass.textParams + (key to value)) else pass
            }
            state.copy(passes = updated).withHistoryFlags()
        }
    }

    fun updatePassBlendMode(blendMode: EffectBlendMode) {
        if (_uiState.value.activePass == null) return
        pushUndoSnapshot(HistoryEntryType.PARAMETER_CHANGE, "تغيير وضع المزج")
        _uiState.update { state ->
            val idx = state.selectedPassIndex
            if (idx !in state.passes.indices) return@update state
            val updated = state.passes.mapIndexed { i, pass ->
                if (i == idx) pass.copy(blendMode = blendMode) else pass
            }
            state.copy(passes = updated).withHistoryFlags()
        }
    }

    fun updatePassOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(0f, 1f)
        val snapshot = _uiState.value
        val activePass = snapshot.passes.getOrNull(snapshot.selectedPassIndex) ?: return
        historyHelper.recordCoalescedChange(
            coalesceKey = "param:${activePass.id}:$PARAM_KEY_OPACITY",
            snapshotBefore = snapshot.toHistorySnapshot(
                entryType = HistoryEntryType.PARAMETER_CHANGE,
                description = "تعديل الشفافية"
            )
        )
        _uiState.update { state ->
            val idx = state.selectedPassIndex
            if (idx !in state.passes.indices) return@update state
            val p = state.previewProgress
            val updated = state.passes.mapIndexed { i, pass ->
                if (i != idx) pass
                else {
                    val hasKfs = pass.hasKeyframesForProperty(PARAM_KEY_OPACITY)
                    if (!hasKfs) {
                        pass.copy(passOpacity = clamped)
                    } else {
                        val existingList = pass.keyframesForProperty(PARAM_KEY_OPACITY)
                        val nearIdx = existingList.indexOfFirst { abs(it.time - p) <= 0.03f }
                        val nextParamList = if (nearIdx >= 0) {
                            existingList.mapIndexed { kIdx, kf -> if (kIdx == nearIdx) kf.copy(value = clamped) else kf }
                        } else {
                            (existingList + ParameterKeyframe(
                                id = "okf_${UUID.randomUUID().toString().take(6)}",
                                time = p,
                                value = clamped,
                                easing = state.defaultKeyframeEasing
                            )).sortedBy { it.time }
                        }
                        applyPropertyKeyframesToPass(pass, PARAM_KEY_OPACITY, nextParamList).copy(passOpacity = clamped)
                    }
                }
            }
            state.copy(
                passes = updated,
                selectedKeyframePropertyKey = PARAM_KEY_OPACITY
            ).withHistoryFlags()
        }
    }

    fun resetActivePassParameters() {
        if (_uiState.value.activePass == null) return
        pushUndoSnapshot(HistoryEntryType.PARAMETER_CHANGE, "إعادة ضبط المؤثر")
        _uiState.update { state ->
            val idx = state.selectedPassIndex
            if (idx !in state.passes.indices) return@update state
            val catalogItem = state.activeCatalogItem
            val updated = state.passes.mapIndexed { i, pass ->
                if (i != idx) pass
                else pass.copy(
                    intensity = catalogItem.defaultIntensity,
                    params = catalogItem.defaultParamMap(),
                    colorParams = catalogItem.defaultColorMap(),
                    textParams = catalogItem.defaultTextMap(),
                    blendMode = EffectBlendMode.NORMAL,
                    passOpacity = 1.0f,
                    keyframes = emptyList(),
                    paramKeyframes = emptyMap()
                )
            }
            state.copy(passes = updated).withHistoryFlags()
        }
    }

    fun toggleKeyframeAtCurrentTime() {
        togglePropertyKeyframe(_uiState.value.selectedKeyframePropertyKey)
    }

    fun togglePropertyKeyframe(propertyKey: String) {
        if (_uiState.value.activePass == null) return
        pushUndoSnapshot(HistoryEntryType.KEYFRAME_CHANGE, "تعديل نقطة حركة")
        _uiState.update { state ->
            val idx = state.selectedPassIndex
            if (idx !in state.passes.indices) return@update state
            val progress = state.previewProgress
            val catalogItem = state.activeCatalogItem
            val updated = state.passes.mapIndexed { i, pass ->
                if (i != idx) pass
                else {
                    val list = pass.keyframesForProperty(propertyKey)
                    val existing = list.minByOrNull { abs(it.time - progress) }?.takeIf { abs(it.time - progress) <= 0.03f }
                    val nextList = if (existing != null) {
                        list.filterNot { it.id == existing.id }
                    } else {
                        val spec = catalogItem.params.firstOrNull { it.key == propertyKey }
                        val newKf = if (spec?.paramType == EffectParamType.COLOR) {
                            val currentArgb = pass.evaluateColorAt(propertyKey, progress, spec.defaultColorArgb)
                            ParameterKeyframe(
                                id = "ckf_${UUID.randomUUID().toString().take(6)}",
                                time = progress,
                                value = 1f,
                                easing = state.defaultKeyframeEasing,
                                colorArgb = currentArgb
                            )
                        } else {
                            val currentVal = evaluatePropertyValue(pass, propertyKey, progress, catalogItem)
                            val prefix = if (propertyKey == PARAM_KEY_INTENSITY) "ekf" else "pkf"
                            ParameterKeyframe(
                                id = "${prefix}_${UUID.randomUUID().toString().take(6)}",
                                time = progress,
                                value = currentVal,
                                easing = state.defaultKeyframeEasing
                            )
                        }
                        (list + newKf).sortedBy { it.time }
                    }
                    applyPropertyKeyframesToPass(pass, propertyKey, nextList)
                }
            }
            state.copy(
                passes = updated,
                selectedKeyframePropertyKey = propertyKey
            ).withHistoryFlags()
        }
    }

    fun updatePropertyKeyframeEasing(propertyKey: String, easing: KeyframeEasing) {
        pushUndoSnapshot(HistoryEntryType.KEYFRAME_CHANGE, "تغيير منحنى الحركة")
        _uiState.update { state ->
            val idx = state.selectedPassIndex
            if (idx !in state.passes.indices) {
                return@update state.copy(
                    defaultKeyframeEasing = easing,
                    selectedKeyframePropertyKey = propertyKey
                )
            }
            val progress = state.previewProgress
            val updated = state.passes.mapIndexed { i, pass ->
                if (i != idx) pass
                else {
                    val list = pass.keyframesForProperty(propertyKey)
                    if (list.isEmpty()) {
                        pass
                    } else {
                        val target = list.minByOrNull { abs(it.time - progress) }?.takeIf { abs(it.time - progress) <= 0.03f }
                            ?: list.lastOrNull { it.time <= progress }
                            ?: list.first()
                        val nextList = list.map { kf ->
                            if (kf.id == target.id) {
                                kf.copy(
                                    easing = easing,
                                    customBezier = if (easing == KeyframeEasing.CUBIC_BEZIER) {
                                        kf.customBezier ?: easing.toBezierCurveSpec()
                                    } else kf.customBezier
                                )
                            } else kf
                        }
                        applyPropertyKeyframesToPass(pass, propertyKey, nextList)
                    }
                }
            }
            state.copy(
                passes = updated,
                selectedKeyframePropertyKey = propertyKey,
                defaultKeyframeEasing = easing
            ).withHistoryFlags()
        }
    }

    fun updatePropertyCustomBezier(propertyKey: String, curveSpec: BezierCurveSpec) {
        val activePass = _uiState.value.activePass ?: return
        historyHelper.recordCoalescedChange(
            coalesceKey = "bezier:${activePass.id}:$propertyKey",
            snapshotBefore = _uiState.value.toHistorySnapshot(
                entryType = HistoryEntryType.KEYFRAME_CHANGE,
                description = "تعديل منحنى بيزيه"
            )
        )
        _uiState.update { state ->
            val idx = state.selectedPassIndex
            if (idx !in state.passes.indices) return@update state
            val progress = state.previewProgress
            val updated = state.passes.mapIndexed { i, pass ->
                if (i != idx) pass
                else {
                    val list = pass.keyframesForProperty(propertyKey)
                    if (list.isEmpty()) pass
                    else {
                        val target = list.minByOrNull { abs(it.time - progress) }?.takeIf { abs(it.time - progress) <= 0.03f }
                            ?: list.lastOrNull { it.time <= progress }
                            ?: list.first()
                        val nextList = list.map { kf ->
                            if (kf.id == target.id) {
                                kf.copy(easing = KeyframeEasing.CUBIC_BEZIER, customBezier = curveSpec)
                            } else kf
                        }
                        applyPropertyKeyframesToPass(pass, propertyKey, nextList)
                    }
                }
            }
            state.copy(
                passes = updated,
                selectedKeyframePropertyKey = propertyKey,
                defaultKeyframeEasing = KeyframeEasing.CUBIC_BEZIER
            ).withHistoryFlags()
        }
    }

    fun clearPropertyKeyframes(propertyKey: String) {
        if (_uiState.value.activePass == null) return
        pushUndoSnapshot(HistoryEntryType.KEYFRAME_CHANGE, "مسح نقاط الحركة")
        _uiState.update { state ->
            val idx = state.selectedPassIndex
            if (idx !in state.passes.indices) return@update state
            val updated = state.passes.mapIndexed { i, pass ->
                if (i != idx) pass else applyPropertyKeyframesToPass(pass, propertyKey, emptyList())
            }
            state.copy(
                passes = updated,
                selectedKeyframePropertyKey = propertyKey
            ).withHistoryFlags()
        }
    }

    fun beginKeyframeDrag() {
        historyHelper.beginTransaction(
            transactionKey = "keyframe_drag",
            snapshotBefore = _uiState.value.toHistorySnapshot(
                entryType = HistoryEntryType.KEYFRAME_CHANGE,
                description = "تحريك نقطة حركة"
            )
        )
        _uiState.update { it.withHistoryFlags() }
    }

    fun endKeyframeDrag() {
        historyHelper.commitTransaction(_uiState.value.toHistorySnapshot())
        _uiState.update { it.withHistoryFlags() }
    }

    fun moveKeyframeTime(
        propertyKey: String,
        keyframeId: String,
        newTime: Float,
        seekToMovedTime: Boolean = true
    ) {
        val clampedTime = newTime.coerceIn(0f, 1f)
        _uiState.update { state ->
            val idx = state.selectedPassIndex
            if (idx !in state.passes.indices) return@update state
            val updated = state.passes.mapIndexed { i, pass ->
                if (i != idx) pass
                else {
                    val list = pass.keyframesForProperty(propertyKey)
                    if (list.none { it.id == keyframeId }) pass
                    else {
                        val nextList = list.map { kf ->
                            if (kf.id == keyframeId) kf.copy(time = clampedTime) else kf
                        }.sortedBy { it.time }
                        applyPropertyKeyframesToPass(pass, propertyKey, nextList)
                    }
                }
            }
            val nextLocalMs = if (seekToMovedTime) {
                (clampedTime * state.totalDurationMs).toLong().coerceIn(0L, state.totalDurationMs)
            } else {
                state.previewLocalMs
            }
            state.copy(
                passes = updated,
                selectedKeyframePropertyKey = propertyKey,
                isPlaying = if (seekToMovedTime) false else state.isPlaying,
                previewLocalMs = nextLocalMs
            )
        }
    }

    fun deleteKeyframe(propertyKey: String, keyframeId: String) {
        if (_uiState.value.activePass == null) return
        pushUndoSnapshot(HistoryEntryType.KEYFRAME_CHANGE, "حذف نقطة حركة")
        _uiState.update { state ->
            val idx = state.selectedPassIndex
            if (idx !in state.passes.indices) return@update state
            val updated = state.passes.mapIndexed { i, pass ->
                if (i != idx) pass
                else {
                    val list = pass.keyframesForProperty(propertyKey)
                    val nextList = list.filterNot { it.id == keyframeId }
                    applyPropertyKeyframesToPass(pass, propertyKey, nextList)
                }
            }
            state.copy(
                passes = updated,
                selectedKeyframePropertyKey = propertyKey
            ).withHistoryFlags()
        }
    }

    fun selectAndSeekKeyframe(propertyKey: String, timeProgress: Float) {
        _uiState.update { state ->
            val clamped = timeProgress.coerceIn(0f, 1f)
            val targetMs = (clamped * state.totalDurationMs).toLong().coerceIn(0L, state.totalDurationMs)
            state.copy(
                isPlaying = false,
                previewLocalMs = targetMs,
                selectedKeyframePropertyKey = propertyKey
            )
        }
    }

    fun jumpToPrevKeyframe(propertyKey: String) {
        _uiState.update { state ->
            val kfs = state.keyframesFor(propertyKey)
            if (kfs.isEmpty()) return@update state
            val target = kfs.lastOrNull { it.time < state.previewProgress - 0.008f } ?: kfs.first()
            val targetMs = (target.time.coerceIn(0f, 1f) * state.totalDurationMs).toLong()
            state.copy(
                isPlaying = false,
                previewLocalMs = targetMs,
                selectedKeyframePropertyKey = propertyKey
            )
        }
    }

    fun jumpToNextKeyframe(propertyKey: String) {
        _uiState.update { state ->
            val kfs = state.keyframesFor(propertyKey)
            if (kfs.isEmpty()) return@update state
            val target = kfs.firstOrNull { it.time > state.previewProgress + 0.008f } ?: kfs.last()
            val targetMs = (target.time.coerceIn(0f, 1f) * state.totalDurationMs).toLong()
            state.copy(
                isPlaying = false,
                previewLocalMs = targetMs,
                selectedKeyframePropertyKey = propertyKey
            )
        }
    }

    private fun evaluatePropertyValue(
        pass: ShaderEffectPass,
        propertyKey: String,
        progress: Float,
        catalogItem: EffectCatalogItem
    ): Float = when (propertyKey) {
        PARAM_KEY_INTENSITY -> pass.evaluateIntensityAt(progress)
        PARAM_KEY_OPACITY -> pass.evaluateOpacityAt(progress)
        else -> {
            val spec = catalogItem.params.firstOrNull { it.key == propertyKey }
            pass.evaluateParamAt(
                key = propertyKey,
                progress = progress,
                fallback = spec?.defaultValue ?: 0.5f,
                minValue = spec?.minValue ?: -Float.MAX_VALUE,
                maxValue = spec?.maxValue ?: Float.MAX_VALUE
            )
        }
    }

    private fun applyPropertyKeyframesToPass(
        pass: ShaderEffectPass,
        propertyKey: String,
        nextList: List<ParameterKeyframe>
    ): ShaderEffectPass {
        val sorted = nextList.sortedBy { it.time }
        return if (propertyKey == PARAM_KEY_INTENSITY) {
            val legacyList = sorted.map { pkf ->
                EffectKeyframe(
                    id = pkf.id,
                    timeProgress = pkf.time,
                    intensity = pkf.value,
                    easing = pkf.easing
                )
            }
            val updatedMap = if (sorted.isEmpty()) {
                pass.paramKeyframes - PARAM_KEY_INTENSITY
            } else {
                pass.paramKeyframes + (PARAM_KEY_INTENSITY to sorted)
            }
            pass.copy(keyframes = legacyList, paramKeyframes = updatedMap)
        } else {
            val updatedMap = if (sorted.isEmpty()) {
                pass.paramKeyframes - propertyKey
            } else {
                pass.paramKeyframes + (propertyKey to sorted)
            }
            pass.copy(paramKeyframes = updatedMap)
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
        if (_uiState.value.passes.isNotEmpty()) {
            pushUndoSnapshot(HistoryEntryType.EFFECT_STACK_CHANGE, "مسح كل المؤثرات")
        }
        _uiState.update {
            it.copy(
                passes = emptyList(),
                selectedPassIndex = 0,
                panelLevel = StudioPanelLevel.STACK
            ).withHistoryFlags()
        }
    }

    fun createAndSave(onDone: () -> Unit) {
        val state = _uiState.value
        val currentProject = VideoStarRepository.getProjectById(projectId)
        if (currentProject != null && state.layerOrderIds.isNotEmpty()) {
            val existingIds = currentProject.layers.map { it.id }
            if (existingIds != state.layerOrderIds) {
                val byId = currentProject.layers.associateBy { it.id }
                val reordered = state.layerOrderIds.mapNotNull { byId[it] } +
                    currentProject.layers.filter { it.id !in state.layerOrderIds }
                VideoStarRepository.syncProjectLayers(projectId, reordered)
            }
        }
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
