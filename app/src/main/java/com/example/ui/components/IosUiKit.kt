package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.ui.theme.IosHairlineGray

/**
 * UIKit-style touch feedback modifier: disables Material 3 circular ripple and
 * applies an authentic 0.65f alpha dim while pressed.
 */
fun Modifier.iosPressable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    this
        .graphicsLayer {
            alpha = if (enabled && isPressed) 0.65f else 1f
        }
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

@Composable
fun IosHairlineDivider(
    modifier: Modifier = Modifier,
    color: Color = IosHairlineGray
) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 0.5.dp,
        color = color
    )
}
