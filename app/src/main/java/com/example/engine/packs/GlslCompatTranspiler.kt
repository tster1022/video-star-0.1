package com.example.engine.packs

import com.example.model.EffectCategory
import com.example.model.EffectParamSpec
import com.example.model.EffectParamType

/**
 * Transpiles GLSL ES 1.00 (#version 100) and GLSL ES 3.00 fragment shaders
 * (including Alight Motion / Gold Motion .frag assets) into Star Studio's
 * shared GLSL ES 3.00 ShaderCommonGlsl harness, automatically extracting uniform
 * declarations into UI-controllable EffectParamSpec parameters.
 */
object GlslCompatTranspiler {

    data class TranspileResult(
        val originalVersion: String,
        val preambleDecls: String,
        val mainBody: String,
        val params: List<EffectParamSpec>,
        val isRunnablePostProcess: Boolean,
        val fallbackEffectId: String,
        val warning: String? = null
    )

    private data class UniformDecl(
        val glslType: String,
        val name: String
    )

    private val versionRegex = Regex("""^\s*#version\s+(\d+).*$""", RegexOption.MULTILINE)
    private val extensionRegex = Regex("""^\s*#extension\s+.*$""", RegexOption.MULTILINE)
    private val precisionRegex = Regex("""^\s*precision\s+(lowp|mediump|highp)\s+float\s*;.*$""", RegexOption.MULTILINE)
    private val uniformRegex = Regex("""^\s*uniform\s+(float|int|bool|vec2|vec3|vec4|mat3|mat4|sampler2D|samplerExternalOES)\s+([A-Za-z_][A-Za-z0-9_]*)(?:\s*\[\s*([^\]]+)\s*\])?\s*;""")
    private val varyingOrInOutRegex = Regex("""^\s*(?:varying|attribute|in|out)\s+(?:highp\s+|mediump\s+|lowp\s+)?(?:float|int|vec[234]|mat[34])\s+([A-Za-z_][A-Za-z0-9_]*)\s*;""")

    fun transpile(
        rawSource: String,
        fileName: String = "custom.frag"
    ): TranspileResult {
        val normalized = rawSource.replace("\r\n", "\n").trim()
        val versionMatch = versionRegex.find(normalized)
        val detectedVersion = versionMatch?.groupValues?.getOrNull(1) ?: "100"
        val fallback = inferFallbackEffectId(fileName, normalized)

        // Vertex shaders, point-sprite shaders, or NanoVG multi-UBO templates cannot run
        // directly as fullscreen 2D image-processing passes, so mark them as non-runnable assets.
        val lowerName = fileName.lowercase()
        val isVertexFile = lowerName.endsWith(".vert") || lowerName.endsWith(".vsh")
        val hasUnsupportedConstructs = normalized.contains("USE_UNIFORMBUFFER") ||
            normalized.contains("NANOVG_GL3") ||
            normalized.contains("gl_PointCoord") ||
            normalized.contains("samplerExternalOES") ||
            normalized.contains("gl_Position")

        if (isVertexFile || hasUnsupportedConstructs) {
            return TranspileResult(
                originalVersion = detectedVersion,
                preambleDecls = "",
                mainBody = "",
                params = emptyList(),
                isRunnablePostProcess = false,
                fallbackEffectId = fallback,
                warning = if (isVertexFile) {
                    "Vertex shader indexed for inspection/export"
                } else {
                    "Uses specialized pipeline uniforms; mapped to fallback GPU shader ($fallback)"
                }
            )
        }

        val uniforms = mutableListOf<UniformDecl>()
        val varyingCoordNames = mutableSetOf<String>()
        var samplerUniformName: String? = null
        var hasMain = false

        // Separate header/functions before `void main` and extract main() block safely.
        val mainRegex = Regex("""\bvoid\s+main\s*\(\s*(?:void)?\s*\)\s*\{""")
        val mainMatch = mainRegex.find(normalized)
        if (mainMatch == null) {
            return TranspileResult(
                originalVersion = detectedVersion,
                preambleDecls = "",
                mainBody = "",
                params = emptyList(),
                isRunnablePostProcess = false,
                fallbackEffectId = fallback,
                warning = "Missing void main() entry point; mapped to fallback GPU shader"
            )
        }
        hasMain = true

        val openBraceIndex = normalized.indexOf('{', mainMatch.range.first)
        val closeBraceIndex = findMatchingBrace(normalized, openBraceIndex)
        if (openBraceIndex < 0 || closeBraceIndex <= openBraceIndex) {
            return TranspileResult(
                originalVersion = detectedVersion,
                preambleDecls = "",
                mainBody = "",
                params = emptyList(),
                isRunnablePostProcess = false,
                fallbackEffectId = fallback,
                warning = "Unbalanced shader braces; mapped to fallback GPU shader"
            )
        }

        val rawPreMain = normalized.substring(0, mainMatch.range.first)
        val rawMainBody = normalized.substring(openBraceIndex + 1, closeBraceIndex)
        val postMain = normalized.substring(closeBraceIndex + 1)

        val cleanedPreambleLines = mutableListOf<String>()
        rawPreMain.lines().forEach { line ->
            val trimmed = line.trim()
            when {
                versionRegex.matches(trimmed) -> Unit
                extensionRegex.matches(trimmed) -> Unit
                precisionRegex.matches(trimmed) -> Unit
                uniformRegex.containsMatchIn(trimmed) -> {
                    val m = uniformRegex.find(trimmed)!!
                    val uType = m.groupValues[1]
                    val uName = m.groupValues[2]
                    if (uType == "sampler2D") {
                        samplerUniformName = uName
                    } else {
                        uniforms += UniformDecl(uType, uName)
                    }
                }
                varyingOrInOutRegex.containsMatchIn(trimmed) -> {
                    val m = varyingOrInOutRegex.find(trimmed)!!
                    val vName = m.groupValues[1]
                    if (!vName.equals("outColor", ignoreCase = true) && !vName.equals("fragColor", ignoreCase = true)) {
                        varyingCoordNames += vName
                    }
                }
                else -> cleanedPreambleLines += line
            }
        }
        if (postMain.isNotBlank()) {
            cleanedPreambleLines += postMain
        }

        val rawPreamble = cleanedPreambleLines.joinToString("\n").trim()

        // Extract UI specs and GLSL local bindings for custom uniforms
        val specs = mutableListOf<EffectParamSpec>()
        val bindingLines = mutableListOf<String>()
        var floatIdx = 0
        var colorIdx = 0

        uniforms.forEach { decl ->
            val lname = decl.name.lowercase()
            when {
                lname in setOf("time", "utime", "itime", "u_time", "duration", "uduration") -> Unit
                lname in setOf("alpha", "uintensity", "intensity") -> Unit
                lname in setOf("uprogress", "progress") -> Unit
                lname in setOf("utexsize", "resolution", "uresolution", "iresolution", "viewsize") -> Unit
                decl.glslType == "vec4" && (lname.contains("color") || lname.contains("col") || lname.contains("tint")) -> {
                    if (colorIdx < 3) {
                        val defaultColor = defaultColorForUniform(lname, colorIdx)
                        specs += EffectParamSpec(
                            key = decl.name,
                            label = prettyUniformLabel(decl.name),
                            paramType = EffectParamType.COLOR,
                            defaultColorArgb = defaultColor
                        )
                        bindingLines += "vec4 ${decl.name} = uColors[$colorIdx];"
                        colorIdx++
                    }
                }
                decl.glslType == "vec4" -> {
                    // Non-color vec4 such as offsetScale -> expose scale or fallback nicely
                    if (floatIdx < 12) {
                        val idx = floatIdx++
                        specs += EffectParamSpec(
                            key = decl.name,
                            label = prettyUniformLabel(decl.name),
                            defaultValue = 1.0f,
                            minValue = 0.25f,
                            maxValue = 3.0f
                        )
                        bindingLines += "vec4 ${decl.name} = vec4(0.0, 0.0, max(uParams[$idx], 0.1), max(uParams[$idx], 0.1));"
                    } else {
                        bindingLines += "vec4 ${decl.name} = vec4(0.0, 0.0, 1.0, 1.0);"
                    }
                }
                decl.glslType == "vec2" -> {
                    val (defX, defY) = defaultVec2ForUniform(lname)
                    val xIdx = if (floatIdx < 12) floatIdx++ else -1
                    val yIdx = if (floatIdx < 12) floatIdx++ else -1
                    if (xIdx >= 0 && yIdx >= 0) {
                        specs += EffectParamSpec(
                            key = "${decl.name}_x",
                            label = "${prettyUniformLabel(decl.name)} X",
                            defaultValue = defX,
                            minValue = -0.5f,
                            maxValue = 1.5f,
                            paramType = EffectParamType.POINT_2D_X,
                            pairedKey = "${decl.name}_y"
                        )
                        specs += EffectParamSpec(
                            key = "${decl.name}_y",
                            label = "${prettyUniformLabel(decl.name)} Y",
                            defaultValue = defY,
                            minValue = -0.5f,
                            maxValue = 1.5f,
                            paramType = EffectParamType.POINT_2D_Y,
                            pairedKey = "${decl.name}_x"
                        )
                        bindingLines += "vec2 ${decl.name} = vec2(uParams[$xIdx], uParams[$yIdx]);"
                    } else {
                        bindingLines += "vec2 ${decl.name} = vec2(${defX}f, ${defY}f);"
                    }
                }
                decl.glslType == "vec3" -> {
                    if (colorIdx < 3) {
                        specs += EffectParamSpec(
                            key = decl.name,
                            label = prettyUniformLabel(decl.name),
                            paramType = EffectParamType.COLOR,
                            defaultColorArgb = defaultColorForUniform(lname, colorIdx)
                        )
                        bindingLines += "vec3 ${decl.name} = uColors[$colorIdx].rgb;"
                        colorIdx++
                    } else {
                        bindingLines += "vec3 ${decl.name} = vec3(1.0);"
                    }
                }
                decl.glslType == "float" -> {
                    val (defV, minV, maxV, unit) = defaultRangeForFloatUniform(lname)
                    if (floatIdx < 12) {
                        val idx = floatIdx++
                        specs += EffectParamSpec(
                            key = decl.name,
                            label = prettyUniformLabel(decl.name),
                            defaultValue = defV,
                            minValue = minV,
                            maxValue = maxV,
                            unitSuffix = unit
                        )
                        bindingLines += "float ${decl.name} = uParams[$idx];"
                    } else {
                        bindingLines += "float ${decl.name} = $defV;"
                    }
                }
                decl.glslType == "int" || decl.glslType == "bool" -> {
                    bindingLines += "${decl.glslType} ${decl.name} = ${if (decl.glslType == "bool") "false" else "0"};"
                }
            }
        }

        // Handle shaders (like grad_tex2d_radial.frag / grad_tex2d_sweep.frag in All_Shaders)
        // that reference `alpha`, `st`, or `texture` directly without declaring them.
        val coordAliases = mutableSetOf("st").apply { addAll(varyingCoordNames) }

        fun rewriteGlslSegment(segment: String, insideMain: Boolean): String {
            var s = segment
            // Rewrite GLSL ES 1.00 texture2D -> texture
            s = s.replace(Regex("""\btexture2D\s*\("""), "texture(")
            s = s.replace(Regex("""\btexture2DProj\s*\("""), "textureProj(")
            // Rewrite sampler identifier `texture` (when not followed by `(`) to `uTexture`
            val samplerNames = listOfNotNull(samplerUniformName, "texture", "u_texture", "inputImageTexture").distinct()
            samplerNames.forEach { sName ->
                if (sName != "uTexture") {
                    s = s.replace(Regex("""\b${Regex.escape(sName)}\b(?!\s*\()"""), "uTexture")
                }
            }
            // Alias time variants -> uTime
            listOf("time", "iTime", "u_time").forEach { tName ->
                if (tName != "uTime") {
                    s = s.replace(Regex("""\b${Regex.escape(tName)}\b"""), "uTime")
                }
            }
            // Alias alpha -> clamp(uIntensity, 0.0, 1.0)
            s = s.replace(Regex("""\balpha\b(?!\s*\()"""), "clamp(uIntensity, 0.0, 1.0)")
            // Alias output color variables -> _starOut
            s = s.replace(Regex("""\bgl_FragColor\b"""), "_starOut")
            if (insideMain) {
                coordAliases.forEach { cAlias ->
                    // Do not replace if it's declared locally like `vec2 st = ...`
                    val hasLocalDecl = Regex("""\b(?:vec2|float)\s+${Regex.escape(cAlias)}\b""").containsMatchIn(s)
                    if (!hasLocalDecl) {
                        s = s.replace(Regex("""\b${Regex.escape(cAlias)}\b"""), "_starUv")
                    }
                }
                // Rewrite gl_FragCoord references cleanly for grid/checker resolution awareness
                s = s.replace("gl_FragCoord.xy", "(_starUv * max(uTexSize, vec2(1.0)))")
                s = s.replace("gl_FragCoord.x", "(_starUv.x * max(uTexSize.x, 1.0))")
                s = s.replace("gl_FragCoord.y", "(_starUv.y * max(uTexSize.y, 1.0))")
            }
            return s
        }

        val transpiledPreamble = rewriteGlslSegment(rawPreamble, insideMain = false)
        val rewrittenBody = rewriteGlslSegment(rawMainBody, insideMain = true)

        val finalMainBody = buildString {
            bindingLines.forEach { line ->
                append(line).append('\n')
            }
            append(rewrittenBody.trim())
        }

        return TranspileResult(
            originalVersion = detectedVersion,
            preambleDecls = transpiledPreamble,
            mainBody = finalMainBody,
            params = specs,
            isRunnablePostProcess = hasMain,
            fallbackEffectId = fallback
        )
    }

    fun inferCategory(fileName: String, source: String): EffectCategory {
        val text = (fileName + " " + source).lowercase()
        return when {
            text.contains("ripple") || text.contains("wave") || text.contains("swirl") ->
                EffectCategory.AM_DISTORTION_WARP
            text.contains("checker") || text.contains("grid") || text.contains("grad") || text.contains("solid") ->
                EffectCategory.AM_PROCEDURAL
            text.contains("mask") ->
                EffectCategory.AM_MATTE_MASK_KEY
            text.contains("invert") || text.contains("color") || text.contains("glow") ->
                EffectCategory.COLOR_GLOW
            else -> EffectCategory.AM_OTHER
        }
    }

    fun inferFallbackEffectId(fileName: String, source: String): String {
        val text = (fileName + " " + source).lowercase()
        return when {
            text.contains("ripple") -> "ripple"
            text.contains("checker") -> "am_checker"
            text.contains("grid") -> "am_grid"
            text.contains("grad") -> "duotone"
            text.contains("invert") -> "invert"
            text.contains("mask") -> "am_chroma_key"
            text.contains("blur") -> "gaussian_blur"
            text.contains("glitch") -> "glitch"
            else -> "am_wave_warp"
        }
    }

    private fun findMatchingBrace(text: String, openIndex: Int): Int {
        if (openIndex < 0 || openIndex >= text.length || text[openIndex] != '{') return -1
        var depth = 0
        for (i in openIndex until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    private fun prettyUniformLabel(raw: String): String =
        raw.removePrefix("u_")
            .removePrefix("u")
            .takeIf { it.isNotBlank() }
            ?.replace('_', ' ')
            ?.split(Regex("(?=[A-Z])|\\s+"))
            ?.filter { it.isNotBlank() }
            ?.joinToString(" ") { part ->
                part.replaceFirstChar { ch -> ch.uppercaseChar() }
            }
            ?: raw

    private fun defaultColorForUniform(lname: String, idx: Int): Long = when {
        lname.contains("start") || lname.contains("_a") -> 0xFFA855F7L // Vivid Purple
        lname.contains("end") || lname.contains("_b") -> 0xFF00E5FFL   // Neon Cyan
        idx == 0 -> 0xFFA855F7L
        idx == 1 -> 0xFFEC4899L
        else -> 0xFFFFFFFFL
    }

    private fun defaultVec2ForUniform(lname: String): Pair<Float, Float> = when {
        lname.contains("start") -> 0.15f to 0.15f
        lname.contains("end") -> 0.85f to 0.85f
        else -> 0.5f to 0.5f
    }

    private data class FloatRangeSpec(
        val defVal: Float,
        val minVal: Float,
        val maxVal: Float,
        val unit: String = ""
    )

    private fun defaultRangeForFloatUniform(lname: String): FloatRangeSpec = when {
        lname.contains("amplitude") || lname.contains("amp") -> FloatRangeSpec(0.03f, 0.0f, 0.15f)
        lname.contains("frequency") || lname.contains("freq") -> FloatRangeSpec(24.0f, 2.0f, 64.0f, "Hz")
        lname.contains("oneoverchecksize") -> FloatRangeSpec(0.03f, 0.005f, 0.12f)
        lname.contains("grid_spacing") || lname.contains("spacing") -> FloatRangeSpec(0.14f, 0.03f, 0.50f)
        lname.contains("line_width") || lname.contains("linewidth") -> FloatRangeSpec(0.015f, 0.002f, 0.15f)
        lname.contains("pixel_scale") -> FloatRangeSpec(0.006f, 0.001f, 0.05f)
        lname.contains("radius") -> FloatRangeSpec(0.35f, 0.01f, 1.0f)
        lname.contains("angle") -> FloatRangeSpec(0f, -180f, 180f, "°")
        lname.contains("scale") || lname.contains("zoom") -> FloatRangeSpec(1.0f, 0.1f, 4.0f, "x")
        else -> FloatRangeSpec(0.5f, 0.0f, 2.0f)
    }
}
