package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.ui.theme.VideoStarIosDivider
import com.example.ui.theme.VideoStarIosTrack
import com.example.ui.theme.VideoStarWhite

/**
 * Reusable iOS-style UISegmentedControl clone:
 * - Track: #E5E5EA, 8.dp corners, 36.dp height, 2.dp inner padding.
 * - Animated white thumb (#FFFFFF, 6.dp corners, 2.dp shadow) sliding smoothly via animateDpAsState.
 * - Vertical 1.dp hairline dividers (#D1D1D6) rendered ONLY between unselected adjacent segments.
 */
@Composable
fun <T> IosSegmentedControl(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemTagPrefix: String = "segment",
    segmentHeight: androidx.compose.ui.unit.Dp = 36.dp,
    itemContent: @Composable (item: T, isSelected: Boolean) -> Unit
) {
    if (items.isEmpty()) return
    val selectedIndex = items.indexOf(selectedItem).coerceAtLeast(0)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .height(segmentHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(VideoStarIosTrack)
                .padding(2.dp)
        ) {
            val segmentWidth = maxWidth / items.size
            val thumbOffset by animateDpAsState(
                targetValue = segmentWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "ios_segment_thumb_offset"
            )

            // Sliding white selected thumb
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .background(VideoStarWhite)
            )

            // Segment slots & hairline dividers between unselected neighbors
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    val isSelected = index == selectedIndex
                    val showRightDivider =
                        index < items.lastIndex &&
                            index != selectedIndex &&
                            (index + 1) != selectedIndex

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onItemSelected(item) }
                            )
                            .testTag("${itemTagPrefix}_$index"),
                        contentAlignment = Alignment.Center
                    ) {
                        itemContent(item, isSelected)

                        if (showRightDivider) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .width(1.dp)
                                    .height(18.dp)
                                    .background(VideoStarIosDivider)
                            )
                        }
                    }
                }
            }
        }
    }
}
