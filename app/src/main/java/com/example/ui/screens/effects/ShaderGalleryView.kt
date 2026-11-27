package com.example.ui.screens.effects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.outlined.FolderOpen
import com.example.data.VideoStarRepository
import com.example.engine.packs.CustomPackRepository
import com.example.gl.rememberShaderThumbnail
import com.example.model.EffectCatalog
import com.example.model.EffectCatalogItem
import com.example.model.EffectStudioTab
import com.example.model.MediaClip
import com.example.model.ShaderEffectPass
import com.example.model.StudioLayoutMode
import com.example.model.arabicDisplayName
import com.example.model.toStudioTab
import com.example.ui.components.EffectAnimatedSampleCard
import com.example.ui.components.SampleCardKind
import com.example.ui.components.rememberSceneSampleThumbnail
import com.example.ui.screens.packs.PackImportStudioSheet
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.VideoStarMagenta
import kotlinx.coroutines.launch

private val GalleryBg = Color(0xFF0A0C14)
private val GalleryCardBg = Color(0xFF131622)
private val GallerySurface = Color(0xFF10131E)
private val GalleryBorder = Color(0xFF24283A)
private val GalleryMuted = Color(0xFF8E95A8)

@Composable
fun ShaderGalleryScreen(
    projectId: String,
    sceneIndex: Int = 0,
    targetLayerId: String? = null,
    onBack: () -> Unit,
    onOpenEffectsStudio: (sceneIndices: List<Int>, targetLayerId: String?) -> Unit
) {
    val projects by VideoStarRepository.projects.collectAsStateWithLifecycle()
    val project = remember(projects, projectId) { projects.firstOrNull { it.id == projectId } }

    val resolvedLayer = remember(project, targetLayerId, sceneIndex) {
        when {
            targetLayerId != null -> project?.aeLayers?.firstOrNull { it.id == targetLayerId }
                ?: project?.layers?.firstOrNull { it.id == targetLayerId }
            project?.studioLayout == StudioLayoutMode.AFTER_EFFECTS -> {
                val sceneId = project.scenes.getOrNull(sceneIndex)?.id
                (sceneId?.let { id -> project.aeLayers.firstOrNull { it.sourceSceneId == id } })
                    ?: project.resolveActiveAeLayer(0L)
            }
            else -> null
        }
    }
    val safeSceneIndex = remember(project, sceneIndex) {
        val size = project?.scenes?.size ?: 0
        if (size <= 0) 0 else sceneIndex.coerceIn(0, size - 1)
    }
    val targetScene = remember(project, safeSceneIndex) {
        project?.scenes?.getOrNull(safeSceneIndex)
    }

    val previewClip: MediaClip? = resolvedLayer?.mediaClip ?: targetScene?.clip ?: project?.primaryClip
    val currentPasses: List<ShaderEffectPass> = remember(resolvedLayer, targetScene) {
        resolvedLayer?.effectPasses ?: targetScene?.resolvedEffectPasses().orEmpty()
    }
    val appliedIds = remember(currentPasses) { currentPasses.map { it.effectId }.toSet() }
    val trackDisplayName = remember(resolvedLayer, targetScene, safeSceneIndex) {
        resolvedLayer?.displayTitle
            ?: targetScene?.title
            ?: "المسار #${safeSceneIndex + 1}"
    }

    val customShaders by CustomPackRepository.importedShaders.collectAsStateWithLifecycle()
    val customPresets by CustomPackRepository.importedPresets.collectAsStateWithLifecycle()
    val allShaders = remember(customShaders, customPresets) { EffectCatalog.combinedShaderEffects }
    var selectedTab by remember { mutableStateOf(EffectStudioTab.ALL) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showFavoritesOnly by remember { mutableStateOf(false) }
    var showAppliedOnly by remember { mutableStateOf(false) }
    var showPackImporter by remember { mutableStateOf(false) }
    val favoriteIds by VideoStarRepository.favoriteAeEffectIds.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val sceneSampleThumbnail = rememberSceneSampleThumbnail(previewClip)

    val filteredShaders = remember(allShaders, selectedTab, searchQuery, showFavoritesOnly, showAppliedOnly, favoriteIds, appliedIds) {
        var base = when {
            showFavoritesOnly -> allShaders.filter { it.id in favoriteIds }
            selectedTab == EffectStudioTab.ALL -> allShaders
            else -> allShaders.filter { it.toStudioTab() == selectedTab }
        }
        if (showAppliedOnly) {
            base = base.filter { it.id in appliedIds }
        }
        val q = searchQuery.trim().lowercase()
        if (q.isNotEmpty()) {
            base = base.filter { item ->
                item.name.lowercase().contains(q) ||
                    item.arabicDisplayName().lowercase().contains(q) ||
                    item.id.lowercase().contains(q) ||
                    item.category.shortLabel.lowercase().contains(q)
            }
        }
        base
    }

    fun applyOrToggleShader(item: EffectCatalogItem) {
        if (project == null) return
        val xmlPreset = CustomPackRepository.findXmlPreset(item.id)
        val expandedPasses = CustomPackRepository.expandEffectPassesForSelection(item.id)
        val canonical = EffectCatalog.resolveCanonicalId(item.id)
        val existingIdx = currentPasses.indexOfFirst { EffectCatalog.resolveCanonicalId(it.effectId) == canonical }
        val updatedPasses = if (xmlPreset != null && expandedPasses.isNotEmpty()) {
            val targetIds = expandedPasses.map { it.effectId }.toSet()
            val allApplied = targetIds.all { tid -> currentPasses.any { it.effectId == tid } }
            if (allApplied) currentPasses.filterNot { it.effectId in targetIds } else currentPasses + expandedPasses
        } else if (existingIdx >= 0) {
            currentPasses.filterIndexed { i, _ -> i != existingIdx }
        } else {
            val pass = expandedPasses.firstOrNull() ?: ShaderEffectPass(
                id = "gal_${item.id}_${System.nanoTime() % 100000L}",
                effectId = item.id,
                effectName = item.name,
                category = item.category,
                intensity = item.defaultIntensity,
                params = item.defaultParamMap(),
                colorParams = item.defaultColorMap(),
                textParams = item.defaultTextMap()
            )
            currentPasses + pass
        }

        val hasLayerTarget = resolvedLayer != null
        val hasSceneTarget = targetScene != null
        when {
            hasLayerTarget -> {
                VideoStarRepository.applyLayerEffectPasses(project.id, resolvedLayer!!.id, updatedPasses)
            }
            hasSceneTarget -> {
                VideoStarRepository.applySceneEffectPasses(project.id, setOf(safeSceneIndex), updatedPasses)
            }
            else -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("أضف فيديو أو صورة إلى المسار أولاً لتطبيق الشادرات")
                }
                return
            }
        }

        val actionMsg = if (existingIdx >= 0) {
            "تمت إزالة ${item.name} من $trackDisplayName"
        } else {
            "تم تطبيق ${item.name} على $trackDisplayName"
        }
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(actionMsg)
        }
    }

    fun clearAllOnTrack() {
        if (project == null) return
        when {
            resolvedLayer != null -> VideoStarRepository.applyLayerEffectPasses(project.id, resolvedLayer.id, emptyList())
            targetScene != null -> VideoStarRepository.clearSceneEffects(project.id, safeSceneIndex)
        }
    }

    val accentColor = when (project?.studioLayout) {
        StudioLayoutMode.AFTER_EFFECTS -> Color(0xFF00E676)
        StudioLayoutMode.BLURRR -> Color(0xFFB8FF29)
        StudioLayoutMode.CAPCUT -> Color(0xFF00E5FF)
        StudioLayoutMode.NODE_VIDEO -> Color(0xFF4F8BFF)
        else -> VideoStarMagenta
    }
    val onAccentColor = if (
        project?.studioLayout == StudioLayoutMode.AFTER_EFFECTS ||
        project?.studioLayout == StudioLayoutMode.BLURRR ||
        project?.studioLayout == StudioLayoutMode.CAPCUT
    ) {
        Color(0xFF06130C)
    } else {
        Color.White
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = GalleryBg,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                Surface(
                    color = GallerySurface,
                    border = BorderStroke(1.dp, GalleryBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("shader_gallery_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "العودة لمساحة العمل",
                                    tint = Color.White
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "معرض الشادرات (${allShaders.size} GPU)",
                                    fontFamily = CairoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "المسار الحالي: $trackDisplayName • ${currentPasses.size} مفعّل",
                                    fontFamily = CairoFontFamily,
                                    fontSize = 11.sp,
                                    color = accentColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { showPackImporter = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("shader_gallery_import_packs_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FolderOpen,
                                    contentDescription = "استيراد حزم XML والشيدر",
                                    tint = Color(0xFF00E676)
                                )
                            }
                            if (currentPasses.isNotEmpty()) {
                                IconButton(
                                    onClick = ::clearAllOnTrack,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("shader_gallery_clear_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = "مسح مؤثرات المسار",
                                        tint = Color(0xFFF87171)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { showSearch = !showSearch },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("shader_gallery_search_toggle")
                            ) {
                                Icon(
                                    imageVector = if (showSearch) Icons.Rounded.Close else Icons.Outlined.Search,
                                    contentDescription = "بحث في 145 شادر",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            },
            bottomBar = {
                Surface(
                    color = GallerySurface,
                    border = BorderStroke(1.dp, GalleryBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (currentPasses.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("shader_gallery_active_bar"),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(currentPasses, key = { it.id }) { pass ->
                                    Surface(
                                        color = accentColor.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f)),
                                        modifier = Modifier
                                            .height(34.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                EffectCatalog.findById(pass.effectId)?.let(::applyOrToggleShader)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = pass.effectName,
                                                fontFamily = InterFontFamily,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp,
                                                color = Color.White,
                                                maxLines = 1
                                            )
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "إزالة ${pass.effectName}",
                                                tint = accentColor,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    onOpenEffectsStudio(listOf(safeSceneIndex), resolvedLayer?.id)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accentColor,
                                    contentColor = onAccentColor
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("shader_gallery_open_studio_button")
                            ) {
                                Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "تعديل المعاملات والكي فريم (${currentPasses.size})",
                                    fontFamily = CairoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Surface(
                                color = Color(0xFF1D2234),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, GalleryBorder),
                                modifier = Modifier
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(onClick = onBack)
                                    .testTag("shader_gallery_done_button")
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 18.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "تم",
                                        fontFamily = CairoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 960.dp)
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showSearch) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            placeholder = {
                                Text(
                                    text = "ابحث بين 145 شادر (Glow, Tiles, VHS, Blur...)",
                                    fontFamily = CairoFontFamily,
                                    fontSize = 12.sp,
                                    color = GalleryMuted
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = GalleryBorder,
                                focusedContainerColor = GalleryCardBg,
                                unfocusedContainerColor = GalleryCardBg
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("shader_gallery_search_input")
                        )
                    }

                    // Category & filter chips
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item(key = "fav_chip") {
                            FilterChip(
                                selected = showFavoritesOnly,
                                onClick = { showFavoritesOnly = !showFavoritesOnly },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (showFavoritesOnly) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = "المفضلة (${favoriteIds.size})",
                                        fontFamily = CairoFontFamily,
                                        fontSize = 12.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor,
                                    selectedLabelColor = onAccentColor,
                                    selectedLeadingIconColor = onAccentColor,
                                    containerColor = GalleryCardBg,
                                    labelColor = Color(0xFFD5D9E6)
                                ),
                                modifier = Modifier.testTag("shader_gallery_favorites_chip")
                            )
                        }

                        item(key = "applied_chip") {
                            FilterChip(
                                selected = showAppliedOnly,
                                onClick = { showAppliedOnly = !showAppliedOnly },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = "المفعّل (${appliedIds.size})",
                                        fontFamily = CairoFontFamily,
                                        fontSize = 12.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor,
                                    selectedLabelColor = onAccentColor,
                                    selectedLeadingIconColor = onAccentColor,
                                    containerColor = GalleryCardBg,
                                    labelColor = Color(0xFFD5D9E6)
                                ),
                                modifier = Modifier.testTag("shader_gallery_applied_chip")
                            )
                        }

                        items(EffectStudioTab.entries, key = { it.name }) { tab ->
                            val count = remember(allShaders, tab) {
                                if (tab == EffectStudioTab.ALL) allShaders.size
                                else allShaders.count { it.toStudioTab() == tab }
                            }
                            val selected = !showFavoritesOnly && selectedTab == tab
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    showFavoritesOnly = false
                                    selectedTab = tab
                                },
                                label = {
                                    Text(
                                        text = "${tab.label} ($count)",
                                        fontFamily = InterFontFamily,
                                        fontSize = 12.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor,
                                    selectedLabelColor = onAccentColor,
                                    containerColor = GalleryCardBg,
                                    labelColor = Color(0xFFD5D9E6)
                                ),
                                modifier = Modifier.testTag("shader_gallery_tab_${tab.name.lowercase()}")
                            )
                        }
                    }

                    Text(
                        text = "اضغط على أي شادر لتطبيقه فوراً على المسار (${filteredShaders.size} شادر متاح)",
                        fontFamily = CairoFontFamily,
                        fontSize = 11.5.sp,
                        color = GalleryMuted,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 104.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("shader_gallery_grid"),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredShaders, key = { it.id }) { item ->
                            val isApplied = item.id in appliedIds
                            val isFavorite = item.id in favoriteIds
                            ShaderGalleryThumbCard(
                                item = item,
                                previewClip = previewClip,
                                fallbackSceneThumb = sceneSampleThumbnail,
                                isApplied = isApplied,
                                isFavorite = isFavorite,
                                accentColor = accentColor,
                                onCardClick = { applyOrToggleShader(item) },
                                onToggleFavorite = {
                                    VideoStarRepository.toggleFavoriteAeEffect(item.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPackImporter) {
        PackImportStudioSheet(
            initialTab = "ALL",
            onDismiss = { showPackImporter = false },
            onApplyEffectItem = { effectId ->
                CustomPackRepository.findCatalogItem(effectId)?.let(::applyOrToggleShader)
            }
        )
    }
}

@Composable
private fun ShaderGalleryThumbCard(
    item: EffectCatalogItem,
    previewClip: MediaClip?,
    fallbackSceneThumb: androidx.compose.ui.graphics.ImageBitmap?,
    isApplied: Boolean,
    isFavorite: Boolean,
    accentColor: Color,
    onCardClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val gpuThumb = rememberShaderThumbnail(effectId = item.id, clip = previewClip)

    Surface(
        color = if (isApplied) accentColor.copy(alpha = 0.14f) else GalleryCardBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (isApplied) 1.8.dp else 1.dp,
            color = if (isApplied) accentColor else GalleryBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onCardClick)
            .testTag("shader_gallery_card_${item.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0D101B)),
                contentAlignment = Alignment.Center
            ) {
                if (gpuThumb != null) {
                    Image(
                        bitmap = gpuThumb,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    EffectAnimatedSampleCard(
                        label = "",
                        kind = SampleCardKind.Effect(item.id),
                        isSelected = isApplied,
                        sceneThumbnail = fallbackSceneThumb,
                        onClick = onCardClick,
                        clickable = false,
                        selectedAccentColor = accentColor,
                        labelColor = Color.Transparent
                    )
                }

                // Category badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                            )
                        )
                        .padding(horizontal = 5.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = item.category.shortLabel,
                        fontFamily = InterFontFamily,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFD5DAE8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Favorite button (48dp touch target wrapper or accessible hit area)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(onClick = onToggleFavorite)
                        .testTag("shader_gallery_fav_${item.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = "تفضيل ${item.name}",
                        tint = if (isFavorite) Color(0xFFFFC107) else Color(0xFFB5BCCE),
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (isApplied) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(accentColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "مطبّق",
                            tint = Color(0xFF06130C),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Text(
                text = item.arabicDisplayName(),
                fontFamily = CairoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = if (isApplied) accentColor else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = item.name,
                fontFamily = InterFontFamily,
                fontSize = 9.5.sp,
                color = GalleryMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
