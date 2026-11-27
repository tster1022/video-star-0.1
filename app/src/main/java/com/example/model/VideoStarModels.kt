package com.example.model

import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.example.R
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.math.abs

object ColorAsLongSerializer : KSerializer<Color> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ComposeColor", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Color) {
        encoder.encodeLong(value.value.toLong())
    }

    override fun deserialize(decoder: Decoder): Color {
        return Color(decoder.decodeLong().toULong())
    }
}

@Serializable
enum class AspectRatioMode(val label: String, val ratio: Float) {
    LANDSCAPE("16:9", 16f / 9f),
    LANDSCAPE_4_3("4:3", 4f / 3f),
    SQUARE("1:1", 1f),
    PORTRAIT_3_4("3:4", 3f / 4f),
    PORTRAIT_4_5("4:5", 4f / 5f),
    PORTRAIT("9:16", 9f / 16f)
}

@Serializable
enum class CompressionQuality(
    val arabicLabel: String,
    val subtitle: String,
    val bitrateFactor: Float
) {
    HIGH("جودة عالية", "معدل بت مرتفع • تفاصيل فائقة", 1.35f),
    BALANCED("متوسطة (متوازنة)", "توازن مثالي بين الجودة والحجم", 1.0f),
    COMPACT("حجم مضغوط", "ملف أصغر للمشاركة السريعة", 0.65f)
}

@Serializable
enum class MaskMode(val label: String) {
    NONE("No Mask"),
    SHAPE("Shape Mask"),
    LINEAR("Linear Mask")
}

@Serializable
enum class MaskShape(val arabicLabel: String, val englishLabel: String = arabicLabel) {
    NONE("بدون قناع", "None"),
    CIRCLE("دائرة", "Circle"),
    ROUNDED_SQUARE("مربع دائري", "Rounded Square"),
    HEART("قلب", "Heart"),
    STAR("نجمة خماسية", "Star"),
    TRIANGLE("مثلث", "Triangle"),
    DIAMOND("معين", "Diamond")
}

@Serializable
data class TextLayerSpec(
    val text: String = "",
    val textColorArgb: Long = 0xFFFFFFFFL,
    val strokeColorArgb: Long = 0xFF000000L,
    val strokeWidthPx: Float = 0f,
    val shadowRadius: Float = 6f,
    val glowIntensity: Float = 0f,
    val fontSizeSp: Float = 28f,
    val fontStyleId: String = "sf_bold" // "sf_bold", "sf_display", "arabic_naskh", "mono_arcade"
)

@Serializable
enum class LayerEffectType(val arabicLabel: String) {
    NONE("بدون تأثير"),
    NEON_GLOW("توهج نيون"),
    RGB_SPLIT("انقسام RGB"),
    DREAM_BLUR("ضبابية حالمة"),
    VINTAGE_PULSE("نبض كلاسيكي")
}

enum class WorkspaceTab(val label: String) {
    EDIT("تحرير"),
    SELECT("اختر"),
    NEW("جديد")
}

@Serializable
enum class StudioLayoutMode(
    val displayName: String,
    val statusBadge: String? = null
) {
    NOVA("Nova", null),
    CAPCUT("CapCut", null),
    BLURRR("Blurrr", "Unavailable for now"),
    NODE_VIDEO("Node Video", null),
    AFTER_EFFECTS("After Effects", "Needs a large screen")
}

enum class MultiLayerTab(val label: String) {
    LAYER_EFFECTS("تأثيرات الطبقة"),
    LAYOUT("التخطيط"),
    COLOR("اللون"),
    MASK("القناع")
}

enum class ClassicLayerTab(val label: String) {
    LAYER_EFFECTS("تأثيرات الطبقة"),
    LAYOUT("التخطيط"),
    COLOR("اللون"),
    TEXT("النص"),
    MIRROR("انعكاس")
}

enum class ShareTarget(val labelResId: Int, val platformName: String) {
    YOUTUBE(R.string.share_youtube, "YouTube"),
    FACEBOOK(R.string.share_facebook, "Facebook"),
    INSTAGRAM(R.string.share_instagram, "Instagram"),
    EMAIL(R.string.share_email, "Email"),
    CAMERA_ROLL(R.string.share_camera_roll, "Camera Roll")
}

/**
 * 10-option New Action Popover Grid (2 Columns x 5 Rows, matching Stage 2 Arabic layout):
 * Left Column: تسجيل الفيديو, استيراد الفيديو, استيراد الصورة, حركة, السرعة المتجهة
 * Right Column: إضافة مؤثرات, تحول, مزج, متعدد الطبقات, توقيت
 */
enum class NewClipAction(
    val label: String,
    val isGreen: Boolean,
    val arabicTitle: String,
    val arabicDesc: String
) {
    RECORD_VIDEO("تسجيل الفيديو", false, "تسجيل الفيديو", "تسجيل فيديو بالكاميرا مع تزامن الأغنية."),
    IMPORT_VIDEO("استيراد الفيديو", false, "استيراد الفيديو", "اختيار مقطع فيديو من معرض الهاتف وتعديله."),
    IMPORT_PHOTO("استيراد الصورة", false, "استيراد الصورة", "اختيار صورة من معرض الهاتف وضبط مدتها وتأطيرها."),
    MOTION("حركة", false, "حركة", "تطبيق حركات ديناميكية مستمرة على المشهد."),
    VELOCITY("السرعة المتجهة", false, "السرعة المتجهة", "إنشاء منحنيات تسريع وتباطؤ احترافية."),
    RE_EFFECT("إضافة مؤثرات", false, "إضافة مؤثرات", "تطبيق مؤثرات GPU بالوقت الفعلي مع مفاتيح الشدة."),
    TRANSFORM("تحول", false, "تحول", "ضبط التكبير والزاوية والانعكاس والخلفية."),
    MIX("مزج", false, "مزج", "إضافة انتقال احترافي بين المشهد الحالي والتالي."),
    MULTI_LAYER("متعدد الطبقات", false, "متعدد الطبقات", "فتح محرر الطبقات المتعددة 3D والأقنعة والنصوص."),
    TIMING("توقيت", false, "توقيت", "ضبط المدة الدقيقة وتجميد الإطار أو التكرار.")
}

@Serializable
enum class EffectCategory(val label: String, val shortLabel: String) {
    SHAKE_MOTION("Shake & Motion", "Shake"),
    BLUR_FOCUS("Blur & Focus", "Blur"),
    COLOR_GLOW("Color & Glow", "Color"),
    DISTORTION_GLITCH("Distortion & Glitch", "Glitch"),
    MIRROR_KALEIDOSCOPE("Mirror & Kaleidoscope", "Mirror"),
    RETRO_VHS("Retro & VHS", "Retro"),
    AM_MOVE_TRANSFORM("Move/Transform", "Move"),
    AM_DISTORTION_WARP("Distortion/Warp", "Distort"),
    AM_MATTE_MASK_KEY("Matte/Mask/Key", "Matte"),
    AM_OPACITY_VISIBILITY("Opacity/Visibility", "Opacity"),
    AM_PROCEDURAL("Procedural", "Procedural"),
    AM_REPEAT("Repeat", "Repeat"),
    AM_THREE_D("3D", "3D"),
    AM_TEXT("Text", "Text"),
    AM_OTHER("Other", "Other");

    val isAlightMotion: Boolean
        get() = name.startsWith("AM_")
}

enum class EffectStudioTab(val label: String, val arabicLabel: String = label) {
    ALL("All", "All"),
    MY_EFFECTS("My Effects", "مؤثراتي"),
    VIDEO_STAR("Video Star", "Video Star"),
    MOVE_TRANSFORM("Move/Transform", "Move/Transform"),
    DISTORTION_WARP("Distortion/Warp", "Distortion/Warp"),
    MATTE_MASK_KEY("Matte/Mask/Key", "Matte/Mask/Key"),
    OPACITY_VISIBILITY("Opacity/Visibility", "Opacity/Visibility"),
    PROCEDURAL("Procedural", "Procedural"),
    REPEAT("Repeat", "Repeat"),
    THREE_D("3D", "3D"),
    TEXT("Text", "Text"),
    OTHER("Other", "Other");

    companion object {
        fun tabsForScope(scope: EffectCatalogScope): List<EffectStudioTab> = when (scope) {
            EffectCatalogScope.ALL -> entries
            EffectCatalogScope.VIDEO_STAR_ONLY -> listOf(ALL, MY_EFFECTS, VIDEO_STAR)
            EffectCatalogScope.AFTER_EFFECTS_ONLY -> listOf(
                ALL,
                MY_EFFECTS,
                MOVE_TRANSFORM,
                DISTORTION_WARP,
                MATTE_MASK_KEY,
                OPACITY_VISIBILITY,
                PROCEDURAL,
                REPEAT,
                THREE_D,
                TEXT,
                OTHER
            )
        }
    }
}

@Serializable
enum class EffectCatalogScope {
    ALL,
    VIDEO_STAR_ONLY,
    AFTER_EFFECTS_ONLY
}

@Serializable
enum class EffectBlendMode(
    val id: String,
    val arabicLabel: String,
    val glIndex: Int
) {
    NORMAL("normal", "Normal", 0),
    ADD("add", "Add", 1),
    MULTIPLY("multiply", "Multiply", 2),
    SCREEN("screen", "Screen", 3),
    OVERLAY("overlay", "Overlay", 4)
}

fun EffectCatalogItem.isCustomPackEffect(): Boolean =
    id.startsWith("custom_glsl_") || id.startsWith("custom_xml_")

fun EffectCatalogItem.toStudioTab(): EffectStudioTab =
    if (isCustomPackEffect()) EffectStudioTab.MY_EFFECTS
    else when (category) {
        EffectCategory.SHAKE_MOTION,
        EffectCategory.BLUR_FOCUS,
        EffectCategory.COLOR_GLOW,
        EffectCategory.DISTORTION_GLITCH,
        EffectCategory.MIRROR_KALEIDOSCOPE,
        EffectCategory.RETRO_VHS -> EffectStudioTab.VIDEO_STAR
        EffectCategory.AM_MOVE_TRANSFORM -> EffectStudioTab.MOVE_TRANSFORM
        EffectCategory.AM_DISTORTION_WARP -> EffectStudioTab.DISTORTION_WARP
        EffectCategory.AM_MATTE_MASK_KEY -> EffectStudioTab.MATTE_MASK_KEY
        EffectCategory.AM_OPACITY_VISIBILITY -> EffectStudioTab.OPACITY_VISIBILITY
        EffectCategory.AM_PROCEDURAL -> EffectStudioTab.PROCEDURAL
        EffectCategory.AM_REPEAT -> EffectStudioTab.REPEAT
        EffectCategory.AM_THREE_D -> EffectStudioTab.THREE_D
        EffectCategory.AM_TEXT -> EffectStudioTab.TEXT
        EffectCategory.AM_OTHER -> EffectStudioTab.OTHER
    }

fun EffectCatalogItem.arabicDisplayName(): String =
    if (category.isAlightMotion || isCustomPackEffect()) name else when (id) {
        "motion_tile" -> "تكرار الحركة"
        "hard_shake" -> "اهتزاز قوي"
        "smooth_shake" -> "اهتزاز ناعم"
        "zoom_pulse" -> "نبض التكبير"
        "spin_vortex" -> "دوران دوامة"
        "tile_grid" -> "شبكة التكرار"
        "radial_zoom_blur" -> "ضبابية شعاعية"
        "dir_motion_blur" -> "ضبابية اتجاهية"
        "gaussian_blur" -> "ضبابية غاوسية"
        "lens_bokeh" -> "بوكيه العدسة"
        "sharpen_clarity" -> "وضوح وحدّة"
        "rgb_split" -> "انقسام RGB"
        "exposure_flash" -> "ومضة سطوع"
        "glow_bloom" -> "توهج نيون"
        "hue_shift" -> "تدرج ألوان"
        "color_adjust" -> "سطوع وتشبّع"
        "duotone" -> "ثنائي اللون"
        "invert" -> "عكس الألوان"
        "vignette" -> "تظليل الحواف"
        "bw" -> "أبيض وأسود"
        "ripple" -> "تموّج مائي"
        "twirl" -> "دوامة ملتوية"
        "bulge_pinch" -> "انتفاخ وضغط"
        "fisheye" -> "عين السمكة"
        "glitch" -> "خلل رقمي"
        "pixelate" -> "بكسلّة"
        "turbulent_displace" -> "موجة مضطربة"
        "mirror_lr" -> "مرآة يمين/يسار"
        "mirror_tb" -> "مرآة أعلى/أسفل"
        "quad_mirror" -> "مرآة رباعية"
        "kaleidoscope" -> "مشكال"
        "vhs_tape" -> "شريط VHS"
        "scanlines" -> "خطوط التلفاز"
        "film_grain" -> "تحبب الفيلم"
        "halftone" -> "نقاط كوميك"
        else -> name
    }

fun EffectParamSpec.arabicDisplayLabel(): String = label

@Serializable
enum class EffectParamType {
    FLOAT,
    COLOR,
    POINT_2D_X,
    POINT_2D_Y,
    ENUM_MODE,
    BOOLEAN,
    TEXT_STRING
}

@Serializable
data class EffectParamSpec(
    val key: String,
    val label: String,
    val defaultValue: Float = 0.5f,
    val minValue: Float = 0f,
    val maxValue: Float = 1f,
    val unitSuffix: String = "",
    val paramType: EffectParamType = EffectParamType.FLOAT,
    val enumOptions: List<String> = emptyList(),
    val defaultColorArgb: Long = 0xFF00FF44L,
    val pairedKey: String? = null,
    val defaultText: String = ""
)

@Serializable
data class EffectCatalogItem(
    val id: String,
    val name: String,
    val category: EffectCategory,
    val defaultIntensity: Float = 0.7f,
    val params: List<EffectParamSpec> = emptyList()
) {
    fun defaultParamMap(): Map<String, Float> =
        params.filter { it.paramType != EffectParamType.COLOR && it.paramType != EffectParamType.TEXT_STRING }
            .associate { it.key to it.defaultValue }

    fun defaultColorMap(): Map<String, Long> =
        params.filter { it.paramType == EffectParamType.COLOR }
            .associate { it.key to it.defaultColorArgb }

    fun defaultTextMap(): Map<String, String> =
        params.filter { it.paramType == EffectParamType.TEXT_STRING }
            .associate { it.key to it.defaultText }
}

@Serializable
data class EffectKeyframe(
    val id: String,
    val timeProgress: Float,
    val intensity: Float,
    val easing: KeyframeEasing = KeyframeEasing.LINEAR
)

@Serializable
data class ShaderEffectPass(
    val id: String,
    val effectId: String,
    val effectName: String,
    val category: EffectCategory,
    val intensity: Float = 0.7f,
    val params: Map<String, Float> = emptyMap(),
    val colorParams: Map<String, Long> = emptyMap(),
    val textParams: Map<String, String> = emptyMap(),
    val keyframes: List<EffectKeyframe> = emptyList(),
    val paramKeyframes: Map<String, List<ParameterKeyframe>> = emptyMap(),
    val isEnabled: Boolean = true,
    val blendMode: EffectBlendMode = EffectBlendMode.NORMAL,
    val passOpacity: Float = 1.0f
) {
    fun evaluateIntensityAt(progress: Float): Float {
        val list = keyframesForProperty(PARAM_KEY_INTENSITY)
        return interpolateParameterKeyframes(
            keyframes = list,
            progress = progress,
            staticValue = intensity,
            minValue = 0f,
            maxValue = 1f
        )
    }

    fun evaluateOpacityAt(progress: Float): Float {
        val list = paramKeyframes[PARAM_KEY_OPACITY].orEmpty()
        return interpolateParameterKeyframes(
            keyframes = list,
            progress = progress,
            staticValue = passOpacity,
            minValue = 0f,
            maxValue = 1f
        )
    }

    fun evaluateParamAt(
        key: String,
        progress: Float,
        fallback: Float = 0.5f,
        minValue: Float = -Float.MAX_VALUE,
        maxValue: Float = Float.MAX_VALUE
    ): Float {
        val list = paramKeyframes[key].orEmpty()
        return interpolateParameterKeyframes(
            keyframes = list,
            progress = progress,
            staticValue = param(key, fallback),
            minValue = minValue,
            maxValue = maxValue
        )
    }

    fun evaluateColorAt(
        key: String,
        progress: Float,
        fallbackArgb: Long = 0xFF00FF44L
    ): Long {
        val list = paramKeyframes[key].orEmpty()
        return interpolateColorKeyframes(
            keyframes = list,
            progress = progress,
            staticArgb = colorParams[key] ?: fallbackArgb
        )
    }

    fun textParam(key: String, fallback: String = ""): String = textParams[key] ?: fallback

    fun keyframesForProperty(key: String): List<ParameterKeyframe> {
        return if (key == PARAM_KEY_INTENSITY) {
            if (keyframes.isNotEmpty()) {
                keyframes.map { kf ->
                    ParameterKeyframe(
                        id = kf.id,
                        time = kf.timeProgress,
                        value = kf.intensity,
                        easing = kf.easing
                    )
                }.sortedBy { it.time }
            } else {
                paramKeyframes[PARAM_KEY_INTENSITY].orEmpty().sortedBy { it.time }
            }
        } else {
            paramKeyframes[key].orEmpty().sortedBy { it.time }
        }
    }

    fun hasKeyframesForProperty(key: String): Boolean = keyframesForProperty(key).isNotEmpty()

    fun keyframeNear(key: String, progress: Float, tolerance: Float = 0.03f): ParameterKeyframe? {
        val p = progress.coerceIn(0f, 1f)
        return keyframesForProperty(key).minByOrNull { abs(it.time - p) }?.takeIf { abs(it.time - p) <= tolerance }
    }

    fun hasKeyframeNear(key: String, progress: Float, tolerance: Float = 0.03f): Boolean =
        keyframeNear(key, progress, tolerance) != null

    fun allKeyframeTimes(): List<Float> {
        val all = buildList {
            keyframes.forEach { add(it.timeProgress.coerceIn(0f, 1f)) }
            paramKeyframes.values.flatten().forEach { add(it.time.coerceIn(0f, 1f)) }
        }.sorted()
        val distinct = mutableListOf<Float>()
        for (t in all) {
            if (distinct.none { abs(it - t) <= 0.005f }) {
                distinct.add(t)
            }
        }
        return distinct
    }

    fun param(key: String, fallback: Float = 0.5f): Float = params[key] ?: fallback
}

object EffectCatalog {
    val noneItem = EffectCatalogItem("none", "None", EffectCategory.COLOR_GLOW, 0f)

    /**
     * 35 authentic Video Star GLSL shader effects across 6 categories.
     */
    val shaderEffects: List<EffectCatalogItem> = listOf(
        // 1. Shake & Motion (6)
        EffectCatalogItem(
            id = "motion_tile",
            name = "Motion Tile",
            category = EffectCategory.SHAKE_MOTION,
            defaultIntensity = 0.85f,
            params = listOf(
                EffectParamSpec("mirror_edges", "Mirror Edges", 1f, 0f, 1f),
                EffectParamSpec("offset_x", "Offset X", 0f, -1f, 1f),
                EffectParamSpec("offset_y", "Offset Y", 0f, -1f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "hard_shake",
            name = "Hard Shake",
            category = EffectCategory.SHAKE_MOTION,
            defaultIntensity = 0.72f,
            params = listOf(
                EffectParamSpec("amplitude", "Amplitude", 0.7f, 0f, 1f),
                EffectParamSpec("frequency", "Frequency", 0.65f, 0.1f, 1f),
                EffectParamSpec("decay", "Decay", 0.25f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "smooth_shake",
            name = "Smooth Shake",
            category = EffectCategory.SHAKE_MOTION,
            defaultIntensity = 0.68f,
            params = listOf(
                EffectParamSpec("amplitude", "Amplitude", 0.55f, 0f, 1f),
                EffectParamSpec("frequency", "Frequency", 0.35f, 0.05f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "zoom_pulse",
            name = "Zoom Pulse",
            category = EffectCategory.SHAKE_MOTION,
            defaultIntensity = 0.7f,
            params = listOf(
                EffectParamSpec("amplitude", "Pulse Depth", 0.65f, 0f, 1f),
                EffectParamSpec("frequency", "Beat Speed", 0.5f, 0.1f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "spin_vortex",
            name = "Spin / Vortex",
            category = EffectCategory.SHAKE_MOTION,
            defaultIntensity = 0.65f,
            params = listOf(
                EffectParamSpec("angle", "Spin Angle", 0.5f, -1f, 1f),
                EffectParamSpec("frequency", "Spin Rate", 0.45f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "tile_grid",
            name = "Tile Grid",
            category = EffectCategory.SHAKE_MOTION,
            defaultIntensity = 1.0f,
            params = listOf(
                EffectParamSpec("grid_size", "Grid (2x/3x/4x)", 2f, 2f, 4f),
                EffectParamSpec("offset_x", "Pan Phase", 0f, -1f, 1f)
            )
        ),

        // 2. Blur & Focus (5)
        EffectCatalogItem(
            id = "radial_zoom_blur",
            name = "Radial Zoom Blur",
            category = EffectCategory.BLUR_FOCUS,
            defaultIntensity = 0.65f,
            params = listOf(
                EffectParamSpec("amount", "Blur Power", 0.65f, 0f, 1f),
                EffectParamSpec("center_x", "Focus Center X", 0.5f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "dir_motion_blur",
            name = "Directional Blur",
            category = EffectCategory.BLUR_FOCUS,
            defaultIntensity = 0.68f,
            params = listOf(
                EffectParamSpec("angle", "Angle", 0.25f, 0f, 1f, "°"),
                EffectParamSpec("amount", "Streak", 0.65f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "gaussian_blur",
            name = "Gaussian Blur",
            category = EffectCategory.BLUR_FOCUS,
            defaultIntensity = 0.6f,
            params = listOf(
                EffectParamSpec("radius", "Blur Radius", 0.6f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "lens_bokeh",
            name = "Lens Bokeh",
            category = EffectCategory.BLUR_FOCUS,
            defaultIntensity = 0.65f,
            params = listOf(
                EffectParamSpec("radius", "Aperture", 0.6f, 0f, 1f),
                EffectParamSpec("highlight", "Highlight Boost", 0.5f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "sharpen_clarity",
            name = "Sharpen / Clarity",
            category = EffectCategory.BLUR_FOCUS,
            defaultIntensity = 0.7f,
            params = listOf(
                EffectParamSpec("amount", "Crispness", 0.7f, 0f, 1f)
            )
        ),

        // 3. Color & Glow (9)
        EffectCatalogItem(
            id = "rgb_split",
            name = "RGB Split",
            category = EffectCategory.COLOR_GLOW,
            defaultIntensity = 0.68f,
            params = listOf(
                EffectParamSpec("distance", "Split Distance", 0.55f, 0f, 1f),
                EffectParamSpec("angle", "Angle", 0f, 0f, 1f, "°")
            )
        ),
        EffectCatalogItem(
            id = "exposure_flash",
            name = "Exposure Flash",
            category = EffectCategory.COLOR_GLOW,
            defaultIntensity = 0.72f,
            params = listOf(
                EffectParamSpec("exposure", "Exposure", 0.7f, 0f, 1f),
                EffectParamSpec("decay", "Flash Decay", 0.45f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "glow_bloom",
            name = "Neon Glow",
            category = EffectCategory.COLOR_GLOW,
            defaultIntensity = 0.74f,
            params = listOf(
                EffectParamSpec("radius", "Bloom Spread", 0.65f, 0f, 1f),
                EffectParamSpec("hue", "Glow Hue", 0.9f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "hue_shift",
            name = "Hue Rainbow",
            category = EffectCategory.COLOR_GLOW,
            defaultIntensity = 0.6f,
            params = listOf(
                EffectParamSpec("hue", "Hue Angle", 0.5f, 0f, 1f, "°"),
                EffectParamSpec("speed", "Cycle Speed", 0.35f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "color_adjust",
            name = "Bright / Sat",
            category = EffectCategory.COLOR_GLOW,
            defaultIntensity = 0.75f,
            params = listOf(
                EffectParamSpec("brightness", "Brightness", 0.55f, 0f, 1f),
                EffectParamSpec("contrast", "Contrast", 0.65f, 0f, 1f),
                EffectParamSpec("saturation", "Saturation", 0.7f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "duotone",
            name = "Duotone",
            category = EffectCategory.COLOR_GLOW,
            defaultIntensity = 0.75f,
            params = listOf(
                EffectParamSpec("hue", "Palette Tint", 0.85f, 0f, 1f),
                EffectParamSpec("mix", "Balance", 0.75f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "invert",
            name = "Invert / Negative",
            category = EffectCategory.COLOR_GLOW,
            defaultIntensity = 1.0f,
            params = listOf(
                EffectParamSpec("mix", "Invert Mix", 1f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "vignette",
            name = "Vignette",
            category = EffectCategory.COLOR_GLOW,
            defaultIntensity = 0.68f,
            params = listOf(
                EffectParamSpec("size", "Inner Size", 0.5f, 0.1f, 0.9f),
                EffectParamSpec("softness", "Feather", 0.6f, 0.1f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "bw",
            name = "Noir B&W",
            category = EffectCategory.COLOR_GLOW,
            defaultIntensity = 1.0f,
            params = listOf(
                EffectParamSpec("contrast", "Noir Contrast", 0.65f, 0f, 1f)
            )
        ),

        // 4. Distortion & Glitch (7)
        EffectCatalogItem(
            id = "ripple",
            name = "Water Ripple",
            category = EffectCategory.DISTORTION_GLITCH,
            defaultIntensity = 0.65f,
            params = listOf(
                EffectParamSpec("speed", "Wave Speed", 0.55f, 0f, 1f),
                EffectParamSpec("height", "Wave Height", 0.6f, 0f, 1f),
                EffectParamSpec("frequency", "Ripples", 0.5f, 0.1f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "twirl",
            name = "Twirl / Swirl",
            category = EffectCategory.DISTORTION_GLITCH,
            defaultIntensity = 0.68f,
            params = listOf(
                EffectParamSpec("angle", "Swirl Twist", 0.65f, -1f, 1f),
                EffectParamSpec("radius", "Radius", 0.7f, 0.1f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "bulge_pinch",
            name = "Bulge / Pinch",
            category = EffectCategory.DISTORTION_GLITCH,
            defaultIntensity = 0.65f,
            params = listOf(
                EffectParamSpec("amount", "Bulge <-> Pinch", 0.7f, -1f, 1f),
                EffectParamSpec("radius", "Radius", 0.65f, 0.2f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "fisheye",
            name = "Fisheye",
            category = EffectCategory.DISTORTION_GLITCH,
            defaultIntensity = 0.7f,
            params = listOf(
                EffectParamSpec("curvature", "Lens Curve", 0.7f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "glitch",
            name = "Digital Glitch",
            category = EffectCategory.DISTORTION_GLITCH,
            defaultIntensity = 0.72f,
            params = listOf(
                EffectParamSpec("amount", "Tear Size", 0.7f, 0f, 1f),
                EffectParamSpec("frequency", "Slice Rate", 0.6f, 0.1f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "pixelate",
            name = "Pixelate",
            category = EffectCategory.DISTORTION_GLITCH,
            defaultIntensity = 0.6f,
            params = listOf(
                EffectParamSpec("block_size", "Block Size", 0.55f, 0.05f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "turbulent_displace",
            name = "Turbulent Wave",
            category = EffectCategory.DISTORTION_GLITCH,
            defaultIntensity = 0.65f,
            params = listOf(
                EffectParamSpec("amount", "Displace", 0.6f, 0f, 1f),
                EffectParamSpec("frequency", "Turbulence", 0.55f, 0.1f, 1f)
            )
        ),

        // 5. Mirror & Kaleidoscope (4)
        EffectCatalogItem(
            id = "mirror_lr",
            name = "Mirror L/R",
            category = EffectCategory.MIRROR_KALEIDOSCOPE,
            defaultIntensity = 1.0f,
            params = listOf(
                EffectParamSpec("offset", "Split Axis", 0.5f, 0.1f, 0.9f)
            )
        ),
        EffectCatalogItem(
            id = "mirror_tb",
            name = "Mirror T/B",
            category = EffectCategory.MIRROR_KALEIDOSCOPE,
            defaultIntensity = 1.0f,
            params = listOf(
                EffectParamSpec("offset", "Horizon Axis", 0.5f, 0.1f, 0.9f)
            )
        ),
        EffectCatalogItem(
            id = "quad_mirror",
            name = "Quad Mirror",
            category = EffectCategory.MIRROR_KALEIDOSCOPE,
            defaultIntensity = 1.0f,
            params = listOf(
                EffectParamSpec("offset", "Center Offset", 0.5f, 0.1f, 0.9f)
            )
        ),
        EffectCatalogItem(
            id = "kaleidoscope",
            name = "Kaleidoscope",
            category = EffectCategory.MIRROR_KALEIDOSCOPE,
            defaultIntensity = 0.85f,
            params = listOf(
                EffectParamSpec("sides", "Sides (4..12)", 6f, 4f, 12f),
                EffectParamSpec("rotation", "Angle", 0.2f, 0f, 1f)
            )
        ),

        // 6. Retro & VHS (4)
        EffectCatalogItem(
            id = "vhs_tape",
            name = "VHS Tape",
            category = EffectCategory.RETRO_VHS,
            defaultIntensity = 0.72f,
            params = listOf(
                EffectParamSpec("tracking", "Tracking", 0.6f, 0f, 1f),
                EffectParamSpec("bleed", "Chroma Bleed", 0.65f, 0f, 1f),
                EffectParamSpec("noise", "Tape Static", 0.5f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "scanlines",
            name = "CRT Scanlines",
            category = EffectCategory.RETRO_VHS,
            defaultIntensity = 0.7f,
            params = listOf(
                EffectParamSpec("density", "Line Density", 0.6f, 0.1f, 1f),
                EffectParamSpec("curvature", "CRT Curve", 0.4f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "film_grain",
            name = "Film Grain",
            category = EffectCategory.RETRO_VHS,
            defaultIntensity = 0.65f,
            params = listOf(
                EffectParamSpec("amount", "Grain Amount", 0.65f, 0f, 1f),
                EffectParamSpec("size", "Grain Size", 0.45f, 0.1f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "halftone",
            name = "Comic Halftone",
            category = EffectCategory.RETRO_VHS,
            defaultIntensity = 0.75f,
            params = listOf(
                EffectParamSpec("dot_size", "Dot Size", 0.5f, 0.1f, 1f),
                EffectParamSpec("contrast", "Ink Contrast", 0.65f, 0f, 1f)
            )
        )
    )

    val customPackEffects: List<EffectCatalogItem>
        get() = com.example.engine.packs.CustomPackRepository.dynamicCatalogItems()

    val alightMotionEffects: List<EffectCatalogItem>
        get() = com.example.engine.effects.AlightMotionEffectRegistry.effects

    val combinedShaderEffects: List<EffectCatalogItem>
        get() = shaderEffects + alightMotionEffects + customPackEffects

    val allEffects: List<EffectCatalogItem>
        get() = listOf(noneItem) + combinedShaderEffects

    private val legacyAliases: Map<String, String> = mapOf(
        "tile_2x2" to "tile_grid",
        "tile_3x3" to "tile_grid",
        "spin_blur" to "spin_vortex",
        "bg_blur" to "gaussian_blur",
        "glass_distort" to "turbulent_displace",
        "mirror_hv" to "mirror_lr",
        "bg_neon_aura" to "glow_bloom",
        "bg_soft_gradient" to "duotone",
        "light_leaks" to "exposure_flash"
    )

    fun byCategory(category: EffectCategory): List<EffectCatalogItem> =
        listOf(noneItem) + combinedShaderEffects.filter { it.category == category }

    fun findById(id: String): EffectCatalogItem? {
        com.example.engine.packs.CustomPackRepository.findCatalogItem(id)?.let { return it }
        allEffects.firstOrNull { it.id == id }?.let { return it }
        val mapped = legacyAliases[id] ?: return null
        return allEffects.firstOrNull { it.id == mapped }
    }

    fun resolveCanonicalId(id: String): String = legacyAliases[id] ?: id
}

@Serializable
enum class MotionPreset(val label: String) {
    NONE("None"),
    SHAKE_H("Shake X"),
    SHAKE_V("Shake Y"),
    PENDULUM("Pendulum"),
    ZOOM_BOUNCE("Zoom Bounce"),
    PULSE("Heartbeat"),
    DRIFT("Floating")
}

@Serializable
enum class TransitionCategory(val label: String) {
    MOTION_BLUR("Motion Blur"),
    FLIP_3D("3D Flip"),
    LIGHT_LEAK("Flash & Glow"),
    COLOR_MORPH("Wipe & Slide")
}

@Serializable
data class TransitionCatalogItem(
    val id: String,
    val name: String,
    val category: TransitionCategory
)

object TransitionCatalog {
    val allTransitions: List<TransitionCatalogItem> = listOf(
        TransitionCatalogItem("zoom_blur", "Zoom Blur", TransitionCategory.MOTION_BLUR),
        TransitionCatalogItem("zoom_spin", "Zoom Spin", TransitionCategory.MOTION_BLUR),
        TransitionCatalogItem("slide_blur", "Slide Blur", TransitionCategory.MOTION_BLUR),
        TransitionCatalogItem("cube_left", "Cube Left", TransitionCategory.FLIP_3D),
        TransitionCatalogItem("cube_right", "Cube Right", TransitionCategory.FLIP_3D),
        TransitionCatalogItem("cube_rotate", "Cube Rotate", TransitionCategory.FLIP_3D),
        TransitionCatalogItem("back_flip", "Back Flip", TransitionCategory.FLIP_3D),
        TransitionCatalogItem("diagonal_flip", "Diagonal Flip", TransitionCategory.FLIP_3D),
        TransitionCatalogItem("white_flash", "White Flash", TransitionCategory.LIGHT_LEAK),
        TransitionCatalogItem("warm_leak", "Warm Leak", TransitionCategory.LIGHT_LEAK),
        TransitionCatalogItem("luminate", "Luminate", TransitionCategory.LIGHT_LEAK),
        TransitionCatalogItem("slide_left", "Slide Left", TransitionCategory.COLOR_MORPH),
        TransitionCatalogItem("radial_wipe", "Radial Wipe", TransitionCategory.COLOR_MORPH),
        TransitionCatalogItem("wipe", "Linear Wipe", TransitionCategory.COLOR_MORPH),
        TransitionCatalogItem("crossfade", "Crossfade", TransitionCategory.COLOR_MORPH),
        TransitionCatalogItem("color_blend", "Color Blend", TransitionCategory.COLOR_MORPH),
        TransitionCatalogItem("ripple_dissolve", "Ripple Dissolve", TransitionCategory.COLOR_MORPH)
    )

    fun byCategory(category: TransitionCategory): List<TransitionCatalogItem> =
        allTransitions.filter { it.category == category }
}

@Serializable
enum class TimingMode(val label: String) {
    FIXED("Fixed"),
    FLEXIBLE("Flexible")
}

@Serializable
data class SceneTransform(
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val bgColorArgb: Long = 0xFF000000L,
    val mirrorEdges: Boolean = false,
    val isFitMode: Boolean = false,
    val endScale: Float = 1f,
    val endRotation: Float = 0f,
    val endOffsetX: Float = 0f,
    val endOffsetY: Float = 0f,
    val isAnimated: Boolean = false,
    val easing: String = "Smooth"
) {
    fun evaluatedAt(progress: Float): SceneTransform {
        if (!isAnimated) return this
        val p = progress.coerceIn(0f, 1f)
        val t = when (easing) {
            "Ease In" -> BezierCurveSpec(0.42f, 0f, 1f, 1f).evaluate(p)
            "Ease Out" -> BezierCurveSpec(0f, 0f, 0.58f, 1f).evaluate(p)
            "Bounce" -> BezierCurveSpec(0.34f, 1.38f, 0.64f, 1f).evaluate(p)
            "Linear" -> p
            else -> BezierCurveSpec(0.25f, 0.1f, 0.25f, 1f).evaluate(p)
        }
        return copy(
            scale = scale + (endScale - scale) * t,
            rotation = rotation + (endRotation - rotation) * t,
            offsetX = offsetX + (endOffsetX - offsetX) * t,
            offsetY = offsetY + (endOffsetY - offsetY) * t
        )
    }
}

@Serializable
data class SceneMotion(
    val preset: MotionPreset = MotionPreset.NONE,
    val speed: Float = 1.0f,
    val intensity: Float = 0.5f
)

@Serializable
data class SceneEffect(
    val category: EffectCategory = EffectCategory.COLOR_GLOW,
    val effectId: String = "none",
    val effectName: String = "None",
    val intensity: Float = 0f
)

@Serializable
data class SceneTiming(
    val mode: TimingMode = TimingMode.FIXED,
    val durationMs: Long = 3_000L,
    val freezeFrameEnabled: Boolean = false,
    val freezeAtProgress: Float = 0.5f,
    val freezeDurationMs: Long = 1_000L,
    val repeatCount: Int = 1
)

@Serializable
data class SceneTransition(
    val category: TransitionCategory = TransitionCategory.MOTION_BLUR,
    val transitionId: String = "zoom_blur",
    val transitionName: String = "Zoom Blur",
    val durationMs: Long = 600L,
    val easing: String = "Smooth"
)

@Serializable
data class ProjectAudioConfig(
    val songTitle: String = "إيقاع إلكتروني 128 BPM",
    val songUriString: String? = null,
    val clipVolume: Float = 1.0f,
    val songVolume: Float = 0.85f
)

@Serializable
data class ClipColorGrading(
    val brightness: Float = 0f,    // -0.5f..0.5f
    val contrast: Float = 1f,      // 0.5f..1.8f
    val saturation: Float = 1f,    // 0f..2f
    val hue: Float = 0f            // -180f..180f
) {
    val isNeutral: Boolean
        get() = abs(brightness) < 0.005f &&
            abs(contrast - 1f) < 0.005f &&
            abs(saturation - 1f) < 0.005f &&
            abs(hue) < 0.5f

    fun toColorMatrixArray(): FloatArray {
        // 1. Saturation
        val lumR = 0.213f
        val lumG = 0.715f
        val lumB = 0.072f
        val sr = (1f - saturation) * lumR
        val sg = (1f - saturation) * lumG
        val sb = (1f - saturation) * lumB
        val satM = floatArrayOf(
            sr + saturation, sg, sb, 0f, 0f,
            sr, sg + saturation, sb, 0f, 0f,
            sr, sg, sb + saturation, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )

        // 2. Hue rotation around RGB luma diagonal
        val rad = Math.toRadians(hue.toDouble())
        val cosV = kotlin.math.cos(rad).toFloat()
        val sinV = kotlin.math.sin(rad).toFloat()
        val hueM = floatArrayOf(
            lumR + cosV * (1f - lumR) + sinV * (-lumR),
            lumG + cosV * (-lumG) + sinV * (-lumG),
            lumB + cosV * (-lumB) + sinV * (1f - lumB), 0f, 0f,
            lumR + cosV * (-lumR) + sinV * (0.143f),
            lumG + cosV * (1f - lumG) + sinV * (0.140f),
            lumB + cosV * (-lumB) + sinV * (-0.283f), 0f, 0f,
            lumR + cosV * (-lumR) + sinV * (-(1f - lumR)),
            lumG + cosV * (-lumG) + sinV * (lumG),
            lumB + cosV * (1f - lumB) + sinV * (lumB), 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )

        // 3. Contrast & Brightness
        val translate = 128f * (1f - contrast) + (brightness * 255f)
        val bcM = floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )

        return multiplyMatrices4x5(bcM, multiplyMatrices4x5(hueM, satM))
    }

    private fun multiplyMatrices4x5(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(20)
        for (row in 0 until 4) {
            val r = row * 5
            for (col in 0 until 4) {
                out[r + col] =
                    a[r] * b[col] +
                    a[r + 1] * b[5 + col] +
                    a[r + 2] * b[10 + col] +
                    a[r + 3] * b[15 + col]
            }
            out[r + 4] =
                a[r] * b[4] +
                a[r + 1] * b[9] +
                a[r + 2] * b[14] +
                a[r + 3] * b[19] +
                a[r + 4]
        }
        return out
    }
}

@Serializable
sealed interface MediaClip {
    val id: String
    val uriString: String
    val durationMs: Long
    val scale: Float
    val offsetX: Float
    val offsetY: Float
    val rotation: Float
    val isFitMode: Boolean
    val backgroundColorArgb: Long
    val isFlippedHorizontal: Boolean
    val isFlippedVertical: Boolean
    val colorGrading: ClipColorGrading

    val effectiveDurationMs: Long
        get() = when (this) {
            is Video -> trimmedDurationMs
            is Photo -> durationMs.coerceAtLeast(500L)
        }

    @Serializable
    data class Video(
        override val id: String,
        override val uriString: String,
        val startTrimMs: Long,
        val endTrimMs: Long,
        override val durationMs: Long,
        val sourceFps: Int = 30,
        override val scale: Float = 1f,
        override val offsetX: Float = 0f,
        override val offsetY: Float = 0f,
        override val rotation: Float = 0f,
        override val isFitMode: Boolean = false,
        override val backgroundColorArgb: Long = 0xFF000000L,
        override val isFlippedHorizontal: Boolean = false,
        override val isFlippedVertical: Boolean = false,
        override val colorGrading: ClipColorGrading = ClipColorGrading()
    ) : MediaClip {
        val trimmedDurationMs: Long
            get() = (endTrimMs - startTrimMs).coerceAtLeast(200L)
    }

    @Serializable
    data class Photo(
        override val id: String,
        override val uriString: String,
        override val durationMs: Long = 3_000L,
        override val scale: Float = 1f,
        override val offsetX: Float = 0f,
        override val offsetY: Float = 0f,
        override val rotation: Float = 0f,
        override val isFitMode: Boolean = false,
        override val backgroundColorArgb: Long = 0xFF000000L,
        override val isFlippedHorizontal: Boolean = false,
        override val isFlippedVertical: Boolean = false,
        override val colorGrading: ClipColorGrading = ClipColorGrading()
    ) : MediaClip
}

@Serializable
data class BezierCurveSpec(
    val p1x: Float = 0.25f,
    val p1y: Float = 0.10f,
    val p2x: Float = 0.25f,
    val p2y: Float = 1.00f
) {
    fun evaluate(x: Float): Float {
        val targetX = x.coerceIn(0f, 1f)
        if (targetX <= 0f) return 0f
        if (targetX >= 1f) return 1f
        if (p1x == p1y && p2x == p2y) return targetX

        var lo = 0f
        var hi = 1f
        var t = targetX
        repeat(16) {
            t = (lo + hi) * 0.5f
            val xVal = cubicAxis(t, p1x, p2x)
            if (xVal < targetX) lo = t else hi = t
        }
        return cubicAxis(t, p1y, p2y)
    }

    fun inverted(): BezierCurveSpec = BezierCurveSpec(
        p1x = (1f - p2x).coerceIn(0f, 1f),
        p1y = (1f - p2y).coerceIn(-0.6f, 1.6f),
        p2x = (1f - p1x).coerceIn(0f, 1f),
        p2y = (1f - p1y).coerceIn(-0.6f, 1.6f)
    )

    private fun cubicAxis(t: Float, c1: Float, c2: Float): Float {
        val inv = 1f - t
        return (3f * inv * inv * t * c1) + (3f * inv * t * t * c2) + (t * t * t)
    }
}

data class GraphPreset(
    val name: String,
    val spec: BezierCurveSpec
)

val defaultGraphPresets: List<GraphPreset> = listOf(
    GraphPreset("Linear", BezierCurveSpec(0f, 0f, 1f, 1f)),
    GraphPreset("Ease In", BezierCurveSpec(0.42f, 0f, 1f, 1f)),
    GraphPreset("Ease Out", BezierCurveSpec(0f, 0f, 0.58f, 1f)),
    GraphPreset("Ease In-Out", BezierCurveSpec(0.42f, 0f, 0.58f, 1f)),
    GraphPreset("Smooth", BezierCurveSpec(0.25f, 0.1f, 0.25f, 1f)),
    GraphPreset("Sharp", BezierCurveSpec(0.85f, 0f, 0.15f, 1f)),
    GraphPreset("Bounce", BezierCurveSpec(0.34f, 1.38f, 0.64f, 1f)),
    GraphPreset("Elastic", BezierCurveSpec(0.68f, -0.35f, 0.265f, 1.35f))
)

@Serializable
data class LayerKeyframe(
    val id: String,
    val timeProgress: Float,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val rotationX: Float = 0f,
    val rotationY: Float = 0f,
    val perspectiveFov: Float = 45f,
    val anchorX: Float = 0.5f,
    val anchorY: Float = 0.5f,
    val effectIntensity: Float = 0f,
    val opacity: Float = 1f,
    val curve: BezierCurveSpec = BezierCurveSpec(0.25f, 0.1f, 0.25f, 1f)
)

@Serializable
data class VelocityPoint(
    val id: String,
    val timeProgress: Float,
    val speedMultiplier: Float
)

fun defaultVelocityPoints(): List<VelocityPoint> = listOf(
    VelocityPoint("vp_start", 0.0f, 1.0f),
    VelocityPoint("vp_mid", 0.5f, 1.0f),
    VelocityPoint("vp_end", 1.0f, 1.0f)
)

fun evaluateVelocityAt(
    progress: Float,
    points: List<VelocityPoint>,
    isSmooth: Boolean
): Float {
    val sorted = points.sortedBy { it.timeProgress }
    if (sorted.isEmpty()) return 1f
    val p = progress.coerceIn(0f, 1f)
    if (sorted.size == 1 || p <= sorted.first().timeProgress) return sorted.first().speedMultiplier
    if (p >= sorted.last().timeProgress) return sorted.last().speedMultiplier
    val nextIdx = sorted.indexOfFirst { it.timeProgress >= p }.coerceAtLeast(1)
    val prev = sorted[nextIdx - 1]
    val next = sorted[nextIdx]
    val span = (next.timeProgress - prev.timeProgress).takeIf { abs(it) > 0.0001f } ?: 1f
    val rawT = ((p - prev.timeProgress) / span).coerceIn(0f, 1f)
    val t = if (isSmooth) rawT * rawT * (3f - 2f * rawT) else rawT
    return (prev.speedMultiplier + (next.speedMultiplier - prev.speedMultiplier) * t).coerceIn(0.1f, 4.0f)
}

fun computeAverageVelocityMultiplier(
    points: List<VelocityPoint>,
    isSmooth: Boolean
): Float {
    if (points.isEmpty()) return 1f
    val samples = 16
    var total = 0f
    for (i in 0..samples) {
        total += evaluateVelocityAt(i / samples.toFloat(), points, isSmooth)
    }
    return (total / (samples + 1)).coerceIn(0.15f, 4.0f)
}

enum class LayerMediaType {
    NONE,
    VIDEO,
    PHOTO,
    COLOR
}

data class LayerSlot(
    val id: Int,
    val mediaType: LayerMediaType = LayerMediaType.NONE,
    val mediaUri: Uri? = null,
    val backgroundColor: Color? = null,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f
)

@Serializable
enum class AeLayerKind(val defaultTitle: String) {
    MEDIA("Imagem"),
    AUDIO("Audio"),
    SHAPE("Shape"),
    ELEMENT("Element"),
    TEXT("Text"),
    DRAW("Draw"),
    ADJUSTMENT("Adjustment"),
    NULL("Null")
}

@Serializable
data class EditorLayer(
    val id: String,
    val number: Int,
    val mediaClip: MediaClip? = null,
    val backgroundColorArgb: Long? = null,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val rotationX: Float = 0f,
    val rotationY: Float = 0f,
    val perspectiveFov: Float = 45f,
    val anchorX: Float = 0.5f,
    val anchorY: Float = 0.5f,
    val is3DEnabled: Boolean = false,
    @Serializable(with = ColorAsLongSerializer::class)
    val tintColor: Color = Color.Transparent,
    val opacity: Float = 1f,
    val maskMode: MaskMode = MaskMode.NONE,
    val maskShape: MaskShape = MaskShape.NONE,
    val maskFeather: Float = 0f,
    val maskInvert: Boolean = false,
    val maskLinearAngle: Float = 0f,
    val effect: LayerEffectType = LayerEffectType.NONE,
    val effectPasses: List<ShaderEffectPass> = emptyList(),
    val layoutPreset: String = "قياسي",
    val textOverlay: String = "",
    val textSpec: TextLayerSpec = TextLayerSpec(),
    val isMirrored: Boolean = false,
    val isFlippedVertical: Boolean = false,
    val layoutEffectName: String = "Layout Effect Not Selected",
    val effectIntensity: Float = 0f,
    val keyframes: List<LayerKeyframe> = emptyList(),
    val samCutout: SamCutoutSpec = SamCutoutSpec(),
    val parentLayerId: String? = null,
    val title: String = "",
    val layerKind: AeLayerKind = AeLayerKind.MEDIA,
    val isVisible: Boolean = true,
    val isLocked: Boolean = false,
    val startTimeMs: Long = 0L,
    val durationMs: Long = 3_000L,
    val zIndex: Int = 0,
    val sourceSceneId: String? = null,
    val speedMultiplier: Float = 1f,
    val blendMode: EffectBlendMode = EffectBlendMode.NORMAL,
    val borderWidthDp: Float = 0f,
    val borderColorArgb: Long = 0xFFFFFFFFL,
    val shadowOpacity: Float = 0f,
    val shadowRadiusDp: Float = 8f,
    val shadowOffsetYDp: Float = 4f,
    val audioVolume: Float = 1f
) {
    val effectiveText: String
        get() = textSpec.text.ifBlank { textOverlay }

    val displayTitle: String
        get() = when {
            title.isNotBlank() -> title
            effectiveText.isNotBlank() -> effectiveText
            mediaClip is MediaClip.Photo -> "Imagem"
            mediaClip is MediaClip.Video -> "Video"
            layerKind != AeLayerKind.MEDIA -> layerKind.defaultTitle
            else -> "Layer $number"
        }

    val effectiveLayerDurationMs: Long
        get() = (mediaClip?.effectiveDurationMs ?: durationMs).coerceAtLeast(300L)

    fun isActiveAt(timeMs: Long): Boolean =
        timeMs >= startTimeMs && timeMs <= (startTimeMs + effectiveLayerDurationMs)

    val layerMediaType: LayerMediaType
        get() = when {
            mediaClip is MediaClip.Video -> LayerMediaType.VIDEO
            mediaClip is MediaClip.Photo -> LayerMediaType.PHOTO
            backgroundColorArgb != null -> LayerMediaType.COLOR
            else -> LayerMediaType.NONE
        }

    fun toLayerSlot(): LayerSlot = LayerSlot(
        id = number,
        mediaType = layerMediaType,
        mediaUri = mediaClip?.uriString?.let { Uri.parse(it) },
        backgroundColor = backgroundColorArgb?.let { Color(it.toInt()) },
        offsetX = offsetX,
        offsetY = offsetY,
        scale = scale,
        rotation = rotation
    )
}

@Serializable
data class VideoTrimAdjustment(
    val startMs: Long = 0L,
    val endMs: Long = 3_000L,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val isFitMode: Boolean = false,
    val backgroundColorArgb: Long = 0xFF000000L,
    val isFlippedHorizontal: Boolean = false,
    val isFlippedVertical: Boolean = false
)

@Serializable
data class TimelineScene(
    val id: String,
    val title: String,
    val durationMs: Long,
    val clipId: String? = null,
    val mediaUriString: String? = null,
    val isVideo: Boolean = true,
    val clip: MediaClip? = null,
    val transform: SceneTransform = SceneTransform(),
    val motion: SceneMotion = SceneMotion(),
    val effect: SceneEffect = SceneEffect(),
    val effectPasses: List<ShaderEffectPass> = emptyList(),
    val colorGrading: ClipColorGrading = ClipColorGrading(),
    val velocityPoints: List<VelocityPoint> = defaultVelocityPoints(),
    val isVelocitySmooth: Boolean = true,
    val timing: SceneTiming = SceneTiming(durationMs = durationMs),
    val isReversed: Boolean = false,
    val transitionToNext: SceneTransition? = null,
    val layers: List<EditorLayer> = emptyList(),
    val isMultiLayerComposite: Boolean = false
) {
    val effectiveDurationMs: Long
        get() {
            val base = timing.durationMs.coerceAtLeast(200L)
            val freezeExtra = if (timing.freezeFrameEnabled) timing.freezeDurationMs.coerceAtLeast(0L) else 0L
            return (base + freezeExtra).coerceAtLeast(200L)
        }

    fun resolvedEffectPasses(): List<ShaderEffectPass> {
        val activePasses = effectPasses.filter { it.effectId != "none" && it.isEnabled }
        if (activePasses.isNotEmpty()) return activePasses
        if (effect.effectId != "none" && effect.intensity > 0.005f) {
            val catalogItem = EffectCatalog.findById(effect.effectId)
            val params = buildMap<String, Float> {
                if (catalogItem != null) putAll(catalogItem.defaultParamMap())
                if (effect.effectId == "tile_grid" || effect.effectId == "tile_2x2") put("grid_size", 2f)
                if (effect.effectId == "tile_3x3") put("grid_size", 3f)
            }
            return listOf(
                ShaderEffectPass(
                    id = "legacy_${effect.effectId}",
                    effectId = EffectCatalog.resolveCanonicalId(effect.effectId),
                    effectName = effect.effectName,
                    category = effect.category,
                    intensity = effect.intensity,
                    params = params
                )
            )
        }
        return emptyList()
    }
}

typealias ProjectScene = TimelineScene

@Serializable
data class VideoProject(
    val id: String,
    val title: String,
    val aspectRatio: AspectRatioMode,
    val frameRate: Int = 30,
    val resolution: String = "1080p",
    val compressionQuality: CompressionQuality = CompressionQuality.BALANCED,
    val durationMs: Long = 0L,
    val isFavorite: Boolean = false,
    val clips: List<MediaClip> = emptyList(),
    val clipCount: Int = clips.size,
    val activeAudioTitle: String = "إيقاع إلكتروني 128 BPM",
    val audioConfig: ProjectAudioConfig = ProjectAudioConfig(songTitle = activeAudioTitle),
    val layers: List<EditorLayer> = defaultLayers(),
    val trimAdjustment: VideoTrimAdjustment? = null,
    val velocityPoints: List<VelocityPoint> = defaultVelocityPoints(),
    val isVelocitySmooth: Boolean = true,
    val scenes: List<TimelineScene> = emptyList(),
    val studioLayout: StudioLayoutMode = StudioLayoutMode.NOVA,
    val aeLayers: List<EditorLayer> = emptyList()
) {
    val primaryClip: MediaClip?
        get() = scenes.firstOrNull()?.clip
            ?: aeLayers.firstOrNull { it.mediaClip != null }?.mediaClip
            ?: clips.firstOrNull()

    val totalTimelineDurationMs: Long
        get() = if (studioLayout == StudioLayoutMode.AFTER_EFFECTS && aeLayers.isNotEmpty()) {
            aeLayers.maxOf { (it.startTimeMs + it.effectiveLayerDurationMs).coerceAtLeast(500L) }
                .coerceAtLeast(durationMs)
        } else if (scenes.isNotEmpty()) {
            scenes.sumOf { it.effectiveDurationMs }.coerceAtLeast(500L)
        } else {
            durationMs.coerceAtLeast(0L)
        }

    fun resolveActiveAeLayer(positionMs: Long, preferredLayerId: String? = null): EditorLayer? {
        if (aeLayers.isEmpty()) return null
        preferredLayerId?.let { id ->
            aeLayers.firstOrNull { it.id == id }?.let { return it }
        }
        return aeLayers.firstOrNull { layer ->
            val end = layer.startTimeMs + layer.effectiveLayerDurationMs
            positionMs >= layer.startTimeMs && positionMs < end
        }
            ?: aeLayers.firstOrNull { it.isActiveAt(positionMs) }
            ?: aeLayers.minByOrNull { it.startTimeMs }
            ?: aeLayers.firstOrNull()
    }

    fun resolveSceneIndexForAeLayer(layer: EditorLayer?): Int? {
        if (layer == null || scenes.isEmpty()) return null
        layer.sourceSceneId?.let { srcId ->
            val byId = scenes.indexOfFirst { it.id == srcId }
            if (byId >= 0) return byId
        }
        val byClipId = layer.mediaClip?.id?.let { cid ->
            scenes.indexOfFirst { it.clip?.id == cid || it.clipId == cid }
        }
        if (byClipId != null && byClipId >= 0) return byClipId
        val posMid = layer.startTimeMs + (layer.effectiveLayerDurationMs / 2L)
        var cursor = 0L
        scenes.forEachIndexed { idx, sc ->
            val end = cursor + sc.effectiveDurationMs
            if (posMid >= cursor && posMid < end) return idx
            cursor = end
        }
        return null
    }
}

fun defaultLayers(): List<EditorLayer> = (1..5).map { num ->
    EditorLayer(
        id = "layer_$num",
        number = num,
        mediaClip = null
    )
}
