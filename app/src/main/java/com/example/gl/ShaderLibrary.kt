package com.example.gl

import com.example.engine.effects.shaders.DistortionWarpShaders
import com.example.engine.effects.shaders.MatteMaskKeyShaders
import com.example.engine.effects.shaders.MoveTransformShaders
import com.example.engine.effects.shaders.OpacityVisibilityShaders
import com.example.engine.effects.shaders.ProceduralShaders
import com.example.engine.effects.shaders.RepeatShaders
import com.example.engine.effects.shaders.ShaderCommonGlsl
import com.example.engine.effects.shaders.TextAndOtherShaders
import com.example.engine.effects.shaders.ThreeDShaders
import com.example.model.EffectCatalog
import com.example.model.ShaderEffectPass

/**
 * Shared GLSL ES 3.00 Vertex & Fragment Shader library for all 145 GPU effects
 * (35 Video Star + 110 Alight Motion across 9 categories).
 * Used identically by the live OpenGL ES 3.0 preview renderer (VideoStarGlRenderer) and
 * Jetpack Media3 Transformer GPU export (Media3ShaderEffect).
 */
object ShaderLibrary {

    val supportedEffectIds: Set<String>
        get() = (EffectCatalog.shaderEffects.map { it.id } +
            EffectCatalog.alightMotionEffects.map { it.id } +
            com.example.engine.packs.CustomPackRepository.dynamicCatalogItems().map { it.id }).toSet()

    const val VERTEX_SHADER_2D = """#version 300 es
layout(location = 0) in vec4 aPosition;
layout(location = 1) in vec4 aTexCoord;
out vec2 vTexCoord;
uniform mat4 uTexMatrix;
void main() {
    gl_Position = aPosition;
    vTexCoord = (uTexMatrix * aTexCoord).xy;
}
"""

    const val SOURCE_VERTEX_SHADER_2D = """#version 300 es
layout(location = 0) in vec4 aPosition;
layout(location = 1) in vec4 aTexCoord;
out vec2 vSceneUv;
out vec2 vTexCoord;
uniform mat4 uTexMatrix;
uniform mat4 uSourceTexMatrix;
void main() {
    gl_Position = aPosition;
    vec4 sceneCoord = uTexMatrix * aTexCoord;
    vSceneUv = sceneCoord.xy;
    vTexCoord = (uSourceTexMatrix * sceneCoord).xy;
}
"""

    const val SOURCE_VERTEX_SHADER_COMPAT = """
attribute vec4 aPosition;
attribute vec4 aTexCoord;
varying vec2 vSceneUv;
varying vec2 vTexCoord;
uniform mat4 uTexMatrix;
uniform mat4 uSourceTexMatrix;
void main() {
    gl_Position = aPosition;
    vec4 sceneCoord = uTexMatrix * aTexCoord;
    vSceneUv = sceneCoord.xy;
    vTexCoord = (uSourceTexMatrix * sceneCoord).xy;
}
"""

    const val PASSTHROUGH_OES_FRAGMENT_SHADER = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;
in vec2 vSceneUv;
in vec2 vTexCoord;
uniform samplerExternalOES uOesTexture;
uniform vec4 uBgColor;
out vec4 outColor;
void main() {
    if (vSceneUv.x < 0.0 || vSceneUv.x > 1.0 || vSceneUv.y < 0.0 || vSceneUv.y > 1.0) {
        outColor = uBgColor;
        return;
    }
    vec4 tex = texture(uOesTexture, vTexCoord);
    outColor = vec4(tex.rgb, 1.0);
}
"""

    const val PASSTHROUGH_OES_FRAGMENT_SHADER_COMPAT = """
#extension GL_OES_EGL_image_external : require
precision highp float;
varying vec2 vSceneUv;
varying vec2 vTexCoord;
uniform samplerExternalOES uOesTexture;
uniform vec4 uBgColor;
void main() {
    if (vSceneUv.x < 0.0 || vSceneUv.x > 1.0 || vSceneUv.y < 0.0 || vSceneUv.y > 1.0) {
        gl_FragColor = uBgColor;
        return;
    }
    vec4 tex = texture2D(uOesTexture, vTexCoord);
    gl_FragColor = vec4(tex.rgb, 1.0);
}
"""

    const val PASSTHROUGH_2D_FRAGMENT_SHADER = """#version 300 es
precision highp float;
in vec2 vSceneUv;
in vec2 vTexCoord;
uniform sampler2D uTexture;
uniform vec4 uBgColor;
out vec4 outColor;
void main() {
    if (vSceneUv.x < 0.0 || vSceneUv.x > 1.0 || vSceneUv.y < 0.0 || vSceneUv.y > 1.0) {
        outColor = uBgColor;
        return;
    }
    outColor = texture(uTexture, vTexCoord);
}
"""

    const val PASSTHROUGH_2D_FRAGMENT_SHADER_COMPAT = """
precision highp float;
varying vec2 vSceneUv;
varying vec2 vTexCoord;
uniform sampler2D uTexture;
uniform vec4 uBgColor;
void main() {
    if (vSceneUv.x < 0.0 || vSceneUv.x > 1.0 || vSceneUv.y < 0.0 || vSceneUv.y > 1.0) {
        gl_FragColor = uBgColor;
        return;
    }
    gl_FragColor = texture2D(uTexture, vTexCoord);
}
"""

    const val SAM_MASK_APPLY_FRAGMENT_SHADER = """#version 300 es
precision highp float;
in vec2 vSceneUv;
in vec2 vTexCoord;
uniform sampler2D uTexture;
uniform sampler2D uMaskTex;
uniform float uFeather;
uniform int uInvertMask;
out vec4 outColor;
void main() {
    vec4 src = texture(uTexture, clamp(vTexCoord, 0.0, 1.0));
    vec4 m = texture(uMaskTex, clamp(vTexCoord, 0.0, 1.0));
    float a = m.a;
    if (uInvertMask == 1) a = 1.0 - a;
    float edge = smoothstep(0.05, max(0.08, 0.2 + uFeather * 0.6), a);
    outColor = vec4(src.rgb, src.a * clamp(edge, 0.0, 1.0));
}
"""

    const val PASSTHROUGH_PREMULT_2D_FRAGMENT_SHADER = """#version 300 es
precision highp float;
in vec2 vSceneUv;
in vec2 vTexCoord;
uniform sampler2D uTexture;
uniform vec4 uBgColor;
out vec4 outColor;
void main() {
    vec4 tex = (vSceneUv.x < 0.0 || vSceneUv.x > 1.0 || vSceneUv.y < 0.0 || vSceneUv.y > 1.0)
        ? uBgColor
        : texture(uTexture, vTexCoord);
    outColor = vec4(tex.rgb * tex.a, tex.a);
}
"""

    const val PASSTHROUGH_PREMULT_2D_FRAGMENT_SHADER_COMPAT = """
precision highp float;
varying vec2 vSceneUv;
varying vec2 vTexCoord;
uniform sampler2D uTexture;
uniform vec4 uBgColor;
void main() {
    vec4 tex = (vSceneUv.x < 0.0 || vSceneUv.x > 1.0 || vSceneUv.y < 0.0 || vSceneUv.y > 1.0)
        ? uBgColor
        : texture2D(uTexture, vTexCoord);
    gl_FragColor = vec4(tex.rgb * tex.a, tex.a);
}
"""

    const val COMPOSITE_VERTEX_SHADER_3D = """#version 300 es
layout(location = 0) in vec4 aPosition;
layout(location = 1) in vec4 aTexCoord;
out vec2 vTexCoord;
out vec2 vBaseUv;
uniform mat4 uMvpMatrix;
uniform mat4 uTexMatrix;
void main() {
    vec4 clip = uMvpMatrix * aPosition;
    gl_Position = clip;
    vTexCoord = (uTexMatrix * aTexCoord).xy;
    float wSafe = (abs(clip.w) > 0.0001) ? clip.w : 1.0;
    vBaseUv = (clip.xy / wSafe) * 0.5 + 0.5;
}
"""

    const val MASK_SHAPE_FRAGMENT_SHADER = """#version 300 es
precision highp float;
in vec2 vSceneUv;
in vec2 vTexCoord;
uniform sampler2D uTexture;
uniform int uMaskMode;
uniform int uMaskShape;
uniform float uFeather;
uniform int uInvertMask;
uniform float uAngleRad;
uniform float uAspect;
out vec4 outColor;

float sdCircle(vec2 p, float r) {
    return length(p) - r;
}

float sdRoundedBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + vec2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

float sdDiamond(vec2 p, vec2 b) {
    p = abs(p);
    return (p.x * b.y + p.y * b.x - b.x * b.y) / max(length(b), 0.0001);
}

float sdTriangle(vec2 p, float r) {
    const float k = sqrt(3.0);
    p.x = abs(p.x) - r;
    p.y = p.y + r / k;
    if (p.x + k * p.y > 0.0) p = vec2(p.x - k * p.y, -k * p.x - p.y) * 0.5;
    p.x -= clamp(p.x, -2.0 * r, 0.0);
    return -length(p) * sign(p.y);
}

float sdStar5(vec2 p, float r, float rf) {
    const vec2 k1 = vec2(0.809016994, -0.587785252);
    const vec2 k2 = vec2(-k1.x, k1.y);
    p.x = abs(p.x);
    p -= 2.0 * max(dot(k1, p), 0.0) * k1;
    p -= 2.0 * max(dot(k2, p), 0.0) * k2;
    p.x = abs(p.x);
    p.y -= r;
    vec2 ba = rf * vec2(-k1.y, k1.x) - vec2(0.0, 1.0);
    float h = clamp(dot(p, ba) / dot(ba, ba), 0.0, r);
    return length(p - ba * h) * sign(p.y * ba.x - p.x * ba.y);
}

float sdHeart(vec2 p) {
    p.x = abs(p.x);
    p.y = -p.y + 0.35;
    if (p.y + p.x > 1.0) {
        return sqrt(dot(p - vec2(0.25, 0.75), p - vec2(0.25, 0.75))) - 0.35355339;
    }
    return sqrt(min(
        dot(p - vec2(0.0, 1.0), p - vec2(0.0, 1.0)),
        dot(p - 0.5 * max(p.x + p.y, 0.0), p - 0.5 * max(p.x + p.y, 0.0))
    )) * sign(p.x - p.y);
}

void main() {
    vec2 uv = clamp(vTexCoord, 0.0, 1.0);
    vec4 src = texture(uTexture, uv);
    if (uMaskMode == 0 || (uMaskMode == 1 && uMaskShape == 0)) {
        outColor = src;
        return;
    }
    vec2 p = uv - 0.5;
    p.x *= max(uAspect, 0.2);
    float d = -1.0;
    if (uMaskMode == 2) {
        vec2 dir = vec2(cos(uAngleRad), sin(uAngleRad));
        d = dot(p, dir);
    } else {
        float minSpan = min(max(uAspect, 0.2), 1.0) * 0.42;
        if (uMaskShape == 1) {
            d = sdCircle(p, minSpan);
        } else if (uMaskShape == 2) {
            d = sdRoundedBox(p, vec2(minSpan * 0.95), minSpan * 0.24);
        } else if (uMaskShape == 3) {
            d = sdHeart(p / max(minSpan * 1.45, 0.001)) * minSpan;
        } else if (uMaskShape == 4) {
            d = sdStar5(vec2(p.x, -p.y), minSpan * 1.05, 0.46);
        } else if (uMaskShape == 5) {
            d = sdTriangle(vec2(p.x, -p.y + minSpan * 0.15), minSpan * 0.95);
        } else {
            d = sdDiamond(p, vec2(minSpan));
        }
    }
    float feather = max(0.006, uFeather * 0.22);
    float maskAlpha = 1.0 - smoothstep(-feather, feather, d);
    if (uInvertMask == 1) maskAlpha = 1.0 - maskAlpha;
    outColor = vec4(src.rgb, src.a * clamp(maskAlpha, 0.0, 1.0));
}
"""

    const val COMPOSITE_BLEND_FRAGMENT_SHADER = """#version 300 es
precision highp float;
in vec2 vTexCoord;
in vec2 vBaseUv;
uniform sampler2D uTexture;
uniform sampler2D uBaseTex;
uniform float uOpacity;
uniform int uBlendMode;
uniform float uBorderWidthUv;
uniform vec4 uBorderColor;
uniform vec2 uShadowOffsetUv;
uniform float uShadowOpacity;
out vec4 outColor;

vec3 compositeBlendRgb(vec3 baseRgb, vec3 topRgb, int mode) {
    if (mode == 1) return clamp(baseRgb + topRgb, 0.0, 1.0);
    if (mode == 2) return baseRgb * topRgb;
    if (mode == 3) return 1.0 - (1.0 - baseRgb) * (1.0 - topRgb);
    if (mode == 4) {
        return mix(
            2.0 * baseRgb * topRgb,
            1.0 - 2.0 * (1.0 - baseRgb) * (1.0 - topRgb),
            step(0.5, baseRgb)
        );
    }
    return topRgb;
}

void main() {
    vec4 base = texture(uBaseTex, clamp(vBaseUv, 0.0, 1.0));
    if (vTexCoord.x < 0.0 || vTexCoord.x > 1.0 || vTexCoord.y < 0.0 || vTexCoord.y > 1.0) {
        outColor = base;
        return;
    }
    vec4 top = texture(uTexture, vTexCoord);
    vec3 outRgb = base.rgb;
    float outA = base.a;

    // Optional soft drop shadow from layer alpha
    if (uShadowOpacity > 0.01) {
        vec2 shUv = vTexCoord - uShadowOffsetUv;
        float shSample = (shUv.x >= 0.0 && shUv.x <= 1.0 && shUv.y >= 0.0 && shUv.y <= 1.0)
            ? texture(uTexture, shUv).a
            : 0.0;
        float shA = clamp(shSample * uShadowOpacity * uOpacity * 0.72, 0.0, 1.0);
        outRgb = mix(outRgb, vec3(0.0), shA);
        outA = max(outA, shA);
    }

    // Optional layer frame border
    if (uBorderWidthUv > 0.0005) {
        float edgeDist = min(min(vTexCoord.x, 1.0 - vTexCoord.x), min(vTexCoord.y, 1.0 - vTexCoord.y));
        if (edgeDist < uBorderWidthUv) {
            float bA = clamp(uBorderColor.a * uOpacity, 0.0, 1.0);
            outRgb = mix(outRgb, uBorderColor.rgb, bA);
            outA = max(outA, bA);
        }
    }

    float sa = clamp(top.a * uOpacity, 0.0, 1.0);
    if (sa > 0.0001) {
        vec3 bRgb = compositeBlendRgb(clamp(outRgb, 0.0, 1.0), clamp(top.rgb, 0.0, 1.0), uBlendMode);
        float nextA = sa + outA * (1.0 - sa);
        outRgb = (bRgb * sa + outRgb * outA * (1.0 - sa)) / max(nextA, 0.0001);
        outA = nextA;
    }
    outColor = vec4(clamp(outRgb, 0.0, 1.0), clamp(outA, 0.0, 1.0));
}
"""

    fun hasShader(effectId: String): Boolean {
        val canonical = EffectCatalog.resolveCanonicalId(effectId)
        return canonical in effectBodies || com.example.engine.packs.CustomPackRepository.hasCustomEffect(canonical)
    }

    fun fragmentShaderFor(effectId: String): String {
        val canonical = EffectCatalog.resolveCanonicalId(effectId)
        com.example.engine.packs.CustomPackRepository.findCustomShaderFragmentSource(canonical)?.let {
            return it
        }
        val mappedFallback = com.example.engine.packs.CustomPackRepository.findFallbackEffectId(canonical)
        val body = effectBodies[canonical]
            ?: mappedFallback?.let { effectBodies[EffectCatalog.resolveCanonicalId(it)] }
            ?: "vec4 c = sampleMirrored(uv);"
        return ShaderCommonGlsl.buildFragmentShader(body)
    }

    fun fallbackShaderFor(effectId: String): String {
        val canonical = EffectCatalog.resolveCanonicalId(effectId)
        val fb = com.example.engine.packs.CustomPackRepository.findFallbackEffectId(canonical)
            ?: "ripple"
        val fbBody = effectBodies[EffectCatalog.resolveCanonicalId(fb)] ?: "vec4 c = sampleMirrored(uv);"
        return ShaderCommonGlsl.buildFragmentShader(fbBody)
    }

    fun extractUniformParams(pass: ShaderEffectPass, progress: Float = 0f): FloatArray {
        val item = EffectCatalog.findById(pass.effectId)
        val p1 = item?.params?.getOrNull(0)?.let {
            pass.evaluateParamAt(it.key, progress, it.defaultValue, it.minValue, it.maxValue)
        } ?: 0.5f
        val p2 = item?.params?.getOrNull(1)?.let {
            pass.evaluateParamAt(it.key, progress, it.defaultValue, it.minValue, it.maxValue)
        } ?: 0.5f
        val p3 = item?.params?.getOrNull(2)?.let {
            pass.evaluateParamAt(it.key, progress, it.defaultValue, it.minValue, it.maxValue)
        } ?: 0.5f
        return floatArrayOf(p1, p2, p3)
    }

    private val effectBodies: Map<String, String> = mapOf(
        // 1. Shake & Motion (6)
        "motion_tile" to """
            vec2 pan = vec2(uParam2 * 0.45, uParam3 * 0.45);
            float tileZoom = 1.0 + 0.55 * k;
            vec2 p = (uv - 0.5) * tileZoom + 0.5 + pan;
            c = uParam1 >= 0.5 ? sampleMirrored(p) : texture(uTexture, fract(p));
        """.trimIndent(),

        "hard_shake" to """
            float amp = (0.015 + uParam1 * 0.06) * k;
            float freq = 18.0 + uParam2 * 45.0;
            float decay = 1.0 - (uParam3 * 0.5 * uTime);
            vec2 off = vec2(
                sin(uTime * freq) * cos(uTime * freq * 0.37),
                cos(uTime * freq * 1.13) * sin(uTime * freq * 0.53)
            ) * amp * max(decay, 0.2);
            c = sampleMirrored(uv + off);
        """.trimIndent(),

        "smooth_shake" to """
            float amp = (0.01 + uParam1 * 0.045) * k;
            float freq = 3.0 + uParam2 * 10.0;
            vec2 off = vec2(sin(uTime * freq * 6.2831), cos(uTime * freq * 4.71)) * amp;
            c = sampleMirrored(uv + off);
        """.trimIndent(),

        "zoom_pulse" to """
            float pulseDepth = (0.08 + uParam1 * 0.25) * k;
            float beatSpeed = 3.0 + uParam2 * 9.0;
            float z = 1.0 + abs(sin(uTime * beatSpeed * 3.14159)) * pulseDepth;
            c = sampleMirrored((uv - 0.5) / z + 0.5);
        """.trimIndent(),

        "spin_vortex" to """
            float ang = (uParam1 - 0.5) * 3.14159 * k + sin(uTime * (2.0 + uParam2 * 8.0)) * 0.35 * k;
            float cs = cos(ang);
            float sn = sin(ang);
            vec2 d = uv - 0.5;
            vec2 rotUv = vec2(d.x * cs - d.y * sn, d.x * sn + d.y * cs) * (1.0 - 0.12 * k) + 0.5;
            c = sampleMirrored(rotUv);
        """.trimIndent(),

        "tile_grid" to """
            float g = clamp(floor(uParam1 + 0.5), 2.0, 4.0);
            vec2 p = fract(uv * g + vec2(uParam2 * 0.5 * sin(uTime * 2.0), 0.0));
            c = texture(uTexture, p);
        """.trimIndent(),

        // 2. Blur & Focus (5)
        "radial_zoom_blur" to """
            vec2 center = vec2(uParam2, 0.5);
            vec2 dir = (center - uv) * (0.025 + uParam1 * 0.055) * k;
            vec4 acc = vec4(0.0);
            for (int i = 0; i < 8; i++) {
                float f = float(i) / 7.0;
                acc += sampleMirrored(uv + dir * f);
            }
            c = acc / 8.0;
        """.trimIndent(),

        "dir_motion_blur" to """
            float a = uParam1 * 6.283185;
            vec2 dir = vec2(cos(a), sin(a)) * (0.008 + uParam2 * 0.035) * k;
            vec4 acc = vec4(0.0);
            for (int i = -4; i <= 4; i++) {
                acc += sampleMirrored(uv + dir * float(i) * 0.25);
            }
            c = acc / 9.0;
        """.trimIndent(),

        "gaussian_blur" to """
            vec2 texel = (1.0 / max(uTexSize, vec2(1.0))) * (1.0 + uParam1 * 7.0) * k;
            vec4 acc = orig * 0.227;
            acc += sampleMirrored(uv + vec2(texel.x, 0.0)) * 0.155;
            acc += sampleMirrored(uv - vec2(texel.x, 0.0)) * 0.155;
            acc += sampleMirrored(uv + vec2(0.0, texel.y)) * 0.155;
            acc += sampleMirrored(uv - vec2(0.0, texel.y)) * 0.155;
            acc += sampleMirrored(uv + texel) * 0.038;
            acc += sampleMirrored(uv - texel) * 0.038;
            acc += sampleMirrored(uv + vec2(texel.x, -texel.y)) * 0.038;
            acc += sampleMirrored(uv + vec2(-texel.x, texel.y)) * 0.038;
            c = acc;
        """.trimIndent(),

        "lens_bokeh" to """
            vec2 texel = (1.0 / max(uTexSize, vec2(1.0))) * (2.0 + uParam1 * 9.0) * k;
            vec4 acc = vec4(0.0);
            float wSum = 0.0;
            for (int i = 0; i < 8; i++) {
                float a = float(i) * 0.785398;
                vec4 s = sampleMirrored(uv + vec2(cos(a), sin(a)) * texel);
                float lum = dot(s.rgb, vec3(0.299, 0.587, 0.114));
                float w = 1.0 + pow(lum, 2.0) * (1.0 + uParam2 * 3.0);
                acc += s * w;
                wSum += w;
            }
            c = acc / max(wSum, 0.001);
        """.trimIndent(),

        "sharpen_clarity" to """
            vec2 texel = (1.0 / max(uTexSize, vec2(1.0))) * 1.3;
            vec4 n = sampleMirrored(uv + vec2(0.0, texel.y));
            vec4 s = sampleMirrored(uv - vec2(0.0, texel.y));
            vec4 e = sampleMirrored(uv + vec2(texel.x, 0.0));
            vec4 w = sampleMirrored(uv - vec2(texel.x, 0.0));
            float strength = 0.8 + uParam1 * 2.2;
            vec3 sharp = orig.rgb * (1.0 + 4.0 * strength) - (n.rgb + s.rgb + e.rgb + w.rgb) * strength;
            c = vec4(clamp(sharp, 0.0, 1.0), orig.a);
        """.trimIndent(),

        // 3. Color & Glow (9)
        "rgb_split" to """
            float a = uParam2 * 6.283185;
            float dist = (0.005 + uParam1 * 0.032) * k;
            vec2 off = vec2(cos(a), sin(a)) * dist;
            float r = sampleMirrored(uv + off).r;
            float g = orig.g;
            float b = sampleMirrored(uv - off).b;
            c = vec4(r, g, b, orig.a);
        """.trimIndent(),

        "exposure_flash" to """
            float pulse = abs(sin(uTime * 6.28318));
            float decayMult = mix(1.0, pulse, uParam2);
            float expVal = (uParam1 * 1.45) * k * decayMult;
            c = vec4(clamp(orig.rgb * (1.0 + expVal) + vec3(expVal * 0.25), 0.0, 1.0), orig.a);
        """.trimIndent(),

        "glow_bloom" to """
            vec2 texel = (1.0 / max(uTexSize, vec2(1.0))) * (2.5 + uParam1 * 8.0);
            vec3 bloom = vec3(0.0);
            for (int i = 0; i < 6; i++) {
                float ang = float(i) * 1.047197;
                vec3 s = sampleMirrored(uv + vec2(cos(ang), sin(ang)) * texel).rgb;
                bloom += max(s - vec3(0.42), vec3(0.0));
            }
            bloom /= 6.0;
            vec3 tint = hsv2rgb(vec3(uParam2, 0.8, 1.0));
            c = vec4(clamp(orig.rgb + bloom * (1.4 + tint * 0.65) * k, 0.0, 1.0), orig.a);
        """.trimIndent(),

        "hue_shift" to """
            vec3 hsv = rgb2hsv(orig.rgb);
            hsv.x = fract(hsv.x + uParam1 + uTime * uParam2 * 0.8);
            c = vec4(hsv2rgb(hsv), orig.a);
        """.trimIndent(),

        "color_adjust" to """
            float bright = (uParam1 - 0.5) * 0.8;
            float cont = 0.5 + uParam2 * 1.25;
            float sat = uParam3 * 2.0;
            vec3 col = orig.rgb + bright;
            col = (col - 0.5) * cont + 0.5;
            float lum = dot(col, vec3(0.299, 0.587, 0.114));
            col = mix(vec3(lum), col, sat);
            c = vec4(clamp(col, 0.0, 1.0), orig.a);
        """.trimIndent(),

        "duotone" to """
            float lum = dot(orig.rgb, vec3(0.299, 0.587, 0.114));
            vec3 shadowCol = hsv2rgb(vec3(fract(uParam1 + 0.45), 0.78, 0.28));
            vec3 highCol = hsv2rgb(vec3(uParam1, 0.88, 0.98));
            vec3 duo = mix(shadowCol, highCol, smoothstep(0.1, 0.9, lum));
            c = vec4(mix(orig.rgb, duo, uParam2), orig.a);
        """.trimIndent(),

        "invert" to """
            c = vec4(mix(orig.rgb, 1.0 - orig.rgb, uParam1), orig.a);
        """.trimIndent(),

        "vignette" to """
            float d = distance(uv, vec2(0.5));
            float inner = clamp(uParam1 * 0.65, 0.1, 0.75);
            float soft = max(uParam2 * 0.55, 0.05);
            float vig = smoothstep(inner, inner + soft, d);
            c = vec4(orig.rgb * (1.0 - vig * 0.92), orig.a);
        """.trimIndent(),

        "bw" to """
            float lum = dot(orig.rgb, vec3(0.299, 0.587, 0.114));
            float cont = 0.75 + uParam1 * 0.85;
            lum = clamp((lum - 0.5) * cont + 0.5, 0.0, 1.0);
            c = vec4(vec3(lum), orig.a);
        """.trimIndent(),

        // 4. Distortion & Glitch (7)
        "ripple" to """
            vec2 d = uv - 0.5;
            float dist = length(d);
            float freq = 14.0 + uParam3 * 36.0;
            float spd = 4.0 + uParam1 * 10.0;
            float amp = (0.008 + uParam2 * 0.03) * k;
            vec2 rippleUv = uv + normalize(d + vec2(0.0001)) * sin(dist * freq - uTime * spd) * amp;
            c = sampleMirrored(rippleUv);
        """.trimIndent(),

        "twirl" to """
            vec2 d = uv - 0.5;
            float rad = 0.2 + uParam2 * 0.45;
            float dist = length(d);
            if (dist < rad) {
                float pct = (rad - dist) / rad;
                float theta = pct * pct * (uParam1 * 6.0) * k;
                float s = sin(theta);
                float cs = cos(theta);
                d = vec2(d.x * cs - d.y * s, d.x * s + d.y * cs);
            }
            c = sampleMirrored(d + 0.5);
        """.trimIndent(),

        "bulge_pinch" to """
            vec2 d = uv - 0.5;
            float rad = 0.25 + uParam2 * 0.45;
            float dist = length(d);
            if (dist < rad && dist > 0.0001) {
                float pct = dist / rad;
                float amt = mix(1.0, pct, abs(uParam1) * 0.75 * k);
                d = uParam1 >= 0.0 ? d * amt : d / max(amt, 0.2);
            }
            c = sampleMirrored(d + 0.5);
        """.trimIndent(),

        "fisheye" to """
            vec2 d = (uv - 0.5) * 2.0;
            float r = length(d);
            float power = 1.0 + uParam1 * 1.45 * k;
            vec2 fishUv = 0.5 + d * pow(max(r, 0.0001), power - 1.0) * 0.5;
            c = sampleMirrored(fishUv);
        """.trimIndent(),

        "glitch" to """
            float slices = 8.0 + uParam2 * 24.0;
            float band = floor(uv.y * slices);
            float tStep = floor(uTime * 14.0);
            float rnd = hash12(vec2(band, tStep));
            float shift = (rnd - 0.5) * (0.04 + uParam1 * 0.14) * k * step(0.62, rnd);
            vec2 gUv = vec2(uv.x + shift, uv.y);
            c = vec4(
                sampleMirrored(gUv + vec2(shift * 0.45, 0.0)).r,
                sampleMirrored(gUv).g,
                sampleMirrored(gUv - vec2(shift * 0.45, 0.0)).b,
                orig.a
            );
        """.trimIndent(),

        "pixelate" to """
            float cells = mix(96.0, 12.0, clamp(uParam1, 0.05, 1.0));
            vec2 gridUv = (floor(uv * cells) + 0.5) / cells;
            c = texture(uTexture, gridUv);
        """.trimIndent(),

        "turbulent_displace" to """
            float freq = 5.0 + uParam2 * 16.0;
            float amp = (0.015 + uParam1 * 0.065) * k;
            vec2 off = vec2(
                sin(uv.y * freq + uTime * 5.0),
                cos(uv.x * freq + uTime * 4.2)
            ) * amp;
            c = sampleMirrored(uv + off);
        """.trimIndent(),

        // 5. Mirror & Kaleidoscope (4)
        "mirror_lr" to """
            float axis = clamp(uParam1, 0.1, 0.9);
            float mx = uv.x > axis ? (axis - (uv.x - axis)) : uv.x;
            c = sampleMirrored(vec2(mx, uv.y));
        """.trimIndent(),

        "mirror_tb" to """
            float axis = clamp(uParam1, 0.1, 0.9);
            float my = uv.y > axis ? (axis - (uv.y - axis)) : uv.y;
            c = sampleMirrored(vec2(uv.x, my));
        """.trimIndent(),

        "quad_mirror" to """
            float axis = clamp(uParam1, 0.1, 0.9);
            float mx = uv.x > axis ? (axis - (uv.x - axis)) : uv.x;
            float my = uv.y > axis ? (axis - (uv.y - axis)) : uv.y;
            c = sampleMirrored(vec2(mx, my));
        """.trimIndent(),

        "kaleidoscope" to """
            float sides = max(floor(uParam1 + 0.5), 4.0);
            vec2 p = uv - 0.5;
            float r = length(p);
            float a = atan(p.y, p.x) + uParam2 * 6.28318 + uTime * 0.4;
            float slice = 6.283185 / sides;
            a = mod(a, slice);
            a = abs(a - slice * 0.5);
            vec2 kUv = vec2(cos(a), sin(a)) * r + 0.5;
            c = sampleMirrored(kUv);
        """.trimIndent(),

        // 6. Retro & VHS (4)
        "vhs_tape" to """
            float roll = sin(uv.y * 18.0 + uTime * 7.0) * 0.004 * uParam1 * k;
            float trackLine = smoothstep(0.96, 1.0, sin(uv.y * 6.0 - uTime * 3.5));
            float tear = trackLine * 0.035 * uParam1 * k;
            float bleed = (0.006 + uParam2 * 0.018) * k;
            vec2 vUv = vec2(uv.x + roll + tear, uv.y);
            float r = sampleMirrored(vUv + vec2(bleed, 0.0)).r;
            float g = sampleMirrored(vUv).g;
            float b = sampleMirrored(vUv - vec2(bleed, 0.0)).b;
            float scan = 1.0 - 0.18 * step(0.5, fract(uv.y * max(uTexSize.y * 0.15, 60.0)));
            float staticNoise = (hash12(uv * 320.0 + uTime * 90.0) - 0.5) * 0.22 * uParam3;
            c = vec4(clamp(vec3(r, g, b) * scan + staticNoise, 0.0, 1.0), orig.a);
        """.trimIndent(),

        "scanlines" to """
            vec2 d = uv - 0.5;
            float curv = uParam2 * 0.28 * k;
            vec2 crtUv = uv + d * dot(d, d) * curv;
            vec4 tex = sampleMirrored(crtUv);
            float lineFreq = max(uTexSize.y * (0.12 + uParam1 * 0.25), 50.0);
            float lineMask = 0.72 + 0.28 * sin(crtUv.y * lineFreq * 3.14159);
            c = vec4(tex.rgb * lineMask, orig.a);
        """.trimIndent(),

        "film_grain" to """
            float scale = 180.0 + (1.0 - uParam2) * 320.0;
            float grain = (hash12(floor(uv * scale) + fract(uTime * 24.0) * 97.0) - 0.5);
            c = vec4(clamp(orig.rgb + grain * (0.28 * uParam1), 0.0, 1.0), orig.a);
        """.trimIndent(),

        "halftone" to """
            float grid = mix(90.0, 26.0, clamp(uParam1, 0.1, 1.0));
            vec2 st = uv * grid;
            vec2 cell = floor(st) + 0.5;
            vec4 sampleCol = texture(uTexture, cell / grid);
            float lum = dot(sampleCol.rgb, vec3(0.299, 0.587, 0.114));
            float dist = length(fract(st) - 0.5);
            float radius = sqrt(clamp(lum, 0.0, 1.0)) * 0.58;
            float dotVal = smoothstep(radius + 0.05, radius - 0.05, dist);
            vec3 col = mix(vec3(0.06), sampleCol.rgb * (0.85 + uParam2 * 0.45), dotVal);
            c = vec4(col, orig.a);
        """.trimIndent()
    ) +
        MoveTransformShaders.bodies +
        DistortionWarpShaders.bodies +
        MatteMaskKeyShaders.bodies +
        OpacityVisibilityShaders.bodies +
        ProceduralShaders.bodies +
        RepeatShaders.bodies +
        ThreeDShaders.bodies +
        TextAndOtherShaders.bodies
}
