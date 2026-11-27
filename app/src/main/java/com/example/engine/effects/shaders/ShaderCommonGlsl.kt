package com.example.engine.effects.shaders

/**
 * Shared GLSL ES 3.00 preamble containing math, noise, color space, UV wrapping,
 * premultiplied-alpha safe sampling, and 3D ray/projection utilities used by
 * all 145 shaders (35 Video Star + 110 Alight Motion).
 */
object ShaderCommonGlsl {

    fun buildFragmentShader(body: String): String = """#version 300 es
precision highp float;
in vec2 vTexCoord;

uniform sampler2D uTexture;
uniform sampler2D uBackgroundTex;
uniform sampler2D uHistoryTex;
uniform sampler2D uMaskTex;

uniform vec2 uTexSize;
uniform float uTime;
uniform float uDuration;
uniform float uIntensity;
uniform float uProgress;
uniform float uParam1;
uniform float uParam2;
uniform float uParam3;
uniform float uParams[12];
uniform vec4 uColors[3];
uniform int uModes[4];
uniform vec4 uTintColor;
uniform int uBlendMode;
uniform int uHasMask;
uniform int uHasBg;

out vec4 outColor;

const float PI = 3.14159265359;
const float TAU = 6.28318530718;

vec3 applyBlendMode(vec3 base, vec3 top, int mode) {
    if (mode == 1) return clamp(base + top, 0.0, 1.0);
    if (mode == 2) return base * top;
    if (mode == 3) return 1.0 - (1.0 - base) * (1.0 - top);
    if (mode == 4) {
        return mix(2.0 * base * top, 1.0 - 2.0 * (1.0 - base) * (1.0 - top), step(0.5, base));
    }
    return top;
}

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p, int octaves) {
    float v = 0.0;
    float amp = 0.5;
    mat2 rot = mat2(0.8, 0.6, -0.6, 0.8);
    for (int i = 0; i < 6; i++) {
        if (i >= octaves) break;
        v += amp * valueNoise(p);
        p = rot * p * 2.03 + vec2(17.1, 31.7);
        amp *= 0.5;
    }
    return v;
}

vec2 mirrorUv(vec2 p) {
    vec2 m = mod(p, 2.0);
    return vec2(
        m.x > 1.0 ? 2.0 - m.x : m.x,
        m.y > 1.0 ? 2.0 - m.y : m.y
    );
}

vec4 sampleClean(sampler2D tex, vec2 p) {
    vec4 s = texture(tex, clamp(p, 0.0, 1.0));
    if (s.a > 0.001 && s.a < 0.999) {
        // Avoid dark halo when interpolating edges
        s.rgb = clamp(s.rgb / mix(1.0, s.a, 0.0), 0.0, 1.0);
    }
    return s;
}

vec4 sampleMirrored(vec2 p) {
    return sampleClean(uTexture, mirrorUv(p));
}

vec4 sampleMirroredBounded(vec2 p, int wrapMode) {
    if (wrapMode == 0) {
        if (p.x < 0.0 || p.x > 1.0 || p.y < 0.0 || p.y > 1.0) return vec4(0.0);
        return texture(uTexture, p);
    }
    if (wrapMode == 1) {
        return texture(uTexture, fract(p));
    }
    return sampleMirrored(p);
}

vec2 rotate2d(vec2 v, float rad) {
    float c = cos(rad);
    float s = sin(rad);
    return vec2(v.x * c - v.y * s, v.x * s + v.y * c);
}

float aspectRatio() {
    return max(uTexSize.x, 1.0) / max(uTexSize.y, 1.0);
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

vec3 rgb2ycbcr(vec3 c) {
    float y = dot(c, vec3(0.299, 0.587, 0.114));
    float cb = (c.b - y) * 0.564 + 0.5;
    float cr = (c.r - y) * 0.713 + 0.5;
    return vec3(y, cb, cr);
}

mat3 rotEulerDeg(vec3 deg) {
    vec3 r = radians(deg);
    float cx = cos(r.x), sx = sin(r.x);
    float cy = cos(r.y), sy = sin(r.y);
    float cz = cos(r.z), sz = sin(r.z);
    mat3 rx = mat3(1.0, 0.0, 0.0, 0.0, cx, -sx, 0.0, sx, cx);
    mat3 ry = mat3(cy, 0.0, sy, 0.0, 1.0, 0.0, -sy, 0.0, cy);
    mat3 rz = mat3(cz, -sz, 0.0, sz, cz, 0.0, 0.0, 0.0, 1.0);
    return rz * ry * rx;
}

float sdBox(vec3 p, vec3 b) {
    vec3 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
}

vec2 triplanarUv(vec3 p, vec3 n) {
    vec3 an = abs(n);
    if (an.x > an.y && an.x > an.z) return p.zy * 0.5 + 0.5;
    if (an.y > an.z) return p.xz * 0.5 + 0.5;
    return p.xy * 0.5 + 0.5;
}

void main() {
    vec2 uv = clamp(vTexCoord, 0.0, 1.0);
    float k = clamp(uIntensity, 0.0, 1.0);
    vec4 orig = texture(uTexture, uv);
    vec4 c = orig;
    $body
    vec4 safeC = vec4(clamp(c.rgb, 0.0, 1.0), clamp(c.a, 0.0, 1.0));
    vec3 blendedRgb = (uBlendMode == 0)
        ? safeC.rgb
        : applyBlendMode(orig.rgb, safeC.rgb, uBlendMode);
    vec4 targetCol = vec4(mix(safeC.rgb, blendedRgb, safeC.a), safeC.a);
    outColor = mix(orig, targetCol, k);
}
"""

    fun buildCustomFragmentShader(preambleDecls: String, mainBody: String): String {
        val base = buildFragmentShader("/* body_placeholder */")
        val header = base.substringBefore("void main() {")
        return buildString {
            append(header)
            if (preambleDecls.isNotBlank()) {
                append("\n// Custom helper functions\n")
                append(preambleDecls.trim())
                append('\n')
            }
            append(
                """
vec4 runCustomUserMain(vec2 _starUv, vec4 _starOrig) {
    vec4 _starOut = _starOrig;
    ${mainBody.trim()}
    return _starOut;
}

void main() {
    vec2 uv = clamp(vTexCoord, 0.0, 1.0);
    float k = clamp(uIntensity, 0.0, 1.0);
    vec4 orig = texture(uTexture, uv);
    vec4 c = runCustomUserMain(uv, orig);
    vec4 safeC = vec4(clamp(c.rgb, 0.0, 1.0), clamp(c.a, 0.0, 1.0));
    vec3 blendedRgb = (uBlendMode == 0)
        ? safeC.rgb
        : applyBlendMode(orig.rgb, safeC.rgb, uBlendMode);
    vec4 targetCol = vec4(mix(safeC.rgb, blendedRgb, safeC.a), safeC.a);
    outColor = mix(orig, targetCol, k);
}
                """.trimIndent()
            )
            append('\n')
        }
    }
}
