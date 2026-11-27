package com.example.ui.screens.effects

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.BezierCurveSpec
import com.example.model.EffectBlendMode
import com.example.model.EffectCatalogItem
import com.example.model.EffectStudioTab
import com.example.model.KeyframeEasing

@Composable
fun EffectsStudioBottomSheet(
    uiState: EffectsStudioUiState,
    sceneThumbnail: ImageBitmap?,
    isExpanded: Boolean,
    onToggleExpand: (Boolean) -> Unit,
    onOpenLibrary: () -> Unit,
    onBackToStack: () -> Unit,
    onOpenPassEditor: (Int) -> Unit,
    onTogglePassEnabled: (Int) -> Unit,
    onDuplicatePass: (Int) -> Unit,
    onRemovePass: (Int) -> Unit,
    onBeginPassReorder: () -> Unit = {},
    onMovePass: (Int, Int) -> Unit,
    onEndPassReorder: () -> Unit = {},
    onClearAllPasses: () -> Unit,
    onAddEffectFromLibrary: (EffectCatalogItem) -> Unit,
    onToggleFavoriteEffect: (String) -> Unit,
    onSelectTab: (EffectStudioTab) -> Unit,
    onToggleFavoritesFilter: () -> Unit,
    onToggleSearch: () -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onPassParamChange: (String, Float) -> Unit,
    onPassPoint2DChange: (String, String, Float, Float) -> Unit = { _, _, _, _ -> },
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
    onParameterEditFinished: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    var dragAccum by remember { mutableFloatStateOf(0f) }

    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = colorScheme.surface,
        shadowElevation = 10.dp,
        border = BorderStroke(0.8.dp, colorScheme.outline.copy(alpha = 0.25f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("effects_studio_bottom_sheet")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .pointerInput(isExpanded) {
                        detectVerticalDragGestures(
                            onDragEnd = { dragAccum = 0f },
                            onVerticalDrag = { _, dragAmount ->
                                dragAccum += dragAmount
                                if (dragAccum < -36f && !isExpanded) {
                                    onToggleExpand(true)
                                    dragAccum = 0f
                                } else if (dragAccum > 36f && isExpanded) {
                                    onToggleExpand(false)
                                    dragAccum = 0f
                                }
                            }
                        )
                    }
                    .clickable { onToggleExpand(!isExpanded) }
                    .testTag("effects_sheet_drag_handle"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 42.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                )
            }

            AnimatedContent(
                targetState = uiState.panelLevel,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "studio_panel_level",
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { level ->
                when (level) {
                    StudioPanelLevel.STACK -> {
                        EffectStackPanel(
                            uiState = uiState,
                            sceneThumbnail = sceneThumbnail,
                            onOpenLibrary = onOpenLibrary,
                            onOpenPassEditor = onOpenPassEditor,
                            onTogglePassEnabled = onTogglePassEnabled,
                            onDuplicatePass = onDuplicatePass,
                            onRemovePass = onRemovePass,
                            onBeginPassReorder = onBeginPassReorder,
                            onMovePass = onMovePass,
                            onEndPassReorder = onEndPassReorder,
                            onClearAllPasses = onClearAllPasses
                        )
                    }

                    StudioPanelLevel.LIBRARY -> {
                        EffectLibraryPanel(
                            uiState = uiState,
                            sceneThumbnail = sceneThumbnail,
                            onBackToStack = onBackToStack,
                            onSelectEffect = onAddEffectFromLibrary,
                            onToggleFavoriteEffect = onToggleFavoriteEffect,
                            onSelectTab = onSelectTab,
                            onToggleFavoritesFilter = onToggleFavoritesFilter,
                            onToggleSearch = onToggleSearch,
                            onUpdateSearchQuery = onUpdateSearchQuery
                        )
                    }

                    StudioPanelLevel.EDITOR -> {
                        SingleEffectEditorPanel(
                            uiState = uiState,
                            onBackToStack = onBackToStack,
                            onIntensityChange = onIntensityChange,
                            onPassParamChange = onPassParamChange,
                            onPassPoint2DChange = onPassPoint2DChange,
                            onPassColorChange = onPassColorChange,
                            onPassTextChange = onPassTextChange,
                            onBlendModeChange = onBlendModeChange,
                            onOpacityChange = onOpacityChange,
                            onFocusKeyframeProperty = onFocusKeyframeProperty,
                            onTogglePropertyKeyframe = onTogglePropertyKeyframe,
                            onUpdatePropertyEasing = onUpdatePropertyEasing,
                            onUpdatePropertyCustomBezier = onUpdatePropertyCustomBezier,
                            onJumpPrevKeyframe = onJumpPrevKeyframe,
                            onJumpNextKeyframe = onJumpNextKeyframe,
                            onClearPropertyKeyframes = onClearPropertyKeyframes,
                            onSeekProgress = onSeekProgress,
                            onResetPass = onResetPass,
                            onParameterEditFinished = onParameterEditFinished
                        )
                    }
                }
            }
        }
    }
}
