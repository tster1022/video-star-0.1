package com.example.ui.screens.effects

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.SceneRenderStage
import com.example.ui.components.geometricTriangleBackground
import com.example.ui.components.rememberSceneSampleThumbnail
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.ClassicGreen
import com.example.ui.theme.ClassicRed
import com.example.ui.theme.LocalVideoStarColors

@Composable
fun EffectsStudioScreen(
    projectId: String,
    sceneIndices: List<Int> = listOf(0),
    targetLayerId: String? = null,
    isDarkTheme: Boolean = false,
    catalogScope: com.example.model.EffectCatalogScope = com.example.model.EffectCatalogScope.ALL,
    onCancel: () -> Unit,
    onCreated: () -> Unit
) {
    val keySuffix = "${projectId}_${sceneIndices.joinToString("_")}_${targetLayerId ?: "scene"}_${catalogScope.name}"
    val viewModel: EffectsStudioViewModel = viewModel(
        key = "effects_studio_$keySuffix",
        factory = remember(keySuffix) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return EffectsStudioViewModel(
                        projectId = projectId,
                        sceneIndices = sceneIndices,
                        targetLayerId = targetLayerId,
                        catalogScope = catalogScope
                    ) as T
                }
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sceneThumbnail = rememberSceneSampleThumbnail(uiState.previewClip)
    val colorScheme = MaterialTheme.colorScheme
    val extraColors = LocalVideoStarColors.current
    var isSheetExpanded by remember { mutableStateOf(false) }

    val previewWeight by animateFloatAsState(
        targetValue = if (isSheetExpanded) 0.29f else 0.39f,
        label = "preview_height_fraction"
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = if (isDarkTheme) colorScheme.background else Color.Transparent,
            topBar = {
                EffectsStudioTopBar(
                    layerTitle = uiState.layerDisplayTitle,
                    canUndo = uiState.canUndo,
                    canRedo = uiState.canRedo,
                    undoActionLabel = uiState.undoActionLabel,
                    redoActionLabel = uiState.redoActionLabel,
                    isDarkTheme = isDarkTheme,
                    onCancel = onCancel,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onDone = { viewModel.createAndSave(onCreated) }
                )
            }
        ) { innerPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isDarkTheme) Modifier.background(colorScheme.background)
                        else Modifier.geometricTriangleBackground()
                    )
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            ) {
                val previewContainerHeight = (maxHeight * previewWeight).coerceAtLeast(156.dp)

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 2. Live WebGL/GLSL Aspect-Ratio Video Preview (40–45% of screen height)
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(previewContainerHeight)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val availW = maxWidth
                        val availH = maxHeight
                        val targetRatio = uiState.aspectRatio.ratio.coerceIn(0.35f, 2.2f)
                        val availRatio = if (availH > 0.dp) availW / availH else targetRatio
                        val (stageW, stageH) = if (availRatio > targetRatio) {
                            (availH * targetRatio) to availH
                        } else {
                            availW to (availW / targetRatio)
                        }

                        Box(
                            modifier = Modifier
                                .size(width = stageW, height = stageH)
                                .shadow(elevation = 6.dp, shape = RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black)
                                .border(
                                    width = 1.dp,
                                    color = colorScheme.outline.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable(onClick = viewModel::togglePlayback)
                                .testTag("reeffect_preview_stage")
                        ) {
                            SceneRenderStage(
                                scene = uiState.previewScene,
                                localPositionMs = uiState.previewLocalMs,
                                isPlaying = uiState.isPlaying,
                                useLiveVideoPlayer = true,
                                forceGlStage = true,
                                contentDescription = "معاينة استوديو المؤثرات",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // 3. Playback Control Strip (Play/Pause, Time Scrubber with Keyframe diamonds, Keyframe ◆ button)
                    EffectsPlaybackControlStrip(
                        uiState = uiState,
                        isDarkTheme = isDarkTheme,
                        onTogglePlay = viewModel::togglePlayback,
                        onScrub = viewModel::seekToProgress,
                        onToggleKeyframe = viewModel::toggleKeyframeAtCurrentTime,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    // 3b. Mini Interactive Keyframe Timeline (Drag ◆ to move time, Long-Press to delete)
                    EffectsKeyframeMiniTimeline(
                        uiState = uiState,
                        isDarkTheme = isDarkTheme,
                        onSeekProgress = viewModel::seekToProgress,
                        onQuickAddKeyframe = viewModel::toggleKeyframeAtCurrentTime,
                        onSelectAndSeekMarker = viewModel::selectAndSeekKeyframe,
                        onBeginDrag = viewModel::beginKeyframeDrag,
                        onMoveKeyframeTime = { propKey, kfId, newTime ->
                            viewModel.moveKeyframeTime(propKey, kfId, newTime)
                        },
                        onEndDrag = viewModel::endKeyframeDrag,
                        onDeleteKeyframe = viewModel::deleteKeyframe,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    // 4. Draggable Bottom Sheet (Effect Stack / Library / Single Effect Editor)
                    EffectsStudioBottomSheet(
                        uiState = uiState,
                        sceneThumbnail = sceneThumbnail,
                        isExpanded = isSheetExpanded,
                        onToggleExpand = { isSheetExpanded = it },
                        onOpenLibrary = viewModel::openEffectLibrary,
                        onBackToStack = viewModel::backToEffectStack,
                        onOpenPassEditor = viewModel::openPassEditor,
                        onTogglePassEnabled = viewModel::togglePassEnabled,
                        onDuplicatePass = viewModel::duplicatePass,
                        onRemovePass = viewModel::removePass,
                        onBeginPassReorder = viewModel::beginPassReorderDrag,
                        onMovePass = { from, to -> viewModel.movePass(from, to, recordUndo = false) },
                        onEndPassReorder = viewModel::endPassReorderDrag,
                        onClearAllPasses = viewModel::resetEffects,
                        onAddEffectFromLibrary = viewModel::addEffectFromLibrary,
                        onToggleFavoriteEffect = viewModel::toggleFavoriteEffect,
                        onSelectTab = viewModel::selectTab,
                        onToggleFavoritesFilter = viewModel::toggleFavoritesFilter,
                        onToggleSearch = viewModel::toggleSearch,
                        onUpdateSearchQuery = viewModel::updateSearchQuery,
                        onIntensityChange = viewModel::updateIntensity,
                        onPassParamChange = viewModel::updatePassParam,
                        onPassPoint2DChange = viewModel::updatePassPoint2D,
                        onPassColorChange = viewModel::updatePassColor,
                        onPassTextChange = viewModel::updatePassText,
                        onBlendModeChange = viewModel::updatePassBlendMode,
                        onOpacityChange = viewModel::updatePassOpacity,
                        onFocusKeyframeProperty = viewModel::focusKeyframeProperty,
                        onTogglePropertyKeyframe = viewModel::togglePropertyKeyframe,
                        onUpdatePropertyEasing = viewModel::updatePropertyKeyframeEasing,
                        onUpdatePropertyCustomBezier = viewModel::updatePropertyCustomBezier,
                        onJumpPrevKeyframe = viewModel::jumpToPrevKeyframe,
                        onJumpNextKeyframe = viewModel::jumpToNextKeyframe,
                        onClearPropertyKeyframes = viewModel::clearPropertyKeyframes,
                        onSeekProgress = viewModel::seekToProgress,
                        onResetPass = viewModel::resetActivePassParameters,
                        onParameterEditFinished = viewModel::commitParameterEdit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun EffectsStudioTopBar(
    layerTitle: String,
    canUndo: Boolean,
    canRedo: Boolean,
    undoActionLabel: String?,
    redoActionLabel: String?,
    isDarkTheme: Boolean,
    onCancel: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDone: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val extraColors = LocalVideoStarColors.current
    val cancelTint = if (isDarkTheme) extraColors.neonPink else ClassicRed
    val doneTint = if (isDarkTheme) extraColors.neonPink else ClassicGreen

    Surface(
        color = extraColors.toolbarBackground,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // RTL Start (Right): إلغاء
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onCancel)
                        .padding(horizontal = 12.dp)
                        .testTag("reeffect_cancel_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "إلغاء",
                        fontFamily = CairoFontFamily,
                        color = cancelTint,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                // Undo / Redo + Center Title (Layer Name + Subtitle)
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(
                        onClick = onUndo,
                        enabled = canUndo,
                        modifier = Modifier.size(44.dp).testTag("effects_undo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Undo,
                            contentDescription = undoActionLabel?.let { "تراجع: $it" } ?: "تراجع",
                            tint = if (canUndo) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.28f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f, fill = false).padding(horizontal = 6.dp)
                    ) {
                        Text(
                            text = layerTitle,
                            fontFamily = CairoFontFamily,
                            color = colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("effects_studio_layer_title")
                        )
                        Text(
                            text = "استوديو المؤثرات",
                            fontFamily = CairoFontFamily,
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }

                    IconButton(
                        onClick = onRedo,
                        enabled = canRedo,
                        modifier = Modifier.size(44.dp).testTag("effects_redo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Redo,
                            contentDescription = redoActionLabel?.let { "إعادة: $it" } ?: "إعادة",
                            tint = if (canRedo) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.28f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // RTL End (Left): تم
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onDone)
                        .padding(horizontal = 12.dp)
                        .testTag("reeffect_create_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "تم",
                        fontFamily = CairoFontFamily,
                        color = doneTint,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            HorizontalDivider(thickness = 0.8.dp, color = colorScheme.outline.copy(alpha = 0.22f))
        }
    }
}
