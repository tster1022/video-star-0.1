package com.example.ui.screens.workspace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.engine.ae.AeEffectDefinition
import com.example.engine.ae.AeTileVisualStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val TileLightSurface = Color(0xFFFDFEFE)
private val TileCoralRed = Color(0xFFE96F6E)
private val TileVividOrange = Color(0xFFF27324)
private val TileSkyBlue = Color(0xFF288CC8)
private val TileSlateStroke = Color(0xFF3A4458)
private val TileSoftBlueBg = Color(0xFFE7EFFA)

@Composable
internal fun AeTileIllustration(
    definition: AeEffectDefinition,
    modifier: Modifier = Modifier
) {
    val style = definition.tileStyle
    val usesLightCard = style in setOf(
        AeTileVisualStyle.ORBIT_ORANGE,
        AeTileVisualStyle.PATH_BEZIER,
        AeTileVisualStyle.SPLIT_CIRCLE,
        AeTileVisualStyle.PULSE_RING,
        AeTileVisualStyle.BAR_ROTATE,
        AeTileVisualStyle.PENDULUM_SWING,
        AeTileVisualStyle.CHANNEL_NODES,
        AeTileVisualStyle.TIME_QUANTIZE,
        AeTileVisualStyle.PARENT_LINK
    )

    Box(
        modifier = modifier.background(
            if (usesLightCard) TileLightSurface else Color(0xFF15192B)
        )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (usesLightCard) 6.dp else 4.dp)
        ) {
            val w = size.width
            val h = size.height
            val cx = w * 0.5f
            val cy = h * 0.5f
            val minD = size.minDimension

            when (style) {
                AeTileVisualStyle.ORBIT_ORANGE -> {
                    val accent = if (definition.id.contains("oscillate") || definition.id.contains("displacement")) {
                        TileCoralRed
                    } else {
                        TileVividOrange
                    }
                    val radius = when {
                        definition.id.contains("oscillate") -> minD * 0.25f
                        definition.id.contains("displacement") -> minD * 0.14f
                        else -> minD * 0.11f
                    }
                    val dotCenter = when {
                        definition.id.contains("oscillate") -> Offset(w * 0.64f, h * 0.64f)
                        definition.id.contains("displacement") -> Offset(w * 0.50f, h * 0.34f)
                        else -> Offset(cx, cy)
                    }
                    drawCircle(color = accent, radius = radius, center = dotCenter)
                }

                AeTileVisualStyle.PATH_BEZIER -> {
                    val path = Path().apply {
                        moveTo(w * 0.24f, h * 0.52f)
                        cubicTo(w * 0.45f, h * 0.08f, w * 0.90f, h * 0.15f, w * 0.78f, h * 0.45f)
                        cubicTo(w * 0.65f, h * 0.75f, w * 0.22f, h * 0.88f, w * 0.24f, h * 0.52f)
                    }
                    drawPath(path, color = TileCoralRed.copy(alpha = 0.28f), style = Stroke(width = 1.6.dp.toPx()))
                    val arrow = Path().apply {
                        moveTo(w * 0.14f, h * 0.48f)
                        lineTo(w * 0.34f, h * 0.50f)
                        lineTo(w * 0.22f, h * 0.62f)
                        close()
                    }
                    drawPath(arrow, color = TileVividOrange)
                }

                AeTileVisualStyle.SPLIT_CIRCLE -> {
                    drawCircle(color = TileCoralRed, radius = minD * 0.26f, center = Offset(w * 0.68f, cy))
                    drawArc(
                        color = TileCoralRed,
                        startAngle = -75f,
                        sweepAngle = 150f,
                        useCenter = false,
                        topLeft = Offset(-minD * 0.22f, cy - minD * 0.26f),
                        size = Size(minD * 0.52f, minD * 0.52f)
                    )
                }

                AeTileVisualStyle.PULSE_RING -> {
                    val isLayered = definition.id.contains("raster")
                    if (isLayered) {
                        drawCircle(color = TileCoralRed.copy(alpha = 0.45f), radius = minD * 0.34f, center = Offset(cx, cy))
                        drawCircle(color = TileCoralRed, radius = minD * 0.24f, center = Offset(cx, cy))
                    } else {
                        drawCircle(color = TileVividOrange, radius = minD * 0.34f, center = Offset(cx, cy))
                    }
                }

                AeTileVisualStyle.BAR_ROTATE -> {
                    if (definition.id.contains("spin")) {
                        drawLine(
                            color = TileCoralRed,
                            start = Offset(w * 0.12f, h * 0.64f),
                            end = Offset(w * 0.88f, h * 0.36f),
                            strokeWidth = 6.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    } else {
                        drawOval(
                            color = TileVividOrange,
                            topLeft = Offset(w * 0.18f, h * 0.22f),
                            size = Size(w * 0.64f, h * 0.56f)
                        )
                    }
                }

                AeTileVisualStyle.PENDULUM_SWING -> {
                    val pivot = Offset(w * 0.58f, h * 0.16f)
                    val bob = Offset(w * 0.26f, h * 0.72f)
                    drawLine(TileVividOrange, start = pivot, end = bob, strokeWidth = 2.dp.toPx())
                    drawCircle(TileVividOrange, radius = 3.dp.toPx(), center = pivot)
                    drawCircle(TileVividOrange, radius = minD * 0.16f, center = bob)
                }

                AeTileVisualStyle.CHANNEL_NODES -> {
                    val isHsv = definition.id.contains("hsv")
                    val leftColors = if (isHsv) {
                        listOf(Color(0xFFEF4444), Color(0xFF22C55E), Color(0xFF3B82F6), Color(0xFFA855F7))
                    } else {
                        listOf(Color(0xFFEF4444), Color(0xFF10B981), Color(0xFF3B82F6), Color(0xFF94A3B8))
                    }
                    val rightColors = if (isHsv) {
                        listOf(Color(0xFF38BDF8), Color(0xFFA855F7), Color(0xFF334155), Color(0xFF64748B))
                    } else {
                        listOf(Color(0xFFEF4444), Color(0xFF10B981), Color(0xFF38BDF8), Color(0xFFCBD5E1))
                    }
                    val rowYs = listOf(0.20f, 0.40f, 0.60f, 0.80f)
                    rowYs.forEachIndexed { idx, fy ->
                        val y = h * fy
                        val chipW = w * 0.22f
                        val chipH = h * 0.11f
                        drawRoundRect(
                            color = leftColors[idx],
                            topLeft = Offset(w * 0.08f, y - chipH / 2f),
                            size = Size(chipW, chipH),
                            cornerRadius = CornerRadius(3.dp.toPx())
                        )
                        drawRoundRect(
                            color = rightColors[idx],
                            topLeft = Offset(w * 0.70f, y - chipH / 2f),
                            size = Size(chipW, chipH),
                            cornerRadius = CornerRadius(3.dp.toPx())
                        )
                        val targetIdx = if (idx == 0) 2 else if (idx == 2) 0 else idx
                        drawLine(
                            color = TileSlateStroke.copy(alpha = 0.55f),
                            start = Offset(w * 0.32f, y),
                            end = Offset(w * 0.68f, h * rowYs[targetIdx]),
                            strokeWidth = 1.2.dp.toPx()
                        )
                    }
                }

                AeTileVisualStyle.TIME_QUANTIZE -> {
                    if (definition.id.contains("echo")) {
                        for (i in 0..5) {
                            val t = i / 5f
                            drawCircle(
                                color = TileSkyBlue.copy(alpha = 0.28f + 0.12f * i),
                                radius = minD * (0.09f + 0.01f * i),
                                center = Offset(cx + sin(t * 2f) * minD * 0.08f, h * (0.18f + t * 0.64f))
                            )
                            drawCircle(
                                color = Color(0xFF0F4C81),
                                radius = minD * (0.09f + 0.01f * i),
                                center = Offset(cx + sin(t * 2f) * minD * 0.08f, h * (0.18f + t * 0.64f)),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                    } else {
                        drawLine(
                            color = TileSlateStroke,
                            start = Offset(0f, cy),
                            end = Offset(w, cy),
                            strokeWidth = 1.5.dp.toPx()
                        )
                        drawArc(
                            color = TileSkyBlue,
                            startAngle = 180f,
                            sweepAngle = 180f,
                            useCenter = true,
                            topLeft = Offset(w * 0.45f, cy - minD * 0.10f),
                            size = Size(minD * 0.20f, minD * 0.20f)
                        )
                        drawArc(
                            color = TileSkyBlue,
                            startAngle = 0f,
                            sweepAngle = 180f,
                            useCenter = true,
                            topLeft = Offset(w * 0.63f, cy - minD * 0.10f),
                            size = Size(minD * 0.20f, minD * 0.20f)
                        )
                    }
                }

                AeTileVisualStyle.PARENT_LINK -> {
                    drawCircle(
                        color = Color(0xFF93C5FD).copy(alpha = 0.45f),
                        radius = minD * 0.34f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 3.dp.toPx())
                    )
                    drawLine(TileSlateStroke, Offset(cx, cy), Offset(w * 0.68f, h * 0.36f), strokeWidth = 1.6.dp.toPx())
                    drawCircle(TileSlateStroke, radius = 2.5.dp.toPx(), center = Offset(cx, cy))
                    drawRoundRect(
                        color = TileCoralRed,
                        topLeft = Offset(w * 0.56f, h * 0.24f),
                        size = Size(w * 0.24f, h * 0.22f),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                    drawRoundRect(
                        color = TileCoralRed.copy(alpha = 0.5f),
                        topLeft = Offset(w * 0.14f, h * 0.28f),
                        size = Size(w * 0.20f, h * 0.18f),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )
                }

                AeTileVisualStyle.COLOR_SPECTRUM -> {
                    drawRect(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color(0xFFFF3B30),
                                Color(0xFFFACC15),
                                Color(0xFF00E676),
                                Color(0xFF38BDF8),
                                Color(0xFFA855F7),
                                Color(0xFFFF3B30)
                            )
                        )
                    )
                    drawCircle(color = Color(0xFF15192B), radius = minD * 0.18f, center = Offset(cx, cy))
                }

                AeTileVisualStyle.BLUR_SOFT -> {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF38BDF8), Color(0xFF1D4ED8).copy(alpha = 0.35f), Color.Transparent),
                            center = Offset(cx, cy),
                            radius = minD * 0.48f
                        )
                    )
                    drawRoundRect(
                        color = Color(0xFF60A5FA).copy(alpha = 0.55f),
                        topLeft = Offset(w * 0.22f, h * 0.22f),
                        size = Size(w * 0.56f, h * 0.56f),
                        cornerRadius = CornerRadius(12.dp.toPx())
                    )
                }

                AeTileVisualStyle.WAVE_STRIP -> {
                    for (row in 0..3) {
                        val yBase = h * (0.22f + row * 0.18f)
                        val wave = Path().apply {
                            moveTo(w * 0.08f, yBase)
                            cubicTo(w * 0.35f, yBase - h * 0.12f, w * 0.65f, yBase + h * 0.12f, w * 0.92f, yBase)
                        }
                        drawPath(
                            wave,
                            color = Color(0xFF38BDF8).copy(alpha = 0.55f + row * 0.12f),
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                AeTileVisualStyle.SWIRL_VORTEX -> {
                    for (ring in 1..4) {
                        drawArc(
                            color = Color(0xFF60A5FA).copy(alpha = 0.35f + ring * 0.15f),
                            startAngle = ring * 35f,
                            sweepAngle = 225f,
                            useCenter = false,
                            topLeft = Offset(cx - minD * ring * 0.10f, cy - minD * ring * 0.10f),
                            size = Size(minD * ring * 0.20f, minD * ring * 0.20f),
                            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                AeTileVisualStyle.PINCH_LENS -> {
                    drawRect(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFF00E676).copy(alpha = 0.45f), Color(0xFF1D4ED8).copy(alpha = 0.15f)),
                            center = Offset(cx, cy),
                            radius = minD * 0.45f
                        )
                    )
                    drawCircle(
                        color = Color(0xFF93C5FD),
                        radius = minD * 0.34f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.7f),
                        radius = minD * 0.15f,
                        center = Offset(cx - minD * 0.08f, cy - minD * 0.08f),
                        style = Stroke(width = 1.4.dp.toPx())
                    )
                }

                AeTileVisualStyle.MIRROR_AXIS -> {
                    drawRect(
                        color = Color(0xFF38BDF8).copy(alpha = 0.35f),
                        topLeft = Offset(w * 0.12f, h * 0.18f),
                        size = Size(w * 0.34f, h * 0.64f)
                    )
                    drawRect(
                        color = Color(0xFF818CF8).copy(alpha = 0.55f),
                        topLeft = Offset(w * 0.54f, h * 0.18f),
                        size = Size(w * 0.34f, h * 0.64f)
                    )
                    drawLine(Color.White, Offset(cx, h * 0.08f), Offset(cx, h * 0.92f), strokeWidth = 2.dp.toPx())
                }

                AeTileVisualStyle.HEX_CELLS -> {
                    val r = minD * 0.15f
                    val centers = listOf(
                        Offset(cx, cy),
                        Offset(cx - r * 1.65f, cy - r * 0.9f),
                        Offset(cx + r * 1.65f, cy - r * 0.9f),
                        Offset(cx - r * 1.65f, cy + r * 0.9f),
                        Offset(cx + r * 1.65f, cy + r * 0.9f)
                    )
                    centers.forEach { c ->
                        val hex = Path().apply {
                            for (i in 0 until 6) {
                                val angle = (PI / 3.0) * i
                                val px = c.x + (r * 0.88f * cos(angle)).toFloat()
                                val py = c.y + (r * 0.88f * sin(angle)).toFloat()
                                if (i == 0) moveTo(px, py) else lineTo(px, py)
                            }
                            close()
                        }
                        drawPath(hex, color = Color(0xFF60A5FA).copy(alpha = 0.25f))
                        drawPath(hex, color = Color(0xFF93C5FD), style = Stroke(width = 1.4.dp.toPx()))
                    }
                }

                AeTileVisualStyle.GRID_MATRIX -> {
                    val step = 3
                    val cellW = w * 0.22f
                    val gap = w * 0.05f
                    val startX = (w - (cellW * step + gap * (step - 1))) / 2f
                    val startY = (h - (cellW * step + gap * (step - 1))) / 2f
                    for (r in 0 until step) {
                        for (c in 0 until step) {
                            drawRoundRect(
                                color = if ((r + c) % 2 == 0) Color(0xFF38BDF8) else Color(0xFF4F46E5),
                                topLeft = Offset(startX + c * (cellW + gap), startY + r * (cellW + gap)),
                                size = Size(cellW, cellW),
                                cornerRadius = CornerRadius(3.dp.toPx())
                            )
                        }
                    }
                }

                AeTileVisualStyle.RADIAL_RING -> {
                    val count = 8
                    val ringR = minD * 0.30f
                    for (i in 0 until count) {
                        val a = (2 * PI / count) * i
                        val px = cx + (ringR * cos(a)).toFloat()
                        val py = cy + (ringR * sin(a)).toFloat()
                        drawCircle(
                            color = if (i % 2 == 0) Color(0xFF00E676) else Color(0xFF818CF8),
                            radius = minD * 0.075f,
                            center = Offset(px, py)
                        )
                    }
                }

                AeTileVisualStyle.MATTE_KEY -> {
                    drawRect(
                        color = Color(0xFF10B981).copy(alpha = 0.25f),
                        topLeft = Offset(w * 0.10f, h * 0.10f),
                        size = Size(w * 0.80f, h * 0.80f)
                    )
                    drawCircle(color = Color(0xFFA78BFA), radius = minD * 0.24f, center = Offset(cx, cy))
                    drawCircle(color = Color.White, radius = minD * 0.24f, center = Offset(cx, cy), style = Stroke(width = 1.6.dp.toPx()))
                }

                AeTileVisualStyle.ALPHA_FADE -> {
                    for (i in 0..3) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.9f - i * 0.22f),
                            topLeft = Offset(w * (0.14f + i * 0.14f), h * 0.20f),
                            size = Size(w * 0.22f, h * 0.60f),
                            cornerRadius = CornerRadius(4.dp.toPx())
                        )
                    }
                }

                AeTileVisualStyle.PROCEDURAL_NOISE -> {
                    drawRect(
                        brush = Brush.linearGradient(listOf(Color(0xFF312E81), Color(0xFF7C3AED), Color(0xFF2563EB)))
                    )
                    for (i in 0..10) {
                        val x = w * ((i * 37 % 80 + 10) / 100f)
                        val y = h * ((i * 53 % 80 + 10) / 100f)
                        drawCircle(Color.White.copy(alpha = 0.65f), radius = 1.8.dp.toPx(), center = Offset(x, y))
                    }
                }

                AeTileVisualStyle.STARFIELD -> {
                    val pts = listOf(
                        Offset(0.2f, 0.25f), Offset(0.75f, 0.2f), Offset(0.5f, 0.5f),
                        Offset(0.28f, 0.72f), Offset(0.82f, 0.68f), Offset(0.62f, 0.82f)
                    )
                    pts.forEachIndexed { idx, p ->
                        drawCircle(
                            color = if (idx == 2) Color(0xFFFACC15) else Color.White,
                            radius = if (idx == 2) 4.dp.toPx() else 2.dp.toPx(),
                            center = Offset(w * p.x, h * p.y)
                        )
                    }
                }

                AeTileVisualStyle.LIGHTNING_BOLT -> {
                    val bolt = Path().apply {
                        moveTo(w * 0.58f, h * 0.12f)
                        lineTo(w * 0.34f, h * 0.50f)
                        lineTo(w * 0.54f, h * 0.50f)
                        lineTo(w * 0.40f, h * 0.88f)
                        lineTo(w * 0.70f, h * 0.44f)
                        lineTo(w * 0.50f, h * 0.44f)
                        close()
                    }
                    drawPath(bolt, color = Color(0xFF22D3EE))
                    drawPath(bolt, color = Color.White, style = Stroke(width = 1.dp.toPx()))
                }

                AeTileVisualStyle.THREE_D_CUBE -> {
                    val s = minD * 0.28f
                    drawRect(
                        color = Color(0xFF6366F1).copy(alpha = 0.45f),
                        topLeft = Offset(cx - s * 0.75f, cy - s * 0.45f),
                        size = Size(s * 1.25f, s * 1.25f)
                    )
                    drawRect(
                        color = Color(0xFF93C5FD),
                        topLeft = Offset(cx - s * 0.45f, cy - s * 0.75f),
                        size = Size(s * 1.25f, s * 1.25f),
                        style = Stroke(width = 1.8.dp.toPx(), join = StrokeJoin.Round)
                    )
                }

                AeTileVisualStyle.THREE_D_SPHERE -> {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFE0E7FF), Color(0xFF6366F1), Color(0xFF1E1B4B)),
                            center = Offset(cx - minD * 0.10f, cy - minD * 0.10f),
                            radius = minD * 0.42f
                        ),
                        radius = minD * 0.34f,
                        center = Offset(cx, cy)
                    )
                }

                AeTileVisualStyle.THREE_D_TORUS -> {
                    drawCircle(
                        color = Color(0xFF818CF8),
                        radius = minD * 0.26f,
                        center = Offset(cx, cy),
                        style = Stroke(width = minD * 0.14f)
                    )
                    drawCircle(
                        color = Color(0xFFE0E7FF),
                        radius = minD * 0.26f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                }

                AeTileVisualStyle.THREE_D_PAGE -> {
                    val page = Path().apply {
                        moveTo(w * 0.20f, h * 0.16f)
                        lineTo(w * 0.62f, h * 0.16f)
                        lineTo(w * 0.82f, h * 0.36f)
                        lineTo(w * 0.82f, h * 0.84f)
                        lineTo(w * 0.20f, h * 0.84f)
                        close()
                    }
                    drawPath(page, color = Color(0xFF312E81))
                    drawPath(page, color = Color(0xFF93C5FD), style = Stroke(width = 1.6.dp.toPx()))
                }

                AeTileVisualStyle.DRAW_STROKE -> {
                    val strokePath = Path().apply {
                        moveTo(w * 0.16f, h * 0.72f)
                        cubicTo(w * 0.35f, h * 0.18f, w * 0.65f, h * 0.82f, w * 0.84f, h * 0.28f)
                    }
                    drawPath(
                        strokePath,
                        color = Color(0xFF38BDF8),
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                AeTileVisualStyle.TEXT_GLYPH -> {
                    drawLine(Color(0xFF38BDF8), Offset(w * 0.22f, h * 0.26f), Offset(w * 0.78f, h * 0.26f), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(Color.White, Offset(cx, h * 0.26f), Offset(cx, h * 0.78f), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
    }
}
