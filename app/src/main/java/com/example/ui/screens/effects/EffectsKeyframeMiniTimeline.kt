package com.example.ui.screens.effects

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.arabicDisplayName
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.ClassicRed
import com.example.ui.theme.InterFontFamily
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Interactive mini Keyframe Timeline directly below the playback strip.
 * Renders ◆ diamonds for the selected effect pass, supports horizontal drag to adjust
 * keyframe time, tap to seek, and long-press to delete a keyframe.
 */
@Composable
internal fun EffectsKeyframeMiniTimeline(
    uiState: EffectsStudioUiState,
    isDarkTheme: Boolean,
    onSeekProgress: (Float) -> Unit,
    onQuickAddKeyframe: () -> Unit,
    onSelectAndSeekMarker: (propertyKey: String, time: Float) -> Unit,
    onBeginDrag: () -> Unit,
    onMoveKeyframeTime: (propertyKey: String, keyframeId: String, newTime: Float) -> Unit,
    onEndDrag: () -> Unit = {},
    onDeleteKeyframe: (propertyKey: String, keyframeId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activePass = uiState.activePass
    AnimatedVisibility(visible = activePass != null, modifier = modifier) {
        if (activePass == null) return@AnimatedVisibility
        val colorScheme = MaterialTheme.colorScheme
        val markers = uiState.activePassKeyframeMarkers
        val effectArabicName = uiState.activeCatalogItem.arabicDisplayName()
        var draggingMarker by remember { mutableStateOf<EffectTimelineKeyframeMarker?>(null) }

        val surfaceBg = if (isDarkTheme) {
            colorScheme.surface.copy(alpha = 0.92f)
        } else {
            colorScheme.surface.copy(alpha = 0.94f)
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = surfaceBg,
            border = BorderStroke(0.8.dp, colorScheme.outline.copy(alpha = 0.28f)),
            shadowElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("effects_mini_keyframe_timeline")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        KeyframeDiamondGlyph(
                            filled = markers.isNotEmpty(),
                            color = if (markers.isNotEmpty()) ClassicRed else colorScheme.primary,
                            size = 12.dp
                        )
                        Text(
                            text = "نقاط Keyframes • $effectArabicName",
                            fontFamily = CairoFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (markers.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colorScheme.primary.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = "${markers.size}",
                                    fontFamily = InterFontFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    val activeDrag = draggingMarker
                    if (activeDrag != null) {
                        val dragMs = (uiState.previewProgress * uiState.totalDurationMs).toLong()
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ClassicRed.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${activeDrag.propertyLabel} • ${formatMillisShort(dragMs)}",
                                fontFamily = CairoFontFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ClassicRed,
                                modifier = Modifier
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                                    .testTag("effects_mini_timeline_drag_readout")
                            )
                        }
                    } else if (markers.isEmpty()) {
                        Text(
                            text = "اضغط + ◆ للإضافة",
                            fontFamily = CairoFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onQuickAddKeyframe)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("effects_mini_timeline_quick_add")
                        )
                    } else {
                        Text(
                            text = "اسحب للتحريك • مطوّل للحذف",
                            fontFamily = CairoFontFamily,
                            fontSize = 10.sp,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                // LTR Timeline Lane
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    val density = LocalDensity.current
                    val handleSizeDp = 38.dp
                    val handleSizePx = with(density) { handleSizeDp.toPx() }

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .testTag("effects_mini_timeline_track"),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        val totalWidthPx = with(density) { maxWidth.toPx() }
                        val usableWidthPx = (totalWidthPx - handleSizePx).coerceAtLeast(1f)
                        val halfHandlePx = handleSizePx / 2f

                        // Background rail, ticks, playhead needle & tap-to-seek
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(usableWidthPx) {
                                    detectTapGestures { offset ->
                                        val norm = ((offset.x - halfHandlePx) / usableWidthPx).coerceIn(0f, 1f)
                                        onSeekProgress(norm)
                                    }
                                }
                        ) {
                            val cy = size.height / 2f
                            val startX = halfHandlePx
                            val endX = startX + usableWidthPx

                            drawLine(
                                color = colorScheme.outline.copy(alpha = 0.36f),
                                start = Offset(startX, cy),
                                end = Offset(endX, cy),
                                strokeWidth = 2.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            for (step in 0..4) {
                                val tx = startX + (step / 4f) * usableWidthPx
                                drawLine(
                                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                    start = Offset(tx, cy - 5.dp.toPx()),
                                    end = Offset(tx, cy + 5.dp.toPx()),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }

                            if (markers.size >= 2) {
                                val firstX = startX + markers.first().time.coerceIn(0f, 1f) * usableWidthPx
                                val lastX = startX + markers.last().time.coerceIn(0f, 1f) * usableWidthPx
                                drawLine(
                                    color = colorScheme.primary.copy(alpha = 0.5f),
                                    start = Offset(firstX, cy),
                                    end = Offset(lastX, cy),
                                    strokeWidth = 3.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }

                            val playX = startX + uiState.previewProgress.coerceIn(0f, 1f) * usableWidthPx
                            drawLine(
                                color = ClassicRed,
                                start = Offset(playX, 3.dp.toPx()),
                                end = Offset(playX, size.height - 3.dp.toPx()),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }

                        // Draggable & Long-Press Deletable ◆ Diamonds
                        markers.forEach { marker ->
                            DraggableTimelineDiamondNode(
                                marker = marker,
                                isNearPlayhead = abs(marker.time - uiState.previewProgress) <= 0.03f,
                                isDragging = draggingMarker?.keyframeId == marker.keyframeId,
                                usableWidthPx = usableWidthPx,
                                handleSizeDp = handleSizeDp,
                                onTap = {
                                    onSelectAndSeekMarker(marker.propertyKey, marker.time)
                                },
                                onLongPressDelete = {
                                    draggingMarker = null
                                    onDeleteKeyframe(marker.propertyKey, marker.keyframeId)
                                },
                                onDragStart = {
                                    draggingMarker = marker
                                    onBeginDrag()
                                },
                                onDragTimeChanged = { updatedTime ->
                                    draggingMarker = marker.copy(time = updatedTime)
                                    onMoveKeyframeTime(marker.propertyKey, marker.keyframeId, updatedTime)
                                },
                                onDragEnd = {
                                    draggingMarker = null
                                    onEndDrag()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DraggableTimelineDiamondNode(
    marker: EffectTimelineKeyframeMarker,
    isNearPlayhead: Boolean,
    isDragging: Boolean,
    usableWidthPx: Float,
    handleSizeDp: androidx.compose.ui.unit.Dp,
    onTap: () -> Unit,
    onLongPressDelete: () -> Unit,
    onDragStart: () -> Unit,
    onDragTimeChanged: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current

    val latestTime by rememberUpdatedState(marker.time)
    val latestUsableWidth by rememberUpdatedState(usableWidthPx)
    var localDragTime by remember(marker.keyframeId) { mutableFloatStateOf(marker.time) }

    val displayTime = if (isDragging) localDragTime else marker.time
    val offsetX = (displayTime.coerceIn(0f, 1f) * usableWidthPx).roundToInt()

    val diamondColor = when {
        isDragging || isNearPlayhead -> ClassicRed
        marker.isFocusedProperty -> colorScheme.primary
        else -> colorScheme.secondary
    }
    val diamondSize = if (isDragging) 20.dp else if (marker.isFocusedProperty) 16.dp else 13.dp

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX, 0) }
            .size(handleSizeDp)
            .clip(CircleShape)
            .pointerInput(marker.keyframeId) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        localDragTime = latestTime
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDragStart()
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val delta = dragAmount / latestUsableWidth.coerceAtLeast(1f)
                        localDragTime = (localDragTime + delta).coerceIn(0f, 1f)
                        onDragTimeChanged(localDragTime)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                )
            }
            .combinedClickable(
                onClick = onTap,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPressDelete()
                }
            )
            .semantics {
                contentDescription = "نقطة ${marker.propertyLabel} عند ${(marker.time * 100).roundToInt()}%"
            }
            .testTag("effects_mini_kf_${marker.propertyKey}_${marker.keyframeId}"),
        contentAlignment = Alignment.Center
    ) {
        if (isDragging || isNearPlayhead) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(ClassicRed.copy(alpha = if (isDragging) 0.25f else 0.14f))
            )
        }
        KeyframeDiamondGlyph(
            filled = marker.isFocusedProperty || isNearPlayhead || isDragging,
            color = diamondColor,
            size = diamondSize
        )
    }
}
