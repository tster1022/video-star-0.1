package com.example.ui.screens.effects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ClassicGreen
import com.example.ui.theme.ClassicRed
import com.example.ui.theme.SfProDisplayFontFamily
import com.example.ui.theme.SfProTextFontFamily
import com.example.ui.theme.VideoStarInkDark
import com.example.ui.theme.VideoStarInkMuted
import com.example.ui.theme.VideoStarIosDivider
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Video Star iOS-style rotary Jog Wheel for tactile fine-tuning of GPU shader parameters.
 */
@Composable
fun EffectsJogWheel(
    label: String,
    value: Float,
    minValue: Float,
    maxValue: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val span = (maxValue - minValue).takeIf { it > 0.0001f } ?: 1f
    val normalized = ((value - minValue) / span).coerceIn(0f, 1f)
    var lastAngleDeg by remember { mutableFloatStateOf(0f) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .testTag("effects_jog_wheel")
                .pointerInput(enabled, minValue, maxValue, value) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            lastAngleDeg = Math.toDegrees(atan2((offset.y - cy).toDouble(), (offset.x - cx).toDouble())).toFloat()
                        },
                        onDrag = { change, _ ->
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val pos = change.position
                            val nextDeg = Math.toDegrees(atan2((pos.y - cy).toDouble(), (pos.x - cx).toDouble())).toFloat()
                            var delta = nextDeg - lastAngleDeg
                            if (delta > 180f) delta -= 360f
                            if (delta < -180f) delta += 360f
                            lastAngleDeg = nextDeg
                            val nextNorm = (normalized + delta / 260f).coerceIn(0f, 1f)
                            onValueChange(minValue + nextNorm * span)
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f - 4.dp.toPx()
                drawCircle(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFFF6F6F9), Color(0xFFDCE0E6))
                    ),
                    radius = r
                )
                drawCircle(
                    color = if (enabled) VideoStarIosDivider else Color(0xFFE0E0E5),
                    radius = r,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Active sweep arc
                drawArc(
                    color = if (enabled) ClassicGreen else VideoStarInkMuted,
                    startAngle = 135f,
                    sweepAngle = 270f * normalized,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // Tick marks around outer edge
                for (i in 0..10) {
                    val deg = 135.0 + (i / 10.0) * 270.0
                    val rad = Math.toRadians(deg)
                    val p1 = Offset(
                        center.x + ((r - 6.dp.toPx()) * cos(rad)).toFloat(),
                        center.y + ((r - 6.dp.toPx()) * sin(rad)).toFloat()
                    )
                    val p2 = Offset(
                        center.x + ((r - 2.dp.toPx()) * cos(rad)).toFloat(),
                        center.y + ((r - 2.dp.toPx()) * sin(rad)).toFloat()
                    )
                    drawLine(Color(0xFF9E9EA8), p1, p2, strokeWidth = 1.dp.toPx())
                }

                // Thumb indicator dot
                val activeDeg = 135.0 + normalized * 270.0
                val activeRad = activeDeg * (PI / 180.0)
                val dotPos = Offset(
                    center.x + ((r - 13.dp.toPx()) * cos(activeRad)).toFloat(),
                    center.y + ((r - 13.dp.toPx()) * sin(activeRad)).toFloat()
                )
                drawCircle(
                    color = if (enabled) ClassicRed else VideoStarInkMuted,
                    radius = 5.dp.toPx(),
                    center = dotPos
                )
            }

            Text(
                text = "${(normalized * 100f).roundToInt()}%",
                fontFamily = SfProDisplayFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) VideoStarInkDark else VideoStarInkMuted
            )
        }

        Text(
            text = label,
            fontFamily = SfProTextFontFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = VideoStarInkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
