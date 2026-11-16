package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.example.ui.theme.LocalVideoStarColors
import kotlin.math.roundToInt

/**
 * Custom Video Star floating popover with pure white surface (#FFFFFF),
 * 12.dp corner radius, soft 8.dp shadow, and a downward triangle pointer
 * aimed accurately at the tapped anchor button's screen coordinates.
 */
@Composable
fun VideoStarPopover(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchorBounds: Rect?,
    width: Dp,
    modifier: Modifier = Modifier,
    focusable: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!expanded || anchorBounds == null) return

    val extraColors = LocalVideoStarColors.current
    val density = LocalDensity.current
    val pointerHeight = 10.dp
    val horizontalMarginPx = with(density) { 12.dp.roundToPx() }
    val gapAboveAnchorPx = with(density) { 4.dp.roundToPx() }
    val cornerGuardPx = with(density) { 22.dp.roundToPx() }

    var arrowCenterOffsetPx by remember { mutableIntStateOf(0) }

    val positionProvider = remember(anchorBounds, width) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBoundsIgnored: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val targetCenterX = anchorBounds.center.x.roundToInt()
                val rawX = targetCenterX - (popupContentSize.width / 2)
                val maxX = (windowSize.width - popupContentSize.width - horizontalMarginPx)
                    .coerceAtLeast(horizontalMarginPx)
                val clampedX = rawX.coerceIn(horizontalMarginPx, maxX)

                val minArrow = cornerGuardPx
                val maxArrow = (popupContentSize.width - cornerGuardPx).coerceAtLeast(minArrow)
                arrowCenterOffsetPx = (targetCenterX - clampedX).coerceIn(minArrow, maxArrow)

                val preferredY = anchorBounds.top.roundToInt() - popupContentSize.height - gapAboveAnchorPx
                val clampedY = preferredY.coerceAtLeast(horizontalMarginPx)
                return IntOffset(clampedX, clampedY)
            }
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = focusable,
            dismissOnBackPress = true,
            dismissOnClickOutside = dismissOnClickOutside
        )
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(modifier = modifier.width(width)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = extraColors.popoverBackground,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(12.dp),
                            ambientColor = Color.Black.copy(alpha = 0.22f),
                            spotColor = Color.Black.copy(alpha = 0.22f)
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        content = content
                    )
                }

                // Triangle pointer pointing down to the anchor button
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(pointerHeight)
                ) {
                    val halfBase = 10.dp.toPx()
                    val tipY = size.height
                    val centerX = arrowCenterOffsetPx.toFloat().coerceIn(
                        halfBase + 4.dp.toPx(),
                        (size.width - halfBase - 4.dp.toPx()).coerceAtLeast(halfBase + 4.dp.toPx())
                    )
                    val trianglePath = Path().apply {
                        moveTo(centerX - halfBase, 0f)
                        lineTo(centerX + halfBase, 0f)
                        lineTo(centerX, tipY)
                        close()
                    }
                    drawPath(
                        path = trianglePath,
                        color = extraColors.popoverBackground
                    )
                }
            }
        }
    }
}
