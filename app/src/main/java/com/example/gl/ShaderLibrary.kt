package com.example.gl

import com.example.model.EffectCatalog
import com.example.model.ShaderEffectPass

/**
 * Shared GLSL ES 3.00 Vertex & Fragment Shader library for all 35 Video Star GPU effects.
 * Used identically by the live OpenGL ES 3.0 preview renderer (VideoStarGlRenderer) and
 * Jetpack Media3 Transformer GPU export (Media3ShaderEffect).
 */
object ShaderLibrary {

    val supportedEffectIds: Set<String> = EffectCatalog.shaderEffects.map { it.id }.toSet()

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

    const val PASSTHROUGH_OES_FRAGMENT_SHADER = """#version 300 es
#extension GL_OES_EGL_image_external_OES : require
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
    outColor = texture(uOesTexture, vTexCoord);
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

    fun hasShader(effectId: String): Boolean =
        EffectCatalog.resolveCanonicalId(effectId) in supportedEffectIds

    fun fragmentShaderFor(effectId: String): String {
        val canonical = EffectCatalog.resolveCanonicalId(effectId)
        val body = effectBodies[canonical] ?: "vec4 c = sampleMirrored(uv);"
        return buildFragmentShader(body)
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

    private fun buildFragmentShader(body: String): String = """#version 300 es
precision highp float;
in vec2 vTexCoord;
uniform sampler2D uTexture;
uniform vec2 uTexSize;
uniform float uTime;
uniform float uIntensity;
uniform float uParam1;
uniform float uParam2;
uniform float uParam3;
uniform vec4 uTintColor;
uniform int uBlendMode;
out vec4 outColor;

vec3 applyBlendMode(vec3 base, vec3 top, int mode) {
    if (mode == 1) return clamp(base + top, 0.0, 1.0);
    if (mode == 2) return base * top;
    if (mode == 3) return 1.0 - (1.0 - base) * (1.0 - top);
    if (mode == 4) {
        return mix(2.0 * base * top, 1.0 - 2.0 * (1.0 - base) * (1.0 - top), step(0.5, base));
    }
    return top;
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec2 mirrorUv(vec2 p) {
    vec2 m = mod(p, 2.0);
    return vec2(
        m.x > 1.0 ? 2.0 - m.x : m.x,
        m.y > 1.0 ? 2.0 - m.y : m.y
    );
}

vec4 sampleMirrored(vec2 p) {
    return texture(uTexture, mirrorUv(p));
}

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    vec2 uv = clamp(vTexCoord, 0.0, 1.0);
    float k = clamp(uIntensity, 0.0, 1.0);
    vec4 orig = texture(uTexture, uv);
    vec4 c = orig;
    $body
    vec3 blended = applyBlendMode(orig.rgb, clamp(c.rgb, 0.0, 1.0), uBlendMode);
    outColor = vec4(mix(orig.rgb, blended, k), orig.a);
}
"""

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
    )
}
