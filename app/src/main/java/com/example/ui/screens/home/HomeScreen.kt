package com.example.ui.screens.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.model.AspectRatioMode
import com.example.model.MediaClip
import com.example.model.ShareTarget
import com.example.model.VideoProject
import com.example.ui.components.AudioLibraryDialog
import com.example.ui.components.DeleteProjectDialog
import com.example.ui.components.MediaUriThumbnail
import com.example.ui.components.NewProjectBottomSheet
import com.example.ui.components.ProjectEditorDialog
import com.example.ui.components.QuickActionsOrHelpDialog
import com.example.ui.components.SettingsDialog
import com.example.ui.components.StarStudioLogo
import com.example.ui.components.VideoStarPopover
import com.example.ui.components.geometricTriangleBackground
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.LocalVideoStarColors
import com.example.ui.theme.VideoStarBadgeRed
import com.example.ui.theme.VideoStarBlack
import com.example.ui.theme.VideoStarInkDark
import com.example.ui.theme.VideoStarInkMuted
import com.example.ui.theme.VideoStarLightSurface
import com.example.ui.theme.VideoStarMagenta
import com.example.ui.theme.VideoStarWhite
import com.example.viewmodel.SharedProjectViewModel
import java.util.Locale
import kotlin.math.absoluteValue

@Composable
fun HomeScreen(
    sharedProjectViewModel: SharedProjectViewModel,
    onOpenProject: (String) -> Unit,
    onOpenAdjustVideo: (projectId: String, mediaUri: String, durationMs: Long, sourceFps: Int) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isImportingMedia by sharedProjectViewModel.isImportingMedia.collectAsStateWithLifecycle()
    val importError by sharedProjectViewModel.importError.collectAsStateWithLifecycle()
    val extraColors = LocalVideoStarColors.current
    val snackbarHostState = remember { SnackbarHostState() }
    var shareAnchorBounds by remember { mutableStateOf<Rect?>(null) }
    val context = LocalContext.current

    // Local audio file picker ("حدد الأغنية"): extracts real duration & navigates to Screen 2
    val newProjectSongPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { pickedUri ->
        if (pickedUri != null) {
            viewModel.createProjectFromDraft { newProjectId ->
                sharedProjectViewModel.importAudioToProject(
                    context = context,
                    projectId = newProjectId,
                    sourceUri = pickedUri,
                    extractFromVideo = false
                ) { _ ->
                    onOpenProject(newProjectId)
                }
            }
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbar()
        }
    }

    LaunchedEffect(importError) {
        val err = importError
        if (err != null) {
            snackbarHostState.showSnackbar(err)
            sharedProjectViewModel.clearImportError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .geometricTriangleBackground()
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                HomeTopHeader(
                    isFavoritesFilterActive = uiState.showFavoritesOnly,
                    onFavoritesClick = viewModel::toggleFavoritesFilter,
                    onStoreClick = { viewModel.openDialog(HomeDialogState.StoreShowcase) },
                    onAudioLibraryClick = { viewModel.openDialog(HomeDialogState.AudioLibrary) },
                    onCreateProjectClick = viewModel::openCreateProjectDialog
                )
            },
            bottomBar = {
                HomeBottomFooter(
                    onSearchClick = { viewModel.openDialog(HomeDialogState.SearchProjects) },
                    onSettingsClick = { viewModel.openDialog(HomeDialogState.Settings) },
                    onCloudClick = { viewModel.openDialog(HomeDialogState.CloudRestore) },
                    onHelpClick = { viewModel.openDialog(HomeDialogState.Help) }
                )
            }
        ) { innerPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
            val projects = uiState.visibleProjects
            val maxCardSize = (maxWidth * 0.68f).coerceAtMost(320.dp)

            if (projects.isEmpty()) {
                EmptyProjectsState(
                    onCreateClick = viewModel::openCreateProjectDialog,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                    val pagerState = rememberPagerState(
                        initialPage = uiState.activeProjectIndex.coerceIn(0, projects.lastIndex),
                        pageCount = { projects.size }
                    )

                    LaunchedEffect(pagerState.currentPage) {
                        viewModel.onActiveProjectChanged(pagerState.currentPage)
                    }

                    LaunchedEffect(uiState.activeProjectIndex, projects.size) {
                        val target = uiState.activeProjectIndex.coerceIn(0, projects.lastIndex)
                        if (pagerState.currentPage != target) {
                            pagerState.animateScrollToPage(target)
                        }
                    }

                    val horizontalContentPadding = ((maxWidth - maxCardSize) / 2f).coerceAtLeast(24.dp)

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 720.dp)
                            .align(Alignment.Center),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            HorizontalPager(
                                state = pagerState,
                                contentPadding = PaddingValues(horizontal = horizontalContentPadding),
                                pageSpacing = 18.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("home_projects_pager")
                            ) { page ->
                                val project = projects[page]
                                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                                val isActive = page == pagerState.currentPage

                                ProjectCarouselCard(
                                    project = project,
                                    isActive = isActive,
                                    pageOffset = pageOffset,
                                    onToggleFavorite = {
                                        if (isActive) viewModel.toggleCurrentProjectFavorite()
                                    },
                                    onOpenProject = { onOpenProject(project.id) },
                                    modifier = Modifier
                                        .widthIn(max = maxCardSize)
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        val activeProject = uiState.currentProject
                        if (activeProject != null) {
                            Text(
                                text = activeProject.title,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = VideoStarInkDark
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(horizontal = 24.dp)
                                    .testTag("active_project_title")
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = VideoStarMagenta.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = activeProject.aspectRatio.label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = VideoStarMagenta,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Text(
                                    text = "${activeProject.clipCount} مقاطع • ${activeProject.frameRate}fps • ${activeProject.resolution}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = VideoStarInkMuted
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { onOpenProject(activeProject.id) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VideoStarMagenta,
                                    contentColor = VideoStarWhite
                                ),
                                shape = RoundedCornerShape(22.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("open_workspace_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayCircleOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = stringResource(R.string.open_project_cta),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    VideoStarPopover(
        expanded = uiState.isSharePopoverVisible,
        onDismissRequest = viewModel::dismissSharePopover,
        anchorBounds = shareAnchorBounds,
        width = 224.dp
    ) {
        ShareTarget.entries.forEachIndexed { index, target ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable { viewModel.onShareTargetSelected(target) }
                    .testTag("share_option_${target.name.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(target.labelResId),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = VideoStarMagenta,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                )
            }
            if (index < ShareTarget.entries.lastIndex) {
                HorizontalDivider(
                    thickness = 0.8.dp,
                    color = extraColors.popoverDivider
                )
            }
        }
    }

    when (val dialog = uiState.activeDialog) {
        HomeDialogState.CreateProject -> {
            NewProjectBottomSheet(
                selectedFrameRate = uiState.newProjectFrameRate,
                selectedAspectRatio = uiState.newProjectAspectRatio,
                selectedResolution = uiState.newProjectResolution,
                selectedCompression = uiState.newProjectCompression,
                selectedPresetName = uiState.newProjectPresetName,
                savedProjects = uiState.allProjects,
                isCompressionSheetVisible = uiState.isCompressionSheetVisible,
                onFrameRateSelected = viewModel::updateNewProjectFrameRate,
                onAspectRatioSelected = viewModel::updateNewProjectAspectRatio,
                onResolutionSelected = viewModel::updateNewProjectResolution,
                onCompressionSelected = viewModel::updateNewProjectCompression,
                onOpenCompressionSheet = viewModel::openCompressionSheet,
                onCloseCompressionSheet = viewModel::closeCompressionSheet,
                onApplyPreset = viewModel::applyNewProjectPreset,
                onStepAspectRatio = viewModel::stepNewProjectAspectRatio,
                onToggleOrientation = viewModel::toggleNewProjectOrientation,
                onOpenSavedProject = { projectId ->
                    viewModel.dismissDialog()
                    onOpenProject(projectId)
                },
                onSelectSongClick = {
                    newProjectSongPickerLauncher.launch(arrayOf("audio/*"))
                },
                onEditVideoClick = {
                    viewModel.createProjectFromDraft { newProjectId ->
                        onOpenProject(newProjectId)
                    }
                },
                onDismiss = viewModel::dismissDialog
            )
        }
        is HomeDialogState.EditProject -> {
            ProjectEditorDialog(
                isEdit = true,
                initialTitle = dialog.project.title,
                initialRatio = dialog.project.aspectRatio,
                onDismiss = viewModel::dismissDialog,
                onConfirm = { title, ratio ->
                    viewModel.updateProject(dialog.project.id, title, ratio)
                }
            )
        }
        is HomeDialogState.DeleteProject -> {
            DeleteProjectDialog(
                projectTitle = dialog.project.title,
                onDismiss = viewModel::dismissDialog,
                onConfirmDelete = { viewModel.deleteProject(dialog.project.id) }
            )
        }
        HomeDialogState.Settings -> {
            SettingsDialog(
                exportResolution = uiState.exportResolution,
                fpsOption = uiState.fpsOption,
                watermarkEnabled = uiState.watermarkEnabled,
                onResolutionChange = viewModel::updateResolution,
                onFpsChange = viewModel::updateFps,
                onWatermarkToggle = viewModel::updateWatermark,
                onDismiss = viewModel::dismissDialog
            )
        }
        HomeDialogState.AudioLibrary -> {
            AudioLibraryDialog(
                selectedTrack = uiState.selectedAudioTrack,
                onSelectTrack = viewModel::updateAudioTrack,
                onDismiss = viewModel::dismissDialog
            )
        }
        HomeDialogState.StoreShowcase -> {
            QuickActionsOrHelpDialog(
                title = "حزم المؤثرات والانتقالات",
                items = listOf(
                    "مؤثرات الألوان والتوهج" to "تشمل التوهج النيون وانقسام RGB والتدرج اللوني.",
                    "حزم الحركة 3D" to "طبقات متعددة مع مفاتيح حركة ومنحنيات سرعة احترافية.",
                    "مقاسات جاهزة" to "قوالب 16:9 و 9:16 و 4:5 و 1:1 متاحة مباشرة عند إنشاء مشروع جديد."
                ),
                actionLabel = "إنشاء مشروع جديد",
                onActionClick = { viewModel.openCreateProjectDialog() },
                onDismiss = viewModel::dismissDialog
            )
        }
        HomeDialogState.SearchProjects -> {
            SearchProjectsDialog(
                query = uiState.searchQuery,
                projects = uiState.allProjects,
                onQueryChange = viewModel::updateSearchQuery,
                onOpenProject = { id ->
                    viewModel.dismissDialog()
                    onOpenProject(id)
                },
                onDismiss = viewModel::dismissDialog
            )
        }
        HomeDialogState.CloudRestore -> {
            CloudProjectsDialog(
                projects = uiState.allProjects,
                onOpenProject = { id ->
                    viewModel.dismissDialog()
                    onOpenProject(id)
                },
                onCreateQuick = {
                    viewModel.createQuickProject("مشروع سريع", AspectRatioMode.LANDSCAPE, onOpenProject)
                },
                onDismiss = viewModel::dismissDialog
            )
        }
        HomeDialogState.QuickActions -> {
            QuickActionsOrHelpDialog(
                title = "إجراءات سريعة",
                items = listOf(
                    "بدء تحرير سريع 9:16" to "إنشاء مشروع عمودي فارغ جاهز للريلز والقصص.",
                    "نسخ إعدادات الإيقاع" to "مزامنة المسار الصوتي الحالي (${uiState.selectedAudioTrack}).",
                    "استيراد فيديو أو صورة" to "افتح المشروع واستخدم جديد لاختيار الوسائط من جهازك."
                ),
                actionLabel = "إنشاء مشروع 9:16",
                onActionClick = {
                    viewModel.createQuickProject("مقطع سريع 9:16", AspectRatioMode.PORTRAIT, onOpenProject)
                },
                onDismiss = viewModel::dismissDialog
            )
        }
        HomeDialogState.Help -> {
            QuickActionsOrHelpDialog(
                title = "دليل استوديو النجم السريع",
                items = listOf(
                    "1. إنشاء مشروع" to "اضغط زر (+) لاختيار معدل الإطارات وشكل الفيديو والدّقة ثم تحرير الفيديو أو حدد الأغنية.",
                    "2. مساحة العمل" to "استخدم شريط التمرير وأزرار الإطار (< و >) واختصارات تحرير واختر وجديد.",
                    "3. محرر الطبقات المتعددة" to "أضف فيديوهات أو صوراً على الطبقات وحرّكها مع Keyframes."
                ),
                onDismiss = viewModel::dismissDialog
            )
        }
        null -> Unit
    }

    if (isImportingMedia) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = VideoStarWhite,
                shape = RoundedCornerShape(14.dp),
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = VideoStarMagenta)
                    Text(
                        text = "جاري تجهيز الأغنية للمشروع...",
                        fontFamily = CairoFontFamily,
                        style = MaterialTheme.typography.bodyMedium,
                        color = VideoStarInkDark
                    )
                }
            }
        }
    }
}

/**
 * Top Header matching Screen 1 reference:
 * - Row 1: Centered "VIDEO STAR" logo (#0E4D64 + #B81D6F star with #0E4D64 outline)
 * - Row 2 (Sub-header icon row):
 *   Left: Magenta heart with red circle star badge (★) + Magenta shopping bag with star
 *   Right: Magenta music note + Magenta plus (+)
 */
@Composable
private fun HomeTopHeader(
    isFavoritesFilterActive: Boolean,
    onFavoritesClick: () -> Unit,
    onStoreClick: () -> Unit,
    onAudioLibraryClick: () -> Unit,
    onCreateProjectClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 6.dp, bottom = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            StarStudioLogo()
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left side: Magenta heart with red circle star badge + Magenta shopping bag with star
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onFavoritesClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("home_favorites_button")
                    ) {
                        Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isFavoritesFilterActive) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = stringResource(R.string.cd_favorites),
                                tint = VideoStarMagenta,
                                modifier = Modifier.size(26.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 2.dp, y = 1.dp)
                                    .size(13.dp)
                                    .clip(CircleShape)
                                    .background(VideoStarBadgeRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = null,
                                    tint = VideoStarWhite,
                                    modifier = Modifier.size(9.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onStoreClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("home_quick_actions_button")
                    ) {
                        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.ShoppingBag,
                                contentDescription = stringResource(R.string.cd_quick_actions),
                                tint = VideoStarMagenta,
                                modifier = Modifier.size(25.dp)
                            )
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = null,
                                tint = VideoStarMagenta,
                                modifier = Modifier
                                    .size(10.dp)
                                    .offset(y = 3.dp)
                            )
                        }
                    }
                }

                // Right side: Magenta music note + Magenta plus (+)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onAudioLibraryClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("home_audio_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MusicNote,
                            contentDescription = stringResource(R.string.cd_audio_library),
                            tint = VideoStarMagenta,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    IconButton(
                        onClick = onCreateProjectClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("home_create_project_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.cd_new_project),
                            tint = VideoStarMagenta,
                            modifier = Modifier.size(29.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bottom Footer matching Screen 1 reference:
 * - Magenta magnifying glass icon at bottom-left above the gray footer bar
 * - Gray footer bar: Magenta gear icon (left), Magenta cloud-download icon and "?" icon (right)
 */
@Composable
private fun HomeBottomFooter(
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCloudClick: () -> Unit,
    onHelpClick: () -> Unit
) {
    val extraColors = LocalVideoStarColors.current
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(
                    onClick = onSearchClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("home_search_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "بحث في المشاريع",
                        tint = VideoStarMagenta,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        Surface(
            color = extraColors.toolbarBackground,
            shadowElevation = 2.dp
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .height(54.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Magenta gear icon
                    ToolbarIconAction(
                        icon = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.cd_settings),
                        enabled = true,
                        onClick = onSettingsClick,
                        testTag = "home_settings_button"
                    )

                    // Right: Magenta cloud-download icon + "?" icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ToolbarIconAction(
                            icon = Icons.Outlined.CloudDownload,
                            contentDescription = "المشاريع المحفوظة",
                            enabled = true,
                            onClick = onCloudClick,
                            testTag = "home_cloud_button"
                        )
                        ToolbarIconAction(
                            icon = Icons.Outlined.HelpOutline,
                            contentDescription = stringResource(R.string.cd_help),
                            enabled = true,
                            onClick = onHelpClick,
                            testTag = "home_help_button"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchProjectsDialog(
    query: String,
    projects: List<VideoProject>,
    onQueryChange: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val filtered = remember(query, projects) {
        val q = query.trim()
        if (q.isEmpty()) projects else projects.filter { it.title.contains(q, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VideoStarWhite,
        title = {
            Text(
                text = "بحث في المشاريع",
                fontFamily = CairoFontFamily,
                fontWeight = FontWeight.Bold,
                color = VideoStarInkDark
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("اكتب اسم المشروع...", fontFamily = CairoFontFamily) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (filtered.isEmpty()) {
                    Text(
                        text = "لا توجد مشاريع مطابقة حالياً",
                        fontFamily = CairoFontFamily,
                        color = VideoStarInkMuted,
                        fontSize = 13.sp
                    )
                } else {
                    filtered.take(5).forEach { proj ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(VideoStarLightSurface)
                                .clickable { onOpenProject(proj.id) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(proj.title, fontFamily = CairoFontFamily, color = VideoStarInkDark, fontWeight = FontWeight.SemiBold)
                            Text(proj.aspectRatio.label, color = VideoStarMagenta, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = VideoStarMagenta)
            ) {
                Text(stringResource(R.string.btn_close), fontFamily = CairoFontFamily)
            }
        }
    )
}

@Composable
private fun CloudProjectsDialog(
    projects: List<VideoProject>,
    onOpenProject: (String) -> Unit,
    onCreateQuick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VideoStarWhite,
        title = {
            Text(
                text = "المشاريع المحفوظة",
                fontFamily = CairoFontFamily,
                fontWeight = FontWeight.Bold,
                color = VideoStarInkDark
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (projects.isEmpty()) {
                    Text(
                        text = "لا توجد مشاريع محفوظة بعد. يمكنك إنشاء مشروع جديد الآن.",
                        fontFamily = CairoFontFamily,
                        color = VideoStarInkMuted
                    )
                } else {
                    projects.take(6).forEach { proj ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(VideoStarLightSurface)
                                .clickable { onOpenProject(proj.id) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(proj.title, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = VideoStarInkDark)
                                Text(
                                    text = "${proj.aspectRatio.label} • ${proj.frameRate}fps • ${String.format(Locale.US, "%.2fs", proj.totalTimelineDurationMs / 1000.0)}",
                                    fontSize = 12.sp,
                                    color = VideoStarInkMuted
                                )
                            }
                            Text("فتح", fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = VideoStarMagenta)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = if (projects.isEmpty()) onCreateQuick else onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = VideoStarMagenta)
            ) {
                Text(if (projects.isEmpty()) "إنشاء مشروع" else stringResource(R.string.btn_close), fontFamily = CairoFontFamily)
            }
        }
    )
}

@Composable
private fun ProjectCarouselCard(
    project: VideoProject,
    isActive: Boolean,
    pageOffset: Float,
    onToggleFavorite: () -> Unit,
    onOpenProject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = (1f - (pageOffset * 0.10f)).coerceIn(0.86f, 1f)
    val borderColor = if (isActive) VideoStarMagenta else VideoStarBlack
    val borderWidth = if (isActive) 3.5.dp else 1.5.dp
    val firstClip = project.primaryClip

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (isActive) 12.dp else 4.dp,
                shape = RoundedCornerShape(4.dp)
            )
            .clip(RoundedCornerShape(4.dp))
            .background(VideoStarBlack)
            .border(width = borderWidth, color = borderColor, shape = RoundedCornerShape(4.dp))
            .clickable(onClick = onOpenProject)
            .testTag("project_card_${project.id}")
    ) {
        MediaUriThumbnail(
            uriString = firstClip?.uriString,
            isVideo = firstClip !is MediaClip.Photo,
            contentDescription = project.title,
            frameTimeMs = if (firstClip is MediaClip.Video) firstClip.startTrimMs else 0L,
            contentScale = ContentScale.Crop,
            emptyLabel = "مشروع جديد",
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.48f))
                .clickable(onClick = onToggleFavorite)
                .testTag("project_fav_toggle_${project.id}"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (project.isFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = stringResource(R.string.cd_favorites),
                tint = if (project.isFavorite) VideoStarMagenta else VideoStarWhite,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ToolbarIconAction(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(48.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) VideoStarMagenta else VideoStarMagenta.copy(alpha = 0.35f),
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun EmptyProjectsState(
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.VideoLibrary,
            contentDescription = null,
            tint = VideoStarMagenta,
            modifier = Modifier.size(56.dp)
        )
        Text(
            text = stringResource(R.string.empty_projects_title),
            style = MaterialTheme.typography.titleLarge,
            color = VideoStarInkDark,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.empty_projects_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = VideoStarInkMuted,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onCreateClick,
            colors = ButtonDefaults.buttonColors(containerColor = VideoStarMagenta),
            modifier = Modifier.height(48.dp)
        ) {
            Text(stringResource(R.string.dialog_new_project_title))
        }
    }
}
