package com.example.ui.screens.timing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.model.AspectRatioMode
import com.example.model.TimingMode
import com.example.ui.components.IosSegmentedControl
import com.example.ui.components.SceneRenderStage
import com.example.ui.components.geometricTriangleBackground
import com.example.ui.theme.AdjustVideoTitleDark
import com.example.ui.theme.ClassicGreen
import com.example.ui.theme.ClassicRed
import com.example.ui.theme.VideoStarInkDark
import com.example.ui.theme.VideoStarInkMuted
import com.example.ui.theme.VideoStarIosDivider
import com.example.ui.theme.VideoStarLightToolbar
import com.example.ui.theme.VideoStarWhite
import java.util.Locale

@Composable
fun TimingScreen(
    projectId: String,
    sceneIndex: Int = 0,
    onCancel: () -> Unit,
    onCreated: () -> Unit
) {
    val viewModel: TimingViewModel = viewModel(
        key = "timing_${projectId}_$sceneIndex",
        factory = remember(projectId, sceneIndex) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TimingViewModel(projectId, sceneIndex) as T
                }
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Surface(color = VideoStarLightToolbar, shadowElevation = 1.dp) {
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
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onCancel)
                                    .padding(horizontal = 10.dp)
                                    .testTag("timing_cancel_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Cancel", color = ClassicRed, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            }
                            Text(
                                text = "Timing",
                                color = AdjustVideoTitleDark,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.createAndSave(onCreated) }
                                    .padding(horizontal = 10.dp)
                                    .testTag("timing_create_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Create", color = ClassicGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        HorizontalDivider(thickness = 0.8.dp, color = VideoStarIosDivider)
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
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Scene Preview Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val ratioModifier = when (uiState.aspectRatio) {
                        AspectRatioMode.SQUARE -> Modifier.fillMaxHeight().aspectRatio(1f)
                        AspectRatioMode.PORTRAIT -> Modifier.fillMaxHeight().aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
                        AspectRatioMode.PORTRAIT_3_4 -> Modifier.fillMaxHeight().aspectRatio(3f / 4f, matchHeightConstraintsFirst = true)
                        AspectRatioMode.PORTRAIT_4_5 -> Modifier.fillMaxHeight().aspectRatio(4f / 5f, matchHeightConstraintsFirst = true)
                        AspectRatioMode.LANDSCAPE_4_3 -> Modifier.fillMaxHeight().aspectRatio(4f / 3f, matchHeightConstraintsFirst = true)
                        AspectRatioMode.LANDSCAPE -> Modifier.fillMaxWidth(0.92f).aspectRatio(16f / 9f)
                    }

                    Box(
                        modifier = ratioModifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black)
                            .border(1.dp, VideoStarIosDivider, RoundedCornerShape(10.dp))
                    ) {
                        val preview = uiState.previewScene
                        if (preview != null) {
                            SceneRenderStage(
                                scene = preview,
                                localPositionMs = if (uiState.freezeFrameEnabled) {
                                    (uiState.freezeAtProgress * uiState.durationMs).toLong()
                                } else 0L,
                                isPlaying = false,
                                useLiveVideoPlayer = false,
                                contentDescription = "Timing Preview",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("لا يوجد مشهد محدد", color = VideoStarInkMuted, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // Fixed vs Flexible Segmented Control
                IosSegmentedControl(
                    items = TimingMode.entries,
                    selectedItem = uiState.mode,
                    onItemSelected = viewModel::setMode,
                    itemTagPrefix = "timing_mode"
                ) { item, isSelected ->
                    Text(
                        text = item.label,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) AdjustVideoTitleDark else VideoStarInkMuted
                    )
                }

                // Duration & Loop Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = VideoStarWhite,
                    shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, VideoStarIosDivider),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Scene Duration",
                                color = AdjustVideoTitleDark,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = String.format(Locale.US, "%.2fs", uiState.durationMs / 1000f),
                                color = ClassicGreen,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("timing_duration_value")
                            )
                        }

                        Slider(
                            value = (uiState.durationMs / 1000f).coerceIn(0.2f, 30.0f),
                            onValueChange = viewModel::setDurationSeconds,
                            valueRange = 0.2f..30.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = ClassicGreen,
                                activeTrackColor = ClassicGreen,
                                inactiveTrackColor = Color(0xFFDADAE0)
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("timing_duration_slider")
                        )

                        HorizontalDivider(thickness = 0.6.dp, color = VideoStarIosDivider)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Repeat / Loop",
                                color = AdjustVideoTitleDark,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(1, 2, 3).forEach { count ->
                                    val selected = uiState.repeatCount == count
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (selected) ClassicGreen else Color(0xFFF0F0F5),
                                        modifier = Modifier
                                            .height(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { viewModel.setRepeatCount(count) }
                                            .testTag("timing_repeat_${count}x")
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 14.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${count}x",
                                                color = if (selected) VideoStarWhite else VideoStarInkDark,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Freeze Frame Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = VideoStarWhite,
                    shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, VideoStarIosDivider),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Freeze Frame",
                                    color = AdjustVideoTitleDark,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "تجميد الإطار مؤقتاً داخل المشهد",
                                    color = VideoStarInkMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = uiState.freezeFrameEnabled,
                                onCheckedChange = viewModel::setFreezeEnabled,
                                colors = SwitchDefaults.colors(checkedThumbColor = VideoStarWhite, checkedTrackColor = ClassicGreen),
                                modifier = Modifier.testTag("timing_freeze_switch")
                            )
                        }

                        if (uiState.freezeFrameEnabled) {
                            Text(
                                text = "Freeze Position: ${(uiState.freezeAtProgress * 100).toInt()}%",
                                color = AdjustVideoTitleDark,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Slider(
                                value = uiState.freezeAtProgress,
                                onValueChange = viewModel::setFreezeAtProgress,
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(thumbColor = ClassicRed, activeTrackColor = ClassicRed),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(
                                text = String.format(Locale.US, "Freeze Duration: +%.2fs", uiState.freezeDurationMs / 1000f),
                                color = AdjustVideoTitleDark,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Slider(
                                value = (uiState.freezeDurationMs / 1000f).coerceIn(0.2f, 5.0f),
                                onValueChange = viewModel::setFreezeDurationSeconds,
                                valueRange = 0.2f..5.0f,
                                colors = SliderDefaults.colors(thumbColor = ClassicGreen, activeTrackColor = ClassicGreen),
                                modifier = Modifier.fillMaxWidth().testTag("timing_freeze_duration_slider")
                            )
                        }
                    }
                }
            }
        }
    }
}
