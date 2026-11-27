package com.example.engine.packs

import com.example.model.EffectCategory

/**
 * Curated GLSL ES 1.00 shaders from the uploaded Alight Motion / NanoVG shader collection,
 * pre-bundled into the "Core GLSL Shaders" custom pack so users can preview, export,
 * copy, or remix them immediately inside My Effects across all editing engines.
 */
object BundledCustomShaders {

    const val BUNDLED_PACK_ID = "pack_bundled_all_shaders"
    const val BUNDLED_PACK_NAME = "All Shaders (Core GLSL)"

    private data class ShaderSpec(
        val id: String,
        val displayName: String,
        val fileName: String,
        val category: EffectCategory,
        val rawGlsl: String
    )

    private val rawSpecs: List<ShaderSpec> = listOf(
        ShaderSpec(
            id = "custom_glsl_tex2d_ripple",
            displayName = "AM Water Ripple",
            fileName = "tex2d_ripple.frag",
            category = EffectCategory.AM_DISTORTION_WARP,
            rawGlsl = """
                #version 100
                precision mediump float;
                uniform sampler2D texture;
                uniform float alpha;
                varying vec2 st;
                uniform float time;
                uniform float amplitude;
                uniform float frequency;
                uniform vec2 center;

                void main() {
                    vec2 p = st - center;
                    float len = length(p);
                    float ripple = sin(len * frequency - time * 6.0) * amplitude;
                    vec2 uv = st + (p / max(len, 1e-4)) * ripple;
                    vec4 dst = texture2D(texture, clamp(uv, 0.0, 1.0));
                    gl_FragColor = dst * alpha;
                }
            """.trimIndent()
        ),
        ShaderSpec(
            id = "custom_glsl_ripple_field",
            displayName = "AM Ripple Field",
            fileName = "Ripple.frag",
            category = EffectCategory.AM_PROCEDURAL,
            rawGlsl = """
                #version 100
                precision mediump float;
                uniform float time;
                uniform float amplitude;
                uniform float frequency;
                uniform vec2 center;
                varying vec2 st;

                void main() {
                    vec2 p = st - center;
                    float len = length(p);
                    float ripple = sin(len * frequency - time * 5.0) * amplitude;
                    vec2 uv = st + (p / max(len, 1e-4)) * ripple;
                    gl_FragColor = vec4(uv, 0.5 + 0.5 * ripple, 1.0);
                }
            """.trimIndent()
        ),
        ShaderSpec(
            id = "custom_glsl_grad_tex2d_linear",
            displayName = "AM Linear Grad Tint",
            fileName = "grad_tex2d_linear.frag",
            category = EffectCategory.AM_PROCEDURAL,
            rawGlsl = """
                #version 100
                precision mediump float;
                uniform vec4 color_start;
                uniform vec4 color_end;
                uniform vec2 start;
                uniform vec2 end;
                varying vec2 st;
                uniform sampler2D texture;
                uniform float alpha;

                vec2 projectAndClamp(vec2 a, vec2 b) {
                    return clamp(dot(a,b)/max(dot(b,b), 1e-5),0.0,1.0) * b;
                }

                void main() {
                    vec4 dst = texture2D(texture,st);
                    vec2 p = projectAndClamp(st-start, end-start)+start;
                    float f = smoothstep(0.0,1.0,distance(p,start)/max(distance(end,start), 1e-4));
                    vec4 src = mix(color_start,color_end,f) * 0.65;
                    gl_FragColor = mix(dst, src + dst * (1.0 - src.a), alpha);
                }
            """.trimIndent()
        ),
        ShaderSpec(
            id = "custom_glsl_grad_tex2d_radial",
            displayName = "AM Radial Grad Tint",
            fileName = "grad_tex2d_radial.frag",
            category = EffectCategory.AM_PROCEDURAL,
            rawGlsl = """
                #version 100
                precision mediump float;
                uniform vec4 color_start;
                uniform vec4 color_end;
                uniform vec2 start;
                uniform vec2 end;
                varying vec2 st;
                uniform sampler2D texture;
                uniform float alpha;

                void main() {
                    vec4 dst = texture2D(texture,st);
                    float f = smoothstep(0.0,1.0,distance(st,start)/max(distance(end,start), 1e-4));
                    vec4 src = mix(color_start,color_end,f) * 0.65;
                    gl_FragColor = mix(dst, src + dst * (1.0 - src.a), alpha);
                }
            """.trimIndent()
        ),
        ShaderSpec(
            id = "custom_glsl_grad_tex2d_sweep",
            displayName = "AM Sweep Grad Tint",
            fileName = "grad_tex2d_sweep.frag",
            category = EffectCategory.AM_PROCEDURAL,
            rawGlsl = """
                #version 100
                precision mediump float;
                uniform sampler2D texture;
                uniform float alpha;
                varying vec2 st;
                uniform vec4 color_start;
                uniform vec4 color_end;
                uniform vec2 start;
                uniform vec2 end;

                void main() {
                    vec4 dst = texture2D(texture,st);
                    vec2 xy = st - start;
                    vec2 zw = end - start;
                    float a = atan(xy.x, xy.y);
                    float b = atan(zw.x, zw.y);
                    float f = mod(((a-b)+6.28318),6.28318)/6.28318;
                    vec4 src = mix(color_start,color_end,f) * 0.65;
                    gl_FragColor = mix(dst, src + dst * (1.0 - src.a), alpha);
                }
            """.trimIndent()
        ),
        ShaderSpec(
            id = "custom_glsl_grid",
            displayName = "AM Precision Grid",
            fileName = "grid.frag",
            category = EffectCategory.AM_PROCEDURAL,
            rawGlsl = """
                #version 100
                precision highp float;
                uniform vec4 color;
                uniform float grid_spacing;
                uniform vec2 grid_offset;
                uniform float pixel_scale;
                uniform float line_width;
                varying vec2 st;
                uniform sampler2D texture;

                void main() {
                    vec4 dst = texture2D(texture, st);
                    vec2 uv = abs(fract((st+grid_offset+grid_spacing*0.5+0.5)/max(grid_spacing, 0.02))-0.5);
                    float t = min(uv.x,uv.y);
                    float p = clamp(1. - (t-(line_width/max(grid_spacing, 0.02))) / max(pixel_scale/max(grid_spacing, 0.02), 1e-4),0.,1.);
                    gl_FragColor = mix(dst, color, p * color.a);
                }
            """.trimIndent()
        ),
        ShaderSpec(
            id = "custom_glsl_checker",
            displayName = "AM Checkerboard",
            fileName = "checker.frag",
            category = EffectCategory.AM_PROCEDURAL,
            rawGlsl = """
                #version 100
                precision mediump float;
                uniform vec4 color_a;
                uniform vec4 color_b;
                uniform float oneOverCheckSize;
                uniform vec4 offsetScale;

                void main() {
                    float f = step(mod(floor((gl_FragCoord.x + offsetScale.x)*oneOverCheckSize*offsetScale.z)+floor((gl_FragCoord.y + offsetScale.y)*oneOverCheckSize*offsetScale.w),2.0),0.5);
                    gl_FragColor = mix(color_a,color_b,f);
                }
            """.trimIndent()
        ),
        ShaderSpec(
            id = "custom_glsl_tex2d_invert",
            displayName = "AM Negative Invert",
            fileName = "tex2d_invert.frag",
            category = EffectCategory.COLOR_GLOW,
            rawGlsl = """
                #version 100
                precision mediump float;
                uniform sampler2D texture;
                varying vec2 st;

                void main() {
                    vec4 texColor = texture2D(texture,st);
                    gl_FragColor = vec4(1.0-texColor.r, 1.0-texColor.g, 1.0-texColor.b, texColor.a);
                }
            """.trimIndent()
        ),
        ShaderSpec(
            id = "custom_glsl_tex2d_mask",
            displayName = "AM Alpha Tint Mask",
            fileName = "tex2d_mask.frag",
            category = EffectCategory.AM_MATTE_MASK_KEY,
            rawGlsl = """
                #version 100
                precision mediump float;
                uniform sampler2D texture;
                uniform vec4 color;
                varying vec2 st;

                void main() {
                    vec4 texColor = texture2D(texture,st);
                    gl_FragColor = color * texColor.a;
                }
            """.trimIndent()
        ),
        ShaderSpec(
            id = "custom_glsl_tex2d_solid",
            displayName = "AM Solid Tint",
            fileName = "tex2d_solid.frag",
            category = EffectCategory.COLOR_GLOW,
            rawGlsl = """
                #version 100
                precision mediump float;
                uniform sampler2D texture;
                uniform float alpha;
                varying vec2 st;
                uniform vec4 color;

                void main() {
                    vec4 dst = texture2D(texture,st);
                    vec4 src = color;
                    gl_FragColor = (src*dst.a + dst*(1.0-src.a)*vec4(1.0,1.0,1.0,dst.a)) * alpha;
                }
            """.trimIndent()
        )
    )

    val shaders: List<ImportedShaderEffect> by lazy {
        rawSpecs.map { spec ->
            val transpiled = GlslCompatTranspiler.transpile(spec.rawGlsl, spec.fileName)
            ImportedShaderEffect(
                id = spec.id,
                name = spec.displayName,
                packId = BUNDLED_PACK_ID,
                packName = BUNDLED_PACK_NAME,
                fileName = spec.fileName,
                category = spec.category,
                originalGlslVersion = transpiled.originalVersion,
                rawSource = spec.rawGlsl,
                preambleDecls = transpiled.preambleDecls,
                mainBody = transpiled.mainBody,
                fallbackEffectId = transpiled.fallbackEffectId,
                params = transpiled.params,
                isRunnablePostProcess = transpiled.isRunnablePostProcess,
                compileWarning = transpiled.warning
            )
        }
    }

    val packInfo: CustomPackInfo by lazy {
        CustomPackInfo(
            id = BUNDLED_PACK_ID,
            name = BUNDLED_PACK_NAME,
            sourceType = ImportedPackSourceType.BUNDLED_SHADERS,
            presetCount = 0,
            shaderCount = shaders.size,
            fontCount = 0
        )
    }
}
