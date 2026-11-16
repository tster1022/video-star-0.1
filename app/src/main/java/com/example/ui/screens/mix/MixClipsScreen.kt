package com.example.ui.screens.mix

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.TransitionCatalog
import com.example.model.TransitionCategory
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
import java.util.Locale

@Composable
fun MixClipsScreen(
    projectId: String,
    sceneIndex: Int = 0,
    onCancel: () -> Unit,
    onCreated: () -> Unit
) {
    val viewModel: MixViewModel = viewModel(
        key = "mix_${projectId}_$sceneIndex",
        factory = remember(projectId, sceneIndex) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MixViewModel(projectId, sceneIndex) as T
                }
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sceneThumbnail = rememberSceneSampleThumbnail(uiState.outgoingClip)

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
                                    .testTag("mix_cancel_button"),
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
                                text = "Mix Clips",
                                color = AdjustVideoTitleDark,
                                fontFamily = SfProDisplayFontFamily,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .height(48.dp)
                                    .iosPressable(enabled = uiState.hasValidPair) { viewModel.createAndSave(onCreated) }
                                    .padding(horizontal = 10.dp)
                                    .testTag("mix_create_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Create",
                                    color = if (uiState.hasValidPair) ClassicGreen else VideoStarInkMuted,
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
                // Dual-Scene True Aspect-Ratio Transition Preview Stage
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
                            .testTag("mix_preview_stage"),
                        contentAlignment = Alignment.Center
                    ) {
                        val preview = uiState.previewScene
                        val next = uiState.incomingScene
                        if (preview != null && next != null) {
                            SceneRenderStage(
                                scene = preview,
                                nextScene = next,
                                localPositionMs = uiState.previewLocalPositionMs,
                                isPlaying = uiState.isPlaying,
                                useLiveVideoPlayer = false,
                                contentDescription = "Transition Preview",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = "Select a scene that has another scene after it.",
                                color = VideoStarWhite,
                                fontFamily = SfProTextFontFamily,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }
                }

                // Translucent Gray iOS Transition Playback Scrubber Bar
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = viewModel::togglePlayback,
                            modifier = Modifier.size(44.dp).testTag("mix_play_pause")
                        ) {
                            Icon(
                                imageVector = if (uiState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "Play or Pause",
                                tint = VideoStarWhite
                            )
                        }
                        IosThinSlider(
                            value = uiState.previewProgress,
                            onValueChange = viewModel::scrubTransition,
                            activeTrackColor = VideoStarWhite,
                            inactiveTrackColor = VideoStarWhite.copy(alpha = 0.35f),
                            modifier = Modifier.weight(1f).testTag("mix_preview_slider")
                        )
                        IconButton(
                            onClick = viewModel::toggleDrawer,
                            modifier = Modifier.size(44.dp).testTag("mix_drawer_toggle")
                        ) {
                            Icon(
                                imageVector = if (uiState.isDrawerExpanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                                contentDescription = "Toggle Transition Drawer",
                                tint = VideoStarWhite
                            )
                        }
                    }
                }

                // Expandable iOS Drawer: 4 Clean Tabs + Live Transition Sample Cards + Slim Duration Slider
                AnimatedVisibility(visible = uiState.isDrawerExpanded) {
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
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 4 Clean Non-Truncated Category Tabs
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TransitionCategory.entries.forEach { cat ->
                                    val selected = uiState.selectedCategory == cat
                                    val weightVal = when (cat) {
                                        TransitionCategory.FLIP_3D -> 0.80f
                                        TransitionCategory.MOTION_BLUR -> 1.06f
                                        TransitionCategory.LIGHT_LEAK -> 1.08f
                                        TransitionCategory.COLOR_MORPH -> 1.06f
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (selected) ClassicGreen else Color(0xFFF2F2F7),
                                        border = BorderStroke(0.5.dp, if (selected) ClassicGreen else IosHairlineGray),
                                        modifier = Modifier
                                            .weight(weightVal)
                                            .height(34.dp)
                                            .iosPressable { viewModel.selectCategory(cat) }
                                            .testTag("mix_cat_${cat.name.lowercase()}")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = cat.label,
                                                fontFamily = SfProTextFontFamily,
                                                color = if (selected) VideoStarWhite else VideoStarInkDark,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }

                            // Live Animated Transition Sample Cards (LazyRow)
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(
                                    items = TransitionCatalog.byCategory(uiState.selectedCategory),
                                    key = { it.id }
                                ) { item ->
                                    EffectAnimatedSampleCard(
                                        label = item.name,
                                        kind = SampleCardKind.Transition(item.id),
                                        isSelected = uiState.selectedTransition.id == item.id,
                                        sceneThumbnail = sceneThumbnail,
                                        onClick = { viewModel.selectTransition(item) },
                                        testTag = "mix_item_${item.id}"
                                    )
                                }
                            }

                            IosHairlineDivider()

                            // Slim Duration Slider + Easing Curve Chips
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
                                        text = String.format(Locale.US, "Duration: %.2fs", uiState.durationMs / 1000f),
                                        fontFamily = SfProDisplayFontFamily,
                                        color = AdjustVideoTitleDark,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf("Smooth", "Linear", "Sharp", "Bounce").forEach { ease ->
                                            val active = uiState.easing == ease
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (active) ClassicGreen else Color(0xFFF2F2F7),
                                                modifier = Modifier
                                                    .height(26.dp)
                                                    .iosPressable { viewModel.selectEasing(ease) }
                                            ) {
                                                Box(
                                                    modifier = Modifier.padding(horizontal = 8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = ease,
                                                        fontFamily = SfProTextFontFamily,
                                                        color = if (active) VideoStarWhite else VideoStarInkDark,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                IosThinSlider(
                                    value = (uiState.durationMs / 1000f).coerceIn(0.2f, 2.0f),
                                    onValueChange = viewModel::updateDurationSeconds,
                                    valueRange = 0.2f..2.0f,
                                    activeTrackColor = ClassicGreen,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(34.dp)
                                        .testTag("mix_duration_slider")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
