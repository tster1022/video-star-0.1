package com.example.engine.packs

import com.example.engine.effects.AlightMotionEffectRegistry
import com.example.model.BezierCurveSpec
import com.example.model.EffectBlendMode
import com.example.model.EffectCatalog
import com.example.model.EffectCatalogItem
import com.example.model.EffectCategory
import com.example.model.EffectParamType
import com.example.model.KeyframeEasing
import com.example.model.PARAM_KEY_INTENSITY
import com.example.model.ParameterKeyframe
import com.example.model.ShaderEffectPass
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.UUID
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs

/**
 * Streaming parser for Alight Motion / Gold Motion XML presets and effect descriptors.
 * Extracts `<effect>` tags, `<property>` parameters, colors, and `<kf>` keyframes,
 * binding each effect to an imported custom GLSL shader (when present) or the closest
 * native Star Studio / Alight Motion shader pass.
 */
object AmXmlEffectParser {

    data class ParseOutput(
        val presets: List<ImportedXmlEffectPreset>,
        val matchedEffectCount: Int,
        val unresolvedIds: List<String>
    )

    private data class ParsedKf(
        val timeMs: Float,
        val rawValue: String,
        val easingRaw: String
    )

    private data class ParsedProperty(
        val name: String,
        val type: String,
        val rawValue: String,
        val keyframes: MutableList<ParsedKf> = mutableListOf()
    )

    private data class ParsedEffectBlock(
        val rawId: String,
        val label: String,
        val enabled: Boolean,
        val blendMode: String,
        val properties: MutableList<ParsedProperty> = mutableListOf()
    )

    private val exactAmIdMap: Map<String, String> = mapOf(
        // Move & Transform
        "com.alightcreative.effects.oscillate" to "am_oscillate",
        "com.alightcreative.effects.oscillate2" to "am_oscillate",
        "com.alightcreative.effects.swing" to "am_swing",
        "com.alightcreative.effects.swing2" to "am_swing",
        "com.alightcreative.effects.spin" to "am_spin",
        "com.alightcreative.effects.shake" to "am_auto_shake",
        "com.alightcreative.effects.autoshake" to "am_auto_shake",
        "com.alightcreative.effects.randomdisplace" to "am_random_displacement",
        "com.alightcreative.effects.randomjitter" to "am_random_jitter",
        "com.alightcreative.effects.pulsesize" to "am_pulse_size",
        "com.alightcreative.effects.movealongpath" to "am_move_along_path",
        "com.alightcreative.effects.offset" to "am_offset",
        "com.alightcreative.effects.stretchsegment" to "am_stretch_segment",
        "com.alightcreative.effects.stretchaxis" to "am_stretch_axis",
        "com.alightcreative.effects.scaleassist" to "am_scale_assist",
        // Distortion & Warp + Tiles
        "com.alightcreative.effects.tiles" to "am_tiles",
        "com.alightcreative.effects.tile" to "am_tiles",
        "com.alightcreative.effects.tile2" to "am_tiles",
        "com.alightcreative.effects.tilerotate" to "am_tile_rotate",
        "com.alightcreative.effects.tileshift" to "am_tile_shift",
        "com.alightcreative.effects.hextileshift" to "am_hex_tile_shift",
        "com.alightcreative.effects.hextiling" to "am_hex_tiling",
        "com.alightcreative.effects.wavewarp" to "am_wave_warp",
        "com.alightcreative.effects.wavewarp2" to "am_wave_warp",
        "com.alightcreative.effects.turbulentdisplace" to "am_turbulent_displace",
        "com.alightcreative.effects.fractalwarp" to "am_fractal_warp",
        "com.alightcreative.effects.pinchbulge" to "am_pinch_bulge",
        "com.alightcreative.effects.swirl" to "am_swirl",
        "com.alightcreative.effects.ripple" to "ripple",
        "com.alightcreative.effects.circularripple" to "ripple",
        "com.alightcreative.effects.squeeze" to "am_squeeze",
        "com.alightcreative.effects.polarcoords" to "am_polar_coords",
        "com.alightcreative.effects.polardisplace" to "am_polar_displace",
        "com.alightcreative.effects.displacementmap" to "am_turbulent_displace",
        "com.alightcreative.effects.mirror" to "am_mirror",
        "com.alightcreative.effects.kaleidoscope" to "am_kaleidoscope",
        "com.alightcreative.effects.stretch" to "am_stretch_segment",
        // Blur & Focus
        "com.alightcreative.effects.gaussianblur" to "gaussian_blur",
        "com.alightcreative.effects.motionblur" to "dir_motion_blur",
        "com.alightcreative.effects.motionblur2" to "dir_motion_blur",
        "com.alightcreative.effects.zoomblur" to "radial_zoom_blur",
        "com.alightcreative.effects.lensblur" to "lens_bokeh",
        "com.alightcreative.effects.sharpen" to "sharpen_clarity",
        // Color & Glow
        "com.alightcreative.effects.exposure" to "exposure_flash",
        "com.alightcreative.effects.exposuregamma" to "exposure_flash",
        "com.alightcreative.effects.glow" to "glow_bloom",
        "com.alightcreative.effects.bloom" to "glow_bloom",
        "com.alightcreative.effects.hueshift" to "hue_shift",
        "com.alightcreative.effects.colorize" to "duotone",
        "com.alightcreative.effects.brightnesscontrast" to "color_adjust",
        "com.alightcreative.effects.saturationvibrance" to "color_adjust",
        "com.alightcreative.effects.invert" to "invert",
        "com.alightcreative.effects.vignette" to "vignette",
        "com.alightcreative.effects.rgbshift" to "rgb_split",
        "com.alightcreative.effects.chromashift" to "rgb_split",
        "com.alightcreative.effects.chromaticaberration" to "rgb_split",
        "com.alightcreative.effects.channelremap" to "am_channel_remap_rgb",
        "com.alightcreative.effects.channelremaphsv" to "am_channel_remap_hsv",
        // Matte / Mask / Procedural / Repeat / 3D / Other
        "com.alightcreative.effects.chromakey" to "am_chroma_key",
        "com.alightcreative.effects.lumakey" to "am_luma_key",
        "com.alightcreative.effects.wipe" to "am_wipe",
        "com.alightcreative.effects.radialwipe" to "am_radial_wipe",
        "com.alightcreative.effects.fade" to "am_fade_in_out",
        "com.alightcreative.effects.blink" to "am_blink",
        "com.alightcreative.effects.flicker" to "am_flicker",
        "com.alightcreative.effects.grid" to "am_grid",
        "com.alightcreative.effects.checkerboard" to "am_checker",
        "com.alightcreative.effects.gradient" to "duotone",
        "com.alightcreative.effects.starfield" to "am_starfield",
        "com.alightcreative.effects.lightning" to "am_lightning",
        "com.alightcreative.effects.cloud" to "am_clouds",
        "com.alightcreative.effects.clouds" to "am_clouds",
        "com.alightcreative.effects.fractalridge" to "am_fractal_ridges",
        "com.alightcreative.effects.fractalridges" to "am_fractal_ridges",
        "com.alightcreative.effects.halftone" to "halftone",
        "com.alightcreative.effects.scanlines" to "scanlines",
        "com.alightcreative.effects.vhs" to "vhs_tape",
        "com.alightcreative.effects.glitch" to "glitch",
        "com.alightcreative.effects.pixelate" to "pixelate",
        "com.alightcreative.effects.filmgrain" to "film_grain",
        "com.alightcreative.effects.fliplayer" to "am_3d_flip_layer",
        "com.alightcreative.effects.pagecurl" to "am_3d_page_curl",
        "com.alightcreative.effects.cube" to "am_3d_cube",
        "com.alightcreative.effects.sphere" to "am_3d_spherize",
        "com.alightcreative.effects.copybackground" to "am_copy_background",
        "com.alightcreative.effects.magnifybackground" to "am_magnify_background",
        "com.alightcreative.effects.timequantize" to "am_time_quantization",
        "com.alightcreative.effects.posterizetime" to "am_time_quantization",
        "com.alightcreative.effects.echo" to "am_echo_keyframes",
        "com.alightcreative.effects.textprogress" to "am_text_progress",
        "com.alightcreative.effects.textcount" to "am_text_count"
    )

    fun parseXml(
        rawXml: String,
        fileName: String,
        packId: String,
        packName: String,
        availableCustomShaders: List<ImportedShaderEffect> = emptyList()
    ): ParseOutput {
        val trimmed = rawXml.trim()
        if (trimmed.isEmpty() || !trimmed.contains("<")) {
            return ParseOutput(emptyList(), 0, emptyList())
        }

        val effectBlocks = mutableListOf<ParsedEffectBlock>()
        var docTitle: String? = null
        var clipDurationMs = 2_500L

        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val doc = runCatching {
            factory.newDocumentBuilder().parse(InputSource(StringReader(trimmed)))
        }.getOrNull()

        if (doc != null) {
            fun Element.attrOrNull(name: String): String? =
                if (hasAttribute(name)) getAttribute(name) else null

            fun walk(node: Node) {
                if (node is Element) {
                    val tag = (node.tagName ?: "").lowercase()
                    when (tag) {
                        "scene", "project", "preset" -> {
                            docTitle = node.attrOrNull("title")
                                ?: node.attrOrNull("name")
                                ?: docTitle
                            node.attrOrNull("duration")?.toLongOrNull()?.let { d ->
                                if (d > 100L) clipDurationMs = d
                            }
                        }
                        "media", "shape", "text", "null", "group" -> {
                            val start = node.attrOrNull("startTime")?.toLongOrNull() ?: 0L
                            val end = node.attrOrNull("endTime")?.toLongOrNull() ?: 0L
                            if (end > start) {
                                clipDurationMs = (end - start).coerceIn(300L, 60_000L)
                            }
                        }
                        "effect", "shader" -> {
                            val effId = node.attrOrNull("id")
                                ?: node.attrOrNull("effectId")
                                ?: node.attrOrNull("type")
                                ?: node.attrOrNull("name")
                                ?: ""
                            val label = node.attrOrNull("label")
                                ?: node.attrOrNull("name")
                                ?: effId.substringAfterLast('.')
                            val enabledAttr = node.attrOrNull("locallyApplied")
                                ?: node.attrOrNull("enabled")
                                ?: "true"
                            val blend = node.attrOrNull("blendMode")
                                ?: node.attrOrNull("blend")
                                ?: "normal"
                            val block = ParsedEffectBlock(
                                rawId = effId.trim(),
                                label = label.trim().ifEmpty { "XML Effect" },
                                enabled = !enabledAttr.equals("false", ignoreCase = true) && enabledAttr != "0",
                                blendMode = blend
                            )
                            val propNodes = node.childNodes
                            for (i in 0 until propNodes.length) {
                                val child = propNodes.item(i)
                                if (child is Element) {
                                    val cTag = (child.tagName ?: "").lowercase()
                                    if (cTag == "property" || cTag == "param" || cTag == "uniform") {
                                        val pName = child.attrOrNull("name")
                                            ?: child.attrOrNull("id")
                                            ?: child.attrOrNull("key")
                                            ?: "param"
                                        val pType = child.attrOrNull("type") ?: "float"
                                        val pVal = child.attrOrNull("value")
                                            ?: child.attrOrNull("default")
                                            ?: ""
                                        val parsedProp = ParsedProperty(pName.trim(), pType.trim(), pVal.trim())
                                        val kfNodes = child.childNodes
                                        for (j in 0 until kfNodes.length) {
                                            val kfNode = kfNodes.item(j)
                                            if (kfNode is Element) {
                                                val kTag = (kfNode.tagName ?: "").lowercase()
                                                if (kTag == "kf" || kTag == "keyframe") {
                                                    val tRaw = kfNode.attrOrNull("t") ?: kfNode.attrOrNull("time") ?: "0"
                                                    val vRaw = kfNode.attrOrNull("v") ?: kfNode.attrOrNull("value") ?: "0"
                                                    val eRaw = kfNode.attrOrNull("e") ?: kfNode.attrOrNull("easing") ?: ""
                                                    parsedProp.keyframes += ParsedKf(
                                                        timeMs = tRaw.toFloatOrNull() ?: 0f,
                                                        rawValue = vRaw,
                                                        easingRaw = eRaw
                                                    )
                                                }
                                            }
                                        }
                                        block.properties += parsedProp
                                    }
                                }
                            }
                            if (block.rawId.isNotBlank() || block.properties.isNotEmpty()) {
                                effectBlocks += block
                            }
                        }
                    }
                }
                val children = node.childNodes
                for (i in 0 until children.length) {
                    walk(children.item(i))
                }
            }

            doc.documentElement?.let { walk(it) }
        }

        val baseName = (docTitle?.takeIf { it.isNotBlank() }
            ?: fileName.substringBeforeLast('.').replace('_', ' ').replace('-', ' '))
            .trim()
            .ifBlank { "XML Effect Preset" }

        val unresolved = mutableListOf<String>()
        val mappedPasses = effectBlocks.mapNotNull { block ->
            val (targetItem, wasExact) = resolveCatalogTarget(block, availableCustomShaders)
            if (!wasExact && block.rawId.isNotBlank()) {
                unresolved += block.rawId
            }
            buildShaderPassFromBlock(block, targetItem, clipDurationMs)
        }

        // If an XML file had no <effect> tag (e.g. a shader wrapper or metadata XML), map via filename
        val finalPasses = if (mappedPasses.isNotEmpty()) {
            mappedPasses
        } else {
            val fallbackItem = resolveCatalogTarget(
                ParsedEffectBlock(fileName.substringBeforeLast('.'), baseName, true, "normal"),
                availableCustomShaders
            ).first
            listOf(
                ShaderEffectPass(
                    id = "xml_pass_${UUID.randomUUID().toString().take(6)}",
                    effectId = fallbackItem.id,
                    effectName = "$baseName (${fallbackItem.name})",
                    category = fallbackItem.category,
                    intensity = fallbackItem.defaultIntensity,
                    params = fallbackItem.defaultParamMap(),
                    colorParams = fallbackItem.defaultColorMap(),
                    textParams = fallbackItem.defaultTextMap()
                )
            )
        }

        val primaryId = finalPasses.first().effectId
        val primaryCategory = finalPasses.first().category
        val presetId = "custom_xml_${stableSlugHash(fileName + "_" + trimmed.take(256))}"

        val preset = ImportedXmlEffectPreset(
            id = presetId,
            name = baseName,
            packId = packId,
            packName = packName,
            fileName = fileName,
            category = primaryCategory,
            rawXml = trimmed,
            passes = finalPasses,
            primaryMappedEffectId = primaryId,
            unresolvedEffectIds = unresolved.distinct()
        )
        return ParseOutput(
            presets = listOf(preset),
            matchedEffectCount = effectBlocks.size.coerceAtLeast(1),
            unresolvedIds = unresolved.distinct()
        )
    }

    private fun resolveCatalogTarget(
        block: ParsedEffectBlock,
        customShaders: List<ImportedShaderEffect>
    ): Pair<EffectCatalogItem, Boolean> {
        val rawLower = block.rawId.lowercase().trim()
        val shortSlug = rawLower.substringAfterLast('.').removePrefix("gold_").removePrefix("am_")

        // 1. Check matching custom shader in imported packs first
        val matchingShader = customShaders.firstOrNull { sh ->
            val shSlug = sh.fileName.substringBeforeLast('.').lowercase()
            shSlug == shortSlug || sh.name.equals(block.label, ignoreCase = true) ||
                shSlug.contains(shortSlug) && shortSlug.length >= 4
        }
        if (matchingShader != null) {
            return EffectCatalogItem(
                id = matchingShader.id,
                name = matchingShader.name,
                category = matchingShader.category,
                defaultIntensity = 0.85f,
                params = matchingShader.params
            ) to true
        }

        // 2. Exact reverse-DNS Alight Motion ID match
        exactAmIdMap[rawLower]?.let { mappedId ->
            findBuiltInItem(mappedId)?.let { return it to true }
        }
        exactAmIdMap["com.alightcreative.effects.$shortSlug"]?.let { mappedId ->
            findBuiltInItem(mappedId)?.let { return it to true }
        }

        // 3. Direct built-in ID or normalized name match
        val builtInPool = EffectCatalog.shaderEffects + AlightMotionEffectRegistry.effects
        builtInPool.firstOrNull { item ->
            val itemNorm = item.id.removePrefix("am_").replace("_", "")
            val slugNorm = shortSlug.replace("_", "").replace("-", "")
            item.id.equals(rawLower, ignoreCase = true) ||
                itemNorm == slugNorm ||
                item.name.replace(" ", "").equals(slugNorm, ignoreCase = true)
        }?.let { return it to true }

        // 4. Keyword similarity fallback across both label and effect ID
        val combined = "$rawLower ${block.label.lowercase()}"
        val fallbackId = when {
            combined.contains("tile") -> "am_tiles"
            combined.contains("oscil") -> "am_oscillate"
            combined.contains("swing") -> "am_swing"
            combined.contains("shake") -> "am_auto_shake"
            combined.contains("spin") -> "am_spin"
            combined.contains("wave") -> "am_wave_warp"
            combined.contains("turbul") -> "am_turbulent_displace"
            combined.contains("ripple") -> "ripple"
            combined.contains("swirl") -> "am_swirl"
            combined.contains("pinch") || combined.contains("bulge") -> "am_pinch_bulge"
            combined.contains("motion") && combined.contains("blur") -> "dir_motion_blur"
            combined.contains("zoom") && combined.contains("blur") -> "radial_zoom_blur"
            combined.contains("blur") -> "gaussian_blur"
            combined.contains("glow") || combined.contains("bloom") -> "glow_bloom"
            combined.contains("expos") || combined.contains("flash") -> "exposure_flash"
            combined.contains("rgb") || combined.contains("chrom") -> "rgb_split"
            combined.contains("glitch") -> "glitch"
            combined.contains("kaleid") -> "am_kaleidoscope"
            combined.contains("mirror") -> "am_mirror"
            combined.contains("grid") -> "am_grid"
            combined.contains("check") -> "am_checker"
            combined.contains("grad") -> "duotone"
            combined.contains("hue") -> "hue_shift"
            combined.contains("invert") -> "invert"
            combined.contains("vign") -> "vignette"
            combined.contains("vhs") -> "vhs_tape"
            combined.contains("scan") -> "scanlines"
            combined.contains("grain") -> "film_grain"
            combined.contains("half") -> "halftone"
            combined.contains("flip") -> "am_3d_flip_layer"
            combined.contains("cube") -> "am_3d_cube"
            combined.contains("curl") -> "am_3d_page_curl"
            combined.contains("3d") -> "am_3d_box"
            else -> "am_tiles"
        }
        return (findBuiltInItem(fallbackId) ?: builtInPool.first()) to false
    }

    private fun findBuiltInItem(id: String): EffectCatalogItem? =
        EffectCatalog.shaderEffects.firstOrNull { it.id == id }
            ?: AlightMotionEffectRegistry.effects.firstOrNull { it.id == id }

    private fun buildShaderPassFromBlock(
        block: ParsedEffectBlock,
        item: EffectCatalogItem,
        clipDurationMs: Long
    ): ShaderEffectPass {
        val paramValues = item.defaultParamMap().toMutableMap()
        val colorValues = item.defaultColorMap().toMutableMap()
        val textValues = item.defaultTextMap().toMutableMap()
        val paramKeyframes = mutableMapOf<String, List<ParameterKeyframe>>()
        var passIntensity = item.defaultIntensity

        block.properties.forEach { prop ->
            val pLower = prop.name.lowercase()
            if (pLower == "intensity" || pLower == "alpha" || pLower == "strength" || pLower == "amount") {
                val parsed = parseFirstFloat(prop.rawValue)
                if (parsed != null) {
                    passIntensity = normalizePotentialPercent(parsed).coerceIn(0.05f, 1f)
                }
                if (prop.keyframes.isNotEmpty()) {
                    paramKeyframes[PARAM_KEY_INTENSITY] = convertKeyframes(
                        prop.keyframes, clipDurationMs, 0f, 1f, normalizePercent = true
                    )
                }
            }

            // Match property against item's typed parameter specs
            val matchedSpec = item.params.firstOrNull { spec ->
                val sk = spec.key.lowercase()
                val sl = spec.label.lowercase()
                sk == pLower || sl == pLower ||
                    (pLower.contains("freq") && sk.contains("freq")) ||
                    (pLower.contains("amp") && (sk.contains("amp") || sk.contains("mag") || sk.contains("strength"))) ||
                    (pLower.contains("mag") && (sk.contains("mag") || sk.contains("amp") || sk.contains("dist"))) ||
                    (pLower.contains("angle") && sk.contains("ang")) ||
                    (pLower.contains("crop") && sk.contains("crop")) ||
                    (pLower.contains("color") && spec.paramType == EffectParamType.COLOR) ||
                    (pLower.contains("radius") && sk.contains("rad")) ||
                    (pLower.contains("count") && sk.contains("count"))
            }

            if (matchedSpec != null) {
                when (matchedSpec.paramType) {
                    EffectParamType.COLOR -> {
                        parseArgbColor(prop.rawValue)?.let { colorValues[matchedSpec.key] = it }
                    }
                    EffectParamType.TEXT_STRING -> {
                        if (prop.rawValue.isNotBlank()) textValues[matchedSpec.key] = prop.rawValue
                    }
                    EffectParamType.FLOAT,
                    EffectParamType.POINT_2D_X,
                    EffectParamType.POINT_2D_Y,
                    EffectParamType.ENUM_MODE,
                    EffectParamType.BOOLEAN -> {
                        val v = parseFirstFloat(prop.rawValue)
                        if (v != null) {
                            paramValues[matchedSpec.key] = mapIntoRange(v, matchedSpec.minValue, matchedSpec.maxValue)
                        }
                        if (prop.keyframes.isNotEmpty()) {
                            paramKeyframes[matchedSpec.key] = convertKeyframes(
                                prop.keyframes,
                                clipDurationMs,
                                matchedSpec.minValue,
                                matchedSpec.maxValue,
                                normalizePercent = false
                            )
                        }
                    }
                }
            }
        }

        val resolvedBlend = when {
            block.blendMode.contains("add", ignoreCase = true) || block.blendMode.contains("linear_dodge", ignoreCase = true) -> EffectBlendMode.ADD
            block.blendMode.contains("mult", ignoreCase = true) -> EffectBlendMode.MULTIPLY
            block.blendMode.contains("screen", ignoreCase = true) -> EffectBlendMode.SCREEN
            block.blendMode.contains("overlay", ignoreCase = true) -> EffectBlendMode.OVERLAY
            else -> EffectBlendMode.NORMAL
        }

        return ShaderEffectPass(
            id = "xml_pass_${UUID.randomUUID().toString().take(6)}",
            effectId = item.id,
            effectName = block.label.ifBlank { item.name },
            category = item.category,
            intensity = passIntensity,
            params = paramValues,
            colorParams = colorValues,
            textParams = textValues,
            paramKeyframes = paramKeyframes,
            isEnabled = block.enabled,
            blendMode = resolvedBlend
        )
    }

    private fun convertKeyframes(
        rawList: List<ParsedKf>,
        clipDurationMs: Long,
        minVal: Float,
        maxVal: Float,
        normalizePercent: Boolean
    ): List<ParameterKeyframe> {
        val maxTime = rawList.maxOfOrNull { it.timeMs } ?: 1f
        // AM exports time in ms or normalized 0..1; detect scale automatically
        val divisor = when {
            maxTime <= 1.05f -> 1f
            maxTime <= clipDurationMs.toFloat() * 1.5f -> clipDurationMs.toFloat().coerceAtLeast(1000f)
            else -> maxTime.coerceAtLeast(1000f)
        }
        return rawList.mapNotNull { kf ->
            val numeric = parseFirstFloat(kf.rawValue) ?: return@mapNotNull null
            val normVal = if (normalizePercent) normalizePotentialPercent(numeric) else numeric
            val clampedVal = mapIntoRange(normVal, minVal, maxVal)
            val normTime = (kf.timeMs / divisor).coerceIn(0f, 1f)
            val (easing, bezier) = parseEasing(kf.easingRaw)
            ParameterKeyframe(
                id = "xkf_${UUID.randomUUID().toString().take(6)}",
                time = normTime,
                value = clampedVal,
                easing = easing,
                customBezier = bezier
            )
        }.sortedBy { it.time }
    }

    private fun parseEasing(raw: String): Pair<KeyframeEasing, BezierCurveSpec?> {
        val parts = raw.trim().split(Regex("[,\\s]+")).mapNotNull { it.toFloatOrNull() }
        if (parts.size >= 4 && raw.contains("cubicBezier", ignoreCase = true)) {
            return KeyframeEasing.CUBIC_BEZIER to BezierCurveSpec(
                p1x = parts[0].coerceIn(0f, 1f),
                p1y = parts[1].coerceIn(-0.5f, 1.5f),
                p2x = parts[2].coerceIn(0f, 1f),
                p2y = parts[3].coerceIn(-0.5f, 1.5f)
            )
        }
        val lower = raw.lowercase()
        return when {
            lower.contains("step") || lower.contains("hold") -> KeyframeEasing.STEP to null
            lower.contains("in") && lower.contains("out") -> KeyframeEasing.EASE_IN_OUT to null
            lower.contains("out") -> KeyframeEasing.EASE_OUT to null
            lower.contains("in") -> KeyframeEasing.EASE_IN to null
            else -> KeyframeEasing.LINEAR to null
        }
    }

    private fun parseFirstFloat(raw: String): Float? {
        if (raw.isBlank()) return null
        if (raw.equals("true", ignoreCase = true)) return 1f
        if (raw.equals("false", ignoreCase = true)) return 0f
        return Regex("""[-+]?\d*\.?\d+""").find(raw)?.value?.toFloatOrNull()
    }

    private fun normalizePotentialPercent(v: Float): Float =
        if (abs(v) > 1.5f) (v / 100f).coerceIn(0f, 1f) else v

    private fun mapIntoRange(value: Float, minVal: Float, maxVal: Float): Float {
        if (maxVal <= minVal) return value
        return value.coerceIn(minVal, maxVal)
    }

    private fun parseArgbColor(raw: String): Long? {
        val clean = raw.trim().removePrefix("#")
        if (clean.isEmpty()) return null
        val parsed = clean.toLongOrNull(16) ?: return null
        return if (clean.length <= 6) (0xFF000000L or parsed) else parsed
    }

    internal fun stableSlugHash(input: String): String {
        val hash = input.hashCode().toUInt().toString(16)
        return hash.take(10)
    }
}
