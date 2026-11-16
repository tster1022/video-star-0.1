package com.example.ui.screens.layers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.EditorLayer
import com.example.model.LayerMediaType
import com.example.ui.components.ColorWheelIcon
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.ClassicGreen
import com.example.ui.theme.ClassicRed
import com.example.ui.theme.VideoStarDarkBorder
import com.example.ui.theme.VideoStarDarkButton
import com.example.ui.theme.VideoStarDarkSurface
import com.example.ui.theme.VideoStarInkDark
import com.example.ui.theme.VideoStarInkMuted
import com.example.ui.theme.VideoStarIosDivider
import com.example.ui.theme.VideoStarMagenta
import com.example.ui.theme.VideoStarNeonPink
import com.example.ui.theme.VideoStarWhite

/**
 * Compact 36x36dp horizontal scrollable layer bar placed directly below the top bar.
 * Displays Color Wheel, Layer 1..N buttons, (+) Add Layer, and Manage Layers.
 */
@Composable
fun CompactHorizontalLayersStrip(
    layers: List<EditorLayer>,
    selectedIndex: Int?,
    stageBackgroundColor: Color,
    isDarkTheme: Boolean,
    colorWheelTag: String,
    layerButtonTagPrefix: String,
    addLayerTag: String,
    manageLayersTag: String,
    onColorWheelClick: () -> Unit,
    onSelectLayer: (Int) -> Unit,
    onAddLayerClick: () -> Unit,
    onManageLayersClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgContainer = if (isDarkTheme) VideoStarDarkSurface else Color(0xFFF6F6F9)
    val itemBg = if (isDarkTheme) VideoStarDarkButton else VideoStarWhite
    val borderNeutral = if (isDarkTheme) VideoStarDarkBorder else VideoStarIosDivider
    val accentSelected = if (isDarkTheme) VideoStarNeonPink else ClassicRed
    val accentIdle = if (isDarkTheme) VideoStarWhite else ClassicGreen

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(
            color = bgContainer,
            tonalElevation = 1.dp,
            modifier = modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                // Color Wheel 36x36dp button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = itemBg,
                    border = BorderStroke(1.3.dp, accentSelected),
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(onClick = onColorWheelClick)
                        .testTag(colorWheelTag)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ColorWheelIcon(
                            selectedColor = stageBackgroundColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Layer buttons 1..N (36x36dp each)
                layers.forEachIndexed { slotIdx, layer ->
                    val isSelected = selectedIndex == slotIdx
                    val mediaType = layer.layerMediaType
                    val dotColor = when (mediaType) {
                        LayerMediaType.VIDEO -> if (isDarkTheme) Color(0xFF4CAF50) else ClassicGreen
                        LayerMediaType.PHOTO -> accentSelected
                        LayerMediaType.COLOR -> layer.backgroundColorArgb?.let { Color(it.toInt()) } ?: accentSelected
                        LayerMediaType.NONE -> null
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) accentSelected.copy(alpha = if (isDarkTheme) 0.24f else 0.12f) else itemBg,
                        border = BorderStroke(
                            width = if (isSelected) 1.8.dp else 1.dp,
                            color = if (isSelected) accentSelected else borderNeutral
                        ),
                        modifier = Modifier
                            .size(36.dp)
                            .clickable { onSelectLayer(slotIdx) }
                            .testTag("${layerButtonTagPrefix}${layer.number}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = layer.number.toString(),
                                color = if (isSelected) accentSelected else accentIdle,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (dotColor != null) {
                                if (isDarkTheme) {
                                    Icon(
                                        imageVector = Icons.Rounded.Star,
                                        contentDescription = null,
                                        tint = dotColor,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(2.dp)
                                            .size(8.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(3.dp)
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                            .border(0.5.dp, borderNeutral, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }

                // Add Layer (+) 36x36dp button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDarkTheme) VideoStarMagenta.copy(alpha = 0.22f) else ClassicGreen.copy(alpha = 0.12f),
                    border = BorderStroke(1.2.dp, if (isDarkTheme) VideoStarNeonPink else ClassicGreen),
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(onClick = onAddLayerClick)
                        .testTag(addLayerTag)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "إضافة طبقة",
                            tint = if (isDarkTheme) VideoStarNeonPink else ClassicGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Manage Layers Hierarchy 36x36dp button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = itemBg,
                    border = BorderStroke(1.dp, borderNeutral),
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(onClick = onManageLayersClick)
                        .testTag(manageLayersTag)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Layers,
                            contentDescription = stringResource(R.string.cd_manage_layers),
                            tint = if (isDarkTheme) VideoStarNeonPink else ClassicGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Small translucent floating corner icons (Hierarchy, 3D, Tips/Grid) overlaid inside a corner
 * of the preview so they never take away horizontal stage width.
 */
@Composable
fun FloatingPreviewToolsOverlay(
    isDarkTheme: Boolean,
    is3DEnabled: Boolean,
    isSecondaryActive: Boolean = false,
    useGridForThirdButton: Boolean = false,
    hierarchyTag: String,
    threeDTag: String,
    thirdButtonTag: String,
    onHierarchyClick: () -> Unit,
    onToggle3DClick: () -> Unit,
    onThirdButtonClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glassBg = if (isDarkTheme) Color.Black.copy(alpha = 0.56f) else Color.White.copy(alpha = 0.78f)
    val glassBorder = if (isDarkTheme) Color.White.copy(alpha = 0.18f) else VideoStarIosDivider.copy(alpha = 0.9f)
    val accent = if (isDarkTheme) VideoStarNeonPink else ClassicGreen
    val activeAccent = if (isDarkTheme) VideoStarNeonPink else ClassicRed

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(glassBg)
            .border(0.8.dp, glassBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(onClick = onHierarchyClick)
                .testTag(hierarchyTag),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.AccountTree,
                contentDescription = "ترتيب الطبقات",
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (is3DEnabled) activeAccent.copy(alpha = 0.22f) else Color.Transparent)
                .clickable(onClick = onToggle3DClick)
                .testTag(threeDTag),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "3D",
                color = if (is3DEnabled) activeAccent else accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (isSecondaryActive) activeAccent.copy(alpha = 0.22f) else Color.Transparent)
                .clickable(onClick = onThirdButtonClick)
                .testTag(thirdButtonTag),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (useGridForThirdButton) Icons.Outlined.GridView else Icons.Outlined.Lightbulb,
                contentDescription = if (useGridForThirdButton) "شبكة المحاذاة" else "نصائح",
                tint = if (isSecondaryActive) activeAccent else if (!isDarkTheme) ClassicRed else accent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Small semi-transparent Arabic gesture hint inside preview that fades away after first touch.
 */
@Composable
fun StageGestureHintBadge(
    visible: Boolean,
    text: String,
    modifier: Modifier = Modifier,
    tag: String = "stage_gesture_hint"
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.45f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .testTag(tag)
        ) {
            Text(
                text = text,
                fontFamily = CairoFontFamily,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
    }
}

/**
 * Draggable Bottom Sheet for Layer tools (defaults to 25% height so it never covers the preview,
 * draggable upward up to 62% via drag handle).
 */
@Composable
fun DraggableLayerToolSheet(
    title: String,
    isDarkTheme: Boolean,
    containerHeight: Dp,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    var heightFraction by remember(title) { mutableFloatStateOf(0.25f) }
    val density = LocalDensity.current
    val containerHeightPx = with(density) { containerHeight.toPx().coerceAtLeast(320f) }
    val sheetHeight = (containerHeight * heightFraction).coerceIn(136.dp, containerHeight * 0.64f)

    val sheetBg = if (isDarkTheme) VideoStarDarkSurface else VideoStarWhite
    val sheetBorder = if (isDarkTheme) VideoStarDarkBorder else VideoStarIosDivider
    val accentColor = if (isDarkTheme) VideoStarNeonPink else ClassicGreen
    val handleColor = if (isDarkTheme) Color.White.copy(alpha = 0.28f) else VideoStarIosDivider
    val closeTint = if (isDarkTheme) VideoStarInkMuted else ClassicRed

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(
            color = sheetBg,
            shadowElevation = 10.dp,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            border = BorderStroke(1.dp, sheetBorder),
            modifier = modifier
                .fillMaxWidth()
                .height(sheetHeight)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Drag Handle + Compact Header Row
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(containerHeightPx) {
                            detectVerticalDragGestures { _, dragAmount ->
                                val deltaFraction = -dragAmount / containerHeightPx
                                heightFraction = (heightFraction + deltaFraction).coerceIn(0.22f, 0.64f)
                            }
                        }
                        .padding(top = 8.dp, bottom = 4.dp, start = 14.dp, end = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(handleColor)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = title,
                            fontFamily = CairoFontFamily,
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.btn_close),
                                tint = closeTint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Scrollable Sheet Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content
                )
            }
        }
    }
}
