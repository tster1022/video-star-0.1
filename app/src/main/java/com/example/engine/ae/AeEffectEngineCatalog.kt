package com.example.engine.ae

import androidx.compose.ui.graphics.Color
import com.example.engine.effects.AlightMotionEffectRegistry
import com.example.model.EffectCatalogItem
import com.example.model.EffectCategory
import com.example.model.EffectParamSpec

/**
 * Origin badge inside the standalone After Effects engine.
 * Completely independent from Video Star and Node Video catalogs.
 */
enum class AeEffectSource(val badgeLabel: String, val filterLabel: String) {
    AFTER_EFFECTS("Ae", "After Effects"),
    ALIGHT_MOTION("AM", "Alight Motion")
}

/**
 * Standalone After Effects category taxonomy matching the 2-column dark category hub
 * and the 3-column paginated square-tile category screens.
 */
enum class AeEngineCategory(
    val id: String,
    val title: String,
    val bannerTitle: String,
    val gradStart: Color,
    val gradEnd: Color,
    val accentColor: Color
) {
    MY_EFFECTS("my_effects", "My effects", "My effects", Color(0xFF232838), Color(0xFF191D2A), Color(0xFFFACC15)),
    COLOR("color", "Color & Light", "Color", Color(0xFF1A294B), Color(0xFF0E1528), Color(0xFF38BDF8)),
    BLUR("blur", "Blur", "Blur", Color(0xFF13304A), Color(0xFF0C1524), Color(0xFF00E5FF)),
    DISTORTION("distortion", "Distortion/Warp", "Distortion", Color(0xFF18315A), Color(0xFF101933), Color(0xFF60A5FA)),
    STYLIZE("stylize", "Stylize", "Stylize", Color(0xFF12364C), Color(0xFF0B1829), Color(0xFF22D3EE)),
    CUTOUT("cutout", "Matte/Mask/Key", "Cutout", Color(0xFF2B1C54), Color(0xFF14112E), Color(0xFFA78BFA)),
    PROCEDURAL("procedural", "Procedural", "Procedural", Color(0xFF2A194E), Color(0xFF121028), Color(0xFFC084FC)),
    REPEAT("repeat", "Repeat", "Repeat", Color(0xFF341E5C), Color(0xFF15122C), Color(0xFF818CF8)),
    TRANSFORM("transform", "Move/Transform", "Transform", Color(0xFF232838), Color(0xFF1A1E2B), Color(0xFF94A3B8)),
    THREE_D("3d", "3D", "3D", Color(0xFF1B2452), Color(0xFF0F132A), Color(0xFF6366F1)),
    DRAWING_EDGE("drawing", "Drawing & Edge", "Drawing", Color(0xFF202A3E), Color(0xFF121826), Color(0xFF38BDF8)),
    TEXT("text", "Text", "Text", Color(0xFF18263C), Color(0xFF101624), Color(0xFF38BDF8)),
    TIME("time", "Time", "Time", Color(0xFFDC5442), Color(0xFF531E26), Color(0xFFFF7A66)),
    OPACITY("opacity", "Opacity/Visibility", "Opacity", Color(0xFF242938), Color(0xFF191D29), Color(0xFF9CA3AF)),
    TRANSITION("transition", "Transition", "Transition", Color(0xFF242938), Color(0xFF191D29), Color(0xFF00E676)),
    OTHER("other", "Other", "Other", Color(0xFF252A3F), Color(0xFF151929), Color(0xFF60A5FA))
}

enum class AeTileVisualStyle {
    ORBIT_ORANGE,
    PATH_BEZIER,
    SPLIT_CIRCLE,
    PULSE_RING,
    BAR_ROTATE,
    PENDULUM_SWING,
    GRID_MATRIX,
    RADIAL_RING,
    WAVE_STRIP,
    SWIRL_VORTEX,
    PINCH_LENS,
    MIRROR_AXIS,
    HEX_CELLS,
    COLOR_SPECTRUM,
    CHANNEL_NODES,
    MATTE_KEY,
    ALPHA_FADE,
    BLUR_SOFT,
    PROCEDURAL_NOISE,
    STARFIELD,
    LIGHTNING_BOLT,
    THREE_D_CUBE,
    THREE_D_SPHERE,
    THREE_D_TORUS,
    THREE_D_PAGE,
    DRAW_STROKE,
    TEXT_GLYPH,
    TIME_QUANTIZE,
    PARENT_LINK
}

data class AeEffectDefinition(
    val id: String,
    val name: String,
    val category: AeEngineCategory,
    val source: AeEffectSource,
    val description: String,
    val shaderCategory: EffectCategory,
    val defaultIntensity: Float = 0.75f,
    val params: List<EffectParamSpec> = emptyList(),
    val tileStyle: AeTileVisualStyle = AeTileVisualStyle.PROCEDURAL_NOISE
) {
    fun toCatalogItem(): EffectCatalogItem = EffectCatalogItem(
        id = id,
        name = name,
        category = shaderCategory,
        defaultIntensity = defaultIntensity,
        params = params
    )
}

/**
 * Standalone After Effects Effects Catalog.
 * Zero dependency on Video Star's 35-effect catalog or Node Video.
 * Structured cleanly so upcoming user-provided AE effect lists plug straight into this registry.
 */
object AeEffectEngineCatalog {

    private val afterEffectsSourceIds: Set<String> = setOf(
        "am_turbulent_displace",
        "am_polar_coords",
        "am_pixelate",
        "am_twirl",
        "am_swirl",
        "am_pinch_bulge",
        "am_kaleidoscope",
        "am_adv_chroma_key",
        "am_key_spill_cleaner",
        "am_luma_key",
        "am_matte_choker",
        "am_radial_wipe",
        "am_vignette",
        "am_block_dissolve",
        "am_dissolve",
        "am_feather",
        "am_lens_flare",
        "am_lightning",
        "am_fractal_ridges",
        "am_noise",
        "am_turbulence",
        "am_grid",
        "am_channel_remap_hsv",
        "am_channel_remap_rgb",
        "am_echo_keyframes",
        "am_time_quantization",
        "am_parenting_helper",
        "am_text_timecode",
        "am_text_randomizer",
        "am_3d_page_curl",
        "am_3d_spherize",
        "am_3d_cube",
        "am_offset"
    )

    private val categoryOverrideById: Map<String, AeEngineCategory> = mapOf(
        "am_channel_remap_hsv" to AeEngineCategory.COLOR,
        "am_channel_remap_rgb" to AeEngineCategory.COLOR,
        "am_solid_matte" to AeEngineCategory.COLOR,
        "am_vignette" to AeEngineCategory.COLOR,
        "am_iridescence" to AeEngineCategory.COLOR,
        "am_time_quantization" to AeEngineCategory.TIME,
        "am_echo_keyframes" to AeEngineCategory.TIME,
        "am_luma_stamper" to AeEngineCategory.TIME,
        "am_feather" to AeEngineCategory.BLUR,
        "am_omino_diffusion" to AeEngineCategory.BLUR,
        "am_dissolve" to AeEngineCategory.OPACITY,
        "am_block_dissolve" to AeEngineCategory.OPACITY,
        "am_radial_wipe" to AeEngineCategory.TRANSITION,
        "am_wipe" to AeEngineCategory.TRANSITION,
        "am_matte_fringe" to AeEngineCategory.DRAWING_EDGE,
        "am_contour_strips" to AeEngineCategory.DRAWING_EDGE,
        "am_stripes" to AeEngineCategory.DRAWING_EDGE,
        "am_copy_background" to AeEngineCategory.OTHER,
        "am_magnify_background" to AeEngineCategory.OTHER,
        "am_fill_behind" to AeEngineCategory.OTHER,
        "am_parenting_helper" to AeEngineCategory.OTHER,
        "am_lens_flare" to AeEngineCategory.STYLIZE,
        "am_lightning" to AeEngineCategory.STYLIZE,
        "am_grunge" to AeEngineCategory.STYLIZE,
        "am_energy_beam" to AeEngineCategory.STYLIZE
    )

    private val descriptionsById: Map<String, String> = mapOf(
        "am_auto_shake" to "Continuous procedural camera shake with frequency & slack control.",
        "am_move_along_path" to "Slides and orients the layer smoothly along a curved bezier path.",
        "am_offset" to "Shifts layer pixels horizontally and vertically with wrap or mirror tiling.",
        "am_oscillate" to "Drives rhythmic sine or triangle motion along a custom angle.",
        "am_pulse_size" to "Beats the layer scale in and out at an adjustable Hz tempo.",
        "am_random_displacement" to "Organically drifts layer coordinates using evolving noise seeds.",
        "am_random_jitter" to "Adds crisp staccato micro-jitters to position and rotation.",
        "am_raster_transform" to "High-precision pixel transform with subpixel anti-aliasing.",
        "am_scale_assist" to "Uniform and directional scale multiplier with X/Y axis bias.",
        "am_spin" to "Rotates the layer continuously at exact rotations per second.",
        "am_stretch_axis" to "Elongates the layer along any angle with content compensation.",
        "am_swing" to "Pendulum sway between two keyable angles over time.",
        "am_hex_tile_shift" to "Splits the frame into hexagons and offsets neighboring cells.",
        "am_hex_tiling" to "Creates a seamless honeycomb tile grid with mirrored borders.",
        "am_inner_pinch_bulge" to "Localized radial bulge or pinch with smooth falloff feathering.",
        "am_kaleidoscope" to "Symmetric multi-slice mirror mandala with angle and zoom.",
        "am_mirror" to "Reflects one half of the layer seamlessly across a custom axis.",
        "am_omino_glass" to "Refracts the image through ribbed glass prisms with dispersion.",
        "am_pinch_bulge" to "Pushes or pulls pixels from a center point for lens warp looks.",
        "am_pixelate" to "Quantizes image resolution into crisp retro square or aspect pixels.",
        "am_polar_coords" to "Converts rectangular coordinates into circular polar space.",
        "am_polar_displace" to "Displaces pixels radially using angular polar field waves.",
        "am_squeeze" to "Compresses and expands horizontal or vertical edge curvature.",
        "am_stretch_segment" to "Freezes and stretches a narrow slice across the canvas.",
        "am_swirl" to "Twists pixels smoothly into a rotational vortex around a center.",
        "am_tile_rotate" to "Subdivides the frame into a grid where each tile rotates cleanly.",
        "am_tile_shift" to "Staggers alternating rows or columns in sliding strip bands.",
        "am_tiles" to "Repeats and mirrors edges for seamless motion-tile edits.",
        "am_turbulent_displace" to "Distorts edges using animated multi-octave fractal turbulence.",
        "am_wave_warp" to "Bends the layer along smooth directional sine waves.",
        "am_adv_chroma_key" to "Studio green/blue screen keyer with edge refine and despill.",
        "am_chroma_key" to "Fast color-screen keying with adjustable threshold and feather.",
        "am_key_spill_cleaner" to "Neutralizes green or blue spill halo around keyed subjects.",
        "am_luma_key" to "Removes bright or dark luminance ranges cleanly with soft roll-off.",
        "am_luma_stamper" to "Stamps bright temporal trails over dark background frames.",
        "am_matte_choker" to "Shrinks or expands alpha matte edges for clean cutouts.",
        "am_matte_fringe" to "Adds a glowing colored outline strictly along cutout borders.",
        "am_radial_wipe" to "Sweeps a clock-style angular reveal wipe around a pivot.",
        "am_solid_matte" to "Fills visible pixels with a clean solid color matte.",
        "am_vignette" to "Darkens or tints outer corners with controllable roundness.",
        "am_wipe" to "Linear edge wipe transition with angle and soft feather.",
        "am_blink" to "Strobes visibility on and off with adjustable duty cycle.",
        "am_block_dissolve" to "Dissolves pixels away using randomized rectangular blocks.",
        "am_dissolve" to "Fine film-grain pixel dissolve transition across opacity.",
        "am_fade_in_out" to "Automatic smooth entrance and exit fade without keyframes.",
        "am_feather" to "Softens outer layer borders with smooth alpha gradient blur.",
        "am_flicker" to "Organic brightness & opacity flicker like vintage cinema.",
        "am_pulse_opacity" to "Breathing opacity curve oscillating between min and max alpha.",
        "am_block_noise" to "Digital glitch block noise pattern for cyber transitions.",
        "am_checker" to "Procedural checkerboard pattern with scale and color tint.",
        "am_clouds" to "Soft procedural fBm cloud texture overlay.",
        "am_contour_strips" to "Topographic contour bands carved across luminance levels.",
        "am_dots" to "Halftone polka-dot screen matrix over the active layer.",
        "am_energy_beam" to "Glowing plasma beam streak with core & outer bloom.",
        "am_fractal_ridges" to "Electric ridged noise veins ideal for abstract backgrounds.",
        "am_grid" to "Technical blueprint coordinate grid overlay with line thickness.",
        "am_grunge" to "Distressed film texture & organic surface wear overlay.",
        "am_hex_array" to "Futuristic sci-fi hexagonal HUD pattern generator.",
        "am_iridescence" to "Oil-slick rainbow spectral sheen driven by surface luma.",
        "am_lens_flare" to "Anamorphic optical flare burst with halo rings and streaks.",
        "am_lightning" to "Procedural branching electric lightning bolts between points.",
        "am_noise" to "Fine dynamic monochrome or chromatic sensor film grain.",
        "am_omino_diffusion" to "Dreamy soft halation glow that blooms bright highlights.",
        "am_radial_rays" to "Volumetric god-rays radiating from an adjustable light source.",
        "am_ribbon" to "Flowing multi-strand sine wave light ribbons.",
        "am_simple_starfield" to "Clean drifting 2D starfield backdrop with depth parallax.",
        "am_star" to "Sharp procedural geometric star burst polygon overlay.",
        "am_starfield" to "Deep-space parallax star tunnel with twinkling density.",
        "am_stripes" to "Crisp diagonal graphic stripes with width and phase animation.",
        "am_turbulence" to "Swirling plasma turbulence field for atmospheric textures.",
        "am_voronoi_cells" to "Cellular crystal facets with glowing organic boundaries.",
        "am_grid_repeat" to "Clones the layer across an infinite 2D tiled matrix.",
        "am_linear_repeat" to "Duplicates copies along a straight line with stepped scale/fade.",
        "am_radial_repeat" to "Arranges clones evenly in a spinning circular ring.",
        "am_repeat" to "Stacks cascaded echo clones with progressive offset & opacity.",
        "am_repeat_along_path" to "Distributes layer copies evenly along a curved spline.",
        "am_scatter_field" to "Scatters randomized organic copies across the frame.",
        "am_scatter_repeat" to "Controlled burst of layered clones with random rotation.",
        "am_3d_box" to "Wraps the layer onto a perspective-shaded 3D box mesh.",
        "am_3d_cube" to "True 3D rotating cube projection with lighting depth.",
        "am_3d_cylinder" to "Curves the layer smoothly around a rotating 3D cylinder.",
        "am_3d_ellipsoid" to "Maps media onto a sculpted 3D oval ellipsoid surface.",
        "am_3d_flip_layer" to "Flips the layer cleanly in 3D space with perspective tilt.",
        "am_3d_heart" to "Extruded glossy 3D heart volume shaded from layer colors.",
        "am_3d_hex_prism" to "Six-sided beveled 3D prism rotation in camera space.",
        "am_3d_hollow_box" to "Open wireframe-style hollow 3D cube frame.",
        "am_3d_octahedron" to "Eight-faced diamond crystal polyhedron in 3D space.",
        "am_3d_page_curl" to "Realistic 3D curled page turn reveal with curled shadow.",
        "am_3d_pyramid" to "Four-sided rotating 3D pyramid geometry projection.",
        "am_3d_raster_extrude" to "Extrudes pixels into stacked 3D depth relief slices.",
        "am_3d_ring" to "Wraps the frame into an orbital 3D halo ring.",
        "am_3d_spherize" to "Bends the layer into a glossy 3D orb sphere.",
        "am_3d_star_polyhedron" to "Faceted 3D stellated star polyhedron geometry.",
        "am_3d_star_prism" to "Extruded 5-point 3D star prism with specular edges.",
        "am_3d_three_axis_cross" to "Intersecting 3-axis volumetric cross sculpture.",
        "am_3d_torus" to "Twists the layer continuously around a 3D donut torus.",
        "am_text_count" to "Animates numeric counters smoothly between start & end values.",
        "am_text_progress" to "Reveals characters progressively left-to-right over time.",
        "am_text_randomizer" to "Scrambles glyphs in matrix decode style before locking in.",
        "am_text_spacing" to "Animates tracking and letter spacing dynamically.",
        "am_text_transform" to "Per-character wave, scale, and fade text animation.",
        "am_text_timecode" to "Live SMPTE / millisecond timecode readout generator.",
        "am_channel_remap_hsv" to "Remaps Hue, Saturation, and Value channels independently.",
        "am_channel_remap_rgb" to "Swaps Red, Green, Blue, and Alpha channels cleanly.",
        "am_copy_background" to "Samples underlying layers for frosted glass & refraction.",
        "am_echo_keyframes" to "Renders ghosted temporal motion echoes behind movement.",
        "am_fill_behind" to "Fills transparent cutouts with a clean ambient backdrop.",
        "am_magnify_background" to "Zooms and refracts background layers inside layer bounds.",
        "am_parenting_helper" to "Links position, scale, and rotation follow weights to parent.",
        "am_time_quantization" to "Reduces effective frame rate for crisp stop-motion / posterize time."
    )

    private fun resolveTileStyle(id: String, cat: AeEngineCategory): AeTileVisualStyle = when (id) {
        "am_auto_shake", "am_random_displacement", "am_random_jitter", "am_oscillate" -> AeTileVisualStyle.ORBIT_ORANGE
        "am_move_along_path", "am_repeat_along_path" -> AeTileVisualStyle.PATH_BEZIER
        "am_offset", "am_stretch_segment" -> AeTileVisualStyle.SPLIT_CIRCLE
        "am_pulse_size", "am_scale_assist", "am_raster_transform" -> AeTileVisualStyle.PULSE_RING
        "am_spin", "am_stretch_axis" -> AeTileVisualStyle.BAR_ROTATE
        "am_swing" -> AeTileVisualStyle.PENDULUM_SWING
        "am_hex_tile_shift", "am_hex_tiling", "am_hex_array" -> AeTileVisualStyle.HEX_CELLS
        "am_grid_repeat", "am_tiles", "am_tile_rotate", "am_tile_shift", "am_grid", "am_checker" -> AeTileVisualStyle.GRID_MATRIX
        "am_radial_repeat", "am_kaleidoscope", "am_radial_rays", "am_radial_wipe" -> AeTileVisualStyle.RADIAL_RING
        "am_wave_warp", "am_turbulent_displace", "am_ribbon" -> AeTileVisualStyle.WAVE_STRIP
        "am_swirl", "am_polar_coords", "am_polar_displace" -> AeTileVisualStyle.SWIRL_VORTEX
        "am_pinch_bulge", "am_inner_pinch_bulge", "am_squeeze", "am_magnify_background" -> AeTileVisualStyle.PINCH_LENS
        "am_mirror" -> AeTileVisualStyle.MIRROR_AXIS
        "am_channel_remap_hsv", "am_channel_remap_rgb" -> AeTileVisualStyle.CHANNEL_NODES
        "am_3d_page_curl" -> AeTileVisualStyle.THREE_D_PAGE
        "am_3d_spherize", "am_3d_ellipsoid" -> AeTileVisualStyle.THREE_D_SPHERE
        "am_3d_torus", "am_3d_ring" -> AeTileVisualStyle.THREE_D_TORUS
        "am_starfield", "am_simple_starfield", "am_star" -> AeTileVisualStyle.STARFIELD
        "am_lightning", "am_energy_beam" -> AeTileVisualStyle.LIGHTNING_BOLT
        "am_time_quantization", "am_echo_keyframes", "am_luma_stamper" -> AeTileVisualStyle.TIME_QUANTIZE
        "am_parenting_helper" -> AeTileVisualStyle.PARENT_LINK
        else -> when (cat) {
            AeEngineCategory.COLOR -> AeTileVisualStyle.COLOR_SPECTRUM
            AeEngineCategory.BLUR -> AeTileVisualStyle.BLUR_SOFT
            AeEngineCategory.DISTORTION -> AeTileVisualStyle.WAVE_STRIP
            AeEngineCategory.STYLIZE -> AeTileVisualStyle.LIGHTNING_BOLT
            AeEngineCategory.CUTOUT -> AeTileVisualStyle.MATTE_KEY
            AeEngineCategory.PROCEDURAL -> AeTileVisualStyle.PROCEDURAL_NOISE
            AeEngineCategory.REPEAT -> AeTileVisualStyle.RADIAL_RING
            AeEngineCategory.TRANSFORM -> AeTileVisualStyle.ORBIT_ORANGE
            AeEngineCategory.THREE_D -> AeTileVisualStyle.THREE_D_CUBE
            AeEngineCategory.DRAWING_EDGE -> AeTileVisualStyle.DRAW_STROKE
            AeEngineCategory.TEXT -> AeTileVisualStyle.TEXT_GLYPH
            AeEngineCategory.TIME -> AeTileVisualStyle.TIME_QUANTIZE
            AeEngineCategory.OPACITY -> AeTileVisualStyle.ALPHA_FADE
            AeEngineCategory.TRANSITION -> AeTileVisualStyle.MIRROR_AXIS
            AeEngineCategory.OTHER -> AeTileVisualStyle.CHANNEL_NODES
            AeEngineCategory.MY_EFFECTS -> AeTileVisualStyle.ORBIT_ORANGE
        }
    }

    private fun mapCategory(item: EffectCatalogItem): AeEngineCategory {
        categoryOverrideById[item.id]?.let { return it }
        return when (item.category) {
            EffectCategory.AM_MOVE_TRANSFORM -> AeEngineCategory.TRANSFORM
            EffectCategory.AM_DISTORTION_WARP -> AeEngineCategory.DISTORTION
            EffectCategory.AM_MATTE_MASK_KEY -> AeEngineCategory.CUTOUT
            EffectCategory.AM_OPACITY_VISIBILITY -> AeEngineCategory.OPACITY
            EffectCategory.AM_PROCEDURAL -> AeEngineCategory.PROCEDURAL
            EffectCategory.AM_REPEAT -> AeEngineCategory.REPEAT
            EffectCategory.AM_THREE_D -> AeEngineCategory.THREE_D
            EffectCategory.AM_TEXT -> AeEngineCategory.TEXT
            EffectCategory.AM_OTHER -> AeEngineCategory.OTHER
            else -> AeEngineCategory.STYLIZE
        }
    }

    private val builtInEffects: List<AeEffectDefinition> by lazy {
        AlightMotionEffectRegistry.effects.map { item ->
            val cat = mapCategory(item)
            val src = if (item.id in afterEffectsSourceIds) {
                AeEffectSource.AFTER_EFFECTS
            } else {
                AeEffectSource.ALIGHT_MOTION
            }
            val desc = descriptionsById[item.id]
                ?: "Real-time AE ${cat.title.lowercase()} processor with keyframeable controls."
            AeEffectDefinition(
                id = item.id,
                name = item.name,
                category = cat,
                source = src,
                description = desc,
                shaderCategory = item.category,
                defaultIntensity = item.defaultIntensity,
                params = item.params,
                tileStyle = resolveTileStyle(item.id, cat)
            )
        }
    }

    private val builtInByIdMap: Map<String, AeEffectDefinition> by lazy {
        builtInEffects.associateBy { it.id }
    }

    val allEffects: List<AeEffectDefinition>
        get() = com.example.engine.packs.CustomPackRepository.allAeDefinitions() + builtInEffects

    fun findById(effectId: String): AeEffectDefinition? =
        com.example.engine.packs.CustomPackRepository.findAeDefinition(effectId)
            ?: builtInByIdMap[effectId]

    fun visibleCategories(): List<AeEngineCategory> {
        val populated = builtInEffects.map { it.category }.toSet()
        return listOf(AeEngineCategory.MY_EFFECTS) +
            AeEngineCategory.entries.filter { it != AeEngineCategory.MY_EFFECTS && it in populated }
    }

    fun navigableCategories(): List<AeEngineCategory> = visibleCategories()
}
