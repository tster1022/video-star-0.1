package com.example.ui.screens.layers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.ui.components.PrecisionRulerDialSlider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.packs.CustomFontRegistry
import com.example.engine.packs.CustomPackRepository
import com.example.model.EditorLayer
import com.example.model.TextLayerSpec
import com.example.ui.components.IosHairlineDivider
import com.example.ui.components.iosPressable
import com.example.ui.screens.packs.PackImportStudioSheet
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.ClassicGreen
import com.example.ui.theme.ClassicRed
import com.example.ui.theme.VideoStarInkDark
import com.example.ui.theme.VideoStarInkMuted
import com.example.ui.theme.VideoStarIosDivider
import com.example.ui.theme.VideoStarWhite
import kotlin.math.roundToInt

private val textFontStyles = listOf(
    "sf_bold" to "عريض",
    "sf_display" to "عنوان",
    "arabic_naskh" to "نسخ عربي",
    "mono_arcade" to "رقمي"
)

private val textColorPalette = listOf(
    0xFFFFFFFFL,
    0xFF000000L,
    0xFFD91E18L,
    0xFF2E7D32L,
    0xFFFF2A85L,
    0xFF00E5FFL,
    0xFFFFD600L
)

@Composable
fun TextLayerEditor(
    activeLayer: EditorLayer?,
    isDarkTheme: Boolean,
    onTextSpecUpdated: (TextLayerSpec) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isDarkTheme) Color(0xFF222228) else VideoStarWhite
    val borderColor = if (isDarkTheme) Color(0xFF3A3A44) else VideoStarIosDivider
    val primaryText = if (isDarkTheme) VideoStarWhite else VideoStarInkDark
    val mutedText = if (isDarkTheme) Color(0xFFAAAAB4) else VideoStarInkMuted

    val spec = activeLayer?.textSpec?.let {
        if (it.text.isBlank() && !activeLayer.textOverlay.isNullOrBlank()) it.copy(text = activeLayer.textOverlay) else it
    } ?: TextLayerSpec()

    val importedFonts by CustomPackRepository.importedFonts.collectAsStateWithLifecycle()
    val allFontOptions = remember(importedFonts) {
        textFontStyles + CustomFontRegistry.importedFontOptions()
    }
    var showFontPackImporter by remember { mutableStateOf(false) }

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
                        text = "استوديو النصوص",
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
                            .testTag("text_editor_done")
                    )
                }

                OutlinedTextField(
                    value = spec.text,
                    onValueChange = { onTextSpecUpdated(spec.copy(text = it)) },
                    placeholder = { Text("اكتب نص الطبقة...", fontFamily = CairoFontFamily) },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("text_editor_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ClassicGreen.copy(alpha = 0.16f),
                        border = BorderStroke(0.8.dp, ClassicGreen.copy(alpha = 0.65f)),
                        modifier = Modifier
                            .height(34.dp)
                            .iosPressable { showFontPackImporter = true }
                            .testTag("text_import_font_btn")
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "+ خط / XML",
                                fontFamily = CairoFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ClassicGreen
                            )
                        }
                    }

                    allFontOptions.forEach { (id, label) ->
                        val selected = spec.fontStyleId == id
                        val customPreviewFont = remember(id, importedFonts) {
                            CustomFontRegistry.resolveComposeFont(id)?.first
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) ClassicRed else if (isDarkTheme) Color(0xFF303038) else Color(0xFFF2F2F7),
                            border = BorderStroke(0.5.dp, if (selected) ClassicRed else borderColor),
                            modifier = Modifier
                                .height(34.dp)
                                .iosPressable { onTextSpecUpdated(spec.copy(fontStyleId = id)) }
                                .testTag("text_font_$id")
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    fontFamily = customPreviewFont ?: CairoFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) Color.White else primaryText
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("اللون", fontFamily = CairoFontFamily, color = mutedText, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        textColorPalette.forEach { argb ->
                            val selected = spec.textColorArgb == argb
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb.toInt()))
                                    .border(
                                        width = if (selected) 2.5.dp else 1.dp,
                                        color = if (selected) ClassicGreen else borderColor,
                                        shape = CircleShape
                                    )
                                    .iosPressable { onTextSpecUpdated(spec.copy(textColorArgb = argb)) }
                            )
                        }
                    }
                }

                IosHairlineDivider()

                PrecisionRulerDialSlider(
                    label = "الحجم",
                    valueText = "${spec.fontSizeSp.roundToInt()} sp",
                    value = spec.fontSizeSp,
                    onValueChange = { onTextSpecUpdated(spec.copy(fontSizeSp = it)) },
                    valueRange = 16f..64f,
                    height = 38.dp,
                    sliderTestTag = "text_size_slider"
                )
                PrecisionRulerDialSlider(
                    label = "الحدود",
                    valueText = String.format(java.util.Locale.US, "%.1f px", spec.strokeWidthPx),
                    value = spec.strokeWidthPx,
                    onValueChange = { onTextSpecUpdated(spec.copy(strokeWidthPx = it)) },
                    valueRange = 0f..8f,
                    height = 38.dp,
                    sliderTestTag = "text_stroke_slider"
                )
                PrecisionRulerDialSlider(
                    label = "التوهج",
                    valueText = "${(spec.glowIntensity * 100f).roundToInt()}%",
                    value = spec.glowIntensity,
                    onValueChange = { onTextSpecUpdated(spec.copy(glowIntensity = it)) },
                    valueRange = 0f..1f,
                    height = 38.dp,
                    sliderTestTag = "text_glow_slider"
                )
            }
        }
    }

    if (showFontPackImporter) {
        PackImportStudioSheet(
            initialTab = "FONTS",
            onDismiss = { showFontPackImporter = false },
            onSelectFontId = { fontId ->
                onTextSpecUpdated(spec.copy(fontStyleId = fontId))
            }
        )
    }
}
