package com.example.ui.screens.effects

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.ui.components.PrecisionRulerDialSlider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BezierCurveSpec
import com.example.model.KeyframeEasing
import com.example.model.ParameterKeyframe
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.ClassicRed
import com.example.ui.theme.InterFontFamily
import java.util.Locale
import kotlin.math.abs

/**
 * Header row for an animatable shader property with value readout, prev/next keyframe jump,
 * and the ◆ keyframe toggle control.
 */
@Composable
internal fun PropertyKeyframeRowHeader(
    propertyKey: String,
    label: String,
    valueDisplay: String,
    keyframes: List<ParameterKeyframe>,
    hasKeyframeNear: Boolean,
    isFocused: Boolean,
    enabled: Boolean,
    onFocusProperty: () -> Unit,
    onToggleKeyframe: () -> Unit,
    onJumpPrev: () -> Unit,
    onJumpNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val isAnimated = keyframes.isNotEmpty()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onFocusProperty)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isAnimated || isFocused) {
                Box(
                    modifier = Modifier
                        .size(width = 3.dp, height = 16.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (hasKeyframeNear) ClassicRed
                            else if (isAnimated) colorScheme.primary
                            else colorScheme.primary.copy(alpha = 0.45f)
                        )
                )
            }
            Text(
                text = label,
                fontFamily = CairoFontFamily,
                fontSize = 13.sp,
                fontWeight = if (isFocused || isAnimated) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isFocused) colorScheme.primary else colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.7f)
            ) {
                Text(
                    text = valueDisplay,
                    fontFamily = InterFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
            if (isAnimated) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (hasKeyframeNear) ClassicRed.copy(alpha = 0.16f) else colorScheme.primary.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "${keyframes.size} ◆",
                        fontFamily = InterFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hasKeyframeNear) ClassicRed else colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isAnimated) {
                // Keep jump arrows consistently ordered for timeline progression
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = enabled, onClick = onJumpPrev)
                        .testTag("effects_kf_prev_$propertyKey")
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(6.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "نقطة الحركة السابقة",
                            tint = colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = enabled, onClick = onJumpNext)
                        .testTag("effects_kf_next_$propertyKey")
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(6.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronLeft,
                            contentDescription = "نقطة الحركة التالية",
                            tint = colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            KeyframeDiamondButton(
                isActiveAtPlayhead = hasKeyframeNear,
                hasAnyKeyframes = isAnimated,
                enabled = enabled,
                contentDesc = "تبديل نقطة الحركة $label",
                onClick = onToggleKeyframe,
                modifier = Modifier.testTag("effects_kf_toggle_$propertyKey")
            )
        }
    }
}

/**
 * Crisp custom ◆ Diamond toggle button meeting 48.dp accessibility touch height.
 */
@Composable
internal fun KeyframeDiamondButton(
    isActiveAtPlayhead: Boolean,
    hasAnyKeyframes: Boolean,
    enabled: Boolean,
    contentDesc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = when {
        !enabled -> colorScheme.surfaceVariant.copy(alpha = 0.4f)
        isActiveAtPlayhead -> ClassicRed
        hasAnyKeyframes -> colorScheme.primary.copy(alpha = 0.14f)
        else -> colorScheme.surfaceVariant.copy(alpha = 0.7f)
    }
    val borderStroke = when {
        isActiveAtPlayhead -> null
        hasAnyKeyframes -> BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.65f))
        else -> BorderStroke(0.8.dp, colorScheme.outline.copy(alpha = 0.35f))
    }
    val diamondColor = when {
        !enabled -> colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        isActiveAtPlayhead -> Color.White
        hasAnyKeyframes -> colorScheme.primary
        else -> colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = borderStroke,
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = contentDesc }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            KeyframeDiamondGlyph(
                filled = isActiveAtPlayhead || hasAnyKeyframes,
                color = diamondColor,
                size = 16.dp
            )
        }
    }
}

@Composable
internal fun KeyframeDiamondGlyph(
    filled: Boolean,
    color: Color,
    size: Dp = 14.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val r = (minOf(this.size.width, this.size.height) / 2f) * 0.85f
        val path = Path().apply {
            moveTo(cx, cy - r)
            lineTo(cx + r, cy)
            lineTo(cx, cy + r)
            lineTo(cx - r, cy)
            close()
        }
        if (filled) {
            drawPath(path, color = color, style = Fill)
        } else {
            drawPath(path, color = color, style = Stroke(width = 1.8.dp.toPx()))
        }
    }
}

/**
 * Expandable inspector under a keyframed property: shows clickable ◆ timeline nodes
 * and the per-segment Easing curve picker.
 */
@Composable
internal fun PropertyKeyframeTimelineAndEasingStrip(
    visible: Boolean,
    propertyKey: String,
    keyframes: List<ParameterKeyframe>,
    previewProgress: Float,
    activeEasing: KeyframeEasing,
    customBezier: BezierCurveSpec? = null,
    useEnglishLabels: Boolean = false,
    onSeekProgress: (Float) -> Unit,
    onSelectEasing: (KeyframeEasing) -> Unit,
    onUpdateCustomBezier: ((BezierCurveSpec) -> Unit)? = null,
    onClearAllForProperty: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    AnimatedVisibility(visible = visible && keyframes.isNotEmpty(), modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(0.8.dp, colorScheme.outline.copy(alpha = 0.22f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("effects_kf_inspector_$propertyKey")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "مسار نقاط الحركة (${keyframes.size})",
                        fontFamily = CairoFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onClearAllForProperty)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("effects_kf_clear_$propertyKey"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = "مسح نقاط الحركة",
                            tint = ClassicRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "مسح",
                            fontFamily = CairoFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ClassicRed
                        )
                    }
                }

                // Mini clickable ◆ lane (LTR time axis)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    androidx.compose.foundation.layout.BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colorScheme.surface.copy(alpha = 0.7f))
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        val trackW = maxWidth
                        Canvas(modifier = Modifier.fillMaxWidth().height(24.dp)) {
                            val cy = size.height / 2f
                            drawLine(
                                color = colorScheme.outline.copy(alpha = 0.35f),
                                start = Offset(0f, cy),
                                end = Offset(size.width, cy),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            val sorted = keyframes.sortedBy { it.time }
                            if (sorted.size >= 2) {
                                val firstX = sorted.first().time.coerceIn(0f, 1f) * size.width
                                val lastX = sorted.last().time.coerceIn(0f, 1f) * size.width
                                drawLine(
                                    color = colorScheme.primary.copy(alpha = 0.55f),
                                    start = Offset(firstX, cy),
                                    end = Offset(lastX, cy),
                                    strokeWidth = 3.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                            val playX = previewProgress.coerceIn(0f, 1f) * size.width
                            drawLine(
                                color = ClassicRed.copy(alpha = 0.7f),
                                start = Offset(playX, 2.dp.toPx()),
                                end = Offset(playX, size.height - 2.dp.toPx()),
                                strokeWidth = 1.6.dp.toPx()
                            )
                            sorted.forEach { kf ->
                                val x = kf.time.coerceIn(0f, 1f) * size.width
                                val isCurrent = abs(kf.time - previewProgress) <= 0.03f
                                val r = if (isCurrent) 6.dp.toPx() else 4.5.dp.toPx()
                                val path = Path().apply {
                                    moveTo(x, cy - r)
                                    lineTo(x + r, cy)
                                    lineTo(x, cy + r)
                                    lineTo(x - r, cy)
                                    close()
                                }
                                drawPath(
                                    path = path,
                                    color = if (isCurrent) ClassicRed else colorScheme.primary
                                )
                            }
                        }
                        keyframes.sortedBy { it.time }.forEach { kf ->
                            val targetOffsetX = ((trackW - 22.dp) * kf.time.coerceIn(0f, 1f)).coerceAtLeast(0.dp)
                            Box(
                                modifier = Modifier
                                    .padding(start = targetOffsetX)
                                    .size(22.dp)
                                    .clickable { onSeekProgress(kf.time) }
                                    .testTag("effects_kf_node_${propertyKey}_${(kf.time * 100).toInt()}")
                            )
                        }
                    }
                }

                // Easing Selector Row
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(
                        items = KeyframeEasing.entries,
                        key = { it.id }
                    ) { easing ->
                        val selected = activeEasing == easing
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectEasing(easing) },
                            leadingIcon = {
                                EasingMiniCurveIcon(
                                    easing = easing,
                                    customBezier = if (easing == KeyframeEasing.CUBIC_BEZIER) customBezier else null,
                                    tint = if (selected) colorScheme.onPrimary else colorScheme.primary
                                )
                            },
                            label = {
                                Text(
                                    text = if (useEnglishLabels) easing.englishLabel else easing.arabicLabel,
                                    fontFamily = if (useEnglishLabels) InterFontFamily else CairoFontFamily,
                                    fontSize = 11.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colorScheme.primary,
                                selectedLabelColor = colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("effects_easing_${propertyKey}_${easing.id}")
                        )
                    }
                }

                // Visual Cubic Bezier Curve Inspector when CUBIC_BEZIER is active
                if (activeEasing == KeyframeEasing.CUBIC_BEZIER && onUpdateCustomBezier != null) {
                    val spec = customBezier ?: KeyframeEasing.CUBIC_BEZIER.toBezierCurveSpec()
                    CubicBezierCurveMiniEditor(
                        spec = spec,
                        onUpdate = onUpdateCustomBezier
                    )
                }
            }
        }
    }
}

@Composable
private fun CubicBezierCurveMiniEditor(
    spec: BezierCurveSpec,
    onUpdate: (BezierCurveSpec) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = colorScheme.surface.copy(alpha = 0.75f),
        border = BorderStroke(0.6.dp, colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                val pad = 8.dp.toPx()
                val w = size.width - pad * 2f
                val h = size.height - pad * 2f
                val p0 = Offset(pad, pad + h)
                val p3 = Offset(pad + w, pad)
                val p1 = Offset(pad + spec.p1x.coerceIn(0f, 1f) * w, pad + (1f - spec.p1y) * h)
                val p2 = Offset(pad + spec.p2x.coerceIn(0f, 1f) * w, pad + (1f - spec.p2y) * h)

                drawLine(colorScheme.outline.copy(alpha = 0.25f), p0, p3, strokeWidth = 1.dp.toPx())
                drawLine(colorScheme.secondary.copy(alpha = 0.6f), p0, p1, strokeWidth = 1.2.dp.toPx())
                drawLine(colorScheme.tertiary.copy(alpha = 0.6f), p3, p2, strokeWidth = 1.2.dp.toPx())

                val curvePath = Path().apply {
                    moveTo(p0.x, p0.y)
                    cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
                }
                drawPath(curvePath, colorScheme.primary, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(colorScheme.secondary, radius = 3.5.dp.toPx(), center = p1)
                drawCircle(colorScheme.tertiary, radius = 3.5.dp.toPx(), center = p2)
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = String.format(
                            Locale.US,
                            "Cubic (%.2f, %.2f, %.2f, %.2f)",
                            spec.p1x, spec.p1y, spec.p2x, spec.p2y
                        ),
                        fontFamily = InterFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface
                    )
                    PrecisionRulerDialSlider(
                        label = "P1X",
                        valueText = String.format(Locale.US, "%.2f", spec.p1x),
                        value = spec.p1x,
                        onValueChange = { onUpdate(spec.copy(p1x = it.coerceIn(0.01f, 0.99f))) },
                        valueRange = 0.01f..0.99f,
                        height = 34.dp
                    )
                    PrecisionRulerDialSlider(
                        label = "P1Y",
                        valueText = String.format(Locale.US, "%.2f", spec.p1y),
                        value = spec.p1y,
                        onValueChange = { onUpdate(spec.copy(p1y = it)) },
                        valueRange = -0.3f..1.3f,
                        height = 34.dp
                    )
                    PrecisionRulerDialSlider(
                        label = "P2X",
                        valueText = String.format(Locale.US, "%.2f", spec.p2x),
                        value = spec.p2x,
                        onValueChange = { onUpdate(spec.copy(p2x = it.coerceIn(0.01f, 0.99f))) },
                        valueRange = 0.01f..0.99f,
                        height = 34.dp
                    )
                    PrecisionRulerDialSlider(
                        label = "P2Y",
                        valueText = String.format(Locale.US, "%.2f", spec.p2y),
                        value = spec.p2y,
                        onValueChange = { onUpdate(spec.copy(p2y = it)) },
                        valueRange = -0.3f..1.3f,
                        height = 34.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun EasingMiniCurveIcon(
    easing: KeyframeEasing,
    customBezier: BezierCurveSpec? = null,
    tint: Color
) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val pad = 2.dp.toPx()
        val w = size.width - pad * 2f
        val h = size.height - pad * 2f
        val path = Path()
        val steps = 12
        for (i in 0..steps) {
            val u = i / steps.toFloat()
            val v = easing.evaluate(u, customBezier).coerceIn(-0.15f, 1.15f)
            val x = pad + u * w
            val y = pad + (1f - v) * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
