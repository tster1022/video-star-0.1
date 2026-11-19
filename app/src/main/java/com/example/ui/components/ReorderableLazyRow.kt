package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pure Jetpack Compose drag-and-drop reorder state for a horizontal [LazyRow].
 * Handles both LTR and RTL layout directions with zero-jump offset compensation,
 * smooth neighbor item placement animations, haptic ticks on swap, and edge auto-scrolling.
 */
@Stable
class ReorderableLazyRowState internal constructor(
    val lazyListState: LazyListState,
    private val scope: CoroutineScope,
    private val hapticFeedback: HapticFeedback,
    private val isRtlProvider: () -> Boolean,
    private val edgeThresholdPx: Float,
    private val isDraggableKey: (Any) -> Boolean,
    private val onDragStartCallback: (Any) -> Unit,
    private val onMoveCallback: (fromIndex: Int, toIndex: Int) -> Unit,
    private val onDragEndCallback: () -> Unit
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set

    internal var settlingKey by mutableStateOf<Any?>(null)
        private set

    internal var draggingItemOffsetX by mutableFloatStateOf(0f)
        private set

    internal val settleAnimatable = Animatable(0f)

    internal var edgeScrollVelocityPx by mutableFloatStateOf(0f)
        private set

    fun isItemDragging(key: Any): Boolean = draggingKey == key

    internal fun visualTranslationX(key: Any): Float = when {
        draggingKey == key -> draggingItemOffsetX
        settlingKey == key -> settleAnimatable.value
        else -> 0f
    }

    internal fun beginDrag(key: Any) {
        if (!isDraggableKey(key)) return
        settlingKey = null
        draggingKey = key
        draggingItemOffsetX = 0f
        edgeScrollVelocityPx = 0f
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        onDragStartCallback(key)
    }

    internal fun dragBy(screenDeltaX: Float) {
        val activeKey = draggingKey ?: return
        draggingItemOffsetX += screenDeltaX
        updateEdgeAutoScroll(activeKey)
        evaluateSwap(activeKey)
    }

    internal fun endDrag() {
        val activeKey = draggingKey ?: return
        val releaseOffset = draggingItemOffsetX
        draggingKey = null
        draggingItemOffsetX = 0f
        edgeScrollVelocityPx = 0f

        if (kotlin.math.abs(releaseOffset) > 1f) {
            settlingKey = activeKey
            scope.launch {
                settleAnimatable.snapTo(releaseOffset)
                settleAnimatable.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
                if (settlingKey == activeKey) {
                    settlingKey = null
                }
            }
        }
        onDragEndCallback()
    }

    private fun updateEdgeAutoScroll(activeKey: Any) {
        val layoutInfo = lazyListState.layoutInfo
        val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.key == activeKey }
        if (itemInfo == null) {
            edgeScrollVelocityPx = 0f
            return
        }
        val isRtl = isRtlProvider()
        val axisShift = if (isRtl) -draggingItemOffsetX else draggingItemOffsetX
        val draggedStartAxis = itemInfo.offset + axisShift
        val draggedEndAxis = draggedStartAxis + itemInfo.size

        val viewportStart = layoutInfo.viewportStartOffset.toFloat()
        val viewportEnd = layoutInfo.viewportEndOffset.toFloat()

        edgeScrollVelocityPx = when {
            draggedStartAxis < viewportStart + edgeThresholdPx && lazyListState.canScrollBackward -> -12f
            draggedEndAxis > viewportEnd - edgeThresholdPx && lazyListState.canScrollForward -> 12f
            else -> 0f
        }
    }

    internal fun performAutoScrollStep() {
        val activeKey = draggingKey ?: return
        val velocity = edgeScrollVelocityPx
        if (velocity == 0f) return
        scope.launch {
            val consumed = lazyListState.scrollBy(velocity)
            if (consumed != 0f) {
                val isRtl = isRtlProvider()
                draggingItemOffsetX += if (isRtl) -consumed else consumed
                evaluateSwap(activeKey)
            }
        }
    }

    private fun evaluateSwap(activeKey: Any) {
        val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
            .filter { isDraggableKey(it.key) }
        val draggedInfo: LazyListItemInfo = visibleItems.firstOrNull { it.key == activeKey } ?: return

        val isRtl = isRtlProvider()
        val axisDisplacement = if (isRtl) -draggingItemOffsetX else draggingItemOffsetX
        val draggedCenterAxis = draggedInfo.offset + draggedInfo.size * 0.5f + axisDisplacement

        val targetInfo = visibleItems.firstOrNull { candidate ->
            if (candidate.index == draggedInfo.index) return@firstOrNull false
            val candidateCenter = candidate.offset + candidate.size * 0.5f
            if (candidate.index > draggedInfo.index) {
                draggedCenterAxis > candidateCenter
            } else {
                draggedCenterAxis < candidateCenter
            }
        } ?: return

        val axisDelta = (targetInfo.offset - draggedInfo.offset).toFloat()
        val screenCompensation = if (isRtl) -axisDelta else axisDelta
        draggingItemOffsetX -= screenCompensation
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onMoveCallback(draggedInfo.index, targetInfo.index)
    }
}

@Composable
fun rememberReorderableLazyRowState(
    lazyListState: LazyListState = rememberLazyListState(),
    isDraggableKey: (Any) -> Boolean = { true },
    onDragStart: (Any) -> Unit = {},
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onDragEnd: () -> Unit = {}
): ReorderableLazyRowState {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val isRtl by rememberUpdatedState(LocalLayoutDirection.current == LayoutDirection.Rtl)
    val edgeThresholdPx = with(LocalDensity.current) { 42.dp.toPx() }
    val latestIsDraggable by rememberUpdatedState(isDraggableKey)
    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val latestOnMove by rememberUpdatedState(onMove)
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)

    val state = remember(lazyListState, scope, haptic, edgeThresholdPx) {
        ReorderableLazyRowState(
            lazyListState = lazyListState,
            scope = scope,
            hapticFeedback = haptic,
            isRtlProvider = { isRtl },
            edgeThresholdPx = edgeThresholdPx,
            isDraggableKey = { key -> latestIsDraggable(key) },
            onDragStartCallback = { key -> latestOnDragStart(key) },
            onMoveCallback = { from, to -> latestOnMove(from, to) },
            onDragEndCallback = { latestOnDragEnd() }
        )
    }

    LaunchedEffect(state.draggingKey) {
        if (state.draggingKey != null) {
            while (isActive && state.draggingKey != null) {
                if (state.edgeScrollVelocityPx != 0f) {
                    state.performAutoScrollStep()
                }
                delay(16L)
            }
        }
    }

    return state
}

@Composable
fun ReorderableLazyRow(
    state: ReorderableLazyRowState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: LazyListScope.() -> Unit
) {
    LazyRow(
        state = state.lazyListState,
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
        userScrollEnabled = state.draggingKey == null,
        content = content
    )
}

@Composable
fun androidx.compose.foundation.lazy.LazyItemScope.ReorderableRowItem(
    state: ReorderableLazyRowState,
    key: Any,
    enabled: Boolean = true,
    dragScale: Float = 1.06f,
    modifier: Modifier = Modifier,
    content: @Composable (isDragging: Boolean) -> Unit
) {
    val isDragging = state.isItemDragging(key)
    val latestEnabled by rememberUpdatedState(enabled)

    val placementSpec = if (isDragging) {
        null
    } else {
        spring<IntOffset>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    }

    Box(
        modifier = modifier
            .animateItem(
                fadeInSpec = null,
                fadeOutSpec = null,
                placementSpec = placementSpec
            )
            .zIndex(if (isDragging) 2f else 0f)
            .graphicsLayer {
                translationX = state.visualTranslationX(key)
                val s = if (isDragging) dragScale else 1f
                scaleX = s
                scaleY = s
            }
            .pointerInput(key) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        if (latestEnabled) {
                            state.beginDrag(key)
                        }
                    },
                    onDragEnd = { state.endDrag() },
                    onDragCancel = { state.endDrag() },
                    onHorizontalDrag = { change, dragAmount ->
                        if (state.isItemDragging(key)) {
                            change.consume()
                            state.dragBy(dragAmount)
                        }
                    }
                )
            }
    ) {
        content(isDragging)
    }
}
