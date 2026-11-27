package com.example.ui.screens.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.ae.AeEffectDefinition
import com.example.engine.ae.AeEffectEngineCatalog
import com.example.engine.ae.AeEffectSource
import com.example.engine.ae.AeEngineCategory
import com.example.engine.packs.CustomPackRepository
import com.example.model.MediaClip
import com.example.ui.screens.packs.PackImportStudioSheet
import com.example.ui.theme.InterFontFamily
import kotlinx.coroutines.delay

private val BrowserBgNavy = Color(0xFF0D101B)
private val BrowserSubScreenSlate = Color(0xFF292E46)
private val BrowserCardBg = Color(0xFF151927)
private val BrowserChipBg = Color(0xFF181D2B)
private val BrowserSubChipBg = Color(0xFF20253A)
private val BrowserBorderSubtle = Color(0xFF23283A)
private val BrowserNeonGreen = Color(0xFF00E676)
private val BrowserAeDotPurple = Color(0xFF7C5CFF)
private val BrowserMutedText = Color(0xFF9EA6BC)
private val BrowserStarGold = Color(0xFFFACC15)

private enum class AeBrowserOriginFilter {
    ALL,
    AFTER_EFFECTS,
    ALIGHT_MOTION
}

@Composable
internal fun AeEffectsBrowserView(
    @Suppress("UNUSED_PARAMETER") previewClip: MediaClip?,
    appliedEffectIds: Set<String>,
    favoriteEffectIds: Set<String>,
    initialCategoryId: String? = null,
    onToggleFavorite: (String) -> Boolean,
    onBackToChain: () -> Unit,
    onSelectEffect: (AeEffectDefinition) -> Unit
) {
    val customShaders by CustomPackRepository.importedShaders.collectAsStateWithLifecycle()
    val customPresets by CustomPackRepository.importedPresets.collectAsStateWithLifecycle()
    val allAeEffects = remember(customShaders, customPresets) { AeEffectEngineCatalog.allEffects }
    val categories = remember(allAeEffects) { AeEffectEngineCatalog.navigableCategories() }

    var selectedCategory by remember(initialCategoryId) {
        mutableStateOf(
            initialCategoryId?.let { id -> categories.firstOrNull { it.id == id } }
        )
    }
    var showAllEffectsFlat by remember { mutableStateOf(false) }
    var originFilter by remember { mutableStateOf(AeBrowserOriginFilter.ALL) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var favoriteToastText by remember { mutableStateOf<String?>(null) }
    var showPackImporter by remember { mutableStateOf(false) }

    LaunchedEffect(favoriteToastText) {
        if (favoriteToastText != null) {
            delay(1800L)
            favoriteToastText = null
        }
    }

    val isEffectGridLevel = selectedCategory != null ||
        showAllEffectsFlat ||
        originFilter != AeBrowserOriginFilter.ALL ||
        searchQuery.isNotBlank()

    val baseEffectsForScope = remember(allAeEffects, selectedCategory, favoriteEffectIds) {
        when (val cat = selectedCategory) {
            null -> allAeEffects
            AeEngineCategory.MY_EFFECTS -> allAeEffects.filter {
                it.category == AeEngineCategory.MY_EFFECTS || it.id in favoriteEffectIds
            }
            else -> allAeEffects.filter { it.category == cat }
        }
    }

    val allScopeCount = baseEffectsForScope.size
    val aeScopeCount = remember(baseEffectsForScope) {
        baseEffectsForScope.count { it.source == AeEffectSource.AFTER_EFFECTS }
    }
    val amScopeCount = remember(baseEffectsForScope) {
        baseEffectsForScope.count { it.source == AeEffectSource.ALIGHT_MOTION }
    }

    val filteredEffects = remember(baseEffectsForScope, originFilter, searchQuery) {
        val byOrigin = when (originFilter) {
            AeBrowserOriginFilter.ALL -> baseEffectsForScope
            AeBrowserOriginFilter.AFTER_EFFECTS -> baseEffectsForScope.filter {
                it.source == AeEffectSource.AFTER_EFFECTS
            }
            AeBrowserOriginFilter.ALIGHT_MOTION -> baseEffectsForScope.filter {
                it.source == AeEffectSource.ALIGHT_MOTION
            }
        }
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) byOrigin
        else byOrigin.filter { def ->
            def.name.lowercase().contains(q) ||
                def.id.lowercase().contains(q) ||
                def.description.lowercase().contains(q) ||
                def.category.title.lowercase().contains(q)
        }
    }

    val handleBack = {
        if (selectedCategory != null || showAllEffectsFlat || originFilter != AeBrowserOriginFilter.ALL || showSearch) {
            selectedCategory = null
            showAllEffectsFlat = false
            originFilter = AeBrowserOriginFilter.ALL
            showSearch = false
            searchQuery = ""
        } else {
            onBackToChain()
        }
    }

    Dialog(
        onDismissRequest = handleBack,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler(enabled = true) { handleBack() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isEffectGridLevel) BrowserSubScreenSlate else BrowserBgNavy)
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("ae_effects_browser_sheet")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(onClick = handleBack)
                            .testTag("ae_browser_back_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isEffectGridLevel) {
                                Icons.AutoMirrored.Rounded.ArrowBack
                            } else {
                                Icons.Rounded.Close
                            },
                            contentDescription = if (isEffectGridLevel) "Back to Categories" else "Close Effects Browser",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Text(
                        text = when {
                            selectedCategory != null -> selectedCategory!!.title
                            showAllEffectsFlat -> "All effects"
                            originFilter == AeBrowserOriginFilter.AFTER_EFFECTS -> "After Effects"
                            originFilter == AeBrowserOriginFilter.ALIGHT_MOTION -> "Alight Motion"
                            else -> "Effects browser"
                        },
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable {
                                showSearch = !showSearch
                                if (!showSearch) searchQuery = ""
                            }
                            .testTag("ae_effects_search_toggle"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (showSearch) Icons.Rounded.Close else Icons.Outlined.Search,
                            contentDescription = "Search AE Effects",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                if (showSearch) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        placeholder = {
                            Text(
                                "Search ${allAeEffects.size} AE/AM effects...",
                                fontFamily = InterFontFamily,
                                fontSize = 13.sp,
                                color = BrowserMutedText
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = BrowserNeonGreen,
                            unfocusedBorderColor = BrowserBorderSubtle,
                            focusedContainerColor = BrowserCardBg,
                            unfocusedContainerColor = BrowserCardBg
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .testTag("ae_effects_search_input")
                    )
                }

                // Filter Chips Row: All | • After Effects | • Alight Motion
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AeFilterPill(
                        label = "All",
                        count = allScopeCount,
                        dotColor = null,
                        isSelected = originFilter == AeBrowserOriginFilter.ALL,
                        chipBg = if (isEffectGridLevel) BrowserSubChipBg else BrowserChipBg,
                        testTag = "ae_filter_chip_all",
                        onClick = {
                            if (originFilter == AeBrowserOriginFilter.ALL && selectedCategory == null) {
                                showAllEffectsFlat = !showAllEffectsFlat
                            } else {
                                originFilter = AeBrowserOriginFilter.ALL
                            }
                        }
                    )
                    AeFilterPill(
                        label = "After Effects",
                        count = aeScopeCount,
                        dotColor = BrowserAeDotPurple,
                        isSelected = originFilter == AeBrowserOriginFilter.AFTER_EFFECTS,
                        chipBg = if (isEffectGridLevel) BrowserSubChipBg else BrowserChipBg,
                        testTag = "ae_filter_chip_ae",
                        onClick = {
                            originFilter = if (originFilter == AeBrowserOriginFilter.AFTER_EFFECTS) {
                                AeBrowserOriginFilter.ALL
                            } else {
                                AeBrowserOriginFilter.AFTER_EFFECTS
                            }
                        }
                    )
                    AeFilterPill(
                        label = "Alight Motion",
                        count = amScopeCount,
                        dotColor = BrowserNeonGreen,
                        isSelected = originFilter == AeBrowserOriginFilter.ALIGHT_MOTION,
                        chipBg = if (isEffectGridLevel) BrowserSubChipBg else BrowserChipBg,
                        testTag = "ae_filter_chip_am",
                        onClick = {
                            originFilter = if (originFilter == AeBrowserOriginFilter.ALIGHT_MOTION) {
                                AeBrowserOriginFilter.ALL
                            } else {
                                AeBrowserOriginFilter.ALIGHT_MOTION
                            }
                        }
                    )
                    AeFilterPill(
                        label = "+ XML / Pack",
                        count = customPresets.size + customShaders.size,
                        dotColor = BrowserAeDotPurple,
                        isSelected = false,
                        chipBg = if (isEffectGridLevel) BrowserSubChipBg else BrowserChipBg,
                        testTag = "ae_filter_import_packs",
                        onClick = { showPackImporter = true }
                    )
                }

                if (!isEffectGridLevel) {
                    // LEVEL 1: 2-Column Stylized Gradient Category Hub (#0D101B)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("ae_effects_categories_grid"),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories, key = { it.id }) { cat ->
                            val count = if (cat == AeEngineCategory.MY_EFFECTS) {
                                allAeEffects.count {
                                    it.category == AeEngineCategory.MY_EFFECTS || it.id in favoriteEffectIds
                                }
                            } else {
                                allAeEffects.count { it.category == cat }
                            }
                            AeCategoryBannerCard(
                                category = cat,
                                count = count,
                                onClick = {
                                    selectedCategory = cat
                                    showAllEffectsFlat = false
                                    originFilter = AeBrowserOriginFilter.ALL
                                }
                            )
                        }

                        item(span = { GridItemSpan(2) }) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 14.dp, bottom = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${allAeEffects.size} effects in the After Effects catalog",
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.sp,
                                    color = BrowserMutedText,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Hold an effect and tap the ★ to save it to My effects",
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.sp,
                                    color = BrowserMutedText,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    // LEVEL 2: Classic 3-Column Square Illustrated Tiles (#292E46) + Bottom < > Category Pager
                    if (filteredEffects.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = null,
                                    tint = BrowserMutedText.copy(alpha = 0.6f),
                                    modifier = Modifier.size(34.dp)
                                )
                                Text(
                                    text = if (selectedCategory == AeEngineCategory.MY_EFFECTS) {
                                        "No favorite effects yet.\nHold any effect tile or tap ★ to save it here."
                                    } else {
                                        "No effects matched your filter in this category."
                                    },
                                    fontFamily = InterFontFamily,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    color = BrowserMutedText,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("ae_effects_browser_grid"),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(filteredEffects, key = { it.id }) { def ->
                                val isApplied = def.id in appliedEffectIds
                                val isFav = def.id in favoriteEffectIds
                                AeClassicSquareTile(
                                    definition = def,
                                    isApplied = isApplied,
                                    isFavorite = isFav,
                                    onSelect = { onSelectEffect(def) },
                                    onToggleFavorite = {
                                        val added = onToggleFavorite(def.id)
                                        favoriteToastText = if (added) {
                                            "Saved ${def.name} to My effects ★"
                                        } else {
                                            "Removed ${def.name} from My effects"
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Bottom Sequential Category Pagination Footer (<   Category   >) matching reference screenshots
                    val currentCat = selectedCategory ?: categories.first()
                    val currentIndex = categories.indexOf(currentCat).coerceAtLeast(0)
                    val prevCat = categories[(currentIndex - 1 + categories.size) % categories.size]
                    val nextCat = categories[(currentIndex + 1) % categories.size]

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .background(Color(0xFF23283E))
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable {
                                    selectedCategory = prevCat
                                    showAllEffectsFlat = false
                                    originFilter = AeBrowserOriginFilter.ALL
                                }
                                .testTag("ae_category_prev_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                contentDescription = "Previous Category",
                                tint = Color.White.copy(alpha = 0.88f),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = currentCat.title,
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = Color.White,
                                maxLines = 1
                            )
                            Text(
                                text = "${currentIndex + 1} / ${categories.size}",
                                fontFamily = InterFontFamily,
                                fontSize = 10.sp,
                                color = BrowserMutedText
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable {
                                    selectedCategory = nextCat
                                    showAllEffectsFlat = false
                                    originFilter = AeBrowserOriginFilter.ALL
                                }
                                .testTag("ae_category_next_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "Next Category",
                                tint = Color.White.copy(alpha = 0.88f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }

            val toast = favoriteToastText
            if (toast != null) {
                Surface(
                    color = Color(0xFF1A2234),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, BrowserStarGold.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 62.dp, start = 16.dp, end = 16.dp)
                ) {
                    Text(
                        text = toast,
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }

    if (showPackImporter) {
        PackImportStudioSheet(
            initialTab = "ALL",
            onDismiss = { showPackImporter = false },
            onApplyEffectItem = { effectId ->
                AeEffectEngineCatalog.findById(effectId)?.let(onSelectEffect)
            }
        )
    }
}

@Composable
private fun AeFilterPill(
    label: String,
    count: Int,
    dotColor: Color?,
    isSelected: Boolean,
    chipBg: Color,
    testTag: String,
    onClick: () -> Unit
) {
    val bgColor = when {
        isSelected && dotColor == null -> BrowserNeonGreen
        isSelected && dotColor != null -> dotColor.copy(alpha = 0.24f)
        else -> chipBg
    }
    val borderColor = when {
        isSelected && dotColor == null -> BrowserNeonGreen
        isSelected && dotColor != null -> dotColor
        else -> BrowserBorderSubtle
    }
    val textColor = if (isSelected && dotColor == null) Color(0xFF04140B) else Color(0xFFE5E7EB)
    val countColor = if (isSelected && dotColor == null) Color(0xFF04140B).copy(alpha = 0.78f) else BrowserMutedText

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
            Text(
                text = label,
                fontFamily = InterFontFamily,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp,
                color = textColor,
                maxLines = 1
            )
            Text(
                text = count.toString(),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = countColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AeCategoryBannerCard(
    category: AeEngineCategory,
    count: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(category.gradStart, category.gradEnd)
                )
            )
            .border(1.dp, BrowserBorderSubtle.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("ae_category_card_${category.id}")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val accent = category.accentColor
            when (category) {
                AeEngineCategory.MY_EFFECTS,
                AeEngineCategory.TRANSFORM,
                AeEngineCategory.OPACITY,
                AeEngineCategory.TRANSITION -> Unit

                AeEngineCategory.TIME -> {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.08f),
                        radius = h * 0.65f,
                        center = Offset(w * 0.88f, h * 0.25f)
                    )
                }

                AeEngineCategory.COLOR, AeEngineCategory.BLUR, AeEngineCategory.STYLIZE, AeEngineCategory.DRAWING_EDGE -> {
                    drawLine(
                        color = accent.copy(alpha = 0.24f),
                        start = Offset(0f, h * 0.85f),
                        end = Offset(w, h * 0.15f),
                        strokeWidth = 10.dp.toPx()
                    )
                    drawCircle(
                        color = accent.copy(alpha = 0.16f),
                        radius = h * 0.55f,
                        center = Offset(w * 0.2f, h * 0.35f)
                    )
                }

                AeEngineCategory.DISTORTION, AeEngineCategory.PROCEDURAL, AeEngineCategory.REPEAT -> {
                    for (i in 0..4) {
                        val rx = w * (0.2f + i * 0.16f)
                        drawCircle(
                            color = accent.copy(alpha = 0.14f),
                            radius = h * 0.32f,
                            center = Offset(rx, h * 0.5f),
                            style = Stroke(width = 1.4.dp.toPx())
                        )
                    }
                }

                AeEngineCategory.THREE_D, AeEngineCategory.CUTOUT, AeEngineCategory.TEXT, AeEngineCategory.OTHER -> {
                    drawRect(
                        color = accent.copy(alpha = 0.18f),
                        topLeft = Offset(w * 0.10f, h * 0.20f),
                        size = Size(h * 0.55f, h * 0.55f),
                        style = Stroke(width = 1.8.dp.toPx())
                    )
                    drawCircle(
                        color = accent.copy(alpha = 0.14f),
                        radius = h * 0.45f,
                        center = Offset(w * 0.85f, h * 0.3f)
                    )
                }
            }
        }

        Text(
            text = category.bannerTitle,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 12.dp)
        )

        Text(
            text = count.toString(),
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            color = Color(0xFFD5DCE0),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = 7.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AeClassicSquareTile(
    definition: AeEffectDefinition,
    isApplied: Boolean,
    isFavorite: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onSelect,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleFavorite()
                }
            )
            .testTag("ae_effect_browser_item_${definition.id}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = if (isApplied) 2.dp else 1.dp,
                    color = if (isApplied) BrowserNeonGreen else Color.White.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(10.dp)
                )
        ) {
            AeTileIllustration(
                definition = definition,
                modifier = Modifier.fillMaxSize()
            )

            // Top-left subtle origin badge ("Ae" / "AM")
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.56f))
                    .padding(horizontal = 5.dp, vertical = 1.5.dp)
            ) {
                Text(
                    text = definition.source.badgeLabel,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = if (definition.source == AeEffectSource.AFTER_EFFECTS) {
                        Color(0xFFC4B5FD)
                    } else {
                        BrowserNeonGreen
                    }
                )
            }

            // Top-right ★ Favorite button
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = if (isFavorite) 0.58f else 0.25f))
                    .clickable { onToggleFavorite() }
                    .testTag("ae_effect_star_${definition.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = if (isFavorite) "Remove from My effects" else "Save to My effects",
                    tint = if (isFavorite) BrowserStarGold else Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.size(14.dp)
                )
            }

            if (isApplied) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(BrowserNeonGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Active",
                        tint = Color(0xFF04140B),
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = definition.name,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            color = if (isApplied) BrowserNeonGreen else Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
