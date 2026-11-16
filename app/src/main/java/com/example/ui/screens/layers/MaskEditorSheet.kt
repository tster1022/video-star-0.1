package com.example.ui.screens.layers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EditorLayer
import com.example.model.MaskMode
import com.example.model.MaskShape
import com.example.ui.components.IosHairlineDivider
import com.example.ui.components.iosPressable
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.ClassicGreen
import com.example.ui.theme.ClassicRed
import com.example.ui.theme.VideoStarInkDark
import com.example.ui.theme.VideoStarInkMuted
import com.example.ui.theme.VideoStarIosDivider
import com.example.ui.theme.VideoStarWhite
import kotlin.math.roundToInt

private fun MaskMode.arabicLabel(): String = when (this) {
    MaskMode.NONE -> "بدون قناع"
    MaskMode.SHAPE -> "قناع شكل"
    MaskMode.LINEAR -> "قناع خطي"
}

@Composable
fun MaskEditorSheet(
    activeLayer: EditorLayer?,
    isDarkTheme: Boolean,
    onMaskUpdated: (
        mode: MaskMode,
        shape: MaskShape,
        feather: Float,
        invert: Boolean,
        linearAngle: Float
    ) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isDarkTheme) Color(0xFF222228) else VideoStarWhite
    val borderColor = if (isDarkTheme) Color(0xFF3A3A44) else VideoStarIosDivider
    val primaryText = if (isDarkTheme) VideoStarWhite else VideoStarInkDark
    val mutedText = if (isDarkTheme) Color(0xFFAAAAB4) else VideoStarInkMuted

    val mode = activeLayer?.maskMode ?: MaskMode.NONE
    val shape = activeLayer?.maskShape ?: MaskShape.NONE
    val feather = activeLayer?.maskFeather ?: 0f
    val invert = activeLayer?.maskInvert ?: false
    val angle = activeLayer?.maskLinearAngle ?: 0f

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = bgColor,
            border = BorderStroke(0.5.dp, borderColor),
            modifier = modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "محرر القناع",
                        color = primaryText,
                        fontFamily = CairoFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "تم",
                        color = ClassicGreen,
                        fontFamily = CairoFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .iosPressable(onClick = onClose)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("mask_editor_done")
                    )
                }

                // Mask Mode Selector: None | Shape Mask | Linear Mask
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MaskMode.entries.forEach { m ->
                        val selected = mode == m
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) ClassicRed else if (isDarkTheme) Color(0xFF303038) else Color(0xFFF2F2F7),
                            border = BorderStroke(0.5.dp, if (selected) ClassicRed else borderColor),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .iosPressable {
                                    val nextShape = if (m == MaskMode.SHAPE && shape == MaskShape.NONE) MaskShape.CIRCLE else shape
                                    onMaskUpdated(m, nextShape, feather, invert, angle)
                                }
                                .testTag("mask_mode_${m.name.lowercase()}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = m.arabicLabel(),
                                    fontFamily = CairoFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) Color.White else primaryText
                                )
                            }
                        }
                    }
                }

                if (mode == MaskMode.SHAPE) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MaskShape.entries.filter { it != MaskShape.NONE }.forEach { s ->
                            val selected = shape == s
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) ClassicGreen else if (isDarkTheme) Color(0xFF303038) else Color(0xFFF2F2F7),
                                border = BorderStroke(0.5.dp, if (selected) ClassicGreen else borderColor),
                                modifier = Modifier
                                    .height(34.dp)
                                    .iosPressable { onMaskUpdated(MaskMode.SHAPE, s, feather, invert, angle) }
                                    .testTag("mask_shape_${s.name.lowercase()}")
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = s.arabicLabel,
                                        fontFamily = CairoFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selected) Color.White else primaryText
                                    )
                                }
                            }
                        }
                    }
                }

                if (mode == MaskMode.LINEAR) {
                    IosHairlineDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "الزاوية: ${angle.roundToInt()}°",
                            fontFamily = CairoFontFamily,
                            color = mutedText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = angle,
                            onValueChange = { onMaskUpdated(MaskMode.LINEAR, shape, feather, invert, it) },
                            valueRange = 0f..360f,
                            colors = SliderDefaults.colors(thumbColor = ClassicRed, activeTrackColor = ClassicRed),
                            modifier = Modifier.weight(1f).height(24.dp).testTag("mask_linear_angle_slider")
                        )
                    }
                }

                if (mode != MaskMode.NONE) {
                    IosHairlineDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "النعومة: ${(feather * 100f).roundToInt()}%",
                            fontFamily = CairoFontFamily,
                            color = mutedText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = feather,
                            onValueChange = { onMaskUpdated(mode, shape, it, invert, angle) },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(thumbColor = ClassicGreen, activeTrackColor = ClassicGreen),
                            modifier = Modifier.weight(1f).height(24.dp).testTag("mask_feather_slider")
                        )
                        Text("عكس", fontFamily = CairoFontFamily, color = mutedText, fontSize = 11.sp)
                        Switch(
                            checked = invert,
                            onCheckedChange = { onMaskUpdated(mode, shape, feather, it, angle) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ClassicRed),
                            modifier = Modifier.testTag("mask_invert_switch")
                        )
                    }
                }
            }
        }
    }
}
