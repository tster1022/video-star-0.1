package com.example.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ImportedAudioInfo
import com.example.data.ImportedMediaInfo
import com.example.data.MediaImportHelper
import com.example.data.Mp4VideoExporter
import com.example.data.SnapshotExporter
import com.example.data.VideoStarRepository
import com.example.model.AspectRatioMode
import com.example.model.ClipColorGrading
import com.example.model.MediaClip
import com.example.model.TimelineScene
import com.example.model.VideoProject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class SharedProjectViewModel : ViewModel() {

    val projects: StateFlow<List<VideoProject>> = VideoStarRepository.projects

    private val _isImportingMedia = MutableStateFlow(false)
    val isImportingMedia: StateFlow<Boolean> = _isImportingMedia.asStateFlow()

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()

    private val _exportProgress = MutableStateFlow<Float?>(null)
    val exportProgress: StateFlow<Float?> = _exportProgress.asStateFlow()

    private val _exportStatusMessage = MutableStateFlow<String?>(null)
    val exportStatusMessage: StateFlow<String?> = _exportStatusMessage.asStateFlow()

    fun getProject(projectId: String): VideoProject? =
        VideoStarRepository.getProjectById(projectId)

    fun createProject(
        title: String,
        aspectRatio: AspectRatioMode,
        frameRate: Int = 30,
        resolution: String = "1080p",
        audioTitle: String = "إيقاع إلكتروني 128 BPM"
    ): VideoProject = VideoStarRepository.createProject(
        title = title,
        aspectRatio = aspectRatio,
        frameRate = frameRate,
        resolution = resolution,
        audioTitle = audioTitle
    )

    fun importMediaToProject(
        context: Context,
        projectId: String,
        sourceUri: Uri,
        isVideo: Boolean,
        onReady: (ImportedMediaInfo) -> Unit
    ) {
        viewModelScope.launch {
            _isImportingMedia.value = true
            _importError.value = null
            try {
                val info = MediaImportHelper.copyUriToProjectMediaDir(
                    context = context,
                    projectId = projectId,
                    sourceUri = sourceUri,
                    isVideo = isVideo
                )
                onReady(info)
            } catch (e: Throwable) {
                _importError.value = e.message ?: "تعذر استيراد الملف من المعرض"
            } finally {
                _isImportingMedia.value = false
            }
        }
    }

    fun importAudioToProject(
        context: Context,
        projectId: String,
        sourceUri: Uri,
        extractFromVideo: Boolean,
        onReady: (ImportedAudioInfo) -> Unit
    ) {
        viewModelScope.launch {
            _isImportingMedia.value = true
            _importError.value = null
            try {
                val info = if (extractFromVideo) {
                    MediaImportHelper.extractAudioFromVideoUri(context, projectId, sourceUri)
                } else {
                    MediaImportHelper.copyAudioUriToProjectDir(context, projectId, sourceUri)
                }
                val current = VideoStarRepository.getProjectById(projectId)
                if (current != null) {
                    VideoStarRepository.updateAudioConfig(
                        projectId = projectId,
                        audioConfig = current.audioConfig.copy(
                            songTitle = info.title,
                            songUriString = info.localUriString
                        ),
                        durationMs = info.durationMs
                    )
                }
                onReady(info)
            } catch (e: Throwable) {
                _importError.value = e.message ?: "تعذر استيراد المسار الصوتي"
            } finally {
                _isImportingMedia.value = false
            }
        }
    }

    fun captureSnapshotToGallery(
        context: Context,
        project: VideoProject,
        scene: TimelineScene?,
        localPositionMs: Long,
        onDone: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val path = SnapshotExporter.captureAndSaveSnapshot(
                    context = context,
                    project = project,
                    scene = scene,
                    localPositionMs = localPositionMs
                )
                onDone("تم حفظ الصورة في $path")
            } catch (e: Throwable) {
                _importError.value = e.message ?: "تعذر حفظ اللقطة"
            }
        }
    }

    fun exportProjectVideo(context: Context, projectId: String) {
        val project = VideoStarRepository.getProjectById(projectId) ?: return
        if (project.scenes.isEmpty()) {
            _importError.value = "أضف مشهداً واحداً على الأقل قبل التصدير"
            return
        }
        viewModelScope.launch {
            _exportProgress.value = 0.02f
            _exportStatusMessage.value = null
            try {
                val outputPath = Mp4VideoExporter.exportProjectToMp4(context, project) { p ->
                    _exportProgress.value = p
                }
                _exportStatusMessage.value = "تم تصدير الفيديو بنجاح إلى $outputPath"
            } catch (e: Throwable) {
                _importError.value = e.message ?: "تعذر تصدير الفيديو"
            } finally {
                _exportProgress.value = null
            }
        }
    }

    fun exportSingleSceneToShare(
        context: Context,
        projectId: String,
        sceneIndex: Int,
        onShareReady: (outputPath: String, shareUri: Uri?) -> Unit
    ) {
        val project = VideoStarRepository.getProjectById(projectId) ?: return
        val scene = project.scenes.getOrNull(sceneIndex)
        if (scene == null) {
            _importError.value = "لا يوجد مشهد محدد للمشاركة"
            return
        }
        val singleSceneProject = project.copy(
            scenes = listOf(scene),
            durationMs = scene.effectiveDurationMs
        )
        viewModelScope.launch {
            _exportProgress.value = 0.04f
            _exportStatusMessage.value = null
            try {
                val (outputPath, shareUri) = Mp4VideoExporter.exportProjectToMp4WithUri(
                    context = context,
                    project = singleSceneProject
                ) { p ->
                    _exportProgress.value = p
                }
                _exportStatusMessage.value = "تم تصدير المقطع للمشاركة: $outputPath"
                onShareReady(outputPath, shareUri)
            } catch (e: Throwable) {
                _importError.value = e.message ?: "تعذر تصدير المقطع"
            } finally {
                _exportProgress.value = null
            }
        }
    }

    fun clearExportStatus() {
        _exportStatusMessage.value = null
    }

    fun saveAdjustedVideo(
        projectId: String,
        uriString: String,
        startTrimMs: Long,
        endTrimMs: Long,
        durationMs: Long,
        sourceFps: Int,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        rotation: Float,
        isFitMode: Boolean,
        backgroundColorArgb: Long,
        colorGrading: ClipColorGrading = ClipColorGrading(),
        targetLayerIndex: Int? = null,
        replaceSceneIndex: Int? = null
    ) {
        val clip = MediaClip.Video(
            id = "vid_${UUID.randomUUID().toString().take(8)}",
            uriString = uriString,
            startTrimMs = startTrimMs,
            endTrimMs = endTrimMs,
            durationMs = durationMs,
            sourceFps = sourceFps,
            scale = scale,
            offsetX = offsetX,
            offsetY = offsetY,
            rotation = rotation,
            isFitMode = isFitMode,
            backgroundColorArgb = backgroundColorArgb,
            colorGrading = colorGrading
        )
        VideoStarRepository.addOrUpdateVideoClip(
            projectId = projectId,
            clip = clip,
            targetLayerIndex = targetLayerIndex,
            replaceSceneIndex = replaceSceneIndex
        )
    }

    fun saveAdjustedPhoto(
        projectId: String,
        uriString: String,
        durationMs: Long,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        rotation: Float,
        isFitMode: Boolean,
        isFlippedHorizontal: Boolean,
        isFlippedVertical: Boolean,
        backgroundColorArgb: Long,
        colorGrading: ClipColorGrading = ClipColorGrading(),
        targetLayerIndex: Int? = null,
        replaceSceneIndex: Int? = null
    ) {
        val clip = MediaClip.Photo(
            id = "img_${UUID.randomUUID().toString().take(8)}",
            uriString = uriString,
            durationMs = durationMs,
            scale = scale,
            offsetX = offsetX,
            offsetY = offsetY,
            rotation = rotation,
            isFitMode = isFitMode,
            backgroundColorArgb = backgroundColorArgb,
            isFlippedHorizontal = isFlippedHorizontal,
            isFlippedVertical = isFlippedVertical,
            colorGrading = colorGrading
        )
        VideoStarRepository.addOrUpdatePhotoClip(
            projectId = projectId,
            clip = clip,
            targetLayerIndex = targetLayerIndex,
            replaceSceneIndex = replaceSceneIndex
        )
    }

    fun splitScene(projectId: String, sceneIndex: Int, localSplitMs: Long) {
        VideoStarRepository.splitSceneAt(projectId, sceneIndex, localSplitMs)
    }

    fun duplicateScene(projectId: String, sceneIndex: Int) {
        VideoStarRepository.duplicateScene(projectId, sceneIndex)
    }

    fun mergeSceneWithNext(projectId: String, sceneIndex: Int): Boolean =
        VideoStarRepository.mergeSceneWithNext(projectId, sceneIndex)

    fun toggleReverseScene(projectId: String, sceneIndex: Int) {
        VideoStarRepository.toggleReverseScene(projectId, sceneIndex)
    }

    fun trimScene(projectId: String, sceneIndex: Int, startTrimMs: Long, endTrimMs: Long) {
        VideoStarRepository.trimScene(projectId, sceneIndex, startTrimMs, endTrimMs)
    }

    fun deleteScene(projectId: String, sceneIndex: Int) {
        VideoStarRepository.deleteSceneAt(projectId, sceneIndex)
    }

    fun clearSceneEffects(projectId: String, sceneIndex: Int) {
        VideoStarRepository.clearSceneEffects(projectId, sceneIndex)
    }

    fun applySceneEffectPasses(
        projectId: String,
        sceneIndices: Set<Int>,
        passes: List<com.example.model.ShaderEffectPass>
    ) {
        VideoStarRepository.applySceneEffectPasses(projectId, sceneIndices, passes)
    }

    fun applyLayerEffectPasses(
        projectId: String,
        layerId: String,
        passes: List<com.example.model.ShaderEffectPass>
    ) {
        VideoStarRepository.applyLayerEffectPasses(projectId, layerId, passes)
    }

    fun clearImportError() {
        _importError.value = null
    }
}
