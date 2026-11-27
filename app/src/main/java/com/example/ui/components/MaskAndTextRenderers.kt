package com.example.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EditorLayer
import com.example.model.MaskMode
import com.example.model.MaskShape
import com.example.model.TextLayerSpec
import com.example.ui.theme.SfProDisplayFontFamily
import com.example.ui.theme.SfProTextFontFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

fun resolveMaskShape(mask: MaskShape): Shape = when (mask) {
    MaskShape.NONE -> RectangleShape
    MaskShape.CIRCLE -> CircleShape
    MaskShape.ROUNDED_SQUARE -> RoundedCornerShape(24.dp)
    MaskShape.TRIANGLE -> GenericShape { size, _ ->
        val cx = size.width / 2f
        val pad = minOf(size.width, size.height) * 0.08f
        moveTo(cx, pad)
        lineTo(size.width - pad, size.height - pad)
        lineTo(pad, size.height - pad)
        close()
    }
    MaskShape.DIAMOND -> GenericShape { size, _ ->
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(size.width, size.height) * 0.45f
        moveTo(cx, cy - r)
        lineTo(cx + r, cy)
        lineTo(cx, cy + r)
        lineTo(cx - r, cy)
        close()
    }
    MaskShape.STAR -> GenericShape { size, _ ->
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(size.width, size.height) * 0.45f
        for (i in 0 until 10) {
            val angle = (-PI / 2.0) + i * (PI / 5.0)
            val rad = if (i % 2 == 0) r else r * 0.44f
            val x = cx + (rad * cos(angle)).toFloat()
            val y = cy + (rad * sin(angle)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    MaskShape.HEART -> GenericShape { size, _ ->
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(size.width, size.height) * 0.44f
        moveTo(cx, cy + r * 0.85f)
        cubicTo(cx - r * 1.3f, cy + r * 0.1f, cx - r * 0.9f, cy - r * 0.9f, cx, cy - r * 0.3f)
        cubicTo(cx + r * 0.9f, cy - r * 0.9f, cx + r * 1.3f, cy + r * 0.1f, cx, cy + r * 0.85f)
        close()
    }
}

fun Modifier.applyLayerMask(layer: EditorLayer): Modifier {
    val effectiveMode = when {
        layer.maskMode != MaskMode.NONE -> layer.maskMode
        layer.maskShape != MaskShape.NONE -> MaskMode.SHAPE
        else -> MaskMode.NONE
    }
    if (effectiveMode == MaskMode.NONE) return this

    return when (effectiveMode) {
        MaskMode.NONE -> this
        MaskMode.SHAPE -> {
            if (layer.maskShape == MaskShape.NONE) return this
            val shape = resolveMaskShape(layer.maskShape)
            if (!layer.maskInvert && layer.maskFeather <= 0.01f) {
                this.clip(shape)
            } else {
                this
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        val outline = shape.createOutline(size, layoutDirection, this)
                        val maskPath = Path().apply {
                            when (outline) {
                                is Outline.Rectangle -> addRect(outline.rect)
                                is Outline.Rounded -> addRoundRect(outline.roundRect)
                                is Outline.Generic -> addPath(outline.path)
                            }
                        }
                        val featherPx = (layer.maskFeather.coerceIn(0f, 1f) * minOf(size.width, size.height) * 0.12f)
                        val blend = if (layer.maskInvert) BlendMode.DstOut else BlendMode.DstIn
                        if (featherPx > 1f) {
                            drawIntoCanvas { canvas ->
                                val frameworkPaint = android.graphics.Paint().apply {
                                    isAntiAlias = true
                                    color = android.graphics.Color.BLACK
                                    style = android.graphics.Paint.Style.FILL
                                    maskFilter = BlurMaskFilter(featherPx, BlurMaskFilter.Blur.NORMAL)
                                }
                                canvas.nativeCanvas.saveLayer(0f, 0f, size.width, size.height, null)
                                canvas.nativeCanvas.drawPath(maskPath.asAndroidPath(), frameworkPaint)
                                canvas.nativeCanvas.restore()
                                drawOutline(
                                    outline = outline,
                                    color = Color.Black,
                                    blendMode = blend
                                )
                            }
                        } else {
                            drawOutline(
                                outline = outline,
                                color = Color.Black,
                                blendMode = blend
                            )
                        }
                    }
            }
        }
        MaskMode.LINEAR -> {
            this
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    val rad = Math.toRadians(layer.maskLinearAngle.toDouble())
                    val dx = cos(rad).toFloat()
                    val dy = sin(rad).toFloat()
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val halfDiag = (maxOf(size.width, size.height) * 0.5f) * (0.25f + layer.maskFeather.coerceIn(0f, 1f) * 0.75f)
                    val start = Offset(cx - dx * halfDiag, cy - dy * halfDiag)
                    val end = Offset(cx + dx * halfDiag, cy + dy * halfDiag)
                    val brush = if (layer.maskInvert) {
                        Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color.Black),
                            start = start,
                            end = end
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(Color.Black, Color.Transparent),
                            start = start,
                            end = end
                        )
                    }
                    drawRect(
                        brush = brush,
                        blendMode = BlendMode.DstIn
                    )
                }
        }
    }
}

fun textFontForStyleId(styleId: String): Pair<FontFamily, FontWeight> {
    com.example.engine.packs.CustomFontRegistry.resolveComposeFont(styleId)?.let { return it }
    return when (styleId) {
        "sf_display" -> SfProTextFontFamily to FontWeight.SemiBold
        "arabic_naskh" -> FontFamily.Serif to FontWeight.Bold
        "mono_arcade" -> FontFamily.Monospace to FontWeight.Bold
        else -> SfProDisplayFontFamily to FontWeight.ExtraBold
    }
}

@Composable
fun RichLayerTextOverlay(
    layer: EditorLayer,
    modifier: Modifier = Modifier
) {
    val content = layer.effectiveText
    if (content.isBlank()) return
    val spec: TextLayerSpec = layer.textSpec
    val (fontFamily, fontWeight) = textFontForStyleId(spec.fontStyleId)
    val fillColor = Color(spec.textColorArgb.toInt())
    val strokeColor = Color(spec.strokeColorArgb.toInt())
    val shadow = when {
        spec.glowIntensity > 0.05f -> Shadow(
            color = fillColor.copy(alpha = (0.55f + spec.glowIntensity * 0.4f).coerceIn(0f, 1f)),
            offset = Offset.Zero,
            blurRadius = 24f * spec.glowIntensity
        )
        spec.shadowRadius > 0.5f -> Shadow(
            color = Color.Black.copy(alpha = 0.7f),
            offset = Offset(2f, 3f),
            blurRadius = spec.shadowRadius
        )
        else -> null
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (spec.strokeWidthPx > 0.5f) {
            Text(
                text = content,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontFamily = fontFamily,
                    fontWeight = fontWeight,
                    fontSize = spec.fontSizeSp.sp,
                    color = strokeColor,
                    drawStyle = Stroke(width = spec.strokeWidthPx * 2f)
                )
            )
        }
        Text(
            text = content,
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                fontSize = spec.fontSizeSp.sp,
                color = fillColor,
                shadow = shadow
            )
        )
    }
}
