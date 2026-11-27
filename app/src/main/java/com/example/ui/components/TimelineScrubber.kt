package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.VideoStarInkMuted
import com.example.ui.theme.VideoStarWhite
import java.util.Locale
import kotlin.math.roundToInt

private val FrostedHudBg = Color(0xFF101015).copy(alpha = 0.28f)
private val FrostedHudBorder = Color.White.copy(alpha = 0.16f)
private val FrostedTrackInactive = Color.White.copy(alpha = 0.22f)
private val FrostedTrackActive = Color.White.copy(alpha = 0.88f)

/**
 * Sleek Video Star Pro semi-hidden ("شبه مخفي") floating timeline scrubber:
 * 1. Minimal translucent frosted frame steppers (< / >) & snapshot rounded-square action
 * 2. Translucent hairline scrubber strip: elapsed · 2.dp hairline track + 12.dp thumb · duration
 */
@Composable
fun TimelineScrubber(
    currentPositionMs: Long,
    durationMs: Long,
    onSeekProgress: (Float) -> Unit,
    onStepBackward: () -> Unit,
    onStepForward: () -> Unit,
    modifier: Modifier = Modifier,
    sceneBoundaryProgresses: List<Float> = emptyList(),
    onThumbBoundsChanged: ((Rect) -> Unit)? = null,
    onSnapshotClick: (() -> Unit)? = null,
    onBeginScrubbing: () -> Unit = {},
    onEndScrubbing: () -> Unit = {}
) {
    val safeDuration = durationMs.coerceAtLeast(500L)
    val progress = (currentPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Minimal semi-hidden frame steppers + snapshot button floating quietly above bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(FrostedHudBg)
                        .border(0.6.dp, FrostedHudBorder, RoundedCornerShape(8.dp))
                        .clickable(onClick = onStepBackward)
                        .testTag("timeline_step_backward"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.cd_step_backward),
                        tint = VideoStarWhite.copy(alpha = 0.90f),
                        modifier = Modifier.size(19.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onSnapshotClick != null) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(FrostedHudBg)
                                .border(0.6.dp, FrostedHudBorder, RoundedCornerShape(8.dp))
                                .clickable(onClick = onSnapshotClick)
                                .testTag("workspace_camera_snapshot_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CameraAlt,
                                contentDescription = stringResource(R.string.cd_capture_snapshot),
                                tint = VideoStarWhite.copy(alpha = 0.90f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(FrostedHudBg)
                            .border(0.6.dp, FrostedHudBorder, RoundedCornerShape(8.dp))
                            .clickable(onClick = onStepForward)
                            .testTag("timeline_step_forward"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.cd_step_forward),
                            tint = VideoStarWhite.copy(alpha = 0.90f),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            // Sleek semi-hidden translucent scrubber pill: 0.00 ──●── 10.02
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(FrostedHudBg)
                    .border(0.6.dp, FrostedHudBorder, RoundedCornerShape(11.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formatTimeDecimal(currentPositionMs),
                    style = TextStyle(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        fontFeatureSettings = "tnum",
                        color = VideoStarWhite.copy(alpha = 0.92f)
                    ),
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    modifier = Modifier
                        .widthIn(min = 38.dp)
                        .testTag("timeline_elapsed_text")
                )

                // Hairline scrubber track with full-height touch area & compact 12.dp thumb
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .pointerInput(safeDuration) {
                            detectTapGestures { offset ->
                                onBeginScrubbing()
                                val tapped = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                onSeekProgress(tapped)
                                onEndScrubbing()
                            }
                        }
                        .pointerInput(safeDuration) {
                            detectHorizontalDragGestures(
                                onDragStart = { onBeginScrubbing() },
                                onDragEnd = { onEndScrubbing() },
                                onDragCancel = { onEndScrubbing() },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    val dragged = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    onSeekProgress(dragged)
                                }
                            )
                        }
                        .testTag("timeline_scrubber_track"),
                    contentAlignment = Alignment.CenterStart
                ) {
                    val density = LocalDensity.current
                    val thumbSize = 12.dp
                    val thumbPx = with(density) { thumbSize.toPx() }
                    val trackWidthPx = with(density) { maxWidth.toPx() }
                    val usableWidthPx = (trackWidthPx - thumbPx).coerceAtLeast(1f)
                    val thumbOffsetX = (progress * usableWidthPx).roundToInt()

                    // Hairline inactive track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(FrostedTrackInactive)
                    )

                    // Active hairline progress fill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = progress.coerceIn(0.01f, 1f))
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(FrostedTrackActive)
                    )

                    // Subtle scene boundary divider ticks
                    sceneBoundaryProgresses.forEach { boundary ->
                        val clamped = boundary.coerceIn(0.01f, 0.99f)
                        val tickOffsetX = ((thumbPx * 0.5f) + clamped * usableWidthPx).roundToInt()
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(tickOffsetX, 0) }
                                .width(1.dp)
                                .height(7.dp)
                                .background(VideoStarInkMuted.copy(alpha = 0.7f))
                        )
                    }

                    // Minimal crisp white circular thumb
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(thumbOffsetX, 0) }
                            .size(thumbSize)
                            .shadow(2.dp, CircleShape)
                            .clip(CircleShape)
                            .background(VideoStarWhite)
                            .border(0.5.dp, Color.Black.copy(alpha = 0.18f), CircleShape)
                            .onGloballyPositioned { coords ->
                                onThumbBoundsChanged?.invoke(coords.boundsInWindow())
                            }
                    )
                }

                Text(
                    text = formatTimeDecimal(safeDuration),
                    style = TextStyle(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        fontFeatureSettings = "tnum",
                        color = VideoStarWhite.copy(alpha = 0.68f)
                    ),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier
                        .widthIn(min = 38.dp)
                        .testTag("timeline_remaining_text")
                )
            }
        }
    }
}

fun formatTimeDecimal(ms: Long): String {
    val safeMs = ms.coerceAtLeast(0L)
    if (safeMs < 60_000L) {
        return String.format(Locale.US, "%.2f", safeMs / 1000.0)
    }
    val totalSeconds = safeMs / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val hundredths = (safeMs % 1000L) / 10L
    return String.format(Locale.US, "%d:%02d.%02d", minutes, seconds, hundredths)
}

fun formatTimeSeconds(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
