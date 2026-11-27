package com.example.ui.screens.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.ui.components.PrecisionGoldAccent
import com.example.ui.components.PrecisionObsidianLabelBg
import com.example.ui.components.PrecisionReadoutBg
import com.example.ui.components.PrecisionRulerDialSlider
import com.example.ui.components.PrecisionSegmentedOptionRow
import com.example.ui.components.rememberStudioTickHaptics
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.ae.AeEffectEngine
import com.example.model.BezierCurveSpec
import com.example.model.EditorLayer
import com.example.model.EffectBlendMode
import com.example.model.defaultGraphPresets
import com.example.ui.theme.InterFontFamily
import java.util.Locale
import kotlin.math.abs

private val InlinePanelTealBg = Color(0xFF132226)
private val InlineCardBg = Color(0xFF0E1B1F)
private val InlineNeonGreen = Color(0xFF00E676)
private val InlineMutedText = Color(0xFF8E94A5)

@Composable
private fun AeInlinePanelFrame(
    title: String,
    subtitle: String,
    testTag: String,
    onBack: () -> Unit,
    onExitToIdle: () -> Unit,
    actionChipLabel: String? = null,
    onActionChipClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        color = InlinePanelTealBg,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onBack)
                            .testTag("${testTag}_back_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitle,
                            fontFamily = InterFontFamily,
                            fontSize = 10.sp,
                            color = InlineNeonGreen,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (actionChipLabel != null && onActionChipClick != null) {
                        Surface(
                            color = Color(0xFF1B3036),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(onClick = onActionChipClick)
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = actionChipLabel,
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    color = Color(0xFFE5E7EB)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1B3036))
                            .clickable(onClick = onExitToIdle)
                            .testTag("${testTag}_exit_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Exit to Edit & Add",
                            tint = Color(0xFFD1D5DB),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
internal fun AeTransformInlinePanel(
    layer: EditorLayer,
    currentPositionMs: Long,
    onBack: () -> Unit,
    onExitToIdle: () -> Unit,
    onUpdateLayer: (EditorLayer) -> Unit,
    onToggleKeyframe: () -> Unit
) {
    val evaluated = AeEffectEngine.composeAeLayerTransformAt(layer, currentPositionMs)
    val dur = layer.effectiveLayerDurationMs.toFloat().coerceAtLeast(1f)
    val progress = ((currentPositionMs - layer.startTimeMs).toFloat() / dur).coerceIn(0f, 1f)
    val hasKfHere = layer.keyframes.any { abs(it.timeProgress - progress) <= 0.03f }

    AeInlinePanelFrame(
        title = "Move & Transform",
        subtitle = layer.displayTitle,
        testTag = "ae_transform_inline_panel",
        onBack = onBack,
        onExitToIdle = onExitToIdle,
        actionChipLabel = "Reset",
        onActionChipClick = {
            onUpdateLayer(
                layer.copy(
                    offsetX = 0f,
                    offsetY = 0f,
                    scale = 1f,
                    rotation = 0f,
                    isMirrored = false,
                    isFlippedVertical = false
                )
            )
        }
    ) {
        AeInlineSliderRow(
            label = "Position X",
            valueText = "${evaluated.offsetX.toInt()} px",
            value = evaluated.offsetX,
            min = -420f,
            max = 420f,
            hasKeyframeHere = hasKfHere,
            onToggleKeyframe = onToggleKeyframe,
            onValueChange = { onUpdateLayer(layer.copy(offsetX = it)) }
        )
        AeInlineSliderRow(
            label = "Position Y",
            valueText = "${evaluated.offsetY.toInt()} px",
            value = evaluated.offsetY,
            min = -640f,
            max = 640f,
            hasKeyframeHere = hasKfHere,
            onToggleKeyframe = onToggleKeyframe,
            onValueChange = { onUpdateLayer(layer.copy(offsetY = it)) }
        )
        AeInlineSliderRow(
            label = "Scale",
            valueText = String.format(Locale.US, "%.2fx", evaluated.scale),
            value = evaluated.scale,
            min = 0.2f,
            max = 3.5f,
            hasKeyframeHere = hasKfHere,
            onToggleKeyframe = onToggleKeyframe,
            onValueChange = { onUpdateLayer(layer.copy(scale = it)) }
        )
        AeInlineSliderRow(
            label = "Rotation",
            valueText = "${evaluated.rotation.toInt()}°",
            value = evaluated.rotation,
            min = -180f,
            max = 180f,
            hasKeyframeHere = hasKfHere,
            onToggleKeyframe = onToggleKeyframe,
            onValueChange = { onUpdateLayer(layer.copy(rotation = it)) }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AeToggleChip(
                label = "Mirror H",
                active = layer.isMirrored,
                onClick = { onUpdateLayer(layer.copy(isMirrored = !layer.isMirrored)) },
                modifier = Modifier.weight(1f)
            )
            AeToggleChip(
                label = "Flip V",
                active = layer.isFlippedVertical,
                onClick = { onUpdateLayer(layer.copy(isFlippedVertical = !layer.isFlippedVertical)) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun AeBlendingInlinePanel(
    layer: EditorLayer,
    currentPositionMs: Long,
    onBack: () -> Unit,
    onExitToIdle: () -> Unit,
    onUpdateLayer: (EditorLayer) -> Unit,
    onToggleKeyframe: () -> Unit
) {
    val evaluated = AeEffectEngine.composeAeLayerTransformAt(layer, currentPositionMs)
    val dur = layer.effectiveLayerDurationMs.toFloat().coerceAtLeast(1f)
    val progress = ((currentPositionMs - layer.startTimeMs).toFloat() / dur).coerceIn(0f, 1f)
    val hasKfHere = layer.keyframes.any { abs(it.timeProgress - progress) <= 0.03f }

    AeInlinePanelFrame(
        title = "Opacity & Blending",
        subtitle = layer.displayTitle,
        testTag = "ae_blending_inline_panel",
        onBack = onBack,
        onExitToIdle = onExitToIdle,
        actionChipLabel = "100%",
        onActionChipClick = { onUpdateLayer(layer.copy(opacity = 1f, blendMode = EffectBlendMode.NORMAL)) }
    ) {
        AeInlineSliderRow(
            label = "Layer Opacity",
            valueText = "${(evaluated.opacity * 100).toInt()}%",
            value = evaluated.opacity,
            min = 0f,
            max = 1f,
            hasKeyframeHere = hasKfHere,
            onToggleKeyframe = onToggleKeyframe,
            onValueChange = { onUpdateLayer(layer.copy(opacity = it)) }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(InlineCardBg)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            PrecisionSegmentedOptionRow(
                label = "Blend",
                options = EffectBlendMode.entries,
                selectedOption = layer.blendMode,
                optionLabel = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                onSelectOption = { mode -> onUpdateLayer(layer.copy(blendMode = mode)) },
                accentColor = PrecisionGoldAccent,
                labelColor = InlineMutedText,
                trackColor = Color(0xFF15272C),
                activeContainerColor = Color(0xFF213C44)
            )
        }
    }
}

@Composable
internal fun AeBorderShadowInlinePanel(
    layer: EditorLayer,
    onBack: () -> Unit,
    onExitToIdle: () -> Unit,
    onUpdateLayer: (EditorLayer) -> Unit
) {
    val borderSwatches = listOf(0xFFFFFFFFL, 0xFF00E676L, 0xFFFF2D7AL, 0xFF38BDF8L, 0xFFFACC15L, 0xFF000000L)

    AeInlinePanelFrame(
        title = "Border & Shadow",
        subtitle = layer.displayTitle,
        testTag = "ae_border_shadow_inline_panel",
        onBack = onBack,
        onExitToIdle = onExitToIdle,
        actionChipLabel = "Clear",
        onActionChipClick = {
            onUpdateLayer(layer.copy(borderWidthDp = 0f, shadowOpacity = 0f))
        }
    ) {
        AeInlineSliderRow(
            label = "Stroke Width",
            valueText = "${layer.borderWidthDp.toInt()} dp",
            value = layer.borderWidthDp,
            min = 0f,
            max = 18f,
            onValueChange = { onUpdateLayer(layer.copy(borderWidthDp = it)) }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(InlineCardBg)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Border Color", fontFamily = InterFontFamily, fontSize = 12.sp, color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                borderSwatches.forEach { argb ->
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(argb.toInt()))
                            .border(
                                width = if (layer.borderColorArgb == argb) 2.dp else 1.dp,
                                color = if (layer.borderColorArgb == argb) InlineNeonGreen else Color.White.copy(alpha = 0.35f),
                                shape = CircleShape
                            )
                            .clickable { onUpdateLayer(layer.copy(borderColorArgb = argb)) }
                    )
                }
            }
        }

        AeInlineSliderRow(
            label = "Drop Shadow Opacity",
            valueText = "${(layer.shadowOpacity * 100).toInt()}%",
            value = layer.shadowOpacity,
            min = 0f,
            max = 1f,
            onValueChange = { onUpdateLayer(layer.copy(shadowOpacity = it)) }
        )
        AeInlineSliderRow(
            label = "Shadow Softness",
            valueText = "${layer.shadowRadiusDp.toInt()} dp",
            value = layer.shadowRadiusDp,
            min = 0f,
            max = 28f,
            onValueChange = { onUpdateLayer(layer.copy(shadowRadiusDp = it)) }
        )
    }
}

@Composable
internal fun AeTimingSpeedInlinePanel(
    layer: EditorLayer,
    currentPositionMs: Long,
    onBack: () -> Unit,
    onExitToIdle: () -> Unit,
    onUpdateLayer: (EditorLayer) -> Unit,
    onTrimStartToPlayhead: () -> Unit,
    onTrimEndToPlayhead: () -> Unit,
    onSplitAtPlayhead: () -> Unit
) {
    AeInlinePanelFrame(
        title = "Speed, Trim & Audio",
        subtitle = "${layer.displayTitle} • ${layer.effectiveLayerDurationMs}ms",
        testTag = "ae_timing_speed_inline_panel",
        onBack = onBack,
        onExitToIdle = onExitToIdle,
        actionChipLabel = "1.0x",
        onActionChipClick = { onUpdateLayer(layer.copy(speedMultiplier = 1f, audioVolume = 1f)) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AeActionButton(
                label = "Trim In",
                testTag = "ae_inline_trim_in_button",
                onClick = onTrimStartToPlayhead,
                modifier = Modifier.weight(1f)
            )
            AeActionButton(
                label = "Split Layer",
                testTag = "ae_inline_split_button",
                accent = true,
                onClick = onSplitAtPlayhead,
                modifier = Modifier.weight(1f)
            )
            AeActionButton(
                label = "Trim Out",
                testTag = "ae_inline_trim_out_button",
                onClick = onTrimEndToPlayhead,
                modifier = Modifier.weight(1f)
            )
        }

        AeInlineSliderRow(
            label = "Playback Speed",
            valueText = String.format(Locale.US, "%.2fx", layer.speedMultiplier),
            value = layer.speedMultiplier,
            min = 0.25f,
            max = 4.0f,
            onValueChange = { onUpdateLayer(layer.copy(speedMultiplier = it)) }
        )

        AeInlineSliderRow(
            label = "Layer Audio Volume",
            valueText = "${(layer.audioVolume * 100).toInt()}%",
            value = layer.audioVolume,
            min = 0f,
            max = 1.5f,
            onValueChange = { onUpdateLayer(layer.copy(audioVolume = it)) }
        )

        AeActionButton(
            label = "Align Layer Start to Playhead (${currentPositionMs}ms)",
            testTag = "ae_inline_align_start_button",
            onClick = { onUpdateLayer(layer.copy(startTimeMs = currentPositionMs.coerceAtLeast(0L))) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun AeAnimateInlinePanel(
    layer: EditorLayer,
    currentPositionMs: Long,
    onBack: () -> Unit,
    onExitToIdle: () -> Unit,
    onUpdateLayer: (EditorLayer) -> Unit,
    onToggleKeyframe: () -> Unit,
    onSeekToMs: (Long) -> Unit
) {
    val dur = layer.effectiveLayerDurationMs.coerceAtLeast(1L)
    val sorted = layer.keyframes.sortedBy { it.timeProgress }
    val curProg = ((currentPositionMs - layer.startTimeMs).toFloat() / dur.toFloat()).coerceIn(0f, 1f)
    val nearest = sorted.minByOrNull { abs(it.timeProgress - curProg) }
    val hasHere = nearest != null && abs(nearest.timeProgress - curProg) <= 0.03f

    AeInlinePanelFrame(
        title = "Animate & Keyframes",
        subtitle = "${sorted.size} keyframes on ${layer.displayTitle}",
        testTag = "ae_animate_inline_panel",
        onBack = onBack,
        onExitToIdle = onExitToIdle,
        actionChipLabel = if (hasHere) "Remove ◆" else "+ Keyframe",
        onActionChipClick = onToggleKeyframe
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(InlineCardBg)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                color = Color(0xFF172D33),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        val prev = sorted.lastOrNull { it.timeProgress < curProg - 0.02f }
                        if (prev != null) {
                            onSeekToMs(layer.startTimeMs + (prev.timeProgress * dur).toLong())
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("Prev ◆", fontFamily = InterFontFamily, fontSize = 11.sp, color = Color.White)
                }
            }

            Text(
                text = String.format(Locale.US, "%.0f%%", curProg * 100f),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = InlineNeonGreen
            )

            Surface(
                color = Color(0xFF172D33),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        val next = sorted.firstOrNull { it.timeProgress > curProg + 0.02f }
                        if (next != null) {
                            onSeekToMs(layer.startTimeMs + (next.timeProgress * dur).toLong())
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Next ◆", fontFamily = InterFontFamily, fontSize = 11.sp, color = Color.White)
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }

        val graphPresets = defaultGraphPresets.take(6)
        val targetKf = sorted.lastOrNull { it.timeProgress <= curProg + 0.03f } ?: sorted.firstOrNull()
        val selectedPreset = graphPresets.firstOrNull { targetKf?.curve == it.spec } ?: graphPresets.first()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(InlineCardBg)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            PrecisionSegmentedOptionRow(
                label = "Easing",
                options = graphPresets,
                selectedOption = selectedPreset,
                optionLabel = { it.name },
                onSelectOption = { preset ->
                    if (targetKf != null) {
                        onUpdateLayer(
                            layer.copy(
                                keyframes = layer.keyframes.map { kf ->
                                    if (kf.id == targetKf.id) kf.copy(curve = preset.spec) else kf
                                }
                            )
                        )
                    }
                },
                accentColor = PrecisionGoldAccent,
                labelColor = InlineMutedText,
                trackColor = Color(0xFF15272C),
                activeContainerColor = Color(0xFF213C44)
            )
        }
    }
}

@Composable
private fun AeInlineSliderRow(
    label: String,
    valueText: String,
    value: Float,
    min: Float,
    max: Float,
    hasKeyframeHere: Boolean = false,
    onToggleKeyframe: (() -> Unit)? = null,
    onValueChange: (Float) -> Unit
) {
    val safeMax = if (max <= min) min + 1f else max
    val haptics = rememberStudioTickHaptics()
    PrecisionRulerDialSlider(
        label = label,
        valueText = valueText,
        value = value.coerceIn(min, safeMax),
        onValueChange = onValueChange,
        valueRange = min..safeMax,
        accentColor = PrecisionGoldAccent,
        containerColor = Color(0xFF132227),
        labelContainerColor = PrecisionObsidianLabelBg,
        readoutContainerColor = PrecisionReadoutBg,
        height = 46.dp,
        trailingContent = if (onToggleKeyframe != null) {
            {
                Surface(
                    color = if (hasKeyframeHere) InlineNeonGreen.copy(alpha = 0.22f) else Color(0xFF1A2E34),
                    shape = RoundedCornerShape(7.dp),
                    border = BorderStroke(1.dp, if (hasKeyframeHere) InlineNeonGreen else Color(0xFF314D56)),
                    modifier = Modifier
                        .size(width = 32.dp, height = 34.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .clickable {
                            haptics.pulseSelection()
                            onToggleKeyframe()
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (hasKeyframeHere) "◆" else "◇",
                            fontSize = 14.sp,
                            color = if (hasKeyframeHere) InlineNeonGreen else Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        } else null
    )
}

@Composable
private fun AeToggleChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberStudioTickHaptics()
    Surface(
        color = if (active) InlineNeonGreen else InlineCardBg,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (active) InlineNeonGreen else Color(0xFF263E46)),
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                haptics.pulseSelection()
                onClick()
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = if (active) Color(0xFF04140B) else Color.White
            )
        }
    }
}

@Composable
private fun AeActionButton(
    label: String,
    testTag: String,
    accent: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberStudioTickHaptics()
    Surface(
        color = if (accent) InlineNeonGreen else Color(0xFF172D33),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                haptics.pulseSelection()
                onClick()
            }
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                text = label,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = if (accent) Color(0xFF04140B) else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
