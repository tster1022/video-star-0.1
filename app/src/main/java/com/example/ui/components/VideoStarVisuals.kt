package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.VideoStarLightSurface
import com.example.ui.theme.VideoStarLogoNavy
import com.example.ui.theme.VideoStarMagenta
import com.example.ui.theme.VideoStarPatternLine
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Renders the centered original StarStudio ("استوديو النجم") header lockup
 * with a custom dual-tone prism-star emblem in deep teal (#0E4D64) and magenta (#B81D6F).
 */
@Composable
fun VideoStarLogo(modifier: Modifier = Modifier) {
    StarStudioLogo(modifier = modifier)
}

@Composable
fun StarStudioLogo(modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InterlockingStarGlyph(
                size = 26.dp,
                primaryColor = VideoStarMagenta,
                outlineColor = VideoStarLogoNavy
            )
            Text(
                text = "استوديو النجم",
                fontFamily = com.example.ui.theme.CairoFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 19.sp,
                color = VideoStarLogoNavy
            )
            Text(
                text = "StarStudio",
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 0.4.sp,
                color = VideoStarMagenta
            )
        }
    }
}

@Composable
fun InterlockingStarGlyph(
    size: Dp,
    primaryColor: Color,
    outlineColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val outerR = this.size.minDimension * 0.46f
        val innerR = outerR * 0.45f

        val starPath = Path()
        for (i in 0 until 10) {
            val angle = (-PI / 2.0) + i * (PI / 5.0)
            val r = if (i % 2 == 0) outerR else innerR
            val x = cx + (r * cos(angle)).toFloat()
            val y = cy + (r * sin(angle)).toFloat()
            if (i == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
        }
        starPath.close()

        drawPath(
            path = starPath,
            color = outlineColor,
            style = Stroke(width = 2.2.dp.toPx(), join = StrokeJoin.Round)
        )
        drawPath(
            path = starPath,
            brush = Brush.linearGradient(colors = listOf(primaryColor, Color(0xFFFF2A85)))
        )

        // Inner play-aperture triangle representing video motion inside StarStudio emblem
        val triR = outerR * 0.34f
        val playPath = Path().apply {
            moveTo(cx - triR * 0.45f, cy - triR * 0.65f)
            lineTo(cx + triR * 0.85f, cy)
            lineTo(cx - triR * 0.45f, cy + triR * 0.65f)
            close()
        }
        drawPath(path = playPath, color = Color.White.copy(alpha = 0.92f))
    }
}

/**
 * Draws the subtle geometric equilateral triangle grid (#E3E3E3, 1px stroke)
 * overlaid on the light off-white/gray canvas (#F2F2F2).
 */
fun Modifier.geometricTriangleBackground(
    baseColor: Color = VideoStarLightSurface,
    lineColor: Color = VideoStarPatternLine
): Modifier = this.drawBehind {
    drawRect(color = baseColor)
    val side = 36.dp.toPx()
    val rowHeight = side * (sqrt(3f) / 2f)
    val strokePx = 1.dp.toPx().coerceAtLeast(1f)
    val rows = (size.height / rowHeight).toInt() + 3
    val cols = (size.width / side).toInt() + 4

    for (row in -1..rows) {
        val y0 = row * rowHeight
        val y1 = (row + 1) * rowHeight
        val xShift0 = if ((row and 1) == 0) 0f else side * 0.5f
        val xShift1 = if (((row + 1) and 1) == 0) 0f else side * 0.5f

        // Horizontal line along row top
        drawLine(
            color = lineColor,
            start = Offset(0f, y0),
            end = Offset(size.width, y0),
            strokeWidth = strokePx
        )

        for (col in -2..cols) {
            val xTop = col * side + xShift0
            val xBotLeft = if ((row and 1) == 0) (col - 1) * side + xShift1 else col * side + xShift1
            val xBotRight = xBotLeft + side

            // Left diagonal edge of equilateral triangle
            drawLine(
                color = lineColor,
                start = Offset(xTop, y0),
                end = Offset(xBotLeft, y1),
                strokeWidth = strokePx
            )
            // Right diagonal edge of equilateral triangle
            drawLine(
                color = lineColor,
                start = Offset(xTop, y0),
                end = Offset(xBotRight, y1),
                strokeWidth = strokePx
            )
        }
    }
}

/**
 * Multi-hue color wheel icon used in the Multi-Layer Editor sub-toolbar.
 */
@Composable
fun ColorWheelIcon(
    selectedColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(28.dp)) {
        val radius = size.minDimension / 2f
        val sweep = Brush.sweepGradient(
            colors = listOf(
                Color.Red,
                Color.Yellow,
                Color.Green,
                Color.Cyan,
                Color.Blue,
                Color.Magenta,
                Color.Red
            )
        )
        drawCircle(brush = sweep, radius = radius)
        drawCircle(color = Color(0xFF0D0D0F), radius = radius * 0.45f)
        drawCircle(color = selectedColor, radius = radius * 0.30f)
    }
}
