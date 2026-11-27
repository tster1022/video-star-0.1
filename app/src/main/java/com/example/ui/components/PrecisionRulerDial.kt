package com.example.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.InterFontFamily
import kotlin.math.abs
import kotlin.math.roundToInt

val PrecisionObsidianLabelBg = Color(0xFF05050A)
val PrecisionRulerWellBg = Color(0xFF171A2B)
val PrecisionReadoutBg = Color(0xFF101322)
val PrecisionGoldAccent = Color(0xFFF6C554)
val PrecisionSegmentTrackBg = Color(0xFF23273D)
val PrecisionSegmentActiveBg = Color(0xFF363D5E)
val PrecisionSegmentDivider = Color(0xFF141726)

@Stable
class StudioTickHaptics(
    private val view: View?,
    private val vibrator: Vibrator?
) {
    private var lastTickAtMs: Long = 0L
    private var lastSelectAtMs: Long = 0L

    fun tick() {
        val now = System.currentTimeMillis()
        if (now - lastTickAtMs < 22L) return
        lastTickAtMs = now
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(8L, 95))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(8L)
                }
            }
        } catch (_: Throwable) {
            // Best-effort hardware tick; ignore on emulators/devices without permission or vibrator.
        }
        try {
            view?.performHapticFeedback(
                HapticFeedbackConstants.CLOCK_TICK,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        } catch (_: Throwable) {
            // Safe fallback
        }
    }

    fun pulseSelection() {
        val now = System.currentTimeMillis()
        if (now - lastSelectAtMs < 30L) return
        lastSelectAtMs = now
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(14L, 130))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(14L)
                }
            }
        } catch (_: Throwable) {
            // Safe fallback
        }
        try {
            view?.performHapticFeedback(
                HapticFeedbackConstants.CONTEXT_CLICK,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        } catch (_: Throwable) {
            // Safe fallback
        }
    }
}

@Composable
fun rememberStudioTickHaptics(): StudioTickHaptics {
    val context = LocalContext.current
    val view = LocalView.current
    val vibrator = remember(context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Throwable) {
            null
        }
    }
    return remember(view, vibrator) { StudioTickHaptics(view, vibrator) }
}

/**
 * Precision Tick-Ruler Dial Slider shared across After Effects, Video Star, and Node Video engines:
 * - Left solid obsidian label pill ([ Radius (px) ]) in warm gold #F6C554
 * - Center animated fine vertical tick ruler with a fixed center gold hairline needle
 * - Right bold numerical readout box ([ 12.0 ]) + optional trailing action slot (e.g. keyframe diamond)
 * - Fires subtle hardware device tick vibrations for every ruler graduation crossed while scrubbing
 */
@Composable
fun PrecisionRulerDialSlider(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: () -> Unit = {},
    defaultValue: Float? = null,
    enabled: Boolean = true,
    accentColor: Color = PrecisionGoldAccent,
    containerColor: Color = PrecisionRulerWellBg,
    labelContainerColor: Color = PrecisionObsidianLabelBg,
    readoutContainerColor: Color = PrecisionReadoutBg,
    height: Dp = 44.dp,
    tickStepsCount: Int = 48,
    sliderTestTag: String? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val haptics = rememberStudioTickHaptics()
    val safeStart = valueRange.start
    val safeEnd = if (valueRange.endInclusive <= safeStart) safeStart + 1f else valueRange.endInclusive
    val span = (safeEnd - safeStart).coerceAtLeast(0.0001f)
    val clampedValue = value.coerceIn(safeStart, safeEnd)
    val fraction = ((clampedValue - safeStart) / span).coerceIn(0f, 1f)

    val latestValue by rememberUpdatedState(clampedValue)
    val latestOnChange by rememberUpdatedState(onValueChange)
    val latestOnFinished by rememberUpdatedState(onValueChangeFinished)
    val latestRangeStart by rememberUpdatedState(safeStart)
    val latestRangeEnd by rememberUpdatedState(safeEnd)

    var dragAccumulatedValue by remember { mutableFloatStateOf(clampedValue) }
    var lastHapticBucket by remember { mutableIntStateOf((fraction * tickStepsCount).roundToInt()) }

    val density = LocalDensity.current

    val rootModifier = if (sliderTestTag != null) modifier.testTag(sliderTestTag) else modifier

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = rootModifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(10.dp))
                .background(containerColor.copy(alpha = if (enabled) 1f else 0.55f))
                .border(0.8.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left solid obsidian label box (double-tap resets to defaultValue when provided)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(min = 78.dp, max = 124.dp)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(labelContainerColor)
                    .pointerInput(enabled, defaultValue) {
                        if (!enabled || defaultValue == null) return@pointerInput
                        detectTapGestures(
                            onDoubleTap = {
                                val reset = defaultValue.coerceIn(latestRangeStart, latestRangeEnd)
                                haptics.pulseSelection()
                                latestOnChange(reset)
                                latestOnFinished()
                            }
                        )
                    }
                    .padding(horizontal = 9.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = label,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    color = if (enabled) accentColor else accentColor.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Center interactive tick ruler + fixed gold hairline needle
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(enabled, safeStart, safeEnd, tickStepsCount) {
                        if (!enabled) return@pointerInput
                        detectTapGestures(
                            onDoubleTap = {
                                if (defaultValue != null) {
                                    val reset = defaultValue.coerceIn(latestRangeStart, latestRangeEnd)
                                    haptics.pulseSelection()
                                    latestOnChange(reset)
                                    latestOnFinished()
                                }
                            },
                            onTap = { tapOffset ->
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val deltaRatio = ((tapOffset.x - w * 0.5f) / (w * 0.5f)).coerceIn(-1f, 1f)
                                val stepNudge = (deltaRatio * 0.08f) * (latestRangeEnd - latestRangeStart)
                                val nextVal = (latestValue + stepNudge).coerceIn(latestRangeStart, latestRangeEnd)
                                haptics.tick()
                                latestOnChange(nextVal)
                                latestOnFinished()
                            }
                        )
                    }
                    .pointerInput(enabled, safeStart, safeEnd, tickStepsCount) {
                        if (!enabled) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragAccumulatedValue = latestValue
                                val curFrac = ((latestValue - latestRangeStart) / (latestRangeEnd - latestRangeStart).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
                                lastHapticBucket = (curFrac * tickStepsCount).roundToInt()
                            },
                            onDragEnd = {
                                latestOnFinished()
                            },
                            onDragCancel = {
                                latestOnFinished()
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val widthPx = size.width.toFloat().coerceAtLeast(with(density) { 80.dp.toPx() })
                                val s = (latestRangeEnd - latestRangeStart).coerceAtLeast(0.0001f)
                                val deltaVal = (dragAmount / widthPx) * s * 1.15f
                                dragAccumulatedValue = (dragAccumulatedValue + deltaVal).coerceIn(latestRangeStart, latestRangeEnd)

                                val nextFrac = ((dragAccumulatedValue - latestRangeStart) / s).coerceIn(0f, 1f)
                                val bucket = (nextFrac * tickStepsCount).roundToInt()
                                if (bucket != lastHapticBucket) {
                                    lastHapticBucket = bucket
                                    haptics.tick()
                                }
                                latestOnChange(dragAccumulatedValue)
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val spacingPx = 5.5.dp.toPx()
                    val virtualTravelPx = spacingPx * tickStepsCount
                    val shiftPx = fraction * virtualTravelPx
                    val centerX = size.width * 0.5f
                    val halfCount = ((size.width * 0.5f) / spacingPx).toInt() + 2
                    val baseTickIndex = (shiftPx / spacingPx).toInt()
                    val subOffsetPx = shiftPx % spacingPx

                    for (i in -halfCount..halfCount) {
                        val x = centerX + i * spacingPx - subOffsetPx
                        if (x < 2.dp.toPx() || x > size.width - 2.dp.toPx()) continue
                        val logicalIndex = baseTickIndex + i
                        val isMajor = (logicalIndex % 5) == 0
                        val distNorm = (abs(x - centerX) / (size.width * 0.55f)).coerceIn(0f, 1f)
                        val edgeFade = (1f - distNorm * 0.65f).coerceIn(0.20f, 1f)
                        val tickH = if (isMajor) size.height * 0.56f else size.height * 0.42f
                        val top = (size.height - tickH) * 0.5f

                        drawLine(
                            color = (if (isMajor) Color(0xFFA2A8C9) else Color(0xFF6B7194))
                                .copy(alpha = (if (enabled) 1f else 0.4f) * edgeFade),
                            start = Offset(x, top),
                            end = Offset(x, top + tickH),
                            strokeWidth = if (isMajor) 1.3.dp.toPx() else 0.9.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // Fixed Center Gold Hairline Needle
                    drawLine(
                        color = if (enabled) accentColor else accentColor.copy(alpha = 0.45f),
                        start = Offset(centerX, 3.dp.toPx()),
                        end = Offset(centerX, size.height - 3.dp.toPx()),
                        strokeWidth = 1.9.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // Right numeric readout box (supports drag scrubbing too for tight layouts!)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(min = 58.dp, max = 82.dp)
                    .padding(top = 3.dp, bottom = 3.dp, end = if (trailingContent != null) 2.dp else 3.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(readoutContainerColor)
                    .pointerInput(enabled, safeStart, safeEnd, tickStepsCount) {
                        if (!enabled) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragAccumulatedValue = latestValue
                                val curFrac = ((latestValue - latestRangeStart) / (latestRangeEnd - latestRangeStart).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
                                lastHapticBucket = (curFrac * tickStepsCount).roundToInt()
                            },
                            onDragEnd = { latestOnFinished() },
                            onDragCancel = { latestOnFinished() },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val s = (latestRangeEnd - latestRangeStart).coerceAtLeast(0.0001f)
                                val deltaVal = (dragAmount / with(density) { 120.dp.toPx() }) * s
                                dragAccumulatedValue = (dragAccumulatedValue + deltaVal).coerceIn(latestRangeStart, latestRangeEnd)
                                val nextFrac = ((dragAccumulatedValue - latestRangeStart) / s).coerceIn(0f, 1f)
                                val bucket = (nextFrac * tickStepsCount).roundToInt()
                                if (bucket != lastHapticBucket) {
                                    lastHapticBucket = bucket
                                    haptics.tick()
                                }
                                latestOnChange(dragAccumulatedValue)
                            }
                        )
                    }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = valueText,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (enabled) Color.White else Color.White.copy(alpha = 0.45f),
                    maxLines = 1,
                    textAlign = TextAlign.End
                )
            }

            if (trailingContent != null) {
                Box(
                    modifier = Modifier.padding(end = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    trailingContent()
                }
            }
        }
    }
}

/**
 * Joined Segmented Pill Bar matching screenshot `Quality | LOW | MEDIUM | HIGH`:
 * - Left crisp label
 * - Connected dark segmented track with hairline vertical separators
 * - Active option glows in gold #F6C554 and fires hardware selection haptic feedback
 */
@Composable
fun <T> PrecisionSegmentedOptionRow(
    label: String?,
    options: List<T>,
    selectedOption: T,
    optionLabel: (T) -> String,
    onSelectOption: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = PrecisionGoldAccent,
    labelColor: Color = Color.White,
    trackColor: Color = PrecisionSegmentTrackBg,
    activeContainerColor: Color = PrecisionSegmentActiveBg,
    optionTestTag: ((Int, T) -> String)? = null
) {
    val haptics = rememberStudioTickHaptics()
    val useEqualWeight = options.size in 1..4

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp,
                color = if (enabled) labelColor else labelColor.copy(alpha = 0.45f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(min = 52.dp, max = 96.dp)
            )
        }

        Surface(
            color = trackColor,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.10f)),
            modifier = Modifier
                .weight(1f)
                .height(34.dp)
        ) {
            if (useEqualWeight) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .height(IntrinsicSize.Min)
                ) {
                    options.forEachIndexed { index, opt ->
                        val isSelected = opt == selectedOption
                        if (index > 0) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(PrecisionSegmentDivider)
                            )
                        }
                        val cellModifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (isSelected) activeContainerColor else Color.Transparent)
                            .clickable(enabled = enabled) {
                                haptics.pulseSelection()
                                onSelectOption(opt)
                            }
                            .let { m ->
                                val tag = optionTestTag?.invoke(index, opt)
                                if (tag != null) m.testTag(tag) else m
                            }
                        Box(
                            modifier = cellModifier.padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = optionLabel(opt),
                                fontFamily = InterFontFamily,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = when {
                                    !enabled -> Color.White.copy(alpha = 0.4f)
                                    isSelected -> accentColor
                                    else -> Color.White.copy(alpha = 0.88f)
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                ) {
                    options.forEachIndexed { index, opt ->
                        val isSelected = opt == selectedOption
                        if (index > 0) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(PrecisionSegmentDivider)
                            )
                        }
                        val cellModifier = Modifier
                            .fillMaxHeight()
                            .background(if (isSelected) activeContainerColor else Color.Transparent)
                            .clickable(enabled = enabled) {
                                haptics.pulseSelection()
                                onSelectOption(opt)
                            }
                            .let { m ->
                                val tag = optionTestTag?.invoke(index, opt)
                                if (tag != null) m.testTag(tag) else m
                            }
                        Box(
                            modifier = cellModifier.padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = optionLabel(opt),
                                fontFamily = InterFontFamily,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = when {
                                    !enabled -> Color.White.copy(alpha = 0.4f)
                                    isSelected -> accentColor
                                    else -> Color.White.copy(alpha = 0.88f)
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
