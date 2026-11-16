package com.example.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.VideoStarRepository
import com.example.model.AspectRatioMode
import com.example.model.CompressionQuality
import com.example.model.ShareTarget
import com.example.model.VideoProject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

sealed interface HomeDialogState {
    data object CreateProject : HomeDialogState
    data class EditProject(val project: VideoProject) : HomeDialogState
    data class DeleteProject(val project: VideoProject) : HomeDialogState
    data object Settings : HomeDialogState
    data object QuickActions : HomeDialogState
    data object StoreShowcase : HomeDialogState
    data object SearchProjects : HomeDialogState
    data object CloudRestore : HomeDialogState
    data object AudioLibrary : HomeDialogState
    data object Help : HomeDialogState
}

data class HomeUiState(
    val allProjects: List<VideoProject> = emptyList(),
    val showFavoritesOnly: Boolean = false,
    val activeProjectIndex: Int = 0,
    val isSharePopoverVisible: Boolean = false,
    val activeDialog: HomeDialogState? = null,
    val isSelectingSongForNewProject: Boolean = false,
    val isCompressionSheetVisible: Boolean = false,
    val newProjectFrameRate: Int = 30,
    val newProjectAspectRatio: AspectRatioMode = AspectRatioMode.LANDSCAPE,
    val newProjectResolution: String = "1080p",
    val newProjectCompression: CompressionQuality = CompressionQuality.BALANCED,
    val newProjectPresetName: String = "افتراضي",
    val selectedAudioTrack: String = "إيقاع إلكتروني 128 BPM",
    val exportResolution: String = "1080p",
    val fpsOption: Int = 60,
    val watermarkEnabled: Boolean = false,
    val searchQuery: String = "",
    val snackbarMessage: String? = null
) {
    val visibleProjects: List<VideoProject>
        get() = if (showFavoritesOnly) allProjects.filter { it.isFavorite } else allProjects

    val currentProject: VideoProject?
        get() = visibleProjects.getOrNull(activeProjectIndex.coerceIn(0, (visibleProjects.size - 1).coerceAtLeast(0)))
}

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        VideoStarRepository.projects
            .onEach { list ->
                _uiState.update { state ->
                    val maxIdx = (if (state.showFavoritesOnly) list.count { it.isFavorite } else list.size) - 1
                    state.copy(
                        allProjects = list,
                        activeProjectIndex = state.activeProjectIndex.coerceIn(0, maxIdx.coerceAtLeast(0))
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onActiveProjectChanged(index: Int) {
        _uiState.update { it.copy(activeProjectIndex = index) }
    }

    fun toggleFavoritesFilter() {
        _uiState.update { state ->
            val nextFav = !state.showFavoritesOnly
            val msg = if (nextFav) "عرض المشاريع المفضلة فقط" else "عرض جميع المشاريع"
            state.copy(
                showFavoritesOnly = nextFav,
                activeProjectIndex = 0,
                activeDialog = if (state.allProjects.isNotEmpty()) null else state.activeDialog,
                snackbarMessage = msg
            )
        }
    }

    fun toggleCurrentProjectFavorite() {
        val current = _uiState.value.currentProject ?: return
        VideoStarRepository.toggleFavorite(current.id)
    }

    fun openCreateProjectDialog() {
        _uiState.update {
            it.copy(
                activeDialog = HomeDialogState.CreateProject,
                isSharePopoverVisible = false,
                isSelectingSongForNewProject = false,
                isCompressionSheetVisible = false,
                newProjectFrameRate = 30,
                newProjectAspectRatio = AspectRatioMode.LANDSCAPE,
                newProjectResolution = "1080p",
                newProjectCompression = CompressionQuality.BALANCED,
                newProjectPresetName = "افتراضي"
            )
        }
    }

    fun updateNewProjectFrameRate(fps: Int) {
        _uiState.update { it.copy(newProjectFrameRate = fps, newProjectPresetName = "مخصص") }
    }

    fun updateNewProjectAspectRatio(ratio: AspectRatioMode) {
        _uiState.update { it.copy(newProjectAspectRatio = ratio, newProjectPresetName = "مخصص") }
    }

    fun updateNewProjectResolution(resolution: String) {
        _uiState.update { it.copy(newProjectResolution = resolution, newProjectPresetName = "مخصص") }
    }

    fun updateNewProjectCompression(quality: CompressionQuality) {
        _uiState.update {
            it.copy(
                newProjectCompression = quality,
                isCompressionSheetVisible = false,
                newProjectPresetName = "مخصص"
            )
        }
    }

    fun openCompressionSheet() {
        _uiState.update { it.copy(isCompressionSheetVisible = true) }
    }

    fun closeCompressionSheet() {
        _uiState.update { it.copy(isCompressionSheetVisible = false) }
    }

    fun applyNewProjectPreset(
        name: String,
        fps: Int,
        ratio: AspectRatioMode,
        resolution: String,
        compression: CompressionQuality = CompressionQuality.BALANCED
    ) {
        _uiState.update {
            it.copy(
                newProjectPresetName = name,
                newProjectFrameRate = fps,
                newProjectAspectRatio = ratio,
                newProjectResolution = resolution,
                newProjectCompression = compression
            )
        }
    }

    fun stepNewProjectAspectRatio(direction: Int) {
        val ordered = listOf(
            AspectRatioMode.PORTRAIT,
            AspectRatioMode.PORTRAIT_4_5,
            AspectRatioMode.PORTRAIT_3_4,
            AspectRatioMode.SQUARE,
            AspectRatioMode.LANDSCAPE_4_3,
            AspectRatioMode.LANDSCAPE
        )
        _uiState.update { state ->
            val currentIdx = ordered.indexOf(state.newProjectAspectRatio).let { if (it >= 0) it else ordered.lastIndex }
            val nextIdx = (currentIdx + direction + ordered.size) % ordered.size
            state.copy(
                newProjectAspectRatio = ordered[nextIdx],
                newProjectPresetName = "مخصص"
            )
        }
    }

    fun toggleNewProjectOrientation() {
        _uiState.update { state ->
            val nextRatio = when (state.newProjectAspectRatio) {
                AspectRatioMode.LANDSCAPE -> AspectRatioMode.PORTRAIT
                AspectRatioMode.LANDSCAPE_4_3 -> AspectRatioMode.PORTRAIT_3_4
                AspectRatioMode.SQUARE -> AspectRatioMode.PORTRAIT
                AspectRatioMode.PORTRAIT_3_4 -> AspectRatioMode.LANDSCAPE_4_3
                AspectRatioMode.PORTRAIT_4_5 -> AspectRatioMode.LANDSCAPE_4_3
                AspectRatioMode.PORTRAIT -> AspectRatioMode.LANDSCAPE
            }
            state.copy(newProjectAspectRatio = nextRatio, newProjectPresetName = "مخصص")
        }
    }

    fun openSongPickerForNewProject() {
        _uiState.update { it.copy(isSelectingSongForNewProject = true) }
    }

    fun closeSongPickerForNewProject() {
        _uiState.update { it.copy(isSelectingSongForNewProject = false) }
    }

    fun createProjectFromDraft(
        initialDurationMs: Long = 10_020L,
        audioTitleOverride: String? = null,
        audioUriString: String? = null,
        onCreated: (String) -> Unit
    ) {
        val state = _uiState.value
        val created = VideoStarRepository.createProject(
            title = "",
            aspectRatio = state.newProjectAspectRatio,
            frameRate = state.newProjectFrameRate,
            resolution = state.newProjectResolution,
            audioTitle = audioTitleOverride ?: state.selectedAudioTrack,
            compressionQuality = state.newProjectCompression,
            initialDurationMs = initialDurationMs,
            audioUriString = audioUriString
        )
        _uiState.update {
            it.copy(
                activeDialog = null,
                isSelectingSongForNewProject = false,
                isCompressionSheetVisible = false,
                showFavoritesOnly = false,
                activeProjectIndex = 0
            )
        }
        onCreated(created.id)
    }

    fun openEditCurrentProjectDialog() {
        val current = _uiState.value.currentProject ?: return
        _uiState.update { it.copy(activeDialog = HomeDialogState.EditProject(current), isSharePopoverVisible = false) }
    }

    fun openDeleteCurrentProjectDialog() {
        val current = _uiState.value.currentProject ?: return
        _uiState.update { it.copy(activeDialog = HomeDialogState.DeleteProject(current), isSharePopoverVisible = false) }
    }

    fun openDialog(dialog: HomeDialogState) {
        _uiState.update { it.copy(activeDialog = dialog, isSharePopoverVisible = false) }
    }

    fun dismissDialog() {
        _uiState.update {
            it.copy(
                activeDialog = null,
                isSelectingSongForNewProject = false,
                isCompressionSheetVisible = false
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleSharePopover() {
        _uiState.update { state ->
            if (state.currentProject == null) state
            else state.copy(isSharePopoverVisible = !state.isSharePopoverVisible)
        }
    }

    fun dismissSharePopover() {
        _uiState.update { it.copy(isSharePopoverVisible = false) }
    }

    fun createQuickProject(title: String, aspectRatio: AspectRatioMode, onCreated: (String) -> Unit) {
        val created = VideoStarRepository.createProject(
            title = title,
            aspectRatio = aspectRatio,
            frameRate = 30,
            resolution = "1080p",
            audioTitle = _uiState.value.selectedAudioTrack,
            initialDurationMs = 10_020L
        )
        _uiState.update {
            it.copy(
                activeDialog = null,
                showFavoritesOnly = false,
                activeProjectIndex = 0
            )
        }
        onCreated(created.id)
    }

    fun updateProject(projectId: String, title: String, aspectRatio: AspectRatioMode) {
        VideoStarRepository.updateProject(projectId, title, aspectRatio)
        _uiState.update {
            it.copy(
                activeDialog = null,
                snackbarMessage = "تم تحديث بيانات المشروع"
            )
        }
    }

    fun deleteProject(projectId: String) {
        VideoStarRepository.deleteProject(projectId)
        _uiState.update {
            it.copy(
                activeDialog = null,
                snackbarMessage = "تم حذف المشروع"
            )
        }
    }

    fun onShareTargetSelected(target: ShareTarget) {
        val title = _uiState.value.currentProject?.title ?: ""
        _uiState.update {
            it.copy(
                isSharePopoverVisible = false,
                snackbarMessage = "تم تجهيز «$title» للتصدير إلى ${target.platformName}"
            )
        }
    }

    fun updateAudioTrack(track: String) {
        if (_uiState.value.activeDialog != HomeDialogState.CreateProject) {
            _uiState.value.currentProject?.let { VideoStarRepository.updateProjectAudio(it.id, track) }
        }
        _uiState.update { it.copy(selectedAudioTrack = track) }
    }

    fun updateResolution(res: String) {
        _uiState.update { it.copy(exportResolution = res) }
    }

    fun updateFps(fps: Int) {
        _uiState.update { it.copy(fpsOption = fps) }
    }

    fun updateWatermark(enabled: Boolean) {
        _uiState.update { it.copy(watermarkEnabled = enabled) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
