package com.example.engine.effects

import com.example.model.EffectCatalogItem
import com.example.model.EffectCategory
import com.example.model.EffectParamSpec
import com.example.model.EffectParamType

/**
 * Central typed catalog of all 110 Alight Motion-grade GPU effects strictly organized
 * across the 9 core categories:
 * 1. Move/Transform (12)
 * 2. Distortion/Warp (18)
 * 3. Matte/Mask/Key (11)
 * 4. Opacity/Visibility (7)
 * 5. Procedural (23)
 * 6. Repeat (7)
 * 7. 3D (18)
 * 8. Text (6)
 * 9. Other (8)
 */
object AlightMotionEffectRegistry {

    private fun f(
        key: String,
        label: String,
        def: Float,
        min: Float = 0f,
        max: Float = 1f,
        unit: String = ""
    ) = EffectParamSpec(key, label, def, min, max, unit, EffectParamType.FLOAT)

    private fun col(
        key: String,
        label: String,
        argb: Long
    ) = EffectParamSpec(
        key = key,
        label = label,
        defaultValue = 1f,
        minValue = 0f,
        maxValue = 1f,
        paramType = EffectParamType.COLOR,
        defaultColorArgb = argb
    )

    private fun ptX(key: String, label: String, pairedY: String, def: Float = 0.5f) =
        EffectParamSpec(key, label, def, 0f, 1f, "", EffectParamType.POINT_2D_X, pairedKey = pairedY)

    private fun ptY(key: String, label: String, pairedX: String, def: Float = 0.5f) =
        EffectParamSpec(key, label, def, 0f, 1f, "", EffectParamType.POINT_2D_Y, pairedKey = pairedX)

    private fun mode(key: String, label: String, def: Int, options: List<String>) =
        EffectParamSpec(
            key = key,
            label = label,
            defaultValue = def.toFloat(),
            minValue = 0f,
            maxValue = (options.size - 1).coerceAtLeast(0).toFloat(),
            paramType = EffectParamType.ENUM_MODE,
            enumOptions = options
        )

    private fun bool(key: String, label: String, def: Boolean = false) =
        EffectParamSpec(
            key = key,
            label = label,
            defaultValue = if (def) 1f else 0f,
            minValue = 0f,
            maxValue = 1f,
            paramType = EffectParamType.BOOLEAN,
            enumOptions = listOf("Off", "On")
        )

    private fun txt(key: String, label: String, def: String) =
        EffectParamSpec(
            key = key,
            label = label,
            defaultValue = 0f,
            minValue = 0f,
            maxValue = 1f,
            paramType = EffectParamType.TEXT_STRING,
            defaultText = def
        )

    val moveTransformEffects: List<EffectCatalogItem> = listOf(
        EffectCatalogItem(
            id = "am_auto_shake",
            name = "Auto-Shake",
            category = EffectCategory.AM_MOVE_TRANSFORM,
            defaultIntensity = 0.8f,
            params = listOf(
                f("magnitude", "Magnitude", 0.35f, 0f, 1.5f),
                f("frequency", "Frequency", 4.5f, 0.2f, 20f, "Hz"),
                f("evolution", "Evolution", 0.5f, 0f, 10f),
                f("angle", "Angle", 0f, -180f, 180f, "°"),
                f("seed", "Seed", 1f, 0f, 99f),
                f("slack", "Slack", 0.3f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "am_move_along_path",
            name = "Move Along Path",
            category = EffectCategory.AM_MOVE_TRANSFORM,
            defaultIntensity = 1.0f,
            params = listOf(
                f("progress", "Progress %", 0.5f, 0f, 1f, "%"),
                bool("orient_to_path", "Orient to Path", true),
                f("curvature", "Path Curvature", 0.65f, -1.5f, 1.5f)
            )
        ),
        EffectCatalogItem(
            id = "am_offset",
            name = "Offset",
            category = EffectCategory.AM_MOVE_TRANSFORM,
            defaultIntensity = 1.0f,
            params = listOf(
                f("offset_x", "X Offset", 0.2f, -2f, 2f),
                f("offset_y", "Y Offset", 0f, -2f, 2f),
                mode("wrap_mode", "Wrap Mode", 1, listOf("Clamp", "Repeat", "Mirror"))
            )
        ),
        EffectCatalogItem(
            id = "am_oscillate",
            name = "Oscillate",
            category = EffectCategory.AM_MOVE_TRANSFORM,
            defaultIntensity = 1.0f,
            params = listOf(
                f("angle", "Angle", 45f, -180f, 180f, "°"),
                f("frequency", "Frequency Hz", 2.0f, 0.1f, 16f, "Hz"),
                f("magnitude", "Magnitude", 0.25f, 0f, 1.2f),
                f("phase", "Phase", 0f, 0f, 360f, "°"),
                mode("wave_type", "Wave Type", 0, listOf("Sine", "Triangle"))
            )
        ),
        EffectCatalogItem(
            id = "am_pulse_size",
            name = "Pulse Size",
            category = EffectCategory.AM_MOVE_TRANSFORM,
            defaultIntensity = 1.0f,
            params = listOf(
                f("frequency", "Frequency", 2.0f, 0.1f, 12f, "Hz"),
                f("grow_size", "Grow Size", 1.28f, 1.0f, 2.5f, "x"),
                f("shrink_size", "Shrink Size", 0.85f, 0.2f, 1.0f, "x"),
                f("phase", "Phase", 0f, 0f, 360f, "°")
            )
        ),
        EffectCatalogItem(
            id = "am_random_displacement",
            name = "Random Displacement",
            category = EffectCategory.AM_MOVE_TRANSFORM,
            defaultIntensity = 0.85f,
            params = listOf(
                f("magnitude", "Magnitude", 0.32f, 0f, 1.5f),
                f("evolution", "Evolution", 1.2f, 0f, 10f),
                f("seed", "Seed", 7f, 0f, 99f),
                f("scatter", "Scatter", 0.45f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "am_random_jitter",
            name = "Random Jitter",
            category = EffectCategory.AM_MOVE_TRANSFORM,
            defaultIntensity = 0.85f,
            params = listOf(
                f("pos_jitter", "Position Jitter", 0.25f, 0f, 1f),
                f("angle_jitter", "Angle Jitter", 12f, 0f, 60f, "°"),
                f("frequency", "Frequency", 14f, 1f, 30f, "Hz"),
                f("seed", "Seed", 3f, 0f, 99f)
            )
        ),
        EffectCatalogItem(
            id = "am_raster_transform",
            name = "Raster Transform",
            category = EffectCategory.AM_MOVE_TRANSFORM,
            defaultIntensity = 1.0f,
            params = listOf(
                ptX("pos_x", "Position X", "pos_y", 0.5f),
                ptY("pos_y", "Position Y", "pos_x", 0.5f),
                f("scale", "Scale", 1.15f, 0.1f, 4f, "x"),
                f("rotation", "Rotation", 15f, -360f, 360f, "°"),
                bool("anti_aliasing", "Anti-aliasing", true)
            )
        ),
        EffectCatalogItem(
            id = "am_scale_assist",
            name = "Scale Assist",
            category = EffectCategory.AM_MOVE_TRANSFORM,
            defaultIntensity = 1.0f,
            params = listOf(
                f("scale_mult", "Scale Multiplier", 1.25f, 0.1f, 4f, "x"),
                bool("uniform_lock", "Uniform Lock", true),
                f("x_bias", "X Bias", 1.0f, 0.2f, 3f),
                f("y_bias", "Y Bias", 1.0f, 0.2f, 3f)
            )
        ),
        EffectCatalogItem(
            id = "am_spin",
            name = "Spin",
            category = EffectCategory.AM_MOVE_TRANSFORM,
            defaultIntensity = 1.0f,
            params = listOf(
                f("rot_per_sec", "Rotations/sec", 0.5f, 0f, 6f),
                f("angle_offset", "Angle Offset", 0f, -180f, 180f, "°"),
                mode("direction", "Direction", 0, listOf("CW", "CCW"))
            )
        ),
        EffectCatalogItem(
            id = "am_stretch_axis",
            name = "Stretch Axis",
            category = EffectCategory.AM_MOVE_TRANSFORM,
            defaultIntensity = 1.0f,
            params = listOf(
                f("scale", "Scale", 1.6f, 0.2f, 4f, "x"),
                f("angle", "Angle", 0f, -180f, 180f, "°"),
                bool("content_compensate", "Content-Aware Compensate", true)
            )
        ),
        EffectCatalogItem(
            id = "am_swing",
            name = "Swing",
            category = EffectCategory.AM_MOVE_TRANSFORM,
            defaultIntensity = 1.0f,
            params = listOf(
                f("frequency", "Frequency", 1.5f, 0.1f, 10f, "Hz"),
                f("angle_1", "Angle 1", -25f, -180f, 180f, "°"),
                f("angle_2", "Angle 2", 25f, -180f, 180f, "°"),
                f("phase", "Phase", 0f, 0f, 360f, "°")
            )
        )
    )

    val distortionWarpEffects: List<EffectCatalogItem> = listOf(
        EffectCatalogItem(
            id = "am_hex_tile_shift",
            name = "Hexagon Tile Shift",
            category = EffectCategory.AM_DISTORTION_WARP,
            defaultIntensity = 0.85f,
            params = listOf(
                f("size", "Size", 0.14f, 0.03f, 0.5f),
                f("shift_x", "Shift X", 0.25f, -1f, 1f),
                f("shift_y", "Shift Y", 0.15f, -1f, 1f),
                f("angle", "Angle", 0f, -180f, 180f, "°")
            )
        ),
        EffectCatalogItem(
            id = "am_hex_tiling",
            name = "Hexagon Tiling",
            category = EffectCategory.AM_DISTORTION_WARP,
            defaultIntensity = 0.9f,
            params = listOf(
                f("scale", "Scale", 3.5f, 1f, 12f),
                f("rotation", "Rotation", 0f, -180f, 180f, "°"),
                bool("mirror_edges", "Mirror Edges", true)
            )
        ),
        EffectCatalogItem(
            id = "am_inner_pinch_bulge",
            name = "Inner Pinch/Bulge",
            category = EffectCategory.AM_DISTORTION_WARP,
            defaultIntensity = 0.85f,
            params = listOf(
                ptX("center_x", "Center X", "center_y", 0.5f),
                ptY("center_y", "Center Y", "center_x", 0.5f),
                f("strength", "Strength", 0.65f, -1f, 1f),
                f("radius", "Radius", 0.42f, 0.05f, 1f),
                f("feather", "Feather", 0.25f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "am_kaleidoscope",
            name = "Kaleidoscope",
            category = EffectCategory.AM_DISTORTION_WARP,
            defaultIntensity = 1.0f,
            params = listOf(
                f("slices", "Slices", 6f, 2f, 24f),
                f("angle", "Angle", 0f, -180f, 180f, "°"),
                f("zoom", "Zoom", 1.1f, 0.3f, 3f, "x"),
                ptX("offset_x", "Offset X", "offset_y", 0.5f),
                ptY("offset_y", "Offset Y", "offset_x", 0.5f)
            )
        ),
        EffectCatalogItem(
            id = "am_mirror",
            name = "Mirror",
            category = EffectCategory.AM_DISTORTION_WARP,
            defaultIntensity = 1.0f,
            params = listOf(
                f("angle", "Angle", 0f, -180f, 180f, "°"),
                f("offset", "Offset", 0.5f, 0f, 1f),
                bool("flip_side", "Flip Side", false)
            )
        ),
        EffectCatalogItem(
            id = "am_omino_glass",
            name = "Omino Glass",
            category = EffectCategory.AM_DISTORTION_WARP,
            defaultIntensity = 0.85f,
            params = listOf(
                f("refraction", "Refraction Index", 0.65f, 0f, 1.5f),
                f("scale", "Scale", 4.0f, 0.5f, 16f),
                f("dispersion", "Dispersion", 0.5f, 0f, 1f),
                f("roughness", "Roughness", 0.25f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "am_pinch_bulge",
            name = "Pinch/Bulge",
            category = EffectCategory.AM_DISTORTION_WARP,
            defaultIntensity = 0.85f,
            params = listOf(
                ptX("center_x", "Center X", "center_y", 0.5f),
                ptY("center_y", "Center Y", "center_x", 0.5f),
                f("strength", "Strength", 0.6f, -1f, 1f),
                f("radius", "Radius", 0.55f, 0.1f, 1.2f)
            )
        ),
        EffectCatalogItem(
            id = "am_pixelate",
            name = "Pixelate",
            category = EffectCategory.AM_DISTORTION_WARP,
            defaultIntensity = 0.9f,
            params = listOf(
                f("pixel_size", "Pixel Size", 0.45f, 0.02f, 1f),
                f("aspect_ratio", "Aspect Ratio", 1.0f, 0.25f, 4f),
                f("grid_opacity", "Grid Opacity", 0.18f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "am_polar_coords",
            name = "Polar Coordinates",
            category = EffectCategory.AM_DISTORTION_WARP,
            defaultIntensity = 1.0f,
            params = listOf(
                f("interpolation", "Interpolation %", 1.0f, 0f, 1f, "%"),
                mode("type", "Type", 0, listOf("Rect-to-Polar", "Polar-to-Rect"))
            )
        ),
        EffectCatalogItem(
            id = "am_polar_displace",
            name = "Polar Displacement Map",
            category = EffectCategory.AM_DISTORTION_WARP,
            defaultIntensity = 0.8f,
            params = listOf(
                f("angle_offset", "Angle Offset", 45f, -180f, 180f, "°"),
                f("radial_strength", "Radial Strength", 0.4f, -1f, 1f),
                f("scale", "Scale", 3.0f, 0.5f, 12f)
            )
        ),
        EffectCatalogItem(
            id = "am_squeeze",
            name = "Squeeze",
            category = EffectCategory.AM_DISTORTION_WARP,
            defaultIntensity = 0.85f,
            params = listOf(
                f("strength_x", "Strength X", 0.55f, -1f, 1f),
                f("strength_y", "Strength Y", 0.25f, -1f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "am_stretch_segment",
            name = "Stretch Segment",
            category = EffectCategory.AM_DISTORTION_WARP,
            defaultIntensity = 1.0f,
            params = listOf(
                f("angle", "Angle", 0f, -180f, 180f, "°"),
                f("offset", "Offset", 0.5f, 0.1f, 0.9f),
                f("stretch_width", "Stretch Width", 0.35f, 0f, 0.9f)
            )
        ),
        EffectCatalogItem(
            id = "am_swirl",
            name = "Swirl",
            category = EffectCategory.AM_DISTORTION_WARP,
            defaultIntensity = 0.85f,
            params = listOf(
                ptX("center_x", "Center X", "center_y", 0.5f),
                ptY("center_y", "Center Y", "center_x", 0.5f),
                f("radius", "Radius", 0.55f, 0.1f, 1.2f),
                f("strength", "Strength", 0.6f, -1f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "am_tile_rotate",
            name = "Tile Rotate",
            category = EffectCategory.AM_DISTORTION_WARP,
            defaultIntensity = 1.0f,
            params = listOf(
                f("grid_count", "Grid Count", 4f, 1f, 16f),
                f("cell_angle", "Cell Angle", 35f, -180f, 180f, "°"),
                f("scale", "Scale", 1.0f, 0.25f, 3f, "x")
            )
        ),
        EffectCatalogItem(
            id = "am_tile_shift",
            name = "Tile Shift",
            category = EffectCategory.AM_DISTORTION_WARP,
            defaultIntensity = 1.0f,
            params = listOf(
                f("count", "Count", 6f, 2f, 24f),
                f("offset", "Offset", 0.25f, -1f, 1f),
                f("angle", "Angle", 0f, -180f, 180f, "°")
            )
        ),
        EffectCatalogItem(
            id = "am_tiles",
            name = "Tiles",
            category = EffectCategory.AM_DISTORTION_WARP,
            defaultIntensity = 1.0f,
            params = listOf(
                f("crop", "Crop", 1.0f, 0.2f, 6f),
                bool("mirror", "Mirror", true),
                f("offset", "Offset", 0f, -2f, 2f),
                f("angle", "Angle", 0f, -180f, 180f, "°")
            )
        ),
        EffectCatalogItem(
            id = "am_turbulent_displace",
            name = "Turbulent Displace",
            category = EffectCategory.AM_DISTORTION_WARP,
            defaultIntensity = 0.85f,
            params = listOf(
                ptX("offset_x", "Offset X", "offset_y", 0f),
                ptY("offset_y", "Offset Y", "offset_x", 0f),
                f("strength", "Strength", 0.45f, 0f, 1.5f),
                f("evolution", "Evolution", 0.5f, 0f, 10f),
                f("scale", "Scale", 4.0f, 0.5f, 16f),
                f("octaves", "Octaves", 4f, 1f, 6f)
            )
        ),
        EffectCatalogItem(
            id = "am_wave_warp",
            name = "Wave Warp",
            category = EffectCategory.AM_DISTORTION_WARP,
            defaultIntensity = 0.85f,
            params = listOf(
                f("phase", "Phase", 0f, 0f, 360f, "°"),
                f("angle", "Angle", 0f, -180f, 180f, "°"),
                f("spacing", "Spacing", 6f, 1f, 24f),
                f("magnitude", "Magnitude", 0.35f, 0f, 1f),
                f("damping", "Damping", 0f, 0f, 1f)
            )
        )
    )

    val matteMaskKeyEffects: List<EffectCatalogItem> = listOf(
        EffectCatalogItem(
            id = "am_adv_chroma_key",
            name = "Advanced Chroma Key",
            category = EffectCategory.AM_MATTE_MASK_KEY,
            defaultIntensity = 1.0f,
            params = listOf(
                col("key_color", "Key Color", 0xFF00D840L),
                f("threshold", "Threshold", 0.32f, 0.01f, 1f),
                f("feather", "Feather", 0.16f, 0f, 0.8f),
                f("despill", "Despill", 0.65f, 0f, 1f),
                f("edge_refine", "Edge Refine", 0.35f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "am_chroma_key",
            name = "Chroma Key",
            category = EffectCategory.AM_MATTE_MASK_KEY,
            defaultIntensity = 1.0f,
            params = listOf(
                col("key_color", "Key Color", 0xFF00D840L),
                f("threshold", "Threshold", 0.30f, 0.01f, 1f),
                f("feather", "Feather", 0.14f, 0f, 0.8f)
            )
        ),
        EffectCatalogItem(
            id = "am_key_spill_cleaner",
            name = "Key Spill Cleaner",
            category = EffectCategory.AM_MATTE_MASK_KEY,
            defaultIntensity = 0.9f,
            params = listOf(
                col("spill_color", "Spill Color", 0xFF00D840L),
                f("suppression", "Suppression Strength", 0.7f, 0f, 1f),
                f("luma_restore", "Luma Restore", 0.5f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "am_luma_key",
            name = "Luma Key",
            category = EffectCategory.AM_MATTE_MASK_KEY,
            defaultIntensity = 1.0f,
            params = listOf(
                f("low_thresh", "Low Threshold", 0.18f, 0f, 1f),
                f("high_thresh", "High Threshold", 0.95f, 0f, 1f),
                f("feather", "Feather", 0.12f, 0f, 0.6f),
                bool("invert", "Invert", false)
            )
        ),
        EffectCatalogItem(
            id = "am_luma_stamper",
            name = "Luma Stamper",
            category = EffectCategory.AM_MATTE_MASK_KEY,
            defaultIntensity = 0.9f,
            params = listOf(
                f("threshold", "Threshold", 0.55f, 0f, 1f),
                f("decay", "Decay", 0.82f, 0.1f, 0.99f),
                mode("stamp_blend", "Blend Mode", 0, listOf("Screen", "Add", "Normal"))
            )
        ),
        EffectCatalogItem(
            id = "am_matte_choker",
            name = "Matte Choker",
            category = EffectCategory.AM_MATTE_MASK_KEY,
            defaultIntensity = 1.0f,
            params = listOf(
                f("choke", "Choke/Spread", 0.25f, -1f, 1f),
                f("softness", "Softness", 0.2f, 0f, 1f),
                f("passes", "Passes", 2f, 1f, 4f)
            )
        ),
        EffectCatalogItem(
            id = "am_matte_fringe",
            name = "Matte Fringe",
            category = EffectCategory.AM_MATTE_MASK_KEY,
            defaultIntensity = 0.9f,
            params = listOf(
                f("width", "Width", 0.35f, 0.02f, 1f),
                col("fringe_color", "Color", 0xFFFF2A85L),
                f("softness", "Softness", 0.45f, 0f, 1f),
                f("opacity", "Opacity", 0.9f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "am_radial_wipe",
            name = "Radial Wipe",
            category = EffectCategory.AM_MATTE_MASK_KEY,
            defaultIntensity = 1.0f,
            params = listOf(
                f("progress", "Progress %", 0.65f, 0f, 1f, "%"),
                f("start_angle", "Start Angle", 0f, -180f, 180f, "°"),
                f("feather", "Feather", 0.08f, 0f, 0.5f),
                ptX("center_x", "Center X", "center_y", 0.5f),
                ptY("center_y", "Center Y", "center_x", 0.5f)
            )
        ),
        EffectCatalogItem(
            id = "am_solid_matte",
            name = "Solid Matte",
            category = EffectCategory.AM_MATTE_MASK_KEY,
            defaultIntensity = 1.0f,
            params = listOf(
                col("fill_color", "Fill Color", 0xFFFFFFFFL),
                f("blend", "Blend %", 1.0f, 0f, 1f, "%")
            )
        ),
        EffectCatalogItem(
            id = "am_vignette",
            name = "Vignette",
            category = EffectCategory.AM_MATTE_MASK_KEY,
            defaultIntensity = 0.85f,
            params = listOf(
                f("size", "Size", 0.65f, 0.1f, 1.5f),
                f("roundness", "Roundness", 0.8f, 0f, 1f),
                f("feather", "Feather", 0.55f, 0.05f, 1f),
                f("strength", "Strength", 0.85f, 0f, 1f),
                col("vignette_color", "Color", 0xFF000000L)
            )
        ),
        EffectCatalogItem(
            id = "am_wipe",
            name = "Wipe",
            category = EffectCategory.AM_MATTE_MASK_KEY,
            defaultIntensity = 1.0f,
            params = listOf(
                f("start", "Start", 0.0f, 0f, 1f),
                f("end", "End", 0.68f, 0f, 1f),
                f("angle", "Angle", 0f, -180f, 180f, "°"),
                f("feather", "Feather", 0.1f, 0f, 0.6f)
            )
        )
    )

    val opacityVisibilityEffects: List<EffectCatalogItem> = listOf(
        EffectCatalogItem(
            id = "am_blink",
            name = "Blink",
            category = EffectCategory.AM_OPACITY_VISIBILITY,
            defaultIntensity = 1.0f,
            params = listOf(
                f("frequency", "Frequency Hz", 4.0f, 0.2f, 20f, "Hz"),
                f("duty_cycle", "Duty Cycle %", 0.5f, 0.05f, 0.95f, "%"),
                f("phase", "Phase", 0f, 0f, 360f, "°")
            )
        ),
        EffectCatalogItem(
            id = "am_block_dissolve",
            name = "Block Dissolve",
            category = EffectCategory.AM_OPACITY_VISIBILITY,
            defaultIntensity = 1.0f,
            params = listOf(
                f("progress", "Progress %", 0.35f, 0f, 1f, "%"),
                f("block_size", "Block Size", 0.25f, 0.02f, 1f),
                f("feather", "Feather", 0.1f, 0f, 0.5f),
                f("seed", "Seed", 12f, 0f, 99f)
            )
        ),
        EffectCatalogItem(
            id = "am_dissolve",
            name = "Dissolve",
            category = EffectCategory.AM_OPACITY_VISIBILITY,
            defaultIntensity = 1.0f,
            params = listOf(
                f("progress", "Progress %", 0.35f, 0f, 1f, "%"),
                f("grain_size", "Grain Size", 0.25f, 0.05f, 1f),
                f("seed", "Seed", 5f, 0f, 99f)
            )
        ),
        EffectCatalogItem(
            id = "am_fade_in_out",
            name = "Fade In/Out",
            category = EffectCategory.AM_OPACITY_VISIBILITY,
            defaultIntensity = 1.0f,
            params = listOf(
                f("in_dur", "In Duration", 0.25f, 0f, 0.8f),
                f("out_dur", "Out Duration", 0.25f, 0f, 0.8f),
                mode("fade_easing", "Easing", 1, listOf("Linear", "Smooth", "Ease In-Out"))
            )
        ),
        EffectCatalogItem(
            id = "am_feather",
            name = "Feather",
            category = EffectCategory.AM_OPACITY_VISIBILITY,
            defaultIntensity = 1.0f,
            params = listOf(
                f("feather_radius", "Feather Radius", 0.22f, 0f, 0.6f),
                f("corner_roundness", "Corner Roundness", 0.2f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "am_flicker",
            name = "Flicker",
            category = EffectCategory.AM_OPACITY_VISIBILITY,
            defaultIntensity = 0.85f,
            params = listOf(
                f("frequency", "Frequency", 12f, 1f, 40f, "Hz"),
                f("strength", "Strength", 0.55f, 0f, 1f),
                f("seed", "Seed", 9f, 0f, 99f)
            )
        ),
        EffectCatalogItem(
            id = "am_pulse_opacity",
            name = "Pulse Opacity",
            category = EffectCategory.AM_OPACITY_VISIBILITY,
            defaultIntensity = 1.0f,
            params = listOf(
                f("frequency", "Frequency", 2.0f, 0.1f, 12f, "Hz"),
                f("min_opacity", "Min Opacity", 0.25f, 0f, 1f),
                f("max_opacity", "Max Opacity", 1.0f, 0f, 1f),
                f("phase", "Phase", 0f, 0f, 360f, "°")
            )
        )
    )

    val proceduralEffects: List<EffectCatalogItem> = listOf(
        EffectCatalogItem(
            id = "am_block_noise",
            name = "Block Noise",
            category = EffectCategory.AM_PROCEDURAL,
            defaultIntensity = 0.75f,
            params = listOf(
                f("scale", "Scale", 10f, 2f, 40f),
                f("evolution", "Evolution", 0.5f, 0f, 10f),
                bool("colorize", "Colorize", true),
                f("seed", "Seed", 4f, 0f, 99f)
            )
        ),
        EffectCatalogItem(
            id = "am_checker",
            name = "Checker",
            category = EffectCategory.AM_PROCEDURAL,
            defaultIntensity = 0.75f,
            params = listOf(
                f("size", "Size", 8f, 2f, 32f),
                f("angle", "Angle", 0f, -180f, 180f, "°"),
                col("color_1", "Color 1", 0xFFFFFFFFL),
                col("color_2", "Color 2", 0xFF1A1A22L)
            )
        ),
        EffectCatalogItem(
            id = "am_clouds",
            name = "Clouds",
            category = EffectCategory.AM_PROCEDURAL,
            defaultIntensity = 0.7f,
            params = listOf(
                f("scale", "Scale", 4.5f, 0.5f, 16f),
                f("evolution", "Evolution", 0.4f, 0f, 10f),
                f("contrast", "Contrast", 0.6f, 0.1f, 1.5f),
                col("color_1", "Color 1", 0xFFFFFFFFL),
                col("color_2", "Color 2", 0xFF2858B8L)
            )
        ),
        EffectCatalogItem(
            id = "am_contour_strips",
            name = "Contour Strips",
            category = EffectCategory.AM_PROCEDURAL,
            defaultIntensity = 0.8f,
            params = listOf(
                f("spacing", "Spacing", 12f, 2f, 40f),
                f("thickness", "Thickness", 0.25f, 0.05f, 0.9f),
                f("offset", "Offset", 0f, 0f, 10f),
                col("strip_color", "Color", 0xFF00F0FFL)
            )
        ),
        EffectCatalogItem(
            id = "am_dots",
            name = "Dots",
            category = EffectCategory.AM_PROCEDURAL,
            defaultIntensity = 0.75f,
            params = listOf(
                f("spacing", "Spacing", 14f, 3f, 48f),
                f("dot_radius", "Dot Radius", 0.35f, 0.05f, 0.9f),
                f("angle", "Angle", 15f, -180f, 180f, "°"),
                col("dot_color", "Color", 0xFFFF2A85L)
            )
        ),
        EffectCatalogItem(
            id = "am_energy_beam",
            name = "Energy Beam",
            category = EffectCategory.AM_PROCEDURAL,
            defaultIntensity = 0.95f,
            params = listOf(
                ptX("start_x", "Start X", "start_y", 0.15f),
                ptY("start_y", "Start Y", "start_x", 0.5f),
                ptX("end_x", "End X", "end_y", 0.85f),
                ptY("end_y", "End Y", "end_x", 0.5f),
                f("thickness", "Thickness", 0.12f, 0.02f, 0.6f),
                f("glow_radius", "Glow Radius", 0.45f, 0.05f, 1f),
                col("core_color", "Core Color", 0xFFFFFFFFL),
                col("outer_color", "Outer Color", 0xFF00E5FFL)
            )
        ),
        EffectCatalogItem(
            id = "am_fractal_ridges",
            name = "Fractal Ridges",
            category = EffectCategory.AM_PROCEDURAL,
            defaultIntensity = 0.8f,
            params = listOf(
                f("scale", "Scale", 4.0f, 0.5f, 16f),
                f("evolution", "Evolution", 0.5f, 0f, 10f),
                f("sharpness", "Sharpness", 0.7f, 0.1f, 1.5f),
                col("ridge_color", "Color", 0xFF8C52FFL)
            )
        ),
        EffectCatalogItem(
            id = "am_grid",
            name = "Grid",
            category = EffectCategory.AM_PROCEDURAL,
            defaultIntensity = 0.75f,
            params = listOf(
                f("spacing", "Spacing", 10f, 2f, 40f),
                f("line_width", "Line Width", 0.08f, 0.01f, 0.4f),
                col("grid_color", "Color", 0xFFFFFFFFL),
                f("opacity", "Opacity", 0.85f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "am_grunge",
            name = "Grunge",
            category = EffectCategory.AM_PROCEDURAL,
            defaultIntensity = 0.7f,
            params = listOf(
                f("scale", "Scale", 5.0f, 1f, 20f),
                f("grunge_int", "Intensity", 0.65f, 0f, 1f),
                f("seed", "Seed", 19f, 0f, 99f),
                f("contrast", "Contrast", 0.7f, 0.1f, 1.5f)
            )
        ),
        EffectCatalogItem(
            id = "am_hex_array",
            name = "Hexagon Array",
            category = EffectCategory.AM_PROCEDURAL,
            defaultIntensity = 0.8f,
            params = listOf(
                f("radius", "Radius", 8f, 2f, 30f),
                f("gap", "Gap", 0.14f, 0.02f, 0.5f),
                col("fill_color", "Fill Color", 0xFF202540L),
                col("stroke_color", "Stroke Color", 0xFF00F0FFL)
            )
        ),
        EffectCatalogItem(
            id = "am_iridescence",
            name = "Iridescence",
            category = EffectCategory.AM_PROCEDURAL,
            defaultIntensity = 0.72f,
            params = listOf(
                f("frequency", "Frequency", 2.5f, 0.2f, 10f),
                f("phase", "Phase", 0f, 0f, 360f, "°"),
                f("saturation", "Saturation", 0.85f, 0f, 1f),
                f("angle", "Angle", 35f, -180f, 180f, "°")
            )
        ),
        EffectCatalogItem(
            id = "am_lens_flare",
            name = "Lens Flare",
            category = EffectCategory.AM_PROCEDURAL,
            defaultIntensity = 0.9f,
            params = listOf(
                ptX("pos_x", "Position X", "pos_y", 0.35f),
                ptY("pos_y", "Position Y", "pos_x", 0.30f),
                f("brightness", "Brightness", 0.85f, 0f, 2f),
                f("scale", "Scale", 0.65f, 0.1f, 2f),
                col("flare_tint", "Color Tint", 0xFFFFC870L),
                f("rays", "Rays", 8f, 2f, 24f)
            )
        ),
        EffectCatalogItem(
            id = "am_lightning",
            name = "Lightning",
            category = EffectCategory.AM_PROCEDURAL,
            defaultIntensity = 0.95f,
            params = listOf(
                ptX("start_x", "Start X", "start_y", 0.2f),
                ptY("start_y", "Start Y", "start_x", 0.15f),
                ptX("end_x", "End X", "end_y", 0.8f),
                ptY("end_y", "End Y", "end_x", 0.85f),
                f("amplitude", "Amplitude", 0.45f, 0f, 1f),
                f("branches", "Branches", 3f, 0f, 8f),
                f("seed", "Seed", 11f, 0f, 99f),
                f("glow", "Glow", 0.6f, 0f, 1f),
                col("bolt_color", "Color", 0xFF7CE8FFL)
            )
        ),
        EffectCatalogItem(
            id = "am_noise",
            name = "Noise",
            category = EffectCategory.AM_PROCEDURAL,
            defaultIntensity = 0.55f,
            params = listOf(
                f("strength", "Strength", 0.45f, 0f, 1f),
                bool("monochrome", "Monochrome", true),
                bool("dynamic", "Dynamic/Animated", true)
            )
        ),
        EffectCatalogItem(
            id = "am_omino_diffusion",
            name = "Omino Diffusion+",
            category = EffectCategory.AM_PROCEDURAL,
            defaultIntensity = 0.8f,
            params = listOf(
                f("scale", "Scale", 6f, 1f, 24f),
                f("threshold", "Threshold", 0.5f, 0.05f, 0.95f),
                f("evolution", "Evolution", 0.5f, 0f, 10f)
            )
        ),
        EffectCatalogItem(
            id = "am_radial_rays",
            name = "Radial Rays",
            category = EffectCategory.AM_PROCEDURAL,
            defaultIntensity = 0.78f,
            params = listOf(
                f("count", "Count", 12f, 2f, 36f),
                f("angle", "Angle", 0f, -180f, 180f, "°"),
                f("twist", "Twist", 0.2f, -2f, 2f),
                col("color_1", "Color 1", 0xFFFFD166L),
                col("color_2", "Color 2", 0xFF1A1030L)
            )
        ),
        EffectCatalogItem(
            id = "am_ribbon",
            name = "Ribbon",
            category = EffectCategory.AM_PROCEDURAL,
            defaultIntensity = 0.85f,
            params = listOf(
                f("amplitude", "Amplitude", 0.25f, 0f, 0.8f),
                f("frequency", "Frequency", 2.5f, 0.3f, 10f),
                f("thickness", "Thickness", 0.14f, 0.02f, 0.5f),
                f("phase", "Phase", 0f, 0f, 360f, "°"),
                col("ribbon_color", "Color", 0xFFFF4090L)
            )
        ),
        EffectCatalogItem(
            id = "am_simple_starfield",
            name = "Simple Starfield",
            category = EffectCategory.AM_PROCEDURAL,
            defaultIntensity = 0.9f,
            params = listOf(
                f("density", "Density", 0.65f, 0.1f, 1f),
                f("speed", "Speed", 0.45f, 0f, 2f),
                f("size", "Size", 0.45f, 0.1f, 1f),
                f("seed", "Seed", 17f, 0f, 99f)
            )
        ),
        EffectCatalogItem(
            id = "am_star",
            name = "Star",
            category = EffectCategory.AM_PROCEDURAL,
            defaultIntensity = 0.9f,
            params = listOf(
                f("points", "Points", 5f, 3f, 16f),
                f("inner_radius", "Inner Radius", 0.16f, 0.02f, 0.5f),
                f("outer_radius", "Outer Radius", 0.38f, 0.08f, 0.8f),
                f("angle", "Angle", 0f, -180f, 180f, "°"),
                f("glow", "Glow", 0.45f, 0f, 1f),
                col("star_color", "Color", 0xFFFFE066L)
            )
        ),
        EffectCatalogItem(
            id = "am_starfield",
            name = "Starfield",
            category = EffectCategory.AM_PROCEDURAL,
            defaultIntensity = 0.92f,
            params = listOf(
                f("speed_z", "Speed Z", 0.65f, 0f, 3f),
                f("density", "Density", 0.7f, 0.1f, 1f),
                f("star_size", "Star Size", 0.5f, 0.1f, 1f),
                f("color_var", "Color Variation", 0.5f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "am_stripes",
            name = "Stripes",
            category = EffectCategory.AM_PROCEDURAL,
            defaultIntensity = 0.75f,
            params = listOf(
                f("count", "Count/Width", 10f, 1f, 40f),
                f("angle", "Angle", 45f, -180f, 180f, "°"),
                f("phase", "Phase", 0f, 0f, 360f, "°"),
                col("color_1", "Color 1", 0xFFFFFFFFL),
                col("color_2", "Color 2", 0xFF141418L)
            )
        ),
        EffectCatalogItem(
            id = "am_turbulence",
            name = "Turbulence",
            category = EffectCategory.AM_PROCEDURAL,
            defaultIntensity = 0.75f,
            params = listOf(
                f("scale", "Scale", 4.0f, 0.5f, 16f),
                f("evolution", "Evolution", 0.6f, 0f, 10f),
                f("octaves", "Octaves", 4f, 1f, 6f),
                f("brightness", "Brightness", 0.85f, 0f, 2f)
            )
        ),
        EffectCatalogItem(
            id = "am_voronoi_cells",
            name = "Voronoi Cells",
            category = EffectCategory.AM_PROCEDURAL,
            defaultIntensity = 0.8f,
            params = listOf(
                f("scale", "Scale", 6f, 1f, 24f),
                f("evolution", "Evolution", 0.5f, 0f, 10f),
                f("border_width", "Border Width", 0.15f, 0f, 0.6f),
                bool("colorize", "Colorize", true)
            )
        )
    )

    val repeatEffects: List<EffectCatalogItem> = listOf(
        EffectCatalogItem(
            id = "am_grid_repeat",
            name = "Grid Repeat",
            category = EffectCategory.AM_REPEAT,
            defaultIntensity = 1.0f,
            params = listOf(
                f("rows", "Rows", 3f, 1f, 6f),
                f("columns", "Columns", 3f, 1f, 6f),
                f("spacing_x", "Spacing X", 0.32f, 0.05f, 1f),
                f("spacing_y", "Spacing Y", 0.32f, 0.05f, 1f),
                f("opacity_falloff", "Opacity Falloff", 0.12f, 0f, 0.8f)
            )
        ),
        EffectCatalogItem(
            id = "am_linear_repeat",
            name = "Linear Repeat",
            category = EffectCategory.AM_REPEAT,
            defaultIntensity = 1.0f,
            params = listOf(
                f("count", "Count", 5f, 1f, 16f),
                f("offset_x", "Offset X", 0.12f, -0.5f, 0.5f),
                f("offset_y", "Offset Y", -0.08f, -0.5f, 0.5f),
                f("angle_delta", "Angle Delta", 8f, -45f, 45f, "°"),
                f("scale_delta", "Scale Delta", 0.9f, 0.5f, 1.3f),
                f("alpha_falloff", "Alpha Falloff", 0.18f, 0f, 0.8f)
            )
        ),
        EffectCatalogItem(
            id = "am_radial_repeat",
            name = "Radial Repeat",
            category = EffectCategory.AM_REPEAT,
            defaultIntensity = 1.0f,
            params = listOf(
                f("count", "Count", 8f, 2f, 18f),
                f("radius", "Radius", 0.28f, 0f, 0.7f),
                f("start_angle", "Start Angle", 0f, -180f, 180f, "°"),
                f("sweep_angle", "Sweep Angle", 360f, 30f, 360f, "°"),
                bool("orient_center", "Orient to Center", true)
            )
        ),
        EffectCatalogItem(
            id = "am_repeat",
            name = "Repeat",
            category = EffectCategory.AM_REPEAT,
            defaultIntensity = 1.0f,
            params = listOf(
                f("count", "Count", 4f, 1f, 12f),
                f("offset_x", "Offset X", 0.08f, -0.5f, 0.5f),
                f("offset_y", "Offset Y", 0.08f, -0.5f, 0.5f),
                f("scale", "Scale", 0.88f, 0.4f, 1.4f),
                f("rotation", "Rotation", 12f, -90f, 90f, "°")
            )
        ),
        EffectCatalogItem(
            id = "am_repeat_along_path",
            name = "Repeat Along Path",
            category = EffectCategory.AM_REPEAT,
            defaultIntensity = 1.0f,
            params = listOf(
                f("count", "Count", 7f, 2f, 16f),
                f("start_pct", "Start %", 0.05f, 0f, 1f, "%"),
                f("end_pct", "End %", 0.95f, 0f, 1f, "%"),
                bool("align_path", "Align to Path", true)
            )
        ),
        EffectCatalogItem(
            id = "am_scatter_field",
            name = "Scatter Field",
            category = EffectCategory.AM_REPEAT,
            defaultIntensity = 1.0f,
            params = listOf(
                f("count", "Count", 10f, 2f, 20f),
                f("seed", "Seed", 14f, 0f, 99f),
                f("scale_var", "Scale Variance", 0.45f, 0f, 1f),
                f("rot_var", "Rotation Variance", 0.65f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "am_scatter_repeat",
            name = "Scatter Repeat",
            category = EffectCategory.AM_REPEAT,
            defaultIntensity = 1.0f,
            params = listOf(
                f("count", "Count", 9f, 2f, 18f),
                f("spread_radius", "Spread Radius", 0.35f, 0.05f, 0.8f),
                f("seed", "Seed", 22f, 0f, 99f),
                f("scale_decay", "Scale Decay", 0.22f, 0f, 0.8f)
            )
        )
    )

    val threeDEffects: List<EffectCatalogItem> = listOf(
        EffectCatalogItem(
            id = "am_3d_box",
            name = "Box",
            category = EffectCategory.AM_THREE_D,
            defaultIntensity = 1.0f,
            params = listOf(
                f("width", "Width", 0.45f, 0.1f, 0.9f),
                f("height", "Height", 0.45f, 0.1f, 0.9f),
                f("depth", "Depth", 0.35f, 0.05f, 0.9f),
                f("rot_x", "Rotation X", 20f, -180f, 180f, "°"),
                f("rot_y", "Rotation Y", 30f, -180f, 180f, "°"),
                f("rot_z", "Rotation Z", 0f, -180f, 180f, "°"),
                f("fov", "Perspective FOV", 45f, 15f, 90f, "°")
            )
        ),
        EffectCatalogItem(
            id = "am_3d_cube",
            name = "Cube",
            category = EffectCategory.AM_THREE_D,
            defaultIntensity = 1.0f,
            params = listOf(
                f("size", "Size", 0.42f, 0.1f, 0.85f),
                f("rot_x", "Rotation X", 25f, -180f, 180f, "°"),
                f("rot_y", "Rotation Y", 35f, -180f, 180f, "°"),
                f("rot_z", "Rotation Z", 0f, -180f, 180f, "°"),
                f("light_angle", "Light Angle", 45f, -180f, 180f, "°")
            )
        ),
        EffectCatalogItem(
            id = "am_3d_cylinder",
            name = "Cylinder",
            category = EffectCategory.AM_THREE_D,
            defaultIntensity = 1.0f,
            params = listOf(
                f("radius", "Radius", 0.35f, 0.1f, 0.7f),
                f("height", "Height", 0.6f, 0.1f, 1.0f),
                f("rot_x", "Rotation X", 15f, -180f, 180f, "°"),
                f("rot_y", "Rotation Y", 25f, -180f, 180f, "°"),
                f("rot_z", "Rotation Z", 0f, -180f, 180f, "°"),
                mode("cap_mode", "Cap Mode", 0, listOf("Textured", "Shaded", "Open"))
            )
        ),
        EffectCatalogItem(
            id = "am_3d_ellipsoid",
            name = "Ellipsoid",
            category = EffectCategory.AM_THREE_D,
            defaultIntensity = 1.0f,
            params = listOf(
                f("radius_x", "Radius X", 0.45f, 0.1f, 0.8f),
                f("radius_y", "Radius Y", 0.35f, 0.1f, 0.8f),
                f("radius_z", "Radius Z", 0.40f, 0.1f, 0.8f),
                f("rot_x", "Rotation X", 15f, -180f, 180f, "°"),
                f("rot_y", "Rotation Y", 25f, -180f, 180f, "°"),
                f("rot_z", "Rotation Z", 0f, -180f, 180f, "°")
            )
        ),
        EffectCatalogItem(
            id = "am_3d_flip_layer",
            name = "Flip Layer",
            category = EffectCategory.AM_THREE_D,
            defaultIntensity = 1.0f,
            params = listOf(
                f("flip_angle", "Flip Angle", 32f, -180f, 180f, "°"),
                mode("axis", "Axis", 0, listOf("Horizontal", "Vertical")),
                f("perspective", "Perspective", 0.55f, 0.05f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "am_3d_heart",
            name = "Heart",
            category = EffectCategory.AM_THREE_D,
            defaultIntensity = 1.0f,
            params = listOf(
                f("depth", "Depth", 0.25f, 0.05f, 0.7f),
                f("bevel", "Bevel", 0.25f, 0f, 0.8f),
                f("rot_x", "Rotation X", 12f, -180f, 180f, "°"),
                f("rot_y", "Rotation Y", 24f, -180f, 180f, "°"),
                f("rot_z", "Rotation Z", 0f, -180f, 180f, "°")
            )
        ),
        EffectCatalogItem(
            id = "am_3d_hex_prism",
            name = "Hexagonal Prism",
            category = EffectCategory.AM_THREE_D,
            defaultIntensity = 1.0f,
            params = listOf(
                f("radius", "Radius", 0.38f, 0.1f, 0.75f),
                f("depth", "Depth", 0.45f, 0.05f, 0.9f),
                f("rot_x", "Rotation X", 20f, -180f, 180f, "°"),
                f("rot_y", "Rotation Y", 30f, -180f, 180f, "°"),
                f("rot_z", "Rotation Z", 0f, -180f, 180f, "°")
            )
        ),
        EffectCatalogItem(
            id = "am_3d_hollow_box",
            name = "Hollow Box",
            category = EffectCategory.AM_THREE_D,
            defaultIntensity = 1.0f,
            params = listOf(
                f("size", "Size", 0.44f, 0.1f, 0.85f),
                f("wall_thickness", "Wall Thickness", 0.14f, 0.03f, 0.4f),
                f("rot_x", "Rotation X", 22f, -180f, 180f, "°"),
                f("rot_y", "Rotation Y", 32f, -180f, 180f, "°"),
                f("rot_z", "Rotation Z", 0f, -180f, 180f, "°")
            )
        ),
        EffectCatalogItem(
            id = "am_3d_octahedron",
            name = "Octahedron",
            category = EffectCategory.AM_THREE_D,
            defaultIntensity = 1.0f,
            params = listOf(
                f("size", "Size", 0.44f, 0.1f, 0.85f),
                f("rot_x", "Rotation X", 22f, -180f, 180f, "°"),
                f("rot_y", "Rotation Y", 28f, -180f, 180f, "°"),
                f("rot_z", "Rotation Z", 0f, -180f, 180f, "°")
            )
        ),
        EffectCatalogItem(
            id = "am_3d_page_curl",
            name = "Page Curl",
            category = EffectCategory.AM_THREE_D,
            defaultIntensity = 1.0f,
            params = listOf(
                f("curl_angle", "Curl Angle", 35f, -180f, 180f, "°"),
                f("curl_radius", "Curl Radius", 0.22f, 0.05f, 0.6f),
                f("progress", "Progress %", 0.45f, 0f, 1f, "%"),
                f("back_opacity", "Backside Opacity", 0.85f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "am_3d_pyramid",
            name = "Pyramid",
            category = EffectCategory.AM_THREE_D,
            defaultIntensity = 1.0f,
            params = listOf(
                f("base_size", "Base Size", 0.45f, 0.1f, 0.85f),
                f("height", "Height", 0.55f, 0.1f, 0.9f),
                f("rot_x", "Rotation X", 20f, -180f, 180f, "°"),
                f("rot_y", "Rotation Y", 30f, -180f, 180f, "°"),
                f("rot_z", "Rotation Z", 0f, -180f, 180f, "°")
            )
        ),
        EffectCatalogItem(
            id = "am_3d_raster_extrude",
            name = "Raster Extrude",
            category = EffectCategory.AM_THREE_D,
            defaultIntensity = 1.0f,
            params = listOf(
                f("extrude_depth", "Extrude Depth", 0.25f, 0.02f, 0.7f),
                f("steps", "Steps", 16f, 4f, 32f),
                f("rot_x", "Rotation X", 16f, -60f, 60f, "°"),
                f("rot_y", "Rotation Y", 24f, -60f, 60f, "°"),
                f("rot_z", "Rotation Z", 0f, -180f, 180f, "°"),
                f("shading", "Shading", 0.7f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "am_3d_ring",
            name = "Ring",
            category = EffectCategory.AM_THREE_D,
            defaultIntensity = 1.0f,
            params = listOf(
                f("outer_radius", "Outer Radius", 0.44f, 0.15f, 0.8f),
                f("inner_radius", "Inner Radius", 0.26f, 0.05f, 0.7f),
                f("height", "Height", 0.28f, 0.05f, 0.7f),
                f("rot_x", "Rotation X", 25f, -180f, 180f, "°"),
                f("rot_y", "Rotation Y", 20f, -180f, 180f, "°"),
                f("rot_z", "Rotation Z", 0f, -180f, 180f, "°")
            )
        ),
        EffectCatalogItem(
            id = "am_3d_spherize",
            name = "Spherize",
            category = EffectCategory.AM_THREE_D,
            defaultIntensity = 1.0f,
            params = listOf(
                f("radius", "Radius", 0.45f, 0.1f, 0.9f),
                f("rot_x", "Rotation X", 10f, -180f, 180f, "°"),
                f("rot_y", "Rotation Y", 25f, -180f, 180f, "°"),
                f("rot_z", "Rotation Z", 0f, -180f, 180f, "°"),
                f("strength", "Strength", 1.0f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "am_3d_star_polyhedron",
            name = "Star Polyhedron",
            category = EffectCategory.AM_THREE_D,
            defaultIntensity = 1.0f,
            params = listOf(
                f("points", "Points", 5f, 3f, 10f),
                f("depth", "Depth", 0.35f, 0.05f, 0.8f),
                f("rot_x", "Rotation X", 20f, -180f, 180f, "°"),
                f("rot_y", "Rotation Y", 30f, -180f, 180f, "°"),
                f("rot_z", "Rotation Z", 0f, -180f, 180f, "°")
            )
        ),
        EffectCatalogItem(
            id = "am_3d_star_prism",
            name = "Star Prism",
            category = EffectCategory.AM_THREE_D,
            defaultIntensity = 1.0f,
            params = listOf(
                f("outer_radius", "Outer Radius", 0.45f, 0.15f, 0.8f),
                f("inner_radius", "Inner Radius", 0.22f, 0.05f, 0.6f),
                f("extrude_depth", "Extrude Depth", 0.30f, 0.05f, 0.8f),
                f("rot_x", "Rotation X", 20f, -180f, 180f, "°"),
                f("rot_y", "Rotation Y", 28f, -180f, 180f, "°"),
                f("rot_z", "Rotation Z", 0f, -180f, 180f, "°")
            )
        ),
        EffectCatalogItem(
            id = "am_3d_three_axis_cross",
            name = "Three-axis Cross",
            category = EffectCategory.AM_THREE_D,
            defaultIntensity = 1.0f,
            params = listOf(
                f("length", "Length", 0.45f, 0.15f, 0.85f),
                f("thickness", "Thickness", 0.12f, 0.03f, 0.35f),
                f("rot_x", "Rotation X", 25f, -180f, 180f, "°"),
                f("rot_y", "Rotation Y", 35f, -180f, 180f, "°"),
                f("rot_z", "Rotation Z", 0f, -180f, 180f, "°")
            )
        ),
        EffectCatalogItem(
            id = "am_3d_torus",
            name = "Torus",
            category = EffectCategory.AM_THREE_D,
            defaultIntensity = 1.0f,
            params = listOf(
                f("major_radius", "Major Radius", 0.34f, 0.1f, 0.7f),
                f("minor_radius", "Minor Radius", 0.13f, 0.03f, 0.35f),
                f("rot_x", "Rotation X", 32f, -180f, 180f, "°"),
                f("rot_y", "Rotation Y", 22f, -180f, 180f, "°"),
                f("rot_z", "Rotation Z", 0f, -180f, 180f, "°")
            )
        )
    )

    val textEffects: List<EffectCatalogItem> = listOf(
        EffectCatalogItem(
            id = "am_text_count",
            name = "Count Up/Down",
            category = EffectCategory.AM_TEXT,
            defaultIntensity = 1.0f,
            params = listOf(
                f("start_val", "Start Value", 0f, -9999f, 9999f),
                f("end_val", "End Value", 1000f, -9999f, 99999f),
                f("decimals", "Decimals", 0f, 0f, 4f),
                bool("comma_sep", "Comma Separator", true),
                txt("prefix", "Prefix", ""),
                txt("suffix", "Suffix", "%")
            )
        ),
        EffectCatalogItem(
            id = "am_text_progress",
            name = "Text Progress",
            category = EffectCategory.AM_TEXT,
            defaultIntensity = 1.0f,
            params = listOf(
                f("start_pct", "Start %", 0f, 0f, 1f, "%"),
                f("end_pct", "End %", 1f, 0f, 1f, "%"),
                txt("cursor_char", "Cursor Character", "|"),
                f("blink_speed", "Blink Speed", 2.0f, 0f, 10f, "Hz")
            )
        ),
        EffectCatalogItem(
            id = "am_text_randomizer",
            name = "Text Randomizer",
            category = EffectCategory.AM_TEXT,
            defaultIntensity = 1.0f,
            params = listOf(
                f("progress", "Progress %", 0.6f, 0f, 1f, "%"),
                mode("char_set", "Character Set", 0, listOf("Alpha", "Numeric", "Symbols")),
                f("seed", "Seed", 7f, 0f, 99f)
            )
        ),
        EffectCatalogItem(
            id = "am_text_spacing",
            name = "Text Spacing",
            category = EffectCategory.AM_TEXT,
            defaultIntensity = 1.0f,
            params = listOf(
                f("letter_spacing", "Letter Spacing", 0.15f, -0.3f, 1f),
                f("line_spacing", "Line Spacing", 1.2f, 0.5f, 3f),
                mode("alignment", "Anchor Alignment", 1, listOf("Left", "Center", "Right"))
            )
        ),
        EffectCatalogItem(
            id = "am_text_transform",
            name = "Text Transform",
            category = EffectCategory.AM_TEXT,
            defaultIntensity = 1.0f,
            params = listOf(
                f("range_start", "Range Start %", 0f, 0f, 1f, "%"),
                f("range_end", "Range End %", 1f, 0f, 1f, "%"),
                f("offset_x", "Offset X", 0f, -0.5f, 0.5f),
                f("offset_y", "Offset Y", -0.15f, -0.5f, 0.5f),
                f("scale", "Scale", 1.25f, 0.1f, 3f, "x"),
                f("rotation", "Rotation", 18f, -180f, 180f, "°"),
                f("overlap", "Overlap", 0.5f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "am_text_timecode",
            name = "Timecode",
            category = EffectCategory.AM_TEXT,
            defaultIntensity = 1.0f,
            params = listOf(
                mode("tc_format", "Format", 0, listOf("HH:MM:SS:FF", "MM:SS.ms")),
                f("offset_sec", "Offset", 0f, -60f, 60f, "s"),
                f("frame_rate", "Frame Rate", 30f, 12f, 60f, "fps")
            )
        )
    )

    val otherEffects: List<EffectCatalogItem> = listOf(
        EffectCatalogItem(
            id = "am_channel_remap_hsv",
            name = "Channel Remap (HSV)",
            category = EffectCategory.AM_OTHER,
            defaultIntensity = 1.0f,
            params = listOf(
                mode("hue_src", "Hue Source", 0, listOf("H", "S", "V", "0", "1")),
                mode("sat_src", "Sat Source", 1, listOf("H", "S", "V", "0", "1")),
                mode("val_src", "Val Source", 2, listOf("H", "S", "V", "0", "1"))
            )
        ),
        EffectCatalogItem(
            id = "am_channel_remap_rgb",
            name = "Channel Remap (RGB)",
            category = EffectCategory.AM_OTHER,
            defaultIntensity = 1.0f,
            params = listOf(
                mode("r_src", "R Source", 0, listOf("R", "G", "B", "A", "0", "1")),
                mode("g_src", "G Source", 1, listOf("R", "G", "B", "A", "0", "1")),
                mode("b_src", "B Source", 2, listOf("R", "G", "B", "A", "0", "1")),
                mode("a_src", "A Source", 3, listOf("R", "G", "B", "A", "0", "1"))
            )
        ),
        EffectCatalogItem(
            id = "am_copy_background",
            name = "Copy Background",
            category = EffectCategory.AM_OTHER,
            defaultIntensity = 1.0f,
            params = listOf(
                f("opacity", "Opacity", 1.0f, 0f, 1f),
                f("scale", "Scale", 1.0f, 0.2f, 3f, "x")
            )
        ),
        EffectCatalogItem(
            id = "am_echo_keyframes",
            name = "Echo Keyframes",
            category = EffectCategory.AM_OTHER,
            defaultIntensity = 0.85f,
            params = listOf(
                f("echo_offset", "Echo Time Offset", 0.08f, 0.01f, 0.5f, "s"),
                f("echo_count", "Number of Echoes", 4f, 1f, 10f),
                f("start_intensity", "Starting Intensity", 0.7f, 0.05f, 1f),
                f("decay", "Decay", 0.65f, 0.1f, 0.95f),
                mode("echo_blend", "Blend Mode", 1, listOf("Normal", "Add", "Screen"))
            )
        ),
        EffectCatalogItem(
            id = "am_fill_behind",
            name = "Fill Behind",
            category = EffectCategory.AM_OTHER,
            defaultIntensity = 1.0f,
            params = listOf(
                col("fill_color", "Fill Color", 0xFF1A102EL),
                f("opacity", "Opacity", 1.0f, 0f, 1f)
            )
        ),
        EffectCatalogItem(
            id = "am_magnify_background",
            name = "Magnify Background",
            category = EffectCategory.AM_OTHER,
            defaultIntensity = 1.0f,
            params = listOf(
                ptX("center_x", "Center X", "center_y", 0.5f),
                ptY("center_y", "Center Y", "center_x", 0.5f),
                f("magnification", "Magnification", 1.85f, 0.5f, 4f, "x"),
                f("radius", "Radius", 0.34f, 0.08f, 0.8f),
                f("feather", "Feather", 0.12f, 0f, 0.6f),
                f("border", "Border", 0.04f, 0f, 0.2f)
            )
        ),
        EffectCatalogItem(
            id = "am_parenting_helper",
            name = "Parenting Helper",
            category = EffectCategory.AM_OTHER,
            defaultIntensity = 1.0f,
            params = listOf(
                f("follow_pos", "Follow Position %", 1.0f, 0f, 2f, "%"),
                f("follow_scale", "Follow Scale %", 1.0f, 0f, 2f, "%"),
                f("follow_rot", "Follow Rotation %", 1.0f, 0f, 2f, "%"),
                f("delay_sec", "Delay/Lag sec", 0.1f, 0f, 1f, "s")
            )
        ),
        EffectCatalogItem(
            id = "am_time_quantization",
            name = "Time Quantization",
            category = EffectCategory.AM_OTHER,
            defaultIntensity = 1.0f,
            params = listOf(
                f("target_fps", "Target FPS", 8f, 1f, 30f, "fps"),
                f("phase_offset", "Phase Offset", 0f, 0f, 1f)
            )
        )
    )

    val effects: List<EffectCatalogItem> = buildList {
        addAll(moveTransformEffects)
        addAll(distortionWarpEffects)
        addAll(matteMaskKeyEffects)
        addAll(opacityVisibilityEffects)
        addAll(proceduralEffects)
        addAll(repeatEffects)
        addAll(threeDEffects)
        addAll(textEffects)
        addAll(otherEffects)
    }
}
