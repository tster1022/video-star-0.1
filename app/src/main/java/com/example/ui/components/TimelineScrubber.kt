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

private val StepperBoxGray = Color(0xFFA0A0A5)
private val TimestampBoxGray = Color(0xFF9E9EA4)
private val ScrubberTrackGray = Color(0xFFD0D0D4)

/**
 * Video Star Screen 2 Timeline Controls:
 * 1. Frame Stepper Row above timeline:
 *    - Left gray rounded square with white '<' (step backward 1 frame)
 *    - Right gray rounded square with white '>' (step forward 1 frame)
 * 2. Scrubber Bar Row:
 *    - Left gray box displaying current timestamp (e.g., "0.00") in white tabular numerals
 *    - Center gray track with a white circular thumb handle
 *    - Right gray box displaying total scene duration (e.g., "10.02") in white tabular numerals
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
    onSnapshotClick: (() -> Unit)? = null
) {
    val safeDuration = durationMs.coerceAtLeast(500L)
    val progress = (currentPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Frame Stepper Row above timeline (with circular gray PNG camera snapshot button directly above '>')
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(StepperBoxGray)
                        .clickable(onClick = onStepBackward)
                        .testTag("timeline_step_backward"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.cd_step_backward),
                        tint = VideoStarWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (onSnapshotClick != null) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(StepperBoxGray)
                                .clickable(onClick = onSnapshotClick)
                                .testTag("workspace_camera_snapshot_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CameraAlt,
                                contentDescription = stringResource(R.string.cd_capture_snapshot),
                                tint = VideoStarWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(width = 44.dp, height = 34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(StepperBoxGray)
                            .clickable(onClick = onStepForward)
                            .testTag("timeline_step_forward"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.cd_step_forward),
                            tint = VideoStarWhite,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Scrubber Bar: [0.00] ────●──── [10.02]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Left gray box: current timestamp in white tabular numerals
                Box(
                    modifier = Modifier
                        .widthIn(min = 52.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(TimestampBoxGray)
                        .padding(horizontal = 8.dp)
                        .testTag("timeline_elapsed_text"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatTimeDecimal(currentPositionMs),
                        style = TextStyle(
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            fontFeatureSettings = "tnum",
                            color = VideoStarWhite
                        ),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }

                // Center gray track with white circular thumb handle
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .pointerInput(safeDuration) {
                            detectTapGestures { offset ->
                                val tapped = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                onSeekProgress(tapped)
                            }
                        }
                        .pointerInput(safeDuration) {
                            detectHorizontalDragGestures { change, _ ->
                                change.consume()
                                val dragged = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                onSeekProgress(dragged)
                            }
                        }
                        .testTag("timeline_scrubber_track"),
                    contentAlignment = Alignment.CenterStart
                ) {
                    val density = LocalDensity.current
                    val thumbSize = 24.dp
                    val thumbPx = with(density) { thumbSize.toPx() }
                    val trackWidthPx = with(density) { maxWidth.toPx() }
                    val usableWidthPx = (trackWidthPx - thumbPx).coerceAtLeast(1f)
                    val thumbOffsetX = (progress * usableWidthPx).roundToInt()

                    // Base gray track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(ScrubberTrackGray)
                    )

                    // Subtle scene boundary divider ticks
                    sceneBoundaryProgresses.forEach { boundary ->
                        val clamped = boundary.coerceIn(0.01f, 0.99f)
                        val tickOffsetX = ((thumbPx * 0.5f) + clamped * usableWidthPx).roundToInt()
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(tickOffsetX, 0) }
                                .width(1.5.dp)
                                .height(12.dp)
                                .background(VideoStarInkMuted.copy(alpha = 0.65f))
                        )
                    }

                    // White circular thumb handle
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(thumbOffsetX, 0) }
                            .size(thumbSize)
                            .shadow(3.dp, CircleShape)
                            .clip(CircleShape)
                            .background(VideoStarWhite)
                            .border(0.8.dp, Color.Black.copy(alpha = 0.14f), CircleShape)
                            .onGloballyPositioned { coords ->
                                onThumbBoundsChanged?.invoke(coords.boundsInWindow())
                            }
                    )
                }

                // Right gray box: total scene duration in white tabular numerals
                Box(
                    modifier = Modifier
                        .widthIn(min = 52.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(TimestampBoxGray)
                        .padding(horizontal = 8.dp)
                        .testTag("timeline_remaining_text"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatTimeDecimal(safeDuration),
                        style = TextStyle(
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            fontFeatureSettings = "tnum",
                            color = VideoStarWhite
                        ),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
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
