package com.example.ui.screens.effects

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.ui.components.PrecisionGoldAccent
import com.example.ui.components.PrecisionRulerDialSlider
import com.example.ui.components.PrecisionSegmentedOptionRow
import com.example.ui.components.rememberStudioTickHaptics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BezierCurveSpec
import com.example.model.EffectBlendMode
import com.example.model.EffectParamSpec
import com.example.model.EffectParamType
import com.example.model.KeyframeEasing
import com.example.model.PARAM_KEY_INTENSITY
import com.example.model.PARAM_KEY_OPACITY
import com.example.model.arabicDisplayLabel
import com.example.model.arabicDisplayName
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.ClassicRed
import com.example.ui.theme.InterFontFamily
import java.util.Locale
import kotlin.math.roundToInt

private val effectPaletteSwatches: List<Long> = listOf(
    0xFF00FF44L, // Chroma Green
    0xFF0055FFL, // Chroma Blue
    0xFFFF2A85L, // Neon Pink
    0xFF00E5FFL, // Cyan
    0xFFFFFFFFL, // White
    0xFF000000L, // Black
    0xFFFFD600L, // Gold
    0xFFFF5252L, // Crimson
    0xFFAA00FFL  // Purple
)

@Composable
internal fun SingleEffectEditorPanel(
    uiState: EffectsStudioUiState,
    onBackToStack: () -> Unit,
    onIntensityChange: (Float) -> Unit,
    onPassParamChange: (String, Float) -> Unit,
    onPassPoint2DChange: (String, String, Float, Float) -> Unit = { kx, ky, x, y ->
        onPassParamChange(kx, x)
        onPassParamChange(ky, y)
    },
    onPassColorChange: (String, Long) -> Unit = { _, _ -> },
    onPassTextChange: (String, String) -> Unit = { _, _ -> },
    onBlendModeChange: (EffectBlendMode) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onFocusKeyframeProperty: (String) -> Unit,
    onTogglePropertyKeyframe: (String) -> Unit,
    onUpdatePropertyEasing: (String, KeyframeEasing) -> Unit,
    onUpdatePropertyCustomBezier: (String, BezierCurveSpec) -> Unit = { _, _ -> },
    onJumpPrevKeyframe: (String) -> Unit,
    onJumpNextKeyframe: (String) -> Unit,
    onClearPropertyKeyframes: (String) -> Unit,
    onSeekProgress: (Float) -> Unit,
    onResetPass: () -> Unit,
    onParameterEditFinished: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val activePass = uiState.activePass
    val catalogItem = uiState.activeCatalogItem
    val hasPass = activePass != null
    val isEnglishAm = catalogItem.category.isAlightMotion

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                onClick = onBackToStack,
                modifier = Modifier.height(44.dp).testTag("effects_editor_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "لقائمة المؤثرات",
                    tint = colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isEnglishAm) "Effects" else "لقائمة المؤثرات",
                    fontFamily = if (isEnglishAm) InterFontFamily else CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary,
                    fontSize = 12.sp
                )
            }

            Text(
                text = catalogItem.arabicDisplayName(),
                fontFamily = if (isEnglishAm) InterFontFamily else CairoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            TextButton(
                onClick = onResetPass,
                enabled = hasPass,
                modifier = Modifier.height(44.dp).testTag("effects_editor_reset_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.RestartAlt,
                    contentDescription = "Reset",
                    tint = ClassicRed,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = if (isEnglishAm) "Reset" else "ضبط",
                    fontFamily = if (isEnglishAm) InterFontFamily else CairoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = ClassicRed,
                    fontSize = 12.sp
                )
            }
        }

        HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.16f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Effect Intensity + ◆ Keyframe Controls & Easing
            val intensityKfs = uiState.keyframesFor(PARAM_KEY_INTENSITY)
            val intensityFocused = uiState.selectedKeyframePropertyKey == PARAM_KEY_INTENSITY
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PropertyKeyframeRowHeader(
                    propertyKey = PARAM_KEY_INTENSITY,
                    label = if (isEnglishAm) "Intensity" else "الشدة",
                    valueDisplay = "${(uiState.activeEvaluatedIntensity * 100f).roundToInt()}%",
                    keyframes = intensityKfs,
                    hasKeyframeNear = uiState.hasKeyframeNear(PARAM_KEY_INTENSITY),
                    isFocused = intensityFocused,
                    enabled = hasPass,
                    onFocusProperty = { onFocusKeyframeProperty(PARAM_KEY_INTENSITY) },
                    onToggleKeyframe = { onTogglePropertyKeyframe(PARAM_KEY_INTENSITY) },
                    onJumpPrev = { onJumpPrevKeyframe(PARAM_KEY_INTENSITY) },
                    onJumpNext = { onJumpNextKeyframe(PARAM_KEY_INTENSITY) }
                )

                PrecisionRulerDialSlider(
                    label = if (isEnglishAm) "Intensity" else "الشدة",
                    valueText = "${(uiState.activeEvaluatedIntensity * 100f).roundToInt()}%",
                    value = uiState.activeEvaluatedIntensity,
                    onValueChange = onIntensityChange,
                    onValueChangeFinished = onParameterEditFinished,
                    valueRange = 0f..1f,
                    defaultValue = 1f,
                    enabled = hasPass,
                    sliderTestTag = "reeffect_intensity_slider"
                )

                PropertyKeyframeTimelineAndEasingStrip(
                    visible = intensityFocused && intensityKfs.isNotEmpty(),
                    propertyKey = PARAM_KEY_INTENSITY,
                    keyframes = intensityKfs,
                    previewProgress = uiState.previewProgress,
                    activeEasing = uiState.activeEasingFor(PARAM_KEY_INTENSITY),
                    customBezier = uiState.activeCustomBezierFor(PARAM_KEY_INTENSITY),
                    useEnglishLabels = isEnglishAm,
                    onSeekProgress = onSeekProgress,
                    onSelectEasing = { easing -> onUpdatePropertyEasing(PARAM_KEY_INTENSITY, easing) },
                    onUpdateCustomBezier = { spec -> onUpdatePropertyCustomBezier(PARAM_KEY_INTENSITY, spec) },
                    onClearAllForProperty = { onClearPropertyKeyframes(PARAM_KEY_INTENSITY) }
                )
            }

            // 2. Typed Shader Parameters (FLOAT, POINT_2D XY Pad, COLOR Picker, ENUM_MODE, BOOLEAN, TEXT_STRING)
            val consumedPairedYKeys = remember(catalogItem.id) { mutableSetOf<String>() }.apply { clear() }

            catalogItem.params.forEach { param ->
                if (param.paramType == EffectParamType.POINT_2D_Y && param.key in consumedPairedYKeys) {
                    return@forEach
                }

                when (param.paramType) {
                    EffectParamType.POINT_2D_X -> {
                        val pairedY = param.pairedKey?.let { pk ->
                            catalogItem.params.firstOrNull { it.key == pk }
                        }
                        if (pairedY != null) {
                            consumedPairedYKeys.add(pairedY.key)
                            Point2DInspectorBlock(
                                paramX = param,
                                paramY = pairedY,
                                uiState = uiState,
                                hasPass = hasPass,
                                useEnglishLabels = isEnglishAm,
                                onPointChange = { x, y -> onPassPoint2DChange(param.key, pairedY.key, x, y) },
                                onParamChange = onPassParamChange,
                                onParameterEditFinished = onParameterEditFinished,
                                onFocusKeyframeProperty = onFocusKeyframeProperty,
                                onTogglePropertyKeyframe = onTogglePropertyKeyframe,
                                onUpdatePropertyEasing = onUpdatePropertyEasing,
                                onUpdatePropertyCustomBezier = onUpdatePropertyCustomBezier,
                                onJumpPrevKeyframe = onJumpPrevKeyframe,
                                onJumpNextKeyframe = onJumpNextKeyframe,
                                onClearPropertyKeyframes = onClearPropertyKeyframes,
                                onSeekProgress = onSeekProgress
                            )
                        } else {
                            FloatParamInspectorBlock(
                                param = param,
                                uiState = uiState,
                                hasPass = hasPass,
                                useEnglishLabels = isEnglishAm,
                                onPassParamChange = onPassParamChange,
                                onParameterEditFinished = onParameterEditFinished,
                                onFocusKeyframeProperty = onFocusKeyframeProperty,
                                onTogglePropertyKeyframe = onTogglePropertyKeyframe,
                                onUpdatePropertyEasing = onUpdatePropertyEasing,
                                onUpdatePropertyCustomBezier = onUpdatePropertyCustomBezier,
                                onJumpPrevKeyframe = onJumpPrevKeyframe,
                                onJumpNextKeyframe = onJumpNextKeyframe,
                                onClearPropertyKeyframes = onClearPropertyKeyframes,
                                onSeekProgress = onSeekProgress
                            )
                        }
                    }

                    EffectParamType.COLOR -> {
                        ColorParamInspectorBlock(
                            param = param,
                            uiState = uiState,
                            hasPass = hasPass,
                            useEnglishLabels = isEnglishAm,
                            onColorChange = { argb -> onPassColorChange(param.key, argb) },
                            onParameterEditFinished = onParameterEditFinished,
                            onFocusKeyframeProperty = onFocusKeyframeProperty,
                            onTogglePropertyKeyframe = onTogglePropertyKeyframe,
                            onUpdatePropertyEasing = onUpdatePropertyEasing,
                            onUpdatePropertyCustomBezier = onUpdatePropertyCustomBezier,
                            onJumpPrevKeyframe = onJumpPrevKeyframe,
                            onJumpNextKeyframe = onJumpNextKeyframe,
                            onClearPropertyKeyframes = onClearPropertyKeyframes,
                            onSeekProgress = onSeekProgress
                        )
                    }

                    EffectParamType.ENUM_MODE -> {
                        val currentMode = uiState.evaluatedParamValue(param).roundToInt()
                            .coerceIn(0, (param.enumOptions.size - 1).coerceAtLeast(0))
                        val paramKfs = uiState.keyframesFor(param.key)
                        val paramFocused = uiState.selectedKeyframePropertyKey == param.key
                        val modeLabel = param.enumOptions.getOrElse(currentMode) { "$currentMode" }

                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            PropertyKeyframeRowHeader(
                                propertyKey = param.key,
                                label = param.arabicDisplayLabel(),
                                valueDisplay = modeLabel,
                                keyframes = paramKfs,
                                hasKeyframeNear = uiState.hasKeyframeNear(param.key),
                                isFocused = paramFocused,
                                enabled = hasPass,
                                onFocusProperty = { onFocusKeyframeProperty(param.key) },
                                onToggleKeyframe = { onTogglePropertyKeyframe(param.key) },
                                onJumpPrev = { onJumpPrevKeyframe(param.key) },
                                onJumpNext = { onJumpNextKeyframe(param.key) }
                            )
                            val indexedOptions = param.enumOptions.mapIndexed { idx, opt -> idx to opt }
                            if (indexedOptions.isNotEmpty()) {
                                PrecisionSegmentedOptionRow(
                                    label = null,
                                    options = indexedOptions,
                                    selectedOption = indexedOptions[currentMode],
                                    optionLabel = { it.second },
                                    enabled = hasPass,
                                    onSelectOption = { (idx, _) ->
                                        if (hasPass) {
                                            onPassParamChange(param.key, idx.toFloat())
                                            onParameterEditFinished()
                                        }
                                    },
                                    optionTestTag = { idx, _ -> "effects_mode_${param.key}_$idx" },
                                    modifier = Modifier.testTag("effects_param_${param.key}")
                                )
                            }
                            PropertyKeyframeTimelineAndEasingStrip(
                                visible = paramFocused && paramKfs.isNotEmpty(),
                                propertyKey = param.key,
                                keyframes = paramKfs,
                                previewProgress = uiState.previewProgress,
                                activeEasing = uiState.activeEasingFor(param.key),
                                customBezier = uiState.activeCustomBezierFor(param.key),
                                useEnglishLabels = isEnglishAm,
                                onSeekProgress = onSeekProgress,
                                onSelectEasing = { easing -> onUpdatePropertyEasing(param.key, easing) },
                                onUpdateCustomBezier = { spec -> onUpdatePropertyCustomBezier(param.key, spec) },
                                onClearAllForProperty = { onClearPropertyKeyframes(param.key) }
                            )
                        }
                    }

                    EffectParamType.BOOLEAN -> {
                        val isChecked = uiState.evaluatedParamValue(param) >= 0.5f
                        val paramKfs = uiState.keyframesFor(param.key)
                        val paramFocused = uiState.selectedKeyframePropertyKey == param.key
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            PropertyKeyframeRowHeader(
                                propertyKey = param.key,
                                label = param.arabicDisplayLabel(),
                                valueDisplay = if (isChecked) "On" else "Off",
                                keyframes = paramKfs,
                                hasKeyframeNear = uiState.hasKeyframeNear(param.key),
                                isFocused = paramFocused,
                                enabled = hasPass,
                                onFocusProperty = { onFocusKeyframeProperty(param.key) },
                                onToggleKeyframe = { onTogglePropertyKeyframe(param.key) },
                                onJumpPrev = { onJumpPrevKeyframe(param.key) },
                                onJumpNext = { onJumpNextKeyframe(param.key) }
                            )
                            val haptics = rememberStudioTickHaptics()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isChecked) "Enabled" else "Disabled",
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurface
                                )
                                Switch(
                                    checked = isChecked,
                                    enabled = hasPass,
                                    onCheckedChange = { checked ->
                                        haptics.pulseSelection()
                                        onPassParamChange(param.key, if (checked) 1f else 0f)
                                        onParameterEditFinished()
                                    },
                                    colors = SwitchDefaults.colors(checkedTrackColor = colorScheme.secondary),
                                    modifier = Modifier.testTag("effects_param_${param.key}")
                                )
                            }
                        }
                    }

                    EffectParamType.TEXT_STRING -> {
                        val textVal = uiState.evaluatedTextValue(param)
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = param.arabicDisplayLabel(),
                                fontFamily = InterFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface
                            )
                            OutlinedTextField(
                                value = textVal,
                                onValueChange = { onPassTextChange(param.key, it) },
                                singleLine = true,
                                enabled = hasPass,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("effects_param_${param.key}")
                            )
                        }
                    }

                    EffectParamType.FLOAT,
                    EffectParamType.POINT_2D_Y -> {
                        FloatParamInspectorBlock(
                            param = param,
                            uiState = uiState,
                            hasPass = hasPass,
                            useEnglishLabels = isEnglishAm,
                            onPassParamChange = onPassParamChange,
                            onParameterEditFinished = onParameterEditFinished,
                            onFocusKeyframeProperty = onFocusKeyframeProperty,
                            onTogglePropertyKeyframe = onTogglePropertyKeyframe,
                            onUpdatePropertyEasing = onUpdatePropertyEasing,
                            onUpdatePropertyCustomBezier = onUpdatePropertyCustomBezier,
                            onJumpPrevKeyframe = onJumpPrevKeyframe,
                            onJumpNextKeyframe = onJumpNextKeyframe,
                            onClearPropertyKeyframes = onClearPropertyKeyframes,
                            onSeekProgress = onSeekProgress
                        )
                    }
                }
            }

            // 3. Pass Opacity + ◆ Keyframe Controls
            val passOpacity = uiState.activeEvaluatedOpacity
            val opacityKfs = uiState.keyframesFor(PARAM_KEY_OPACITY)
            val opacityFocused = uiState.selectedKeyframePropertyKey == PARAM_KEY_OPACITY
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PropertyKeyframeRowHeader(
                    propertyKey = PARAM_KEY_OPACITY,
                    label = if (isEnglishAm) "Pass Opacity" else "الشفافية (المزج)",
                    valueDisplay = "${(passOpacity * 100f).roundToInt()}%",
                    keyframes = opacityKfs,
                    hasKeyframeNear = uiState.hasKeyframeNear(PARAM_KEY_OPACITY),
                    isFocused = opacityFocused,
                    enabled = hasPass,
                    onFocusProperty = { onFocusKeyframeProperty(PARAM_KEY_OPACITY) },
                    onToggleKeyframe = { onTogglePropertyKeyframe(PARAM_KEY_OPACITY) },
                    onJumpPrev = { onJumpPrevKeyframe(PARAM_KEY_OPACITY) },
                    onJumpNext = { onJumpNextKeyframe(PARAM_KEY_OPACITY) }
                )

                PrecisionRulerDialSlider(
                    label = if (isEnglishAm) "Opacity" else "الشفافية",
                    valueText = "${(passOpacity * 100f).roundToInt()}%",
                    value = passOpacity,
                    onValueChange = onOpacityChange,
                    onValueChangeFinished = onParameterEditFinished,
                    valueRange = 0f..1f,
                    defaultValue = 1f,
                    enabled = hasPass,
                    sliderTestTag = "effects_opacity_slider"
                )

                PropertyKeyframeTimelineAndEasingStrip(
                    visible = opacityFocused && opacityKfs.isNotEmpty(),
                    propertyKey = PARAM_KEY_OPACITY,
                    keyframes = opacityKfs,
                    previewProgress = uiState.previewProgress,
                    activeEasing = uiState.activeEasingFor(PARAM_KEY_OPACITY),
                    customBezier = uiState.activeCustomBezierFor(PARAM_KEY_OPACITY),
                    useEnglishLabels = isEnglishAm,
                    onSeekProgress = onSeekProgress,
                    onSelectEasing = { easing -> onUpdatePropertyEasing(PARAM_KEY_OPACITY, easing) },
                    onUpdateCustomBezier = { spec -> onUpdatePropertyCustomBezier(PARAM_KEY_OPACITY, spec) },
                    onClearAllForProperty = { onClearPropertyKeyframes(PARAM_KEY_OPACITY) }
                )
            }

            // 4. Blend Mode Segmented Strip
            PrecisionSegmentedOptionRow(
                label = if (isEnglishAm) "Blend Mode" else "وضع المزج",
                options = EffectBlendMode.entries,
                selectedOption = activePass?.blendMode ?: EffectBlendMode.NORMAL,
                optionLabel = { it.arabicLabel },
                onSelectOption = onBlendModeChange,
                enabled = hasPass,
                labelColor = colorScheme.onSurface,
                optionTestTag = { _, mode -> "effects_blend_${mode.id}" },
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun FloatParamInspectorBlock(
    param: EffectParamSpec,
    uiState: EffectsStudioUiState,
    hasPass: Boolean,
    useEnglishLabels: Boolean,
    onPassParamChange: (String, Float) -> Unit,
    onParameterEditFinished: () -> Unit,
    onFocusKeyframeProperty: (String) -> Unit,
    onTogglePropertyKeyframe: (String) -> Unit,
    onUpdatePropertyEasing: (String, KeyframeEasing) -> Unit,
    onUpdatePropertyCustomBezier: (String, BezierCurveSpec) -> Unit,
    onJumpPrevKeyframe: (String) -> Unit,
    onJumpNextKeyframe: (String) -> Unit,
    onClearPropertyKeyframes: (String) -> Unit,
    onSeekProgress: (Float) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val currentVal = uiState.evaluatedParamValue(param)
    val paramKfs = uiState.keyframesFor(param.key)
    val paramFocused = uiState.selectedKeyframePropertyKey == param.key

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PropertyKeyframeRowHeader(
            propertyKey = param.key,
            label = param.arabicDisplayLabel(),
            valueDisplay = String.format(Locale.US, "%.2f%s", currentVal, param.unitSuffix),
            keyframes = paramKfs,
            hasKeyframeNear = uiState.hasKeyframeNear(param.key),
            isFocused = paramFocused,
            enabled = hasPass,
            onFocusProperty = { onFocusKeyframeProperty(param.key) },
            onToggleKeyframe = { onTogglePropertyKeyframe(param.key) },
            onJumpPrev = { onJumpPrevKeyframe(param.key) },
            onJumpNext = { onJumpNextKeyframe(param.key) }
        )

        PrecisionRulerDialSlider(
            label = param.arabicDisplayLabel(),
            valueText = String.format(Locale.US, "%.2f%s", currentVal, param.unitSuffix),
            value = currentVal,
            onValueChange = { onPassParamChange(param.key, it) },
            onValueChangeFinished = onParameterEditFinished,
            valueRange = param.minValue..param.maxValue,
            defaultValue = param.defaultValue,
            enabled = hasPass,
            sliderTestTag = "effects_param_${param.key}"
        )

        PropertyKeyframeTimelineAndEasingStrip(
            visible = paramFocused && paramKfs.isNotEmpty(),
            propertyKey = param.key,
            keyframes = paramKfs,
            previewProgress = uiState.previewProgress,
            activeEasing = uiState.activeEasingFor(param.key),
            customBezier = uiState.activeCustomBezierFor(param.key),
            useEnglishLabels = useEnglishLabels,
            onSeekProgress = onSeekProgress,
            onSelectEasing = { easing -> onUpdatePropertyEasing(param.key, easing) },
            onUpdateCustomBezier = { spec -> onUpdatePropertyCustomBezier(param.key, spec) },
            onClearAllForProperty = { onClearPropertyKeyframes(param.key) }
        )
    }
}

@Composable
private fun Point2DInspectorBlock(
    paramX: EffectParamSpec,
    paramY: EffectParamSpec,
    uiState: EffectsStudioUiState,
    hasPass: Boolean,
    useEnglishLabels: Boolean,
    onPointChange: (Float, Float) -> Unit,
    onParamChange: (String, Float) -> Unit,
    onParameterEditFinished: () -> Unit,
    onFocusKeyframeProperty: (String) -> Unit,
    onTogglePropertyKeyframe: (String) -> Unit,
    onUpdatePropertyEasing: (String, KeyframeEasing) -> Unit,
    onUpdatePropertyCustomBezier: (String, BezierCurveSpec) -> Unit,
    onJumpPrevKeyframe: (String) -> Unit,
    onJumpNextKeyframe: (String) -> Unit,
    onClearPropertyKeyframes: (String) -> Unit,
    onSeekProgress: (Float) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val curX = uiState.evaluatedParamValue(paramX)
    val curY = uiState.evaluatedParamValue(paramY)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.28f),
        border = BorderStroke(0.8.dp, colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${paramX.label} / ${paramY.label}",
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                IconButton(
                    onClick = {
                        if (hasPass) {
                            onPointChange(paramX.defaultValue, paramY.defaultValue)
                            onParameterEditFinished()
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CenterFocusStrong,
                        contentDescription = "Center 2D Point",
                        tint = colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colorScheme.surface.copy(alpha = 0.8f))
                            .border(1.dp, colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .pointerInput(hasPass, paramX.key, paramY.key) {
                                if (!hasPass) return@pointerInput
                                detectTapGestures { offset ->
                                    val nx = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    val ny = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                                    onPointChange(nx, ny)
                                    onParameterEditFinished()
                                }
                            }
                            .pointerInput(hasPass, paramX.key, paramY.key) {
                                if (!hasPass) return@pointerInput
                                detectDragGestures(
                                    onDragEnd = onParameterEditFinished
                                ) { change, _ ->
                                    change.consume()
                                    val nx = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    val ny = (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                                    onPointChange(nx, ny)
                                }
                            }
                            .testTag("effects_xy_pad_${paramX.key}")
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawLine(
                                color = colorScheme.outline.copy(alpha = 0.25f),
                                start = Offset(size.width * 0.5f, 0f),
                                end = Offset(size.width * 0.5f, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                            drawLine(
                                color = colorScheme.outline.copy(alpha = 0.25f),
                                start = Offset(0f, size.height * 0.5f),
                                end = Offset(size.width, size.height * 0.5f),
                                strokeWidth = 1.dp.toPx()
                            )
                            val cx = curX.coerceIn(0f, 1f) * size.width
                            val cy = curY.coerceIn(0f, 1f) * size.height
                            drawLine(
                                color = colorScheme.primary.copy(alpha = 0.4f),
                                start = Offset(size.width * 0.5f, size.height * 0.5f),
                                end = Offset(cx, cy),
                                strokeWidth = 1.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            drawCircle(
                                color = colorScheme.primary,
                                radius = 6.dp.toPx(),
                                center = Offset(cx, cy)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 5.5.dp.toPx(),
                                center = Offset(cx, cy),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FloatParamInspectorBlock(
                        param = paramX,
                        uiState = uiState,
                        hasPass = hasPass,
                        useEnglishLabels = useEnglishLabels,
                        onPassParamChange = onParamChange,
                        onParameterEditFinished = onParameterEditFinished,
                        onFocusKeyframeProperty = onFocusKeyframeProperty,
                        onTogglePropertyKeyframe = onTogglePropertyKeyframe,
                        onUpdatePropertyEasing = onUpdatePropertyEasing,
                        onUpdatePropertyCustomBezier = onUpdatePropertyCustomBezier,
                        onJumpPrevKeyframe = onJumpPrevKeyframe,
                        onJumpNextKeyframe = onJumpNextKeyframe,
                        onClearPropertyKeyframes = onClearPropertyKeyframes,
                        onSeekProgress = onSeekProgress
                    )
                    FloatParamInspectorBlock(
                        param = paramY,
                        uiState = uiState,
                        hasPass = hasPass,
                        useEnglishLabels = useEnglishLabels,
                        onPassParamChange = onParamChange,
                        onParameterEditFinished = onParameterEditFinished,
                        onFocusKeyframeProperty = onFocusKeyframeProperty,
                        onTogglePropertyKeyframe = onTogglePropertyKeyframe,
                        onUpdatePropertyEasing = onUpdatePropertyEasing,
                        onUpdatePropertyCustomBezier = onUpdatePropertyCustomBezier,
                        onJumpPrevKeyframe = onJumpPrevKeyframe,
                        onJumpNextKeyframe = onJumpNextKeyframe,
                        onClearPropertyKeyframes = onClearPropertyKeyframes,
                        onSeekProgress = onSeekProgress
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorParamInspectorBlock(
    param: EffectParamSpec,
    uiState: EffectsStudioUiState,
    hasPass: Boolean,
    useEnglishLabels: Boolean,
    onColorChange: (Long) -> Unit,
    onParameterEditFinished: () -> Unit,
    onFocusKeyframeProperty: (String) -> Unit,
    onTogglePropertyKeyframe: (String) -> Unit,
    onUpdatePropertyEasing: (String, KeyframeEasing) -> Unit,
    onUpdatePropertyCustomBezier: (String, BezierCurveSpec) -> Unit,
    onJumpPrevKeyframe: (String) -> Unit,
    onJumpNextKeyframe: (String) -> Unit,
    onClearPropertyKeyframes: (String) -> Unit,
    onSeekProgress: (Float) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val currentArgb = uiState.evaluatedColorValue(param)
    val cInt = currentArgb.toInt()
    val rVal = AndroidColor.red(cInt)
    val gVal = AndroidColor.green(cInt)
    val bVal = AndroidColor.blue(cInt)
    val hexLabel = String.format(Locale.US, "#%02X%02X%02X", rVal, gVal, bVal)
    val paramKfs = uiState.keyframesFor(param.key)
    val paramFocused = uiState.selectedKeyframePropertyKey == param.key
    var showRgbSliders by remember(param.key) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PropertyKeyframeRowHeader(
            propertyKey = param.key,
            label = param.arabicDisplayLabel(),
            valueDisplay = hexLabel,
            keyframes = paramKfs,
            hasKeyframeNear = uiState.hasKeyframeNear(param.key),
            isFocused = paramFocused,
            enabled = hasPass,
            onFocusProperty = { onFocusKeyframeProperty(param.key) },
            onToggleKeyframe = { onTogglePropertyKeyframe(param.key) },
            onJumpPrev = { onJumpPrevKeyframe(param.key) },
            onJumpNext = { onJumpNextKeyframe(param.key) }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("effects_param_${param.key}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(cInt))
                    .border(1.2.dp, colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .clickable(enabled = hasPass) { showRgbSliders = !showRgbSliders }
            )
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(effectPaletteSwatches) { swatchArgb ->
                    val selected = (swatchArgb.toInt() and 0xFFFFFF) == (cInt and 0xFFFFFF)
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(swatchArgb.toInt()))
                            .border(
                                width = if (selected) 2.dp else 0.8.dp,
                                color = if (selected) colorScheme.primary else colorScheme.outline.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                            .clickable(enabled = hasPass) {
                                onColorChange(swatchArgb)
                                onParameterEditFinished()
                            }
                    )
                }
            }
            IconButton(
                onClick = { showRgbSliders = !showRgbSliders },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Palette,
                    contentDescription = "Custom RGB Color",
                    tint = if (showRgbSliders) colorScheme.primary else colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        AnimatedVisibility(visible = showRgbSliders) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        RgbChannelSliderRow("R", rVal, Color(0xFFFF5252), hasPass) { newR ->
                            val next = (AndroidColor.argb(255, newR, gVal, bVal).toLong() and 0xFFFFFFFFL)
                            onColorChange(next)
                        }
                        RgbChannelSliderRow("G", gVal, Color(0xFF00E676), hasPass) { newG ->
                            val next = (AndroidColor.argb(255, rVal, newG, bVal).toLong() and 0xFFFFFFFFL)
                            onColorChange(next)
                        }
                        RgbChannelSliderRow("B", bVal, Color(0xFF448AFF), hasPass) { newB ->
                            val next = (AndroidColor.argb(255, rVal, gVal, newB).toLong() and 0xFFFFFFFFL)
                            onColorChange(next)
                        }
                    }
                }
            }
        }

        PropertyKeyframeTimelineAndEasingStrip(
            visible = paramFocused && paramKfs.isNotEmpty(),
            propertyKey = param.key,
            keyframes = paramKfs,
            previewProgress = uiState.previewProgress,
            activeEasing = uiState.activeEasingFor(param.key),
            customBezier = uiState.activeCustomBezierFor(param.key),
            useEnglishLabels = useEnglishLabels,
            onSeekProgress = onSeekProgress,
            onSelectEasing = { easing -> onUpdatePropertyEasing(param.key, easing) },
            onUpdateCustomBezier = { spec -> onUpdatePropertyCustomBezier(param.key, spec) },
            onClearAllForProperty = { onClearPropertyKeyframes(param.key) }
        )
    }
}

@Composable
private fun RgbChannelSliderRow(
    channelLabel: String,
    value: Int,
    accent: Color,
    enabled: Boolean,
    onChange: (Int) -> Unit
) {
    PrecisionRulerDialSlider(
        label = channelLabel,
        valueText = value.toString(),
        value = value.toFloat(),
        onValueChange = { onChange(it.roundToInt().coerceIn(0, 255)) },
        valueRange = 0f..255f,
        enabled = enabled,
        accentColor = accent,
        height = 38.dp,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}
