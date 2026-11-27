package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.ClassicGreen
import com.example.ui.theme.ClassicRed
import com.example.ui.theme.IosHairlineGray
import com.example.ui.theme.SfProDisplayFontFamily
import com.example.ui.theme.SfProTextFontFamily
import com.example.ui.theme.VideoStarInkDark
import com.example.ui.theme.VideoStarInkMuted
import com.example.ui.theme.VideoStarWhite
import kotlin.math.roundToInt

/**
 * Video Star iOS-style slim slider:
 * - 2.5.dp horizontal track with customizable active tint (#2E7D32 / #D91E18)
 * - 20.dp pure white circular thumb with soft elevation shadow
 * - 48.dp touch height for accessible precision scrubbing
 */
@Composable
fun IosThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    activeTrackColor: Color = ClassicGreen,
    inactiveTrackColor: Color = Color(0xFFD1D1D6),
    thumbColor: Color = VideoStarWhite,
    thumbSize: androidx.compose.ui.unit.Dp = 14.dp,
    trackHeight: androidx.compose.ui.unit.Dp = 2.dp
) {
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0.00001f } ?: 1f
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val latestOnChange by rememberUpdatedState(onValueChange)
    val latestRange by rememberUpdatedState(valueRange)
    val haptics = rememberStudioTickHaptics()
    var lastHapticBucket by remember { mutableIntStateOf((fraction * 40f).roundToInt()) }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val thumbPx = with(density) { thumbSize.toPx() }
        val totalPx = with(density) { maxWidth.toPx() }.coerceAtLeast(thumbPx + 1f)
        val usablePx = (totalPx - thumbPx).coerceAtLeast(1f)
        val thumbX = (fraction * usablePx).roundToInt()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled, usablePx, thumbPx) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        val f = ((offset.x - thumbPx * 0.5f) / usablePx).coerceIn(0f, 1f)
                        val r = latestRange
                        haptics.tick()
                        latestOnChange(r.start + f * (r.endInclusive - r.start))
                    }
                }
                .pointerInput(enabled, usablePx, thumbPx) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = {
                            lastHapticBucket = (fraction * 40f).roundToInt()
                        }
                    ) { change, _ ->
                        change.consume()
                        val f = ((change.position.x - thumbPx * 0.5f) / usablePx).coerceIn(0f, 1f)
                        val bucket = (f * 40f).roundToInt()
                        if (bucket != lastHapticBucket) {
                            lastHapticBucket = bucket
                            haptics.tick()
                        }
                        val r = latestRange
                        latestOnChange(r.start + f * (r.endInclusive - r.start))
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // Inactive background track (hairline)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(if (enabled) inactiveTrackColor else Color(0xFFE5E5EA))
            )

            // Active filled track (hairline)
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceAtLeast(0.001f))
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(if (enabled) activeTrackColor else VideoStarInkMuted.copy(alpha = 0.4f))
            )

            // Sleek circular micro thumb with subtle rim & shadow
            Box(
                modifier = Modifier
                    .offset { IntOffset(thumbX, 0) }
                    .size(thumbSize)
                    .shadow(elevation = 3.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(if (enabled) thumbColor else Color(0xFFF2F2F7))
                    .border(0.6.dp, Color.Black.copy(alpha = 0.16f), CircleShape)
            )
        }
    }
}

/**
 * Interactive Video Star iOS Ruler Jog Wheel:
 * - Canvas-drawn closely spaced vertical gray graduations
 * - Fixed center red hairline marker (#D91E18)
 * - Horizontal drag rotates angle in precise 0.5° steps
 */
@Composable
fun RulerJogWheel(
    angleDegrees: Float,
    onAngleChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = -180f..180f,
    stepDegrees: Float = 0.5f
) {
    val latestAngle by rememberUpdatedState(angleDegrees)
    val latestOnAngleChange by rememberUpdatedState(onAngleChange)
    val haptics = rememberStudioTickHaptics()
    var accumulatedAngle by remember { mutableFloatStateOf(angleDegrees) }
    var lastSnappedAngle by remember { mutableFloatStateOf(angleDegrees) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF2F2F7))
            .border(0.5.dp, IosHairlineGray, RoundedCornerShape(8.dp))
            .pointerInput(valueRange, stepDegrees) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        accumulatedAngle = latestAngle
                        lastSnappedAngle = latestAngle
                    },
                    onDragEnd = {},
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        // ~0.22° per px -> smooth tactile 0.5° precision
                        accumulatedAngle = (accumulatedAngle + dragAmount * 0.22f)
                            .coerceIn(valueRange.start, valueRange.endInclusive)
                        val snapped = (if (stepDegrees > 0f) {
                            (accumulatedAngle / stepDegrees).roundToInt() * stepDegrees
                        } else accumulatedAngle).coerceIn(valueRange.start, valueRange.endInclusive)
                        if (kotlin.math.abs(snapped - lastSnappedAngle) >= (stepDegrees * 0.9f).coerceAtLeast(0.4f)) {
                            lastSnappedAngle = snapped
                            haptics.tick()
                        }
                        latestOnAngleChange(snapped)
                    }
                )
            }
            .testTag("transform_ruler_jog_wheel")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val tickSpacingPx = 6.dp.toPx()
            val pixelsPerDegree = 3.2.dp.toPx()
            val offsetPx = (angleDegrees * pixelsPerDegree) % tickSpacingPx
            val centerX = size.width / 2f
            val countHalf = ((size.width / 2f) / tickSpacingPx).toInt() + 2
            val baseIndex = -(angleDegrees * pixelsPerDegree / tickSpacingPx).roundToInt()

            for (i in -countHalf..countHalf) {
                val x = centerX + i * tickSpacingPx + offsetPx
                if (x < 4.dp.toPx() || x > size.width - 4.dp.toPx()) continue
                val tickLogicalIndex = baseIndex + i
                val isMajor = (tickLogicalIndex % 5) == 0
                val tickH = if (isMajor) size.height * 0.52f else size.height * 0.30f
                val top = (size.height - tickH) / 2f
                val alphaFactor = (1f - (kotlin.math.abs(x - centerX) / (size.width * 0.52f))).coerceIn(0.22f, 1f)
                drawLine(
                    color = (if (isMajor) Color(0xFF6E6E73) else Color(0xFFAEAEB2)).copy(alpha = alphaFactor),
                    start = Offset(x, top),
                    end = Offset(x, top + tickH),
                    strokeWidth = if (isMajor) 1.4.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Center fixed red hairline indicator
            drawLine(
                color = ClassicRed,
                start = Offset(centerX, 2.dp.toPx()),
                end = Offset(centerX, size.height - 2.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = ClassicRed,
                radius = 2.6.dp.toPx(),
                center = Offset(centerX, 4.dp.toPx())
            )
        }
    }
}

/**
 * Pure iOS UIKit translucent Alert Dialog:
 * - 14.dp rounded corners, 0.5.dp #C6C6C8 dividers
 * - Bold 17.sp SF Pro Display title, 13.sp SF Pro Text body
 * - Text-only full-height UIKit action buttons (no big red M3 pills)
 */
@Composable
fun IosAlertDialog(
    title: String,
    message: String,
    confirmText: String = "OK",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmColor: Color = ClassicGreen,
    dismissText: String? = null,
    dismissColor: Color = VideoStarInkDark,
    confirmTestTag: String = "ios_alert_confirm",
    dismissTestTag: String = "ios_alert_dismiss"
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xF8FFFFFF),
            shadowElevation = 10.dp,
            border = BorderStroke(0.5.dp, IosHairlineGray),
            modifier = Modifier
                .width(278.dp)
                .testTag("ios_alert_dialog")
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        fontFamily = SfProDisplayFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        letterSpacing = (-0.3).sp,
                        color = VideoStarInkDark,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = message,
                        fontFamily = SfProTextFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        letterSpacing = (-0.2).sp,
                        color = VideoStarInkMuted,
                        textAlign = TextAlign.Center
                    )
                }

                IosHairlineDivider()

                if (dismissText != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .height(46.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .iosPressable(onClick = onDismiss)
                                .testTag(dismissTestTag),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dismissText,
                                fontFamily = SfProTextFontFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 17.sp,
                                color = dismissColor
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(0.5.dp)
                                .fillMaxHeight()
                                .background(IosHairlineGray)
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .iosPressable(onClick = onConfirm)
                                .testTag(confirmTestTag),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = confirmText,
                                fontFamily = SfProDisplayFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp,
                                color = confirmColor
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .iosPressable(onClick = onConfirm)
                            .testTag(confirmTestTag),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = confirmText,
                            fontFamily = SfProDisplayFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            color = confirmColor
                        )
                    }
                }
            }
        }
    }
}
