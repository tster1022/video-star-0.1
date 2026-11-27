package com.example.data

import android.content.Context
import com.example.model.AeLayerKind
import com.example.model.AspectRatioMode
import com.example.model.BezierCurveSpec
import com.example.model.CompressionQuality
import com.example.model.EditorLayer
import com.example.model.LayerKeyframe
import com.example.model.MediaClip
import com.example.model.ProjectAudioConfig
import com.example.model.SceneEffect
import com.example.model.SceneMotion
import com.example.model.SceneTiming
import com.example.model.SceneTransform
import com.example.model.SceneTransition
import com.example.model.ShaderEffectPass
import com.example.model.StudioLayoutMode
import com.example.model.TextLayerSpec
import com.example.model.TimelineScene
import com.example.model.VelocityPoint
import com.example.model.VideoProject
import com.example.model.VideoTrimAdjustment
import com.example.model.computeAverageVelocityMultiplier
import com.example.model.defaultLayers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

object VideoStarRepository {

    private val _projects = MutableStateFlow<List<VideoProject>>(emptyList())
    val projects: StateFlow<List<VideoProject>> = _projects.asStateFlow()

    private val _preferredLayout = MutableStateFlow(StudioLayoutMode.NOVA)
    val preferredLayout: StateFlow<StudioLayoutMode> = _preferredLayout.asStateFlow()

    private val _favoriteAeEffectIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteAeEffectIds: StateFlow<Set<String>> = _favoriteAeEffectIds.asStateFlow()

    private const val PREFS_NAME = "videostar_ae_prefs"
    private const val KEY_FAVORITE_AE_EFFECTS = "favorite_ae_effects"

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveDirty = java.util.concurrent.atomic.AtomicBoolean(false)
    private val saveInFlight = java.util.concurrent.atomic.AtomicBoolean(false)
    private val saveLock = Any()
    private var appContext: Context? = null
    private var isInitialized = false

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (isInitialized) return
        isInitialized = true
        val saved = JsonProjectStore.loadProjects(context.applicationContext)
        if (saved.isNotEmpty()) {
            _projects.value = saved.map { normalizeProject(it) }
        }
        runCatching {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val favs = prefs.getStringSet(KEY_FAVORITE_AE_EFFECTS, emptySet()).orEmpty().toSet()
            _favoriteAeEffectIds.value = favs
        }
    }

    fun toggleFavoriteAeEffect(effectId: String): Boolean {
        var added = false
        _favoriteAeEffectIds.update { current ->
            if (effectId in current) {
                added = false
                current - effectId
            } else {
                added = true
                current + effectId
            }
        }
        val snapshot = _favoriteAeEffectIds.value
        val ctx = appContext
        if (ctx != null) {
            ioScope.launch {
                runCatching {
                    ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putStringSet(KEY_FAVORITE_AE_EFFECTS, snapshot)
                        .apply()
                }
            }
        }
        return added
    }

    fun saveNow() {
        val ctx = appContext ?: return
        saveDirty.set(false)
        val snapshot = _projects.value
        synchronized(saveLock) {
            JsonProjectStore.saveProjects(ctx, snapshot)
        }
    }

    private fun persistAsync() {
        val ctx = appContext ?: return
        saveDirty.set(true)
        if (!saveInFlight.compareAndSet(false, true)) return
        ioScope.launch {
            try {
                while (saveDirty.compareAndSet(true, false)) {
                    val snapshot = _projects.value
                    synchronized(saveLock) {
                        JsonProjectStore.saveProjects(ctx, snapshot)
                    }
                    if (saveDirty.get()) {
                        kotlinx.coroutines.delay(120L)
                    }
                }
            } finally {
                saveInFlight.set(false)
                if (saveDirty.get()) {
                    persistAsync()
                }
            }
        }
    }

    private fun mutateProjects(transform: (List<VideoProject>) -> List<VideoProject>) {
        _projects.update { current ->
            transform(current).map { normalizeProject(it) }
        }
        persistAsync()
    }

    private fun normalizeProject(project: VideoProject): VideoProject {
        val reindexedScenes = project.scenes.mapIndexed { idx, scene ->
            scene.copy(title = "Scene ${idx + 1}")
        }
        val derivedClips = reindexedScenes.mapNotNull { it.clip }.ifEmpty { project.clips }
        val totalDuration = if (reindexedScenes.isNotEmpty()) {
            reindexedScenes.sumOf { it.effectiveDurationMs }.coerceAtLeast(500L)
        } else {
            project.durationMs
        }
        return project.copy(
            scenes = reindexedScenes,
            clips = derivedClips,
            clipCount = reindexedScenes.size.coerceAtLeast(derivedClips.size),
            durationMs = totalDuration,
            activeAudioTitle = project.audioConfig.songTitle
        )
    }

    fun getProjectById(projectId: String): VideoProject? =
        _projects.value.firstOrNull { it.id == projectId }

    fun createProject(
        title: String,
        aspectRatio: AspectRatioMode,
        frameRate: Int = 30,
        resolution: String = "1080p",
        audioTitle: String = "إيقاع إلكتروني 128 BPM",
        compressionQuality: CompressionQuality = CompressionQuality.BALANCED,
        initialDurationMs: Long = 0L,
        audioUriString: String? = null
    ): VideoProject {
        val cleanTitle = title.trim().ifEmpty { "مشروع جديد ${_projects.value.size + 1}" }
        val created = VideoProject(
            id = "proj_${UUID.randomUUID().toString().take(6)}",
            title = cleanTitle,
            aspectRatio = aspectRatio,
            frameRate = frameRate,
            resolution = resolution,
            compressionQuality = compressionQuality,
            durationMs = initialDurationMs.coerceAtLeast(0L),
            isFavorite = false,
            clips = emptyList(),
            clipCount = 0,
            activeAudioTitle = audioTitle,
            audioConfig = ProjectAudioConfig(
                songTitle = audioTitle,
                songUriString = audioUriString
            ),
            layers = defaultLayers(),
            scenes = emptyList(),
            studioLayout = _preferredLayout.value
        )
        mutateProjects { listOf(created) + it }
        return created
    }

    fun updateProject(projectId: String, newTitle: String, newRatio: AspectRatioMode) {
        val cleanTitle = newTitle.trim()
        mutateProjects { list ->
            list.map { project ->
                if (project.id == projectId) {
                    project.copy(
                        title = cleanTitle.ifEmpty { project.title },
                        aspectRatio = newRatio
                    )
                } else {
                    project
                }
            }
        }
    }

    fun toggleFavorite(projectId: String) {
        mutateProjects { list ->
            list.map { project ->
                if (project.id == projectId) project.copy(isFavorite = !project.isFavorite) else project
            }
        }
    }

    fun updatePreferredLayout(layout: StudioLayoutMode, projectId: String? = null) {
        _preferredLayout.value = layout
        if (projectId != null) {
            updateStudioLayout(projectId, layout)
        }
    }

    fun updateStudioLayout(projectId: String, layout: StudioLayoutMode) {
        _preferredLayout.value = layout
        mutateProjects { list ->
            list.map { project ->
                if (project.id == projectId) project.copy(studioLayout = layout) else project
            }
        }
    }

    fun deleteProject(projectId: String) {
        mutateProjects { list -> list.filterNot { it.id == projectId } }
    }

    fun addOrUpdateVideoClip(
        projectId: String,
        clip: MediaClip.Video,
        targetLayerIndex: Int? = null,
        replaceSceneIndex: Int? = null
    ) {
        addOrUpdateMediaClip(projectId, clip, targetLayerIndex, replaceSceneIndex)
    }

    fun addOrUpdatePhotoClip(
        projectId: String,
        clip: MediaClip.Photo,
        targetLayerIndex: Int? = null,
        replaceSceneIndex: Int? = null
    ) {
        addOrUpdateMediaClip(projectId, clip, targetLayerIndex, replaceSceneIndex)
    }

    private fun addOrUpdateMediaClip(
        projectId: String,
        clip: MediaClip,
        targetLayerIndex: Int?,
        replaceSceneIndex: Int?
    ) {
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project

                if (targetLayerIndex != null) {
                    val updatedLayers = if (targetLayerIndex in project.layers.indices) {
                        project.layers.mapIndexed { idx, layer ->
                            if (idx == targetLayerIndex) {
                                layer.copy(mediaClip = clip, backgroundColorArgb = null)
                            } else layer
                        }
                    } else {
                        val nextNum = (project.layers.maxOfOrNull { it.number } ?: 0) + 1
                        project.layers + EditorLayer(
                            id = "layer_$nextNum",
                            number = nextNum,
                            mediaClip = clip,
                            backgroundColorArgb = null
                        )
                    }
                    val updatedDuration = project.durationMs.coerceAtLeast(clip.effectiveDurationMs)
                    project.copy(
                        layers = updatedLayers,
                        durationMs = updatedDuration,
                        clipCount = project.clips.size.coerceAtLeast(
                            updatedLayers.count { it.mediaClip != null || it.backgroundColorArgb != null }
                        )
                    )
                } else if (replaceSceneIndex != null && replaceSceneIndex in project.scenes.indices) {
                    // Edit > Replace: preserve existing transform, effect, motion, velocity, and timing!
                    val updatedScenes = project.scenes.mapIndexed { idx, existing ->
                        if (idx == replaceSceneIndex) {
                            val newDur = clip.effectiveDurationMs
                            existing.copy(
                                clipId = clip.id,
                                mediaUriString = clip.uriString,
                                isVideo = clip is MediaClip.Video,
                                clip = clip,
                                colorGrading = clip.colorGrading,
                                durationMs = newDur,
                                timing = existing.timing.copy(durationMs = newDur)
                            )
                        } else existing
                    }
                    project.copy(scenes = updatedScenes)
                } else {
                    val initialTransform = SceneTransform(
                        scale = clip.scale,
                        rotation = clip.rotation,
                        offsetX = clip.offsetX,
                        offsetY = clip.offsetY,
                        flipH = clip.isFlippedHorizontal,
                        flipV = clip.isFlippedVertical,
                        bgColorArgb = clip.backgroundColorArgb,
                        isFitMode = clip.isFitMode
                    )
                    val newScene = TimelineScene(
                        id = "scene_${UUID.randomUUID().toString().take(8)}",
                        title = "Scene ${project.scenes.size + 1}",
                        durationMs = clip.effectiveDurationMs,
                        clipId = clip.id,
                        mediaUriString = clip.uriString,
                        isVideo = clip is MediaClip.Video,
                        clip = clip,
                        transform = initialTransform,
                        colorGrading = clip.colorGrading,
                        timing = SceneTiming(durationMs = clip.effectiveDurationMs)
                    )
                    val updatedScenes = project.scenes + newScene
                    val updatedLayers = project.layers.mapIndexed { idx, layer ->
                        if (idx == 0 && layer.mediaClip == null) layer.copy(mediaClip = clip) else layer
                    }
                    val updatedAeLayers = if (project.studioLayout == StudioLayoutMode.AFTER_EFFECTS) {
                        val nextNum = (project.aeLayers.maxOfOrNull { it.number } ?: 0) + 1
                        val stackedLayer = EditorLayer(
                            id = "ae_layer_${UUID.randomUUID().toString().take(8)}",
                            number = nextNum,
                            mediaClip = clip,
                            backgroundColorArgb = null,
                            offsetX = clip.offsetX,
                            offsetY = clip.offsetY,
                            scale = clip.scale,
                            rotation = clip.rotation,
                            isMirrored = clip.isFlippedHorizontal,
                            isFlippedVertical = clip.isFlippedVertical,
                            title = if (clip is MediaClip.Photo) "Imagem" else "Video",
                            layerKind = AeLayerKind.MEDIA,
                            isVisible = true,
                            isLocked = false,
                            startTimeMs = 0L,
                            durationMs = clip.effectiveDurationMs,
                            sourceSceneId = newScene.id
                        )
                        reindexZOrder(listOf(stackedLayer) + project.aeLayers)
                    } else {
                        project.aeLayers
                    }
                    val trimFromClip = VideoTrimAdjustment(
                        startMs = if (clip is MediaClip.Video) clip.startTrimMs else 0L,
                        endMs = if (clip is MediaClip.Video) clip.endTrimMs else clip.effectiveDurationMs,
                        offsetX = clip.offsetX,
                        offsetY = clip.offsetY,
                        scale = clip.scale,
                        rotation = clip.rotation,
                        isFitMode = clip.isFitMode,
                        backgroundColorArgb = clip.backgroundColorArgb,
                        isFlippedHorizontal = clip.isFlippedHorizontal,
                        isFlippedVertical = clip.isFlippedVertical
                    )
                    project.copy(
                        scenes = updatedScenes,
                        layers = updatedLayers,
                        aeLayers = updatedAeLayers,
                        trimAdjustment = trimFromClip
                    )
                }
            }
        }
    }

    fun duplicateScene(projectId: String, sceneIndex: Int) {
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                if (sceneIndex !in project.scenes.indices) return@map project
                val original = project.scenes[sceneIndex]
                val newClipId = "clip_${UUID.randomUUID().toString().take(6)}"
                val copiedClip = when (val c = original.clip) {
                    is MediaClip.Video -> c.copy(id = newClipId)
                    is MediaClip.Photo -> c.copy(id = newClipId)
                    null -> null
                }
                val clone = original.copy(
                    id = "scene_${UUID.randomUUID().toString().take(8)}",
                    clipId = copiedClip?.id ?: original.clipId,
                    clip = copiedClip,
                    transitionToNext = null
                )
                val mutable = project.scenes.toMutableList()
                mutable.add(sceneIndex + 1, clone)
                project.copy(scenes = mutable)
            }
        }
    }

    fun duplicateActiveClipInProject(projectId: String, extraDurationMs: Long = 1000L) {
        val project = getProjectById(projectId) ?: return
        if (project.scenes.isNotEmpty()) {
            duplicateScene(projectId, 0)
            return
        }
        mutateProjects { list ->
            list.map { p ->
                if (p.id != projectId) return@map p
                val newDur = (p.durationMs + extraDurationMs).coerceAtMost(60_000L)
                p.copy(clipCount = p.clipCount + 1, durationMs = newDur)
            }
        }
    }

    fun splitProjectScene(projectId: String, splitTimeMs: Long) {
        splitSceneAt(projectId = projectId, sceneIndex = 0, localSplitMs = splitTimeMs)
    }

    fun splitSceneAt(projectId: String, sceneIndex: Int, localSplitMs: Long) {
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                if (project.scenes.isEmpty()) {
                    val baseDur = project.durationMs.coerceAtLeast(1000L)
                    val safeSplit = localSplitMs.coerceIn(300L, (baseDur - 300L).coerceAtLeast(300L))
                    val secondDur = (baseDur - safeSplit).coerceAtLeast(300L)
                    val baseClip = project.primaryClip
                    val baseScenes = listOf(
                        TimelineScene(
                            id = "scene_${UUID.randomUUID().toString().take(8)}",
                            title = "Scene 1",
                            durationMs = safeSplit,
                            clipId = baseClip?.id,
                            mediaUriString = baseClip?.uriString,
                            isVideo = baseClip !is MediaClip.Photo,
                            clip = baseClip,
                            timing = SceneTiming(durationMs = safeSplit)
                        ),
                        TimelineScene(
                            id = "scene_${UUID.randomUUID().toString().take(8)}",
                            title = "Scene 2",
                            durationMs = secondDur,
                            clipId = baseClip?.id,
                            mediaUriString = baseClip?.uriString,
                            isVideo = baseClip !is MediaClip.Photo,
                            clip = baseClip,
                            timing = SceneTiming(durationMs = secondDur)
                        )
                    )
                    return@map project.copy(scenes = baseScenes)
                }

                val targetIdx = sceneIndex.coerceIn(0, project.scenes.lastIndex)
                val target = project.scenes[targetIdx]
                val totalDur = target.effectiveDurationMs.coerceAtLeast(600L)
                val safeSplit = localSplitMs.coerceIn(250L, (totalDur - 250L).coerceAtLeast(250L))
                val secondDur = (totalDur - safeSplit).coerceAtLeast(250L)

                val (firstClip, secondClip) = when (val c = target.clip) {
                    is MediaClip.Video -> {
                        val ratio = safeSplit.toFloat() / totalDur.toFloat()
                        val trimSpan = (c.endTrimMs - c.startTrimMs).coerceAtLeast(400L)
                        val splitPoint = (c.startTrimMs + (trimSpan * ratio).toLong())
                            .coerceIn(c.startTrimMs + 200L, c.endTrimMs - 200L)
                        val c1 = c.copy(id = "vid_${UUID.randomUUID().toString().take(6)}", endTrimMs = splitPoint)
                        val c2 = c.copy(id = "vid_${UUID.randomUUID().toString().take(6)}", startTrimMs = splitPoint)
                        c1 to c2
                    }
                    is MediaClip.Photo -> {
                        val c1 = c.copy(id = "img_${UUID.randomUUID().toString().take(6)}", durationMs = safeSplit)
                        val c2 = c.copy(id = "img_${UUID.randomUUID().toString().take(6)}", durationMs = secondDur)
                        c1 to c2
                    }
                    null -> null to null
                }

                val firstScene = target.copy(
                    id = "scene_${UUID.randomUUID().toString().take(8)}",
                    durationMs = safeSplit,
                    clipId = firstClip?.id ?: target.clipId,
                    clip = firstClip,
                    timing = target.timing.copy(durationMs = safeSplit, freezeFrameEnabled = false),
                    transitionToNext = null
                )
                val secondScene = target.copy(
                    id = "scene_${UUID.randomUUID().toString().take(8)}",
                    durationMs = secondDur,
                    clipId = secondClip?.id ?: target.clipId,
                    clip = secondClip,
                    timing = target.timing.copy(durationMs = secondDur, freezeFrameEnabled = false)
                )

                val mutable = project.scenes.toMutableList()
                mutable.removeAt(targetIdx)
                mutable.add(targetIdx, secondScene)
                mutable.add(targetIdx, firstScene)
                project.copy(scenes = mutable)
            }
        }
    }

    fun mergeSceneWithNext(projectId: String, sceneIndex: Int): Boolean {
        val proj = getProjectById(projectId) ?: return false
        if (sceneIndex !in 0 until proj.scenes.lastIndex) return false
        mergeScenesByIndices(projectId, setOf(sceneIndex, sceneIndex + 1))
        return true
    }

    fun mergeScenesByIndices(projectId: String, indices: Set<Int>): Boolean {
        val proj = getProjectById(projectId) ?: return false
        val sorted = indices.filter { it in proj.scenes.indices }.sorted()
        if (sorted.size < 2) return false
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                val firstIdx = sorted.first()
                val firstScene = project.scenes[firstIdx]
                val combinedDuration = sorted.sumOf { project.scenes[it].effectiveDurationMs }.coerceAtLeast(500L)
                val updatedFirstClip = when (val c = firstScene.clip) {
                    is MediaClip.Video -> c.copy(
                        endTrimMs = (c.startTrimMs + combinedDuration).coerceAtMost(c.durationMs)
                    )
                    is MediaClip.Photo -> c.copy(durationMs = combinedDuration)
                    null -> null
                }
                val merged = firstScene.copy(
                    durationMs = combinedDuration,
                    clip = updatedFirstClip,
                    timing = firstScene.timing.copy(durationMs = combinedDuration),
                    transitionToNext = project.scenes[sorted.last()].transitionToNext
                )
                val toRemove = sorted.drop(1).toSet()
                val remaining = project.scenes.mapIndexedNotNull { idx, scene ->
                    when {
                        idx == firstIdx -> merged
                        idx in toRemove -> null
                        else -> scene
                    }
                }
                project.copy(scenes = remaining)
            }
        }
        return true
    }

    fun toggleReverseScene(projectId: String, sceneIndex: Int) {
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                if (sceneIndex !in project.scenes.indices) return@map project
                project.copy(
                    scenes = project.scenes.mapIndexed { idx, scene ->
                        if (idx == sceneIndex) scene.copy(isReversed = !scene.isReversed) else scene
                    }
                )
            }
        }
    }

    fun trimScene(projectId: String, sceneIndex: Int, startTrimMs: Long, endTrimMs: Long) {
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                if (sceneIndex !in project.scenes.indices) return@map project
                project.copy(
                    scenes = project.scenes.mapIndexed { idx, scene ->
                        if (idx == sceneIndex) {
                            when (val c = scene.clip) {
                                is MediaClip.Video -> {
                                    val safeStart = startTrimMs.coerceIn(0L, (c.durationMs - 200L).coerceAtLeast(0L))
                                    val safeEnd = endTrimMs.coerceIn((safeStart + 200L).coerceAtMost(c.durationMs), c.durationMs)
                                    val newClip = c.copy(startTrimMs = safeStart, endTrimMs = safeEnd)
                                    scene.copy(
                                        clip = newClip,
                                        durationMs = newClip.trimmedDurationMs,
                                        timing = scene.timing.copy(durationMs = newClip.trimmedDurationMs)
                                    )
                                }
                                is MediaClip.Photo -> {
                                    val newDur = (endTrimMs - startTrimMs).coerceIn(500L, 30_000L)
                                    val newClip = c.copy(durationMs = newDur)
                                    scene.copy(
                                        clip = newClip,
                                        durationMs = newDur,
                                        timing = scene.timing.copy(durationMs = newDur)
                                    )
                                }
                                null -> {
                                    val newDur = (endTrimMs - startTrimMs).coerceIn(500L, 30_000L)
                                    scene.copy(
                                        durationMs = newDur,
                                        timing = scene.timing.copy(durationMs = newDur)
                                    )
                                }
                            }
                        } else scene
                    }
                )
            }
        }
    }

    fun deleteSceneAt(projectId: String, sceneIndex: Int) {
        deleteScenesByIndices(projectId, setOf(sceneIndex))
    }

    fun deleteScenesByIndices(projectId: String, indices: Set<Int>) {
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                val remaining = project.scenes.filterIndexed { idx, _ -> idx !in indices }
                project.copy(scenes = remaining)
            }
        }
    }

    fun updateProjectAudio(projectId: String, audioTitle: String) {
        mutateProjects { list ->
            list.map { project ->
                if (project.id == projectId) {
                    project.copy(
                        activeAudioTitle = audioTitle,
                        audioConfig = project.audioConfig.copy(songTitle = audioTitle)
                    )
                } else project
            }
        }
    }

    fun updateAudioConfig(
        projectId: String,
        audioConfig: ProjectAudioConfig,
        durationMs: Long? = null
    ) {
        mutateProjects { list ->
            list.map { project ->
                if (project.id == projectId) {
                    val updatedDuration = if (project.scenes.isEmpty() && durationMs != null && durationMs > 0L) {
                        durationMs.coerceAtLeast(500L)
                    } else {
                        project.durationMs
                    }
                    project.copy(
                        activeAudioTitle = audioConfig.songTitle,
                        audioConfig = audioConfig,
                        durationMs = updatedDuration
                    )
                } else project
            }
        }
    }

    fun saveProjectLayers(
        projectId: String,
        layers: List<EditorLayer>,
        targetSceneIndex: Int? = null,
        createNewSceneIfEmpty: Boolean = true
    ) {
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                val hasActiveContent = layers.any {
                    it.mediaClip != null ||
                        it.backgroundColorArgb != null ||
                        it.textOverlay.isNotBlank() ||
                        it.effectPasses.isNotEmpty()
                }
                if (!hasActiveContent) {
                    return@map project.copy(layers = layers)
                }
                val baseClip = layers.firstNotNullOfOrNull { it.mediaClip }
                val compDuration = baseClip?.effectiveDurationMs ?: 3_000L
                val resolvedIdx = when {
                    targetSceneIndex != null && targetSceneIndex in project.scenes.indices -> targetSceneIndex
                    project.scenes.isNotEmpty() -> {
                        project.scenes.indexOfFirst { it.isMultiLayerComposite }.takeIf { it >= 0 } ?: 0
                    }
                    else -> null
                }

                var updatedSceneId: String? = null
                var updatedScenePasses: List<ShaderEffectPass>? = null
                val updatedScenes = when {
                    resolvedIdx != null -> {
                        project.scenes.mapIndexed { idx, scene ->
                            if (idx == resolvedIdx) {
                                updatedSceneId = scene.id
                                val primaryLayerPasses = layers
                                    .firstOrNull { it.mediaClip != null || it.effectPasses.isNotEmpty() }
                                    ?.effectPasses
                                    ?.filter { it.effectId != "none" }
                                    .orEmpty()
                                val mergedPasses = when {
                                    primaryLayerPasses.isNotEmpty() -> primaryLayerPasses
                                    scene.isMultiLayerComposite -> emptyList()
                                    else -> scene.resolvedEffectPasses()
                                }
                                updatedScenePasses = mergedPasses
                                val primaryFx = mergedPasses.firstOrNull()?.let {
                                    SceneEffect(
                                        category = it.category,
                                        effectId = it.effectId,
                                        effectName = it.effectName,
                                        intensity = it.intensity
                                    )
                                } ?: SceneEffect()
                                val resolvedClip = scene.clip ?: baseClip
                                scene.copy(
                                    layers = layers,
                                    isMultiLayerComposite = true,
                                    clip = resolvedClip,
                                    mediaUriString = scene.mediaUriString ?: resolvedClip?.uriString,
                                    isVideo = resolvedClip is MediaClip.Video,
                                    effect = primaryFx,
                                    effectPasses = mergedPasses
                                )
                            } else scene
                        }
                    }
                    createNewSceneIfEmpty -> {
                        val mlScene = TimelineScene(
                            id = "scene_${UUID.randomUUID().toString().take(8)}",
                            title = "Scene 1",
                            durationMs = compDuration,
                            clipId = baseClip?.id,
                            mediaUriString = baseClip?.uriString,
                            isVideo = baseClip is MediaClip.Video,
                            clip = baseClip,
                            timing = SceneTiming(durationMs = compDuration),
                            layers = layers,
                            isMultiLayerComposite = true
                        )
                        updatedSceneId = mlScene.id
                        listOf(mlScene)
                    }
                    else -> project.scenes
                }

                val updatedAeLayers = if (updatedSceneId != null && updatedScenePasses != null) {
                    val passesToSync = updatedScenePasses!!
                    project.aeLayers.map { aeLayer ->
                        if (aeLayer.sourceSceneId == updatedSceneId) {
                            aeLayer.copy(effectPasses = passesToSync)
                        } else aeLayer
                    }
                } else project.aeLayers

                project.copy(
                    layers = layers,
                    scenes = updatedScenes,
                    aeLayers = updatedAeLayers
                )
            }
        }
    }

    fun syncProjectLayers(projectId: String, layers: List<EditorLayer>) {
        mutateProjects { list ->
            list.map { project ->
                if (project.id == projectId) project.copy(layers = layers) else project
            }
        }
    }

    fun updateLayerFromKeyframes(
        projectId: String,
        layerId: String,
        keyframes: List<LayerKeyframe>,
        offsetX: Float,
        offsetY: Float,
        scale: Float,
        rotation: Float,
        rotationX: Float = 0f,
        rotationY: Float = 0f,
        perspectiveFov: Float = 45f,
        is3DEnabled: Boolean? = null,
        anchorX: Float,
        anchorY: Float,
        isFlippedVertical: Boolean,
        layoutEffectName: String,
        effectIntensity: Float
    ) {
        mutateProjects { list ->
            list.map { project ->
                if (project.id == projectId) {
                    project.copy(
                        layers = project.layers.map { layer ->
                            if (layer.id == layerId) {
                                layer.copy(
                                    keyframes = keyframes,
                                    offsetX = offsetX,
                                    offsetY = offsetY,
                                    scale = scale,
                                    rotation = rotation,
                                    rotationX = rotationX,
                                    rotationY = rotationY,
                                    perspectiveFov = perspectiveFov,
                                    is3DEnabled = is3DEnabled ?: layer.is3DEnabled,
                                    anchorX = anchorX,
                                    anchorY = anchorY,
                                    isFlippedVertical = isFlippedVertical,
                                    layoutEffectName = layoutEffectName,
                                    effectIntensity = effectIntensity
                                )
                            } else layer
                        }
                    )
                } else project
            }
        }
    }

    var copiedGraphCurve: BezierCurveSpec? = null
        private set

    fun saveCopiedGraphCurve(curve: BezierCurveSpec) {
        copiedGraphCurve = curve
    }

    fun applyKeyframeGraph(
        projectId: String,
        layerId: String,
        keyframeIndex: Int,
        curve: BezierCurveSpec,
        applyToAll: Boolean
    ) {
        mutateProjects { list ->
            list.map { project ->
                if (project.id == projectId) {
                    project.copy(
                        layers = project.layers.map { layer ->
                            if (layer.id == layerId) {
                                val sorted = layer.keyframes.sortedBy { it.timeProgress }
                                val updatedKfs = sorted.mapIndexed { idx, kf ->
                                    if (applyToAll || idx == keyframeIndex) {
                                        kf.copy(curve = curve)
                                    } else kf
                                }
                                layer.copy(keyframes = updatedKfs)
                            } else layer
                        }
                    )
                } else project
            }
        }
    }

    fun applyVelocityCurve(
        projectId: String,
        points: List<VelocityPoint>,
        isSmooth: Boolean,
        sceneIndex: Int = 0
    ) {
        val sortedPoints = points.sortedBy { it.timeProgress }
        val avgSpeed = computeAverageVelocityMultiplier(sortedPoints, isSmooth)
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                val updatedScenes = project.scenes.mapIndexed { idx, scene ->
                    if (idx == sceneIndex) {
                        val baseRawDur = scene.clip?.effectiveDurationMs ?: scene.durationMs
                        val adjustedDur = (baseRawDur / avgSpeed).toLong().coerceIn(300L, 60_000L)
                        scene.copy(
                            velocityPoints = sortedPoints,
                            isVelocitySmooth = isSmooth,
                            durationMs = adjustedDur,
                            timing = scene.timing.copy(durationMs = adjustedDur)
                        )
                    } else scene
                }
                project.copy(
                    velocityPoints = sortedPoints,
                    isVelocitySmooth = isSmooth,
                    scenes = updatedScenes
                )
            }
        }
    }

    fun applyQuickTransform(
        projectId: String,
        offsetX: Float,
        offsetY: Float,
        scale: Float,
        rotation: Float,
        flipH: Boolean,
        flipV: Boolean,
        backgroundColorArgb: Long,
        mirrorEdges: Boolean = false,
        sceneIndex: Int = 0,
        endOffsetX: Float = offsetX,
        endOffsetY: Float = offsetY,
        endScale: Float = scale,
        endRotation: Float = rotation,
        isAnimated: Boolean = false,
        easing: String = "Smooth"
    ) {
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                val existing = project.trimAdjustment ?: VideoTrimAdjustment(
                    startMs = 0L,
                    endMs = project.durationMs.coerceAtLeast(1000L)
                )
                val targetSceneTransform = SceneTransform(
                    scale = scale,
                    rotation = rotation,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    flipH = flipH,
                    flipV = flipV,
                    bgColorArgb = backgroundColorArgb,
                    mirrorEdges = mirrorEdges,
                    endScale = endScale,
                    endRotation = endRotation,
                    endOffsetX = endOffsetX,
                    endOffsetY = endOffsetY,
                    isAnimated = isAnimated,
                    easing = easing
                )
                val updatedScenes = project.scenes.mapIndexed { idx, scene ->
                    if (idx == sceneIndex) {
                        val updatedClip = when (val c = scene.clip) {
                            is MediaClip.Video -> c.copy(
                                offsetX = offsetX,
                                offsetY = offsetY,
                                scale = scale,
                                rotation = rotation,
                                isFlippedHorizontal = flipH,
                                isFlippedVertical = flipV,
                                backgroundColorArgb = backgroundColorArgb
                            )
                            is MediaClip.Photo -> c.copy(
                                offsetX = offsetX,
                                offsetY = offsetY,
                                scale = scale,
                                rotation = rotation,
                                isFlippedHorizontal = flipH,
                                isFlippedVertical = flipV,
                                backgroundColorArgb = backgroundColorArgb
                            )
                            null -> null
                        }
                        scene.copy(
                            clip = updatedClip,
                            transform = targetSceneTransform.copy(isFitMode = scene.transform.isFitMode)
                        )
                    } else scene
                }
                project.copy(
                    scenes = updatedScenes,
                    trimAdjustment = existing.copy(
                        offsetX = offsetX,
                        offsetY = offsetY,
                        scale = scale,
                        rotation = rotation,
                        isFlippedHorizontal = flipH,
                        isFlippedVertical = flipV,
                        backgroundColorArgb = backgroundColorArgb
                    )
                )
            }
        }
    }

    fun applySceneEffect(projectId: String, sceneIndices: Set<Int>, effect: SceneEffect) {
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                val passList = if (effect.effectId != "none" && effect.intensity > 0.005f) {
                    val item = com.example.model.EffectCatalog.findById(effect.effectId)
                    listOf(
                        ShaderEffectPass(
                            id = "pass_${effect.effectId}",
                            effectId = com.example.model.EffectCatalog.resolveCanonicalId(effect.effectId),
                            effectName = effect.effectName,
                            category = effect.category,
                            intensity = effect.intensity,
                            params = item?.defaultParamMap() ?: emptyMap()
                        )
                    )
                } else emptyList()
                val targetSceneIds = project.scenes
                    .filterIndexed { idx, _ -> idx in sceneIndices }
                    .map { it.id }
                    .toSet()
                val updatedScenes = project.scenes.mapIndexed { idx, scene ->
                    if (idx in sceneIndices) {
                        val updatedSceneLayers = if (scene.isMultiLayerComposite && scene.layers.isNotEmpty()) {
                            scene.layers.mapIndexed { lIdx, layer ->
                                if (lIdx == 0) layer.copy(effectPasses = passList) else layer
                            }
                        } else scene.layers
                        scene.copy(effect = effect, effectPasses = passList, layers = updatedSceneLayers)
                    } else scene
                }
                val updatedAeLayers = project.aeLayers.map { aeLayer ->
                    if (aeLayer.sourceSceneId != null && aeLayer.sourceSceneId in targetSceneIds) {
                        aeLayer.copy(effectPasses = passList)
                    } else aeLayer
                }
                project.copy(scenes = updatedScenes, aeLayers = updatedAeLayers)
            }
        }
    }

    fun applySceneEffectPasses(projectId: String, sceneIndices: Set<Int>, passes: List<ShaderEffectPass>) {
        val active = passes.filter { it.effectId != "none" }
        val primary = active.firstOrNull()?.let {
            SceneEffect(
                category = it.category,
                effectId = it.effectId,
                effectName = it.effectName,
                intensity = it.intensity
            )
        } ?: SceneEffect()
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                val targetSceneIds = project.scenes
                    .filterIndexed { idx, _ -> idx in sceneIndices }
                    .map { it.id }
                    .toSet()
                val updatedScenes = project.scenes.mapIndexed { idx, scene ->
                    if (idx in sceneIndices) {
                        val updatedSceneLayers = if (scene.isMultiLayerComposite && scene.layers.isNotEmpty()) {
                            scene.layers.mapIndexed { lIdx, layer ->
                                if (lIdx == 0) layer.copy(effectPasses = active) else layer
                            }
                        } else scene.layers
                        scene.copy(effect = primary, effectPasses = active, layers = updatedSceneLayers)
                    } else scene
                }
                val updatedAeLayers = project.aeLayers.map { aeLayer ->
                    if (aeLayer.sourceSceneId != null && aeLayer.sourceSceneId in targetSceneIds) {
                        aeLayer.copy(effectPasses = active)
                    } else aeLayer
                }
                project.copy(scenes = updatedScenes, aeLayers = updatedAeLayers)
            }
        }
    }

    fun clearSceneEffects(projectId: String, sceneIndex: Int) {
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                val targetSceneId = project.scenes.getOrNull(sceneIndex)?.id
                val updatedScenes = project.scenes.mapIndexed { idx, scene ->
                    if (idx == sceneIndex) {
                        val updatedSceneLayers = if (scene.isMultiLayerComposite && scene.layers.isNotEmpty()) {
                            scene.layers.mapIndexed { lIdx, layer ->
                                if (lIdx == 0) layer.copy(effectPasses = emptyList()) else layer
                            }
                        } else scene.layers
                        scene.copy(
                            effect = SceneEffect(),
                            effectPasses = emptyList(),
                            layers = updatedSceneLayers
                        )
                    } else scene
                }
                val updatedAeLayers = if (targetSceneId != null) {
                    project.aeLayers.map { aeLayer ->
                        if (aeLayer.sourceSceneId == targetSceneId) {
                            aeLayer.copy(effectPasses = emptyList())
                        } else aeLayer
                    }
                } else project.aeLayers
                project.copy(scenes = updatedScenes, aeLayers = updatedAeLayers)
            }
        }
    }

    fun applyLayerEffectPasses(projectId: String, layerId: String, passes: List<ShaderEffectPass>) {
        val active = passes.filter { it.effectId != "none" }
        val primary = active.firstOrNull()?.let {
            SceneEffect(
                category = it.category,
                effectId = it.effectId,
                effectName = it.effectName,
                intensity = it.intensity
            )
        } ?: SceneEffect()
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                val matchingAeSourceSceneIds = project.aeLayers
                    .filter { it.id == layerId }
                    .mapNotNull { it.sourceSceneId }
                    .toSet()
                val updatedLayers = project.layers.map { layer ->
                    if (layer.id == layerId) {
                        layer.copy(effectPasses = active)
                    } else layer
                }
                val updatedAeLayers = project.aeLayers.map { layer ->
                    if (layer.id == layerId) {
                        layer.copy(effectPasses = active)
                    } else layer
                }
                val updatedScenes = project.scenes.map { sc ->
                    val matchesCompositeLayer = sc.isMultiLayerComposite && sc.layers.any { it.id == layerId }
                    val matchesAeSource = sc.id in matchingAeSourceSceneIds
                    when {
                        matchesCompositeLayer -> {
                            val newLayers = sc.layers.map { l ->
                                if (l.id == layerId) l.copy(effectPasses = active) else l
                            }
                            val isPrimaryLayer = sc.layers.firstOrNull()?.id == layerId
                            if (isPrimaryLayer || matchesAeSource) {
                                sc.copy(layers = newLayers, effect = primary, effectPasses = active)
                            } else {
                                sc.copy(layers = newLayers)
                            }
                        }
                        matchesAeSource -> sc.copy(effect = primary, effectPasses = active)
                        else -> sc
                    }
                }
                project.copy(layers = updatedLayers, aeLayers = updatedAeLayers, scenes = updatedScenes)
            }
        }
    }

    fun applySceneMotion(projectId: String, sceneIndex: Int, motion: SceneMotion) {
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                project.copy(
                    scenes = project.scenes.mapIndexed { idx, scene ->
                        if (idx == sceneIndex) scene.copy(motion = motion) else scene
                    }
                )
            }
        }
    }

    fun applySceneTiming(projectId: String, sceneIndex: Int, timing: SceneTiming) {
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                project.copy(
                    scenes = project.scenes.mapIndexed { idx, scene ->
                        if (idx == sceneIndex) {
                            scene.copy(
                                timing = timing,
                                durationMs = timing.durationMs.coerceAtLeast(200L)
                            )
                        } else scene
                    }
                )
            }
        }
    }

    fun applySceneTransition(projectId: String, sceneIndex: Int, transition: SceneTransition) {
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                project.copy(
                    scenes = project.scenes.mapIndexed { idx, scene ->
                        if (idx == sceneIndex) scene.copy(transitionToNext = transition) else scene
                    }
                )
            }
        }
    }

    fun restoreScenes(projectId: String, scenes: List<TimelineScene>) {
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                project.copy(scenes = scenes)
            }
        }
    }

    fun pasteSceneAt(projectId: String, copiedScene: TimelineScene, insertAfterIndex: Int) {
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                val newClipId = "clip_${UUID.randomUUID().toString().take(6)}"
                val pastedClip = when (val c = copiedScene.clip) {
                    is MediaClip.Video -> c.copy(id = newClipId)
                    is MediaClip.Photo -> c.copy(id = newClipId)
                    null -> null
                }
                val pastedScene = copiedScene.copy(
                    id = "scene_${UUID.randomUUID().toString().take(8)}",
                    clipId = pastedClip?.id ?: copiedScene.clipId,
                    clip = pastedClip,
                    transitionToNext = null
                )
                val mutable = project.scenes.toMutableList()
                val targetIndex = if (mutable.isEmpty()) 0 else (insertAfterIndex + 1).coerceIn(0, mutable.size)
                mutable.add(targetIndex, pastedScene)
                project.copy(scenes = mutable)
            }
        }
    }

    fun updateSceneTimingMode(projectId: String, sceneIndex: Int, mode: com.example.model.TimingMode) {
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                if (sceneIndex !in project.scenes.indices) return@map project
                project.copy(
                    scenes = project.scenes.mapIndexed { idx, scene ->
                        if (idx == sceneIndex) {
                            scene.copy(timing = scene.timing.copy(mode = mode))
                        } else scene
                    }
                )
            }
        }
    }

    // --- After Effects Mode Unlimited Vertical Multi-Layer Stack ---

    private fun reindexZOrder(layers: List<EditorLayer>): List<EditorLayer> {
        val count = layers.size
        return layers.mapIndexed { idx, layer ->
            layer.copy(zIndex = (count - 1 - idx).coerceAtLeast(0))
        }
    }

    fun ensureAeLayersHydrated(projectId: String) {
        val proj = getProjectById(projectId) ?: return
        if (proj.aeLayers.isNotEmpty() || proj.scenes.isEmpty()) return
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId || project.aeLayers.isNotEmpty() || project.scenes.isEmpty()) {
                    return@map project
                }
                var cursor = 0L
                val seeded = project.scenes.mapIndexed { idx, sc ->
                    val dur = sc.effectiveDurationMs
                    val clip = sc.clip
                    val layer = EditorLayer(
                        id = "ae_layer_seed_${idx + 1}_${sc.id.take(6)}",
                        number = idx + 1,
                        mediaClip = clip,
                        backgroundColorArgb = if (clip == null) sc.transform.bgColorArgb else null,
                        offsetX = sc.transform.offsetX,
                        offsetY = sc.transform.offsetY,
                        scale = sc.transform.scale,
                        rotation = sc.transform.rotation,
                        isMirrored = sc.transform.flipH,
                        isFlippedVertical = sc.transform.flipV,
                        effectPasses = sc.resolvedEffectPasses(),
                        title = if (clip is MediaClip.Photo) "Imagem" else if (clip is MediaClip.Video) "Video" else sc.title,
                        layerKind = AeLayerKind.MEDIA,
                        isVisible = true,
                        isLocked = false,
                        startTimeMs = cursor,
                        durationMs = dur,
                        sourceSceneId = sc.id
                    )
                    cursor += dur
                    layer
                }.reversed()
                project.copy(aeLayers = reindexZOrder(seeded))
            }
        }
    }

    fun addAeStackedLayer(
        projectId: String,
        kind: AeLayerKind,
        clip: MediaClip? = null,
        title: String = "",
        startTimeMs: Long = 0L,
        durationMs: Long = 3_000L
    ): EditorLayer? {
        var createdLayer: EditorLayer? = null
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                val nextNum = (project.aeLayers.maxOfOrNull { it.number } ?: 0) + 1
                val resolvedTitle = when {
                    title.isNotBlank() -> title
                    clip is MediaClip.Photo -> "Imagem"
                    clip is MediaClip.Video -> "Video"
                    kind == AeLayerKind.TEXT -> "Text $nextNum"
                    kind == AeLayerKind.SHAPE -> "Shape $nextNum"
                    kind == AeLayerKind.ADJUSTMENT -> "Adjustment $nextNum"
                    kind == AeLayerKind.NULL -> "Null $nextNum"
                    kind == AeLayerKind.ELEMENT -> "Element $nextNum"
                    kind == AeLayerKind.DRAW -> "Draw $nextNum"
                    kind == AeLayerKind.AUDIO -> "Audio $nextNum"
                    else -> "Layer $nextNum"
                }
                val defaultBg = when (kind) {
                    AeLayerKind.SHAPE -> 0xFF00E676L
                    AeLayerKind.ELEMENT -> 0xFF29B6F6L
                    AeLayerKind.DRAW -> 0xFFFF2A85L
                    else -> null
                }
                val defaultTextSpec = if (kind == AeLayerKind.TEXT) {
                    TextLayerSpec(
                        text = resolvedTitle,
                        fontSizeSp = 34f,
                        textColorArgb = 0xFFFFFFFFL
                    )
                } else {
                    TextLayerSpec()
                }
                val resolvedDur = (clip?.effectiveDurationMs ?: durationMs).coerceIn(500L, 60_000L)
                val seededSceneId = if (project.scenes.isEmpty() && clip != null) {
                    "scene_${UUID.randomUUID().toString().take(8)}"
                } else null
                val newLayer = EditorLayer(
                    id = "ae_layer_${UUID.randomUUID().toString().take(8)}",
                    number = nextNum,
                    mediaClip = clip,
                    backgroundColorArgb = if (clip == null) defaultBg else null,
                    textSpec = defaultTextSpec,
                    title = resolvedTitle,
                    layerKind = kind,
                    isVisible = true,
                    isLocked = false,
                    startTimeMs = startTimeMs.coerceAtLeast(0L),
                    durationMs = resolvedDur,
                    sourceSceneId = seededSceneId
                )
                createdLayer = newLayer
                val stacked = reindexZOrder(listOf(newLayer) + project.aeLayers)
                val updatedScenes = if (seededSceneId != null && clip != null) {
                    listOf(
                        TimelineScene(
                            id = seededSceneId,
                            title = resolvedTitle,
                            durationMs = resolvedDur,
                            clipId = clip.id,
                            mediaUriString = clip.uriString,
                            isVideo = clip is MediaClip.Video,
                            clip = clip,
                            timing = SceneTiming(durationMs = resolvedDur)
                        )
                    )
                } else project.scenes
                project.copy(
                    aeLayers = stacked,
                    scenes = updatedScenes,
                    clipCount = project.clipCount.coerceAtLeast(stacked.size)
                )
            }
        }
        return createdLayer
    }

    fun toggleAeLayerVisibility(projectId: String, layerId: String) {
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                project.copy(
                    aeLayers = project.aeLayers.map { layer ->
                        if (layer.id == layerId) layer.copy(isVisible = !layer.isVisible) else layer
                    }
                )
            }
        }
    }

    fun toggleAeLayerLock(projectId: String, layerId: String) {
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                project.copy(
                    aeLayers = project.aeLayers.map { layer ->
                        if (layer.id == layerId) layer.copy(isLocked = !layer.isLocked) else layer
                    }
                )
            }
        }
    }

    fun renameAeLayer(projectId: String, layerId: String, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                project.copy(
                    aeLayers = project.aeLayers.map { layer ->
                        if (layer.id == layerId) {
                            layer.copy(
                                title = trimmed,
                                textSpec = if (layer.layerKind == AeLayerKind.TEXT) {
                                    layer.textSpec.copy(text = trimmed)
                                } else layer.textSpec
                            )
                        } else layer
                    }
                )
            }
        }
    }

    fun duplicateAeLayer(projectId: String, layerId: String): EditorLayer? {
        var cloned: EditorLayer? = null
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                val idx = project.aeLayers.indexOfFirst { it.id == layerId }
                if (idx !in project.aeLayers.indices) return@map project
                val src = project.aeLayers[idx]
                val nextNum = (project.aeLayers.maxOfOrNull { it.number } ?: 0) + 1
                val copyClip = when (val c = src.mediaClip) {
                    is MediaClip.Video -> c.copy(id = "clip_${UUID.randomUUID().toString().take(6)}")
                    is MediaClip.Photo -> c.copy(id = "clip_${UUID.randomUUID().toString().take(6)}")
                    null -> null
                }
                val dup = src.copy(
                    id = "ae_layer_${UUID.randomUUID().toString().take(8)}",
                    number = nextNum,
                    mediaClip = copyClip,
                    title = "${src.displayTitle} Copy"
                )
                cloned = dup
                val mutable = project.aeLayers.toMutableList()
                mutable.add(idx, dup)
                project.copy(aeLayers = reindexZOrder(mutable))
            }
        }
        return cloned
    }

    fun deleteAeLayer(projectId: String, layerId: String) {
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                val remaining = project.aeLayers.filterNot { it.id == layerId }
                project.copy(aeLayers = reindexZOrder(remaining))
            }
        }
    }

    fun reorderAeLayer(projectId: String, fromIndex: Int, toIndex: Int) {
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                if (fromIndex !in project.aeLayers.indices || toIndex !in project.aeLayers.indices || fromIndex == toIndex) {
                    return@map project
                }
                val mutable = project.aeLayers.toMutableList()
                val item = mutable.removeAt(fromIndex)
                mutable.add(toIndex, item)
                project.copy(aeLayers = reindexZOrder(mutable))
            }
        }
    }

    fun updateAeLayer(projectId: String, layerId: String, transform: (EditorLayer) -> EditorLayer) {
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                var updatedTarget: EditorLayer? = null
                var passesChanged = false
                val updatedAeLayers = project.aeLayers.map { layer ->
                    if (layer.id == layerId) {
                        val next = transform(layer)
                        updatedTarget = next
                        passesChanged = next.effectPasses != layer.effectPasses
                        next
                    } else layer
                }
                val targetSceneId = updatedTarget?.sourceSceneId
                val updatedScenes = if (passesChanged && targetSceneId != null) {
                    val active = updatedTarget!!.effectPasses.filter { it.effectId != "none" }
                    val primary = active.firstOrNull()?.let {
                        SceneEffect(
                            category = it.category,
                            effectId = it.effectId,
                            effectName = it.effectName,
                            intensity = it.intensity
                        )
                    } ?: SceneEffect()
                    project.scenes.map { sc ->
                        if (sc.id == targetSceneId) {
                            val syncedLayers = if (sc.isMultiLayerComposite && sc.layers.isNotEmpty()) {
                                sc.layers.mapIndexed { lIdx, l ->
                                    if (lIdx == 0) l.copy(effectPasses = active) else l
                                }
                            } else sc.layers
                            sc.copy(effect = primary, effectPasses = active, layers = syncedLayers)
                        } else sc
                    }
                } else project.scenes
                project.copy(
                    aeLayers = updatedAeLayers,
                    scenes = updatedScenes
                )
            }
        }
    }

    fun moveAeLayerTiming(projectId: String, layerId: String, newStartTimeMs: Long) {
        val clamped = newStartTimeMs.coerceAtLeast(0L)
        updateAeLayer(projectId, layerId) { layer ->
            if (layer.isLocked) layer else layer.copy(startTimeMs = clamped)
        }
    }

    fun splitAeLayerAt(projectId: String, layerId: String, playheadMs: Long): EditorLayer? {
        var createdSecondHalf: EditorLayer? = null
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                val idx = project.aeLayers.indexOfFirst { it.id == layerId }
                if (idx !in project.aeLayers.indices) return@map project
                val target = project.aeLayers[idx]
                if (target.isLocked) return@map project
                val splitPair = com.example.engine.ae.AeEffectEngine.splitLayerAt(target, playheadMs)
                    ?: return@map project
                val nextNum = (project.aeLayers.maxOfOrNull { it.number } ?: 0) + 1
                val secondHalf = splitPair.second.copy(number = nextNum)
                createdSecondHalf = secondHalf
                val mutable = project.aeLayers.toMutableList()
                mutable[idx] = splitPair.first
                mutable.add(idx, secondHalf)
                project.copy(
                    aeLayers = reindexZOrder(mutable),
                    clipCount = project.clipCount.coerceAtLeast(mutable.size)
                )
            }
        }
        return createdSecondHalf
    }

    fun restoreProjectSnapshot(
        projectId: String,
        scenes: List<TimelineScene>,
        aeLayers: List<EditorLayer>
    ) {
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                project.copy(scenes = scenes, aeLayers = aeLayers)
            }
        }
    }
}
