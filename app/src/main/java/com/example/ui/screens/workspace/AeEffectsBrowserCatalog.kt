package com.example.ui.screens.workspace

import androidx.compose.ui.graphics.Color
import com.example.engine.ae.AeEffectEngineCatalog
import com.example.engine.ae.AeEffectSource
import com.example.engine.ae.AeEngineCategory
import com.example.model.EffectCatalogItem

internal enum class AeBrowserOrigin(val badgeLabel: String, val filterLabel: String) {
    AFTER_EFFECTS("Ae", "After Effects"),
    ALIGHT_MOTION("AM", "Alight Motion")
}

internal enum class AeBrowserCategory(
    val id: String,
    val title: String,
    val gradStart: Color,
    val gradEnd: Color,
    val accentColor: Color
) {
    MY_EFFECTS("my_effects", "My effects", Color(0xFF232838), Color(0xFF191D2A), Color(0xFFFACC15)),
    COLOR("color", "Color & Light", Color(0xFF1A294B), Color(0xFF0E1528), Color(0xFF38BDF8)),
    TIME("time", "Time", Color(0xFFDC5442), Color(0xFF531E26), Color(0xFFFF7A66)),
    BLUR("blur", "Blur", Color(0xFF13304A), Color(0xFF0C1524), Color(0xFF00E5FF)),
    DISTORTION("distortion", "Distortion/Warp", Color(0xFF18315A), Color(0xFF101933), Color(0xFF60A5FA)),
    STYLIZE("stylize", "Stylize", Color(0xFF12364C), Color(0xFF0B1829), Color(0xFF22D3EE)),
    CUTOUT("cutout", "Matte/Mask/Key", Color(0xFF2B1C54), Color(0xFF14112E), Color(0xFFA78BFA)),
    PROCEDURAL("procedural", "Procedural", Color(0xFF2A194E), Color(0xFF121028), Color(0xFFC084FC)),
    REPEAT("repeat", "Repeat", Color(0xFF341E5C), Color(0xFF15122C), Color(0xFF818CF8)),
    TRANSFORM("transform", "Move/Transform", Color(0xFF232838), Color(0xFF1A1E2B), Color(0xFF94A3B8)),
    THREE_D("3d", "3D", Color(0xFF1B2452), Color(0xFF0F132A), Color(0xFF6366F1)),
    DRAWING_EDGE("drawing", "Drawing & Edge", Color(0xFF202A3E), Color(0xFF121826), Color(0xFF38BDF8)),
    TEXT("text", "Text", Color(0xFF18263C), Color(0xFF101624), Color(0xFF38BDF8)),
    OPACITY("opacity", "Opacity/Visibility", Color(0xFF242938), Color(0xFF191D29), Color(0xFF9CA3AF)),
    TRANSITION("transition", "Transition", Color(0xFF242938), Color(0xFF191D29), Color(0xFF00E676)),
    OTHER("other", "Other", Color(0xFF252A3F), Color(0xFF151929), Color(0xFF60A5FA))
}

internal data class AeBrowserEffectMeta(
    val browserCategory: AeBrowserCategory,
    val origin: AeBrowserOrigin,
    val description: String
)

internal fun AeEngineCategory.toBrowserCategory(): AeBrowserCategory = when (this) {
    AeEngineCategory.MY_EFFECTS -> AeBrowserCategory.MY_EFFECTS
    AeEngineCategory.COLOR -> AeBrowserCategory.COLOR
    AeEngineCategory.BLUR -> AeBrowserCategory.BLUR
    AeEngineCategory.DISTORTION -> AeBrowserCategory.DISTORTION
    AeEngineCategory.STYLIZE -> AeBrowserCategory.STYLIZE
    AeEngineCategory.CUTOUT -> AeBrowserCategory.CUTOUT
    AeEngineCategory.PROCEDURAL -> AeBrowserCategory.PROCEDURAL
    AeEngineCategory.REPEAT -> AeBrowserCategory.REPEAT
    AeEngineCategory.TRANSFORM -> AeBrowserCategory.TRANSFORM
    AeEngineCategory.THREE_D -> AeBrowserCategory.THREE_D
    AeEngineCategory.DRAWING_EDGE -> AeBrowserCategory.DRAWING_EDGE
    AeEngineCategory.TEXT -> AeBrowserCategory.TEXT
    AeEngineCategory.TIME -> AeBrowserCategory.TIME
    AeEngineCategory.OPACITY -> AeBrowserCategory.OPACITY
    AeEngineCategory.TRANSITION -> AeBrowserCategory.TRANSITION
    AeEngineCategory.OTHER -> AeBrowserCategory.OTHER
}

/**
 * UI bridge backed exclusively by the standalone [AeEffectEngineCatalog].
 * Completely decoupled from Video Star and Node Video catalogs.
 */
internal object AeEffectsBrowserCatalog {

    fun metaFor(item: EffectCatalogItem): AeBrowserEffectMeta {
        val def = AeEffectEngineCatalog.findById(item.id)
        if (def != null) {
            return AeBrowserEffectMeta(
                browserCategory = def.category.toBrowserCategory(),
                origin = if (def.source == AeEffectSource.AFTER_EFFECTS) {
                    AeBrowserOrigin.AFTER_EFFECTS
                } else {
                    AeBrowserOrigin.ALIGHT_MOTION
                },
                description = def.description
            )
        }
        return AeBrowserEffectMeta(
            browserCategory = AeBrowserCategory.OTHER,
            origin = AeBrowserOrigin.ALIGHT_MOTION,
            description = "Real-time AE GPU shader processor."
        )
    }

    fun visibleCategories(allAeEffects: List<EffectCatalogItem>): List<AeBrowserCategory> {
        val populated = allAeEffects.map { metaFor(it).browserCategory }.toSet()
        return listOf(AeBrowserCategory.MY_EFFECTS) +
            AeBrowserCategory.entries.filter { it != AeBrowserCategory.MY_EFFECTS && it in populated }
    }
}
