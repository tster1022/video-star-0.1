package com.example.ui.screens.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.packs.CustomPackRepository
import com.example.model.EffectCatalog
import com.example.model.EffectCatalogItem
import com.example.model.EffectCategory
import com.example.model.ShaderEffectPass
import com.example.model.StudioLayoutMode
import com.example.model.isCustomPackEffect
import com.example.ui.screens.packs.PackImportStudioSheet
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.VideoStarMagenta

private val VideoStarInlineCategories = listOf(
    "All",
    "My Effects",
    "Shake",
    "Blur",
    "Color",
    "Glitch",
    "Mirror",
    "Retro"
)

private val AfterEffectsInlineCategories = listOf(
    "All",
    "My Effects",
    "Move/Transform",
    "Distortion/Warp",
    "Matte/Mask/Key",
    "Opacity/Visibility",
    "Procedural",
    "Repeat",
    "3D",
    "Text",
    "Other"
)

@Composable
fun WorkspaceInlineEffectsDrawer(
    layoutMode: StudioLayoutMode,
    activeCategory: String,
    appliedPasses: List<ShaderEffectPass>,
    hasScenes: Boolean,
    onSelectCategory: (String) -> Unit,
    onToggleShaderEffect: (String) -> Unit,
    onClearAllEffects: () -> Unit,
    onOpenFullStudio: () -> Unit,
    onOpenShaderGallery: () -> Unit = {},
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = when (layoutMode) {
        StudioLayoutMode.BLURRR -> Color(0xFFB8FF29)
        StudioLayoutMode.NODE_VIDEO -> Color(0xFF4F8BFF)
        StudioLayoutMode.AFTER_EFFECTS -> Color(0xFF00E676)
        StudioLayoutMode.CAPCUT -> Color(0xFF00E5FF)
        StudioLayoutMode.NOVA -> VideoStarMagenta
    }
    val onAccent = if (layoutMode == StudioLayoutMode.BLURRR || layoutMode == StudioLayoutMode.CAPCUT || layoutMode == StudioLayoutMode.AFTER_EFFECTS) {
        Color(0xFF0E1015)
    } else {
        Color.White
    }

    val appliedIds = remember(appliedPasses) { appliedPasses.map { it.effectId }.toSet() }
    val isAfterEffects = layoutMode == StudioLayoutMode.AFTER_EFFECTS
    val customShaders by CustomPackRepository.importedShaders.collectAsStateWithLifecycle()
    val customPresets by CustomPackRepository.importedPresets.collectAsStateWithLifecycle()
    var showPackImporter by remember { mutableStateOf(false) }

    val scopedCatalog = remember(isAfterEffects, customShaders, customPresets) {
        if (isAfterEffects) {
            EffectCatalog.combinedShaderEffects.filter { it.category.isAlightMotion || it.isCustomPackEffect() }
        } else {
            EffectCatalog.combinedShaderEffects.filter { !it.category.isAlightMotion || it.isCustomPackEffect() }
        }
    }
    val categories = if (isAfterEffects) AfterEffectsInlineCategories else VideoStarInlineCategories

    val filteredEffects = remember(activeCategory, scopedCatalog, isAfterEffects) {
        val filtered = if (activeCategory == "My Effects") {
            scopedCatalog.filter { it.isCustomPackEffect() }
        } else if (isAfterEffects) {
            when (activeCategory) {
                "Move/Transform" -> scopedCatalog.filter { it.category == EffectCategory.AM_MOVE_TRANSFORM }
                "Distortion/Warp" -> scopedCatalog.filter { it.category == EffectCategory.AM_DISTORTION_WARP }
                "Matte/Mask/Key" -> scopedCatalog.filter { it.category == EffectCategory.AM_MATTE_MASK_KEY }
                "Opacity/Visibility" -> scopedCatalog.filter { it.category == EffectCategory.AM_OPACITY_VISIBILITY }
                "Procedural" -> scopedCatalog.filter { it.category == EffectCategory.AM_PROCEDURAL }
                "Repeat" -> scopedCatalog.filter { it.category == EffectCategory.AM_REPEAT }
                "3D" -> scopedCatalog.filter { it.category == EffectCategory.AM_THREE_D }
                "Text" -> scopedCatalog.filter { it.category == EffectCategory.AM_TEXT }
                "Other" -> scopedCatalog.filter { it.category == EffectCategory.AM_OTHER }
                else -> scopedCatalog
            }
        } else {
            when (activeCategory) {
                "Shake" -> scopedCatalog.filter { it.category == EffectCategory.SHAKE_MOTION }
                "Blur" -> scopedCatalog.filter { it.category == EffectCategory.BLUR_FOCUS }
                "Color" -> scopedCatalog.filter { it.category == EffectCategory.COLOR_GLOW }
                "Glitch" -> scopedCatalog.filter { it.category == EffectCategory.DISTORTION_GLITCH }
                "Mirror" -> scopedCatalog.filter { it.category == EffectCategory.MIRROR_KALEIDOSCOPE }
                "Retro" -> scopedCatalog.filter { it.category == EffectCategory.RETRO_VHS }
                else -> scopedCatalog
            }
        }
        filtered
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(
            color = Color(0xFF12131B),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            border = BorderStroke(1.dp, Color(0xFF282B3B)),
            shadowElevation = 16.dp,
            modifier = modifier
                .fillMaxWidth()
                .testTag("workspace_effect_mode_drawer")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header: Effect Mode indicator + Done button (exits Effect Mode -> restores switcher)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(accent.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (isAfterEffects) {
                                    "After Effects FX (${scopedCatalog.size} AM)"
                                } else {
                                    "Video Star FX (${scopedCatalog.size} GPU)"
                                },
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Text(
                                text = if (appliedPasses.isEmpty()) {
                                    "إضافة تأثير • Switcher hidden while editing"
                                } else {
                                    "${appliedPasses.size} active pass(es) on scene"
                                },
                                fontFamily = CairoFontFamily,
                                fontSize = 11.sp,
                                color = Color(0xFF9195A8)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (appliedPasses.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable(onClick = onClearAllEffects)
                                    .testTag("effect_mode_clear_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = "Clear scene effects",
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Surface(
                            color = Color(0xFF222536),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF34384E)),
                            modifier = Modifier
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onDone)
                                .testTag("effect_mode_done_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Exit Effect Mode",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "تم",
                                    fontFamily = CairoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Category filter chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    categories.forEach { cat ->
                        val selected = cat == activeCategory
                        Surface(
                            color = if (selected) accent else Color(0xFF1B1D29),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.dp,
                                if (selected) accent else Color(0xFF2B2E40)
                            ),
                            modifier = Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelectCategory(cat) }
                                .testTag("effect_drawer_cat_${cat.lowercase().replace("/", "_").replace(" ", "_")}")
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cat,
                                    fontFamily = InterFontFamily,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = if (selected) onAccent else Color(0xFFC8CAD6)
                                )
                            }
                        }
                    }
                }

                // 1-Tap Quick Shader Strip
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredEffects, key = { it.id }) { item ->
                        QuickEffectChipCard(
                            item = item,
                            isApplied = item.id in appliedIds,
                            accent = accent,
                            onClick = { onToggleShaderEffect(item.id) }
                        )
                    }
                }

                // Launch full Keyframe Studio + 145 GPU Shaders Gallery
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onOpenFullStudio,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = onAccent
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("effect_mode_open_full_studio_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (hasScenes) {
                                "استوديو المؤثرات (${scopedCatalog.size}) + Keyframes"
                            } else {
                                "أضف ميديا لتشغيل (${scopedCatalog.size} مؤثر)"
                            },
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        color = Color(0xFF1F1634),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.6f)),
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showPackImporter = true }
                            .testTag("effect_drawer_import_packs_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = "استيراد حزم XML والشيدر",
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "XML",
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }

                    Surface(
                        color = Color(0xFF1E2233),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onOpenShaderGallery)
                            .testTag("effect_drawer_open_gallery_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.GridView,
                                contentDescription = "معرض 145 شادر",
                                tint = accent,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = "145 Gallery",
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPackImporter) {
        PackImportStudioSheet(
            initialTab = "ALL",
            onDismiss = { showPackImporter = false },
            onApplyEffectItem = { effectId ->
                onToggleShaderEffect(effectId)
            }
        )
    }
}

@Composable
private fun QuickEffectChipCard(
    item: EffectCatalogItem,
    isApplied: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        color = if (isApplied) accent.copy(alpha = 0.16f) else Color(0xFF1A1C26),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isApplied) 1.5.dp else 1.dp,
            color = if (isApplied) accent else Color(0xFF2D3142)
        ),
        modifier = Modifier
            .width(118.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("quick_effect_${item.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.category.shortLabel,
                    fontFamily = InterFontFamily,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isApplied) accent else Color(0xFF7D8298),
                    maxLines = 1
                )
                if (isApplied) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Applied",
                        tint = accent,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Text(
                text = item.name,
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
