package com.example.data

import android.content.Context
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

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
    }

    fun saveNow() {
        val ctx = appContext ?: return
        val snapshot = _projects.value
        JsonProjectStore.saveProjects(ctx, snapshot)
    }

    private fun persistAsync() {
        val ctx = appContext ?: return
        val snapshot = _projects.value
        ioScope.launch {
            JsonProjectStore.saveProjects(ctx, snapshot)
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
            scenes = emptyList()
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
                val hasActiveContent = layers.any { it.mediaClip != null || it.backgroundColorArgb != null || it.textOverlay.isNotBlank() }
                if (!hasActiveContent) {
                    return@map project.copy(layers = layers)
                }
                val baseClip = layers.firstNotNullOfOrNull { it.mediaClip }
                val compDuration = baseClip?.effectiveDurationMs ?: 3_000L

                val updatedScenes = when {
                    targetSceneIndex != null && targetSceneIndex in project.scenes.indices -> {
                        project.scenes.mapIndexed { idx, scene ->
                            if (idx == targetSceneIndex) {
                                scene.copy(
                                    layers = layers,
                                    isMultiLayerComposite = true,
                                    clip = scene.clip ?: baseClip,
                                    mediaUriString = scene.mediaUriString ?: baseClip?.uriString,
                                    isVideo = baseClip is MediaClip.Video
                                )
                            } else scene
                        }
                    }
                    createNewSceneIfEmpty && project.scenes.none { it.isMultiLayerComposite } -> {
                        val mlScene = TimelineScene(
                            id = "scene_${UUID.randomUUID().toString().take(8)}",
                            title = "Scene ${project.scenes.size + 1}",
                            durationMs = compDuration,
                            clipId = baseClip?.id,
                            mediaUriString = baseClip?.uriString,
                            isVideo = baseClip is MediaClip.Video,
                            clip = baseClip,
                            timing = SceneTiming(durationMs = compDuration),
                            layers = layers,
                            isMultiLayerComposite = true
                        )
                        if (project.scenes.isEmpty()) listOf(mlScene) else project.scenes + mlScene
                    }
                    else -> {
                        val lastMlIdx = project.scenes.indexOfLast { it.isMultiLayerComposite }
                        if (lastMlIdx >= 0) {
                            project.scenes.mapIndexed { idx, s ->
                                if (idx == lastMlIdx) s.copy(layers = layers, clip = s.clip ?: baseClip) else s
                            }
                        } else project.scenes
                    }
                }
                project.copy(layers = layers, scenes = updatedScenes)
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
                project.copy(
                    scenes = project.scenes.mapIndexed { idx, scene ->
                        if (idx in sceneIndices) scene.copy(effect = effect, effectPasses = passList) else scene
                    }
                )
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
                project.copy(
                    scenes = project.scenes.mapIndexed { idx, scene ->
                        if (idx in sceneIndices) {
                            scene.copy(effect = primary, effectPasses = active)
                        } else scene
                    }
                )
            }
        }
    }

    fun clearSceneEffects(projectId: String, sceneIndex: Int) {
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                project.copy(
                    scenes = project.scenes.mapIndexed { idx, scene ->
                        if (idx == sceneIndex) {
                            scene.copy(
                                effect = SceneEffect(),
                                effectPasses = emptyList()
                            )
                        } else scene
                    }
                )
            }
        }
    }

    fun applyLayerEffectPasses(projectId: String, layerId: String, passes: List<ShaderEffectPass>) {
        val active = passes.filter { it.effectId != "none" }
        mutateProjects { list ->
            list.map { project ->
                if (project.id != projectId) return@map project
                val updatedLayers = project.layers.map { layer ->
                    if (layer.id == layerId) {
                        layer.copy(effectPasses = active)
                    } else layer
                }
                val updatedScenes = project.scenes.map { sc ->
                    if (sc.isMultiLayerComposite && sc.layers.any { it.id == layerId }) {
                        sc.copy(
                            layers = sc.layers.map { l ->
                                if (l.id == layerId) l.copy(effectPasses = active) else l
                            }
                        )
                    } else sc
                }
                project.copy(layers = updatedLayers, scenes = updatedScenes)
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
}
