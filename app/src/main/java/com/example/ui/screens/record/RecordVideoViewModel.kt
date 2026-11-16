package com.example.ui.screens.record

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.VideoStarRepository
import com.example.model.AspectRatioMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class RecordVideoUiState(
    val projectId: String = "",
    val aspectRatio: AspectRatioMode = AspectRatioMode.PORTRAIT,
    val songUriString: String? = null,
    val songTitle: String = "",
    val songVolume: Float = 0.85f,
    val isFrontCamera: Boolean = true,
    val countdownSecondsSetting: Int = 3,
    val countdownRemaining: Int = 0,
    val isRecording: Boolean = false,
    val elapsedRecordingMs: Long = 0L,
    val syncSongPlayback: Boolean = true,
    val statusMessage: String? = null
)

class RecordVideoViewModel(private val projectId: String) : ViewModel() {

    private val project = VideoStarRepository.getProjectById(projectId)

    private val _uiState = MutableStateFlow(
        RecordVideoUiState(
            projectId = projectId,
            aspectRatio = project?.aspectRatio ?: AspectRatioMode.PORTRAIT,
            songUriString = project?.audioConfig?.songUriString,
            songTitle = project?.audioConfig?.songTitle ?: "",
            songVolume = project?.audioConfig?.songVolume ?: 0.85f
        )
    )
    val uiState: StateFlow<RecordVideoUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    fun toggleCameraLens() {
        if (_uiState.value.isRecording || _uiState.value.countdownRemaining > 0) return
        _uiState.update { it.copy(isFrontCamera = !it.isFrontCamera) }
    }

    fun cycleCountdownSetting() {
        if (_uiState.value.isRecording || _uiState.value.countdownRemaining > 0) return
        _uiState.update { state ->
            val next = when (state.countdownSecondsSetting) {
                0 -> 3
                3 -> 5
                else -> 0
            }
            state.copy(countdownSecondsSetting = next)
        }
    }

    fun toggleSyncSong() {
        _uiState.update { it.copy(syncSongPlayback = !it.syncSongPlayback) }
    }

    fun startRecordingSequence(onBeginActualCapture: () -> Unit) {
        if (_uiState.value.isRecording || _uiState.value.countdownRemaining > 0) return
        val initialCountdown = _uiState.value.countdownSecondsSetting
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            if (initialCountdown > 0) {
                for (sec in initialCountdown downTo 1) {
                    _uiState.update { it.copy(countdownRemaining = sec) }
                    delay(1000L)
                }
            }
            _uiState.update { it.copy(countdownRemaining = 0, isRecording = true, elapsedRecordingMs = 0L) }
            onBeginActualCapture()
            val startTime = System.currentTimeMillis()
            while (_uiState.value.isRecording) {
                _uiState.update { it.copy(elapsedRecordingMs = System.currentTimeMillis() - startTime) }
                delay(100L)
            }
        }
    }

    fun onRecordingStoppedState() {
        _uiState.update { it.copy(isRecording = false, countdownRemaining = 0) }
    }

    fun cancelCountdown() {
        timerJob?.cancel()
        _uiState.update { it.copy(countdownRemaining = 0, isRecording = false) }
    }

    fun postStatusMessage(msg: String) {
        _uiState.update { it.copy(statusMessage = msg) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun createOutputFile(context: Context): File {
        val dir = File(context.filesDir, "projects/$projectId/media")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "rec_${System.currentTimeMillis()}.mp4")
    }

    fun extractVideoMetadataAndFinish(
        context: Context,
        outputFile: File,
        onReady: (uriString: String, durationMs: Long, sourceFps: Int) -> Unit
    ) {
        viewModelScope.launch {
            val (durationMs, fps) = withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(outputFile.absolutePath)
                    val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()?.coerceAtLeast(500L) ?: 3_000L
                    val rawFps = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                            ?.toFloatOrNull()?.toInt()
                    } else null
                    dur to (rawFps ?: 30).coerceIn(15, 120)
                } catch (_: Exception) {
                    3_000L to 30
                } finally {
                    try {
                        retriever.release()
                    } catch (_: Exception) {
                    }
                }
            }
            onReady(Uri.fromFile(outputFile).toString(), durationMs, fps)
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}
