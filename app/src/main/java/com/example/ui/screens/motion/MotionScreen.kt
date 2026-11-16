package com.example.ui.screens.motion

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.model.MotionPreset
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
import com.example.ui.theme.VideoStarInkMuted
import com.example.ui.theme.VideoStarLightToolbar
import com.example.ui.theme.VideoStarWhite
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MotionScreen(
    projectId: String,
    sceneIndex: Int = 0,
    onCancel: () -> Unit,
    onCreated: () -> Unit
) {
    val viewModel: MotionViewModel = viewModel(
        key = "motion_${projectId}_$sceneIndex",
        factory = remember(projectId, sceneIndex) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MotionViewModel(projectId, sceneIndex) as T
                }
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sceneThumbnail = rememberSceneSampleThumbnail(uiState.previewClip)

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
                                    .testTag("motion_cancel_button"),
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
                                text = "Motion",
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
                                    .iosPressable { viewModel.createAndSave(onCreated) }
                                    .padding(horizontal = 10.dp)
                                    .testTag("motion_create_button"),
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
                // Live True Aspect-Ratio Motion Preview Stage
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
                            .testTag("motion_preview_stage")
                    ) {
                        val preview = uiState.previewScene
                        if (preview != null) {
                            SceneRenderStage(
                                scene = preview,
                                localPositionMs = uiState.previewLocalMs,
                                isPlaying = uiState.isPlaying,
                                useLiveVideoPlayer = false,
                                contentDescription = "Motion Preview",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No scene selected",
                                    color = VideoStarInkMuted,
                                    fontFamily = SfProTextFontFamily,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = ClassicPlayerOverlay,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                        ) {
                            IconButton(
                                onClick = viewModel::togglePlayback,
                                modifier = Modifier.size(40.dp).testTag("motion_play_pause")
                            ) {
                                Icon(
                                    imageVector = if (uiState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = "Play or Pause",
                                    tint = VideoStarWhite
                                )
                            }
                        }
                    }
                }

                // Bottom iOS Motion Dock: Animated Preset Sample Cards + Slim Speed & Amount Sliders
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
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = MotionPreset.entries,
                                key = { it.name }
                            ) { preset ->
                                EffectAnimatedSampleCard(
                                    label = preset.label,
                                    kind = SampleCardKind.Motion(preset.name),
                                    isSelected = uiState.preset == preset,
                                    sceneThumbnail = sceneThumbnail,
                                    onClick = { viewModel.selectPreset(preset) },
                                    testTag = "motion_preset_${preset.name.lowercase()}"
                                )
                            }
                        }

                        IosHairlineDivider()

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
                                    text = String.format(Locale.US, "Speed: %.2fx", uiState.speed),
                                    fontFamily = SfProDisplayFontFamily,
                                    color = AdjustVideoTitleDark,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Reset",
                                    fontFamily = SfProTextFontFamily,
                                    color = ClassicRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .iosPressable(onClick = viewModel::resetMotion)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            IosThinSlider(
                                value = uiState.speed,
                                onValueChange = viewModel::updateSpeed,
                                valueRange = 0.2f..3.0f,
                                activeTrackColor = ClassicGreen,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .testTag("motion_speed_slider")
                            )

                            IosHairlineDivider()

                            Text(
                                text = "Amount: ${(uiState.intensity * 100f).roundToInt()}%",
                                fontFamily = SfProDisplayFontFamily,
                                color = AdjustVideoTitleDark,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            IosThinSlider(
                                value = uiState.intensity,
                                onValueChange = viewModel::updateIntensity,
                                valueRange = 0f..1f,
                                enabled = uiState.preset != MotionPreset.NONE,
                                activeTrackColor = ClassicRed,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .testTag("motion_intensity_slider")
                            )
                        }
                    }
                }
            }
        }
    }
}
