package com.example.ui.screens.layers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import com.example.ui.components.PrecisionRulerDialSlider
import com.example.ui.components.rememberStudioTickHaptics
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SamCutoutSpec
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.ClassicGreen
import com.example.ui.theme.ClassicRed
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.VideoStarInkDark
import com.example.ui.theme.VideoStarInkMuted
import com.example.ui.theme.VideoStarMagenta
import com.example.ui.theme.VideoStarNeonPink
import com.example.ui.theme.VideoStarWhite
import kotlin.math.roundToInt

@Composable
fun SamCutoutControlsCard(
    samCutout: SamCutoutSpec,
    isProcessing: Boolean,
    isTapMode: Boolean,
    tapInclude: Boolean,
    hasLayerMedia: Boolean,
    isDarkTheme: Boolean,
    onRunAutoCutout: () -> Unit,
    onToggleTapMode: () -> Unit,
    onSetTapInclude: (Boolean) -> Unit,
    onUpdateRefinement: (feather: Float, choke: Float, invert: Boolean) -> Unit,
    onClearCutout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (isDarkTheme) VideoStarNeonPink else ClassicGreen
    val secondaryAccent = if (isDarkTheme) VideoStarMagenta else ClassicRed
    val cardBg = if (isDarkTheme) Color(0xFF1A1A22) else Color(0xFFF7F8FA)
    val borderCol = if (samCutout.isActive) accent.copy(alpha = 0.7f) else if (isDarkTheme) Color(0xFF3A3A44) else Color(0xFFE0E0E6)
    val textPrimary = if (isDarkTheme) VideoStarWhite else VideoStarInkDark
    val textMuted = if (isDarkTheme) Color(0xFFAAAAB4) else VideoStarInkMuted

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardBg,
        border = BorderStroke(0.9.dp, borderCol),
        modifier = modifier.fillMaxWidth().testTag("sam_cutout_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "AI Subject Cutout (SAM)",
                        fontFamily = InterFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                }

                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = accent,
                        strokeWidth = 2.dp
                    )
                } else if (samCutout.isActive) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accent.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = "${samCutout.detectedSubjectCount} Subject(s)",
                            fontFamily = InterFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accent,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onRunAutoCutout,
                    enabled = hasLayerMedia && !isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("sam_auto_cutout_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (samCutout.isActive) "Re-Cutout" else "Auto Cutout",
                        fontFamily = InterFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onToggleTapMode,
                    enabled = hasLayerMedia && !isProcessing,
                    border = BorderStroke(1.dp, if (isTapMode) secondaryAccent else borderCol),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("sam_tap_mode_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.TouchApp,
                        contentDescription = null,
                        tint = if (isTapMode) secondaryAccent else textPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isTapMode) "Tap Active" else "Tap Select",
                        fontFamily = InterFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isTapMode) secondaryAccent else textPrimary
                    )
                }

                if (samCutout.isActive || samCutout.tapPoints.isNotEmpty()) {
                    IconButton(
                        onClick = onClearCutout,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("sam_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Clear SAM Cutout",
                            tint = ClassicRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (isTapMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = tapInclude,
                        onClick = { onSetTapInclude(true) },
                        label = { Text("+ Include", fontFamily = InterFontFamily, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ClassicGreen,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("sam_tap_include_chip")
                    )
                    FilterChip(
                        selected = !tapInclude,
                        onClick = { onSetTapInclude(false) },
                        label = { Text("- Exclude", fontFamily = InterFontFamily, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ClassicRed,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("sam_tap_exclude_chip")
                    )
                    Text(
                        text = "Tap preview (${samCutout.tapPoints.size} pts)",
                        fontFamily = InterFontFamily,
                        fontSize = 10.sp,
                        color = textMuted
                    )
                }
            }

            if (samCutout.isActive) {
                val haptics = rememberStudioTickHaptics()
                PrecisionRulerDialSlider(
                    label = "Choke",
                    valueText = "${(samCutout.choke * 100f).roundToInt()}",
                    value = samCutout.choke,
                    onValueChange = { onUpdateRefinement(samCutout.feather, it, samCutout.invert) },
                    valueRange = -1f..1f,
                    defaultValue = 0f,
                    height = 38.dp,
                    sliderTestTag = "sam_choke_slider"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PrecisionRulerDialSlider(
                        label = "Feather",
                        valueText = "${(samCutout.feather * 100f).roundToInt()}%",
                        value = samCutout.feather,
                        onValueChange = { onUpdateRefinement(it, samCutout.choke, samCutout.invert) },
                        valueRange = 0f..1f,
                        defaultValue = 0f,
                        height = 38.dp,
                        sliderTestTag = "sam_feather_slider",
                        modifier = Modifier.weight(1f)
                    )
                    Text("Invert", fontFamily = CairoFontFamily, fontSize = 11.sp, color = textMuted)
                    Switch(
                        checked = samCutout.invert,
                        onCheckedChange = {
                            haptics.pulseSelection()
                            onUpdateRefinement(samCutout.feather, samCutout.choke, it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = secondaryAccent),
                        modifier = Modifier.testTag("sam_invert_switch")
                    )
                }
            } else if (!hasLayerMedia) {
                Text(
                    text = "Import a photo or video to this layer to enable AI Subject Cutout.",
                    fontFamily = InterFontFamily,
                    fontSize = 10.sp,
                    color = textMuted
                )
            }
        }
    }
}
