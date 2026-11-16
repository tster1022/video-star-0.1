package com.example.ui.screens.effects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MovieFilter
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.EffectStudioTab
import com.example.ui.components.EffectAnimatedSampleCard
import com.example.ui.components.IosHairlineDivider
import com.example.ui.components.IosThinSlider
import com.example.ui.components.SampleCardKind
import com.example.ui.components.SceneRenderStage
import com.example.ui.components.geometricTriangleBackground
import com.example.ui.components.iosPressable
import com.example.ui.components.rememberSceneSampleThumbnail
import com.example.ui.theme.AdjustVideoTitleDark
import com.example.ui.theme.ClassicGreen
import com.example.ui.theme.ClassicPlayerOverlay
import com.example.ui.theme.ClassicRed
import com.example.ui.theme.IosHairlineGray
import com.example.ui.theme.SfProDisplayFontFamily
import com.example.ui.theme.SfProTextFontFamily
import com.example.ui.theme.VideoStarInkDark
import com.example.ui.theme.VideoStarInkMuted
import com.example.ui.theme.VideoStarLightToolbar
import com.example.ui.theme.VideoStarWhite
import kotlin.math.roundToInt

@Composable
fun EffectsStudioScreen(
    projectId: String,
    sceneIndices: List<Int> = listOf(0),
    targetLayerId: String? = null,
    onCancel: () -> Unit,
    onCreated: () -> Unit
) {
    val keySuffix = "${projectId}_${sceneIndices.joinToString("_")}_${targetLayerId ?: "scene"}"
    val viewModel: EffectsStudioViewModel = viewModel(
        key = "effects_studio_$keySuffix",
        factory = remember(keySuffix) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return EffectsStudioViewModel(projectId, sceneIndices, targetLayerId) as T
                }
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sceneThumbnail = rememberSceneSampleThumbnail(uiState.previewClip)
    val topTitle = uiState.activePass?.effectName
        ?: when {
            uiState.targetLayerId != null -> "Layer Effects"
            uiState.targetSceneIndices.size > 1 -> "Re-Effect (${uiState.targetSceneIndices.size})"
            else -> "Effects Studio"
        }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Surface(color = VideoStarLightToolbar, shadowElevation = 0.dp) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(48.dp)
                                    .iosPressable(onClick = onCancel)
                                    .padding(horizontal = 10.dp)
                                    .testTag("reeffect_cancel_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Cancel",
                                    color = ClassicRed,
                                    fontFamily = SfProTextFontFamily,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                            Text(
                                text = topTitle,
                                color = AdjustVideoTitleDark,
                                fontFamily = SfProDisplayFontFamily,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .height(48.dp)
                                    .iosPressable { viewModel.createAndSave(onCreated) }
                                    .padding(horizontal = 10.dp)
                                    .testTag("reeffect_create_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Create",
                                    color = ClassicGreen,
                                    fontFamily = SfProDisplayFontFamily,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        IosHairlineDivider()
                    }
                }
            }
        }
    ) { innerPadding ->
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .geometricTriangleBackground()
                    .padding(innerPadding)
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Live True Aspect-Ratio Preview Stage
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val availW = maxWidth
                    val availH = maxHeight
                    val targetRatio = uiState.aspectRatio.ratio.coerceAtLeast(0.25f)
                    val availRatio = if (availH > 0.dp) availW / availH else targetRatio
                    val (stageW, stageH) = if (availRatio > targetRatio) {
                        (availH * targetRatio) to availH
                    } else {
                        availW to (availW / targetRatio)
                    }

                    Box(
                        modifier = Modifier
                            .size(width = stageW, height = stageH)
                            .shadow(elevation = 4.dp, shape = RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                            .border(0.5.dp, IosHairlineGray, RoundedCornerShape(8.dp))
                            .iosPressable(onClick = viewModel::togglePlayback)
                            .testTag("reeffect_preview_stage")
                    ) {
                        val preview = uiState.previewScene
                        if (preview != null) {
                            SceneRenderStage(
                                scene = preview,
                                localPositionMs = uiState.previewLocalMs,
                                isPlaying = uiState.isPlaying,
                                useLiveVideoPlayer = true,
                                contentDescription = "Effects Studio Preview",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No active scene or layer selected",
                                    color = VideoStarInkMuted,
                                    fontFamily = SfProTextFontFamily,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // Translucent Gray iOS Playback & Keyframe Scrubber Bar
                val activePass = uiState.activePass
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ClassicPlayerOverlay,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = viewModel::togglePlayback,
                            modifier = Modifier.size(44.dp).testTag("reeffect_play_pause")
                        ) {
                            Icon(
                                imageVector = if (uiState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "Play or Pause",
                                tint = VideoStarWhite
                            )
                        }

                        Box(
                            modifier = Modifier.weight(1f).height(44.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxWidth().height(16.dp).padding(horizontal = 8.dp)) {
                                val kfs = activePass?.keyframes ?: emptyList()
                                kfs.forEach { kf ->
                                    val x = kf.timeProgress.coerceIn(0f, 1f) * size.width
                                    val cy = size.height / 2f
                                    val r = 4.5.dp.toPx()
                                    val path = Path().apply {
                                        moveTo(x, cy - r)
                                        lineTo(x + r, cy)
                                        lineTo(x, cy + r)
                                        lineTo(x - r, cy)
                                        close()
                                    }
                                    drawPath(path, color = ClassicRed)
                                }
                            }
                            IosThinSlider(
                                value = uiState.previewProgress,
                                onValueChange = viewModel::seekToProgress,
                                valueRange = 0f..1f,
                                activeTrackColor = VideoStarWhite,
                                inactiveTrackColor = VideoStarWhite.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth().testTag("effects_time_scrubber")
                            )
                        }

                        // Keyframe Diamond Toggle Pill
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = if (uiState.hasKeyframeNearCurrentTime) ClassicRed else Color.White.copy(alpha = 0.2f),
                            modifier = Modifier
                                .height(32.dp)
                                .padding(end = 6.dp)
                                .iosPressable(enabled = activePass != null, onClick = viewModel::toggleKeyframeAtCurrentTime)
                                .testTag("effects_keyframe_toggle")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Diamond,
                                    contentDescription = "Toggle Intensity Keyframe",
                                    tint = VideoStarWhite,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "KF",
                                    fontFamily = SfProDisplayFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VideoStarWhite
                                )
                            }
                        }
                    }
                }

                // Bottom iOS Effects Dock
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = VideoStarWhite,
                    shadowElevation = 2.dp,
                    border = BorderStroke(0.5.dp, IosHairlineGray),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Row 1: Search 🔍, + Add Pass, Active Stack Chips, Clear
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (uiState.isSearchVisible) ClassicRed else Color(0xFFF2F2F7),
                                border = BorderStroke(0.5.dp, if (uiState.isSearchVisible) ClassicRed else IosHairlineGray),
                                modifier = Modifier
                                    .size(34.dp)
                                    .iosPressable(onClick = viewModel::toggleSearch)
                                    .testTag("reeffect_search_toggle")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Search,
                                        contentDescription = "Search Effects",
                                        tint = if (uiState.isSearchVisible) VideoStarWhite else ClassicRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ClassicGreen,
                                modifier = Modifier
                                    .height(34.dp)
                                    .iosPressable(onClick = viewModel::addPass)
                                    .testTag("effects_add_pass_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = "Add Shader Pass", tint = VideoStarWhite, modifier = Modifier.size(16.dp))
                                    Text("Pass", fontFamily = SfProTextFontFamily, color = VideoStarWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (uiState.passes.isEmpty()) {
                                    Text(
                                        text = "Tap a shader preview below",
                                        fontFamily = SfProTextFontFamily,
                                        fontSize = 11.sp,
                                        color = VideoStarInkMuted,
                                        maxLines = 1
                                    )
                                } else {
                                    uiState.passes.forEachIndexed { idx, pass ->
                                        val selected = idx == uiState.selectedPassIndex
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (selected) AdjustVideoTitleDark else Color(0xFFF2F2F7),
                                            border = BorderStroke(0.5.dp, if (selected) ClassicGreen else IosHairlineGray),
                                            modifier = Modifier
                                                .height(32.dp)
                                                .iosPressable { viewModel.selectPass(idx) }
                                                .testTag("effects_pass_chip_$idx")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(start = 8.dp, end = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = "${idx + 1}. ${pass.effectName}",
                                                    fontFamily = SfProTextFontFamily,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (selected) VideoStarWhite else VideoStarInkDark,
                                                    maxLines = 1
                                                )
                                                IconButton(
                                                    onClick = { viewModel.removePass(idx) },
                                                    modifier = Modifier.size(24.dp).testTag("effects_remove_pass_$idx")
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.Close,
                                                        contentDescription = "Remove Pass",
                                                        tint = if (selected) Color(0xFFFF8A80) else ClassicRed,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (uiState.passes.isNotEmpty()) {
                                Text(
                                    text = "Clear",
                                    color = ClassicRed,
                                    fontFamily = SfProTextFontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .iosPressable(onClick = viewModel::resetEffects)
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                        .testTag("effects_clear_all")
                                )
                            }
                        }

                        if (uiState.isSearchVisible) {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = viewModel::updateSearchQuery,
                                singleLine = true,
                                placeholder = { Text("Search 35 GPU shaders...", fontFamily = SfProTextFontFamily, fontSize = 13.sp) },
                                trailingIcon = {
                                    IconButton(onClick = viewModel::toggleSearch) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Close Search")
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp)
                                    .testTag("reeffect_search_input")
                            )
                        }

                        IosHairlineDivider()

                        // Row 2: 5 Clean Category Tabs (All, Color, Layout, Motion, VHS & Glitch)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EffectStudioTab.entries.forEach { tab ->
                                val selected = uiState.selectedTab == tab && uiState.searchQuery.isBlank()
                                val weightVal = if (tab == EffectStudioTab.VHS_GLITCH) 1.28f else 0.93f
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .weight(weightVal)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) ClassicGreen.copy(alpha = 0.1f) else Color.Transparent)
                                        .iosPressable { viewModel.selectTab(tab) }
                                        .testTag("reeffect_tab_${tab.name.lowercase()}")
                                ) {
                                    Icon(
                                        imageVector = studioTabIcon(tab),
                                        contentDescription = tab.label,
                                        tint = if (selected) ClassicGreen else VideoStarInkMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = tab.label,
                                        fontFamily = SfProTextFontFamily,
                                        fontSize = 10.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) ClassicGreen else VideoStarInkMuted,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        // Row 3: Live Animated Thumbnail Sample Cards (LazyRow)
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = uiState.displayedEffects,
                                key = { it.id }
                            ) { item ->
                                EffectAnimatedSampleCard(
                                    label = item.name,
                                    kind = SampleCardKind.Effect(item.id),
                                    isSelected = uiState.activePass?.effectId == item.id,
                                    sceneThumbnail = sceneThumbnail,
                                    onClick = { viewModel.selectEffect(item) },
                                    testTag = "reeffect_item_${item.id}"
                                )
                            }
                        }

                        IosHairlineDivider()

                        // Row 4: Slim iOS Parameter Sliders (Intensity + active shader sub-params)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${activePass?.effectName ?: "Intensity"}: ${(uiState.activeEvaluatedIntensity * 100f).roundToInt()}%",
                                    fontFamily = SfProDisplayFontFamily,
                                    color = AdjustVideoTitleDark,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${uiState.displayedEffects.size} Shaders",
                                    fontFamily = SfProTextFontFamily,
                                    color = VideoStarInkMuted,
                                    fontSize = 11.sp
                                )
                            }
                            IosThinSlider(
                                value = uiState.activeEvaluatedIntensity,
                                onValueChange = viewModel::updateIntensity,
                                valueRange = 0f..1f,
                                enabled = activePass != null,
                                activeTrackColor = ClassicGreen,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(32.dp)
                                    .testTag("reeffect_intensity_slider")
                            )

                            uiState.activeCatalogItem.params.take(2).forEach { param ->
                                val currentVal = activePass?.param(param.key, param.defaultValue) ?: param.defaultValue
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = param.label,
                                        fontFamily = SfProTextFontFamily,
                                        fontSize = 11.sp,
                                        color = VideoStarInkMuted,
                                        modifier = Modifier.width(86.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    IosThinSlider(
                                        value = currentVal,
                                        onValueChange = { viewModel.updatePassParam(param.key, it) },
                                        valueRange = param.minValue..param.maxValue,
                                        enabled = activePass != null,
                                        activeTrackColor = ClassicRed,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(30.dp)
                                            .testTag("effects_param_${param.key}")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun studioTabIcon(tab: EffectStudioTab): ImageVector = when (tab) {
    EffectStudioTab.ALL -> Icons.Outlined.Apps
    EffectStudioTab.COLOR -> Icons.Outlined.ColorLens
    EffectStudioTab.LAYOUT -> Icons.Outlined.GridView
    EffectStudioTab.MOTION -> Icons.Outlined.Animation
    EffectStudioTab.VHS_GLITCH -> Icons.Outlined.MovieFilter
}
