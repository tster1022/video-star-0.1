package com.example.ui.screens.effects

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EffectBlendMode
import com.example.model.KeyframeEasing
import com.example.model.PARAM_KEY_INTENSITY
import com.example.model.PARAM_KEY_OPACITY
import com.example.model.arabicDisplayLabel
import com.example.model.arabicDisplayName
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.ClassicRed
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun SingleEffectEditorPanel(
    uiState: EffectsStudioUiState,
    onBackToStack: () -> Unit,
    onIntensityChange: (Float) -> Unit,
    onPassParamChange: (String, Float) -> Unit,
    onBlendModeChange: (EffectBlendMode) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onFocusKeyframeProperty: (String) -> Unit,
    onTogglePropertyKeyframe: (String) -> Unit,
    onUpdatePropertyEasing: (String, KeyframeEasing) -> Unit,
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
                    text = "لقائمة المؤثرات",
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary,
                    fontSize = 12.sp
                )
            }

            Text(
                text = catalogItem.arabicDisplayName(),
                fontFamily = CairoFontFamily,
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
                    contentDescription = "إعادة ضبط",
                    tint = ClassicRed,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "ضبط",
                    fontFamily = CairoFontFamily,
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
                    label = "الشدة",
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

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Slider(
                        value = uiState.activeEvaluatedIntensity,
                        onValueChange = onIntensityChange,
                        onValueChangeFinished = onParameterEditFinished,
                        valueRange = 0f..1f,
                        enabled = hasPass,
                        colors = SliderDefaults.colors(
                            thumbColor = colorScheme.primary,
                            activeTrackColor = colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .testTag("reeffect_intensity_slider")
                    )
                }

                PropertyKeyframeTimelineAndEasingStrip(
                    visible = intensityFocused && intensityKfs.isNotEmpty(),
                    propertyKey = PARAM_KEY_INTENSITY,
                    keyframes = intensityKfs,
                    previewProgress = uiState.previewProgress,
                    activeEasing = uiState.activeEasingFor(PARAM_KEY_INTENSITY),
                    onSeekProgress = onSeekProgress,
                    onSelectEasing = { easing -> onUpdatePropertyEasing(PARAM_KEY_INTENSITY, easing) },
                    onClearAllForProperty = { onClearPropertyKeyframes(PARAM_KEY_INTENSITY) }
                )
            }

            // 2. Shader Parameters + Per-Parameter ◆ Keyframe Controls
            catalogItem.params.forEach { param ->
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

                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Slider(
                            value = currentVal,
                            onValueChange = { onPassParamChange(param.key, it) },
                            onValueChangeFinished = onParameterEditFinished,
                            valueRange = param.minValue..param.maxValue,
                            enabled = hasPass,
                            colors = SliderDefaults.colors(
                                thumbColor = colorScheme.secondary,
                                activeTrackColor = colorScheme.secondary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(30.dp)
                                .testTag("effects_param_${param.key}")
                        )
                    }

                    PropertyKeyframeTimelineAndEasingStrip(
                        visible = paramFocused && paramKfs.isNotEmpty(),
                        propertyKey = param.key,
                        keyframes = paramKfs,
                        previewProgress = uiState.previewProgress,
                        activeEasing = uiState.activeEasingFor(param.key),
                        onSeekProgress = onSeekProgress,
                        onSelectEasing = { easing -> onUpdatePropertyEasing(param.key, easing) },
                        onClearAllForProperty = { onClearPropertyKeyframes(param.key) }
                    )
                }
            }

            // 3. Pass Opacity + ◆ Keyframe Controls
            val passOpacity = uiState.activeEvaluatedOpacity
            val opacityKfs = uiState.keyframesFor(PARAM_KEY_OPACITY)
            val opacityFocused = uiState.selectedKeyframePropertyKey == PARAM_KEY_OPACITY
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PropertyKeyframeRowHeader(
                    propertyKey = PARAM_KEY_OPACITY,
                    label = "الشفافية (المزج)",
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

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Slider(
                        value = passOpacity,
                        onValueChange = onOpacityChange,
                        onValueChangeFinished = onParameterEditFinished,
                        valueRange = 0f..1f,
                        enabled = hasPass,
                        colors = SliderDefaults.colors(
                            thumbColor = colorScheme.primary,
                            activeTrackColor = colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .testTag("effects_opacity_slider")
                    )
                }

                PropertyKeyframeTimelineAndEasingStrip(
                    visible = opacityFocused && opacityKfs.isNotEmpty(),
                    propertyKey = PARAM_KEY_OPACITY,
                    keyframes = opacityKfs,
                    previewProgress = uiState.previewProgress,
                    activeEasing = uiState.activeEasingFor(PARAM_KEY_OPACITY),
                    onSeekProgress = onSeekProgress,
                    onSelectEasing = { easing -> onUpdatePropertyEasing(PARAM_KEY_OPACITY, easing) },
                    onClearAllForProperty = { onClearPropertyKeyframes(PARAM_KEY_OPACITY) }
                )
            }

            // 4. Blend Mode Selector
            Text(
                text = "وضع المزج",
                fontFamily = CairoFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = EffectBlendMode.entries,
                    key = { it.id }
                ) { mode ->
                    val selected = activePass?.blendMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { onBlendModeChange(mode) },
                        label = {
                            Text(
                                text = mode.arabicLabel,
                                fontFamily = CairoFontFamily,
                                fontSize = 12.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colorScheme.primary,
                            selectedLabelColor = colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("effects_blend_${mode.id}")
                    )
                }
            }
        }
    }
}
