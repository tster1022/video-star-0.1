package com.example.ui.screens.workspace

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.StudioLayoutMode
import com.example.ui.components.iosPressable
import com.example.ui.theme.InterFontFamily

internal val SwitcherPillBg = Color(0xFF12131C)
internal val SwitcherPillBorder = Color(0xFF252838)
internal val SwitcherPillContent = Color(0xFFC8CAD6)
internal val LayoutSheetSurface = Color(0xFF0F1115)
internal val LayoutSheetBorder = Color(0xFF1E222B)
internal val LayoutCardSurface = Color(0xFF161920)
internal val LayoutCardHoverSurface = Color(0xFF1D212B)
internal val LayoutCardBorder = Color(0xFF232732)
internal val LayoutFeaturePillBg = Color(0xFF0B0D11)
internal val LayoutMutedText = Color(0xFF8E95A5)
internal val LayoutFeatureText = Color(0xFF9CA3AF)
internal val LayoutActiveGreen = Color(0xFF22C55E)

/**
 * Two-step circular Layout Switcher trigger:
 * - Collapsed (rest): compact 40dp circle button inside 48dp touch target displaying bento grid icon.
 * - 1st tap: smoothly expands into a pill showing the current active editor name (e.g. "Nova") + caret.
 * - 2nd tap: invokes [onClick] to open the full 5-editor Layout selector modal and resets back to circle.
 */
@Composable
fun LayoutSwitcherPillButton(
    activeLayout: StudioLayoutMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "workspace_layout_switcher_button"
) {
    var isExpandedToCurrent by remember { mutableStateOf(false) }

    LaunchedEffect(activeLayout) {
        isExpandedToCurrent = false
    }

    LaunchedEffect(isExpandedToCurrent) {
        if (isExpandedToCurrent) {
            kotlinx.coroutines.delay(5_000L)
            isExpandedToCurrent = false
        }
    }

    BackHandler(enabled = isExpandedToCurrent) {
        isExpandedToCurrent = false
    }

    val containerShape = if (isExpandedToCurrent) RoundedCornerShape(20.dp) else CircleShape

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .height(48.dp)
                .then(if (isExpandedToCurrent) Modifier else Modifier.width(48.dp))
                .clip(containerShape)
                .iosPressable {
                    if (!isExpandedToCurrent) {
                        isExpandedToCurrent = true
                    } else {
                        isExpandedToCurrent = false
                        onClick()
                    }
                }
                .semantics {
                    contentDescription = if (isExpandedToCurrent) {
                        "Current layout ${activeLayout.displayName}. Tap again to switch editor"
                    } else {
                        "Layout switcher: ${activeLayout.displayName}"
                    }
                }
                .testTag(testTag),
            contentAlignment = Alignment.CenterStart
        ) {
            Surface(
                color = SwitcherPillBg,
                shape = containerShape,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isExpandedToCurrent) LayoutActiveGreen.copy(alpha = 0.65f) else SwitcherPillBorder
                ),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .height(40.dp)
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
            ) {
                if (!isExpandedToCurrent) {
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BentoLayoutGridIcon(
                            color = SwitcherPillContent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BentoLayoutGridIcon(
                            color = SwitcherPillContent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = activeLayout.displayName,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = SwitcherPillContent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        DownCaretTriangleIcon(
                            color = SwitcherPillContent,
                            modifier = Modifier.size(width = 10.dp, height = 7.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BentoLayoutGridIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val gap = size.width * 0.14f
        val leftW = (size.width - gap) * 0.48f
        val rightW = (size.width - gap) * 0.52f
        val rightH = (size.height - gap) * 0.5f
        val radius = CornerRadius(size.width * 0.11f, size.width * 0.11f)

        // Tall left rectangle
        drawRoundRect(
            color = color,
            topLeft = Offset.Zero,
            size = Size(leftW, size.height),
            cornerRadius = radius
        )
        // Top-right square
        drawRoundRect(
            color = color,
            topLeft = Offset(leftW + gap, 0f),
            size = Size(rightW, rightH),
            cornerRadius = radius
        )
        // Bottom-right square
        drawRoundRect(
            color = color,
            topLeft = Offset(leftW + gap, rightH + gap),
            size = Size(rightW, rightH),
            cornerRadius = radius
        )
    }
}

@Composable
private fun DownCaretTriangleIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width * 0.5f, size.height)
            close()
        }
        drawPath(path = path, color = color)
    }
}

/**
 * Dark-themed "Layout" modal selector sheet featuring all 5 interactive workspaces:
 * Nova (green active accent & checkmark), CapCut, Blurrr ("Unavailable for now" badge),
 * Node Video, and After Effects ("Needs a large screen" badge).
 */
@Composable
fun StudioLayoutSelectorModal(
    visible: Boolean,
    activeLayout: StudioLayoutMode,
    onSelectLayout: (StudioLayoutMode) -> Unit,
    onDismiss: () -> Unit
) {
    val menuEasing = remember { CubicBezierEasing(0.4f, 0f, 0.2f, 1f) }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(150, easing = menuEasing)) +
            scaleIn(initialScale = 0.95f, animationSpec = tween(150, easing = menuEasing)),
        exit = fadeOut(animationSpec = tween(150, easing = menuEasing)) +
            scaleOut(targetScale = 0.95f, animationSpec = tween(150, easing = menuEasing))
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = LayoutSheetSurface,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, LayoutSheetBorder),
                    shadowElevation = 20.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .testTag("layout_selector_modal")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Modal Header (left: title + clean non-overlapping subtitle, right: 32dp close circle)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "Layout",
                                    fontFamily = InterFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 19.sp,
                                    color = Color.White,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Switch workspace interface — project & effects stay intact",
                                    fontFamily = InterFontFamily,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = LayoutMutedText,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable(role = Role.Button, onClick = onDismiss)
                                    .testTag("layout_modal_close_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1C2029)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Close Layout Selector",
                                        tint = Color(0xFFD5D7E3),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // Scrollable vertical stack of uniform 5-layout cards with 12dp gap
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StudioLayoutMode.entries.forEach { mode ->
                                StudioLayoutOptionCard(
                                    mode = mode,
                                    isSelected = mode == activeLayout,
                                    onClick = { onSelectLayout(mode) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioLayoutOptionCard(
    mode: StudioLayoutMode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isRestricted = mode.statusBadge != null
    val cardShape = RoundedCornerShape(16.dp)
    val cardBorderColor = if (isSelected) LayoutActiveGreen else LayoutCardBorder
    val cardBorderWidth = if (isSelected) 1.5.dp else 1.dp

    val cardBackgroundModifier = if (isSelected) {
        Modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    LayoutActiveGreen.copy(alpha = 0.08f),
                    LayoutCardSurface
                )
            )
        )
    } else {
        Modifier.background(LayoutCardSurface)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .then(cardBackgroundModifier)
            .border(width = cardBorderWidth, color = cardBorderColor, shape = cardShape)
            .clickable(onClick = onClick)
            .padding(14.dp)
            .testTag("layout_option_${mode.name.lowercase()}")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Row: 42dp Icon + Middle Title/Badge/Description + Right Checkmark (when active)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StudioLayoutEmblemSlot(
                    mode = mode,
                    modifier = Modifier
                        .size(42.dp)
                        .alpha(if (isRestricted && !isSelected) 0.65f else 1f)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 28.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = mode.displayName,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        mode.statusBadge?.let { badgeText ->
                            val isAmber = mode == StudioLayoutMode.BLURRR
                            val pillBg = if (isAmber) Color(0xFF3A2E14) else Color(0xFF1F2438)
                            val pillText = if (isAmber) Color(0xFFF5B041) else Color(0xFF8A99D6)
                            Surface(
                                color = pillBg,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .height(20.dp)
                                    .testTag("layout_badge_${mode.name.lowercase()}")
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = badgeText,
                                        fontFamily = InterFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp,
                                        color = pillText,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = layoutDescriptionFor(mode),
                        fontFamily = InterFontFamily,
                        fontSize = 12.sp,
                        color = LayoutMutedText.copy(alpha = if (isRestricted && !isSelected) 0.65f else 1f),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Active layout",
                        tint = LayoutActiveGreen,
                        modifier = Modifier
                            .size(22.dp)
                            .testTag("layout_active_checkmark_${mode.name.lowercase()}")
                    )
                }
            }

            // Bottom Row: Full-width uniform feature pill bar (#0B0D11, single strictly non-wrapping row)
            StudioLayoutActionIconsStrip(mode = mode)
        }
    }
}

private fun layoutDescriptionFor(mode: StudioLayoutMode): String = when (mode) {
    StudioLayoutMode.NOVA -> "Light geometric studio, quick popovers & classic bar"
    StudioLayoutMode.CAPCUT -> "Magnetic multi-track timeline, split rail & effect drawer"
    StudioLayoutMode.BLURRR -> "Neon kinetic velocity curve, FX deck & audio pulse"
    StudioLayoutMode.NODE_VIDEO -> "Modular node graph chain & precision transport"
    StudioLayoutMode.AFTER_EFFECTS -> "Multi-pane compositor, keyframe rows & FX controls"
}

/**
 * Modular visual emblem slot for each editing app layout.
 * Styled custom Compose vectors render now, ready to swap with exact screenshots/drawables later.
 */
@Composable
fun StudioLayoutEmblemSlot(
    mode: StudioLayoutMode,
    modifier: Modifier = Modifier
) {
    when (mode) {
        StudioLayoutMode.NOVA -> {
            // Purple galaxy cat icon
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF8B5CF6), Color(0xFF4C1D95), Color(0xFF1E1B4B))
                        )
                    )
                    .border(1.dp, Color(0xFFA78BFA).copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                    val w = size.width
                    val h = size.height
                    // Cat silhouette with pointed ears
                    val catPath = Path().apply {
                        moveTo(w * 0.22f, h * 0.42f)
                        lineTo(w * 0.24f, h * 0.14f)
                        lineTo(w * 0.40f, h * 0.28f)
                        lineTo(w * 0.60f, h * 0.28f)
                        lineTo(w * 0.76f, h * 0.14f)
                        lineTo(w * 0.78f, h * 0.42f)
                        cubicTo(w * 0.85f, h * 0.62f, w * 0.76f, h * 0.86f, w * 0.50f, h * 0.86f)
                        cubicTo(w * 0.24f, h * 0.86f, w * 0.15f, h * 0.62f, w * 0.22f, h * 0.42f)
                        close()
                    }
                    drawPath(catPath, color = Color(0xFFF5F3FF))
                    // Eyes (crescent galaxy sparkles)
                    drawCircle(Color(0xFF4C1D95), radius = w * 0.065f, center = Offset(w * 0.40f, h * 0.54f))
                    drawCircle(Color(0xFF4C1D95), radius = w * 0.065f, center = Offset(w * 0.60f, h * 0.54f))
                    // Tiny star sparkle
                    drawCircle(Color(0xFFFDE047), radius = w * 0.055f, center = Offset(w * 0.16f, h * 0.20f))
                    drawCircle(Color(0xFF38BDF8), radius = w * 0.045f, center = Offset(w * 0.86f, h * 0.78f))
                }
            }
        }
        StudioLayoutMode.CAPCUT -> {
            // Crisp black/white opposing chevron icon
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0A0B0E))
                    .border(1.dp, Color(0xFF3E4254), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    val w = size.width
                    val h = size.height
                    val stroke = Stroke(width = w * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val topRibbon = Path().apply {
                        moveTo(w * 0.14f, h * 0.28f)
                        lineTo(w * 0.68f, h * 0.28f)
                        lineTo(w * 0.86f, h * 0.44f)
                    }
                    val bottomRibbon = Path().apply {
                        moveTo(w * 0.14f, h * 0.72f)
                        lineTo(w * 0.68f, h * 0.72f)
                        lineTo(w * 0.86f, h * 0.56f)
                    }
                    drawPath(topRibbon, color = Color.White, style = stroke)
                    drawPath(bottomRibbon, color = Color.White, style = stroke)
                }
            }
        }
        StudioLayoutMode.BLURRR -> {
            // Lime green ghost icon
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF101809))
                    .border(1.dp, Color(0xFFB8FF29).copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(7.dp)) {
                    val w = size.width
                    val h = size.height
                    val ghost = Path().apply {
                        moveTo(w * 0.20f, h * 0.84f)
                        lineTo(w * 0.20f, h * 0.44f)
                        cubicTo(w * 0.20f, h * 0.10f, w * 0.80f, h * 0.10f, w * 0.80f, h * 0.44f)
                        lineTo(w * 0.80f, h * 0.84f)
                        lineTo(w * 0.65f, h * 0.72f)
                        lineTo(w * 0.50f, h * 0.84f)
                        lineTo(w * 0.35f, h * 0.72f)
                        close()
                    }
                    drawPath(ghost, color = Color(0xFFB8FF29))
                    drawCircle(Color(0xFF101809), radius = w * 0.08f, center = Offset(w * 0.40f, h * 0.46f))
                    drawCircle(Color(0xFF101809), radius = w * 0.08f, center = Offset(w * 0.60f, h * 0.46f))
                }
            }
        }
        StudioLayoutMode.NODE_VIDEO -> {
            // Dark "N" badge with node graph port dots
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF1B1F2D), Color(0xFF0E1017)))
                    )
                    .border(1.dp, Color(0xFF4F8BFF).copy(alpha = 0.55f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    val w = size.width
                    val h = size.height
                    val nPath = Path().apply {
                        moveTo(w * 0.22f, h * 0.80f)
                        lineTo(w * 0.22f, h * 0.20f)
                        lineTo(w * 0.78f, h * 0.80f)
                        lineTo(w * 0.78f, h * 0.20f)
                    }
                    drawPath(
                        nPath,
                        color = Color(0xFF60A5FA),
                        style = Stroke(width = w * 0.15f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    drawCircle(Color(0xFFEC4899), radius = w * 0.08f, center = Offset(w * 0.22f, h * 0.20f))
                    drawCircle(Color(0xFF34D399), radius = w * 0.08f, center = Offset(w * 0.78f, h * 0.80f))
                }
            }
        }
        StudioLayoutMode.AFTER_EFFECTS -> {
            // Classic Adobe-inspired blue "Ae" tile
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF00005B))
                    .border(1.5.dp, Color(0xFF9999FF), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Ae",
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = Color(0xFF9999FF)
                )
            }
        }
    }
}

@Composable
private fun StudioLayoutActionIconsStrip(mode: StudioLayoutMode) {
    val items: List<Pair<ImageVector, String>> = when (mode) {
        StudioLayoutMode.NOVA -> listOf(
            Icons.Outlined.Videocam to "Video",
            Icons.Outlined.Image to "Photo",
            Icons.Outlined.Layers to "Layers",
            Icons.Outlined.AutoAwesome to "FX",
            Icons.Outlined.Tune to "Mix"
        )
        StudioLayoutMode.CAPCUT -> listOf(
            Icons.Outlined.ContentCut to "Split",
            Icons.Outlined.MusicNote to "Audio",
            Icons.Outlined.Title to "Text",
            Icons.Outlined.AutoAwesome to "Effects",
            Icons.Outlined.PhotoLibrary to "Media",
            Icons.Outlined.Subtitles to "Captions"
        )
        StudioLayoutMode.BLURRR -> listOf(
            Icons.Outlined.Bolt to "Velocity",
            Icons.Outlined.GraphicEq to "Wave",
            Icons.Outlined.AutoFixHigh to "Effects",
            Icons.Outlined.Animation to "Motion"
        )
        StudioLayoutMode.NODE_VIDEO -> listOf(
            Icons.Outlined.AccountTree to "Nodes",
            Icons.Outlined.Hub to "Chain",
            Icons.Outlined.PlayArrow to "Render",
            Icons.Outlined.AutoAwesome to "FX Node"
        )
        StudioLayoutMode.AFTER_EFFECTS -> listOf(
            Icons.Outlined.DesktopWindows to "Comp",
            Icons.Outlined.Timeline to "Keyframes",
            Icons.Outlined.Layers to "Layers",
            Icons.Outlined.Tune to "FX Ctrl"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(LayoutFeaturePillBg)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("layout_feature_pill_${mode.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (icon, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = LayoutFeatureText,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = label,
                        fontFamily = InterFontFamily,
                        fontSize = 11.sp,
                        color = LayoutFeatureText,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
