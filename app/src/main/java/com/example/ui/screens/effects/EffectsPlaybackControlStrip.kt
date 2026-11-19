package com.example.ui.screens.effects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.IosThinSlider
import com.example.ui.theme.ClassicRed
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.VideoStarWhite
import java.util.Locale

@Composable
internal fun EffectsPlaybackControlStrip(
    uiState: EffectsStudioUiState,
    isDarkTheme: Boolean,
    onTogglePlay: () -> Unit,
    onScrub: (Float) -> Unit,
    onToggleKeyframe: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activePass = uiState.activePass
    val barBg = if (isDarkTheme) {
        Color(0xFF141418).copy(alpha = 0.42f)
    } else {
        Color(0xFF141418).copy(alpha = 0.34f)
    }
    val borderColor = Color.White.copy(alpha = 0.16f)
    val contentTint = VideoStarWhite
    val activeTrack = VideoStarWhite.copy(alpha = 0.90f)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = barBg,
        border = BorderStroke(0.6.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .testTag("effects_playback_strip")
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(38.dp).testTag("reeffect_play_pause")
                ) {
                    Icon(
                        imageVector = if (uiState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "تشغيل أو إيقاف المعاينة",
                        tint = contentTint,
                        modifier = Modifier.size(21.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxWidth().height(12.dp).padding(horizontal = 8.dp)) {
                            val cy = size.height / 2f
                            uiState.allActivePassKeyframeTimes.forEach { progress ->
                                val x = progress.coerceIn(0f, 1f) * size.width
                                val r = 3.dp.toPx()
                                val path = Path().apply {
                                    moveTo(x, cy - r)
                                    lineTo(x + r, cy)
                                    lineTo(x, cy + r)
                                    lineTo(x - r, cy)
                                    close()
                                }
                                drawPath(path, color = contentTint.copy(alpha = 0.62f))
                            }
                            uiState.selectedPropertyKeyframeTimes.forEach { progress ->
                                val x = progress.coerceIn(0f, 1f) * size.width
                                val r = 4.dp.toPx()
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
                            onValueChange = onScrub,
                            valueRange = 0f..1f,
                            activeTrackColor = activeTrack,
                            inactiveTrackColor = contentTint.copy(alpha = 0.24f),
                            thumbSize = 11.dp,
                            trackHeight = 2.dp,
                            modifier = Modifier.fillMaxWidth().testTag("effects_time_scrubber")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatMillisShort(uiState.previewLocalMs),
                            style = TextStyle(
                                fontFamily = InterFontFamily,
                                fontSize = 9.5.sp,
                                fontFeatureSettings = "tnum",
                                color = contentTint.copy(alpha = 0.85f)
                            )
                        )
                        Text(
                            text = formatMillisShort(uiState.totalDurationMs),
                            style = TextStyle(
                                fontFamily = InterFontFamily,
                                fontSize = 9.5.sp,
                                fontFeatureSettings = "tnum",
                                color = contentTint.copy(alpha = 0.65f)
                            )
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = if (uiState.hasKeyframeNearCurrentTime) ClassicRed else contentTint.copy(alpha = 0.14f),
                    border = BorderStroke(
                        0.6.dp,
                        if (uiState.hasKeyframeNearCurrentTime) Color.White.copy(alpha = 0.35f) else borderColor
                    ),
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .clickable(enabled = activePass != null, onClick = onToggleKeyframe)
                        .testTag("effects_keyframe_toggle")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Diamond,
                            contentDescription = "إضافة أو حذف نقطة Keyframe",
                            tint = if (activePass != null) VideoStarWhite else contentTint.copy(alpha = 0.40f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

internal fun formatMillisShort(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val secs = clamped / 1000L
    val tenths = (clamped % 1000L) / 100L
    return String.format(Locale.US, "%02d:%02d.%d", secs / 60L, secs % 60L, tenths)
}
